package com.volleyball.game.match;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Stage 9: match statistics, timeouts, fatigue and the replay. */
class StatisticsTimeoutsReplayTest {
    private static final float DT = 1f / 120f;
    private static final MatchFormat SHORT = new MatchFormat(1, 15, 15, 2, RuleMode.OFFICIAL, 8f);

    @Test
    void theStatisticsAddUpWithTheScore() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        MatchController match = new MatchController(world, SHORT, 3, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        match.start(CourtSide.HOME);
        for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 15; i++) {
            world.step(DT);
            match.update(DT, List.of());
        }
        MatchStatistics stats = match.statistics();
        MatchScore score = match.rules().score();
        int home = 0;
        int away = 0;
        for (MatchScore.SetResult set : score.finishedSets()) {
            home += set.home();
            away += set.away();
        }
        assertEquals(home, stats.points(CourtSide.HOME));
        assertEquals(away, stats.points(CourtSide.AWAY));
        for (CourtSide side : CourtSide.values()) {
            MatchStatistics.Line t = stats.total(side);
            assertTrue(t.attacks > 5, "attacks were counted");
            assertTrue(t.kills <= t.attacks && t.attackErrors + t.blocked <= t.attacks);
            assertTrue(t.aces + t.serveErrors <= t.serves);
            assertTrue(t.assists <= t.kills, "an assist is the set before a kill");
            assertTrue(t.receptions > 0 && t.receptionAverage() >= 0f && t.receptionAverage() <= 3f);
            // Every point won is a kill, an ace, a block point or an error of the opponent (or a fault).
            MatchStatistics.Line o = stats.total(side.opposite());
            int explained = t.kills + t.aces + t.blockPoints + o.errors;
            assertTrue(explained >= stats.points(side) * 0.8f, side + ": points " + stats.points(side) + ", explained " + explained);
        }
        assertFalse(stats.active(CourtSide.HOME).isEmpty());
    }

    @Test
    void aTimeoutIsTakenAtTheNextBreakAndThereAreTwoPerSet() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 4);
        MatchController match = new MatchController(world, SHORT, 4, Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
        int[] timeouts = new int[1];
        match.addListener(new MatchListener() {
            @Override
            public void onTimeout(CourtSide side, int used) {
                timeouts[0]++;
            }
        });
        match.start(CourtSide.HOME);
        assertTrue(match.requestTimeout(CourtSide.HOME));
        assertFalse(match.requestTimeout(CourtSide.AWAY), "one request at a time");
        boolean sawTimeout = false;
        for (long i = 0; i < 120L * 60 * 3 && !sawTimeout; i++) {
            world.step(DT);
            match.update(DT, List.of());
            sawTimeout = match.phase() == MatchController.Phase.TIMEOUT;
        }
        assertTrue(sawTimeout, "the timeout came after the first point");
        assertEquals(CourtSide.HOME, match.timeoutSide());
        assertEquals(1, match.timeoutsLeft(CourtSide.HOME));
        match.skipTimeout();
        world.step(DT);
        match.update(DT, List.of());
        assertNotEquals(MatchController.Phase.TIMEOUT, match.phase(), "Space skips it");
        assertTrue(match.requestTimeout(CourtSide.HOME));
        for (long i = 0; i < 120L * 60 * 3 && match.timeoutsLeft(CourtSide.HOME) > 0; i++) {
            world.step(DT);
            match.update(DT, List.of());
        }
        assertEquals(0, match.timeoutsLeft(CourtSide.HOME));
        assertFalse(match.requestTimeout(CourtSide.HOME), "only two per set");
        assertEquals(2, timeouts[0]);
    }

    @Test
    void runningTiresAndRestRecoversUnlessFatigueIsOff() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 5);
        VolleyballPlayer p = world.home().players().get(1);
        p.placeAt(-8f, -4f);
        MoveIntent intent = world.intent(p);
        for (int i = 0; i < 120 * 4; i++) {
            intent.clear();
            intent.direction.set(i / 120 % 2 == 0 ? 1f : -1f, 0f);
            intent.sprint = true;
            world.step(DT);
        }
        float tired = p.stamina();
        assertTrue(tired < 0.95f, "four seconds of sprints cost stamina: " + tired);
        assertTrue(tired > 0.5f, "but gently");
        for (int i = 0; i < 120 * 3; i++) {
            world.intent(p).clear();
            world.step(DT);
        }
        assertTrue(p.stamina() > tired, "standing restores it");
        world.setFatigue(false);
        world.step(DT);
        assertEquals(1f, p.stamina(), 1e-6f, "arcade: always fresh");
    }

    @Test
    void theReplayShowsTheRallyAndGivesTheLiveStateBack() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 6);
        ReplayRecorder replay = new ReplayRecorder(world.players(), world.ball());
        VolleyballPlayer p = world.home().players().get(2);
        p.placeAt(-6f, 0f);
        replay.start();
        for (int i = 0; i < 240; i++) {
            world.intent(p).clear();
            world.intent(p).direction.set(1f, 0f);
            world.step(DT);
            replay.capture(DT);
        }
        assertTrue(replay.hasReplay());
        assertTrue(replay.duration() > 1.8f);
        Vector3 live = new Vector3(p.position);
        replay.stop();
        replay.saveLive();
        replay.apply(0f);
        assertTrue(p.position.x < live.x - 2f, "at the start of the rally the player was further back");
        replay.apply(replay.duration());
        assertEquals(live.x, p.position.x, 0.15f);
        replay.apply(0.5f);
        replay.restoreLive();
        assertEquals(live.x, p.position.x, 1e-5f, "the live state is back");
    }
}
