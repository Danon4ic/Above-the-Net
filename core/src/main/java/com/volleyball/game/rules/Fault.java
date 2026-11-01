package com.volleyball.game.rules;

/**
 * Why a rally ended. Most are faults of one team; {@link #BALL_IN} means the ball simply landed
 * in a team's court. Faults marked {@code officialOnly} are not called with simplified rules.
 */
public enum Fault {
    BALL_IN("Ball in", false),
    BALL_OUT("Ball out", false),
    ANTENNA("Ball touched the antenna", false),
    OUTSIDE_CROSSING_SPACE("Ball crossed outside the antennas", false),
    UNDER_NET("Ball passed under the net", false),
    OUT_OF_PLAY_OBJECT("Ball hit a post, wall or the ceiling", false),
    FOUR_HITS("Four hits", false),
    DOUBLE_CONTACT("Double contact", false),
    HELD_BALL("Held / thrown ball", true),
    PLAYER_NET_TOUCH("Player touched the net", true),
    SERVE_ORDER("Wrong server (rotation order)", false),
    SERVE_NOT_HIT("Server missed the toss", false),
    SERVE_TIMEOUT("Serve not made within the time limit", false),
    FOOT_FAULT("Foot fault on the serve", true),
    /** The server stepped onto the court with the ball before serving. */
    SERVER_ON_COURT("Server stepped onto the court with the ball", true),
    /** A player of the serving team touched the served ball before it crossed the net. */
    SERVE_TEAM_TOUCH("Serving team touched its own serve", false),
    POSITIONAL("Positional fault (overlap) at the serve", true),
    BACK_ROW_ATTACK("Back-row player attacked from the front zone", true),
    BACK_ROW_BLOCK("Back-row player blocked", true),
    BLOCKED_SERVE("Blocking the serve", true),
    ATTACK_ON_SERVE("Attacking the serve above the net", true),
    LIBERO_ATTACK("Libero attacked above the net", true),
    LIBERO_BLOCK("Libero blocked", true),
    LIBERO_SET_ATTACK("Attack above the net after a libero's overhead set from the front zone", true),
    /** Rally abandoned (e.g. the ball got stuck); replayed without a point. */
    REPLAY("Replay", false);

    public final String label;
    public final boolean officialOnly;

    Fault(String label, boolean officialOnly) {
        this.label = label;
        this.officialOnly = officialOnly;
    }
}
