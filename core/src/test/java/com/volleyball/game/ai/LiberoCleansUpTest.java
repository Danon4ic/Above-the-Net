package com.volleyball.game.ai;

import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The libero saves the balls nobody else gets to and sets when the setter had to take the first ball. */
class LiberoCleansUpTest {
    static final float DT = 1f / 120f;
    static final MatchFormat SHORT = new MatchFormat(1, 15, 15, 2, RuleMode.OFFICIAL, 8f);

    /** Counts over a few computer-only sets. */
    private static int[] play(int sets) {
        // 0: possessions whose first touch was the setter's (libero on court), 1: of those, second touch by the libero,
        // 2: second touch by anyone, 3: libero dives, 4: libero touches
        int[] n = new int[5];
        for (long seed = 1; seed <= sets; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 5);
            MatchController match = new MatchController(world, SHORT, seed, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
            boolean[] setterFirst = new boolean[1];
            world.ballHandling().addListener(t -> {
                VolleyballPlayer p = world.player(t.playerId());
                if (p.role == PlayerRole.LIBERO && t.type() != ContactType.SERVE) {
                    n[4]++;
                    if (t.type() == ContactType.DIVE) n[3]++;
                }
                if (t.type() == ContactType.BLOCK || t.type() == ContactType.SERVE) {
                    setterFirst[0] = false;
                    return;
                }
                if (t.teamTouchNumber() == 1) {
                    VolleyballPlayer libero = (t.side() == CourtSide.HOME ? world.home() : world.away()).libero();
                    setterFirst[0] = p.role == PlayerRole.SETTER && libero != null && libero.isOnCourt();
                    if (setterFirst[0]) n[0]++;
                } else if (t.teamTouchNumber() == 2 && setterFirst[0]) {
                    n[2]++;
                    if (p.role == PlayerRole.LIBERO) n[1]++;
                    setterFirst[0] = false;
                }
            });
            match.start(CourtSide.HOME);
            for (long steps = 0; match.phase() != MatchController.Phase.FINISHED; steps++) {
                world.step(DT);
                match.update(DT, List.of());
                assertTrue(steps < 120L * 60 * 15, "the set must finish");
            }
        }
        return n;
    }

    @Test
    void theLiberoSetsWhenTheSetterTookTheFirstBallAndDivesForFarBalls() {
        int[] n = play(12);
        String counts = "setter first " + n[0] + ", second touch " + n[2] + ", by the libero " + n[1]
                + "; libero touches " + n[4] + ", dives " + n[3];
        assertTrue(n[0] >= 5, "the setter took some first balls: " + counts);
        assertTrue(n[2] >= n[0] * 0.6f, "after the setter's first touch the ball is rarely lost: " + counts);
        assertTrue(n[1] >= n[2] * 0.6f, "the libero sets after the setter's first touch: " + counts);
        assertTrue(n[3] > 0, "the libero dives for balls out of reach: " + counts);
    }
}
