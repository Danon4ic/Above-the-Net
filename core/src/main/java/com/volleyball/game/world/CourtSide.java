package com.volleyball.game.world;

/** One half of the court. HOME occupies negative X, AWAY positive X. */
public enum CourtSide {
    HOME(-1),
    AWAY(1);

    private final int sign;

    CourtSide(int sign) {
        this.sign = sign;
    }

    /** -1 for HOME, +1 for AWAY: multiply a depth by this to get a world X coordinate. */
    public int sign() {
        return sign;
    }

    public CourtSide opposite() {
        return this == HOME ? AWAY : HOME;
    }
}
