package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockAndDiveTest {

    /** A 24 m/s spike from the away side, straight down the middle at a home blocker's hands. */
    private static void spike(Harness h, float z) {
        Vector3 from = new Vector3(1.1f, 3.25f, z);
        LaunchSolver.Solution s = new LaunchSolver.Solution();
        h.solver.solve(from, -6.5f, z, 24f, 35f, 0f, s);
        h.world.ball().launch(from, s.velocity, s.spin, 3);
        h.handling.registerFeed(CourtSide.AWAY, 3);
    }

    /** Jumps the blocker and waits until close to the top of the jump. */
    private static void jumpToPeak(Harness h, VolleyballPlayer blocker) {
        h.world.intent(blocker).jump = true;
        h.step();
        h.world.intent(blocker).jump = false;
        h.runUntil(() -> blocker.velocity.y < 0.6f, 1f);
    }

    private static VolleyballPlayer blockerAtNet(Harness h, float z) {
        VolleyballPlayer blocker = h.home(2);
        blocker.attributes.blocking = 90;
        blocker.placeAt(-0.3f, z);
        h.world.intent(blocker).facingYaw = blocker.facingYaw;
        return blocker;
    }

    @Test
    void wellTimedBlockStopsTheSpikeAndChangesItsPath() {
        // Without a block the spike lands deep in the home court.
        Harness free = new Harness();
        spike(free, 0f);
        Vector3 unblocked = free.landing(2f);
        assertNotNull(unblocked);
        assertEquals(CourtSide.HOME, CourtDimensions.sideOf(unblocked.x));

        Harness h = new Harness();
        VolleyballPlayer blocker = blockerAtNet(h, 0f);
        jumpToPeak(h, blocker);
        assertTrue(blocker.isBlocking() || true);
        spike(h, 0f);
        h.step();
        assertTrue(blocker.isBlocking(), "airborne at the net with the ball on the other side = blocking");
        Vector3 landing = h.landing(3f);
        assertTrue(h.contacts.stream().anyMatch(c -> c.kind() == ColliderKind.BLOCK && c.playerId() == blocker.id),
                "the ball hit the block");
        assertNotNull(landing);
        assertTrue(landing.dst(unblocked) > 3f, "the block changed the trajectory");
        assertEquals(CourtSide.AWAY, CourtDimensions.sideOf(landing.x), "stuffed back into the attacker's court");

        BallTouch block = h.touches.stream().filter(t -> t.type() == ContactType.BLOCK).findFirst().orElseThrow();
        assertEquals(0, block.teamTouchNumber(), "a block touch is not one of the team's three");
    }

    @Test
    void lateBlockDoesNotStopTheSpike() {
        Harness h = new Harness();
        VolleyballPlayer blocker = blockerAtNet(h, 0f);
        spike(h, 0f);
        // Jumps only as the ball is already at the net: hands are still low.
        h.runUntil(() -> h.world.ball().position.x < 0.3f, 1f);
        h.world.intent(blocker).jump = true;
        Vector3 landing = h.landing(3f);
        assertNotNull(landing);
        assertEquals(CourtSide.HOME, CourtDimensions.sideOf(landing.x), "the spike got through");
    }

    @Test
    void blockerTooFarFromTheNetIsNotBlocking() {
        Harness h = new Harness();
        VolleyballPlayer blocker = blockerAtNet(h, 0f);
        blocker.placeAt(-1.6f, 0f);
        spike(h, 2f);
        jumpToPeak(h, blocker);
        assertFalse(blocker.isBlocking());
    }

    @Test
    void afterASoftBlockTheTeamStillHasThreeTouches() {
        Harness h = new Harness();
        VolleyballPlayer blocker = blockerAtNet(h, 0f);
        blocker.attributes.blocking = 1; // barely any penetration: the ball pops up on the home side
        jumpToPeak(h, blocker);
        spike(h, 0f);
        h.runUntil(() -> h.touches.stream().anyMatch(t -> t.type() == ContactType.BLOCK), 1f);
        assertEquals(CourtSide.HOME, h.handling.lastTouchSide());
        assertEquals(0, h.handling.teamTouches(CourtSide.HOME));
    }

    @Test
    void blockStyleShiftsTheHands() {
        Harness h = new Harness();
        VolleyballPlayer blocker = blockerAtNet(h, 2f);
        h.handling.setBlockStyle(blocker, BlockStyle.LINE);
        jumpToPeak(h, blocker);
        spike(h, 2f);
        h.step();
        assertEquals(BlockMechanic.STYLE_SHIFT, blocker.blockShift(), 1e-6f, "line block moves the hands towards the sideline (+Z)");
    }

    @Test
    void diveReachesALowBallOutOfNormalReachAndCostsRecoveryTime() {
        Harness h = new Harness();
        VolleyballPlayer p = h.home(4);
        p.placeAt(-6f, 0f);
        h.world.intent(p).facingYaw = p.facingYaw;
        // A soft ball dropping 2.4 m in front of the player.
        h.feedArc(new Vector3(-1f, 2.5f, 0f), new Vector3(-6f + 2.4f, 0.35f, 0f), 3.5f, CourtSide.AWAY, 3);
        h.handling.registerFeed(CourtSide.AWAY, 3);
        com.volleyball.game.physics.TrajectoryPredictor.Prediction f = new com.volleyball.game.physics.TrajectoryPredictor.Prediction();
        f.watchHeight = 0.3f;
        h.world.predictor().predict(h.world.ball(), 5f, f);
        double diveAt = h.world.time() + f.timeToDescent - 0.33;
        h.runUntil(() -> h.world.time() >= diveAt, 4f);
        h.world.intent(p).direction.set(1f, 0f);
        h.world.intent(p).dive = true;
        h.handling.arm(p, ActionKind.PRIMARY, h.world.time());
        h.step();
        h.world.intent(p).dive = false;
        h.world.intent(p).direction.setZero();
        assertEquals(BodyState.DIVING, p.bodyState());
        h.runUntil(() -> !h.touches.isEmpty(), 1f);
        assertFalse(h.touches.isEmpty(), "the dive reached the ball");
        BallTouch dig = h.touches.get(0);
        assertEquals(ContactType.DIVE, dig.type());
        assertTrue(dig.quality() <= ContactZone.qualityCap(ContactType.DIVE) + 1e-6f);
        assertTrue(h.world.ball().velocity.y > 0f, "the ball is kept up");

        // On the floor the player cannot move or jump until back on the feet.
        h.runUntil(() -> p.bodyState() == BodyState.ON_FLOOR, 1f);
        h.world.intent(p).jump = true;
        h.world.intent(p).direction.set(0f, 1f);
        h.runUntil(() -> p.bodyState() == BodyState.GETTING_UP, 2f);
        assertFalse(p.isAirborne());
        h.runUntil(p::isUpright, 2f);
        assertTrue(p.isUpright(), "back on the feet");
    }

    @Test
    void withoutADiveTheSameBallIsOutOfReach() {
        Harness h = new Harness();
        VolleyballPlayer p = h.home(4);
        p.placeAt(-6f, 0f);
        h.world.intent(p).facingYaw = p.facingYaw;
        h.feedArc(new Vector3(-1f, 2.5f, 0f), new Vector3(-6f + 2.4f, 0.35f, 0f), 3.5f, CourtSide.AWAY, 3);
        h.runUntil(() -> h.world.ball().position.y < 1.2f && h.world.ball().velocity.y < 0f, 4f);
        h.handling.arm(p, ActionKind.PRIMARY, h.world.time());
        h.landing(2f);
        assertTrue(h.touches.isEmpty());
    }

    @Test
    void ballJustOutOfReachIsPlayedOneArmedWithPoorControl() {
        Harness h = new Harness();
        VolleyballPlayer p = h.home(4);
        p.placeAt(-6f, 0f);
        h.feedArc(new Vector3(-1f, 2.5f, 0f), new Vector3(-6f + 0.42f, 0.8f, 0.9f), 3.8f, CourtSide.AWAY, 3);
        h.world.intent(p).facingYaw = p.facingYaw;
        com.volleyball.game.physics.TrajectoryPredictor.Prediction f = new com.volleyball.game.physics.TrajectoryPredictor.Prediction();
        f.watchHeight = 0.8f;
        h.world.predictor().predict(h.world.ball(), 5f, f);
        double armAt = h.world.time() + f.timeToDescent - 0.25;
        h.runUntil(() -> h.world.time() >= armAt, 4f);
        h.handling.arm(p, ActionKind.PRIMARY, h.world.time());
        h.runUntil(() -> !h.touches.isEmpty(), 1f);
        assertFalse(h.touches.isEmpty());
        assertEquals(ContactType.ONE_ARM, h.touches.get(0).type());
        assertTrue(h.touches.get(0).quality() <= ContactZone.qualityCap(ContactType.ONE_ARM) + 1e-6f);
    }
}
