package com.volleyball.game.net;

/**
 * A message channel between two game instances. Messages are whole datagrams: each
 * {@link #send} arrives (if at all) as one {@link #poll} result, possibly out of order — the
 * protocol tolerates loss, since every message carries its tick and snapshots are complete.
 */
public interface Transport extends AutoCloseable {
    void send(byte[] message);

    /** The next received message, or null if none is waiting. Never blocks. */
    byte[] poll();

    /** Host side: the current client left, the next one to write may take its place. */
    default void releasePeer() {
    }

    @Override
    void close();
}
