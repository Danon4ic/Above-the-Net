package com.volleyball.game.render.anim;

import com.volleyball.game.physics.PhysicsConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JumpMotionTest {
    @Test
    void flightPhaseFollowsTakeoffApexAndTouchdown() {
        float takeoff = 4.2f;
        float apex = takeoff * takeoff / (2f * PhysicsConstants.GRAVITY);
        assertEquals(0f, JumpMotion.flightProgress(0f, takeoff), 0.0001f);
        assertEquals(0.25f, JumpMotion.flightProgress(apex * 0.75f, takeoff * 0.5f), 0.0001f);
        assertEquals(0.5f, JumpMotion.flightProgress(apex, 0f), 0.0001f);
        assertEquals(1f, JumpMotion.flightProgress(0f, -takeoff), 0.0001f);
    }

    @Test
    void exportedMotionHasDistinctPosesAndNeutralEndpoints() {
        float[] pose = new float[JumpMotion.CHANNELS];
        for (JumpMotion motion : JumpMotion.values()) {
            motion.sample(0f, pose);
            for (float channel : pose) assertEquals(0f, channel);
            if (motion != JumpMotion.PLANT) {
                motion.sample(1f, pose);
                for (float channel : pose) assertEquals(0f, channel);
            }
        }
        JumpMotion.JUMP_SET.sample(0.5f, pose);
        assertEquals(pose[JumpMotion.LEFT_ARM], pose[JumpMotion.RIGHT_ARM], 0.001f);
        JumpMotion.BACK_ATTACK.sample(0.65f, pose);
        assertTrue(pose[JumpMotion.LEFT_ANKLE] - pose[JumpMotion.RIGHT_ANKLE] > 0.2f);
        JumpMotion.LANDING.sample(0.3f, pose);
        assertTrue(pose[JumpMotion.COMPRESSION] > 0.05f);
    }
}
