package com.volleyball.game.tactics;

import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;

import java.util.List;

/**
 * An attacking combination: who runs where and when, which set goes to whom, and what to do if
 * the plan cannot be played.
 *
 * <ul>
 *   <li><b>Preconditions</b>: the worst pass the play still works with ({@link #minPass}), the
 *       hitters it needs (the lanes of its runs and first option), a front-row setter for a dump.</li>
 *   <li><b>Positions and approach paths</b>: each {@link Run} starts at a spot and runs to its
 *       hitting spot (depth from the net, lateral + = right, in the team's frame) with a
 *       {@link Tempo}; decoys run and jump without getting the ball.</li>
 *   <li><b>Set and target hitter</b>: the first {@link Option}; the following options are the
 *       developments, chosen at the moment of the set if the first cannot be played.</li>
 *   <li><b>Cancellation</b>: a pass worse than {@link #minPass}, a setter who is late to the
 *       ball, a hitter who is not ready (down, late, collided) — then the next option, or the
 *       emergency high ball to the most convenient hitter.</li>
 * </ul>
 *
 * @param id          stable identifier (also the JSON key of custom plays)
 * @param name        display name
 * @param description what the play is for
 * @param minPass     worst first pass the play still works with
 * @param runs        hitters' approach paths (decoys included)
 * @param options     the sets in order of preference: the plan, then its developments
 * @param readBlock   choose among the options by the block (the set goes where the blockers are not)
 * @param emergency   the safety play after a poor pass: the most convenient hitter gets a high ball
 * @param builtIn     part of the game (custom plays come from the playbook file)
 */
public record Combination(String id, String name, String description, PassRating minPass, List<Run> runs,
                          List<Option> options, boolean readBlock, boolean emergency, boolean builtIn) {

    /**
     * One hitter's movement: from the start spot to the hitting spot, with a tempo.
     *
     * @param lane          which hitter (the player in that lane of the rotation)
     * @param startDepth    start of the approach: distance from the net (m)
     * @param startLateral  start of the approach: lateral position (m, + = right)
     * @param targetDepth   hitting spot: distance from the net
     * @param targetLateral hitting spot: lateral position
     * @param tempo         when the approach starts
     * @param decoy         runs and jumps to draw the block, never gets this set
     */
    public record Run(SetType.Lane lane, float startDepth, float startLateral, float targetDepth, float targetLateral,
                      Tempo tempo, boolean decoy) {
    }

    /**
     * A set and the hitter it goes to. A null set is the setter's dump (the setter attacks the
     * second ball).
     */
    public record Option(SetType set, SetType.Lane hitter) {
        public boolean dump() {
            return set == null;
        }

        public String label() {
            return dump() ? "Setter dump" : set.shortLabel + " (" + laneLabel(hitter) + ")";
        }
    }

    /** The run of a lane in this play, or null. */
    public Run runOf(SetType.Lane lane) {
        for (Run r : runs) if (r.lane() == lane) return r;
        return null;
    }

    /** True if the play needs the setter to attack (a dump). */
    public boolean isDump() {
        return !options.isEmpty() && options.get(0).dump();
    }

    /** Zone (1..6, the attacking team's frame) where the planned attack is expected. */
    public int predictedZone() {
        if (options.isEmpty() || options.get(0).dump()) return 2;
        SetType set = options.get(0).set();
        boolean front = set.depth < 3f;
        if (set.lateral < -1.5f) return front ? 4 : 5;
        if (set.lateral > 1.5f) return front ? 2 : 1;
        return front ? 3 : 6;
    }

    public static String laneLabel(SetType.Lane lane) {
        return switch (lane) {
            case OUTSIDE -> "outside";
            case MIDDLE -> "middle";
            case RIGHT -> "right side";
            case BACK_MIDDLE -> "back row, zone 6";
            case BACK_RIGHT -> "back row, zone 1";
        };
    }
}
