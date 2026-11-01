package com.volleyball.game.entities;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Volleyball state (FIVB rule 3: circumference 65–67 cm, mass 260–280 g). Plain data; the
 * motion is computed by {@code physics.BallPhysics}.
 */
public final class Ball {
    public static final float RADIUS = 0.105f;
    public static final float MASS = 0.27f;
    /** Ratio I / (m r²). A thin hollow sphere is 2/3; a volleyball (thick leather shell) is close to it. */
    public static final float INERTIA_FACTOR = 0.66f;
    public static final float INERTIA = INERTIA_FACTOR * MASS * RADIUS * RADIUS;

    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    /** Angular velocity in rad/s; the vector is the rotation axis (right-hand rule). */
    public final Vector3 spin = new Vector3();
    public final Quaternion orientation = new Quaternion();

    /** State at the start of the last fixed step, used to interpolate rendering. */
    public final Vector3 previousPosition = new Vector3();
    public final Quaternion previousOrientation = new Quaternion();

    /** Whether the ball is simulated. An inactive ball is held (e.g. by the server) or parked. */
    private boolean active;
    /** Seed of the unpredictable "knuckle" fluctuations for the current flight; changes on every hit. */
    private long flightSeed;
    private float timeSinceHit;

    public boolean isActive() {
        return active;
    }

    /** Holds the ball still at a position (not simulated). */
    public void hold(Vector3 at) {
        active = false;
        position.set(at);
        previousPosition.set(at);
        velocity.setZero();
        spin.setZero();
    }

    /**
     * Starts a new flight: the ball leaves a contact with the given velocity and spin.
     *
     * @param seed seed for the aerodynamic fluctuations of this flight (hidden from AI prediction)
     */
    public void launch(Vector3 from, Vector3 newVelocity, Vector3 newSpin, long seed) {
        position.set(from);
        previousPosition.set(from);
        velocity.set(newVelocity);
        spin.set(newSpin);
        flightSeed = seed;
        timeSinceHit = 0f;
        active = true;
    }

    /** A contact with a player changes velocity and spin in place, starting a new flight. */
    public void hit(Vector3 newVelocity, Vector3 newSpin, long seed) {
        launch(position, newVelocity, newSpin, seed);
    }

    public long flightSeed() {
        return flightSeed;
    }

    public float timeSinceHit() {
        return timeSinceHit;
    }

    public void advanceFlightTime(float dt) {
        timeSinceHit += dt;
    }

    public float speed() {
        return velocity.len();
    }

    /** Spin rate in revolutions per second. */
    public float spinRevolutionsPerSecond() {
        return spin.len() / (2f * (float) Math.PI);
    }

    /** How the ball looks at a moment (place, rotation, in play): saved for replays. */
    public static final class VisualState {
        final Vector3 position = new Vector3();
        final Vector3 velocity = new Vector3();
        final Vector3 spin = new Vector3();
        final Quaternion orientation = new Quaternion();
        boolean active;
        float timeSinceHit;

        /** Blends two states (slow motion): place and rotation in between. */
        public void interpolate(VisualState a, VisualState b, float t) {
            position.set(a.position).lerp(b.position, t);
            velocity.set(a.velocity).lerp(b.velocity, t);
            spin.set(a.spin);
            orientation.set(a.orientation).slerp(b.orientation, t);
            active = a.active;
            timeSinceHit = a.timeSinceHit;
        }
    }

    public void saveVisual(VisualState out) {
        out.position.set(position);
        out.velocity.set(velocity);
        out.spin.set(spin);
        out.orientation.set(orientation);
        out.active = active;
        out.timeSinceHit = timeSinceHit;
    }

    public void restoreVisual(VisualState in) {
        position.set(in.position);
        previousPosition.set(in.position);
        velocity.set(in.velocity);
        spin.set(in.spin);
        orientation.set(in.orientation);
        previousOrientation.set(in.orientation);
        active = in.active;
        timeSinceHit = in.timeSinceHit;
    }
}
