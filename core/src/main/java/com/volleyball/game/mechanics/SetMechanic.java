package com.volleyball.game.mechanics;

import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.physics.LaunchSolver;

import java.util.Random;

/**
 * Overhead set to an attacker's hitting spot. Faster (lower) sets need more precision, so their
 * error grows; a setter who had to chase a poor pass contacts off-balance or away from the ideal
 * spot, which lowers quality — this is how the pass quality propagates into the set. A one-hand
 * set reaches balls both hands cannot, but places them far less precisely (most of all the fast
 * sets).
 */
public final class SetMechanic {
    /** A one-hand set's error is this many times a two-hand set's (plus the tempo: fast sets suffer most). */
    static final float ONE_HAND_ERROR = 1.6f;

    private final LaunchSolver solver;

    public SetMechanic(LaunchSolver solver) {
        this.solver = solver;
    }

    public void set(ContactContext c, Random random, LaunchSolver.Solution out) {
        float skill = PlayerAttributes.normalized(c.player.attributes.setting);
        if (!solver.solveArc(c.ballPosition, c.target, c.apex, 0f, out)) {
            out.velocity.set((c.target.x - c.ballPosition.x) * 0.4f, 6f, (c.target.z - c.ballPosition.z) * 0.4f);
        }
        // Quick sets (low apex relative to the contact) are the hardest to place.
        float tempo = ExecutionError.clamp01(1f - (c.apex - c.ballPosition.y) / 4f);
        float difficulty = ReceiveMechanic.difficulty(c.incomingVelocity);
        ExecutionError.deflectRight(out.velocity, c.lateralOffset * 8f);
        float sigma = 1.4f * (1.7f - c.quality) * (1.5f - skill) * (1f + 0.6f * tempo + difficulty);
        // Specialists: the tempo setter places the fast sets, the high-ball setter the high ones.
        if (tempo > 0.5f ? c.player.has(PlayerTrait.TEMPO_SETTER) : c.player.has(PlayerTrait.HIGH_BALL_SETTER)) sigma *= 0.8f;
        if (c.type == ContactType.ONE_HAND_SET) sigma *= ONE_HAND_ERROR + tempo;
        ExecutionError.apply(out.velocity, sigma, 0.035f * (1.6f - c.quality), random);
        // Clean hands leave almost no spin; a sloppy set spins (and in a match may be called a double).
        float spinNoise = 4f * (1.1f - c.quality);
        out.spin.set(ExecutionError.gaussian(random) * spinNoise, ExecutionError.gaussian(random) * spinNoise,
                ExecutionError.gaussian(random) * spinNoise);
    }
}
