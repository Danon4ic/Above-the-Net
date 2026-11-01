package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The shots added with the play caller (dump, cut, tool), the overhead reception and the selection of variants. */
class ShotsAndSelectionTest {
    private static ContactContext attackContext(Harness h, VolleyballPlayer hitter, AttackType type, Vector3 ball, Vector3 target) {
        ContactContext c = new ContactContext();
        c.player = hitter;
        c.type = ContactType.ATTACK;
        c.purpose = ContactPurpose.ATTACK;
        c.attackType = type;
        c.quality = 0.8f;
        c.netTop = h.world.court().net().topHeight();
        c.ballPosition.set(ball);
        c.target.set(target);
        return c;
    }

    @Test
    void aSetterDumpFromJustAboveTheTapeGetsOverAndLandsShort() {
        // A setter jumping at the net contacts the second ball at 2.6 m, half a metre off the net,
        // and dumps it 1.5 m behind the net: the old flat solution went into the net.
        int over = 0;
        int n = 20;
        for (int i = 0; i < n; i++) {
            Harness h = new Harness();
            VolleyballPlayer setter = h.home(0);
            ContactContext c = attackContext(h, setter, AttackType.TIP, new Vector3(-0.5f, 2.6f, 1f), new Vector3(1.5f, 0f, 0.5f));
            LaunchSolver.Solution s = new LaunchSolver.Solution();
            new AttackMechanic(h.solver).attack(c, new Random(i), s);
            h.world.ball().launch(c.ballPosition, s.velocity, s.spin, i);
            Vector3 landing = h.landing(3f);
            if (landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY && landing.x < 4f) over++;
        }
        assertTrue(over >= n * 0.9f, "dumps over the net: " + over + "/" + n);
    }

    @Test
    void theCutShotBendsTowardsTheAngleAndStaysFast() {
        Harness h = new Harness();
        VolleyballPlayer hitter = h.home(1);
        hitter.attributes.attackPower = 80;
        ContactContext c = attackContext(h, hitter, AttackType.CUT, new Vector3(-0.6f, 3.2f, -3f), new Vector3(3.5f, 0f, 4f));
        c.quality = 1f;
        LaunchSolver.Solution s = new LaunchSolver.Solution();
        new AttackMechanic(h.solver).attack(c, new Random(1), s);
        assertTrue(s.velocity.len() > 15f, "a cut is still a hard shot: " + s.velocity.len());
        assertTrue(Math.abs(s.spin.y) > 3f, "with sidespin");
    }

    @Test
    void aBallAtShoulderHeightIsReceivedOverheadInsteadOfHittingTheBody() {
        Harness h = new Harness();
        VolleyballPlayer receiver = h.home(4);
        receiver.placeAt(-6f, 0f);
        // A flat ball arriving at about 1.9 m: above the forearms' reach.
        Vector3 ideal = ContactZone.idealPoint(receiver, ContactType.OVERHEAD_PASS, null, new Vector3());
        h.feedArc(new Vector3(4f, 3.2f, ideal.z), new Vector3(ideal.x, ideal.y - 0.15f, ideal.z), 3.6f, CourtSide.AWAY, 1);
        h.world.intent(receiver).facingYaw = receiver.facingYaw;
        h.handling.arm(receiver, ActionKind.PRIMARY, h.world.time());
        int before = h.touches.size();
        h.runUntil(() -> {
            if (h.world.ball().position.x < ideal.x + 1.2f && !h.handling.isArmed(receiver) && h.touches.size() == before) {
                h.handling.arm(receiver, ActionKind.PRIMARY, h.world.time());
            }
            return h.touches.size() > before;
        }, 3f);
        assertTrue(h.touches.size() > before, "the ball was played");
        assertEquals(ContactType.OVERHEAD_PASS, h.touches.get(h.touches.size() - 1).type());
    }

    @Test
    void numberKeysChooseVariantsAndZeroLeavesItToTheGame() {
        Harness h = new Harness();
        VolleyballPlayer setter = h.home(0);
        setter.placeAt(-1f, 1f);
        // A setter's context is the set.
        assertEquals(ContactPurpose.SET, h.handling.variantContext(setter));
        assertTrue(h.handling.isAutoSet(setter), "Auto by default");
        h.handling.selectVariant(setter, SetType.SLIDE.ordinal() + 1);
        assertFalse(h.handling.isAutoSet(setter));
        assertEquals(SetType.SLIDE, h.handling.setType(setter));
        h.handling.selectVariant(setter, 0);
        assertTrue(h.handling.isAutoSet(setter));
        // Q / E steps through Auto and every set, and wraps around.
        for (int i = 0; i <= SetType.values().length; i++) h.handling.cycleVariant(setter, 1);
        assertTrue(h.handling.isAutoSet(setter), "a full cycle comes back to Auto");
        h.handling.selectVariant(setter, 42); // out of range: ignored
        assertTrue(h.handling.isAutoSet(setter));
    }
}
