package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.world.CourtDimensions;

import java.util.Random;

/**
 * Attack shots. The intended shot lands on the aimed spot; what the ball really does is decided
 * by physics from the contact point. A power spike is aimed on a flat line from the hand, so a
 * hitter who contacts the ball too low (late jump, poor set) will drive it into the net, and a
 * mistimed contact (low quality) sprays the ball long or wide. Soft shots (tip, roll) are lobs
 * that pass over the net with a margin; the cut shot curves to the sharp angle; the tool is a
 * hard, flat drive at the block's outside hand.
 */
public final class AttackMechanic {
    /** A tip (and a setter's dump) never lands closer than this behind the net. */
    static final float TIP_MIN_DEPTH = 1.0f;

    private final LaunchSolver solver;
    private final Vector3 lobTarget = new Vector3();

    public AttackMechanic(LaunchSolver solver) {
        this.solver = solver;
    }

    /** Ball speed of a clean power spike by this player (m/s). */
    public static float powerSpeed(PlayerAttributes a) {
        return PlayerAttributes.scale(a.attackPower, 19f, 31f);
    }

    public void attack(ContactContext c, Random random, LaunchSolver.Solution out) {
        PlayerAttributes a = c.player.attributes;
        float accuracy = PlayerAttributes.normalized(a.attackAccuracy);
        float q = c.quality;
        float sigma;
        float topspin;
        float sidespin = 0f;
        switch (c.attackType) {
            case TIP -> {
                topspin = 0f;
                lob(c, 0.55f, 0f, out);
                sigma = 1.3f * (1.6f - q) * (1.4f - accuracy);
            }
            case ROLL_SHOT -> {
                topspin = 18f;
                solver.solveOverNet(c.ballPosition, c.target.x, c.target.z, 9f, 14f, topspin, 0f,
                        c.netTop + Ball.RADIUS + 0.35f, out);
                if (!out.clearsNet) lob(c, 1.0f, topspin, out);
                sigma = 2.0f * (1.6f - q) * (1.4f - accuracy);
            }
            case CUT -> {
                // A little slower than the full swing, with sidespin that bends it further to the angle.
                float speed = powerSpeed(a) * 0.82f * (0.65f + 0.35f * q);
                topspin = 20f + 10f * q;
                float travelX = c.target.x - c.ballPosition.x;
                float travelZ = c.target.z - c.ballPosition.z;
                // ω = +Y bends a ball travelling along ±X towards ∓Z: choose the sign that bends with the shot.
                sidespin = -Math.signum(travelZ == 0f ? 1f : travelZ) * Math.signum(travelX == 0f ? 1f : travelX) * 9f;
                solver.solve(c.ballPosition, c.target.x, c.target.z, speed, topspin, sidespin, out);
                clearFromBackCourt(c, speed, topspin, sidespin, out);
                sigma = 2.6f * (1.6f - q) * (1.4f - accuracy);
            }
            case TOOL -> {
                // Flat and hard: off the hands it flies on, out of the court.
                float speed = powerSpeed(a) * (0.78f + 0.22f * q);
                topspin = 10f;
                solver.solve(c.ballPosition, c.target.x, c.target.z, speed, topspin, 0f, out);
                sigma = 2.2f * (1.6f - q) * (1.4f - accuracy);
            }
            default -> {
                float speed = powerSpeed(a) * (0.65f + 0.35f * q);
                topspin = 30f + 15f * q;
                // Flat line from the hand: no help from the solver to get over the net (at the net).
                solver.solve(c.ballPosition, c.target.x, c.target.z, speed, topspin, 0f, out);
                clearFromBackCourt(c, speed, topspin, 0f, out);
                sigma = 2.4f * (1.6f - q) * (1.4f - accuracy);
            }
        }
        ExecutionError.apply(out.velocity, sigma, 0.05f * (1.5f - q), random);
        LaunchSolver.spinFor(out.velocity, topspin, sidespin + ExecutionError.gaussian(random) * 4f * (1.2f - q), out.spin);
    }

    /**
     * A back-row attack (contact more than {@link #BACK_COURT_DISTANCE} from the net) cannot be hit
     * down at a short spot: the ball would meet the net. Real hitters hit through a higher line
     * and deeper; so does this one, moving the aim back (and, if even the end line is not enough,
     * taking some pace off) until the line clears the tape. Hits near the net are left alone.
     */
    private void clearFromBackCourt(ContactContext c, float speed, float topspin, float sidespin, LaunchSolver.Solution out) {
        float fromNet = Math.abs(c.ballPosition.x);
        if (fromNet < BACK_COURT_DISTANCE || Math.signum(c.target.x) == Math.signum(c.ballPosition.x)) return;
        float clearance = c.netTop + Ball.RADIUS + 0.12f;
        out.spin.setZero();
        LaunchSolver.spinFor(out.velocity, topspin, sidespin, out.spin);
        if (solver.clearsNet(c.ballPosition, out.velocity, out.spin, clearance)) return;
        float sign = Math.signum(c.target.x);
        float x = c.target.x;
        float s = speed;
        for (int attempt = 0; attempt < 12; attempt++) {
            if (Math.abs(x) < CourtDimensions.HALF_LENGTH - 0.6f) x += sign * 0.8f;
            else s *= 0.92f;
            solver.solve(c.ballPosition, x, c.target.z, s, topspin, sidespin, out);
            LaunchSolver.spinFor(out.velocity, topspin, sidespin, out.spin);
            if (solver.clearsNet(c.ballPosition, out.velocity, out.spin, clearance)) return;
        }
    }

    /** Contacts farther than this from the net are back-row attacks (see {@link #clearFromBackCourt}). */
    static final float BACK_COURT_DISTANCE = 2.2f;

    /**
     * A soft lob over the net to the aimed spot (at least {@link #TIP_MIN_DEPTH} behind the net):
     * it peaks at least {@code apexAboveNet} above the net top (higher if needed to clear it) and
     * comes down onto the spot. A tip from close to the net, even from below the tape, gets over.
     */
    private void lob(ContactContext c, float apexAboveNet, float topspin, LaunchSolver.Solution out) {
        lobTarget.set(c.target.x, Ball.RADIUS, c.target.z);
        float sign = Math.signum(lobTarget.x == 0f ? -c.ballPosition.x : lobTarget.x);
        if (Math.abs(lobTarget.x) < TIP_MIN_DEPTH) lobTarget.x = sign * TIP_MIN_DEPTH;
        lobTarget.z = Math.max(-CourtDimensions.HALF_WIDTH - 1f, Math.min(CourtDimensions.HALF_WIDTH + 1f, lobTarget.z));
        float clearance = c.netTop + Ball.RADIUS + 0.08f;
        float apex = Math.max(c.netTop + apexAboveNet, c.ballPosition.y + 0.25f);
        for (int attempt = 0; attempt < 5; attempt++) {
            if (solver.solveArc(c.ballPosition, lobTarget, apex, topspin, out)
                    && solver.clearsNet(c.ballPosition, out.velocity, out.spin, clearance)) return;
            apex += 0.35f;
        }
        // No clean lob found: the old fast solution over the net.
        solver.solveOverNet(c.ballPosition, lobTarget.x, lobTarget.z, 4.5f, 9f, topspin, 0f, clearance, out);
    }
}
