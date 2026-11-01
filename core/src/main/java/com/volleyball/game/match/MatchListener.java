package com.volleyball.game.match;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.world.CourtSide;

/** Match flow events for the presentation layer (sound, score graphics, statistics). */
public interface MatchListener {
    /** The referee whistles: the server may serve now. */
    default void onServeWhistle(VolleyballPlayer server, MatchScore score) {
    }

    /** A substitution was made (before the next serve). */
    default void onSubstitution(CourtSide side, VolleyballPlayer out, VolleyballPlayer in, int used) {
    }

    /** A rally has been decided (or replayed) and applied to the score. */
    /** A team takes a timeout ({@code used} of its timeouts this set). */
    default void onTimeout(CourtSide side, int used) {
    }

    /** The teams changed ends ({@code swapped}: they now play on the ends opposite to the start). */
    default void onChangeOfEnds(boolean swapped) {
    }

    default void onPoint(MatchRules.PointSummary point, MatchScore score) {
    }
}
