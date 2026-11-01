package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The team AI in real simulated matches (full physics, mechanics and referee). */
class TeamAITest {
    static final float DT = 1f / 120f;
    static final MatchFormat ONE_SET = new MatchFormat(1, 15, 15, 2, RuleMode.OFFICIAL, 8f);

    /** Plays one set to 15 and returns the points of (home, away). */
    static int[] playSet(Difficulty home, Difficulty away, long seed, StepCheck check) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 31);
        MatchController match = new MatchController(world, ONE_SET, seed, home, away);
        match.start(CourtSide.HOME);
        Collection<VolleyballPlayer> humans = List.of();
        for (long steps = 0; match.phase() != MatchController.Phase.FINISHED; steps++) {
            world.step(DT);
            match.update(DT, humans);
            if (check != null) check.after(world, match);
            assertTrue(steps < 120L * 60 * 15, "the set must finish");
        }
        MatchScore.SetResult set = match.rules().score().finishedSets().get(0);
        return new int[]{set.home(), set.away()};
    }

    interface StepCheck {
        void after(GameWorld world, MatchController match);
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void everyDifficultyPlaysACompleteSet(Difficulty difficulty) {
        int[] score = playSet(difficulty, difficulty, 3, null);
        assertTrue(Math.max(score[0], score[1]) >= 15);
        assertTrue(Math.abs(score[0] - score[1]) >= 2);
    }

    @Test
    void professionalTeamBeatsBeginners() {
        int pro = 0;
        int beginner = 0;
        for (long seed = 1; seed <= 3; seed++) {
            int[] score = playSet(Difficulty.PROFESSIONAL, Difficulty.BEGINNER, seed, null);
            pro += score[0];
            beginner += score[1];
            assertTrue(score[0] > score[1], "seed " + seed + ": " + score[0] + ":" + score[1]);
        }
        assertTrue(pro > beginner * 1.6f, pro + " : " + beginner);
    }

    @Test
    void onlyOnePlayerPerTeamGoesForTheBallAndHumansAreLeftAlone() {
        int[] maxArmed = new int[2];
        StringBuilder what = new StringBuilder();
        playSet(Difficulty.INTERMEDIATE, Difficulty.PROFESSIONAL, 2, (world, match) -> {
            for (CourtSide side : CourtSide.values()) {
                int armed = 0;
                StringBuilder who = new StringBuilder();
                for (VolleyballPlayer p : side == CourtSide.HOME ? world.home().players() : world.away().players()) {
                    if (world.ballHandling().isArmed(p)) {
                        armed++;
                        who.append(" #").append(p.number).append(' ').append(match.ai(side).state(p)).append(p.isUpright() ? "" : " down");
                    }
                }
                if (armed > 1 && what.isEmpty()) {
                    what.append(String.format("t%.2f %s touches %d last %d ball (%.1f,%.1f,%.1f):%s", world.time(), side,
                            world.ballHandling().teamTouches(side), world.ballHandling().lastToucherId(), world.ball().position.x,
                            world.ball().position.y, world.ball().position.z, who));
                }
                maxArmed[side.ordinal()] = Math.max(maxArmed[side.ordinal()], armed);
            }
        });
        assertEquals(1, maxArmed[0], "home: never two players playing the same ball: " + what);
        assertEquals(1, maxArmed[1], "away: never two players playing the same ball: " + what);
    }

    @Test
    void humanControlledPlayersAreNeverMovedByTheAI() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 5);
        MatchController match = new MatchController(world, ONE_SET, 5, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        VolleyballPlayer human = world.home().players().get(3);
        VolleyballPlayer second = world.away().players().get(2);
        match.start(CourtSide.HOME);
        for (int i = 0; i < 120 * 20; i++) {
            MoveIntent a = world.intent(human);
            MoveIntent b = world.intent(second);
            a.clear();
            b.clear();
            a.direction.set(0.3f, 0.1f);
            world.step(DT);
            // (Before each serve the match itself lines everyone up; during rallies the AI leaves people alone.)
            boolean rally = match.phase() == MatchController.Phase.RALLY;
            match.update(DT, List.of(human, second));
            if (rally && match.phase() == MatchController.Phase.RALLY) {
                assertEquals(0.3f, a.direction.x, 1e-6f, "the AI must not touch the human's intent");
                assertEquals(0f, b.direction.x, 1e-6f);
            }
            assertFalse(world.ballHandling().isArmed(second), "the AI never plays the ball for a person");
            assertEquals(AiState.IDLE, match.ai(CourtSide.HOME).state(human));
        }
    }

    @Test
    void statesFollowTheRally() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 8);
        MatchController match = new MatchController(world, ONE_SET, 8, Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
        match.start(CourtSide.HOME);
        world.step(DT);
        match.update(DT, List.of());
        world.step(DT);
        match.update(DT, List.of());
        VolleyballPlayer server = match.currentServer();
        assertEquals(AiState.SERVING, match.ai(CourtSide.HOME).state(server));
        for (VolleyballPlayer p : world.away().onCourt()) assertEquals(AiState.READY_TO_RECEIVE, match.ai(CourtSide.AWAY).state(p));
        boolean sawApproach = false;
        boolean sawBlockOrDefense = false;
        for (int i = 0; i < 120 * 60; i++) {
            world.step(DT);
            match.update(DT, List.of());
            for (CourtSide side : CourtSide.values()) {
                for (VolleyballPlayer p : side == CourtSide.HOME ? world.home().players() : world.away().players()) {
                    AiState state = match.ai(side).state(p);
                    sawApproach |= state == AiState.APPROACHING || state == AiState.ATTACKING;
                    sawBlockOrDefense |= state == AiState.BLOCKING || state == AiState.DEFENDING;
                }
            }
        }
        assertTrue(sawApproach && sawBlockOrDefense);
    }

    @Test
    void readErrorAndReactionComeFromTheDifficulty() {
        assertTrue(Difficulty.BEGINNER.reaction > Difficulty.INTERMEDIATE.reaction);
        assertTrue(Difficulty.INTERMEDIATE.reaction > Difficulty.PROFESSIONAL.reaction);
        assertTrue(Difficulty.BEGINNER.readError > Difficulty.PROFESSIONAL.readError);
        assertFalse(Difficulty.BEGINNER.meaningfulBlock);
        assertTrue(Difficulty.PROFESSIONAL.readsPlay && Difficulty.PROFESSIONAL.adapts);
        assertEquals(Difficulty.PROFESSIONAL, Difficulty.parse("pro"));
        assertEquals(Difficulty.BEGINNER, Difficulty.parse("Beginner"));
        assertNull(Difficulty.parse("x"));
    }

    @Test
    void zonesOfServeTargets() {
        assertEquals(4, TeamAI.zoneOf(1f, -3f));
        assertEquals(3, TeamAI.zoneOf(2f, 0f));
        assertEquals(2, TeamAI.zoneOf(2f, 3f));
        assertEquals(5, TeamAI.zoneOf(7f, -3f));
        assertEquals(6, TeamAI.zoneOf(7f, 0f));
        assertEquals(1, TeamAI.zoneOf(7f, 3f));
    }

    // ------------------------------------------------------------------ memory

    private static BallTouch touch(int id, CourtSide side, ContactType type, ContactPurpose purpose, PassRating rating,
                                   Vector3 at, Vector3 velocity, String variant) {
        return new BallTouch(id, side, type, purpose, 0.8f, rating, 0f, at, at.cpy(), type == ContactType.ATTACK, 1,
                velocity, variant, 0);
    }

    @Test
    void memoryLearnsAHittersHabits() {
        MatchMemory memory = new MatchMemory(CourtSide.HOME);
        // An away hitter on their left (z = +3) hitting towards the middle/other side (z decreasing): cross.
        for (int i = 0; i < 3; i++) {
            memory.record(touch(20, CourtSide.AWAY, ContactType.ATTACK, ContactPurpose.ATTACK, null, new Vector3(1f, 3.2f, 3f),
                    new Vector3(-20f, -5f, -8f), AttackType.POWER.label));
        }
        memory.record(touch(20, CourtSide.AWAY, ContactType.ATTACK, ContactPurpose.ATTACK, null, new Vector3(1f, 3.2f, 3f),
                new Vector3(-20f, -5f, 0f), AttackType.POWER.label));
        memory.record(touch(20, CourtSide.AWAY, ContactType.ATTACK, ContactPurpose.ATTACK, null, new Vector3(1f, 3.2f, 3f),
                new Vector3(-6f, 1f, 0f), AttackType.TIP.label));
        MatchMemory.Habit habit = memory.habit(20);
        assertEquals(3, habit.cross);
        assertEquals(1, habit.line);
        assertEquals(1, habit.tips);
        assertTrue(habit.crossBias() > 0.4f);
    }

    @Test
    void memoryPrefersSetsThatScore() {
        MatchMemory memory = new MatchMemory(CourtSide.HOME);
        for (int i = 0; i < 4; i++) {
            memory.newRally();
            memory.record(touch(1, CourtSide.HOME, ContactType.OVERHEAD_PASS, ContactPurpose.SET, PassRating.GOOD,
                    new Vector3(-1f, 2.4f, 1f), new Vector3(0f, 6f, -2f), SetType.HIGH_OUTSIDE.label));
            memory.record(touch(2, CourtSide.HOME, ContactType.ATTACK, ContactPurpose.ATTACK, null,
                    new Vector3(-1f, 3.2f, -3f), new Vector3(20f, -5f, 0f), AttackType.POWER.label));
            memory.rallyResult(true);
        }
        memory.newRally();
        memory.record(touch(1, CourtSide.HOME, ContactType.OVERHEAD_PASS, ContactPurpose.SET, PassRating.GOOD,
                new Vector3(-1f, 2.4f, 1f), new Vector3(0f, 4f, 0f), SetType.QUICK_MIDDLE.label));
        memory.record(touch(3, CourtSide.HOME, ContactType.ATTACK, ContactPurpose.ATTACK, null,
                new Vector3(-0.8f, 3.2f, 0f), new Vector3(20f, -5f, 0f), AttackType.POWER.label));
        memory.rallyResult(false);
        assertTrue(memory.setSuccess(SetType.HIGH_OUTSIDE) > memory.setSuccess(SetType.QUICK_MIDDLE));
        assertEquals(0.5f, memory.setSuccess(SetType.PIPE), 1e-6f, "no data: neutral");
    }
}
