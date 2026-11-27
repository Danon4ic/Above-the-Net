package com.volleyball.game.net;

import com.volleyball.game.physics.MoveIntent;

import java.nio.ByteBuffer;

/** Movement is the held state. Action presses are identified by the command's target tick. */
public record NetworkInput(InputCommand command, float facingYaw, float charge, int variant, int spin,
                           int substituteOut, int substituteIn, boolean timeout) {
    static final int BYTES = InputCommand.BYTES + 13;
    static final int HELD = InputCommand.SPRINT | InputCommand.CROUCH | InputCommand.AIM;
    static final int KNOWN = HELD | InputCommand.JUMP | InputCommand.DIVE | InputCommand.PRIMARY
            | InputCommand.SECONDARY | InputCommand.VARIANT_NEXT | InputCommand.VARIANT_PREVIOUS | InputCommand.SWITCH_PLAYER;

    void write(ByteBuffer out) {
        command.write(out);
        out.putFloat(facingYaw).putFloat(charge).put((byte) variant).put((byte) spin)
                .put((byte) substituteOut).put((byte) substituteIn).put((byte) (timeout ? 1 : 0));
    }

    static NetworkInput read(ByteBuffer in) {
        InputCommand c = InputCommand.read(in);
        return new NetworkInput(c, in.getFloat(), in.getFloat(), in.get(), in.get(), in.get(), in.get(), in.get() == 1);
    }

    boolean valid(int slot) {
        return command.slot() == slot && (command.buttons() & ~KNOWN) == 0
                && Float.isFinite(command.moveX()) && Float.isFinite(command.moveZ())
                && command.moveX() * command.moveX() + command.moveZ() * command.moveZ() <= 1.01f
                && command.jumpFactor() >= 0.5f && command.jumpFactor() <= InputCommand.MAX_JUMP_FACTOR
                && (!command.has(InputCommand.AIM) || command.validAim())
                && (Float.isNaN(facingYaw) || Math.abs(facingYaw) <= Math.PI * 2)
                && charge >= 0f && charge <= 1f && variant >= -1 && variant <= 9 && spin >= -1 && spin <= 1
                && ((substituteOut == -1 && substituteIn == -1) || (substituteOut >= 0 && substituteIn >= 0));
    }

    boolean hasPress() {
        return (command.buttons() & ~HELD) != 0 || variant >= 0 || spin != 0 || substituteOut >= 0 || timeout;
    }

    void applyTo(MoveIntent intent, boolean press) {
        command.applyTo(intent);
        intent.jump &= press;
        intent.dive &= press;
        intent.facingYaw = facingYaw;
        intent.jumpChargeLevel = charge;
    }
}
