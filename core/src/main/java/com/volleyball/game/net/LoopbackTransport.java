package com.volleyball.game.net;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Two connected in-memory endpoints (tests, and a host and client in one process). */
public final class LoopbackTransport implements Transport {
    private final ConcurrentLinkedQueue<byte[]> inbox = new ConcurrentLinkedQueue<>();
    private LoopbackTransport peer;
    private volatile boolean closed;

    private LoopbackTransport() {
    }

    /** A connected pair: what one sends, the other receives. */
    public static LoopbackTransport[] pair() {
        LoopbackTransport a = new LoopbackTransport();
        LoopbackTransport b = new LoopbackTransport();
        a.peer = b;
        b.peer = a;
        return new LoopbackTransport[]{a, b};
    }

    @Override
    public void send(byte[] message) {
        if (!closed && !peer.closed) peer.inbox.add(message.clone());
    }

    @Override
    public byte[] poll() {
        return inbox.poll();
    }

    @Override
    public void close() {
        closed = true;
    }
}
