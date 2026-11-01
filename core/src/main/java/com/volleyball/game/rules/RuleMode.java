package com.volleyball.game.rules;

/**
 * How strictly the referee calls the game.
 * <ul>
 *   <li>{@link #OFFICIAL}: all FIVB faults, including net touches, positional faults, foot
 *       faults, back-row and libero restrictions and held balls.</li>
 *   <li>{@link #SIMPLIFIED}: only the essentials — ball in/out, antenna, four hits, double
 *       contact, serve order. Friendlier for beginners.</li>
 * </ul>
 */
public enum RuleMode {
    OFFICIAL("Official rules"),
    SIMPLIFIED("Simplified rules");

    public final String label;

    RuleMode(String label) {
        this.label = label;
    }
}
