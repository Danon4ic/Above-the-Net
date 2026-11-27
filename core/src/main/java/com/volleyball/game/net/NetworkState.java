package com.volleyball.game.net;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.volleyball.game.entities.SwingStyle;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** A complete online state. A later frame always repairs a lost earlier frame. */
public final class NetworkState {
    private static final float TWO_PI = MathUtils.PI2;
    private record Visual(int flags, float stamina, float posePhase, SwingStyle swing, float diveAngle,
                          float charge, float recovery, float block, float stride) { }

    final WorldSnapshot world;
    private final List<Visual> visual = new ArrayList<>();
    private final Quaternion ballRotation = new Quaternion();
    private final int[][] rotation = new int[2][6];
    private final int[] replaced = new int[2];
    private final int[] slots = new int[2];
    private final int[] timeouts = new int[2];
    private final int[] called = new int[2];
    private final List<MatchScore.SetResult> history = new ArrayList<>();
    int inputTick;
    int rejectedInputs;
    private float flightTime;
    private MatchController.Phase phase;
    private BallHandling.ServePhase servePhase;
    private ServeType serveType;
    private CourtSide timeoutSide;
    private CourtSide touchedSide;
    private int touchedCount;
    private int toucher;
    private int server;
    private int nextServer;
    private boolean swapped;
    private float timer;
    private int rallies;
    private int home;
    private int away;

    private NetworkState(WorldSnapshot world) { this.world = world; }

    public int tick() { return world.tick; }
    public int controlledPlayer() { return slots[1]; }
    public MatchController.Phase phase() { return phase; }
    public boolean swapped() { return swapped; }
    public int rallies() { return rallies; }
    public int homePoints() { return home; }
    public int awayPoints() { return away; }
    public VolleyballPlayer calledPlayer(GameWorld world, CourtSide side) { return world.player(called[side.ordinal()]); }

    public static NetworkState capture(int tick, GameWorld world, MatchController match, MatchSession session) {
        var rules = match.rules();
        var score = rules.score();
        var handling = world.ballHandling();
        NetworkState state = new NetworkState(WorldSnapshot.capture(tick, world, score, rules.servingSide()));
        state.ballRotation.set(world.ball().orientation);
        state.flightTime = world.ball().timeSinceHit();
        state.home = score.points(CourtSide.HOME);
        state.away = score.points(CourtSide.AWAY);
        state.history.addAll(score.finishedSets());
        state.phase = match.phase();
        state.timer = match.timeLeft();
        state.rallies = match.rallies();
        state.timeoutSide = match.timeoutSide();
        state.swapped = match.sidesSwapped();
        state.servePhase = handling.servePhase();
        state.server = id(handling.server());
        state.nextServer = id(handling.nextServer());
        state.serveType = handling.server() == null ? ServeType.FLOAT : handling.serveType(handling.server());
        state.touchedSide = handling.lastTouchSide();
        state.touchedCount = state.touchedSide == null ? 0 : handling.teamTouches(state.touchedSide);
        state.toucher = handling.lastToucherId();
        for (CourtSide side : CourtSide.values()) {
            int i = side.ordinal();
            state.rotation[i] = rules.rotation().lineUp(side);
            state.replaced[i] = rules.rotation().replacedByLibero(side);
            state.timeouts[i] = match.timeoutsLeft(side);
            state.called[i] = id(match.ai(side).calledBallPlayer());
        }
        for (int i = 0; i < 2; i++) state.slots[i] = id(session.slot(i).player());
        for (VolleyballPlayer p : world.players()) {
            int flags = (p.isOnCourt() ? 1 : 0) | (p.isCrouching() ? 2 : 0) | (p.isDiveRoll() ? 4 : 0)
                    | (p.isActionPoseHeld() ? 8 : 0) | (p.isBlocking() ? 16 : 0);
            state.visual.add(new Visual(flags, p.stamina(), p.actionPoseProgress(), p.swingStyle(),
                    MathUtils.atan2(p.diveDirection.z, p.diveDirection.x), p.jumpCharge(), p.recoveryTime(), p.blockPenetration(),
                    p.strideCycles() - (float) Math.floor(p.strideCycles())));
        }
        return state;
    }

    ByteBuffer write() {
        ByteBuffer out = ByteBuffer.allocate(NetPacket.LIMIT - NetPacket.HEADER);
        world.write(out);
        out.putInt(inputTick).putInt(rejectedInputs).putShort((short) home).putShort((short) away);
        out.put((byte) phase.ordinal()).putFloat(timer).putInt(rallies).put((byte) (swapped ? 1 : 0))
                .put((byte) ordinal(timeoutSide)).put((byte) servePhase.ordinal()).put((byte) serveType.ordinal())
                .put((byte) server).put((byte) nextServer).put((byte) ordinal(touchedSide)).put((byte) touchedCount).put((byte) toucher);
        out.putFloat(ballRotation.x).putFloat(ballRotation.y).putFloat(ballRotation.z).putFloat(ballRotation.w).putFloat(flightTime);
        for (int i = 0; i < 2; i++) {
            out.put((byte) slots[i]).put((byte) replaced[i]).put((byte) timeouts[i]).put((byte) called[i]);
            for (int p : rotation[i]) out.put((byte) p);
        }
        out.put((byte) history.size());
        for (MatchScore.SetResult set : history) out.putShort((short) set.home()).putShort((short) set.away());
        for (Visual v : visual) {
            out.put((byte) v.flags).put(unit(v.stamina)).put(unit(v.posePhase)).put((byte) v.swing.ordinal())
                    .put(unit(v.diveAngle / TWO_PI + 0.5f)).put(unit(v.charge)).put(unit(v.recovery)).put(unit(v.block)).put(unit(v.stride));
        }
        return out;
    }

    static NetworkState read(ByteBuffer in) {
        WorldSnapshot frame;
        try {
            frame = WorldSnapshot.read(in);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException("invalid snapshot enum", e);
        }
        NetworkState state = new NetworkState(frame);
        if (frame.players.size() > 24 || !Double.isFinite(frame.time) || frame.time < 0) throw new IllegalArgumentException("world state");
        for (float value : frame.ball) finite(value);
        boolean[] seen = new boolean[256];
        for (var p : frame.players) {
            if (seen[p.id()]) throw new IllegalArgumentException("duplicate player");
            seen[p.id()] = true;
            finite(p.x()); finite(p.y()); finite(p.z());
            finite(p.vx()); finite(p.vy()); finite(p.vz()); finite(p.yaw()); finite(p.bodyStateTime());
        }
        state.inputTick = in.getInt();
        state.rejectedInputs = in.getInt();
        state.home = Short.toUnsignedInt(in.getShort());
        state.away = Short.toUnsignedInt(in.getShort());
        state.phase = NetworkSetup.readEnum(in, MatchController.Phase.values());
        state.timer = finite(in.getFloat());
        state.rallies = in.getInt();
        state.swapped = in.get() != 0;
        state.timeoutSide = side(in);
        state.servePhase = NetworkSetup.readEnum(in, BallHandling.ServePhase.values());
        state.serveType = NetworkSetup.readEnum(in, ServeType.values());
        state.server = in.get();
        state.nextServer = in.get();
        state.touchedSide = side(in);
        state.touchedCount = Byte.toUnsignedInt(in.get());
        state.toucher = in.get();
        state.ballRotation.set(finite(in.getFloat()), finite(in.getFloat()), finite(in.getFloat()), finite(in.getFloat())).nor();
        state.flightTime = finite(in.getFloat());
        for (int i = 0; i < 2; i++) {
            state.slots[i] = in.get();
            state.replaced[i] = in.get();
            state.timeouts[i] = Math.min(2, Byte.toUnsignedInt(in.get()));
            state.called[i] = in.get();
            for (int z = 0; z < 6; z++) {
                int id = in.get();
                if (id < 0 || !seen[id]) throw new IllegalArgumentException("rotation player");
                state.rotation[i][z] = id;
            }
        }
        int sets = Byte.toUnsignedInt(in.get());
        if (sets > 9) throw new IllegalArgumentException("sets");
        for (int i = 1; i <= sets; i++) {
            state.history.add(new MatchScore.SetResult(i, Short.toUnsignedInt(in.getShort()), Short.toUnsignedInt(in.getShort())));
        }
        for (int i = 0; i < frame.players.size(); i++) {
            state.visual.add(new Visual(Byte.toUnsignedInt(in.get()), unit(in), unit(in), NetworkSetup.readEnum(in, SwingStyle.values()),
                    (unit(in) - 0.5f) * TWO_PI, unit(in), unit(in), unit(in), unit(in)));
        }
        if (in.hasRemaining()) throw new IllegalArgumentException("extra snapshot bytes");
        return state;
    }

    public void present(GameWorld target, MatchController match, MatchSession session) {
        world.applyTo(target);
        target.presentTime(world.time);
        target.setBenchSign(swapped ? -1f : 1f);
        target.ball().orientation.set(ballRotation);
        target.ball().previousOrientation.set(ballRotation);
        target.ball().presentFlight(flightTime, world.flightSeed);
        for (int i = 0; i < world.players.size(); i++) {
            var value = world.players.get(i);
            var p = target.player(value.id());
            if (p == null) continue;
            player(value, visual.get(i), p);
        }
        var rules = match.rules();
        rules.score().present(home, away, world.homeSets, world.awaySets, world.setNumber, phase == MatchController.Phase.FINISHED, history);
        rules.presentServingSide(world.servingSide);
        for (CourtSide side : CourtSide.values()) {
            var team = side == CourtSide.HOME ? target.home() : target.away();
            rules.rotation().present(side, rotation[side.ordinal()], replaced[side.ordinal()], id(team.libero()));
        }
        session.presentPlayers(target.player(slots[0]), target.player(slots[1]));
        var handling = target.ballHandling();
        handling.present(target.player(server), target.player(nextServer), servePhase, serveType, touchedSide, touchedCount, toucher);
        match.presentState(phase, timer, timeoutSide, timeouts[0], timeouts[1], rallies);
    }

    void presentPlayer(VolleyballPlayer p) {
        for (int i = 0; i < world.players.size(); i++) {
            var state = world.players.get(i);
            if (state.id() == p.id) {
                player(state, visual.get(i), p);
                return;
            }
        }
    }

    private static void player(WorldSnapshot.PlayerState state, Visual v, VolleyballPlayer p) {
        p.position.set(state.x(), state.y(), state.z());
        p.velocity.set(state.vx(), state.vy(), state.vz());
        p.facingYaw = state.yaw();
        p.setAirborne(state.airborne());
        p.setBodyState(state.bodyState(), state.bodyStateTime());
        p.setOnCourt((v.flags & 1) != 0);
        p.setCrouching((v.flags & 2) != 0);
        p.setDiveRoll((v.flags & 4) != 0);
        p.presentActionPose(state.pose(), v.posePhase, (v.flags & 8) != 0);
        p.setBlocking((v.flags & 16) != 0, v.block);
        p.setSwingStyle(v.swing);
        p.setStamina(v.stamina);
        p.setRecoveryTime(v.recovery);
        p.setJumpCharge(v.charge);
        p.presentStride(v.stride);
        p.diveDirection.set(MathUtils.cos(v.diveAngle), 0f, MathUtils.sin(v.diveAngle));
        p.previousPosition.set(p.position);
        p.previousFacingYaw = p.facingYaw;
    }

    /** Blend only continuous visuals. Contact, pose and side changes remain on host tick boundaries. */
    public void blendTowards(NetworkState next, float t, GameWorld target) {
        if (t <= 0f || swapped != next.swapped || phase != next.phase || rallies != next.rallies) return;
        if (world.ballActive && next.world.ballActive && world.flightSeed == next.world.flightSeed) {
            var ball = target.ball();
            ball.position.set(MathUtils.lerp(world.ball[0], next.world.ball[0], t),
                    MathUtils.lerp(world.ball[1], next.world.ball[1], t), MathUtils.lerp(world.ball[2], next.world.ball[2], t));
            ball.previousPosition.set(ball.position);
            ball.orientation.slerp(next.ballRotation, t);
            ball.previousOrientation.set(ball.orientation);
        }
        for (int i = 0; i < world.players.size() && i < next.world.players.size(); i++) {
            var source = world.players.get(i);
            var b = next.world.players.get(i);
            var p = target.player(source.id());
            if (p == null || source.id() != b.id() || source.bodyState() != b.bodyState()
                    || (visual.get(i).flags & 1) != (next.visual.get(i).flags & 1)) continue;
            p.position.set(MathUtils.lerp(source.x(), b.x(), t), MathUtils.lerp(source.y(), b.y(), t),
                    MathUtils.lerp(source.z(), b.z(), t));
            p.previousPosition.set(p.position);
            float turn = (float) Math.atan2(Math.sin(b.yaw() - source.yaw()), Math.cos(b.yaw() - source.yaw()));
            p.facingYaw = source.yaw() + turn * t;
            p.previousFacingYaw = p.facingYaw;
            float stride = visual.get(i).stride;
            float change = next.visual.get(i).stride - stride;
            p.presentStride(stride + (change < -0.5f ? change + 1f : change) * t);
        }
    }

    private static int id(VolleyballPlayer p) { return p == null ? -1 : p.id; }
    private static int ordinal(CourtSide side) { return side == null ? -1 : side.ordinal(); }

    private static CourtSide side(ByteBuffer in) {
        int id = in.get();
        if (id == -1) return null;
        if (id < 0 || id >= CourtSide.values().length) throw new IllegalArgumentException("court side");
        return CourtSide.values()[id];
    }

    private static float finite(float v) {
        if (!Float.isFinite(v)) throw new IllegalArgumentException("non-finite state");
        return v;
    }

    private static byte unit(float value) { return (byte) Math.round(255f * Math.clamp(value, 0f, 1f)); }
    private static float unit(ByteBuffer in) { return Byte.toUnsignedInt(in.get()) / 255f; }
}
