package com.volleyball.game.ai;

import java.util.Locale;

/**
 * AI difficulty. It changes how computer players perceive and decide, never the physics or
 * their ratings: reaction time, how well they judge the ball, positioning accuracy, which sets
 * and tactics they use, whether they read the setter and the hitters' habits, and how they
 * manage risk.
 */
public enum Difficulty {
    /** Slow reactions, simple tactics, positioning mistakes, high sets to the outside only. */
    BEGINNER("Beginner", 0.36f, 0.45f, 1.1f, 0.45f, 0.8f, false, false, false, 0.25f),
    /** Steady reception, basic sets (quick, back set), reacts to the attack direction, sensible block. */
    INTERMEDIATE("Intermediate", 0.14f, 0.26f, 0.5f, 0.2f, 0.3f, true, false, false, 0.5f),
    /**
     * Reads the setter, learns the hitters' habits, coordinated group block, covers and guards
     * free zones, changes tactics during the match, manages risk, uses decoys and combinations.
     */
    PROFESSIONAL("Professional", 0.04f, 0.14f, 0.15f, 0.05f, 0.06f, true, true, true, 0.7f);

    public final String label;
    /** Delay before moving for a new ball, s (scaled by the player's reaction rating). */
    public final float reaction;
    /** Delay before reacting to an opponent's attack, s. */
    public final float defenseReaction;
    /** Typical misjudgement of where the ball comes down, m (shrinks as it approaches). */
    public final float readError;
    /** Fraction of the misjudgement left at the contact. */
    public final float residualError;
    /** Typical error of tactical positions, m. */
    public final float positionError;
    /** Uses the block sensibly: wing blockers join on the hitter's side, block style by the set. */
    public final boolean meaningfulBlock;
    /** Reads the setter and hitters (moves on the set, not after it) and scouts habits. */
    public final boolean readsPlay;
    /** Adapts tactics during the match (what works is used more often), decoys, cover. */
    public final boolean adapts;
    /** Default appetite for risk (0..1): serve power and how close to the lines it aims. */
    public final float risk;

    Difficulty(String label, float reaction, float defenseReaction, float readError, float residualError,
               float positionError, boolean meaningfulBlock, boolean readsPlay, boolean adapts, float risk) {
        this.label = label;
        this.reaction = reaction;
        this.defenseReaction = defenseReaction;
        this.readError = readError;
        this.residualError = residualError;
        this.positionError = positionError;
        this.meaningfulBlock = meaningfulBlock;
        this.readsPlay = readsPlay;
        this.adapts = adapts;
        this.risk = risk;
    }

    public Difficulty next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Parses a name such as "pro", "professional", "beginner" (null if unknown). */
    public static Difficulty parse(String name) {
        if (name == null) return null;
        String n = name.trim().toUpperCase(Locale.ROOT);
        for (Difficulty d : values()) if (d.name().equals(n) || d.name().startsWith(n) && n.length() >= 3) return d;
        return null;
    }
}
