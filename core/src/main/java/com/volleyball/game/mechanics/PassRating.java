package com.volleyball.game.mechanics;

/**
 * Quality class of a pass or set, judged by how close the ball will arrive to the intended spot.
 * The rating limits what the next player can do: a perfect pass allows any combination, an
 * emergency pass forces the team to just send the ball over.
 */
public enum PassRating {
    PERFECT("Perfect", 0.6f),
    GOOD("Good", 1.3f),
    LIMITED("Limited", 2.5f),
    EMERGENCY("Emergency", Float.POSITIVE_INFINITY);

    public final String label;
    /** Largest miss distance (metres) still rated this class. */
    public final float maxMiss;

    PassRating(String label, float maxMiss) {
        this.label = label;
        this.maxMiss = maxMiss;
    }

    /**
     * @param missDistance distance between where the ball will arrive and the intended spot
     * @param overTheNet   the ball is going to the opponent's side (an "overpass")
     */
    public static PassRating classify(float missDistance, boolean overTheNet) {
        if (overTheNet || Float.isNaN(missDistance)) return EMERGENCY;
        for (PassRating rating : values()) if (missDistance <= rating.maxMiss) return rating;
        return EMERGENCY;
    }

    public boolean isPlayable() {
        return this == PERFECT || this == GOOD;
    }
}
