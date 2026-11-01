package com.volleyball.game.rules;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;

import java.util.function.IntFunction;

/**
 * Stateless checks of individual rules, usable without the game running.
 *
 * <p>Positions are judged in a team's own frame: <em>depth</em> is the distance from the net
 * into the team's court, <em>lateral</em> is the distance to the team's right (facing the net).
 */
public final class ViolationDetector {
    /** Length of the shoe in front of the player's position (toe), for foot faults. */
    public static final float TOE_LENGTH = 0.12f;

    /** Front-row zone and the back-row zone behind it (rule 7.4.3.1). */
    private static final int[][] FRONT_BACK_PAIRS = {{2, 1}, {3, 6}, {4, 5}};
    /** Pairs (right, left) in the same row (rule 7.4.3.2). */
    private static final int[][] RIGHT_LEFT_PAIRS = {{2, 3}, {3, 4}, {1, 6}, {6, 5}};

    private ViolationDetector() {}

    public static float depth(Vector3 position, CourtSide side) {
        return position.x * side.sign();
    }

    public static float lateral(Vector3 position, CourtSide side) {
        return -position.z * side.sign();
    }

    /**
     * Overlap check at the moment of the serve. Each back-row player must be further from the
     * net than the corresponding front-row player, and players in a row must keep their
     * right-to-left order. The server is exempt.
     *
     * @param positions player id → current position
     * @param serverId  id of the serving player (exempt), or -1 for the receiving team
     * @return id of a player in a wrong position, or -1 if the team is correctly placed
     */
    public static int positionalFault(CourtSide side, RotationManager rotation, IntFunction<Vector3> positions, int serverId) {
        for (int[] pair : FRONT_BACK_PAIRS) {
            int front = rotation.playerInZone(side, pair[0]);
            int back = rotation.playerInZone(side, pair[1]);
            if (front == serverId || back == serverId) continue;
            if (depth(positions.apply(back), side) <= depth(positions.apply(front), side)) return back;
        }
        for (int[] pair : RIGHT_LEFT_PAIRS) {
            int right = rotation.playerInZone(side, pair[0]);
            int left = rotation.playerInZone(side, pair[1]);
            if (right == serverId || left == serverId) continue;
            if (lateral(positions.apply(right), side) <= lateral(positions.apply(left), side)) return left;
        }
        return -1;
    }

    /**
     * Foot fault: at the moment of the serve the server must not touch the court (end line
     * included) or the floor outside the service zone. For a jump serve the take-off spot counts.
     */
    public static boolean isFootFault(VolleyballPlayer server, CourtSide side) {
        Vector3 feet = server.isAirborne() ? server.takeoffPosition : server.position;
        float d = depth(feet, side);
        boolean onCourt = d - TOE_LENGTH <= CourtDimensions.HALF_LENGTH;
        boolean outsideZone = Math.abs(feet.z) > CourtDimensions.HALF_WIDTH;
        return onCourt || outsideZone;
    }

    /** The player's feet touch the court (end line included) on {@code side}'s end. */
    public static boolean onCourt(VolleyballPlayer player, CourtSide side) {
        return depth(player.position, side) - TOE_LENGTH <= CourtDimensions.HALF_LENGTH;
    }

    /** The ball is completely higher than the top of the net. */
    public static boolean ballEntirelyAboveNet(float ballCentreY, float netTop) {
        return ballCentreY - Ball.RADIUS > netTop;
    }

    /**
     * Back-row attack (rule 13.2.2): a back-row player may hit an attack with the ball entirely
     * above the net only if the take-off was from behind the attack line.
     */
    public static boolean isBackRowAttackFault(VolleyballPlayer attacker, boolean backRow, float contactY, float netTop) {
        if (!backRow || !ballEntirelyAboveNet(contactY, netTop)) return false;
        Vector3 feet = attacker.isAirborne() ? attacker.takeoffPosition : attacker.position;
        return depth(feet, attacker.side) <= CourtDimensions.ATTACK_LINE_DISTANCE;
    }

    /** Libero may not complete an attack hit with the ball entirely above the net (rule 19.3.1.2). */
    public static boolean isLiberoAttackFault(VolleyballPlayer attacker, float contactY, float netTop) {
        return attacker.role == PlayerRole.LIBERO && ballEntirelyAboveNet(contactY, netTop);
    }

    /** True if the player is standing (or took off) in the own front zone. */
    public static boolean inFrontZone(VolleyballPlayer p) {
        Vector3 feet = p.isAirborne() ? p.takeoffPosition : p.position;
        return depth(feet, p.side) <= CourtDimensions.ATTACK_LINE_DISTANCE;
    }
}
