package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.ai.AutoPlayer;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A computer server makes every serve: underhand reaches the far court, jump serves take off behind the end line. */
class ComputerServesTest {
    private static int[] serve(ServeType type, int n) {
        int hit = 0;
        int in = 0;
        int footFaults = 0;
        for (int i = 0; i < n; i++) {
            Harness h = new Harness(500 + i);
            VolleyballPlayer p = h.home(0);
            p.placeAt(-10f, 1f + (i % 3));
            p.facingYaw = VolleyballPlayer.facingNetYaw(CourtSide.HOME);
            AutoPlayer auto = new AutoPlayer(h.world, i);
            h.handling.prepareServe(p, type);
            h.handling.setAim(p, 6f, 0f);
            int before = h.touches.size();
            for (int k = 0; k < 120 * 6 && h.touches.size() == before; k++) {
                auto.act(p, AutoPlayer.Behavior.PASS, h.world.time());
                h.step();
            }
            if (h.touches.size() == before) continue;
            hit++;
            if (type.jump && Math.abs(p.takeoffPosition.x) <= CourtDimensions.HALF_LENGTH) footFaults++;
            Vector3 landing = h.landing(4f);
            if (landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY
                    && CourtDimensions.isInsideCourt(landing.x, landing.z, 0.025f)) in++;
        }
        return new int[]{hit, in, footFaults};
    }

    @Test
    void theUnderhandServeGetsDeepIntoTheOtherCourt() {
        int[] r = serve(ServeType.UNDERHAND, 10);
        assertEquals(10, r[0]);
        assertTrue(r[1] >= 9, "underhand serves in: " + r[1] + " of 10");
    }

    @Test
    void computerJumpServesAreHitFromBehindTheEndLine() {
        for (ServeType type : new ServeType[]{ServeType.JUMP_FLOAT, ServeType.JUMP_TOPSPIN}) {
            int[] r = serve(type, 8);
            assertEquals(8, r[0], type + ": every toss is hit");
            assertEquals(0, r[2], type + ": no foot faults");
            assertTrue(r[1] >= 6, type + " in: " + r[1] + " of 8");
        }
    }
}
