package com.volleyball.game.input;

/** Logical player commands; physical keys are mapped to them by {@link KeyBindings}. */
public enum PlayerAction {
    MOVE_FORWARD,
    MOVE_BACK,
    MOVE_LEFT,
    MOVE_RIGHT,
    JUMP,
    SPRINT,
    CROUCH,
    SWITCH_PLAYER,
    /** Context ball action: serve toss/hit, pass, set, attack. */
    PRIMARY_ACTION,
    /** Alternative ball action: overhead pass on the ground, tip in the air. */
    SECONDARY_ACTION,
    /** Previous / next variant of the current action (serve type, set type, attack shot). */
    VARIANT_PREVIOUS,
    VARIANT_NEXT,
    PAUSE
}
