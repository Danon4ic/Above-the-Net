package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.physics.PhysicsConstants;
import com.volleyball.game.physics.PlayerLocomotion;

import java.util.Random;

/**
 * Serve: the toss and the hit.
 *
 * <p>The toss puts the ball above (or, for jump serves, ahead of) the ideal contact point; a less
 * skilled server tosses less consistently. The hit then aims at the target with the type's
 * pace, preferring the fastest speed that still clears the net with the type's margin, and adds
 * an execution error that grows with poor contact, low serving rating and riskier serve types.
 * A power serve can be curved ({@link ServeSpin}): part of its topspin becomes sidespin, and the
 * launch is aimed so the bending ball still comes down on the target.
 * Nothing guarantees a good serve: a late hit or a bad toss sends it into the net or out.
 */
public final class ServeMechanic {
    private final LaunchSolver solver;

    public ServeMechanic(LaunchSolver solver) {
        this.solver = solver;
    }

    /** Ball speed of a clean serve of this type by this server. */
    public static float speed(ServeType type, PlayerAttributes attributes) {
        return PlayerAttributes.scale(attributes.serving, type.minSpeed, type.maxSpeed);
    }

    /** Where the server holds the ball before tossing. */
    public static Vector3 holdPoint(VolleyballPlayer p, ServeType type, Vector3 out) {
        // As drawn: between the palms in front of the chest, or on the toss hand in front of the belly.
        float forward = type.overhand ? 0.45f : 0.42f;
        float height = type.overhand ? 0.70f * p.height : 0.58f * p.height;
        return out.set(p.position.x + p.facingX() * forward, p.position.y + height, p.position.z + p.facingZ() * forward);
    }

    /** Height of the ball at contact, assuming a full jump for jump serves. */
    static float expectedContactHeight(VolleyballPlayer p, ServeType type) {
        if (!type.overhand) return p.height * 0.42f;
        float reach = p.standingReach() - 0.05f;
        return type.jump ? reach + 0.9f * PlayerLocomotion.jumpHeight(p.attributes) : reach;
    }

    /** Launches the toss from the hold point. */
    public void toss(VolleyballPlayer p, ServeType type, Ball ball, Random random) {
        Vector3 from = holdPoint(p, type, new Vector3());
        float contact = expectedContactHeight(p, type);
        float apex = contact + type.tossAboveContact;
        float g = PhysicsConstants.GRAVITY;
        float up = (float) Math.sqrt(2f * g * Math.max(0.05f, apex - from.y));
        float time = up / g + (float) Math.sqrt(2f * Math.max(0.02f, apex - contact) / g);
        // Toss consistency: up to ±35 cm of drift for a poor server, ±5 cm for an elite one.
        float spread = ExecutionError.lerp(0.35f, 0.05f, PlayerAttributes.normalized(p.attributes.serving));
        // The toss goes to the hitting point (in front of the hitting shoulder), plus the run-up
        // distance for jump serves, plus the server's inconsistency.
        Vector3 hit = ContactZone.idealPoint(p, ContactType.SERVE, type, new Vector3());
        float fx = p.facingX();
        float fz = p.facingZ();
        float forward = type.tossForward + ExecutionError.gaussian(random) * spread * 0.6f;
        float sideways = ExecutionError.gaussian(random) * spread * 0.6f;
        float targetX = hit.x + fx * forward - fz * sideways;
        float targetZ = hit.z + fz * forward + fx * sideways;
        Vector3 velocity = new Vector3(
                (targetX - from.x) / time,
                up * (1f + ExecutionError.gaussian(random) * spread * 0.1f),
                (targetZ - from.z) / time);
        ball.launch(from, velocity, Vector3.Zero, random.nextLong());
    }

    /** Computes the served ball's velocity and spin. */
    public void hit(ContactContext c, Random random, LaunchSolver.Solution out) {
        float skill = PlayerAttributes.normalized(c.player.attributes.serving);
        float speed = speed(c.serveType, c.player.attributes) * (0.85f + 0.15f * c.quality);
        float clearance = c.netTop + Ball.RADIUS + c.serveType.netMargin;
        ServeSpin spin = ServeSpin.applies(c.serveType) && c.serveSpin != null ? c.serveSpin : ServeSpin.TOPSPIN;
        float topspin = c.serveType.topspin * spin.topspinShare;
        float sidespin = c.serveType.topspin * spin.sidespinShare;
        // Fastest safe pace: search downwards from full speed to 75 % of it.
        solver.solveOverNet(c.ballPosition, c.target.x, c.target.z, speed * 0.75f, speed, topspin, sidespin, clearance, out);
        if (!out.clearsNet) {
            // Even the slowest allowed speed would not clear: hit at full pace and let physics decide.
            solver.solve(c.ballPosition, c.target.x, c.target.z, speed, topspin, sidespin, out);
        }
        float sigma = c.serveType.errorDegrees * (1.6f - c.quality) * (1.4f - skill);
        if (c.serveType.topspin == 0f && c.player.has(PlayerTrait.FLOAT_SPECIALIST)) sigma *= 0.7f;
        if (spin != ServeSpin.TOPSPIN) sigma *= ServeSpin.CURVE_ERROR;
        ExecutionError.apply(out.velocity, sigma, 0.03f * (1.6f - c.quality), random);
        // A cleaner contact puts more of the intended spin on the ball.
        float spinQuality = 0.7f + 0.3f * c.quality;
        LaunchSolver.spinFor(out.velocity, topspin * spinQuality, sidespin * spinQuality, out.spin);
        if (c.serveType.topspin == 0f) {
            // A float is hit through the centre of the ball; a mishit leaves a little spin.
            float wobble = (1.2f - c.quality) * 1.2f;
            out.spin.add(ExecutionError.gaussian(random) * wobble, ExecutionError.gaussian(random) * wobble,
                    ExecutionError.gaussian(random) * wobble);
        }
    }

    /** Yaw (radians) pointing from the player towards the target, for facing the aim before serving. */
    public static float aimYaw(VolleyballPlayer p, Vector3 target) {
        return MathUtils.atan2(-(target.z - p.position.z), target.x - p.position.x);
    }
}
