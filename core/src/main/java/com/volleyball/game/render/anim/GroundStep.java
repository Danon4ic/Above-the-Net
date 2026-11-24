package com.volleyball.game.render.anim;

import com.badlogic.gdx.math.MathUtils;

/** One stance and swing cycle. Progress zero is the same heel strike used by the sound engine. */
public final class GroundStep {
    private GroundStep() {
    }

    public static float stanceFraction(float speed, boolean lateral) {
        return lateral ? 0.62f : MathUtils.lerp(0.62f, 0.44f, MathUtils.clamp((speed - 1f) / 4.8f, 0f, 1f));
    }

    /** Backward motion of the planted foot exactly cancels travel along a straight stride. */
    public static float offset(float progress, float stance, float stride) {
        float halfTravel = stride * stance * 0.5f;
        if (progress <= stance) return halfTravel - stride * progress;
        return MathUtils.lerp(-halfTravel, halfTravel, smooth((progress - stance) / (1f - stance)));
    }

    public static float lift(float progress, float stance) {
        if (progress <= stance) return 0f;
        float swing = (progress - stance) / (1f - stance);
        return MathUtils.sin(MathUtils.PI * swing);
    }

    /** Keeps the foot fixed through weight transfer, releasing it near toe-off. */
    public static float plantWeight(float progress, float stance) {
        return 1f - smooth((progress / stance - 0.72f) / 0.28f);
    }

    private static float smooth(float t) {
        float clamped = MathUtils.clamp(t, 0f, 1f);
        return clamped * clamped * (3f - 2f * clamped);
    }
}
