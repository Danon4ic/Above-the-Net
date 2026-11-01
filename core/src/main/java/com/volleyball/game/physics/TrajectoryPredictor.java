package com.volleyball.game.physics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;

import java.util.ArrayList;
import java.util.List;

/**
 * Forecasts a ball's flight from <em>observable</em> state only: position, velocity and spin.
 * It deliberately has no access to the flight seed, so the knuckle wobble of float serves is not
 * part of the forecast — like a real player, the AI (and the landing indicator) can be fooled.
 *
 * <p>The forecast ignores collisions other than with the floor plane and reports where the
 * ball crosses the net plane, so callers can judge whether it will clear the net.
 */
public final class TrajectoryPredictor {
    /** Forecast result. Reuse instances to avoid garbage. */
    public static final class Prediction {
        public boolean lands;
        public final Vector3 landing = new Vector3();
        public float timeToLanding;
        public final Vector3 apex = new Vector3();
        public float timeToApex;
        public boolean crossesNetPlane;
        public final Vector3 netCrossing = new Vector3();
        public float timeToNetCrossing;
        /**
         * Optional height to watch for: where the ball passes it on the way down (e.g. a setter's
         * or hitter's hands). NaN disables it. Set before calling {@code predict}.
         */
        public float watchHeight = Float.NaN;
        public boolean reachesWatchHeight;
        public final Vector3 descentPoint = new Vector3();
        public float timeToDescent;
        /** Ball centre sampled every {@link #SAMPLE_INTERVAL} seconds. */
        public final List<Vector3> path = new ArrayList<>();
        private int pathSize;

        public int pathSize() {
            return pathSize;
        }

        void reset() {
            lands = false;
            crossesNetPlane = false;
            reachesWatchHeight = false;
            timeToDescent = Float.NaN;
            timeToLanding = Float.NaN;
            timeToNetCrossing = Float.NaN;
            timeToApex = 0f;
            pathSize = 0;
        }

        void addSample(Vector3 p) {
            if (pathSize < path.size()) path.get(pathSize).set(p);
            else path.add(p.cpy());
            pathSize++;
        }

        /** Copies every result (the watch height included) from {@code other}. */
        void set(Prediction other) {
            lands = other.lands;
            landing.set(other.landing);
            timeToLanding = other.timeToLanding;
            apex.set(other.apex);
            timeToApex = other.timeToApex;
            crossesNetPlane = other.crossesNetPlane;
            netCrossing.set(other.netCrossing);
            timeToNetCrossing = other.timeToNetCrossing;
            watchHeight = other.watchHeight;
            reachesWatchHeight = other.reachesWatchHeight;
            descentPoint.set(other.descentPoint);
            timeToDescent = other.timeToDescent;
            pathSize = 0;
            for (int i = 0; i < other.pathSize; i++) addSample(other.path.get(i));
        }
    }

    /**
     * A forecast of the live ball, remembered with the state it was made from. Within one
     * simulation step every player and the team AI ask about the same ball (often with the same
     * watched height): those repeats are copied instead of flown again.
     */
    private static final class Memo {
        final Prediction result = new Prediction();
        final Vector3 position = new Vector3();
        final Vector3 velocity = new Vector3();
        final Vector3 spin = new Vector3();
        float maxTime = Float.NaN;
        boolean air;

        boolean matches(Ball ball, float maxTime, float watchHeight, boolean air) {
            return this.maxTime == maxTime && this.air == air && position.equals(ball.position)
                    && velocity.equals(ball.velocity) && spin.equals(ball.spin)
                    && Float.compare(result.watchHeight, watchHeight) == 0;
        }
    }

    private static final int MEMO_SIZE = 8;
    private final Memo[] memos = new Memo[MEMO_SIZE];
    private int nextMemo;

    public static final float STEP = 1f / 120f;
    public static final float SAMPLE_INTERVAL = 0.05f;

    private final Aerodynamics aerodynamics;
    private final Vector3 position = new Vector3();
    private final Vector3 velocity = new Vector3();
    private final Vector3 spin = new Vector3();
    private final Vector3 previous = new Vector3();
    private final Vector3 acceleration = new Vector3();

    public TrajectoryPredictor(Aerodynamics aerodynamics) {
        this.aerodynamics = aerodynamics;
        for (int i = 0; i < MEMO_SIZE; i++) memos[i] = new Memo();
    }

    /** Forecast for a ball currently in the given observable state. */
    public Prediction predict(Vector3 startPosition, Vector3 startVelocity, Vector3 startSpin, float maxTime, Prediction out) {
        out.reset();
        position.set(startPosition);
        velocity.set(startVelocity);
        spin.set(startSpin);
        out.apex.set(position);
        out.addSample(position);
        float time = 0f;
        float nextSample = SAMPLE_INTERVAL;
        while (time < maxTime) {
            // Same sub-stepping and integrator as BallPhysics, minus collisions and hidden effects.
            int n = BallPhysics.subSteps(velocity.len(), STEP);
            float h = STEP / n;
            for (int i = 0; i < n; i++) {
                previous.set(position);
                aerodynamics.acceleration(velocity, spin, 0L, 0f, false, acceleration);
                velocity.mulAdd(acceleration, h);
                position.mulAdd(velocity, h);
                time += h;
                if (position.y > out.apex.y) {
                    out.apex.set(position);
                    out.timeToApex = time;
                }
                if (!out.crossesNetPlane && Math.signum(previous.x) != Math.signum(position.x) && previous.x != 0f) {
                    float t = previous.x / (previous.x - position.x);
                    out.crossesNetPlane = true;
                    out.netCrossing.set(previous).lerp(position, t);
                    out.timeToNetCrossing = time - h * (1f - t);
                }
                if (!out.reachesWatchHeight && !Float.isNaN(out.watchHeight) && velocity.y < 0f
                        && previous.y >= out.watchHeight && position.y < out.watchHeight) {
                    float t = (previous.y - out.watchHeight) / Math.max(1e-6f, previous.y - position.y);
                    out.reachesWatchHeight = true;
                    out.descentPoint.set(previous).lerp(position, t);
                    out.timeToDescent = time - h * (1f - t);
                }
                if (position.y <= Ball.RADIUS && velocity.y < 0f) {
                    float t = (previous.y - Ball.RADIUS) / Math.max(1e-6f, previous.y - position.y);
                    out.lands = true;
                    out.landing.set(previous).lerp(position, t);
                    out.landing.y = Ball.RADIUS;
                    out.timeToLanding = time - h * (1f - t);
                    out.addSample(out.landing);
                    return out;
                }
            }
            spin.scl((float) Math.exp(-BallPhysics.SPIN_DECAY_PER_SECOND * STEP));
            if (time >= nextSample) {
                out.addSample(position);
                nextSample += SAMPLE_INTERVAL;
            }
        }
        return out;
    }

    /**
     * The forecast flies through the net (it has no collisions). A ball that meets the net below
     * its top, between the antennas, before it comes down to the watched height drops back on the
     * side it came from: its descent is moved to the net on that side ({@code fromX} is where the
     * ball is now), so the players there play it instead of letting it go as if it went over.
     */
    public static void dropAtNet(Prediction p, float netTop, float fromX) {
        if (!p.crossesNetPlane || !p.reachesWatchHeight || p.timeToNetCrossing > p.timeToDescent) return;
        if (p.netCrossing.y > netTop + Ball.RADIUS || Math.abs(p.netCrossing.z) > com.volleyball.game.world.CourtDimensions.HALF_WIDTH + 0.1f) return;
        float sign = fromX <= 0f ? -1f : 1f;
        p.descentPoint.set(sign * 0.35f, Math.min(p.netCrossing.y, p.watchHeight), p.netCrossing.z);
        p.timeToDescent = p.timeToNetCrossing;
    }

    /**
     * Forecast for a live ball. Reads only observable state. Repeated questions about the same
     * ball state (same horizon and watched height) are answered from a small memo.
     */
    public Prediction predict(Ball ball, float maxTime, Prediction out) {
        boolean air = aerodynamics.airEnabled();
        for (Memo memo : memos) {
            if (memo.matches(ball, maxTime, out.watchHeight, air)) {
                out.set(memo.result);
                return out;
            }
        }
        predict(ball.position, ball.velocity, ball.spin, maxTime, out);
        Memo memo = memos[nextMemo];
        nextMemo = (nextMemo + 1) % MEMO_SIZE;
        memo.position.set(ball.position);
        memo.velocity.set(ball.velocity);
        memo.spin.set(ball.spin);
        memo.maxTime = maxTime;
        memo.air = air;
        memo.result.set(out);
        return out;
    }
}
