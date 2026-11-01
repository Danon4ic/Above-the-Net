package com.volleyball.game.rules;

import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Points and sets. A set ends only when a team reaches the set's target and leads by the
 * required margin (so 24:24 continues until someone is two points ahead); the match ends when
 * a team has won the required number of sets. After that no more points are accepted.
 */
public final class MatchScore {
    /** Final score of a finished set. */
    public record SetResult(int setNumber, int home, int away) {
        public CourtSide winner() {
            return home > away ? CourtSide.HOME : CourtSide.AWAY;
        }
    }

    /** What a point changed. */
    public record PointResult(boolean setWon, boolean matchWon, CourtSide winner) {
    }

    private final MatchFormat format;
    private final int[] points = new int[2];
    private final int[] sets = new int[2];
    private final List<SetResult> finishedSets = new ArrayList<>();
    private int setNumber = 1;
    private boolean matchOver;

    public MatchScore(MatchFormat format) {
        this.format = format;
    }

    public PointResult pointTo(CourtSide side) {
        if (matchOver) throw new IllegalStateException("The match is over");
        points[index(side)]++;
        if (!setOver()) return new PointResult(false, false, side);
        finishedSets.add(new SetResult(setNumber, points[0], points[1]));
        sets[index(side)]++;
        if (sets[index(side)] >= format.setsToWin()) {
            matchOver = true;
            return new PointResult(true, true, side);
        }
        setNumber++;
        points[0] = 0;
        points[1] = 0;
        return new PointResult(true, false, side);
    }

    /** True when the current points end the set. */
    private boolean setOver() {
        int target = format.pointsForSet(setNumber);
        int leader = Math.max(points[0], points[1]);
        return leader >= target && Math.abs(points[0] - points[1]) >= format.winBy();
    }

    public int points(CourtSide side) {
        return points[index(side)];
    }

    public int sets(CourtSide side) {
        return sets[index(side)];
    }

    public int setNumber() {
        return setNumber;
    }

    public boolean isMatchOver() {
        return matchOver;
    }

    public CourtSide matchWinner() {
        if (!matchOver) return null;
        return sets[0] > sets[1] ? CourtSide.HOME : CourtSide.AWAY;
    }

    public List<SetResult> finishedSets() {
        return Collections.unmodifiableList(finishedSets);
    }

    public MatchFormat format() {
        return format;
    }

    /** One point away from winning the set (set point / match point indicator). */
    public boolean isSetPoint(CourtSide side) {
        if (matchOver) return false;
        int mine = points[index(side)] + 1;
        int theirs = points[index(side.opposite())];
        return mine >= format.pointsForSet(setNumber) && mine - theirs >= format.winBy();
    }

    private static int index(CourtSide side) {
        return side == CourtSide.HOME ? 0 : 1;
    }
}
