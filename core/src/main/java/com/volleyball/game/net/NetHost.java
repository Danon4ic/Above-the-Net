package com.volleyball.game.net;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.multiplayer.ControllerKind;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;

import java.nio.BufferUnderflowException;

/**
 * The authoritative side of a networked match (prepared for the network stage). It owns the
 * world and the rules: remote people only send {@link InputCommand}s, which are applied to
 * their player exactly like local input; the host simulates, referees and sends snapshots.
 */
public final class NetHost {
    /** Snapshots go out every this many simulation steps (120 Hz / 3 = 40 per second). */
    public static final int SNAPSHOT_INTERVAL = 3;

    private final Transport transport;
    private final GameWorld world;
    private final MatchSession session;
    private final InputCommand[] latest;
    private boolean clientJoined;

    public NetHost(Transport transport, GameWorld world, MatchSession session) {
        this.transport = transport;
        this.world = world;
        this.session = session;
        this.latest = new InputCommand[session.slots().size()];
    }

    public boolean clientJoined() {
        return clientJoined;
    }

    /** Reads everything that arrived: joins and the newest command of each remote slot. */
    public void receive() {
        byte[] data;
        while ((data = transport.poll()) != null) {
            NetProtocol.Message message = NetProtocol.decode(data);
            if (message == null) continue;
            try {
                switch (message.type()) {
                    case HELLO -> {
                        clientJoined = true;
                        transport.send(NetProtocol.welcome(remoteSlot()));
                    }
                    case INPUT -> {
                        InputCommand command = InputCommand.read(message.payload());
                        int slot = command.slot();
                        MatchSession.Slot s = session.slot(slot);
                        // Only remote slots can be driven from outside, and only with newer commands.
                        if (s == null || s.controller != ControllerKind.REMOTE) continue;
                        if (latest[slot] == null || command.tick() > latest[slot].tick()) latest[slot] = command;
                    }
                    case BYE -> clientJoined = false;
                    default -> {
                    }
                }
            } catch (BufferUnderflowException malformed) {
                // ignore truncated messages
            }
        }
    }

    /** Applies the remote commands to the world; call before each simulation step. */
    public void applyRemoteInput(double now) {
        BallHandling handling = world.ballHandling();
        for (int i = 0; i < latest.length; i++) {
            InputCommand command = latest[i];
            if (command == null) continue;
            MatchSession.Slot slot = session.slot(i);
            if (command.has(InputCommand.SWITCH_PLAYER)) session.switchPlayer(i);
            VolleyballPlayer p = slot.player();
            command.applyTo(world.intent(p));
            if (command.has(InputCommand.AIM)) handling.setAim(p, command.aimX(), command.aimZ());
            if (command.has(InputCommand.PRIMARY)) handling.arm(p, ActionKind.PRIMARY, now);
            else if (command.has(InputCommand.SECONDARY)) handling.arm(p, ActionKind.SECONDARY, now);
            if (command.has(InputCommand.VARIANT_NEXT)) handling.cycleVariant(p, 1);
            if (command.has(InputCommand.VARIANT_PREVIOUS)) handling.cycleVariant(p, -1);
            // Presses are one-shot: keep only the held state until the next command arrives.
            latest[i] = new InputCommand(command.tick(), command.slot(), command.moveX(), command.moveZ(),
                    command.buttons() & (InputCommand.SPRINT | InputCommand.CROUCH | InputCommand.AIM),
                    command.jumpFactor(), command.aimX(), command.aimZ());
        }
    }

    /** Sends the world state every {@link #SNAPSHOT_INTERVAL} steps. */
    public void broadcast(int tick, MatchScore score, CourtSide serving) {
        if (!clientJoined || tick % SNAPSHOT_INTERVAL != 0) return;
        transport.send(NetProtocol.snapshot(WorldSnapshot.capture(tick, world, score, serving)));
    }

    private int remoteSlot() {
        for (MatchSession.Slot slot : session.slots()) if (slot.controller == ControllerKind.REMOTE) return slot.index;
        return -1;
    }
}
