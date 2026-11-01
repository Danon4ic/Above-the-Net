package com.volleyball.game.input;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.MoveIntent;

/**
 * Turns keyboard state into a {@link MoveIntent} for the human-controlled player.
 * Movement is relative to the camera: "forward" is the camera's view direction projected onto
 * the floor, so W always moves away from the viewer whatever camera is active.
 */
public final class InputController {
    /** Presses up to this long before the action is possible are still executed. */
    public static final double BUFFER_WINDOW = 0.15;
    /** Holding the jump key this long gives the full (highest) jump. */
    public static final double FULL_CHARGE_TIME = 0.45;
    /** Jump height factor for a tap and for a fully charged jump. */
    public static final float TAP_JUMP_FACTOR = 0.7f;
    public static final float CHARGED_JUMP_FACTOR = 1.05f;

    private final KeyState keys;
    private final InputBuffer buffer = new InputBuffer(BUFFER_WINDOW);
    /** Time of the last dive request (primary action pressed while holding the low stance). */
    private double diveRequestedAt = Double.NEGATIVE_INFINITY;
    /** Jump charging: the key went down at this time and is still held. */
    private boolean charging;
    private double chargeStartedAt;
    /** Charge (0..1) of the last released jump, used when the buffered jump executes. */
    private float releasedCharge = 1f;
    /** Face the point under the mouse cursor (WASD then moves sideways and backwards, facing it). */
    private boolean mouseFacing;
    private boolean facingTargetValid;
    private float facingTargetX;
    private float facingTargetZ;


    public InputController(KeyState keys) {
        this.keys = keys;
    }

    /** Records presses. Call once per rendered frame (key "just pressed" flags are per frame). */
    public void poll(double now) {
        // Jump on release: the longer the key is held (up to FULL_CHARGE_TIME), the higher the jump.
        if (keys.justPressed(PlayerAction.JUMP)) {
            charging = true;
            chargeStartedAt = now;
        }
        if (charging && !keys.isDown(PlayerAction.JUMP)) {
            charging = false;
            releasedCharge = chargeLevel(now - chargeStartedAt);
            buffer.press(PlayerAction.JUMP, now);
        }
        // Low stance + play the ball = dive (or fall sideways) in the direction of movement.
        if (keys.justPressed(PlayerAction.PRIMARY_ACTION) && keys.isDown(PlayerAction.CROUCH)) diveRequestedAt = now;
    }

    public boolean switchPlayerPressed() {
        return keys.justPressed(PlayerAction.SWITCH_PLAYER);
    }

    /** Ball action requested this frame, or null. The primary action wins if both are pressed. */
    public BallActionPress ballActionPressed() {
        if (keys.justPressed(PlayerAction.PRIMARY_ACTION)) return BallActionPress.PRIMARY;
        if (keys.justPressed(PlayerAction.SECONDARY_ACTION)) return BallActionPress.SECONDARY;
        return null;
    }

    /** -1, 0 or +1: variant selection change requested this frame. */
    public int variantChange() {
        return (keys.justPressed(PlayerAction.VARIANT_NEXT) ? 1 : 0) - (keys.justPressed(PlayerAction.VARIANT_PREVIOUS) ? 1 : 0);
    }

    /** Ball-action buttons, kept free of the mechanics package to avoid a dependency cycle. */
    public enum BallActionPress { PRIMARY, SECONDARY }

    /** Turns on facing the mouse point (person 1 with the mouse). */
    public void setMouseFacing(boolean enabled) {
        mouseFacing = enabled;
    }

    /** The floor point under the mouse cursor this frame (valid = the cursor is over the floor). */
    public void setFacingTarget(float x, float z, boolean valid) {
        facingTargetX = x;
        facingTargetZ = z;
        facingTargetValid = valid;
    }

    public void clearBuffer() {
        buffer.clear();
        diveRequestedAt = Double.NEGATIVE_INFINITY;
        charging = false;
    }

    /**
     * Fills the intent for one simulation step.
     *
     * @param cameraDirection current camera view direction (only its X/Z part is used)
     */
    public void fillIntent(MoveIntent intent, VolleyballPlayer player, Vector3 cameraDirection, double now) {
        intent.clear();
        float forwardX = cameraDirection.x;
        float forwardZ = cameraDirection.z;
        float length = (float) Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
        if (length < 1e-4f) {
            forwardX = 1f;
            forwardZ = 0f;
        } else {
            forwardX /= length;
            forwardZ /= length;
        }
        // Right-hand side on the floor: forward × up.
        float rightX = -forwardZ;
        float rightZ = forwardX;

        float forward = axis(PlayerAction.MOVE_FORWARD, PlayerAction.MOVE_BACK);
        float right = axis(PlayerAction.MOVE_RIGHT, PlayerAction.MOVE_LEFT);
        intent.direction.set(forwardX * forward + rightX * right, forwardZ * forward + rightZ * right);
        if (intent.direction.len2() > 1f) intent.direction.nor();
        intent.faceMovement = true;
        intent.sprint = keys.isDown(PlayerAction.SPRINT);
        // Facing the mouse: the body turns to the point under the cursor and the feet shuffle, so
        // the player can slide along the net for a block or square up for a set or a spike. A
        // sprint (Shift) still turns the body into the run.
        if (mouseFacing && facingTargetValid && !(intent.sprint && intent.direction.len2() > 0.01f)) {
            float dx = facingTargetX - player.position.x;
            float dz = facingTargetZ - player.position.z;
            if (dx * dx + dz * dz > 0.36f) {
                intent.facingYaw = (float) Math.atan2(-dz, dx);
                intent.faceMovement = false;
            }
        }
        intent.crouch = keys.isDown(PlayerAction.CROUCH);

        boolean canJump = !player.isAirborne() && player.recoveryTime() <= 0f;
        intent.jump = canJump && buffer.consume(PlayerAction.JUMP, now);
        if (intent.jump) intent.jumpHeightFactor = TAP_JUMP_FACTOR + (CHARGED_JUMP_FACTOR - TAP_JUMP_FACTOR) * releasedCharge;
        if (charging && canJump) intent.jumpChargeLevel = Math.max(0.05f, chargeLevel(now - chargeStartedAt));
        if (canJump && player.isUpright() && now - diveRequestedAt <= BUFFER_WINDOW) {
            intent.dive = true;
            intent.jump = false;
            diveRequestedAt = Double.NEGATIVE_INFINITY;
        }
    }

    /** 0 for a tap, 1 at FULL_CHARGE_TIME or longer. */
    static float chargeLevel(double heldSeconds) {
        return (float) Math.max(0.0, Math.min(1.0, heldSeconds / FULL_CHARGE_TIME));
    }

    /** True while the jump key is held down (charging). */
    public boolean isChargingJump() {
        return charging;
    }

    private float axis(PlayerAction positive, PlayerAction negative) {
        return (keys.isDown(positive) ? 1f : 0f) - (keys.isDown(negative) ? 1f : 0f);
    }
}
