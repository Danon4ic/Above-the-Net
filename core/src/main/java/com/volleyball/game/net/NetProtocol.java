package com.volleyball.game.net;

import java.nio.ByteBuffer;

/**
 * Message framing: every datagram starts with a 2-byte magic, the protocol version and the
 * message type. A peer with another version is ignored.
 *
 * <ul>
 *   <li>HELLO (client → host): wants to join.</li>
 *   <li>WELCOME (host → client): the slot index the client controls.</li>
 *   <li>INPUT (client → host): one {@link InputCommand}.</li>
 *   <li>SNAPSHOT (host → client): one {@link WorldSnapshot}.</li>
 *   <li>BYE: leaving.</li>
 * </ul>
 */
public final class NetProtocol {
    public static final byte MAGIC_0 = 'V';
    public static final byte MAGIC_1 = 'B';
    public static final byte VERSION = 1;
    static final int HEADER = 4;

    public enum Type { HELLO, WELCOME, INPUT, SNAPSHOT, BYE }

    /** A decoded message: its type and the payload positioned after the header. */
    public record Message(Type type, ByteBuffer payload) {
    }

    private NetProtocol() {
    }

    public static byte[] hello() {
        return header(Type.HELLO, 0).array();
    }

    public static byte[] bye() {
        return header(Type.BYE, 0).array();
    }

    public static byte[] welcome(int slot) {
        ByteBuffer b = header(Type.WELCOME, 1);
        b.put((byte) slot);
        return b.array();
    }

    public static byte[] input(InputCommand command) {
        ByteBuffer b = header(Type.INPUT, InputCommand.BYTES);
        command.write(b);
        return b.array();
    }

    public static byte[] snapshot(WorldSnapshot snapshot) {
        ByteBuffer b = header(Type.SNAPSHOT, snapshot.byteSize());
        snapshot.write(b);
        return b.array();
    }

    private static ByteBuffer header(Type type, int payload) {
        ByteBuffer b = ByteBuffer.allocate(HEADER + payload);
        b.put(MAGIC_0).put(MAGIC_1).put(VERSION).put((byte) type.ordinal());
        return b;
    }

    /** Decodes the header, or null for a foreign / incompatible / truncated datagram. */
    public static Message decode(byte[] data) {
        if (data == null || data.length < HEADER || data[0] != MAGIC_0 || data[1] != MAGIC_1 || data[2] != VERSION) return null;
        int type = data[3];
        if (type < 0 || type >= Type.values().length) return null;
        ByteBuffer payload = ByteBuffer.wrap(data);
        payload.position(HEADER);
        return new Message(Type.values()[type], payload);
    }
}
