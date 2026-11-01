package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.SwingStyle;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Back-row attacks clear the net, the person's bigger reach, left-handers, the swing styles, net-cord balls. */
class BackRowAssistAndHandsTest {
    private static ContactContext attack(VolleyballPlayer hitter, float x, float y, float targetX, AttackType type) {
        ContactContext c = new ContactContext();
        c.player = hitter;
        c.type = ContactType.ATTACK;
        c.purpose = ContactPurpose.ATTACK;
        c.attackType = type;
        c.quality = 0.9f;
        c.netTop = 2.43f;
        c.ballPosition.set(x, y, 0f);
        c.target.set(targetX, 0f, 0.5f);
        return c;
    }

    @Test
    void aPipeHitHardAtAShortSpotStillGetsOverTheNet() {
        int intoNet = 0;
        for (int i = 0; i < 12; i++) {
            Harness h = new Harness(100 + i);
            VolleyballPlayer hitter = h.home(4);
            hitter.attributes.attackPower = 85;
            ContactContext c = attack(hitter, -3.7f, 3.15f, 2.5f, i % 2 == 0 ? AttackType.POWER : AttackType.CUT);
            LaunchSolver.Solution s = new LaunchSolver.Solution();
            new AttackMechanic(h.solver).attack(c, new java.util.Random(i), s);
            h.world.ball().launch(c.ballPosition, s.velocity, s.spin, 1);
            h.landing(3f);
            if (h.contacts.stream().anyMatch(k -> k.kind() == ColliderKind.NET_MESH || k.kind() == ColliderKind.NET_BAND)) intoNet++;
        }
        assertTrue(intoNet <= 1, "back-row attacks into the net: " + intoNet + " of 12");
    }

    @Test
    void thePersonsPlayerReachesABallTheComputerWouldMiss() {
        // The same feed, the receiver 1.25 m off to the side: out of the normal reach (even one-armed), within the person's.
        BallTouch normal = pass(false);
        BallTouch assisted = pass(true);
        assertNull(normal, "a computer player that far off misses it");
        assertNotNull(assisted, "the person's player gets it");
    }

    private static BallTouch pass(boolean assisted) {
        Harness h = new Harness(7);
        VolleyballPlayer p = h.home(4);
        p.placeAt(-6f, 0f);
        h.feedArc(new Vector3(6f, 2.5f, 0f), new Vector3(-6f, 0.85f, 0f), 5f, CourtSide.AWAY, 1);
        if (assisted) h.handling.setAssisted(p);
        return h.playWhenArriving(p, ContactType.FOREARM_PASS, ActionKind.PRIMARY, 1.25f);
    }

    @Test
    void aLeftHanderMeetsTheBallOnTheLeft() {
        Harness h = new Harness(3);
        VolleyballPlayer p = h.home(3);
        p.placeAt(-1f, 0f);
        p.facingYaw = VolleyballPlayer.facingNetYaw(CourtSide.HOME); // facing +x: the right hand is towards +z
        p.setLeftHanded(false);
        Vector3 right = ContactZone.idealPoint(p, ContactType.ATTACK, null, new Vector3());
        p.setLeftHanded(true);
        Vector3 left = ContactZone.idealPoint(p, ContactType.ATTACK, null, new Vector3());
        assertTrue(right.z > p.position.z && left.z < p.position.z, "right " + right.z + ", left " + left.z);
        assertTrue(p.traitLabel().startsWith("Left-handed"));
    }

    @Test
    void theSwingFollowsTheShot() {
        assertEquals(SwingStyle.CUT, BallHandling.swingFor(AttackType.CUT));
        assertEquals(SwingStyle.TIP, BallHandling.swingFor(AttackType.TIP));
        assertEquals(SwingStyle.FLOAT_SERVE, BallHandling.swingFor(ServeType.FLOAT));
        assertEquals(SwingStyle.TOPSPIN_SERVE, BallHandling.swingFor(ServeType.JUMP_TOPSPIN));
        assertEquals(SwingStyle.UNDERHAND_SERVE, BallHandling.swingFor(ServeType.UNDERHAND));
        // A real contact sets it on the hitter.
        Harness h = new Harness(11);
        Vector3 spot = SetType.HIGH_OUTSIDE.target(CourtSide.HOME, new Vector3());
        h.feedArc(new Vector3(-1f, 2.45f, 1f), spot, SetType.HIGH_OUTSIDE.apex, CourtSide.HOME, 2);
        VolleyballPlayer hitter = h.home(1);
        h.handling.setAttackType(hitter, AttackType.ROLL_SHOT);
        h.handling.setAim(hitter, 6f, 0f);
        assertNotNull(h.spikeWhenArriving(hitter, ActionKind.PRIMARY));
        assertEquals(SwingStyle.ROLL, hitter.swingStyle());
    }

    @Test
    void aBallThatClipsTheTapeCanStillFallOnTheOtherSide() {
        // Shots grazing the top of the net from just above the tape: some roll over.
        boolean overAfterTouch = false;
        for (int i = 0; i < 30 && !overAfterTouch; i++) {
            Harness h = new Harness(200 + i);
            float y = 2.43f + 0.02f + i * 0.006f;
            h.world.ball().launch(new Vector3(-1.2f, y + 0.25f, 0f), new Vector3(5.5f, -0.6f, 0f), new Vector3(), 1);
            Vector3 landing = h.landing(3f);
            boolean touched = h.contacts.stream().anyMatch(k -> k.kind() == ColliderKind.NET_BAND);
            overAfterTouch = touched && landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY;
        }
        assertTrue(overAfterTouch, "a net cord ball that dribbles over");
    }

    @Test
    void aSetterInTheAirSetsTheSecondBallWithThePrimaryAction() {
        Harness h = new Harness(41);
        VolleyballPlayer setter = h.home(0); // the setter
        assertEquals(com.volleyball.game.entities.PlayerRole.SETTER, setter.role);
        setter.placeAt(-0.8f, 0.5f);
        setter.facingYaw = VolleyballPlayer.facingNetYaw(CourtSide.HOME);
        // A pass from the back court, as the team's first touch.
        h.feedArc(new Vector3(-6f, 1f, 0f), new Vector3(-0.6f, 2.6f, 0.5f), 4.6f, CourtSide.HOME, 1);
        float jump = com.volleyball.game.physics.PlayerLocomotion.jumpHeight(setter.attributes);
        Vector3 ideal = ContactZone.idealPoint(setter, ContactType.OVERHEAD_PASS, null, new Vector3());
        com.volleyball.game.physics.TrajectoryPredictor.Prediction f = new com.volleyball.game.physics.TrajectoryPredictor.Prediction();
        f.watchHeight = ideal.y + jump * 0.9f;
        h.world.predictor().predict(h.world.ball(), 6f, f);
        assertTrue(f.reachesWatchHeight);
        setter.position.set(f.descentPoint.x - (ideal.x - setter.position.x), 0f, f.descentPoint.z - (ideal.z - setter.position.z));
        setter.previousPosition.set(setter.position);
        h.world.intent(setter).facingYaw = setter.facingYaw;
        float rise = (float) Math.sqrt(2f * jump / com.volleyball.game.physics.PhysicsConstants.GRAVITY);
        double jumpAt = h.world.time() + f.timeToDescent - rise;
        h.runUntil(() -> h.world.time() >= jumpAt, 6f);
        h.world.intent(setter).jump = true;
        h.step();
        h.world.intent(setter).jump = false;
        double armAt = h.world.time() + rise - 0.2;
        int before = h.touches.size();
        h.runUntil(() -> h.world.time() >= armAt, 2f);
        assertTrue(setter.isAirborne());
        h.handling.arm(setter, ActionKind.PRIMARY, h.world.time());
        h.runUntil(() -> h.touches.size() > before, 1f);
        assertTrue(h.touches.size() > before, "the jump set happened");
        BallTouch t = h.touches.get(h.touches.size() - 1);
        assertEquals(ContactType.OVERHEAD_PASS, t.type(), "a set, not an attack");
        assertEquals(ContactPurpose.SET, t.purpose());
    }
}
