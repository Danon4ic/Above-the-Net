package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServeMechanicTest {

    /** Serves once; {@code lateBy} delays the hit past the ideal moment (seconds). Returns the touch. */
    private static BallTouch serve(Harness h, VolleyballPlayer server, ServeType type, float lateBy) {
        server.placeAt(-10f, 0.5f);
        h.handling.setAim(server, 6f, 0f);
        h.handling.prepareServe(server, type);
        h.handling.arm(server, ActionKind.PRIMARY, h.world.time()); // toss
        assertEquals(BallHandling.ServePhase.TOSSED, h.handling.servePhase());
        Vector3 ideal = new Vector3();
        // Wait until the falling ball is at (or past) the ideal contact height.
        h.runUntil(() -> {
            ContactZone.idealPoint(server, ContactType.SERVE, type, ideal);
            return h.world.ball().velocity.y < 0f && h.world.ball().position.y <= ideal.y + 0.25f;
        }, 3f);
        double hitAt = h.world.time() + lateBy;
        h.runUntil(() -> h.world.time() >= hitAt, 2f);
        int before = h.touches.size();
        h.handling.arm(server, ActionKind.PRIMARY, h.world.time());
        h.runUntil(() -> h.touches.size() > before, 1f);
        return h.touches.size() > before ? h.touches.get(h.touches.size() - 1) : null;
    }

    @Test
    void tossThenHitServesTheBallIntoTheOpponentCourt() {
        int in = 0;
        int attempts = 12;
        for (int i = 0; i < attempts; i++) {
            Harness h = new Harness();
            VolleyballPlayer server = h.home(0);
            server.attributes.serving = 90;
            BallTouch touch = serve(h, server, ServeType.FLOAT, 0f);
            assertNotNull(touch, "the serve was hit");
            assertEquals(ContactType.SERVE, touch.type());
            assertEquals(ContactPurpose.SERVE, touch.purpose());
            Vector3 landing = h.landing(4f);
            if (landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY
                    && CourtDimensions.isInsideCourt(landing.x, landing.z, 0.025f)) in++;
        }
        assertTrue(in >= attempts * 0.8, "a skilled server keeps most serves in: " + in + "/" + attempts);
    }

    @Test
    void lateHitLowersQuality() {
        // Same toss (same seed), hit on time vs 0.12 s late.
        float good = 0f;
        float poor = 0f;
        for (long seed = 100; seed < 106; seed++) {
            Harness onTime = new Harness(seed);
            BallTouch a = serve(onTime, onTime.home(0), ServeType.FLOAT, 0f);
            Harness late = new Harness(seed);
            BallTouch b = serve(late, late.home(0), ServeType.FLOAT, 0.12f);
            assertNotNull(a);
            assertNotNull(b);
            good += a.quality();
            poor += b.quality();
        }
        assertTrue(good > poor + 0.6f, "on time " + good / 6 + " vs late " + poor / 6);
        assertTrue(good / 6 > 0.7f, "a well-timed serve is a clean contact: " + good / 6);
    }

    @Test
    void missingTheBallMeansNoServe() {
        Harness h = new Harness();
        VolleyballPlayer server = h.home(0);
        server.placeAt(-10f, 0f);
        h.handling.prepareServe(server, ServeType.FLOAT);
        h.handling.arm(server, ActionKind.PRIMARY, h.world.time());
        // Never swing: the toss just falls.
        h.runUntil(() -> false, 2.5f);
        assertTrue(h.touches.isEmpty());
        assertTrue(h.contacts.stream().anyMatch(c -> c.kind() == com.volleyball.game.physics.ColliderKind.FLOOR
                || c.kind() == com.volleyball.game.physics.ColliderKind.PLAYER_BODY), "the toss fell down");
    }

    @Test
    void fasterServeTypesAreFasterAndFloatHasNoSpin() {
        PlayerAttributes a = new PlayerAttributes();
        assertTrue(ServeMechanic.speed(ServeType.JUMP_TOPSPIN, a) > ServeMechanic.speed(ServeType.POWER, a));
        assertTrue(ServeMechanic.speed(ServeType.POWER, a) > ServeMechanic.speed(ServeType.FLOAT, a));
        assertTrue(ServeMechanic.speed(ServeType.FLOAT, a) > ServeMechanic.speed(ServeType.UNDERHAND, a));

        Harness h = new Harness();
        BallTouch power = serve(h, h.home(0), ServeType.POWER, 0f);
        assertTrue(h.world.ball().spinRevolutionsPerSecond() > 2f, "power serve spins");
        Harness f = new Harness();
        serve(f, f.home(0), ServeType.FLOAT, 0f);
        assertTrue(f.world.ball().spinRevolutionsPerSecond() < 0.6f, "float barely spins");
        assertTrue(power.outgoingVelocity().len() > f.touches.get(0).outgoingVelocity().len());
    }

    @Test
    void underhandServeIsHitAtHipHeight() {
        Harness h = new Harness();
        BallTouch t = serve(h, h.home(0), ServeType.UNDERHAND, 0f);
        assertNotNull(t);
        assertTrue(t.contactPoint().y < 1.2f, "contact at " + t.contactPoint().y);
    }
}
