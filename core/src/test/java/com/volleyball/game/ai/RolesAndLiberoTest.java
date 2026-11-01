package com.volleyball.game.ai;

import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.CourtZones;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Roles, the libero and the human's area in real simulated matches. */
class RolesAndLiberoTest {
    static final float DT = 1f / 120f;
    static final MatchFormat SHORT = new MatchFormat(1, 15, 15, 2, RuleMode.OFFICIAL, 8f);

    @Test
    void rotationMapsTheLiberoIntoTheReplacedZone() {
        RotationManager r = new RotationManager(new int[]{0, 1, 2, 3, 4, 5}, new int[]{6, 7, 8, 9, 10, 11});
        r.replaceWithLibero(CourtSide.HOME, 12, 5); // the player in zone 6
        assertEquals(12, r.playerInZone(CourtSide.HOME, 6));
        assertEquals(6, r.zoneOf(CourtSide.HOME, 12));
        assertEquals(-1, r.zoneOf(CourtSide.HOME, 5), "the replaced player is off court");
        assertEquals(6, r.slotZone(CourtSide.HOME, 5), "but keeps the place in the rotation order");
        assertFalse(r.isFrontRow(CourtSide.HOME, 12));
        r.rotate(CourtSide.HOME);
        assertEquals(5, r.zoneOf(CourtSide.HOME, 12), "the libero moves with the rotation");
        assertThrows(IllegalArgumentException.class, () -> r.replaceWithLibero(CourtSide.HOME, 12, 2), "front row");
        r.releaseLibero(CourtSide.HOME);
        assertEquals(5, r.playerInZone(CourtSide.HOME, 5));
    }

    /** Plays a set; at the start of every rally runs {@code check}. */
    private static void playSet(GameWorld world, MatchController match, List<VolleyballPlayer> humans, Runnable check) {
        match.start(CourtSide.HOME);
        MatchController.Phase before = match.phase();
        for (long steps = 0; match.phase() != MatchController.Phase.FINISHED; steps++) {
            world.step(DT);
            match.update(DT, humans);
            if (before != MatchController.Phase.RALLY && match.phase() == MatchController.Phase.RALLY && check != null) check.run();
            before = match.phase();
            assertTrue(steps < 120L * 60 * 15, "the set must finish");
        }
    }

    @Test
    void liberoReplacesTheBackRowMiddleButNeverServesOrPlaysFront() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 4);
        MatchController match = new MatchController(world, SHORT, 4, Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
        int[] liberoRallies = new int[1];
        int[] rallies = new int[1];
        playSet(world, match, List.of(), () -> {
            rallies[0]++;
            RotationManager rotation = match.rules().rotation();
            for (Team team : List.of(world.home(), world.away())) {
                assertEquals(6, team.onCourt().size(), "six on court");
                VolleyballPlayer libero = team.libero();
                VolleyballPlayer server = match.currentServer();
                assertNotSame(libero, server, "the libero never serves");
                if (libero.isOnCourt()) {
                    liberoRallies[0]++;
                    assertFalse(rotation.isFrontRow(team.side, libero.id), "the libero is only in the back row");
                    VolleyballPlayer replaced = world.player(rotation.replacedByLibero(team.side));
                    assertEquals(PlayerRole.MIDDLE_BLOCKER, replaced.role);
                    assertFalse(replaced.isOnCourt());
                }
                for (VolleyballPlayer p : team.squad()) {
                    if (!p.isOnCourt()) assertFalse(CourtZones.BENCH_Z > p.position.z + 0.01f, "bench players wait at the bench");
                }
            }
        });
        assertTrue(liberoRallies[0] > rallies[0], "the liberos play most rallies: " + liberoRallies[0] + " of " + rallies[0] * 2);
    }

    @Test
    void serveIsReceivedByOutsideHittersAndTheLibero() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 6);
        MatchController match = new MatchController(world, SHORT, 6, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        List<BallTouch> receptions = new ArrayList<>();
        BallTouch[] previous = new BallTouch[1];
        world.ballHandling().addListener(t -> {
            if (previous[0] != null && previous[0].type() == ContactType.SERVE && t.side() != previous[0].side()) receptions.add(t);
            previous[0] = t;
        });
        playSet(world, match, List.of(), null);
        long byReceivers = receptions.stream().filter(t -> RolePlan.receivesServe(world.player(t.playerId()))).count();
        assertTrue(receptions.size() >= 10);
        // Short serves in front of the attackers are sometimes saved by them: that is allowed.
        assertTrue(byReceivers >= receptions.size() * 0.75, byReceivers + " of " + receptions.size());
    }

    @Test
    void receptionPassesRarelyEndInTheNet() {
        int passes = 0;
        int intoNet = 0;
        for (long seed = 1; seed <= 5; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 7);
            MatchController match = new MatchController(world, SHORT, seed, Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
            BallTouch[] previous = new BallTouch[1];
            boolean[] afterReception = new boolean[1];
            int[] counts = new int[2];
            world.ballHandling().addListener(t -> {
                afterReception[0] = previous[0] != null && previous[0].type() == ContactType.SERVE && t.side() != previous[0].side();
                if (afterReception[0]) counts[0]++;
                previous[0] = t;
            });
            world.addContactListener(c -> {
                if (afterReception[0] && (c.kind() == com.volleyball.game.physics.ColliderKind.NET_MESH
                        || c.kind() == com.volleyball.game.physics.ColliderKind.NET_BAND)) {
                    counts[1]++;
                    afterReception[0] = false;
                }
            });
            playSet(world, match, List.of(), null);
            passes += counts[0];
            intoNet += counts[1];
        }
        assertTrue(passes > 30);
        assertTrue(intoNet <= passes * 0.08f, intoNet + " of " + passes + " reception passes went into the net");
    }

    @Test
    void theAiLeavesABallComingDownAtAHumanToTheHuman() {
        int[] checked = new int[1];
        // A few sets until some balls have come down at the person (it depends on where the opponent serves).
        for (long seed = 9; seed < 12 && checked[0] == 0; seed++) {
            humanSet(seed, checked);
        }
        assertTrue(checked[0] > 0, "some balls came down at the human");
    }

    private void humanSet(long seed, int[] checked) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
        MatchController match = new MatchController(world, SHORT, seed, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        VolleyballPlayer human = world.home().players().get(4); // an outside hitter (receiver)
        TrajectoryPredictor.Prediction forecast = new TrajectoryPredictor.Prediction();
        match.start(CourtSide.AWAY);
        // Several minutes of play: the opponent serves at seams and weak receivers, not always at this one.
        for (int i = 0; i < 120 * 600 && match.phase() != MatchController.Phase.FINISHED; i++) {
            world.intent(human).clear(); // the person just stands there
            world.step(DT);
            match.update(DT, List.of(human));
            BallHandling handling = world.ballHandling();
            if (!world.ball().isActive() || handling.lastTouchSide() != CourtSide.AWAY || world.ball().velocity.x >= 0f) continue;
            forecast.watchHeight = 1f;
            world.predictor().predict(world.ball(), 6f, forecast);
            if (!forecast.reachesWatchHeight) continue;
            float dx = forecast.descentPoint.x - human.position.x;
            float dz = forecast.descentPoint.z - human.position.z;
            if (dx * dx + dz * dz > 1.0f || forecast.timeToDescent > 1.2f) continue;
            checked[0]++;
            for (VolleyballPlayer p : world.home().onCourt()) {
                if (p != human) assertFalse(handling.isArmed(p), "#" + p.number + " went for the human's ball: touch " + handling.lastToucherId()
                        + " teamTouches " + handling.teamTouches(CourtSide.HOME) + " speed " + world.ball().speed() + " t " + forecast.timeToDescent
                        + " d " + Math.sqrt(dx * dx + dz * dz) + " ballY " + world.ball().position.y + " p at " + p.position + " human at " + human.position
                        + " serve " + handling.serveType(world.player(handling.lastToucherId())));
            }
        }
    }

    @Test
    void thePersonFollowsTheLiberoReplacementAndIsAlwaysOnCourt() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 12);
        MatchController match = new MatchController(world, SHORT, 12, Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
        MatchSession session = MatchSession.create(SessionMode.SINGLE, world, PlayerRole.LIBERO);
        boolean[] playedLibero = new boolean[1];
        match.start(CourtSide.HOME);
        for (int i = 0; i < 120 * 60 * 6 && match.phase() != MatchController.Phase.FINISHED; i++) {
            world.step(DT);
            match.update(DT, session.humans());
            session.followReplacements(match.rules().rotation());
            if (match.phase() != MatchController.Phase.RALLY) continue;
            VolleyballPlayer p = session.slot(0).player();
            assertTrue(p.isOnCourt(), "the person always has a player on court");
            playedLibero[0] |= p.role == PlayerRole.LIBERO;
        }
        assertTrue(playedLibero[0], "the chosen role is played");
    }

    @Test
    void aReplacedMiddleIsPlayedThroughTheLiberoAndComesBack() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);
        VolleyballPlayer middle = world.home().players().get(5); // starts in zone 6
        VolleyballPlayer libero = world.home().libero();
        RotationManager rotation = new RotationManager(new int[]{0, 1, 2, 3, 4, 5}, new int[]{6, 7, 8, 9, 10, 11});
        MatchSession session = MatchSession.create(SessionMode.SINGLE, world, PlayerRole.MIDDLE_BLOCKER);
        session.assign(0, middle);
        rotation.replaceWithLibero(CourtSide.HOME, libero.id, middle.id);
        middle.setOnCourt(false);
        libero.setOnCourt(true);
        assertTrue(session.followReplacements(rotation));
        assertSame(libero, session.slot(0).player(), "the middle's person plays the libero");
        rotation.releaseLibero(CourtSide.HOME);
        middle.setOnCourt(true);
        libero.setOnCourt(false);
        session.followReplacements(rotation);
        assertSame(middle, session.slot(0).player(), "and gets the middle back");
    }

    @Test
    void theNextServerCanChooseTheServeBeforeGettingTheBall() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);
        BallHandling handling = world.ballHandling();
        VolleyballPlayer p = world.home().players().get(1);
        assertNotEquals(ContactPurpose.SERVE, handling.variantContext(p));
        handling.setNextServer(p);
        assertEquals(ContactPurpose.SERVE, handling.variantContext(p));
        ServeType before = handling.serveType(p);
        handling.cycleVariant(p, 1);
        assertEquals(before.next(), handling.serveType(p));
        handling.prepareServe(p, handling.serveType(p));
        assertEquals(before.next(), handling.serveType(p), "the chosen serve is kept when the ball arrives");
    }

    @Test
    void rolesHaveTheirZonesAfterTheSwitch() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);
        for (VolleyballPlayer p : world.home().squad()) {
            int front = RolePlan.roleZone(p, true);
            int back = RolePlan.roleZone(p, false);
            assertTrue(CourtZones.isFrontRow(front) && !CourtZones.isFrontRow(back));
        }
        assertEquals(4, RolePlan.roleZone(world.home().players().get(1), true), "outside hitter attacks on the left");
        assertEquals(1, RolePlan.roleZone(world.home().players().get(0), false), "the setter defends right back");
        assertTrue(RolePlan.receivesServe(world.home().libero()));
        assertFalse(RolePlan.receivesServe(world.home().players().get(0)), "the setter does not receive");
    }

    @Test
    void theLiberoDoesTheDirtyWorkAndTheSetterIsKeptForTheSecondTouch() {
        int[] receptions = new int[3]; // all, libero, setter
        int[] freeBalls = new int[2];  // all, setter
        for (long seed = 1; seed <= 15; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 3);
            MatchController match = new MatchController(world, SHORT, seed, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
            BallTouch[] previous = new BallTouch[1];
            world.ballHandling().addListener(t -> {
                BallTouch prev = previous[0];
                previous[0] = t;
                if (t.teamTouchNumber() != 1 || prev == null || t.type() == ContactType.BLOCK || t.type() == ContactType.SERVE) return;
                PlayerRole role = world.player(t.playerId()).role;
                if (prev.type() == ContactType.SERVE) {
                    receptions[0]++;
                    if (role == PlayerRole.LIBERO) receptions[1]++;
                    if (role == PlayerRole.SETTER) receptions[2]++;
                } else if (prev.type() != ContactType.ATTACK) {
                    freeBalls[0]++;
                    if (role == PlayerRole.SETTER) freeBalls[1]++;
                }
            });
            playSet(world, match, List.of(), null);
        }
        assertTrue(receptions[0] > 40);
        assertTrue(receptions[1] >= receptions[0] * 0.3f, "the libero passes a big share: " + receptions[1] + " of " + receptions[0]);
        assertTrue(receptions[2] <= receptions[0] * 0.04f, "the setter rarely receives: " + receptions[2] + " of " + receptions[0]);
        assertTrue(freeBalls[1] <= Math.max(2, freeBalls[0] * 0.3f), "free balls are not the setter's: " + freeBalls[1] + " of " + freeBalls[0]);
    }

    @Test
    void afterTheServeTheServerRunsIntoTheCourtToDefend() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 8);
        MatchController match = new MatchController(world, SHORT, 8, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        VolleyballPlayer[] server = {null};
        double[] servedAt = {-1};
        int[] checked = new int[2]; // serves checked, server back in the court in time
        world.ballHandling().addListener(t -> {
            if (t.type() == ContactType.SERVE) {
                server[0] = world.player(t.playerId());
                servedAt[0] = t.time();
            }
        });
        match.start(CourtSide.HOME);
        for (int i = 0; i < 120 * 300 && match.phase() != MatchController.Phase.FINISHED; i++) {
            world.step(DT);
            match.update(DT, List.of());
            // 1.6 s after the serve (while the rally goes on) the server is back inside the court.
            if (server[0] != null && servedAt[0] >= 0 && world.time() - servedAt[0] > 1.6 && match.phase() == MatchController.Phase.RALLY) {
                checked[0]++;
                if (Math.abs(server[0].position.x) < com.volleyball.game.world.CourtDimensions.HALF_LENGTH - 0.3f) checked[1]++;
                servedAt[0] = -1;
            }
        }
        assertTrue(checked[0] > 10, "serves checked: " + checked[0]);
        assertTrue(checked[1] >= checked[0] * 0.9f, "servers back in the court: " + checked[1] + " of " + checked[0]);
    }
}
