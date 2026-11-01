package com.volleyball.game.render.anim;

/**
 * A critically damped spring: the value follows its target with a natural ease-in and ease-out
 * (it accelerates, then settles without overshooting), and a target that changes mid-way is
 * followed smoothly instead of the motion restarting. Exact for any time step, so the result
 * does not depend on the frame rate.
 *
 * <p>{@code halfLife} is the time (s) in which the distance to a fixed target halves.
 */
public final class Spring {
    private float value;
    private float velocity;
    private boolean initialised;

    public float value() {
        return value;
    }

    public float velocity() {
        return velocity;
    }

    /** Jumps to {@code to} at rest (the next update eases from there). */
    public void snap(float to) {
        value = to;
        velocity = 0f;
        initialised = true;
    }

    /** Moves towards {@code target} over {@code dt} seconds and returns the new value. */
    public float update(float target, float halfLife, float dt) {
        if (!initialised) {
            snap(target);
            return value;
        }
        if (dt <= 0f) return value;
        float y = (float) (2.0 * Math.log(2.0) / Math.max(1e-4f, halfLife)); // damping / 2
        float j0 = value - target;
        float j1 = velocity + j0 * y;
        float e = (float) Math.exp(-y * dt);
        value = e * (j0 + j1 * dt) + target;
        velocity = e * (velocity - j1 * y * dt);
        return value;
    }
}
