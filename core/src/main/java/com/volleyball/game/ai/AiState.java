package com.volleyball.game.ai;

/** What a computer player is doing right now (one state per player, shown in the AI view). */
public enum AiState {
    IDLE("idle"),
    POSITIONING("positioning"),
    READY_TO_RECEIVE("ready to receive"),
    MOVING_TO_BALL("moving to ball"),
    PASSING("passing"),
    SETTING("setting"),
    APPROACHING("approach"),
    JUMPING("jumping"),
    ATTACKING("attacking"),
    BLOCKING("blocking"),
    DEFENDING("defending"),
    COVERING("covering"),
    DIVING("diving"),
    LANDING("landing"),
    RECOVERING("recovering"),
    SERVING("serving"),
    CELEBRATING("celebrating");

    public final String label;

    AiState(String label) {
        this.label = label;
    }
}
