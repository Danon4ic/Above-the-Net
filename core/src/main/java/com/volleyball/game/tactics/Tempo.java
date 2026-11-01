package com.volleyball.game.tactics;

/**
 * When a hitter starts the approach, relative to the setter's contact. The tempo is what makes a
 * combination: the first-tempo hitter is already in the air when the ball leaves the setter's
 * hands, the second-tempo hitter a beat later, the third-tempo hitter (high ball) starts once
 * the set is on its way.
 */
public enum Tempo {
    /** Starts with the pass and jumps as the set is released (quick, bic). */
    FIRST("First tempo", -0.55f),
    /** Starts a beat later and jumps shortly after the set (short set, shoot, tandem follower). */
    SECOND("Second tempo", -0.25f),
    /** Starts once the set is in the air (high balls, back-row attacks). */
    THIRD("Third tempo", 0.15f);

    public final String label;
    /** Seconds from the setter's contact to the start of the approach (negative: before the set). */
    public final float startOffset;

    Tempo(String label, float startOffset) {
        this.label = label;
        this.startOffset = startOffset;
    }

    public static Tempo fromName(String name) {
        for (Tempo t : values()) if (t.name().equalsIgnoreCase(name)) return t;
        return THIRD;
    }
}
