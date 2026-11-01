package com.volleyball.game.ai;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Group blocks: professional teams put up a triple block on slow balls; others block with one or two. */
class GroupBlockTest {
    private static final float DT = TeamAITest.DT;

    /** The largest block (players of one team blocking at once) seen in each rally, over a few sets. */
    private static int[] blockSizes(Difficulty difficulty, long firstSeed, int sets) {
        int[] rallies = new int[4];
        for (long seed = firstSeed; seed < firstSeed + sets; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
            MatchController match = new MatchController(world, TeamAITest.ONE_SET, seed, difficulty, difficulty);
            match.start(CourtSide.HOME);
            int largest = 0;
            for (long i = 0; i < 120L * 60 * 20 && match.phase() != MatchController.Phase.FINISHED; i++) {
                world.step(DT);
                match.update(DT, List.of());
                if (match.phase() != MatchController.Phase.RALLY) {
                    if (largest > 0) rallies[largest]++;
                    largest = 0;
                    continue;
                }
                for (CourtSide side : CourtSide.values()) {
                    int blocking = 0;
                    for (VolleyballPlayer p : side == CourtSide.HOME ? world.home().onCourt() : world.away().onCourt()) {
                        if (match.ai(side).state(p) != AiState.BLOCKING) continue;
                        blocking++;
                        assertTrue(match.rules().rotation().isFrontRow(side, p.id), "only front-row players block");
                    }
                    largest = Math.max(largest, blocking);
                }
            }
        }
        return rallies;
    }

    @Test
    void professionalsPutUpTripleBlocksOnSlowBalls() {
        int[] sizes = blockSizes(Difficulty.PROFESSIONAL, 1, 3);
        int blocks = sizes[1] + sizes[2] + sizes[3];
        assertTrue(sizes[3] > 0, "triple blocks seen: single/double/triple = " + sizes[1] + "/" + sizes[2] + "/" + sizes[3]);
        // Not every ball: quick sets and balls too far for the third blocker stay single or double.
        assertTrue(sizes[3] < blocks, "not every block is a triple: " + sizes[3] + " of " + blocks);
    }

    @Test
    void intermediateTeamsBlockWithAtMostTwo() {
        int[] sizes = blockSizes(Difficulty.INTERMEDIATE, 1, 2);
        assertEquals(0, sizes[3], "no triple block below the professional level");
        assertTrue(sizes[2] > 0, "but double blocks: " + sizes[1] + "/" + sizes[2]);
    }
}
