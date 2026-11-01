package com.volleyball.game.rules;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.Net;

import java.util.List;

/**
 * Match-level rules: score, sets, serving team and rotation (FIVB rules 6, 7, 12).
 *
 * <ul>
 *   <li>The winner of a rally scores a point.</li>
 *   <li>If the receiving team wins it, it gains the serve and rotates one position clockwise
 *       (side-out); if the serving team wins, the same player serves again.</li>
 *   <li>A set ends at the set's target with a two-point lead; the next set starts from the
 *       starting line-ups, served first by the team that received first in the previous set.</li>
 *   <li>A replayed rally changes nothing.</li>
 * </ul>
 */
public final class MatchRules {
    /** What happened after a rally, for the presentation layer. */
    public record PointSummary(RallyOutcome outcome, boolean sideOut, boolean setWon, boolean matchWon,
                               CourtSide nextServingSide) {
    }

    private final MatchFormat format;
    private final MatchScore score;
    private final RotationManager rotation;
    private final RallyManager rally;
    private final SubstitutionManager substitutions = new SubstitutionManager();
    private CourtSide servingSide;
    private CourtSide firstServerOfSet;
    /** The coin toss before the deciding set (rule 7.1). */
    private final java.util.SplittableRandom toss;

    public MatchRules(MatchFormat format, RotationManager rotation, List<VolleyballPlayer> players, Net net) {
        this(format, rotation, players, net, 0L);
    }

    public MatchRules(MatchFormat format, RotationManager rotation, List<VolleyballPlayer> players, Net net, long seed) {
        this.toss = new java.util.SplittableRandom(seed);
        this.format = format;
        this.rotation = rotation;
        this.score = new MatchScore(format);
        this.rally = new RallyManager(format, rotation, players, net);
    }

    public void startMatch(CourtSide firstServer) {
        servingSide = firstServer;
        firstServerOfSet = firstServer;
        rotation.reset();
        substitutions.newSet(rotation);
    }

    public SubstitutionManager substitutions() {
        return substitutions;
    }

    /** Whistles the next serve. */
    public void startRally(double now) {
        if (score.isMatchOver()) throw new IllegalStateException("The match is over");
        rally.start(servingSide, now);
    }

    /** Applies a finished rally to the score and the rotation. */
    public PointSummary applyOutcome(RallyOutcome outcome) {
        if (outcome.isReplay()) return new PointSummary(outcome, false, false, false, servingSide);
        CourtSide winner = outcome.winner();
        MatchScore.PointResult point = score.pointTo(winner);
        boolean sideOut = winner != servingSide;
        if (point.setWon()) {
            if (!point.matchWon()) {
                // New set: starting line-ups; the team that received first in the last set serves first,
                // except in the deciding set, which starts with a new coin toss (rule 7.1).
                boolean deciding = format.maxSets() > 1 && score.setNumber() == format.maxSets();
                firstServerOfSet = deciding ? (toss.nextBoolean() ? CourtSide.HOME : CourtSide.AWAY) : firstServerOfSet.opposite();
                servingSide = firstServerOfSet;
                rotation.reset();
                substitutions.newSet(rotation);
            }
            return new PointSummary(outcome, sideOut, true, point.matchWon(), servingSide);
        }
        if (sideOut) {
            rotation.rotate(winner);
            servingSide = winner;
        }
        return new PointSummary(outcome, sideOut, false, false, servingSide);
    }

    public MatchFormat format() {
        return format;
    }

    public MatchScore score() {
        return score;
    }

    public RotationManager rotation() {
        return rotation;
    }

    public RallyManager rally() {
        return rally;
    }

    public CourtSide servingSide() {
        return servingSide;
    }

    public int currentServer() {
        return rotation.server(servingSide);
    }
}
