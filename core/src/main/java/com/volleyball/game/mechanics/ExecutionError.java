package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;

import java.util.Random;

/**
 * Applies human imprecision to an intended ball velocity: small random rotations of the
 * direction and a random speed change, drawn from a (clamped) normal distribution. The spread
 * is chosen by each mechanic from contact quality, ratings and difficulty.
 */
final class ExecutionError {
    private ExecutionError() {}

    /** Standard normal sample clamped to ±2.5σ (no absurd outliers). */
    static float gaussian(Random random) {
        return (float) Math.max(-2.5, Math.min(2.5, random.nextGaussian()));
    }

    /**
     * @param angleSigmaDegrees standard deviation of the direction error
     * @param speedSigma        standard deviation of the relative speed error (0.05 = 5 %)
     */
    static void apply(Vector3 velocity, float angleSigmaDegrees, float speedSigma, Random random) {
        float yaw = gaussian(random) * angleSigmaDegrees;
        float pitch = gaussian(random) * angleSigmaDegrees * 0.8f;
        velocity.rotate(Vector3.Y, yaw);
        Vector3 axis = new Vector3(velocity).crs(Vector3.Y);
        if (axis.len2() > 1e-8f) velocity.rotate(axis.nor(), pitch);
        velocity.scl(Math.max(0.3f, 1f + gaussian(random) * speedSigma));
    }

    /**
     * Deflects the velocity sideways: positive degrees turn it to the right of its direction of
     * travel (clockwise seen from above).
     */
    static void deflectRight(Vector3 velocity, float degrees) {
        velocity.rotate(Vector3.Y, -degrees);
    }

    /** Linear interpolation helper for rating-based scales. */
    static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
