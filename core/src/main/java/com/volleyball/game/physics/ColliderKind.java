package com.volleyball.game.physics;

/** What the ball touched. The rules module interprets these (e.g. antenna = fault, body = team touch). */
public enum ColliderKind {
    FLOOR,
    NET_MESH,
    NET_BAND,
    ANTENNA,
    POST,
    PLAYER_BODY,
    /** A blocker's hands above the net. A block touch does not count as one of the team's three. */
    BLOCK,
    WALL,
    CEILING;

    /** Objects outside the court whose contact puts the ball out of play (rule 8.4). */
    public boolean isOutOfPlayObject() {
        return this == POST || this == WALL || this == CEILING;
    }
}
