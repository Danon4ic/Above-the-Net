package com.volleyball.game.render.anim;

import com.volleyball.game.entities.BodyState;
import com.volleyball.game.physics.PlayerLocomotion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiveMotionTest {
    @Test
    void physicsPhasesShareTheSamePoseAtEachTransition() {
        float[] end = new float[DiveMotion.CHANNELS];
        float[] next = new float[DiveMotion.CHANNELS];
        for (FallKind kind : FallKind.values()) {
            DiveMotion.sample(kind, BodyState.DIVING, PlayerLocomotion.DIVE_TIME, end);
            DiveMotion.sample(kind, BodyState.ON_FLOOR, 0f, next);
            assertArrayEquals(end, next, 0.0001f);
            DiveMotion.sample(kind, BodyState.ON_FLOOR, PlayerLocomotion.FLOOR_TIME, end);
            DiveMotion.sample(kind, BodyState.GETTING_UP, 0f, next);
            assertArrayEquals(end, next, 0.0001f);
        }
    }

    @Test
    void oneArmReachesDuringTheSlideAndThePlayerReturnsToTheStandingOrigin() {
        float[] pose = new float[DiveMotion.CHANNELS];
        DiveMotion.sample(FallKind.SPRAWL, BodyState.ON_FLOOR, 0.12f, pose);
        assertTrue(pose[DiveMotion.HIP_HEIGHT] < 0.25f);
        assertTrue(pose[DiveMotion.REACH_ARM] - pose[DiveMotion.SUPPORT_ARM] > 0.9f);
        DiveMotion.sample(FallKind.SPRAWL, BodyState.GETTING_UP, PlayerLocomotion.GET_UP_TIME, pose);
        assertEquals(0f, pose[DiveMotion.PITCH], 0.0001f);
        assertEquals(0f, pose[DiveMotion.ADVANCE], 0.0001f);
        assertTrue(pose[DiveMotion.HIP_HEIGHT] > 0.9f);
    }

    @Test
    void theKneeDropAndSideRollHaveDifferentSupports() {
        float[] knee = new float[DiveMotion.CHANNELS];
        float[] roll = new float[DiveMotion.CHANNELS];
        DiveMotion.sample(FallKind.KNEE_DROP, BodyState.ON_FLOOR, 0.3f, knee);
        DiveMotion.sample(FallKind.SIDE_ROLL, BodyState.ON_FLOOR, 0.3f, roll);
        assertTrue(knee[DiveMotion.PITCH] < 0.5f);
        assertTrue(knee[DiveMotion.SUPPORT_LEG] - knee[DiveMotion.TRAIL_LEG] > 0.5f);
        assertTrue(roll[DiveMotion.PITCH] > 1.4f);
        assertTrue(roll[DiveMotion.BANK] > 0.65f);
    }

    @Test
    void sideRollReturnsOverTheKneeBeforeTheHipsRise() {
        float[] pose = new float[DiveMotion.CHANNELS];
        DiveMotion.sample(FallKind.SIDE_ROLL, BodyState.GETTING_UP, 0.12f, pose);
        assertTrue(pose[DiveMotion.PITCH] < 0.32f, "the pelvis is almost upright");
        assertTrue(pose[DiveMotion.HIP_HEIGHT] < 0.42f, "body weight is still low");
        assertTrue(pose[DiveMotion.SUPPORT_LEG] - pose[DiveMotion.TRAIL_LEG] > 0.55f,
                "one foot is forward and the other leg stays folded");
        DiveMotion.sample(FallKind.SIDE_ROLL, BodyState.GETTING_UP, 0.38f, pose);
        assertTrue(pose[DiveMotion.HIP_HEIGHT] > 0.68f, "the hips rise after the support phase");
    }
}
