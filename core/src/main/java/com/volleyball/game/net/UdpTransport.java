package com.volleyball.game.net;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * UDP transport (non-blocking). The host binds a port and talks to the first address that writes
 * to it until {@link #releasePeer} (datagrams from anyone else are dropped, so a stray packet
 * cannot take the session over); the client sends to the host's address. Prepared for the network
 * stage: no encryption, NAT traversal or reliability layer yet.
 */
public final class UdpTransport implements Transport {
    public static final int DEFAULT_PORT = 27960;
    static final int MAX_DATAGRAM = 1400;

    private final DatagramChannel channel;
    private final ByteBuffer receiveBuffer = ByteBuffer.allocate(MAX_DATAGRAM);
    private SocketAddress peer;
    private final boolean hosting;

    private UdpTransport(DatagramChannel channel, SocketAddress peer) {
        this.channel = channel;
        this.peer = peer;
        this.hosting = peer == null;
    }

    /** Host side: listens on {@code port} (0 = any free port). */
    public static UdpTransport host(int port) {
        try {
            DatagramChannel channel = DatagramChannel.open();
            channel.bind(new InetSocketAddress(port));
            channel.configureBlocking(false);
            return new UdpTransport(channel, null);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open UDP port " + port, e);
        }
    }

    /** Client side: talks to the host at {@code host:port}. */
    public static UdpTransport connect(String host, int port) {
        try {
            DatagramChannel channel = DatagramChannel.open();
            channel.bind(null);
            channel.configureBlocking(false);
            return new UdpTransport(channel, new InetSocketAddress(host, port));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open a UDP socket", e);
        }
    }

    /** The local port (useful when the host was opened on port 0). */
    public int localPort() {
        try {
            return ((InetSocketAddress) channel.getLocalAddress()).getPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void send(byte[] message) {
        if (peer == null) return; // host: nobody has written yet
        if (message.length > MAX_DATAGRAM) throw new IllegalArgumentException("Message too large: " + message.length);
        try {
            channel.send(ByteBuffer.wrap(message), peer);
        } catch (IOException e) {
            // Datagrams may be lost; the protocol tolerates it.
        }
    }

    @Override
    public byte[] poll() {
        try {
            while (true) {
                receiveBuffer.clear();
                SocketAddress from = channel.receive(receiveBuffer);
                if (from == null) return null;
                if (peer == null && hosting) peer = from;
                // Only the peer is listened to (the host's client, or the host for a client).
                if (!from.equals(peer)) continue;
                receiveBuffer.flip();
                byte[] message = new byte[receiveBuffer.remaining()];
                receiveBuffer.get(message);
                return message;
            }
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void releasePeer() {
        if (hosting) peer = null;
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
