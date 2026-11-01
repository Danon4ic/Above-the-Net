package com.volleyball.game.mechanics;

/** Where the blocker puts the hands relative to the body: square, or shifted to close the line or the diagonal. */
public enum BlockStyle {
    NORMAL("Block: straight"),
    /** Hands shifted towards the nearer sideline: takes away the line shot. */
    LINE("Block: close the line"),
    /** Hands shifted towards the court centre: takes away the cross-court shot. */
    CROSS("Block: close the diagonal");

    public final String label;

    BlockStyle(String label) {
        this.label = label;
    }

    public BlockStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public BlockStyle previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }
}
