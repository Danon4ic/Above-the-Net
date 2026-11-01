package com.volleyball.game.physics;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;

/**
 * Advances the ball by one fixed simulation step.
 *
 * <p>Integration is semi-implicit Euler (velocity first, then position), which is stable for
 * this mix of gravity, quadratic drag and Magnus force. Each step is split into sub-steps so
 * that the ball never travels more than {@link #MAX_SUBSTEP_TRAVEL} (40 % of its radius) per
 * sub-step; collisions are resolved after every sub-step. A 30 m/s spike at 120 Hz therefore
 * uses 6 sub-steps of ~4 cm, so it cannot pass through the 1 cm net or the antennas.
 */
public final class BallPhysics {
    public static final float MAX_SUBSTEP_TRAVEL = 0.4f * Ball.RADIUS;
    public static final int MAX_SUBSTEPS = 64;
    /** Spin decays slowly in flight due to air friction torque (~4 % per second). */
    static final float SPIN_DECAY_PER_SECOND = 0.04f;

    private final Aerodynamics aerodynamics;
    private final CollisionSystem collisions;
    private final Vector3 acceleration = new Vector3();
    private final Vector3 subStepStart = new Vector3();
    private final Vector3 axis = new Vector3();
    private final Quaternion rotation = new Quaternion();

    public BallPhysics(Aerodynamics aerodynamics, CollisionSystem collisions) {
        this.aerodynamics = aerodynamics;
        this.collisions = collisions;
    }

    public Aerodynamics aerodynamics() {
        return aerodynamics;
    }

    /** Number of sub-steps used for a step of {@code dt} at the given speed. */
    public static int subSteps(float speed, float dt) {
        int n = (int) Math.ceil(speed * dt / MAX_SUBSTEP_TRAVEL);
        return Math.max(1, Math.min(MAX_SUBSTEPS, n));
    }

    public void step(Ball ball, float dt, double time, BallContactListener listener) {
        ball.previousPosition.set(ball.position);
        ball.previousOrientation.set(ball.orientation);
        if (!ball.isActive()) return;

        int n = subSteps(ball.speed(), dt);
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            subStepStart.set(ball.position);
            aerodynamics.acceleration(ball.velocity, ball.spin, ball.flightSeed(), ball.timeSinceHit(), true, acceleration);
            ball.velocity.mulAdd(acceleration, h);
            ball.position.mulAdd(ball.velocity, h);
            collisions.resolve(ball, subStepStart, h, time + i * h, listener);
            ball.advanceFlightTime(h);
        }
        ball.spin.scl((float) Math.exp(-SPIN_DECAY_PER_SECOND * dt));
        integrateOrientation(ball, dt);
        sanitize(ball);
    }

    private void integrateOrientation(Ball ball, float dt) {
        float rate = ball.spin.len();
        if (rate < 1e-5f) return;
        axis.set(ball.spin).scl(1f / rate);
        rotation.setFromAxisRad(axis, rate * dt);
        ball.orientation.mulLeft(rotation).nor();
    }

    /** Guards against numerical blow-ups: a NaN state would otherwise propagate everywhere. */
    private static void sanitize(Ball ball) {
        if (Float.isFinite(ball.position.x) && Float.isFinite(ball.position.y) && Float.isFinite(ball.position.z)
                && Float.isFinite(ball.velocity.x) && Float.isFinite(ball.velocity.y) && Float.isFinite(ball.velocity.z)
                && Float.isFinite(ball.spin.x) && Float.isFinite(ball.spin.y) && Float.isFinite(ball.spin.z)) {
            return;
        }
        ball.hold(ball.previousPosition.isZero() ? new Vector3(0f, Ball.RADIUS, 0f) : ball.previousPosition.cpy());
    }
}
