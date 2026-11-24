package com.volleyball.game.render.anim;

import com.volleyball.game.entities.GaitCycle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundStepTest {
    @Test
    void footfallsAndVisibleStanceStartAtTheSamePhase() {
        assertTrue(GaitCycle.crossedFootfall(0.24f, 0.25f));
        assertEquals(0f, GaitCycle.footCycle(0.25f, 0), 1e-6f);
        assertTrue(GaitCycle.crossedFootfall(0.74f, 0.75f));
        assertEquals(1f, GaitCycle.footCycle(0.75f, 1), 1e-6f);
    }

    @Test
    void theStandingFootMatchesGroundTravelAndTheSwingClearsTheFloor() {
        float stride = 1.4f;
        float stance = GroundStep.stanceFraction(3f, false);
        assertEquals(-stride * 0.2f, GroundStep.offset(0.3f, stance, stride)
                - GroundStep.offset(0.1f, stance, stride), 1e-6f);
        assertEquals(0f, GroundStep.lift(stance, stance), 1e-6f);
        assertTrue(GroundStep.lift((stance + 1f) * 0.5f, stance) > 0.9f);
        assertEquals(GroundStep.offset(0f, stance, stride), GroundStep.offset(1f, stance, stride), 1e-6f);
    }
}
