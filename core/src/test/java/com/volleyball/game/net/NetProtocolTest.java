package com.volleyball.game.net;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/** The prepared network layer: message encoding, host authority and a client mirror. */
class NetProtocolTest {
    @Test
    void inputCommandRoundTrip() {
        MoveIntent intent = new MoveIntent();
        intent.direction.set(0.6f, -0.8f);
        intent.jump = true;
        intent.sprint = true;
        intent.jumpHeightFactor = 0.9f;
        InputCommand command = InputCommand.of(42, 1, intent, InputCommand.PRIMARY | InputCommand.AIM, 5f, -2f);
        NetProtocol.Message message = NetProtocol.decode(NetProtocol.input(command));
        assertEquals(NetProtocol.Type.INPUT, message.type());
        InputCommand decoded = InputCommand.read(message.payload());
        assertEquals(command, decoded);
        MoveIntent applied = new MoveIntent();
        decoded.applyTo(applied);
        assertEquals(0.6f, applied.direction.x, 1e-6f);
        assertTrue(applied.jump && applied.sprint && !applied.crouch);
        assertEquals(0.9f, applied.jumpHeightFactor, 1e-6f);
    }

    @Test
    void foreignOrTruncatedDatagramsAreIgnored() {
        assertNull(NetProtocol.decode(new byte[]{1, 2, 3, 4, 5}));
        assertNull(NetProtocol.decode(new byte[]{'V', 'B', 99, 0}), "other protocol version");
        assertNull(NetProtocol.decode(new byte[]{'V', 'B'}));
        assertNull(NetProtocol.decode(null));
    }

    @Test
    void snapshotRoundTripRestoresTheWorld() {
        GameWorld host = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);
        GameWorld client = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 2);
        VolleyballPlayer p = host.home().players().get(2);
        p.position.set(-2f, 0.5f, 1.5f);
        p.setAirborne(true);
        p.setBodyState(BodyState.DIVING, 0.1f);
        host.ball().launch(new Vector3(1f, 3f, 2f), new Vector3(-10f, 2f, 1f), new Vector3(0f, 30f, 0f), 77L);

        WorldSnapshot snapshot = WorldSnapshot.capture(10, host, null, CourtSide.AWAY);
        byte[] data = NetProtocol.snapshot(snapshot);
        assertTrue(data.length < UdpTransport.MAX_DATAGRAM, "fits one datagram: " + data.length);
        WorldSnapshot decoded = WorldSnapshot.read(NetProtocol.decode(data).payload());
        decoded.applyTo(client);

        VolleyballPlayer mirror = client.player(p.id);
        assertEquals(-2f, mirror.position.x, 1e-6f);
        assertEquals(0.5f, mirror.position.y, 1e-6f);
        assertTrue(mirror.isAirborne());
        assertEquals(BodyState.DIVING, mirror.bodyState());
        assertTrue(client.ball().isActive());
        assertEquals(-10f, client.ball().velocity.x, 1e-6f);
        assertEquals(77L, client.ball().flightSeed());
        assertEquals(CourtSide.AWAY, decoded.servingSide);
    }

    @Test
    void hostAppliesRemoteInputAndClientMirrorsTheHost() {
        GameWorld hostWorld = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);
        GameWorld clientWorld = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);
        MatchSession session = MatchSession.create(SessionMode.ONLINE_VERSUS, hostWorld);
        LoopbackTransport[] pair = LoopbackTransport.pair();
        NetHost host = new NetHost(pair[0], hostWorld, session);
        NetClient client = new NetClient(pair[1], clientWorld);

        client.join();
        host.receive();
        client.receive();
        assertTrue(host.clientJoined());
        assertEquals(1, client.slot());

        VolleyballPlayer remote = session.slot(1).player();
        float startX = remote.position.x;
        MoveIntent local = new MoveIntent();
        for (int tick = 1; tick <= 120; tick++) {
            local.clear();
            local.direction.set(1f, 0f); // run towards +x (away from the net for the away team)
            client.send(InputCommand.of(tick, client.slot(), local, 0, 0f, 0f));
            host.receive();
            host.applyRemoteInput(hostWorld.time());
            hostWorld.step(1f / 120f);
            host.broadcast(tick, null, CourtSide.HOME);
            client.receive();
        }
        assertTrue(remote.position.x > startX + 1f, "the host moved the remote person's player");
        assertEquals(remote.position.x, clientWorld.player(remote.id).position.x, 0.2f, "the client shows the host's state");
    }

    @Test
    void aClientCannotDriveALocalSlot() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);
        MatchSession session = MatchSession.create(SessionMode.ONLINE_VERSUS, world);
        LoopbackTransport[] pair = LoopbackTransport.pair();
        NetHost host = new NetHost(pair[0], world, session);
        MoveIntent intent = new MoveIntent();
        intent.direction.set(1f, 0f);
        pair[1].send(NetProtocol.input(InputCommand.of(1, 0, intent, 0, 0f, 0f))); // slot 0 = the host's own person
        host.receive();
        host.applyRemoteInput(0);
        assertEquals(0f, world.intent(session.slot(0).player()).direction.x);
    }

    @Test
    void udpTransportDeliversOnLocalhost() throws Exception {
        try (UdpTransport hostSide = UdpTransport.host(0);
             UdpTransport clientSide = UdpTransport.connect("127.0.0.1", hostSide.localPort())) {
            clientSide.send(NetProtocol.hello());
            byte[] received = null;
            for (int i = 0; i < 200 && received == null; i++) {
                received = hostSide.poll();
                if (received == null) Thread.sleep(5);
            }
            assertNotNull(received);
            assertEquals(NetProtocol.Type.HELLO, NetProtocol.decode(received).type());
            hostSide.send(NetProtocol.welcome(1));
            byte[] reply = null;
            for (int i = 0; i < 200 && reply == null; i++) {
                reply = clientSide.poll();
                if (reply == null) Thread.sleep(5);
            }
            assertNotNull(reply);
            ByteBuffer payload = NetProtocol.decode(reply).payload();
            assertEquals(1, payload.get());
        }
    }
}
