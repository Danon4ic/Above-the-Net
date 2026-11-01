package com.volleyball.game.net;

import com.volleyball.game.physics.MoveIntent;

import java.nio.ByteBuffer;

/**
 * One simulation step of a remote person's input: the movement intent and the buttons pressed.
 * Clients send only these; the host applies them to its world and is the only one that runs
 * the rules (so results cannot be faked by a client).
 */
public record InputCommand(int tick, int slot, float moveX, float moveZ, int buttons, float jumpFactor,
                           float aimX, float aimZ) {
    public static final int JUMP = 1;
    public static final int SPRINT = 1 << 1;
    public static final int CROUCH = 1 << 2;
    public static final int PRIMARY = 1 << 3;
    public static final int SECONDARY = 1 << 4;
    public static final int DIVE = 1 << 5;
    public static final int VARIANT_NEXT = 1 << 6;
    public static final int VARIANT_PREVIOUS = 1 << 7;
    public static final int AIM = 1 << 8;
    public static final int SWITCH_PLAYER = 1 << 9;

    /** Encoded size in bytes. */
    public static final int BYTES = 4 + 1 + 4 * 2 + 2 + 4 * 3;

    /** Captures a filled intent plus the ball-action buttons of this step. */
    public static InputCommand of(int tick, int slot, MoveIntent intent, int actionButtons, float aimX, float aimZ) {
        int buttons = actionButtons;
        if (intent.jump) buttons |= JUMP;
        if (intent.sprint) buttons |= SPRINT;
        if (intent.crouch) buttons |= CROUCH;
        if (intent.dive) buttons |= DIVE;
        return new InputCommand(tick, slot, intent.direction.x, intent.direction.y, buttons, intent.jumpHeightFactor, aimX, aimZ);
    }

    public boolean has(int button) {
        return (buttons & button) != 0;
    }

    /** The strongest jump a person's input can ask for (a fully charged jump). */
    public static final float MAX_JUMP_FACTOR = com.volleyball.game.input.InputController.CHARGED_JUMP_FACTOR;
    /** Aim points farther than this from the court centre (m) are not meant seriously. */
    public static final float MAX_AIM = 20f;

    /**
     * Writes the movement part into an intent (the facing follows the movement, as for a local
     * person). Values from the network are not trusted: anything not finite or out of range is
     * clamped, so a client cannot move or jump beyond what a local person can.
     */
    public void applyTo(MoveIntent intent) {
        intent.clear();
        if (Float.isFinite(moveX) && Float.isFinite(moveZ)) intent.direction.set(moveX, moveZ);
        if (intent.direction.len2() > 1f) intent.direction.nor();
        intent.faceMovement = true;
        intent.jump = has(JUMP);
        intent.sprint = has(SPRINT);
        intent.crouch = has(CROUCH);
        intent.dive = has(DIVE);
        intent.jumpHeightFactor = jumpFactor > 0f && jumpFactor <= MAX_JUMP_FACTOR ? jumpFactor : 1f;
    }

    /** The aim point if it is a sensible one. */
    public boolean validAim() {
        return Float.isFinite(aimX) && Float.isFinite(aimZ) && Math.abs(aimX) <= MAX_AIM && Math.abs(aimZ) <= MAX_AIM;
    }

    public void write(ByteBuffer out) {
        out.putInt(tick);
        out.put((byte) slot);
        out.putFloat(moveX);
        out.putFloat(moveZ);
        out.putShort((short) buttons);
        out.putFloat(jumpFactor);
        out.putFloat(aimX);
        out.putFloat(aimZ);
    }

    public static InputCommand read(ByteBuffer in) {
        return new InputCommand(in.getInt(), in.get() & 0xff, in.getFloat(), in.getFloat(), in.getShort() & 0xffff,
                in.getFloat(), in.getFloat(), in.getFloat());
    }
}
