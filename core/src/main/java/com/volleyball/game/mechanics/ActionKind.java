package com.volleyball.game.mechanics;

/**
 * A ball-action request from a player (human button or AI decision). The exact technique is
 * chosen at the moment of contact from the situation.
 */
public enum ActionKind {
    /**
     * Context action: serve when serving, attack when in the air, overhead or forearm pass on
     * the ground — whichever the ball's position suits best.
     */
    PRIMARY,
    /** Alternative: overhead pass on the ground (e.g. setting with the fingers), tip in the air. */
    SECONDARY
}
