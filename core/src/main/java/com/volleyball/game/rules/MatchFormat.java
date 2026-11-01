package com.volleyball.game.rules;

/**
 * Match settings: how many sets win the match, points per set and in the deciding set, the
 * winning margin and the rule mode.
 *
 * @param setsToWin          sets needed to win the match (3 = best of five)
 * @param pointsPerSet       points to win a normal set
 * @param decidingSetPoints  points to win the deciding (last possible) set
 * @param winBy              minimum lead to win a set
 * @param mode               which faults are called
 * @param serveTimeLimit     seconds the server has to serve after the whistle
 */
public record MatchFormat(int setsToWin, int pointsPerSet, int decidingSetPoints, int winBy, RuleMode mode,
                          float serveTimeLimit) {

    public MatchFormat {
        if (setsToWin < 1 || setsToWin > 5) throw new IllegalArgumentException("setsToWin must be 1..5");
        if (pointsPerSet < 1 || decidingSetPoints < 1) throw new IllegalArgumentException("points must be positive");
        if (winBy < 1) throw new IllegalArgumentException("winBy must be at least 1");
        if (serveTimeLimit <= 0f) throw new IllegalArgumentException("serveTimeLimit must be positive");
    }

    /** FIVB: best of five, sets to 25, deciding set to 15, two-point lead, 8 s to serve. */
    public static MatchFormat official() {
        return new MatchFormat(3, 25, 15, 2, RuleMode.OFFICIAL, 8f);
    }

    /** Shorter and friendlier: best of three to 15, essential faults only, 15 s to serve. */
    public static MatchFormat simplified() {
        return new MatchFormat(2, 15, 15, 2, RuleMode.SIMPLIFIED, 15f);
    }

    public int maxSets() {
        return setsToWin * 2 - 1;
    }

    /** Points needed in set number {@code setNumber} (1-based). */
    public int pointsForSet(int setNumber) {
        return setNumber == maxSets() ? decidingSetPoints : pointsPerSet;
    }

    public boolean isOfficial() {
        return mode == RuleMode.OFFICIAL;
    }
}
