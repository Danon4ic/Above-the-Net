package com.volleyball.game.rules;

import com.volleyball.game.world.CourtSide;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Regular substitutions (FIVB rule 15.5 - 15.6), counted per team and set:
 * <ul>
 *   <li>at most {@link #MAX_PER_SET} substitutions per set;</li>
 *   <li>a starting player may leave the game once and come back once, only to the same position
 *       in the line-up and only for the substitute who replaced them;</li>
 *   <li>a substitute may enter once per set, only for a starting player, and can only be replaced
 *       by that same starting player;</li>
 *   <li>the libero is never part of a regular substitution (it has its own replacements).</li>
 * </ul>
 * Substitutions are made while the ball is out of play; the match applies them before the next serve.
 */
public final class SubstitutionManager {
    public static final int MAX_PER_SET = 6;

    public enum Result {
        OK("Substitution"),
        LIMIT("No substitutions left in this set (6 per set)"),
        NOT_ON_COURT("That player is not in the line-up"),
        ALREADY_ON_COURT("That player is already playing"),
        LIBERO("The libero is not substituted (it replaces players itself)"),
        STARTER_ALREADY_OUT("This player already left the game once in this set"),
        SUBSTITUTE_USED("This substitute already played in this set"),
        WRONG_PARTNER("A substitute can only be replaced by the player they came in for");

        public final String label;

        Result(String label) {
            this.label = label;
        }
    }

    private final int[] count = new int[2];
    /** Per side: player who came in → player who went out (and back), for the current set. */
    private final Map<Integer, Integer>[] partners;
    /** Per side: starters who left once / substitutes who entered once in this set. */
    private final Set<Integer>[] startersOut;
    private final Set<Integer>[] substitutesIn;
    /** Per side: the line-up the set started with. */
    private final Set<Integer>[] starters;

    @SuppressWarnings("unchecked")
    public SubstitutionManager() {
        partners = new Map[]{new HashMap<>(), new HashMap<>()};
        startersOut = new Set[]{new HashSet<>(), new HashSet<>()};
        substitutesIn = new Set[]{new HashSet<>(), new HashSet<>()};
        starters = new Set[]{new HashSet<>(), new HashSet<>()};
    }

    /** A new set: counts reset, the line-ups in the rotation are the starters. */
    public void newSet(RotationManager rotation) {
        for (CourtSide side : CourtSide.values()) {
            int i = side.ordinal();
            count[i] = 0;
            partners[i].clear();
            startersOut[i].clear();
            substitutesIn[i].clear();
            starters[i].clear();
            for (int zone = 1; zone <= 6; zone++) starters[i].add(rotation.slotPlayer(side, zone));
        }
    }

    public int used(CourtSide side) {
        return count[side.ordinal()];
    }

    public int remaining(CourtSide side) {
        return MAX_PER_SET - used(side);
    }

    /** The player {@code playerId} went out for (or came in for), or -1. */
    public int partner(CourtSide side, int playerId) {
        Integer p = partners[side.ordinal()].get(playerId);
        return p == null ? -1 : p;
    }

    /**
     * Whether {@code inId} may replace {@code outId} now.
     *
     * @param liberoId the team's libero (-1 if none)
     */
    public Result check(CourtSide side, int outId, int inId, RotationManager rotation, int liberoId) {
        int i = side.ordinal();
        if (outId == liberoId || inId == liberoId) return Result.LIBERO;
        if (count[i] >= MAX_PER_SET) return Result.LIMIT;
        if (rotation.slotZone(side, outId) < 0) return Result.NOT_ON_COURT;
        if (rotation.slotZone(side, inId) > 0) return Result.ALREADY_ON_COURT;
        boolean outIsStarter = starters[i].contains(outId);
        if (outIsStarter) {
            // A starter leaves (once) for a substitute who has not played yet.
            if (startersOut[i].contains(outId)) return Result.STARTER_ALREADY_OUT;
            if (substitutesIn[i].contains(inId) || starters[i].contains(inId)) return Result.SUBSTITUTE_USED;
            return Result.OK;
        }
        // A substitute on court can only be replaced by the starter they came in for.
        return partner(side, outId) == inId ? Result.OK : Result.WRONG_PARTNER;
    }

    /** Checks and makes the substitution in the rotation; returns the result. */
    public Result substitute(CourtSide side, int outId, int inId, RotationManager rotation, int liberoId) {
        Result result = check(side, outId, inId, rotation, liberoId);
        if (result != Result.OK) return result;
        int i = side.ordinal();
        rotation.substitute(side, outId, inId);
        count[i]++;
        if (starters[i].contains(outId)) {
            startersOut[i].add(outId);
            substitutesIn[i].add(inId);
            partners[i].put(inId, outId);
            partners[i].put(outId, inId);
        }
        return Result.OK;
    }
}
