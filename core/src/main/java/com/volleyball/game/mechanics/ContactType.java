package com.volleyball.game.mechanics;

/** Technique used to play the ball. Determines where the contact happens relative to the body. */
public enum ContactType {
    SERVE("Serve"),
    /** Bump: both forearms joined into a platform at waist height. */
    FOREARM_PASS("Forearm pass"),
    /** Fingers above the forehead: used for setting and for receiving high, soft balls. */
    OVERHEAD_PASS("Overhead pass"),
    /** One-hand hit above the head, normally in a jump. */
    ATTACK("Attack"),
    /** Hands over the net intercepting an attack (reported from the physics contact). */
    BLOCK("Block"),
    /** Low dig at full stretch at the end of a dive or sideways fall. */
    DIVE("Dive dig"),
    /** Emergency save with one arm / forearm, when the ball is just out of normal reach. */
    ONE_ARM("One-arm save"),
    /** Set with the fingers of one hand, reaching high or wide (a tight pass, a jump at the net). */
    ONE_HAND_SET("One-hand set");

    public final String label;

    ContactType(String label) {
        this.label = label;
    }

    /** A finger contact above the head (two hands or one): the held-ball and libero set rules apply. */
    public boolean fingerSet() {
        return this == OVERHEAD_PASS || this == ONE_HAND_SET;
    }
}
