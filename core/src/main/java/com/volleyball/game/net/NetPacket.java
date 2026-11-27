package com.volleyball.game.net;

import java.nio.ByteBuffer;

/** Framing of the online match. Integers use network byte order. */
record NetPacket(int version, Type type, long session, int sequence, int ack, int ackBits, ByteBuffer body) {
    static final int VERSION = 2;
    static final int HEADER = 26;
    static final int LIMIT = 1200;

    enum Type {
        HELLO(1), WELCOME(2), READY(3), START(4), INPUT(5), SNAPSHOT(6),
        EVENTS(7), PING(8), PONG(9), RESYNC(10), BYE(11), REJECT(12);

        final int wire;

        Type(int wire) {
            this.wire = wire;
        }
    }

    static byte[] encode(Type type, long session, int sequence, Acks received, ByteBuffer body) {
        int size = body.position();
        if (size > LIMIT - HEADER) throw new IllegalArgumentException("Datagram budget exceeded");
        return ByteBuffer.allocate(HEADER + size).put((byte) 'V').put((byte) 'B')
                .put((byte) VERSION).put((byte) type.wire).putShort((short) size).putLong(session)
                .putInt(sequence).putInt(received.high).putInt(received.bits)
                .put(body.flip()).array();
    }

    static NetPacket decode(byte[] bytes) {
        if (bytes == null || bytes.length < HEADER || bytes.length > LIMIT) return null;
        ByteBuffer in = ByteBuffer.wrap(bytes);
        if (in.get() != 'V' || in.get() != 'B') return null;
        int version = Byte.toUnsignedInt(in.get());
        int id = Byte.toUnsignedInt(in.get());
        if (Short.toUnsignedInt(in.getShort()) != bytes.length - HEADER) return null;
        Type type = null;
        for (Type value : Type.values()) if (value.wire == id) type = value;
        if (type == null) return null;
        return new NetPacket(version, type, in.getLong(), in.getInt(), in.getInt(), in.getInt(), in.slice());
    }

    /** Signed subtraction is serial-number arithmetic for windows shorter than 2^31. */
    static boolean newer(int a, int b) {
        return a - b > 0;
    }

    static final class Acks {
        private boolean seen;
        private int high;
        private int bits;

        void reset() {
            seen = false;
            high = bits = 0;
        }

        boolean note(int sequence) {
            if (!seen) {
                seen = true;
                high = sequence;
                return true;
            }
            int delta = sequence - high;
            if (delta > 0) {
                bits = (delta < 32 ? bits << delta : 0) | (delta <= 32 ? 1 << (delta - 1) : 0);
                high = sequence;
                return true;
            }
            if (delta >= 0 || delta < -32) return false;
            int mask = 1 << (-delta - 1);
            if ((bits & mask) != 0) return false;
            bits |= mask;
            return true;
        }
    }
}
