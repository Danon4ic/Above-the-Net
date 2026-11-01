package com.volleyball.game.physics;

import com.badlogic.gdx.math.Vector2;

/**
 * What a player wants to do with their body this step. Produced by the human input controller or
 * by the AI; consumed by {@link PlayerLocomotion}. Direction is in world X/Z, length 0..1.
 */
public final class MoveIntent {
    public final Vector2 direction = new Vector2();
    public boolean sprint;
    public boolean crouch;
    public boolean jump;
    /** Dive (or sideways fall) in the movement direction to reach a low, distant ball. */
    public boolean dive;
    /**
     * Height factor for a jump this step (1 = the player's normal jump). A human charges the jump
     * by holding the key; the AI always jumps normally.
     */
    public float jumpHeightFactor = 1f;
    /** 0..1 while a jump is being charged (the player sinks lower, moves slower); 0 otherwise. */
    public float jumpChargeLevel;
    /**
     * Face the direction of movement while moving (human control, like any third-person game).
     * In the low stance the player still shuffles facing the ball.
     */
    public boolean faceMovement;
    /** Desired facing yaw; NaN lets the player face the direction of movement (or the net when idle). */
    public float facingYaw = Float.NaN;
    /**
     * Where the player's attention is (usually the ball), as a yaw; filled by the world every
     * step. NaN means "nothing to watch": the player faces the net.
     */
    public float attentionYaw = Float.NaN;

    public MoveIntent clear() {
        direction.setZero();
        sprint = false;
        crouch = false;
        jump = false;
        dive = false;
        jumpHeightFactor = 1f;
        jumpChargeLevel = 0f;
        faceMovement = false;
        facingYaw = Float.NaN;
        attentionYaw = Float.NaN;
        return this;
    }
}
