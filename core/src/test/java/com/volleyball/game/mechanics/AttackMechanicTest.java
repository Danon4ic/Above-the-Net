package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttackMechanicTest {
    private static Harness setUpHighSet() {
        Harness h = new Harness();
        Vector3 spot = SetType.HIGH_OUTSIDE.target(CourtSide.HOME, new Vector3());
        h.feedArc(new Vector3(-1f, 2.45f, 1f), spot, SetType.HIGH_OUTSIDE.apex, CourtSide.HOME, 2);
        return h;
    }

    @Test
    void timedJumpSpikeLandsFastInTheOpponentCourt() {
        int kills = 0;
        int n = 10;
        for (int i = 0; i < n; i++) {
            Harness h = setUpHighSet();
            VolleyballPlayer hitter = h.home(1);
            hitter.attributes.attackPower = 85;
            hitter.attributes.attackAccuracy = 80;
            h.handling.setAim(hitter, 6f, -2f + i * 0.4f);
            BallTouch t = h.spikeWhenArriving(hitter, ActionKind.PRIMARY);
            assertNotNull(t, "hit the set");
            assertEquals(ContactType.ATTACK, t.type());
            assertTrue(t.airborne());
            assertTrue(t.outgoingVelocity().len() > 18f, "power spike speed " + t.outgoingVelocity().len());
            Vector3 landing = h.landing(3f);
            if (landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY
                    && CourtDimensions.isInsideCourt(landing.x, landing.z, 0.025f)) kills++;
        }
        assertTrue(kills >= n * 0.7, "well-timed spikes land in: " + kills + "/" + n);
    }

    @Test
    void tipIsSoftAndStillGetsOver() {
        Harness h = setUpHighSet();
        VolleyballPlayer hitter = h.home(1);
        h.handling.setAim(hitter, 2f, -2f);
        BallTouch t = h.spikeWhenArriving(hitter, ActionKind.SECONDARY);
        assertNotNull(t);
        assertTrue(t.variant().contains("Tip"));
        assertTrue(t.outgoingVelocity().len() < 10f);
        Vector3 landing = h.landing(3f);
        assertNotNull(landing);
        assertEquals(CourtSide.AWAY, CourtDimensions.sideOf(landing.x));
    }

    @Test
    void attackingFromBelowTheNetHeightCannotDriveTheBallDownIntoTheCourt() {
        // Contact at 2.1 m (below the 2.43 m net) aiming 2 m behind the net: the mechanic only
        // aims; physics decides, and the ball cannot both go down and clear the net.
        Harness h = new Harness();
        VolleyballPlayer hitter = h.home(1);
        hitter.attributes.attackPower = 90;
        ContactContext c = new ContactContext();
        c.player = hitter;
        c.type = ContactType.ATTACK;
        c.purpose = ContactPurpose.ATTACK;
        c.attackType = AttackType.POWER;
        c.quality = 1f;
        c.netTop = 2.43f;
        c.ballPosition.set(-0.6f, 2.1f, 0f);
        c.target.set(2f, 0f, 0f);
        com.volleyball.game.physics.LaunchSolver.Solution s = new com.volleyball.game.physics.LaunchSolver.Solution();
        new AttackMechanic(h.solver).attack(c, new java.util.Random(3), s);
        h.world.ball().launch(c.ballPosition, s.velocity, s.spin, 1);
        Vector3 landing = h.landing(3f);
        boolean netTouched = h.contacts.stream().anyMatch(k -> k.kind() == ColliderKind.NET_MESH || k.kind() == ColliderKind.NET_BAND);
        boolean landedIn = landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY
                && CourtDimensions.isInsideCourt(landing.x, landing.z, 0f);
        assertTrue(netTouched && !landedIn, "net touched=" + netTouched + ", landed in=" + landedIn);
    }
}
