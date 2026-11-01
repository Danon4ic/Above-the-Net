package com.volleyball.game.entities;

/**
 * Player ratings on a 1–100 scale. Ratings shape how well an action turns out, but never replace
 * timing and positioning: mechanics read them as modifiers of physically computed outcomes.
 *
 * <p>Stage 2 uses speed, acceleration and jump; the remaining ratings are consumed by the ball
 * mechanics, AI and fatigue systems as those are added.
 */
public final class PlayerAttributes {
    public static final int MIN = 1;
    public static final int MAX = 100;

    public int speed = 50;
    public int acceleration = 50;
    public int jump = 50;
    public int attackPower = 50;
    public int attackAccuracy = 50;
    public int receiving = 50;
    public int setting = 50;
    public int reaction = 50;
    public int blocking = 50;
    public int defense = 50;
    public int serving = 50;
    public int stamina = 50;
    public int tactics = 50;
    public int composure = 50;

    public PlayerAttributes() {}

    public PlayerAttributes(PlayerAttributes other) {
        speed = other.speed;
        acceleration = other.acceleration;
        jump = other.jump;
        attackPower = other.attackPower;
        attackAccuracy = other.attackAccuracy;
        receiving = other.receiving;
        setting = other.setting;
        reaction = other.reaction;
        blocking = other.blocking;
        defense = other.defense;
        serving = other.serving;
        stamina = other.stamina;
        tactics = other.tactics;
        composure = other.composure;
    }

    /** Clamps every rating into 1..100 (used after loading saved or edited data). */
    public PlayerAttributes sanitize() {
        speed = clamp(speed);
        acceleration = clamp(acceleration);
        jump = clamp(jump);
        attackPower = clamp(attackPower);
        attackAccuracy = clamp(attackAccuracy);
        receiving = clamp(receiving);
        setting = clamp(setting);
        reaction = clamp(reaction);
        blocking = clamp(blocking);
        defense = clamp(defense);
        serving = clamp(serving);
        stamina = clamp(stamina);
        tactics = clamp(tactics);
        composure = clamp(composure);
        return this;
    }

    /** Maps a rating to 0..1. */
    public static float normalized(int rating) {
        return (clamp(rating) - MIN) / (float) (MAX - MIN);
    }

    /** Linear interpolation between the values for rating 1 and rating 100. */
    public static float scale(int rating, float atMin, float atMax) {
        return atMin + (atMax - atMin) * normalized(rating);
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
