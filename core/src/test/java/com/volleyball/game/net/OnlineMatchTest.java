package com.volleyball.game.net;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.match.MatchListener;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ContactZone;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.screen.MatchSetup;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.simulation.TeamCatalog;
import com.volleyball.game.training.Drill;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.Venue;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OnlineMatchTest {
    private static final long STEP = 8_333_333;
    private static final float DT = 1f / 120f;
    private static final MatchFormat FORMAT = new MatchFormat(1, 7, 7, 2, RuleMode.OFFICIAL, 8f);

    private static NetworkSetup setup(SessionMode mode) {
        return new NetworkSetup(31, 21, new MatchSetup(Drill.MATCH, false, Difficulty.PROFESSIONAL,
                mode, PlayerRole.MIDDLE_BLOCKER, FORMAT, OffenseSystem.FIVE_ONE,
                TeamCatalog.defaultHome().id(), TeamCatalog.defaultAway().id()), NetCategory.MEN, Venue.ROOFTOP, true, true);
    }

    private static GameWorld world() {
        return GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
    }

    @Test
    void packetLengthAndSequenceWrapAreChecked() {
        var ack = new NetPacket.Acks();
        assertTrue(ack.note(Integer.MAX_VALUE - 1));
        assertTrue(ack.note(Integer.MIN_VALUE));
        assertTrue(ack.note(Integer.MAX_VALUE));
        assertFalse(ack.note(Integer.MAX_VALUE));
        assertTrue(NetPacket.newer(Integer.MIN_VALUE, Integer.MAX_VALUE));

        byte[] encoded = NetPacket.encode(NetPacket.Type.PING, 42, 8, ack, ByteBuffer.allocate(4).putInt(21));
        assertEquals(21, NetPacket.decode(encoded).body().getInt());
        assertNull(NetPacket.decode(java.util.Arrays.copyOf(encoded, encoded.length - 1)));
        assertNull(NetPacket.decode(new byte[1201]));
        encoded[4] = 1; // length disagrees with the datagram
        assertNull(NetPacket.decode(encoded));
    }

    @Test
    void incompatibleDataGetsExplicitRejection() {
        LoopbackTransport[] pair = LoopbackTransport.pair();
        try (var host = MatchLink.host(pair[0], setup(SessionMode.ONLINE_VERSUS))) {
            var hello = ByteBuffer.allocate(21).putLong(123).putLong(99).putInt(1).put((byte) PlayerRole.SETTER.ordinal());
            pair[1].send(NetPacket.encode(NetPacket.Type.HELLO, 0, 1, new NetPacket.Acks(), hello));
            host.exchange(1_000_000_000);
            var reply = NetPacket.decode(pair[1].poll());
            assertNotNull(reply);
            assertEquals(NetPacket.Type.REJECT, reply.type());
            assertEquals(99, reply.body().getLong());
            assertEquals(1, reply.body().getInt());
            assertEquals(1, reply.body().get());
            assertFalse(host.started());
        }
    }

    @Test
    void duplicatePressExpiredInputAndForeignSlotAreSeparated() {
        DelayPath path = new DelayPath(0, 0, 0);
        try (var host = MatchLink.host(path.end(0), setup(SessionMode.ONLINE_COOP));
             var guest = MatchLink.client(path.end(1), PlayerRole.SETTER)) {
            join(path, host, guest);
            assertEquals(PlayerRole.SETTER, host.guestRole());
            assertEquals(SessionMode.ONLINE_COOP, guest.setup().match().players());
            List<NetworkInput> seen = new ArrayList<>();
            int target = host.tick() + 4;
            guest.submit(press(target, 0, InputCommand.DIVE));
            guest.submit(press(target + 1, 1, InputCommand.JUMP | InputCommand.PRIMARY));
            for (int step = 0; step < 25; step++) {
                path.advance();
                host.exchange(path.now);
                guest.exchange(path.now);
                host.beginHostStep();
                host.consume(seen::add);
                if (step == 14) guest.submit(press(host.tick() - 9, 1, InputCommand.DIVE));
            }
            assertEquals(1, seen.size());
            assertTrue(seen.getFirst().command().has(InputCommand.PRIMARY));
            assertTrue(seen.getFirst().command().has(InputCommand.JUMP));
            assertEquals(1, host.rejectedInputs());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reconnectClearsHeldInputAndFullStateRestoresTheSameMatch(boolean asymmetric) {
        DelayPath path = new DelayPath(60, 8, 0);
        var world = world();
        var settings = setup(SessionMode.ONLINE_VERSUS);
        var session = MatchSession.create(settings.match().players(), world);
        var match = new MatchController(world, FORMAT, 21);
        match.start(CourtSide.HOME);
        try (var host = MatchLink.host(path.end(0), settings);
             var guest = MatchLink.client(path.end(1), PlayerRole.SETTER)) {
            join(path, host, guest);
            guest.submit(press(host.tick() + 5, 1, InputCommand.PRIMARY));
            path.block = !asymmetric;
            path.blockFromHost = asymmetric;
            for (int i = 0; i < 380; i++) {
                path.advance();
                host.exchange(path.now);
                guest.exchange(path.now);
                host.beginHostStep();
            }
            assertEquals(MatchLink.Status.RECONNECTING, guest.status());
            assertTrue(host.status() == MatchLink.Status.RECONNECTING || host.status() == MatchLink.Status.READY);
            assertNull(host.consume(input -> fail("old action after disconnect")));
            path.block = false;
            path.blockFromHost = false;
            assertNotNull(path.firstWelcome);
            path.wires[0].send(path.firstWelcome);
            for (int i = 0; i < 220; i++) {
                path.advance();
                host.exchange(path.now);
                guest.exchange(path.now);
                host.beginHostStep();
                if (host.snapshotDue()) host.publish(NetworkState.capture(host.tick(), world, match, session));
            }
            assertEquals(MatchLink.Status.LIVE, guest.status());
            assertEquals(settings.matchId(), guest.setup().matchId());
            assertEquals(session.slot(1).player().id, guest.latest().controlledPlayer());
            assertNull(host.consume(input -> fail("previous session action")));
        }
    }

    @ParameterizedTest
    @CsvSource({"60,2", "120,5"})
    void remoteMovementAndEdgeActionsReachTheAuthoritativePlayer(int rtt, int loss) {
        DelayPath path = new DelayPath(rtt, 30, loss);
        var world = world();
        var settings = setup(SessionMode.ONLINE_VERSUS);
        var session = MatchSession.create(settings.match().players(), world);
        var match = new MatchController(world, FORMAT, 21);
        match.start(CourtSide.HOME);
        while (match.phase() != MatchController.Phase.RALLY) {
            world.step(DT);
            match.update(DT, session.humans());
        }
        var controls = new NetworkControls(world, session, match);
        var player = session.slot(1).player();
        player.placeAt(5.0f, 0f);
        var movement = new MoveIntent();
        movement.direction.set(0f, 0.85f);
        var actions = new ArrayList<NetworkInput>();
        int jumps = 0, dives = 0;
        try (var host = MatchLink.host(path.end(0), settings);
             var guest = MatchLink.client(path.end(1), PlayerRole.OUTSIDE_HITTER)) {
            join(path, host, guest);
            for (int i = 0; i < 265; i++) {
                path.advance();
                host.exchange(path.now);
                guest.exchange(path.now);
                host.beginHostStep();
                int button = i == 100 ? InputCommand.JUMP : i == 195 ? InputCommand.DIVE : 0;
                MoveIntent desired = i < 64 ? movement : new MoveIntent();
                guest.submit(new NetworkInput(InputCommand.of(guest.targetTick(path.now), 1, desired, button, 0f, 0f),
                        Float.NaN, 0f, -1, 0, -1, -1, false));
                actions.clear();
                NetworkInput remote = host.consume(actions::add);
                controls.apply(1, remote, actions);
                var intent = world.intent(player);
                if (intent.jump) jumps++;
                if (intent.dive) dives++;
                world.step(DT);
                if (host.snapshotDue()) host.publish(NetworkState.capture(host.tick(), world, match, session));
            }
            assertTrue(player.position.z > 1.2f, "movement reaches the remote slot");
            assertEquals(1, jumps);
            assertEquals(1, dives);
            assertSame(player, session.slot(1).player());
            assertEquals(0, guest.rejectedInputs());
        }
    }

    @ParameterizedTest
    @CsvSource({"60,2", "120,5"})
    void remoteSetterCanTriggerARealFirstTempoContact(int rtt, int loss) {
        DelayPath path = new DelayPath(rtt, 30, loss);
        var authority = world();
        var replica = world();
        var settings = setup(SessionMode.ONLINE_COOP);
        var session = MatchSession.create(settings.match().players(), authority, PlayerRole.OUTSIDE_HITTER);
        var match = new MatchController(authority, FORMAT, 21);
        match.start(CourtSide.HOME);
        while (match.phase() != MatchController.Phase.RALLY) {
            authority.step(DT);
            match.update(DT, session.humans());
        }
        session.chooseRemoteRole(PlayerRole.SETTER, match.rules().rotation());
        var setter = session.slot(1).player();
        var hitter = authority.home().players().get(2);
        for (var p : authority.players()) p.setOnCourt(p == setter || p == hitter);
        setter.placeAt(-1.5f, 1f);
        setter.attributes.setting = 94;
        hitter.placeAt(-1.17f, -0.2f);
        var controls = new NetworkControls(authority, session, match);
        var handling = authority.ballHandling();
        List<BallTouch> contacts = new ArrayList<>();
        List<NetworkInput> actions = new ArrayList<>();
        boolean[] swinging = {false};
        try (var host = MatchLink.host(path.end(0), settings);
             var guest = MatchLink.client(path.end(1), PlayerRole.SETTER)) {
            join(path, host, guest);
            handling.addListener(touch -> {
                contacts.add(touch);
                host.emit(NetworkEvent.touch(touch));
                if (touch.quickHitterId() == hitter.id) swinging[0] = true;
            });
            int contactTick = Integer.MAX_VALUE;
            List<BallTouch> delivered = new ArrayList<>();
            for (int i = 0; i < 240; i++) {
                path.advance();
                host.exchange(path.now);
                guest.exchange(path.now);
                host.beginHostStep();
                int target = guest.targetTick(path.now);
                if (i == 95) contactTick = target;
                int button = i == 95 ? InputCommand.PRIMARY : 0;
                int variant = i == 42 ? SetType.QUICK_MIDDLE.ordinal() + 1 : -1;
                // Pick the set after preparing the second-ball context below.
                if (i == 40) {
                    handling.registerFeed(CourtSide.HOME, 1);
                    authority.ball().launch(new Vector3(-5f, 5f, 3.5f), Vector3.Zero, Vector3.Zero, 20);
                }
                guest.submit(new NetworkInput(InputCommand.of(target, 1, new MoveIntent(), button, 0f, 0f),
                        Float.NaN, 0, variant, 0, -1, -1, false));
                if (host.tick() == contactTick - 1) {
                    hitter.position.y = 0.27f;
                    hitter.velocity.y = 2.1f;
                    hitter.setAirborne(true);
                    Vector3 at = ContactZone.idealPoint(setter, ContactType.OVERHEAD_PASS, null, new Vector3());
                    authority.ball().launch(at.add(0, 0.03f, 0), new Vector3(0, -2f, 0), Vector3.Zero, 21);
                    handling.setQuickSet(setter, new BallHandling.QuickSet(hitter, SetType.QUICK_MIDDLE, false));
                }
                actions.clear();
                NetworkInput remote = host.consume(actions::add);
                controls.apply(1, remote, actions);
                if (swinging[0]) {
                    handling.arm(hitter, ActionKind.PRIMARY, authority.time());
                    swinging[0] = false;
                }
                if (i > 88) authority.step(DT);
                if (host.snapshotDue()) host.publish(NetworkState.capture(host.tick(), authority, match, session));
                if (guest.latest() != null) guest.presentEvents(guest.latest().tick(), e -> delivered.add(e.toTouch(replica)));
            }
            assertTrue(contacts.size() >= 2, () -> "the set and the hit are simulated on the host: " + contacts
                    + " chosen=" + handling.setType(setter) + " late=" + host.rejectedInputs());
            assertEquals(ContactPurpose.SET, contacts.getFirst().purpose());
            assertEquals(hitter.id, contacts.getFirst().quickHitterId());
            assertEquals(ContactType.ATTACK, contacts.get(1).type());
            assertEquals(hitter.id, delivered.getFirst().quickHitterId());
            assertEquals(ContactType.ATTACK, delivered.get(1).type());
        }
    }

    @ParameterizedTest(name = "network RTT {0} ms, loss {1}%")
    @CsvSource({"0,0", "0,2", "0,5", "60,0", "60,2", "60,5", "120,0", "120,2", "120,5"})
    void aCompleteSetConvergesUnderTransportImpairment(int rtt, int loss) {
        DelayPath path = new DelayPath(rtt, 30, loss);
        var authority = world();
        var replica = world();
        var settings = setup(SessionMode.ONLINE_COOP);
        var session = MatchSession.create(settings.match().players(), authority);
        var remote = MatchSession.create(settings.match().players(), replica);
        var match = new MatchController(authority, FORMAT, 21, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        var mirror = new MatchController(replica, FORMAT, 21);
        match.start(CourtSide.HOME);
        mirror.start(CourtSide.HOME);
        try (var host = MatchLink.host(path.end(0), settings);
             var guest = MatchLink.client(path.end(1), PlayerRole.SETTER)) {
            join(path, host, guest);
            match.addListener(new MatchListener() {
                @Override public void onServeWhistle(VolleyballPlayer p, MatchScore score) { host.emit(NetworkEvent.serve(p.id)); }
                @Override public void onPoint(MatchRules.PointSummary point, MatchScore score) { host.emit(NetworkEvent.point(point)); }
                @Override public void onChangeOfEnds(boolean swapped) { host.emit(NetworkEvent.ends(swapped)); }
            });
            authority.ballHandling().addListener(touch -> host.emit(NetworkEvent.touch(touch)));
            Set<Integer> seen = new HashSet<>();
            List<NetworkEvent> points = new ArrayList<>();
            int finalized = -1;
            var predictor = new NetworkPrediction(true);
            for (int i = 0; i < 120 * 360; i++) {
                path.advance();
                host.exchange(path.now);
                guest.exchange(path.now);
                host.beginHostStep();
                host.consume(input -> { });
                int action = i % 61 == 0 ? InputCommand.JUMP : 0;
                guest.submit(press(guest.targetTick(path.now), 1, action));
                if (match.phase() != MatchController.Phase.FINISHED) {
                    authority.step(DT);
                    match.update(DT, List.of());
                } else if (finalized < 0) finalized = i;
                if (host.snapshotDue()) host.publish(NetworkState.capture(host.tick(), authority, match, session));
                var state = guest.latest();
                if (state != null) {
                    guest.presentEvents(state.tick(), event -> {
                        assertTrue(seen.add(event.id()), "duplicate event");
                        if (event.kind() == NetworkEvent.Kind.POINT) points.add(event);
                    });
                    if (i % 4 == 0) {
                        state.present(replica, mirror, remote);
                        predictor.present(guest, remote.slot(1).player(), 1f / 30);
                    }
                }
                if (finalized >= 0 && i - finalized > 140) break;
            }
            assertEquals(MatchController.Phase.FINISHED, match.phase());
            assertEquals(MatchController.Phase.FINISHED, mirror.phase());
            assertEquals(match.rules().score().finishedSets(), mirror.rules().score().finishedSets());
            assertEquals(match.rules().servingSide(), mirror.rules().servingSide());
            assertEquals(match.sidesSwapped(), guest.latest().swapped());
            assertEquals(match.rallies(), points.size());
            for (var event : points) event.presentMatch(replica, mirror);
            assertEquals(match.lastPoint().outcome().reason(), mirror.lastPoint().outcome().reason());
            assertEquals(match.lastPoint().outcome().winner(), mirror.lastPoint().outcome().winner());
            System.out.printf(Locale.ROOT, "rtt=%d loss=%d%% measured=%d ms jitter=%d ms age=%d ms gap=%d late=%d corrections=%d points=%d%n",
                    rtt, loss, guest.rtt(), guest.jitter(), guest.snapshotAge(path.now), guest.snapshotGaps(),
                    guest.rejectedInputs(), guest.corrections(), points.size());
        }
    }

    private static NetworkInput press(int tick, int slot, int buttons) {
        return new NetworkInput(InputCommand.of(tick, slot, new MoveIntent(), buttons, 0, 0),
                Float.NaN, 0f, -1, 0, -1, -1, false);
    }

    private static void join(DelayPath path, MatchLink host, MatchLink guest) {
        guest.attachClient();
        for (int i = 0; i < 600 && guest.status() != MatchLink.Status.LIVE; i++) {
            path.advance();
            host.exchange(path.now);
            guest.exchange(path.now);
        }
        assertEquals(MatchLink.Status.LIVE, guest.status());
        assertEquals(MatchLink.Status.LIVE, host.status());
    }

    private static final class DelayPath {
        private record Delivery(long when, int endpoint, byte[] bytes) implements Comparable<Delivery> {
            @Override public int compareTo(Delivery other) { return Long.compare(when, other.when); }
        }

        private final LoopbackTransport[] wires = LoopbackTransport.pair();
        private final PriorityQueue<Delivery> queue = new PriorityQueue<>();
        private final Random random = new Random(21);
        private final int delay;
        private final int jitter;
        private final int loss;
        private int attempts;
        long now = 1_000_000_000L;
        boolean block;
        boolean blockFromHost;
        byte[] firstWelcome;

        DelayPath(int rtt, int jitter, int loss) {
            delay = rtt / 2;
            this.jitter = jitter;
            this.loss = loss;
        }

        void advance() {
            now += STEP;
            while (!queue.isEmpty() && queue.peek().when <= now) {
                var event = queue.remove();
                if (!block && !(blockFromHost && event.endpoint == 0)) wires[event.endpoint].send(event.bytes);
            }
        }

        Transport end(int index) {
            return new Transport() {
                @Override public void send(byte[] bytes) {
                    assertTrue(bytes.length <= NetPacket.LIMIT);
                    if (index == 0 && firstWelcome == null && NetPacket.decode(bytes).type() == NetPacket.Type.WELCOME) firstWelcome = bytes;
                    if (block || blockFromHost && index == 0 || random.nextInt(100) < loss || loss != 0 && ++attempts % 311 < 4) return;
                    int spread = jitter == 0 ? 0 : random.nextInt(jitter + 1) - jitter / 2;
                    queue.add(new Delivery(now + Math.max(0, delay + spread) * 1_000_000L, index, bytes));
                }
                @Override public byte[] poll() { return wires[index].poll(); }
                @Override public void close() { wires[index].close(); }
            };
        }
    }
}
