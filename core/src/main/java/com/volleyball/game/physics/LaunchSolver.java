package com.volleyball.game.physics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Finds the launch direction that sends a ball with a given speed and spin to a target spot on
 * the floor, using the (observable) aerodynamic model. Players' contacts aim with it; their
 * execution errors are applied on top by the ball-handling mechanics.
 *
 * <p>Spin is specified relative to the flight direction: positive topspin makes the ball dive,
 * negative topspin is backspin; positive sidespin curves the ball to the left of its direction
 * of travel (counter-clockwise seen from above).
 */
public final class LaunchSolver {
    public static final float MIN_PITCH = -25f * MathUtils.degreesToRadians;
    public static final float MAX_PITCH = 75f * MathUtils.degreesToRadians;

    /** Solution of {@link #solve}. */
    public static final class Solution {
        public final Vector3 velocity = new Vector3();
        public final Vector3 spin = new Vector3();
        /** False if the target is out of range; the solution then gives the maximum range. */
        public boolean reachable;
        /** For {@link #solveOverNet}: whether the trajectory passes the net plane above the clearance height. */
        public boolean clearsNet;
        public float pitch;
        public float yaw;
    }

    private final TrajectoryPredictor predictor;
    private final TrajectoryPredictor.Prediction prediction = new TrajectoryPredictor.Prediction();
    private final Vector3 velocity = new Vector3();
    private final Vector3 spin = new Vector3();

    public LaunchSolver(TrajectoryPredictor predictor) {
        this.predictor = predictor;
    }

    /** World-space angular velocity for the given topspin and sidespin (rad/s) and flight direction. */
    public static Vector3 spinFor(Vector3 velocity, float topspin, float sidespin, Vector3 out) {
        // Topspin axis is horizontal and perpendicular to the flight: Y × v̂ (ω̂ × v̂ then points down).
        out.set(Vector3.Y).crs(velocity);
        if (out.len2() < 1e-10f) out.set(Vector3.Z);
        out.nor().scl(topspin);
        out.y += sidespin;
        return out;
    }

    /** Velocity vector for speed, pitch (elevation) and yaw (0 = +X, as player facing). */
    public static Vector3 directionVelocity(float speed, float pitch, float yaw, Vector3 out) {
        float horizontal = MathUtils.cos(pitch) * speed;
        return out.set(horizontal * MathUtils.cos(yaw), MathUtils.sin(pitch) * speed, -horizontal * MathUtils.sin(yaw));
    }

    /**
     * Solves for the lower (flatter) trajectory that lands at {@code targetX, targetZ}.
     */
    public Solution solve(Vector3 start, float targetX, float targetZ, float speed, float topspin, float sidespin,
                          Solution out) {
        float dx = targetX - start.x;
        float dz = targetZ - start.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float yaw = MathUtils.atan2(-dz, dx);
        float pitch = 0f;
        out.reachable = false;
        // Pitch controls range; yaw is then corrected for the sideways drift caused by sidespin.
        // Each yaw correction is followed by a fresh pitch solve, so the final pair is consistent.
        for (int pass = 0; pass < 8; pass++) {
            pitch = solvePitch(start, speed, yaw, topspin, sidespin, distance, out);
            simulate(start, speed, pitch, yaw, topspin, sidespin);
            if (!prediction.lands) break;
            float landedYaw = MathUtils.atan2(-(prediction.landing.z - start.z), prediction.landing.x - start.x);
            float error = wrapAngle(MathUtils.atan2(-dz, dx) - landedYaw);
            if (Math.abs(error) < 0.0005f) break;
            yaw += error;
        }
        out.pitch = pitch;
        out.yaw = yaw;
        directionVelocity(speed, pitch, yaw, out.velocity);
        spinFor(out.velocity, topspin, sidespin, out.spin);
        return out;
    }

    /**
     * Like {@link #solve}, but picks the lowest speed in [minSpeed, maxSpeed] whose flat
     * trajectory still crosses the net plane at or above {@code clearanceHeight} (ball centre).
     * This is how a server or attacker trades pace for safety over the net.
     */
    public Solution solveOverNet(Vector3 start, float targetX, float targetZ, float minSpeed, float maxSpeed,
                                 float topspin, float sidespin, float clearanceHeight, Solution out) {
        for (float speed = minSpeed; speed <= maxSpeed + 1e-3f; speed += 0.5f) {
            solve(start, targetX, targetZ, speed, topspin, sidespin, out);
            if (!out.reachable) continue;
            predictor.predict(start, out.velocity, out.spin, 6f, prediction);
            out.clearsNet = !prediction.crossesNetPlane || prediction.netCrossing.y >= clearanceHeight;
            if (out.clearsNet) return out;
        }
        out.clearsNet = false;
        return out;
    }

    /** Whether a ball launched like this passes the net plane at or above {@code clearanceHeight} (or never reaches it). */
    public boolean clearsNet(Vector3 start, Vector3 launchVelocity, Vector3 launchSpin, float clearanceHeight) {
        predictor.predict(start, launchVelocity, launchSpin, 6f, prediction);
        return !prediction.crossesNetPlane || prediction.netCrossing.y >= clearanceHeight;
    }

    /**
     * Solves a lob: the ball rises to roughly {@code apexHeight} and on the way down passes
     * through {@code target} (a point in the air, e.g. a setter's hands). This is how passes and
     * sets are aimed: by height and spot rather than by speed.
     *
     * @return false if the target cannot be reached on the descent (e.g. it is above the apex)
     */
    public boolean solveArc(Vector3 start, Vector3 target, float apexHeight, float topspin, Solution out) {
        float apex = Math.max(apexHeight, Math.max(start.y, target.y) + 0.15f);
        float dx = target.x - start.x;
        float dz = target.z - start.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float dirX = distance < 1e-4f ? 1f : dx / distance;
        float dirZ = distance < 1e-4f ? 0f : dz / distance;
        prediction.watchHeight = target.y;

        float vertical = (float) Math.sqrt(2f * PhysicsConstants.GRAVITY * (apex - start.y));
        float horizontal = distance / Math.max(0.2f, flightTimeEstimate(start.y, apex, target.y));
        boolean ok = false;
        // Alternate between the vertical speed (apex) and horizontal speed (distance): drag couples them.
        for (int pass = 0; pass < 4; pass++) {
            float low = 0f;
            float high = Math.max(2f, horizontal * 3f);
            ok = false;
            for (int i = 0; i < 40; i++) {
                float mid = (low + high) / 2f;
                velocity.set(dirX * mid, vertical, dirZ * mid);
                spinFor(velocity, topspin, 0f, spin);
                predictor.predict(start, velocity, spin, 8f, prediction);
                if (!prediction.reachesWatchHeight) {
                    high = mid;
                    continue;
                }
                float reached = (float) Math.sqrt(sq(prediction.descentPoint.x - start.x) + sq(prediction.descentPoint.z - start.z));
                ok = true;
                if (reached < distance) low = mid;
                else high = mid;
            }
            horizontal = (low + high) / 2f;
            if (!ok) break;
            float actualApex = prediction.apex.y;
            if (Math.abs(actualApex - apex) < 0.03f) break;
            vertical *= (float) Math.sqrt(Math.max(0.05f, apex - start.y) / Math.max(0.05f, actualApex - start.y));
        }
        prediction.watchHeight = Float.NaN;
        out.velocity.set(dirX * horizontal, vertical, dirZ * horizontal);
        spinFor(out.velocity, topspin, 0f, out.spin);
        out.reachable = ok;
        out.pitch = MathUtils.atan2(vertical, horizontal);
        out.yaw = MathUtils.atan2(-dirZ, dirX);
        return ok;
    }

    private static float flightTimeEstimate(float startY, float apex, float targetY) {
        float g = PhysicsConstants.GRAVITY;
        return (float) (Math.sqrt(2f * Math.max(0f, apex - startY) / g) + Math.sqrt(2f * Math.max(0f, apex - targetY) / g));
    }

    private static float sq(float v) {
        return v * v;
    }

    private float solvePitch(Vector3 start, float speed, float yaw, float topspin, float sidespin, float distance,
                             Solution out) {
        // Coarse scan for the pitch of maximum range, then bisection on the rising (flat) branch.
        float bestPitch = MIN_PITCH;
        float bestRange = -1f;
        for (float pitch = MIN_PITCH; pitch <= MAX_PITCH; pitch += 2.5f * MathUtils.degreesToRadians) {
            float range = range(start, speed, pitch, yaw, topspin, sidespin);
            if (range > bestRange) {
                bestRange = range;
                bestPitch = pitch;
            }
        }
        if (bestRange < distance) {
            out.reachable = false;
            return bestPitch;
        }
        float low = MIN_PITCH;
        float high = bestPitch;
        if (range(start, speed, low, yaw, topspin, sidespin) > distance) {
            out.reachable = true;
            return low;
        }
        for (int i = 0; i < 30; i++) {
            float mid = (low + high) / 2f;
            if (range(start, speed, mid, yaw, topspin, sidespin) < distance) low = mid;
            else high = mid;
        }
        out.reachable = true;
        return (low + high) / 2f;
    }

    private float range(Vector3 start, float speed, float pitch, float yaw, float topspin, float sidespin) {
        simulate(start, speed, pitch, yaw, topspin, sidespin);
        if (!prediction.lands) return -1f;
        float dx = prediction.landing.x - start.x;
        float dz = prediction.landing.z - start.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private void simulate(Vector3 start, float speed, float pitch, float yaw, float topspin, float sidespin) {
        directionVelocity(speed, pitch, yaw, velocity);
        spinFor(velocity, topspin, sidespin, spin);
        predictor.predict(start, velocity, spin, 6f, prediction);
    }

    private static float wrapAngle(float angle) {
        while (angle > MathUtils.PI) angle -= MathUtils.PI2;
        while (angle < -MathUtils.PI) angle += MathUtils.PI2;
        return angle;
    }
}
