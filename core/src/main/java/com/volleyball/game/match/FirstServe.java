package com.volleyball.game.match;

/** Who serves first in a quick match: drawn by a coin toss, the person's team, or the opponent. */
public enum FirstServe {
    TOSS("Coin toss"),
    YOU("Your team"),
    OPPONENT("Opponent");

    public final String label;

    FirstServe(String label) {
        this.label = label;
    }
}
