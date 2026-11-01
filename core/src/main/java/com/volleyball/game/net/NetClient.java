package com.volleyball.game.net;

import com.volleyball.game.simulation.GameWorld;

import java.nio.BufferUnderflowException;

/**
 * The remote side of a networked match (prepared for the network stage): joins the host,
 * sends the local person's input every step and shows the newest snapshot. It never runs the
 * rules; client-side prediction and interpolation come with the network stage.
 */
public final class NetClient {
    private final Transport transport;
    private final GameWorld world;
    private int slot = -1;
    private WorldSnapshot lastSnapshot;

    public NetClient(Transport transport, GameWorld world) {
        this.transport = transport;
        this.world = world;
    }

    public void join() {
        transport.send(NetProtocol.hello());
    }

    public void leave() {
        transport.send(NetProtocol.bye());
    }

    /** Slot assigned by the host, or -1 before the welcome. */
    public int slot() {
        return slot;
    }

    public void send(InputCommand command) {
        if (slot >= 0) transport.send(NetProtocol.input(command));
    }

    /** Reads what arrived and applies the newest snapshot to the local world. */
    public WorldSnapshot receive() {
        byte[] data;
        WorldSnapshot newest = null;
        while ((data = transport.poll()) != null) {
            NetProtocol.Message message = NetProtocol.decode(data);
            if (message == null) continue;
            try {
                switch (message.type()) {
                    case WELCOME -> slot = message.payload().get();
                    case SNAPSHOT -> {
                        WorldSnapshot snapshot = WorldSnapshot.read(message.payload());
                        if (newest == null || snapshot.tick > newest.tick) newest = snapshot;
                    }
                    default -> {
                    }
                }
            } catch (BufferUnderflowException | ArrayIndexOutOfBoundsException malformed) {
                // ignore truncated or corrupted messages
            }
        }
        if (newest != null && (lastSnapshot == null || newest.tick > lastSnapshot.tick)) {
            newest.applyTo(world);
            lastSnapshot = newest;
        }
        return lastSnapshot;
    }
}
