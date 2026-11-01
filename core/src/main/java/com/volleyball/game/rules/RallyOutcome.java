package com.volleyball.game.rules;

import com.volleyball.game.world.CourtSide;

/**
 * Result of a rally.
 *
 * @param winner     team that wins the point, or null for a replay
 * @param reason     what ended the rally
 * @param playerId   player at fault (or who scored), -1 if none
 * @param time       simulation time of the decision
 */
public record RallyOutcome(CourtSide winner, Fault reason, int playerId, double time) {
    public boolean isReplay() {
        return winner == null;
    }

    public String describe() {
        if (winner == null) return reason.label;
        return reason.label + "  ·  point " + (winner == CourtSide.HOME ? "home" : "away");
    }
}
