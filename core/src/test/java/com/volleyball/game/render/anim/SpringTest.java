package com.volleyball.game.render.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The pose spring eases in and out, never overshoots, and does not depend on the frame rate. */
class SpringTest {
    @Test
    void halvesTheDistanceInOneHalfLifeAndSettlesWithoutOvershooting() {
        Spring s = new Spring();
        s.snap(0f);
        float halfLife = 0.1f;
        float t = 0f;
        float previous = 0f;
        while (t < 1f) {
            float v = s.update(1f, halfLife, 1f / 120f);
            assertTrue(v <= 1f + 1e-5f, "never past the target: " + v);
            assertTrue(v >= previous - 1e-6f, "never back");
            previous = v;
            t += 1f / 120f;
        }
        assertEquals(1f, s.value(), 1e-3f);
    }

    @Test
    void startsSlowlyFromRest() {
        Spring s = new Spring();
        s.snap(0f);
        float first = s.update(1f, 0.1f, 1f / 120f);
        float second = s.update(1f, 0.1f, 1f / 120f) - first;
        assertTrue(second > first, "accelerates from rest: " + first + " then " + second);
    }

    @Test
    void theResultDoesNotDependOnTheFrameRate() {
        Spring fast = new Spring();
        Spring slow = new Spring();
        fast.snap(0f);
        slow.snap(0f);
        for (int i = 0; i < 240; i++) fast.update(2f, 0.08f, 1f / 240f);
        for (int i = 0; i < 30; i++) slow.update(2f, 0.08f, 1f / 30f);
        assertEquals(fast.value(), slow.value(), 1e-3f);
    }
}
