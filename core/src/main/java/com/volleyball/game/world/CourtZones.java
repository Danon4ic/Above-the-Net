package com.volleyball.game.world;

import com.badlogic.gdx.math.Vector2;

/**
 * The six court zones (FIVB rule 7.4) on each side, seen by a team facing the net:
 * <pre>
 *        net
 *   4    3    2     front row
 *   5    6    1     back row
 * </pre>
 * Zone 1 is back right, 2 front right, 3 front centre, 4 front left, 5 back left, 6 back centre.
 *
 * <p>A team on HOME faces +X, so its right hand points to +Z; AWAY faces -X and its right is -Z.
 */
public final class CourtZones {
    /** Depth (distance from the net) of front-row and back-row base positions. */
    public static final float FRONT_DEPTH = 2.0f;
    public static final float BACK_DEPTH = 6.5f;
    /** Lateral offset of the left/right columns from the court's centre line. */
    public static final float COLUMN_OFFSET = 3.0f;
    /** Where players off the court sit: the team bench chairs on the scorer's side. */
    public static final float BENCH_Z = CourtDimensions.HALF_WIDTH + CourtDimensions.FREE_ZONE_SIDE + 1.2f;

    /** Chair of the n-th player on a team's bench (nine chairs around x = ±6 m). */
    public static Vector2 benchPosition(CourtSide side, int index, Vector2 out) {
        return benchPosition(side, index, 1f, out);
    }

    /**
     * As {@link #benchPosition(CourtSide, int, Vector2)} on the bench side {@code benchSign}
     * (+1: the usual +z side; -1 after the teams changed ends, see {@code GameWorld.benchSign}).
     */
    public static Vector2 benchPosition(CourtSide side, int index, float benchSign, Vector2 out) {
        return out.set(side.sign() * (6f + (Math.min(index, 8) - 4) * 0.55f), benchSign * BENCH_Z);
    }

    private CourtZones() {}

    /** Base position (x, z) of a zone on the given side. */
    public static Vector2 basePosition(int zone, CourtSide side, Vector2 out) {
        float depth = isFrontRow(zone) ? FRONT_DEPTH : BACK_DEPTH;
        // Lateral position in the team's own frame: +1 = right, -1 = left.
        int lateral = switch (zone) {
            case 1, 2 -> 1;
            case 3, 6 -> 0;
            case 4, 5 -> -1;
            default -> throw new IllegalArgumentException("Zone must be 1..6: " + zone);
        };
        float x = side.sign() * depth;
        float z = -side.sign() * lateral * COLUMN_OFFSET;
        return out.set(x, z);
    }

    public static boolean isFrontRow(int zone) {
        if (zone < 1 || zone > 6) throw new IllegalArgumentException("Zone must be 1..6: " + zone);
        return zone >= 2 && zone <= 4;
    }
}
