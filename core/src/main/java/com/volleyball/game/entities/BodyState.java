package com.volleyball.game.entities;

/** Whole-body state: standing, or going through a dive / fall and getting back up. */
public enum BodyState {
    UPRIGHT,
    /** Launching low towards the ball, arms reaching out. */
    DIVING,
    /** Lying or sliding on the floor after a dive or fall. */
    ON_FLOOR,
    /** Getting back to the feet. */
    GETTING_UP
}
