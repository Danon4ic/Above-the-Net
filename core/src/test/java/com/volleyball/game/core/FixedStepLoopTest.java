package com.volleyball.game.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FixedStepLoopTest {
    @Test
    void stepCountDoesNotDependOnFrameRate() {
        for (int fps : new int[]{30, 60, 75, 144, 240}) {
            FixedStepLoop loop = new FixedStepLoop(120, 16);
            AtomicInteger steps = new AtomicInteger();
            for (int frame = 0; frame < fps * 2; frame++) loop.advance(1f / fps, dt -> steps.incrementAndGet());
            assertEquals(240, steps.get(), 1, "2 s at " + fps + " FPS");
        }
    }

    @Test
    void everyStepUsesTheFixedDelta() {
        FixedStepLoop loop = new FixedStepLoop(120, 16);
        loop.advance(0.1f, dt -> assertEquals(1f / 120f, dt, 1e-7f));
    }

    @Test
    void longFramesAreClampedToAvoidSpiralOfDeath() {
        FixedStepLoop loop = new FixedStepLoop(120, 8);
        int steps = loop.advance(2f, dt -> {});
        assertEquals(8, steps);
        assertTrue(loop.droppedSeconds() > 1.9);
        assertTrue(loop.alpha() >= 0f && loop.alpha() < 1f);
        assertEquals(0, loop.advance(0f, dt -> {}), "backlog was dropped");
    }

    @Test
    void invalidFrameTimesAreIgnored() {
        FixedStepLoop loop = new FixedStepLoop(60, 4);
        assertEquals(0, loop.advance(Float.NaN, dt -> {}));
        assertEquals(0, loop.advance(-1f, dt -> {}));
        assertEquals(0, loop.totalSteps());
    }
}
