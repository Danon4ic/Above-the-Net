package com.volleyball.game.ai;

/**
 * The team's system: how many setters it plays with and which of them sets.
 * <ul>
 *   <li>5-1: one setter who sets in every rotation; an opposite plays across from them.</li>
 *   <li>4-2: two setters across from each other; the one in the front row sets (only two
 *       front-row attackers, simpler for beginners).</li>
 *   <li>6-2: two setters; the one in the back row comes forward to set, the front-row one attacks
 *       as an opposite - three front-row attackers in every rotation.</li>
 * </ul>
 */
public enum OffenseSystem {
    FIVE_ONE("5-1", "One setter sets in every rotation, the opposite attacks on the right."),
    FOUR_TWO("4-2", "Two setters; the front-row setter sets, two hitters attack."),
    SIX_TWO("6-2", "Two setters; the back-row setter sets, the front-row one attacks: three hitters.");

    public final String label;
    public final String description;

    OffenseSystem(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public boolean twoSetters() {
        return this != FIVE_ONE;
    }

    public OffenseSystem next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
