package com.volleyball.game.net;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The authoritative state the host sends to clients: ball, players and score. Clients display
 * it (and later interpolate / predict between snapshots); they never decide points themselves.
 */
public final class WorldSnapshot {
    public record PlayerState(int id, float x, float y, float z, float vx, float vy, float vz, float yaw,
                              boolean airborne, BodyState bodyState, float bodyStateTime, ActionPose pose) {
        static final int BYTES = 1 + 4 * 8 + 3;
    }

    public int tick;
    public double time;
    public boolean ballActive;
    public long flightSeed;
    public final float[] ball = new float[9]; // position, velocity, spin
    public final List<PlayerState> players = new ArrayList<>();
    public int homePoints;
    public int awayPoints;
    public int homeSets;
    public int awaySets;
    public int setNumber;
    public CourtSide servingSide = CourtSide.HOME;

    /** Captures the host's world (score may be null outside a match). */
    public static WorldSnapshot capture(int tick, GameWorld world, MatchScore score, CourtSide serving) {
        WorldSnapshot s = new WorldSnapshot();
        s.tick = tick;
        s.time = world.time();
        Ball b = world.ball();
        s.ballActive = b.isActive();
        s.flightSeed = b.flightSeed();
        put(s.ball, 0, b.position.x, b.position.y, b.position.z);
        put(s.ball, 3, b.velocity.x, b.velocity.y, b.velocity.z);
        put(s.ball, 6, b.spin.x, b.spin.y, b.spin.z);
        for (VolleyballPlayer p : world.players()) {
            s.players.add(new PlayerState(p.id, p.position.x, p.position.y, p.position.z, p.velocity.x, p.velocity.y,
                    p.velocity.z, p.facingYaw, p.isAirborne(), p.bodyState(), p.bodyStateTime(), p.actionPose()));
        }
        if (score != null) {
            s.homePoints = score.points(CourtSide.HOME);
            s.awayPoints = score.points(CourtSide.AWAY);
            s.homeSets = score.sets(CourtSide.HOME);
            s.awaySets = score.sets(CourtSide.AWAY);
            s.setNumber = score.setNumber();
        }
        if (serving != null) s.servingSide = serving;
        return s;
    }

    private static void put(float[] a, int i, float x, float y, float z) {
        a[i] = x;
        a[i + 1] = y;
        a[i + 2] = z;
    }

    /** Puts a client's world into the host's state. */
    public void applyTo(GameWorld world) {
        Ball b = world.ball();
        if (ballActive) {
            b.launch(new Vector3(ball[0], ball[1], ball[2]), new Vector3(ball[3], ball[4], ball[5]),
                    new Vector3(ball[6], ball[7], ball[8]), flightSeed);
        } else {
            b.hold(new Vector3(ball[0], ball[1], ball[2]));
        }
        for (PlayerState state : players) {
            VolleyballPlayer p = world.player(state.id());
            if (p == null) continue;
            p.previousPosition.set(p.position);
            p.position.set(state.x(), state.y(), state.z());
            p.velocity.set(state.vx(), state.vy(), state.vz());
            p.previousFacingYaw = p.facingYaw;
            p.facingYaw = state.yaw();
            p.setAirborne(state.airborne());
            p.setBodyState(state.bodyState(), state.bodyStateTime());
            if (state.pose() != p.actionPose()) p.setActionPose(state.pose(), 0.3f);
        }
    }

    public int byteSize() {
        return 4 + 8 + 1 + 8 + 9 * 4 + 1 + players.size() * PlayerState.BYTES + 5;
    }

    public void write(ByteBuffer out) {
        out.putInt(tick);
        out.putDouble(time);
        out.put((byte) (ballActive ? 1 : 0));
        out.putLong(flightSeed);
        for (float v : ball) out.putFloat(v);
        out.put((byte) players.size());
        for (PlayerState p : players) {
            out.put((byte) p.id());
            out.putFloat(p.x());
            out.putFloat(p.y());
            out.putFloat(p.z());
            out.putFloat(p.vx());
            out.putFloat(p.vy());
            out.putFloat(p.vz());
            out.putFloat(p.yaw());
            out.putFloat(p.bodyStateTime());
            out.put((byte) (p.airborne() ? 1 : 0));
            out.put((byte) p.bodyState().ordinal());
            out.put((byte) p.pose().ordinal());
        }
        out.put((byte) homePoints);
        out.put((byte) awayPoints);
        out.put((byte) (homeSets | awaySets << 4));
        out.put((byte) setNumber);
        out.put((byte) servingSide.ordinal());
    }

    public static WorldSnapshot read(ByteBuffer in) {
        WorldSnapshot s = new WorldSnapshot();
        s.tick = in.getInt();
        s.time = in.getDouble();
        s.ballActive = in.get() != 0;
        s.flightSeed = in.getLong();
        for (int i = 0; i < s.ball.length; i++) s.ball[i] = in.getFloat();
        int count = in.get() & 0xff;
        for (int i = 0; i < count; i++) {
            int id = in.get() & 0xff;
            float x = in.getFloat(), y = in.getFloat(), z = in.getFloat();
            float vx = in.getFloat(), vy = in.getFloat(), vz = in.getFloat();
            float yaw = in.getFloat();
            float stateTime = in.getFloat();
            boolean airborne = in.get() != 0;
            BodyState body = BodyState.values()[in.get()];
            ActionPose pose = ActionPose.values()[in.get()];
            s.players.add(new PlayerState(id, x, y, z, vx, vy, vz, yaw, airborne, body, stateTime, pose));
        }
        s.homePoints = in.get() & 0xff;
        s.awayPoints = in.get() & 0xff;
        int sets = in.get() & 0xff;
        s.homeSets = sets & 0xf;
        s.awaySets = sets >> 4;
        s.setNumber = in.get() & 0xff;
        s.servingSide = CourtSide.values()[in.get()];
        return s;
    }
}
