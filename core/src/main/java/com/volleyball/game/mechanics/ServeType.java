package com.volleyball.game.mechanics;

/**
 * Serve techniques. Faster serves are harder to receive but riskier: their execution error is
 * larger and they leave less margin over the net.
 */
public enum ServeType {
    //            label            overhand jump  min/max speed  topspin errDeg netMargin tossAbove tossForward
    UNDERHAND("Underhand",          false, false, 13f, 16f,      3f,    1.4f,  0.50f,    0.25f,    0.0f),
    FLOAT("Float",                  true,  false, 14f, 18f,      0f,    2.0f,  0.30f,    0.90f,    0.15f),
    POWER("Power topspin",          true,  false, 17f, 22f,      28f,   2.6f,  0.20f,    1.10f,    0.30f),
    JUMP_FLOAT("Jump float",        true,  true,  15f, 20f,      0f,    2.4f,  0.30f,    1.00f,    0.90f),
    JUMP_TOPSPIN("Jump topspin",    true,  true,  22f, 30f,      42f,   3.2f,  0.15f,    1.60f,    1.40f);

    public final String label;
    public final boolean overhand;
    public final boolean jump;
    /** Ball speed for a server rated 1 and rated 100, m/s. */
    public final float minSpeed;
    public final float maxSpeed;
    /** Topspin in rad/s (0 = float: the knuckle effect makes it wobble). */
    public final float topspin;
    /** Base aiming error (standard deviation, degrees) for an average contact. */
    public final float errorDegrees;
    /** Minimum height the ball centre aims to clear the net by. */
    public final float netMargin;
    /** How far above the contact point the toss peaks. */
    public final float tossAboveContact;
    /** How far forward of the toss point the ball comes down (jump serves need a run-up). */
    public final float tossForward;

    ServeType(String label, boolean overhand, boolean jump, float minSpeed, float maxSpeed, float topspin,
              float errorDegrees, float netMargin, float tossAboveContact, float tossForward) {
        this.label = label;
        this.overhand = overhand;
        this.jump = jump;
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.topspin = topspin;
        this.errorDegrees = errorDegrees;
        this.netMargin = netMargin;
        this.tossAboveContact = tossAboveContact;
        this.tossForward = tossForward;
    }

    public ServeType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public ServeType previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }
}
