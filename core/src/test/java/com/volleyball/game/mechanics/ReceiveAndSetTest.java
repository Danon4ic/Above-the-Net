package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReceiveAndSetTest {

    @Test
    void wellPlacedReceiverPassesToTheSetter() {
        int playable = 0;
        int attempts = 10;
        for (int i = 0; i < attempts; i++) {
            Harness h = new Harness();
            VolleyballPlayer receiver = h.home(4);
            receiver.attributes.receiving = 85;
            // A little topspin: this is about the pass, not about reading a float.
            h.machineServe(-6f, 1.5f - i * 0.3f, 16f, 10f);
            BallTouch t = h.playWhenArriving(receiver, ContactType.FOREARM_PASS, ActionKind.PRIMARY, 0f);
            assertNotNull(t, "receiver touched the serve");
            assertEquals(ContactType.FOREARM_PASS, t.type());
            assertEquals(ContactPurpose.PASS, t.purpose());
            assertEquals(1, t.teamTouchNumber());
            if (t.rating().isPlayable()) playable++;
        }
        assertTrue(playable >= attempts * 0.7, "good receiver: " + playable + "/" + attempts + " playable passes");
    }

    @Test
    void poorFootworkAndHardServesMakeWorsePasses() {
        float good = 0f;
        float misplaced = 0f;
        float hard = 0f;
        int n = 12;
        for (int i = 0; i < n; i++) {
            Harness a = new Harness();
            a.machineServe(-6f, 1f, 14f, 0f);
            good += a.playWhenArriving(a.home(4), ContactType.FOREARM_PASS, ActionKind.PRIMARY, 0f).missDistance();

            Harness b = new Harness();
            b.machineServe(-6f, 1f, 14f, 0f);
            BallTouch t = b.playWhenArriving(b.home(4), ContactType.FOREARM_PASS, ActionKind.PRIMARY, 0.45f);
            misplaced += t == null ? 10f : t.missDistance();

            Harness c = new Harness();
            c.machineServe(-6f, 1f, 26f, 40f);
            hard += c.playWhenArriving(c.home(4), ContactType.FOREARM_PASS, ActionKind.PRIMARY, 0f).missDistance();
        }
        assertTrue(misplaced > good * 1.3f, "misplaced " + misplaced / n + " vs good " + good / n);
        assertTrue(hard > good * 1.3f, "hard serve " + hard / n + " vs soft " + good / n);
    }

    @Test
    void withoutARequestTheBallJustHitsTheBody() {
        Harness h = new Harness();
        VolleyballPlayer p = h.home(4);
        p.placeAt(-6f, 0f);
        h.world.ball().launch(new Vector3(-6f + 0.05f, 4f, 0f), Vector3.Zero, Vector3.Zero, 1);
        h.runUntil(() -> false, 1.5f);
        assertTrue(h.touches.isEmpty(), "no deliberate touch without a request");
        assertTrue(h.contacts.stream().anyMatch(c -> c.kind() == ColliderKind.PLAYER_BODY && c.playerId() == p.id));
    }

    @Test
    void requestArmedTooEarlyExpires() {
        Harness h = new Harness();
        VolleyballPlayer p = h.home(4);
        h.machineServe(-6f, 0f, 15f, 0f);
        p.placeAt(-6.4f, 0f);
        h.handling.arm(p, ActionKind.PRIMARY, h.world.time()); // ball still on the other side
        h.runUntil(() -> false, 2f);
        assertTrue(h.touches.isEmpty());
    }

    @Test
    void setterSetsTheSelectedSetAndPassQualityCarriesOver() {
        float cleanMiss = 0f;
        float chasedMiss = 0f;
        int n = 10;
        for (int i = 0; i < n; i++) {
            Harness h = new Harness();
            VolleyballPlayer setter = h.home(0);
            h.handling.setSetType(setter, SetType.HIGH_OUTSIDE);
            h.feedArc(new Vector3(-6f, 1f, 0f), TacticalTargets.setterTarget(CourtSide.HOME, new Vector3()), 4.8f, CourtSide.HOME, 1);
            BallTouch set = h.playWhenArriving(setter, ContactType.OVERHEAD_PASS, ActionKind.SECONDARY, 0f);
            assertNotNull(set);
            assertEquals(ContactPurpose.SET, set.purpose());
            assertEquals(ContactType.OVERHEAD_PASS, set.type());
            assertEquals(2, set.teamTouchNumber());
            cleanMiss += set.missDistance();

            // A poor pass: the setter has to run and plays the ball on the move, off the ideal spot.
            Harness b = new Harness();
            VolleyballPlayer chaser = b.home(0);
            b.feedArc(new Vector3(-6f, 1f, 0f), new Vector3(-2.5f, 2.45f, -1.5f), 4.2f, CourtSide.HOME, 1);
            chaser.velocity.set(3.5f, 0f, 0f);
            BallTouch chased = b.playWhenArriving(chaser, ContactType.OVERHEAD_PASS, ActionKind.SECONDARY, 0.3f);
            chasedMiss += chased == null ? 10f : chased.missDistance();
        }
        assertTrue(cleanMiss / n < 1.3f, "clean sets arrive near the hitter: " + cleanMiss / n);
        assertTrue(chasedMiss > cleanMiss * 1.3f, "sets after a poor pass are worse: " + chasedMiss / n + " vs " + cleanMiss / n);
    }

    @Test
    void thirdTouchOnTheGroundGoesOverTheNet() {
        Harness h = new Harness();
        VolleyballPlayer p = h.home(1);
        h.handling.setAim(p, 6f, 0f);
        h.feedArc(new Vector3(-1f, 2.4f, 1f), new Vector3(-3f, 1.0f, -2f), 4.5f, CourtSide.HOME, 2);
        BallTouch t = h.playWhenArriving(p, ContactType.FOREARM_PASS, ActionKind.PRIMARY, 0f);
        assertNotNull(t);
        assertEquals(ContactPurpose.SEND_OVER, t.purpose());
        assertEquals(3, t.teamTouchNumber());
        Vector3 landing = h.landing(5f);
        assertNotNull(landing);
        assertEquals(CourtSide.AWAY, com.volleyball.game.world.CourtDimensions.sideOf(landing.x));
    }
}
