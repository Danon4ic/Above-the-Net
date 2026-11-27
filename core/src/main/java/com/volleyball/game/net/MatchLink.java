package com.volleyball.game.net;

import com.volleyball.game.entities.PlayerRole;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Owns the connection and bounded network queues. The caller owns the match simulation. */
public final class MatchLink implements AutoCloseable {
    public enum Status { WAITING, CONNECTING, READY, LIVE, RECONNECTING, ENDED, INCOMPATIBLE }

    private static final long SECOND = 1_000_000_000L;
    private static final long TIMEOUT = 2500_000_000L;
    private static final long RESUME_TIME = 30 * SECOND;
    private static final int INPUT_HISTORY = 12;
    private static final int INPUT_GRACE = 6;
    private static final int INPUT_AHEAD = 36;
    private static final int HOLD_TICKS = 24;
    private static final int HISTORY = 160;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Transport transport;
    private final boolean host;
    private final long clientKey = token();
    private final NetPacket.Acks arrival = new NetPacket.Acks();
    private final NetPacket.Acks presses = new NetPacket.Acks();
    private final Map<Integer, NetworkInput> scheduled = new HashMap<>();
    private final ArrayDeque<NetworkInput> sentInputs = new ArrayDeque<>();
    private final ArrayDeque<NetworkState> states = new ArrayDeque<>();
    private final ArrayDeque<NetworkEvent> outgoingEvents = new ArrayDeque<>();
    private final Map<Integer, NetworkEvent> incomingEvents = new HashMap<>();
    private final ArrayDeque<NetworkEvent> confirmedEvents = new ArrayDeque<>();
    private NetworkSetup setup;
    private PlayerRole guestRole;
    private Status status;
    private long peerKey;
    private int attempt = 1;
    private int peerAttempt;
    private long session;
    private long closedSession;
    private long lastReceive = Long.MIN_VALUE;
    private long disconnectedAt;
    private long connectedAt;
    private long retryAt;
    private long pingAt;
    private long eventsAt;
    private boolean attached;
    private boolean started;
    private boolean resync;
    private int sequence;
    private int hostTick;
    private int serverTick;
    private long serverTickAt;
    private int lastSentTick;
    private boolean sentTick;
    private int lastProcessedInput;
    private NetworkInput held;
    private int eventSerial;
    private int eventAck;
    private int rejectedInputs;
    private int remoteRejected;
    private double rttMs;
    private double jitterMs;
    private int snapshotGaps;
    private int correctionCount;
    private int soundCursor;
    private boolean soundCursorSet;
    private long lastPingStamp;
    private long snapshotAt;

    private MatchLink(Transport transport, boolean host, NetworkSetup setup, PlayerRole role) {
        this.transport = transport;
        this.host = host;
        this.setup = setup;
        guestRole = role;
        status = host ? Status.WAITING : Status.CONNECTING;
    }

    public static MatchLink host(Transport transport, NetworkSetup setup) {
        return new MatchLink(transport, true, setup, PlayerRole.OUTSIDE_HITTER);
    }

    public static MatchLink client(Transport transport, PlayerRole role) {
        return new MatchLink(transport, false, null, role);
    }

    public NetworkSetup setup() { return setup; }
    public PlayerRole guestRole() { return guestRole; }
    public long guestIdentity() { return peerKey; }
    public Status status() { return status; }
    public boolean isHost() { return host; }
    public boolean started() { return started; }
    public int tick() { return hostTick; }
    public int localPort() { return transport instanceof UdpTransport udp ? udp.localPort() : 0; }
    public int rtt() { return (int) Math.round(rttMs); }
    public int jitter() { return (int) Math.round(jitterMs); }
    public int snapshotGaps() { return snapshotGaps; }
    public int rejectedInputs() { return host ? rejectedInputs : remoteRejected; }
    public int corrections() { return correctionCount; }
    public int snapshotAge(long now) {
        return latest() == null ? 0 : Math.max(0, (int) ((now - snapshotAt) / 1_000_000L + rttMs * 0.5));
    }
    void noteCorrection() { correctionCount++; }

    public void attachClient() {
        attached = true;
        retryAt = 0;
    }

    public void exchange(long now) {
        if (status == Status.ENDED || status == Status.INCOMPATIBLE) return;
        if (connectedAt == 0) connectedAt = now;
        for (int i = 0; i < 96; i++) {
            byte[] datagram = transport.poll();
            if (datagram == null) break;
            NetPacket packet = NetPacket.decode(datagram);
            if (packet == null) {
                if (host && session == 0) transport.releasePeer();
                continue;
            }
            try {
                receive(packet, now);
            } catch (BufferUnderflowException | IllegalArgumentException badPacket) {
                if (host && session == 0) transport.releasePeer();
            }
        }
        if (status == Status.ENDED || status == Status.INCOMPATIBLE) return;
        if (session != 0 && lastReceive != Long.MIN_VALUE && now - lastReceive > TIMEOUT) disconnect(now);
        if (!host && session == 0 && now >= retryAt) {
            if (now - (started ? disconnectedAt : connectedAt) > RESUME_TIME) {
                status = Status.ENDED;
                return;
            }
            send(NetPacket.Type.HELLO, 0, buffer(21).putLong(NetworkSetup.GAME_DATA).putLong(clientKey)
                    .putInt(attempt).put((byte) guestRole.ordinal()));
            retryAt = now + SECOND / 4;
        } else if (!host && session != 0 && attached && status != Status.LIVE && now >= retryAt) {
            send(NetPacket.Type.READY, session, buffer(8).putLong(clientKey));
            retryAt = now + SECOND / 5;
        }
        if (session != 0 && now >= pingAt) {
            lastPingStamp = now;
            send(NetPacket.Type.PING, session, buffer(16).putLong(now).putInt(eventAck).putInt(hostTick));
            pingAt = now + SECOND / 3;
        }
        if (host && status == Status.LIVE && now >= eventsAt && !outgoingEvents.isEmpty()) {
            while (!outgoingEvents.isEmpty() && hostTick - outgoingEvents.peekFirst().tick() > 600) {
                outgoingEvents.removeFirst();
                resync = true;
            }
            if (!outgoingEvents.isEmpty()) sendEvents();
            eventsAt = now + SECOND / 20;
        }
    }

    private void receive(NetPacket packet, long now) {
        ByteBuffer in = packet.body();
        if (host && packet.type() == NetPacket.Type.HELLO && packet.session() == 0) {
            hello(packet, in, now);
            return;
        }
        if (!host && packet.type() == NetPacket.Type.REJECT && in.remaining() == 13 && in.getLong() == clientKey
                && in.getInt() == attempt && session == 0) {
            status = in.get() == 1 ? Status.INCOMPATIBLE : Status.ENDED;
            return;
        }
        if (!host && packet.type() == NetPacket.Type.WELCOME) {
            welcome(packet, in, now);
            return;
        }
        if (session == 0 && host) transport.releasePeer();
        if (session == 0 || packet.session() != session || packet.version() != NetPacket.VERSION
                || !arrival.note(packet.sequence())) return;
        switch (packet.type()) {
            case READY -> {
                if (!host || in.remaining() != 8 || in.getLong() != peerKey) return;
                status = Status.LIVE;
                started = true;
                resync = true;
                send(NetPacket.Type.START, session, buffer(4).putInt(hostTick));
            }
            case START -> {
                if (host || !attached || in.remaining() != 4) return;
                noteServerTick(in.getInt(), now);
                status = Status.LIVE;
                started = true;
            }
            case INPUT -> {
                if (!host || status != Status.LIVE) return;
                input(in);
            }
            case SNAPSHOT -> {
                if (host || !attached) return;
                snapshot(NetworkState.read(in), now);
            }
            case EVENTS -> {
                if (host || !attached) return;
                events(in);
            }
            case PING -> {
                if (in.remaining() != 16) return;
                long stamp = in.getLong();
                int ack = in.getInt();
                int tick = in.getInt();
                if (host) acknowledge(ack);
                else noteServerTick(tick, now);
                send(NetPacket.Type.PONG, session, buffer(12).putLong(stamp).putInt(hostTick));
            }
            case PONG -> {
                if (in.remaining() != 12) return;
                long stamp = in.getLong();
                int tick = in.getInt();
                if (stamp == lastPingStamp && now >= stamp) {
                    double sample = Math.min(3000, (now - stamp) / 1e6);
                    jitterMs += (Math.abs(sample - rttMs) - jitterMs) * 0.15;
                    rttMs = rttMs == 0 ? sample : rttMs + (sample - rttMs) * 0.2;
                }
                if (!host) noteServerTick(tick, now);
            }
            case RESYNC -> {
                if (!host || in.hasRemaining()) return;
                resync = true;
            }
            case BYE -> {
                if (in.hasRemaining()) return;
                if (host) {
                    disconnect(now);
                    peerKey = 0;
                    peerAttempt = 0;
                } else status = Status.ENDED;
                return;
            }
            default -> { return; }
        }
        lastReceive = now;
    }

    private void hello(NetPacket packet, ByteBuffer in, long now) {
        if (in.remaining() != 21) {
            if (session == 0) transport.releasePeer();
            return;
        }
        long revision = in.getLong();
        long key = in.getLong();
        int requestedAttempt = in.getInt();
        if (packet.version() != NetPacket.VERSION || revision != NetworkSetup.GAME_DATA) {
            send(NetPacket.Type.REJECT, 0, buffer(13).putLong(key).putInt(requestedAttempt).put((byte) 1));
            if (session == 0) transport.releasePeer();
            return;
        }
        PlayerRole requested = NetworkSetup.readEnum(in, PlayerRole.values());
        if (key == 0 || peerKey != 0 && key != peerKey && (session != 0 || now - disconnectedAt < RESUME_TIME)) {
            send(NetPacket.Type.REJECT, 0, buffer(13).putLong(key).putInt(requestedAttempt).put((byte) 2));
            if (session == 0) transport.releasePeer();
            return;
        }
        if (key == peerKey && requestedAttempt != peerAttempt && !NetPacket.newer(requestedAttempt, peerAttempt)) {
            if (session == 0) transport.releasePeer();
            return;
        }
        if (session == 0 || requestedAttempt != peerAttempt) {
            if (key != peerKey) guestRole = requested;
            peerKey = key;
            peerAttempt = requestedAttempt;
            session = token();
            status = Status.READY;
            arrival.reset();
            presses.reset();
            held = null;
            scheduled.clear();
            eventAck = eventSerial;
            outgoingEvents.clear();
            lastProcessedInput = hostTick;
        }
        ByteBuffer welcome = buffer(160).putLong(key).putInt(peerAttempt).put((byte) 1).putInt(hostTick).putInt(eventAck);
        setup.write(welcome);
        send(NetPacket.Type.WELCOME, session, welcome);
        lastReceive = now;
    }

    private void welcome(NetPacket packet, ByteBuffer in, long now) {
        if (packet.version() != NetPacket.VERSION || packet.session() == 0 || packet.session() == closedSession
                || in.getLong() != clientKey || in.getInt() != attempt || in.get() != 1) return;
        if (session == packet.session()) return;
        if (session != 0) return;
        int tick = in.getInt();
        int ack = in.getInt();
        NetworkSetup next = NetworkSetup.read(in);
        if (setup != null && setup.matchId() != next.matchId()) return;
        setup = next;
        session = packet.session();
        eventAck = ack;
        incomingEvents.clear();
        confirmedEvents.clear();
        states.clear();
        arrival.reset();
        arrival.note(packet.sequence());
        serverTick = tick;
        serverTickAt = now;
        lastReceive = now;
        retryAt = 0;
        sentTick = false;
        sentInputs.clear();
        status = Status.READY;
    }

    private void disconnect(long now) {
        disconnectedAt = now;
        closedSession = session;
        session = 0;
        if (!host) attempt++;
        lastReceive = Long.MIN_VALUE;
        held = null;
        scheduled.clear();
        retryAt = 0;
        status = host && !started ? Status.WAITING : Status.RECONNECTING;
        transport.releasePeer();
    }

    public void beginHostStep() { hostTick++; }

    private void input(ByteBuffer in) {
        int ack = in.getInt();
        int count = Byte.toUnsignedInt(in.get());
        if (count < 1 || count > INPUT_HISTORY || in.remaining() != count * NetworkInput.BYTES) return;
        acknowledge(ack);
        for (int i = 0; i < count; i++) {
            NetworkInput value = NetworkInput.read(in);
            if (!value.valid(1)) continue;
            int tick = value.command().tick();
            int ahead = tick - hostTick;
            if (ahead > INPUT_AHEAD) continue;
            if (ahead < -INPUT_GRACE) {
                if (value.hasPress() && presses.note(tick)) rejectedInputs++;
            } else if (!scheduled.containsKey(tick)) {
                scheduled.put(tick, value);
            }
        }
    }

    /** Apply due presses once, then return the latest movement to hold for this step. */
    public NetworkInput consume(Consumer<NetworkInput> onPress) {
        if (status != Status.LIVE) return null;
        for (int i = INPUT_GRACE; i >= 0; i--) {
            int tick = hostTick - i;
            NetworkInput input = scheduled.remove(tick);
            if (input == null) continue;
            if (held == null || NetPacket.newer(tick, held.command().tick())) {
                held = input;
                lastProcessedInput = tick;
            }
            if (input.hasPress() && presses.note(tick)) onPress.accept(input);
        }
        scheduled.keySet().removeIf(t -> hostTick - t > INPUT_GRACE);
        return held != null && hostTick - held.command().tick() <= HOLD_TICKS ? held : null;
    }

    public int targetTick(long now) {
        long age = Math.max(0, now - serverTickAt);
        int elapsed = (int) Math.min(24, age * 120.0 / SECOND);
        int lead = Math.clamp((int) Math.ceil((rttMs + jitterMs) * 0.12) + 4, 4, 24);
        int target = serverTick + elapsed + lead;
        if (sentTick && !NetPacket.newer(target, lastSentTick)) target = lastSentTick + 1;
        return target;
    }

    public void submit(NetworkInput input) {
        if (status != Status.LIVE) return;
        lastSentTick = input.command().tick();
        sentTick = true;
        if (sentInputs.size() >= INPUT_HISTORY) sentInputs.removeFirst();
        sentInputs.addLast(input);
        if (!input.hasPress() && (lastSentTick & 1) != 0) return;
        ByteBuffer out = buffer(5 + sentInputs.size() * NetworkInput.BYTES).putInt(eventAck).put((byte) sentInputs.size());
        for (NetworkInput previous : sentInputs) previous.write(out);
        send(NetPacket.Type.INPUT, session, out);
    }

    List<NetworkInput> inputsAfter(int tick) {
        return sentInputs.stream().filter(input -> NetPacket.newer(input.command().tick(), tick)).toList();
    }

    NetworkInput heldAt(int tick) {
        NetworkInput result = null;
        for (NetworkInput input : sentInputs) {
            if (!NetPacket.newer(input.command().tick(), tick)) result = input;
        }
        return result;
    }

    public boolean snapshotDue() {
        return status == Status.LIVE && (resync || hostTick % 3 == 0);
    }

    public void publish(NetworkState state) {
        if (status != Status.LIVE) return;
        resync = false;
        state.inputTick = lastProcessedInput;
        state.rejectedInputs = rejectedInputs;
        send(NetPacket.Type.SNAPSHOT, session, state.write());
    }

    private void snapshot(NetworkState state, long now) {
        NetworkState newest = states.peekLast();
        if (newest != null && !NetPacket.newer(state.tick(), newest.tick())) return;
        if (newest != null) {
            if (state.tick() - newest.tick() > 30) send(NetPacket.Type.RESYNC, session, buffer(0));
            snapshotGaps += Math.max(0, (state.tick() - newest.tick() - 1) / 3);
        }
        noteServerTick(state.tick(), now);
        remoteRejected = state.rejectedInputs;
        if (states.size() >= 24) states.removeFirst();
        states.addLast(state);
        snapshotAt = now;
        status = Status.LIVE;
        started = true;
    }

    private void noteServerTick(int tick, long now) {
        if (serverTickAt == 0 || NetPacket.newer(tick, serverTick)) {
            serverTick = tick;
            serverTickAt = now;
        }
    }

    public NetworkState latest() { return states.peekLast(); }

    public NetworkState[] display(long now) {
        NetworkState newest = latest();
        if (newest == null) return null;
        double estimate = (serverTick - newest.tick()) + Math.min(8, Math.max(0, now - serverTickAt) * 120.0 / SECOND);
        double target = Math.min(0, estimate - 10);
        NetworkState a = states.peekFirst();
        NetworkState b = a;
        for (NetworkState frame : states) {
            b = frame;
            if (frame.tick() - newest.tick() >= target) break;
            a = frame;
        }
        displayFraction = a == b ? 0f : Math.clamp((float) ((target - (a.tick() - newest.tick())) / (b.tick() - a.tick())), 0f, 1f);
        return new NetworkState[]{a, b};
    }

    private float displayFraction;
    public float displayFraction() { return displayFraction; }

    public void emit(NetworkEvent message) {
        if (!host) return;
        NetworkEvent event = message.withIdentity(++eventSerial, hostTick);
        if (status != Status.LIVE) return;
        if (outgoingEvents.size() == HISTORY) outgoingEvents.removeFirst();
        outgoingEvents.addLast(event);
        eventsAt = 0;
    }

    private void acknowledge(int id) {
        if (NetPacket.newer(id, eventAck) && !NetPacket.newer(id, eventSerial)) {
            eventAck = id;
            while (!outgoingEvents.isEmpty() && !NetPacket.newer(outgoingEvents.peekFirst().id(), id)) outgoingEvents.removeFirst();
        }
    }

    private void sendEvents() {
        ByteBuffer out = buffer(5 + 18 * NetworkEvent.BYTES);
        out.putInt(outgoingEvents.peekFirst().id()).put((byte) Math.min(18, outgoingEvents.size()));
        int count = 0;
        for (NetworkEvent event : outgoingEvents) {
            if (count++ == 18) break;
            event.write(out);
        }
        send(NetPacket.Type.EVENTS, session, out);
    }

    private void events(ByteBuffer in) {
        int first = in.getInt();
        int count = Byte.toUnsignedInt(in.get());
        if (count == 0 || count > 18 || in.remaining() != count * NetworkEvent.BYTES) return;
        if (first - eventAck > 1) {
            eventAck = first - 1;
            incomingEvents.clear();
        }
        for (int i = 0; i < count; i++) {
            NetworkEvent e = NetworkEvent.read(in);
            if (e.id() != first + i) return;
            if (e.id() - eventAck > 0 && e.id() - eventAck <= HISTORY) incomingEvents.put(e.id(), e);
        }
        NetworkEvent next;
        while ((next = incomingEvents.remove(eventAck + 1)) != null) {
            if (confirmedEvents.size() >= HISTORY) confirmedEvents.removeFirst();
            confirmedEvents.addLast(next);
            eventAck++;
        }
    }

    public void presentEvents(int renderTick, Consumer<NetworkEvent> consumer) {
        while (!confirmedEvents.isEmpty() && !NetPacket.newer(confirmedEvents.peekFirst().tick(), renderTick)) {
            NetworkEvent event = confirmedEvents.removeFirst();
            if (!soundCursorSet || NetPacket.newer(event.id(), soundCursor)) {
                soundCursor = event.id();
                soundCursorSet = true;
                consumer.accept(event);
            }
        }
    }

    private void send(NetPacket.Type type, long channel, ByteBuffer body) {
        transport.send(NetPacket.encode(type, channel, ++sequence, arrival, body));
    }

    private static long token() {
        long value;
        do value = RANDOM.nextLong(); while (value == 0);
        return value;
    }

    private static ByteBuffer buffer(int size) { return ByteBuffer.allocate(size); }

    @Override
    public void close() {
        if (session != 0) send(NetPacket.Type.BYE, session, buffer(0));
        status = Status.ENDED;
        transport.close();
    }
}
