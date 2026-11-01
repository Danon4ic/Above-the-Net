package com.volleyball.game.mechanics;

import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtDimensions;

import java.util.List;

/**
 * Decides who is blocking and how. A player blocks when in a jump close to the net while the
 * ball is on (or coming from) the opponent's side and no attack of their own is requested.
 *
 * <p>The block itself is physical: the hands become colliders (see {@code CollisionSystem}). This
 * class only sets how far the hands press over the net — more for a good blocker and around the
 * top of the jump, less while still rising or already falling — and where they are shifted
 * (straight, line or diagonal). A late jump means low hands, a jump too far from the net means
 * no penetration; nothing stops the ball automatically.
 */
public final class BlockMechanic {
    /** The body centre must be at most this far from the net plane to get the hands over it. */
    public static final float MAX_NET_DISTANCE = 1.0f;
    /** Sideways hand shift for line / diagonal blocks. */
    public static final float STYLE_SHIFT = 0.2f;

    /** How far the hands reach over the net right now. */
    public static float penetration(VolleyballPlayer p) {
        float skill = PlayerAttributes.normalized(p.attributes.blocking);
        float reach = 0.06f + 0.26f * skill;
        // Hands press over best near the top of the jump.
        float timing = 1f - Math.min(0.75f, Math.abs(p.velocity.y) / 3.5f);
        return reach * timing;
    }

    public void update(List<VolleyballPlayer> players, Ball ball, BallHandling handling) {
        for (VolleyballPlayer p : players) {
            if (p.isOnCourt() && shouldBlock(p, ball, handling)) {
                float shift = styleShift(p, handling.blockStyle(p));
                p.setBlocking(true, penetration(p), shift);
                p.holdActionPose(ActionPose.BLOCK);
            } else {
                p.setBlocking(false, 0f);
            }
        }
    }

    static boolean shouldBlock(VolleyballPlayer p, Ball ball, BallHandling handling) {
        if (!p.isAirborne() || !p.isUpright() || handling.isArmed(p)) return false;
        if (Math.abs(p.position.x) > MAX_NET_DISTANCE) return false;
        if (!ball.isActive()) return false;
        boolean onOpponentSide = CourtDimensions.sideOf(ball.position.x) != p.side;
        // Moving towards this player's side (home side is -X).
        boolean comingOver = ball.velocity.x * p.side.sign() > 0f && Math.abs(ball.position.x) < 2f;
        return onOpponentSide || comingOver;
    }

    /** World-Z shift of the hands: towards the nearer sideline for a line block, towards the centre for a diagonal one. */
    static float styleShift(VolleyballPlayer p, BlockStyle style) {
        float towardsSideline = Math.signum(p.position.z);
        return switch (style) {
            case LINE -> towardsSideline * STYLE_SHIFT;
            case CROSS -> -towardsSideline * STYLE_SHIFT;
            case NORMAL -> 0f;
        };
    }
}
