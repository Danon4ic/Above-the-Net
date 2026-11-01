package com.volleyball.game.ai;

import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;

/**
 * Where each role plays once the ball is served (the "switch" of a 5-1 system): whatever the
 * rotation, front-row players go to the attacking position of their role and back-row players
 * to their defensive zone. Before the serve everybody keeps the rotation order (overlap rule).
 *
 * <pre>
 *   front row:  outside hitter → zone 4 (left),  middle blocker → 3,  setter / opposite → 2 (right)
 *   back row:   outside hitter → zone 5 (left),  middle / libero → 6,  setter / opposite → 1 (right)
 * </pre>
 * The serve is received by the outside hitters and the libero; setters, opposites and middles
 * stay out of the reception.
 */
public final class RolePlan {
    private RolePlan() {
    }

    /** Zone (1..6) a player takes after the switch, by role and row. */
    public static int roleZone(VolleyballPlayer p, boolean frontRow) {
        return switch (p.role) {
            case OUTSIDE_HITTER -> frontRow ? 4 : 5;
            case MIDDLE_BLOCKER, LIBERO -> frontRow ? 3 : 6;
            case SETTER, OPPOSITE -> frontRow ? 2 : 1;
        };
    }

    /** Serve receivers: outside hitters and the libero. */
    public static boolean receivesServe(VolleyballPlayer p) {
        return p.role == PlayerRole.OUTSIDE_HITTER || p.role == PlayerRole.LIBERO;
    }

    /** Where a front-row attacker starts the approach (lateral in the team frame, + = right). */
    public static float approachLateral(VolleyballPlayer p) {
        return switch (p.role) {
            case OUTSIDE_HITTER -> -3.3f;
            case MIDDLE_BLOCKER -> 0.3f;
            default -> 3.3f;
        };
    }
}
