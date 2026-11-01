package com.volleyball.game.mechanics;

/**
 * How a power serve is struck: straight through the top of the ball (pure topspin, it dives), or
 * brushed across one side so the ball also curves in the air. The serve still lands on the aim
 * point (the launch accounts for the curve); it gets there on a bending path that is harder to
 * read, at the cost of a little less dive and a little more error.
 */
public enum ServeSpin {
    //            label           topspin share  sidespin share (+ = curves to the left of the flight)
    TOPSPIN("Topspin", 1f, 0f),
    CURVE_LEFT("Curve left", 0.6f, 0.8f),
    CURVE_RIGHT("Curve right", 0.6f, -0.8f);

    /** Extra aiming error of a curving serve (the contact is off the centre line of the ball). */
    public static final float CURVE_ERROR = 1.15f;

    public final String label;
    /** Fractions of the serve type's spin rate that go into topspin and sidespin. */
    public final float topspinShare;
    public final float sidespinShare;

    ServeSpin(String label, float topspinShare, float sidespinShare) {
        this.label = label;
        this.topspinShare = topspinShare;
        this.sidespinShare = sidespinShare;
    }

    /** Only the power serves (overhand with real topspin) can be curved. */
    public static boolean applies(ServeType type) {
        return type != null && type.overhand && type.topspin > 20f;
    }
}
