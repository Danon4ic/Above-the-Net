package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.SwingStyle;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The feint, the shot off the top of the block and the one-hand set. */
class FeintTopAndOneHandTest {
    private static ContactContext attack(Harness h, VolleyballPlayer hitter, AttackType type, Vector3 ball, Vector3 target) {
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
    void theFeintLooksLikeASpikeUntilTheContact() {
        assertEquals(SwingStyle.POWER, BallHandling.windUpFor(AttackType.FEINT), "the wind-up of a full swing");
        assertEquals(SwingStyle.TIP, BallHandling.swingFor(AttackType.FEINT), "a soft push at the contact");
        assertEquals(SwingStyle.TIP, BallHandling.windUpFor(AttackType.TIP), "a plain tip shows itself early");
    }

    @Test
    void theFeintIsASoftPushBehindTheBlockFlatterThanATip() {
        int behindBlock = 0;
        int n = 20;
        float feintApex = 0f;
        float tipApex = 0f;
        for (int i = 0; i < n; i++) {
            Harness h = new Harness();
            VolleyballPlayer hitter = h.home(1);
            Vector3 from = new Vector3(-0.5f, 3.1f, 0f);
            Vector3 to = new Vector3(2.8f, 0f, 0.5f);
            LaunchSolver.Solution feint = new LaunchSolver.Solution();
            new AttackMechanic(h.solver).attack(attack(h, hitter, AttackType.FEINT, from, to), new Random(i), feint);
            LaunchSolver.Solution tip = new LaunchSolver.Solution();
            new AttackMechanic(h.solver).attack(attack(h, hitter, AttackType.TIP, from, to), new Random(i), tip);
            assertTrue(feint.velocity.len() < 12f, "a soft ball: " + feint.velocity.len());
            feintApex += apex(h, from, feint);
            tipApex += apex(h, from, tip);
            h.world.ball().launch(from, feint.velocity, feint.spin, i);
            Vector3 landing = h.landing(3f);
            if (landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY && landing.x > 1.2f && landing.x < 5f) behindBlock++;
        }
        assertTrue(behindBlock >= n * 0.8f, "lands just behind the block: " + behindBlock + "/" + n);
        assertTrue(feintApex < tipApex, "flatter than a tip: " + feintApex / n + " vs " + tipApex / n);
    }

    private static float apex(Harness h, Vector3 from, LaunchSolver.Solution s) {
        TrajectoryPredictor.Prediction p = new TrajectoryPredictor.Prediction();
        h.world.predictor().predict(from, s.velocity, s.spin, 3f, p);
        return p.apex.y;
    }

    @Test
    void offTheTopGoesThroughTheBlockersFingertipsAndLong() {
        Harness h = new Harness();
        VolleyballPlayer hitter = h.home(3);
        VolleyballPlayer blocker = h.world.away().players().get(2);
        hitter.placeAt(-0.7f, 1.0f);
        blocker.placeAt(0.45f, 1.3f);
        blocker.setAirborne(true);
        Vector3 contact = new Vector3(-0.6f, 3.3f, 1.0f);
        h.world.ball().launch(contact, new Vector3(0f, -1f, 0f), Vector3.Zero, 1);
        Vector3 target = new Vector3(4f, 0f, 1f);
        h.handling.offTheTopTarget(hitter, target);
        assertEquals(CourtDimensions.HALF_LENGTH + BallHandling.OFF_THE_TOP_BEYOND, target.x, 1e-4f, "aimed well beyond the end line");
        assertTrue(target.z > 1.0f, "on the line from the contact through the block: " + target.z);

        hitter.attributes.attackPower = 80;
        LaunchSolver.Solution s = new LaunchSolver.Solution();
        ContactContext c = attack(h, hitter, AttackType.OFF_THE_TOP, contact, target);
        c.quality = 1f;
        new AttackMechanic(h.solver).attack(c, new Random(2), s);
        // Where the line crosses the blocker's plane: at the height of the hands, not down into the court.
        TrajectoryPredictor.Prediction p = new TrajectoryPredictor.Prediction();
        h.world.predictor().predict(contact, s.velocity, s.spin, 3f, p);
        float atBlock = Float.NaN;
        for (int i = 1; i < p.pathSize(); i++) {
            Vector3 a = p.path.get(i - 1);
            Vector3 b = p.path.get(i);
            if (a.x < blocker.position.x && b.x >= blocker.position.x) {
                atBlock = a.y + (b.y - a.y) * (blocker.position.x - a.x) / (b.x - a.x);
                break;
            }
        }
        assertTrue(atBlock > 2.9f && atBlock < 3.6f, "passes the block at fingertip height: " + atBlock);
        assertTrue(p.lands && p.landing.x > CourtDimensions.HALF_LENGTH, "untouched, it lands long: " + p.landing);
    }

    @Test
    void offTheTopWithoutABlockKeepsTheAimedSpot() {
        Harness h = new Harness();
        VolleyballPlayer hitter = h.home(3);
        hitter.placeAt(-0.7f, 1.0f);
        h.world.ball().launch(new Vector3(-0.6f, 3.3f, 1.0f), new Vector3(0f, -1f, 0f), Vector3.Zero, 1);
        Vector3 target = new Vector3(4f, 0f, 1f);
        h.handling.offTheTopTarget(hitter, target);
        assertEquals(4f, target.x, 1e-6f);
    }

    /** A setter on the second touch plays a ball arriving at the ideal point of {@code where}. */
    private static BallTouch setAt(long seed, ContactType where) {
        Harness h = new Harness(seed);
        VolleyballPlayer setter = h.home(0);
        setter.placeAt(-1.2f, 0.5f);
        h.feedArc(new Vector3(-6f, 1f, 0f), new Vector3(-1.2f, 2.2f, 0.5f), 4.5f, CourtSide.HOME, 1);
        return h.playWhenArriving(setter, where, ActionKind.PRIMARY, 0f);
    }

    @Test
    void aBallBeyondBothHandsIsSetWithOneHand() {
        BallTouch high = setAt(11, ContactType.ONE_HAND_SET);
        assertNotNull(high);
        assertEquals(ContactType.ONE_HAND_SET, high.type(), "out of reach of both hands, one hand still gets it");
        assertEquals(ContactPurpose.SET, high.purpose());
        assertTrue(high.quality() <= ContactZone.qualityCap(ContactType.ONE_HAND_SET) + 1e-6f);
        BallTouch normal = setAt(11, ContactType.OVERHEAD_PASS);
        assertNotNull(normal);
        assertEquals(ContactType.OVERHEAD_PASS, normal.type(), "within reach of both hands they are used");
    }

    @Test
    void aOneHandSetIsLessAccurateThanATwoHandSet() {
        float oneHand = 0f;
        float twoHands = 0f;
        int n = 0;
        for (long seed = 20; seed < 40; seed++) {
            BallTouch a = setAt(seed, ContactType.ONE_HAND_SET);
            BallTouch b = setAt(seed, ContactType.OVERHEAD_PASS);
            if (a == null || b == null || Float.isNaN(a.missDistance()) || Float.isNaN(b.missDistance())) continue;
            oneHand += a.missDistance();
            twoHands += b.missDistance();
            n++;
        }
        assertTrue(n >= 15, "sets made: " + n);
        assertTrue(oneHand > twoHands * 1.3f, "one hand misses more: " + oneHand / n + " m vs " + twoHands / n + " m");
    }
}
