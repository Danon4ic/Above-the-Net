package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServeSpinTest {

    /** The served ball's flight: positions from the hit until it lands (or 4 s). */
    private static List<Vector3> serve(long seed, ServeSpin spin) {
        Harness h = new Harness(seed);
        VolleyballPlayer server = h.home(0);
        server.attributes.serving = 90;
        server.placeAt(-10f, 0.5f);
        h.handling.setAim(server, 6f, 0f);
        h.handling.prepareServe(server, ServeType.POWER);
        h.handling.setServeSpin(server, spin);
        h.handling.arm(server, ActionKind.PRIMARY, h.world.time());
        Vector3 ideal = new Vector3();
        h.runUntil(() -> {
            ContactZone.idealPoint(server, ContactType.SERVE, ServeType.POWER, ideal);
            return h.world.ball().velocity.y < 0f && h.world.ball().position.y <= ideal.y + 0.25f;
        }, 3f);
        h.handling.arm(server, ActionKind.PRIMARY, h.world.time());
        assertTrue(h.runUntil(() -> !h.touches.isEmpty(), 1f), "the serve was hit");
        List<Vector3> path = new ArrayList<>();
        h.runUntil(() -> {
            path.add(new Vector3(h.world.ball().position));
            return h.world.ball().position.y < 0.15f;
        }, 4f);
        return path;
    }

    /** Signed sideways bulge (m, + = towards +Z) of the path from the straight line between its ends. */
    private static float bulge(List<Vector3> path) {
        Vector3 a = path.get(0);
        Vector3 b = path.get(path.size() - 1);
        Vector3 mid = path.get(path.size() / 2);
        float t = (mid.x - a.x) / (b.x - a.x);
        return mid.z - (a.z + (b.z - a.z) * t);
    }

    private static boolean landsIn(List<Vector3> path) {
        Vector3 end = path.get(path.size() - 1);
        return CourtDimensions.sideOf(end.x) == CourtSide.AWAY && CourtDimensions.isInsideCourt(end.x, end.z, 0.1f);
    }

    @Test
    void curvingServesBendBothWaysAndStillLandIn() {
        float straight = 0f;
        float left = 0f;
        float right = 0f;
        int in = 0;
        int seeds = 6;
        for (long seed = 200; seed < 200 + seeds; seed++) {
            List<Vector3> s = serve(seed, ServeSpin.TOPSPIN);
            List<Vector3> l = serve(seed, ServeSpin.CURVE_LEFT);
            List<Vector3> r = serve(seed, ServeSpin.CURVE_RIGHT);
            straight += Math.abs(bulge(s));
            left += bulge(l);
            right += bulge(r);
            if (landsIn(l)) in++;
            if (landsIn(r)) in++;
        }
        straight /= seeds;
        left /= seeds;
        right /= seeds;
        // Serving towards +X, "left" is -Z: the ball curving left is launched to the right of the
        // aim, so its path bows out to the right (+Z) of the straight line, and the other way round.
        assertTrue(left > 0.15f, "curve left bows out to the right: " + left);
        assertTrue(right < -0.15f, "curve right bows out to the left: " + right);
        assertTrue(straight < 0.1f, "a topspin serve flies straight: " + straight);
        assertTrue(in >= 2 * seeds * 0.75f, "a good server keeps curving serves in: " + in + "/" + 2 * seeds);
    }

    @Test
    void onlyPowerServesCurve() {
        assertTrue(ServeSpin.applies(ServeType.POWER));
        assertTrue(ServeSpin.applies(ServeType.JUMP_TOPSPIN));
        assertFalse(ServeSpin.applies(ServeType.FLOAT));
        assertFalse(ServeSpin.applies(ServeType.JUMP_FLOAT));
        assertFalse(ServeSpin.applies(ServeType.UNDERHAND));
    }

    @Test
    void spinKeysCurveThePowerServeAndPressedAgainStraightenIt() {
        Harness h = new Harness(5);
        VolleyballPlayer server = h.home(0);
        h.handling.prepareServe(server, ServeType.FLOAT);
        h.handling.toggleServeSpin(server, ServeSpin.CURVE_LEFT);
        assertEquals(ServeSpin.TOPSPIN, h.handling.serveSpin(server), "a float serve takes no spin choice");
        h.handling.selectVariant(server, ServeType.POWER.ordinal() + 1);
        h.handling.toggleServeSpin(server, ServeSpin.CURVE_RIGHT);
        assertEquals(ServeType.POWER, h.handling.serveType(server));
        assertEquals(ServeSpin.CURVE_RIGHT, h.handling.serveSpin(server));
        h.handling.toggleServeSpin(server, ServeSpin.CURVE_LEFT);
        assertEquals(ServeSpin.CURVE_LEFT, h.handling.serveSpin(server), "the other key switches the curve");
        h.handling.toggleServeSpin(server, ServeSpin.CURVE_LEFT);
        assertEquals(ServeSpin.TOPSPIN, h.handling.serveSpin(server), "the same key again: straight topspin");
    }
}
