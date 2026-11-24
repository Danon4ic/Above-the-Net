package com.volleyball.game.entities;

import com.badlogic.gdx.math.MathUtils;

/** Distance-driven gait shared by the animated legs and their footfall sounds. */
public final class GaitCycle {
    private GaitCycle() {
    }

    public static float strideLength(float speed) {
        return MathUtils.clamp(0.95f + 0.3f * speed, 1.15f, 1.7f);
    }

    /** A left or right heel strike occurred between the two simulation readings. */
    public static boolean crossedFootfall(float previous, float current) {
        return MathUtils.floor(current * 2f - 0.5f) > MathUtils.floor(previous * 2f - 0.5f);
    }

    /** Continuous cycle for one foot, with integers marking contact. */
    public static float footCycle(float cycles, int foot) {
        return cycles + foot * 0.5f - 0.25f;
    }
}
