package com.volleyball.game.entities;

import com.badlogic.gdx.math.MathUtils;

/** Distance-driven gait shared by the animated legs and their footfall sounds. */
public final class GaitCycle {
    private float cycles;
    private float previousDistance = Float.NaN;

    public static float strideLength(float speed) {
        return MathUtils.clamp(0.95f + 0.3f * speed, 1.15f, 1.7f);
    }

    /** Each foot plants as its lift cosine crosses zero towards the ground. */
    public boolean advance(float travelled, float speed) {
        float previous = cycles;
        if (!Float.isNaN(previousDistance) && travelled > previousDistance) {
            cycles += (travelled - previousDistance) / strideLength(speed);
        }
        previousDistance = travelled;
        boolean planted = MathUtils.floor(cycles * 2f - 0.5f) > MathUtils.floor(previous * 2f - 0.5f);
        cycles %= 64f;
        return planted;
    }

    public float phase() {
        return cycles * MathUtils.PI2;
    }
}
