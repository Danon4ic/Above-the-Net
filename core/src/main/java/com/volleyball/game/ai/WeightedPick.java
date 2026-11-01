package com.volleyball.game.ai;

import java.util.Map;
import java.util.Random;

/** A random choice in proportion to weights (negative weights count as zero). */
final class WeightedPick {
    private WeightedPick() {}

    /**
     * One key of {@code weights}, each with a chance proportional to its weight, or
     * {@code fallback} if no weight is positive. The map's iteration order must be stable (an
     * EnumMap or a LinkedHashMap) so the same seed gives the same choice.
     */
    static <T> T pick(Random random, Map<T, Float> weights, T fallback) {
        float total = 0f;
        for (float w : weights.values()) total += Math.max(0f, w);
        if (total <= 0f) return fallback;
        float r = random.nextFloat() * total;
        T last = fallback;
        for (Map.Entry<T, Float> e : weights.entrySet()) {
            if (e.getValue() <= 0f) continue;
            last = e.getKey();
            r -= e.getValue();
            if (r <= 0f) return last;
        }
        return last;
    }
}
