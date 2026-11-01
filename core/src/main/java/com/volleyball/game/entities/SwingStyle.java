package com.volleyball.game.entities;

/**
 * How the hitting arm moves for the shot being played (the renderer animates the wind-up and the
 * swing from it): a power spike is a full bow-and-arrow whip, a cut snaps across the body, a roll
 * shot brushes up over the ball, a tip is a soft push with the fingers, a tool shot swings out
 * towards the block's edge; a float serve punches and stops, a topspin serve whips through, an
 * underhand serve swings from below.
 */
public enum SwingStyle {
    POWER, CUT, ROLL, TIP, TOOL, FLOAT_SERVE, TOPSPIN_SERVE, UNDERHAND_SERVE
}
