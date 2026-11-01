package com.volleyball.game.world;

/** Competition category, which defines the net height. */
public enum NetCategory {
    MEN(2.43f, "Men 2.43 m"),
    WOMEN(2.24f, "Women 2.24 m");

    private final float height;
    private final String label;

    NetCategory(float height, String label) {
        this.height = height;
        this.label = label;
    }

    /** Height of the top of the net above the floor, measured at the centre of the court. */
    public float height() {
        return height;
    }

    public String label() {
        return label;
    }

    public NetCategory next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
