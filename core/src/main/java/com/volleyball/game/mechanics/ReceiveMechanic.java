package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.physics.LaunchSolver;

import java.util.Random;

/**
 * Passing the ball with the forearm platform or overhead: serve receive, digs of soft balls,
 * free balls over the net and bump-sets.
 *
 * <p>The intended ball is a lob to the target (usually the setter). How close the real ball gets
 * depends on contact quality, the receiving rating and the incoming pace: a hard ball rebounds
 * off a platform that does not absorb it well, adding uncontrolled speed in the mirror direction.
 */
public final class ReceiveMechanic {
    private final LaunchSolver solver;
    private final Vector3 reflected = new Vector3();
    private final Vector3 normal = new Vector3();

    public ReceiveMechanic(LaunchSolver solver) {
        this.solver = solver;
    }

    /** Incoming pace difficulty: 0 for a soft ball (≤ 8 m/s), 1 for a 26 m/s serve or spike. */
    public static float difficulty(Vector3 incoming) {
        return ExecutionError.clamp01((incoming.len() - 8f) / 18f);
    }

    public void pass(ContactContext c, Random random, LaunchSolver.Solution out) {
        boolean overhead = c.type == ContactType.OVERHEAD_PASS;
        PlayerAttributes a = c.player.attributes;
        float skill = PlayerAttributes.normalized(overhead ? (a.receiving + a.setting) / 2 : a.receiving);
        // Fingers handle hard balls much worse than a platform.
        float difficulty = difficulty(c.incomingVelocity) * (overhead ? 1.6f : 1f);

        if (!solver.solveArc(c.ballPosition, c.target, c.apex, -2f, out)) {
            // Target unreachable as a lob (e.g. above the apex): just keep the ball up.
            out.velocity.set((c.target.x - c.ballPosition.x) * 0.3f, 7f, (c.target.z - c.ballPosition.z) * 0.3f);
        }

        // A hard ball leaves less time to set the platform: the effective contact is worse.
        float quality = c.quality * (1f - 0.35f * Math.min(1f, difficulty));
        // Uncontrolled rebound of a hard ball: mirror the incoming velocity about the platform.
        float control = 0.6f * quality + 0.4f * skill;
        normal.set(out.velocity).nor();
        reflected.set(c.incomingVelocity).mulAdd(normal, -2f * c.incomingVelocity.dot(normal));
        out.velocity.mulAdd(reflected, (overhead ? 0.25f : 0.4f) * (1f - control) * Math.min(1f, difficulty));

        // Off-centre contact on the platform: the ball glances off to that side.
        ExecutionError.deflectRight(out.velocity, c.lateralOffset * (overhead ? 8f : 12f));
        float emergency = switch (c.type) {
            case DIVE -> 1.6f;
            case ONE_ARM -> 2.0f;
            default -> 1f;
        };
        float sigma = 2.2f * (1.7f - quality) * (1.5f - skill) * (1f + 1.8f * difficulty) * emergency;
        ExecutionError.apply(out.velocity, sigma, 0.05f * (1.6f - quality) * (1f + 1.5f * difficulty), random);
        float spinNoise = 3f * (1.2f - quality);
        out.spin.set(ExecutionError.gaussian(random) * spinNoise, ExecutionError.gaussian(random) * spinNoise,
                ExecutionError.gaussian(random) * spinNoise);
    }
}
