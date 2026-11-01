package com.volleyball.game.physics;

import com.badlogic.gdx.math.MathUtils;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.Net;

import java.util.List;

/**
 * Moves players: acceleration-limited running, sprinting, low stance, jumping and landing.
 *
 * <p>Players do not teleport: velocity changes at most by the acceleration (or the stronger
 * braking deceleration) times dt, and airborne players cannot steer. Ratings set the limits —
 * elite indoor players reach ~5.5 m/s over a few metres and jump 0.45–0.95 m.
 */
public final class PlayerLocomotion {
    public static final float SPRINT_FACTOR = 1.2f;
    public static final float CROUCH_FACTOR = 0.55f;
    public static final float RECOVERY_FACTOR = 0.3f;
    /** Short landing absorption before the player can move at full speed again. */
    public static final float LANDING_RECOVERY = 0.18f;
    public static final float TURN_RATE = 9f;
    /**
     * Above this speed a player turns to run when sprinting far off the watched direction, or
     * when moving almost straight away from it. Otherwise players shuffle, facing the ball.
     */
    public static final float RUN_TURN_SPEED = 2.6f;
    public static final float RUN_TURN_ANGLE = 70f * MathUtils.degreesToRadians;
    public static final float BACKWARDS_TURN_ANGLE = 125f * MathUtils.degreesToRadians;

    // Dive / fall: a short low launch, a slide on the floor, then getting up.
    public static final float DIVE_TIME = 0.32f;
    public static final float FLOOR_TIME = 0.6f;
    public static final float GET_UP_TIME = 0.5f;
    public static final float DIVE_MIN_SPEED = 3.5f;
    public static final float DIVE_MAX_SPEED = 6.0f;
    public static final float DIVE_BOOST = 2.5f;
    public static final float FLOOR_FRICTION = 7f;
    /** Movement speed factor while a jump is being charged (the player is sinking down). */
    public static final float CHARGING_FACTOR = 0.6f;
    /** Running or jumping into the net faster than this counts as touching it. */
    public static final float NET_CONTACT_SPEED = 0.8f;
    /** Share of the horizontal run-up speed kept in a jump (the penultimate step brakes the rest). */
    public static final float TAKEOFF_CARRY = 0.5f;
    /** A dive more than this far off the facing direction is a sideways fall with a roll. */
    public static final float ROLL_ANGLE = 60f * MathUtils.degreesToRadians;
    /** How fast the body turns into a forward dive. */
    public static final float DIVE_TURN_RATE = 20f;
    /** Below this speed a player controlled with "face movement" turns back to the ball / net. */
    public static final float FACE_MOVEMENT_SPEED = 0.6f;
    /** Minimum distance between two players' centres. */
    public static final float PERSONAL_SPACE = 0.62f;
    /**
     * Horizontal radius of a player's body (torso plus arms). The body centre must stay at least
     * this far from the net plane: the net is a solid obstacle for the body.
     */
    public static final float BODY_RADIUS = 0.22f;
    /** Fixed obstacles on the floor around the net: posts with padding and the referee stand. */
    private static final float[][] OBSTACLES = {
            // x, z, radius
            {0f, Net.POST_DISTANCE_FROM_SIDELINE + CourtDimensions.HALF_WIDTH, 0.14f},
            {0f, -(Net.POST_DISTANCE_FROM_SIDELINE + CourtDimensions.HALF_WIDTH), 0.14f},
            {0f, -(Net.POST_DISTANCE_FROM_SIDELINE + CourtDimensions.HALF_WIDTH + 0.75f), 0.75f},
    };

    public static float maxRunSpeed(PlayerAttributes attributes) {
        return PlayerAttributes.scale(attributes.speed, 3.6f, 5.6f);
    }

    public static float acceleration(PlayerAttributes attributes) {
        return PlayerAttributes.scale(attributes.acceleration, 9f, 18f);
    }

    /** Standing vertical jump height (centre of mass rise), metres. */
    public static float jumpHeight(PlayerAttributes attributes) {
        return PlayerAttributes.scale(attributes.jump, 0.40f, 0.95f);
    }

    /** Take-off vertical speed; a run-up adds up to 15 % jump height (approach jump). */
    public static float takeoffSpeed(VolleyballPlayer player) {
        return takeoffSpeed(player, 1f);
    }

    /** Take-off vertical speed for a jump of {@code heightFactor} times the player's normal height. */
    public static float takeoffSpeed(VolleyballPlayer player, float heightFactor) {
        float approachBonus = 1f + 0.15f * MathUtils.clamp(player.groundSpeed() / maxRunSpeed(player.attributes), 0f, 1f);
        return (float) Math.sqrt(2f * PhysicsConstants.GRAVITY * jumpHeight(player.attributes) * approachBonus * heightFactor);
    }

    /** A workhorse (trait) spends this share of the usual stamina. */
    static final float WORKHORSE_DRAIN = 0.6f;
    /** Stamina (s per second) spent sprinting and running, and restored standing or walking. */
    static final float SPRINT_DRAIN = 0.045f;
    static final float RUN_DRAIN = 0.012f;
    static final float REST_RECOVERY = 0.03f;
    static final float WALK_RECOVERY = 0.012f;
    /** Stamina spent by a full jump and by a dive. */
    static final float JUMP_COST = 0.03f;
    static final float DIVE_COST = 0.05f;
    /** Stamina never drops below this: a tired player is slower, not frozen. */
    static final float MIN_STAMINA = 0.15f;

    private boolean fatigue = true;

    /** Fatigue on (players tire and recover) or off (arcade: always fresh). */
    public void setFatigue(boolean enabled) {
        fatigue = enabled;
    }

    public boolean fatigue() {
        return fatigue;
    }

    public void update(VolleyballPlayer player, MoveIntent intent, float dt) {
        player.previousPosition.set(player.position);
        player.previousFacingYaw = player.facingYaw;
        player.tickRecovery(dt);
        player.tickBodyState(dt);
        updateStamina(player, dt);

        switch (player.bodyState()) {
            case DIVING -> updateDive(player, dt);
            case ON_FLOOR -> updateOnFloor(player, dt);
            case GETTING_UP -> updateGettingUp(player);
            default -> {
                if (player.isAirborne()) {
                    updateAirborne(player, dt);
                } else if (intent.dive && player.recoveryTime() <= 0f) {
                    startDive(player, intent);
                } else {
                    updateGrounded(player, intent, dt);
                }
            }
        }
        updateFacing(player, intent, dt);
    }

    /**
     * Fatigue: running hard costs stamina, standing and walking give it back (slowly; the breaks
     * between rallies restore more). Jumps and dives cost their share when they happen.
     */
    private void updateStamina(VolleyballPlayer player, float dt) {
        if (!fatigue) {
            player.setStamina(1f);
            return;
        }
        if (player.isAirborne() || !player.isUpright()) return;
        float speed = player.groundSpeed();
        float run = maxRunSpeed(player.attributes);
        float change;
        if (speed > run * 1.02f) change = -SPRINT_DRAIN;
        else if (speed > 2.5f) change = -RUN_DRAIN;
        else if (speed > 0.6f) change = WALK_RECOVERY;
        else change = REST_RECOVERY;
        if (change < 0f && player.has(PlayerTrait.WORKHORSE)) change *= WORKHORSE_DRAIN;
        player.setStamina(Math.max(Math.min(player.stamina(), MIN_STAMINA), player.stamina() + change * dt));
    }

    private void spend(VolleyballPlayer player, float cost) {
        if (player.has(PlayerTrait.WORKHORSE)) cost *= WORKHORSE_DRAIN;
        if (fatigue) player.setStamina(Math.max(MIN_STAMINA, player.stamina() - cost));
    }

    private void updateGrounded(VolleyballPlayer player, MoveIntent intent, float dt) {
        player.setCrouching(intent.crouch);
        // A tired player runs a little slower (at most 12 %).
        float limit = maxRunSpeed(player.attributes) * player.fatigueFactor(0.12f);
        if (intent.sprint && !intent.crouch) limit *= SPRINT_FACTOR;
        if (intent.crouch) limit *= CROUCH_FACTOR;
        if (intent.jumpChargeLevel > 0f) limit *= CHARGING_FACTOR;
        player.setJumpCharge(intent.jumpChargeLevel);
        if (player.recoveryTime() > 0f) limit *= RECOVERY_FACTOR;

        float inputLength = Math.min(1f, intent.direction.len());
        float targetX = 0f;
        float targetZ = 0f;
        if (inputLength > 1e-3f) {
            targetX = intent.direction.x / intent.direction.len() * inputLength * limit;
            targetZ = intent.direction.y / intent.direction.len() * inputLength * limit;
        }
        float dvx = targetX - player.velocity.x;
        float dvz = targetZ - player.velocity.z;
        float dv = (float) Math.sqrt(dvx * dvx + dvz * dvz);
        // Braking (target slower than current, or opposite) is stronger than accelerating.
        boolean braking = targetX * player.velocity.x + targetZ * player.velocity.z
                < player.velocity.x * player.velocity.x + player.velocity.z * player.velocity.z;
        float maxChange = acceleration(player.attributes) * (braking ? 1.6f : 1f) * dt;
        if (dv > maxChange) {
            dvx *= maxChange / dv;
            dvz *= maxChange / dv;
        }
        player.velocity.x += dvx;
        player.velocity.z += dvz;
        player.velocity.y = 0f;
        player.position.x += player.velocity.x * dt;
        player.position.z += player.velocity.z * dt;
        player.addStride(player.groundSpeed() * dt);

        if (intent.jump && player.recoveryTime() <= 0f) {
            // The last step of an approach plants the feet: most of the run-up turns into height.
            player.velocity.x *= TAKEOFF_CARRY;
            player.velocity.z *= TAKEOFF_CARRY;
            // A tired player jumps a little lower (at most 10 % of the height).
            player.velocity.y = takeoffSpeed(player, intent.jumpHeightFactor * player.fatigueFactor(0.1f));
            spend(player, JUMP_COST * Math.max(0.4f, intent.jumpHeightFactor));
            player.setJumpCharge(0f);
            player.recordTakeoff();
            player.setAirborne(true);
            player.setCrouching(false);
        }
    }

    /** Launches a dive in the intended direction (or straight ahead), keeping part of the run-up speed. */
    private void startDive(VolleyballPlayer player, MoveIntent intent) {
        spend(player, DIVE_COST);
        float dx = intent.direction.x;
        float dz = intent.direction.y;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-3f) {
            dx = player.facingX();
            dz = player.facingZ();
        } else {
            dx /= length;
            dz /= length;
        }
        float along = Math.max(0f, player.velocity.x * dx + player.velocity.z * dz);
        float speed = MathUtils.clamp(along + DIVE_BOOST, DIVE_MIN_SPEED, DIVE_MAX_SPEED);
        player.velocity.set(dx * speed, 0f, dz * speed);
        player.diveDirection.set(dx, 0f, dz);
        // Far off the facing (typically shuffling sideways in the low stance) it is a sideways
        // fall with a roll; otherwise the player turns into a chest-first dive.
        float diveYaw = MathUtils.atan2(-dz, dx);
        player.setDiveRoll(Math.abs(wrap(diveYaw - player.facingYaw)) > ROLL_ANGLE);
        player.setCrouching(false);
        player.setBodyState(BodyState.DIVING);
    }

    private void updateDive(VolleyballPlayer player, float dt) {
        if (!player.isDiveRoll()) {
            float diveYaw = MathUtils.atan2(-player.diveDirection.z, player.diveDirection.x);
            float delta = wrap(diveYaw - player.facingYaw);
            float maxTurn = DIVE_TURN_RATE * dt;
            player.facingYaw = wrap(player.facingYaw + MathUtils.clamp(delta, -maxTurn, maxTurn));
        }
        float keep = (float) Math.exp(-1.5f * dt);
        player.velocity.x *= keep;
        player.velocity.z *= keep;
        moveHorizontally(player, dt);
        if (player.bodyStateTime() >= DIVE_TIME) player.setBodyState(BodyState.ON_FLOOR);
    }

    private void updateOnFloor(VolleyballPlayer player, float dt) {
        float speed = player.groundSpeed();
        if (speed > 0f) {
            float slowed = Math.max(0f, speed - FLOOR_FRICTION * dt);
            player.velocity.x *= slowed / speed;
            player.velocity.z *= slowed / speed;
        }
        moveHorizontally(player, dt);
        if (player.bodyStateTime() >= FLOOR_TIME) {
            player.velocity.setZero();
            player.setBodyState(BodyState.GETTING_UP);
        }
    }

    private void updateGettingUp(VolleyballPlayer player) {
        player.velocity.setZero();
        if (player.bodyStateTime() >= GET_UP_TIME) {
            player.setBodyState(BodyState.UPRIGHT);
            player.setRecoveryTime(LANDING_RECOVERY);
        }
    }

    private static void moveHorizontally(VolleyballPlayer player, float dt) {
        player.position.x += player.velocity.x * dt;
        player.position.z += player.velocity.z * dt;
        player.velocity.y = 0f;
    }

    private void updateAirborne(VolleyballPlayer player, float dt) {
        player.velocity.y -= PhysicsConstants.GRAVITY * dt;
        player.position.mulAdd(player.velocity, dt);
        if (player.position.y <= 0f) {
            player.position.y = 0f;
            player.velocity.y = 0f;
            // Landing absorbs part of the horizontal momentum.
            player.velocity.x *= 0.5f;
            player.velocity.z *= 0.5f;
            player.setAirborne(false);
            player.setRecoveryTime(LANDING_RECOVERY);
        }
    }

    /**
     * Turns the body (never in the air or on the floor). Priority: an explicit facing request;
     * otherwise the player watches the ball (or the net), shuffling while facing it — but a fast
     * run far off that direction makes them turn and run.
     */
    private void updateFacing(VolleyballPlayer player, MoveIntent intent, float dt) {
        if (player.isAirborne() || !player.isUpright()) return;
        float target;
        if (!Float.isNaN(intent.facingYaw)) {
            target = intent.facingYaw;
        } else if (intent.faceMovement && !player.isCrouching() && player.groundSpeed() > FACE_MOVEMENT_SPEED) {
            target = MathUtils.atan2(-player.velocity.z, player.velocity.x);
        } else {
            float watch = Float.isNaN(intent.attentionYaw) ? VolleyballPlayer.facingNetYaw(player.side) : intent.attentionYaw;
            target = watch;
            if (player.groundSpeed() > RUN_TURN_SPEED) {
                float moveYaw = MathUtils.atan2(-player.velocity.z, player.velocity.x);
                float off = Math.abs(wrap(moveYaw - watch));
                if ((intent.sprint && off > RUN_TURN_ANGLE) || off > BACKWARDS_TURN_ANGLE) target = moveYaw;
            }
        }
        float delta = wrap(target - player.facingYaw);
        float maxTurn = TURN_RATE * dt;
        player.facingYaw = wrap(player.facingYaw + MathUtils.clamp(delta, -maxTurn, maxTurn));
    }

    /**
     * Keeps players apart and inside the playing area. Overlapping players are pushed apart
     * symmetrically; a player cannot run through the net to the opponent's side.
     */
    public void resolvePlayerConstraints(List<VolleyballPlayer> players) {
        for (int i = 0; i < players.size(); i++) {
            VolleyballPlayer a = players.get(i);
            if (!a.isOnCourt()) continue;
            for (int j = i + 1; j < players.size(); j++) {
                VolleyballPlayer b = players.get(j);
                if (!b.isOnCourt()) continue;
                float dx = b.position.x - a.position.x;
                float dz = b.position.z - a.position.z;
                float distance2 = dx * dx + dz * dz;
                if (distance2 >= PERSONAL_SPACE * PERSONAL_SPACE) continue;
                float distance = (float) Math.sqrt(distance2);
                float nx;
                float nz;
                if (distance < 1e-4f) {
                    // Exactly coincident players: split them deterministically along Z.
                    nx = 0f;
                    nz = 1f;
                } else {
                    nx = dx / distance;
                    nz = dz / distance;
                }
                float push = (PERSONAL_SPACE - distance) / 2f;
                a.position.x -= nx * push;
                a.position.z -= nz * push;
                b.position.x += nx * push;
                b.position.z += nz * push;
            }
        }
        for (VolleyballPlayer player : players) if (player.isOnCourt()) clampToArea(player);
    }

    static void clampToArea(VolleyballPlayer player) {
        pushOutOfObstacles(player);
        float maxX = CourtDimensions.HALF_LENGTH + CourtDimensions.FREE_ZONE_END - 0.3f;
        float maxZ = CourtDimensions.HALF_WIDTH + CourtDimensions.FREE_ZONE_SIDE - 0.3f;
        float x = player.position.x;
        // The net (and its extension to the posts and beyond) separates the teams: a body can
        // never pass through it. Contacts with the net and centre-line faults are judged by the rules.
        if (player.side == CourtSide.HOME) {
            x = MathUtils.clamp(x, -maxX, -BODY_RADIUS);
        } else {
            x = MathUtils.clamp(x, BODY_RADIUS, maxX);
        }
        if (x != player.position.x) {
            // Stopped by the net (not by the end of the free zone) while moving into it: the body
            // touched the net. Between the posts only.
            boolean atNet = Math.abs(x) < 1f;
            boolean intoNet = player.velocity.x * -player.side.sign() > NET_CONTACT_SPEED;
            if (atNet && intoNet && Math.abs(player.position.z) <= CourtDimensions.HALF_WIDTH + Net.POST_DISTANCE_FROM_SIDELINE) {
                player.markNetContact();
            }
            player.position.x = x;
            player.velocity.x = 0f;
        }
        float z = MathUtils.clamp(player.position.z, -maxZ, maxZ);
        if (z != player.position.z) {
            player.position.z = z;
            player.velocity.z = 0f;
        }
    }

    private static void pushOutOfObstacles(VolleyballPlayer player) {
        for (float[] obstacle : OBSTACLES) {
            float dx = player.position.x - obstacle[0];
            float dz = player.position.z - obstacle[1];
            float minDistance = obstacle[2] + BODY_RADIUS;
            float distance2 = dx * dx + dz * dz;
            if (distance2 >= minDistance * minDistance) continue;
            float distance = (float) Math.sqrt(distance2);
            if (distance < 1e-4f) {
                // Exactly on the obstacle centre: step back towards the player's own court.
                dx = player.side.sign();
                dz = 0f;
                distance = 1f;
            }
            player.position.x = obstacle[0] + dx / distance * minDistance;
            player.position.z = obstacle[1] + dz / distance * minDistance;
            // Remove the velocity component pointing into the obstacle.
            float nx = dx / distance;
            float nz = dz / distance;
            float into = player.velocity.x * nx + player.velocity.z * nz;
            if (into < 0f) {
                player.velocity.x -= into * nx;
                player.velocity.z -= into * nz;
            }
        }
    }

    private static float wrap(float angle) {
        while (angle > MathUtils.PI) angle -= MathUtils.PI2;
        while (angle < -MathUtils.PI) angle += MathUtils.PI2;
        return angle;
    }
}
