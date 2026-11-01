package com.volleyball.game.render.anim;

import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.VolleyballPlayer;

/**
 * What a player's body is doing, as the animation sees it. The state is read from the
 * simulation every frame (never the other way round), so the animation can not drift away from
 * what the player really does: a player is JUMPING because the physics has them in the air, in
 * SPIKE because ball handling armed or played an attack.
 */
public enum PlayerAnimState {
    IDLE, READY, WALK, RUN, SPRINT, BACKPEDAL, SHUFFLE, APPROACH,
    JUMP_PREP, JUMP, JUMP_PEAK, LANDING,
    DIVE, GET_UP,
    RECEIVE, SET, SPIKE, BLOCK, SERVE,
    CELEBRATE, DISAPPOINTED;

    /** Ground speed (m/s) above which a player walks, runs and sprints. */
    public static final float WALK_SPEED = 0.25f;
    public static final float RUN_SPEED = 2.2f;
    public static final float SPRINT_SPEED = 5.0f;
    /** Vertical speed (m/s) below which a jump is at its top. */
    public static final float PEAK_SPEED = 1.2f;
    /** How long after touching down the landing (absorbing the impact) lasts, s. */
    public static final float LANDING_TIME = 0.32f;

    /**
     * The state of {@code p} now.
     *
     * @param forward      ground velocity along the player's facing (m/s, + = forwards)
     * @param side         ground velocity across it (m/s)
     * @param sinceLanding seconds since the player last touched down (large if long ago)
     * @param approaching  the player runs in for an attack (towards the net with the ball up on their side)
     */
    public static PlayerAnimState of(VolleyballPlayer p, float forward, float side, float sinceLanding, boolean approaching) {
        BodyState body = p.bodyState();
        if (body == BodyState.DIVING || body == BodyState.ON_FLOOR) return DIVE;
        if (body == BodyState.GETTING_UP) return GET_UP;
        ActionPose action = p.actionPose();
        switch (action) {
            case CELEBRATE, HIGH_FIVE -> {
                return CELEBRATE;
            }
            case CLAP, DISAPPOINTED -> {
                return DISAPPOINTED;
            }
            case PLATFORM -> {
                return RECEIVE;
            }
            case OVERHEAD -> {
                return SET;
            }
            case TOSS, HOLD_BALL -> {
                return SERVE;
            }
            case BLOCK -> {
                return BLOCK;
            }
            case SWING -> {
                return p.isAirborne() || !isServeSwing(p) ? SPIKE : SERVE;
            }
            default -> {
            }
        }
        if (p.isBlocking()) return BLOCK;
        if (p.isAirborne()) {
            return Math.abs(p.velocity.y) < PEAK_SPEED ? JUMP_PEAK : JUMP;
        }
        if (p.jumpCharge() > 0f) return JUMP_PREP;
        if (sinceLanding < LANDING_TIME) return LANDING;
        float speed = (float) Math.sqrt(forward * forward + side * side);
        if (speed < WALK_SPEED) return p.isCrouching() ? READY : IDLE;
        if (Math.abs(side) > Math.abs(forward) * 1.2f) return SHUFFLE;
        if (forward < -0.3f * speed) return BACKPEDAL;
        if (p.isCrouching()) return SHUFFLE;
        if (approaching && speed > RUN_SPEED) return APPROACH;
        if (speed > SPRINT_SPEED) return SPRINT;
        return speed > RUN_SPEED ? RUN : WALK;
    }

    private static boolean isServeSwing(VolleyballPlayer p) {
        return switch (p.swingStyle()) {
            case FLOAT_SERVE, TOPSPIN_SERVE, UNDERHAND_SERVE -> true;
            default -> false;
        };
    }

    /** States in which the legs walk or run (the step cycle drives them). */
    public boolean stepping() {
        return switch (this) {
            case WALK, RUN, SPRINT, BACKPEDAL, SHUFFLE, APPROACH -> true;
            default -> false;
        };
    }
}
