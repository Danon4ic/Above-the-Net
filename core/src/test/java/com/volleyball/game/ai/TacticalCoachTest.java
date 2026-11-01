package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.tactics.TeamCommand;
import com.volleyball.game.tactics.TeamTactics;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalCoachTest {
    private final TeamTactics tactics = new TeamTactics();
    private double time;

    private BallTouch touch(CourtSide side, ContactType type, Vector3 contact, Vector3 velocity, PassRating rating, int player) {
        time += 0.5;
        return new BallTouch(player, side, type, ContactPurpose.PASS, 0.8f, rating, 0f, contact, contact.cpy(), false, 1, velocity, "", time);
    }

    /** One rally the opponent (AWAY) wins with an attack hit from z = 3 with the given velocity. */
    private void theyScore(TacticalCoach coach, Vector3 velocity) {
        coach.newRally();
        coach.record(touch(CourtSide.AWAY, ContactType.ATTACK, new Vector3(1f, 3.2f, 3f), velocity, null, 9));
        coach.rallyFinished(false, CourtSide.AWAY, ContactType.ATTACK, 10, 12, 1, null);
    }

    @Test
    void crossCourtKillsCloseTheDiagonal() {
        TacticalCoach coach = new TacticalCoach(CourtSide.HOME, Difficulty.PROFESSIONAL, tactics);
        for (int i = 0; i < 5; i++) theyScore(coach, new Vector3(-17f, -3f, -6f));
        assertEquals(TeamCommand.CLOSE_CROSS, tactics.active(TeamCommand.Group.DEFENSE));
    }

    @Test
    void lineKillsCloseTheLine() {
        TacticalCoach coach = new TacticalCoach(CourtSide.HOME, Difficulty.PROFESSIONAL, tactics);
        for (int i = 0; i < 5; i++) theyScore(coach, new Vector3(-18f, -3f, 0.5f));
        assertEquals(TeamCommand.CLOSE_LINE, tactics.active(TeamCommand.Group.DEFENSE));
    }

    @Test
    void tipsBringTheDefendersUpForThem() {
        TacticalCoach coach = new TacticalCoach(CourtSide.HOME, Difficulty.PROFESSIONAL, tactics);
        for (int i = 0; i < 5; i++) theyScore(coach, new Vector3(-6f, 2f, -1f));
        assertEquals(TeamCommand.EXPECT_TIP, tactics.active(TeamCommand.Group.DEFENSE));
    }

    @Test
    void serveErrorsMakeTheServesSafe() {
        TacticalCoach coach = new TacticalCoach(CourtSide.HOME, Difficulty.INTERMEDIATE, tactics);
        for (int i = 0; i < 8; i++) {
            coach.newRally();
            coach.record(touch(CourtSide.HOME, ContactType.SERVE, new Vector3(-10f, 3f, 0f), new Vector3(18f, 2f, 0f), null, 1));
            coach.rallyFinished(false, CourtSide.HOME, ContactType.SERVE, 5, 8, 1, null);
        }
        assertEquals(TeamCommand.SAFE_SERVE, tactics.active(TeamCommand.Group.SERVE));
    }

    @Test
    void aStrugglingReceiverGetsTheServes() {
        TacticalCoach coach = new TacticalCoach(CourtSide.HOME, Difficulty.PROFESSIONAL, tactics);
        for (int i = 0; i < 6; i++) {
            coach.newRally();
            coach.record(touch(CourtSide.HOME, ContactType.SERVE, new Vector3(-10f, 3f, 0f), new Vector3(16f, 2f, 0f), null, 1));
            coach.record(touch(CourtSide.AWAY, ContactType.FOREARM_PASS, new Vector3(7f, 0.8f, 2f), new Vector3(-3f, 8f, 0f),
                    PassRating.EMERGENCY, 8));
            coach.rallyFinished(true, CourtSide.AWAY, ContactType.FOREARM_PASS, 12, 9, 1, null);
        }
        assertEquals(TeamCommand.SERVE_RECEIVER, tactics.active(TeamCommand.Group.SERVE));
        assertEquals(8, tactics.serveTarget());
    }

    @Test
    void beginnersPlayWithoutAPlan() {
        TacticalCoach coach = new TacticalCoach(CourtSide.HOME, Difficulty.BEGINNER, tactics);
        for (int i = 0; i < 8; i++) theyScore(coach, new Vector3(-17f, -3f, -6f));
        assertNull(tactics.active(TeamCommand.Group.DEFENSE));
    }

    @Test
    void computerTeamsLetBallsGoThatTheOtherTeamSendsClearlyOut() {
        int opportunities = 0;
        int touched = 0;
        int chasedTotal = 0;
        for (long seed = 1; seed <= 3; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 7);
            MatchController match = new MatchController(world, TeamAITest.ONE_SET, seed, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
            TrajectoryPredictor.Prediction prediction = new TrajectoryPredictor.Prediction();
            // The out-ball the receiving team is watching: how far out it lands, and who sent it.
            float[] outBy = {0f};
            CourtSide[] sentBy = {null};
            int[] counts = {0, 0, 0};
            world.ballHandling().addListener(t -> {
                // A serve starts a new rally: it is not a play on the previous rally's ball.
                if (t.type() != ContactType.SERVE && sentBy[0] != null && t.side() != sentBy[0] && t.type() != ContactType.BLOCK
                        && outBy[0] > 0.3f) counts[1]++;
                sentBy[0] = t.side();
                outBy[0] = 0f;
            });
            // A ball off a body is that team's ball (saving it is right, not playing an out-ball).
            world.addContactListener(c -> {
                if (c.kind() != com.volleyball.game.physics.ColliderKind.PLAYER_BODY) return;
                sentBy[0] = world.player(c.playerId()).side;
                outBy[0] = 0f;
            });
            match.start(CourtSide.HOME);
            boolean counted = false;
            boolean chased = false;
            java.util.Set<Integer> diving = new java.util.HashSet<>();
            for (long steps = 0; match.phase() != MatchController.Phase.FINISHED && steps < 120L * 60 * 15; steps++) {
                // Who was already diving before this step (a dive that started earlier is not a new chase).
                diving.clear();
                for (VolleyballPlayer p : world.players()) if (p.bodyState() == BodyState.DIVING) diving.add(p.id);
                world.step(TeamAITest.DT);
                match.update(TeamAITest.DT, List.<VolleyballPlayer>of());
                if (!world.ball().isActive() || sentBy[0] == null) {
                    counted = false;
                    continue;
                }
                // Once the ball is on the receiving side, read where it lands (gravity and air, no float wobble).
                if (CourtDimensions.sideOf(world.ball().position.x) == sentBy[0]) {
                    counted = false;
                    continue;
                }
                world.predictor().predict(world.ball(), 6f, prediction);
                if (!prediction.lands) continue;
                outBy[0] = Math.max(Math.abs(prediction.landing.x) - CourtDimensions.HALF_LENGTH,
                        Math.abs(prediction.landing.z) - CourtDimensions.HALF_WIDTH);
                if (outBy[0] > 0.3f && !counted) {
                    counted = true;
                    counts[0]++;
                    chased = false;
                }
                // Somebody of the receiving team going for it: a contact armed, a dive started now.
                if (outBy[0] > 0.3f && !chased && world.ballHandling().servePhase() == BallHandling.ServePhase.NONE) {
                    for (VolleyballPlayer p : world.players()) {
                        if (p.side == world.ballHandling().lastTouchSide() || !p.isOnCourt()) continue;
                        boolean dive = p.bodyState() == BodyState.DIVING && !diving.contains(p.id);
                        if (world.ballHandling().isArmed(p) || dive) {
                            chased = true;
                            counts[2]++;
                            break;
                        }
                    }
                }
            }
            opportunities += counts[0];
            touched += counts[1];
            chasedTotal += counts[2];
        }
        assertTrue(opportunities >= 3, "out-balls seen: " + opportunities);
        assertTrue(touched == 0, touched + " of " + opportunities + " clear out-balls were played");
        assertTrue(chasedTotal <= opportunities / 20, chasedTotal + " of " + opportunities + " clear out-balls were chased");
    }
}
