package com.volleyball.game.match;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The same seed plays the same match, step for step (so a match can be replayed and a bug reproduced). */
class DeterminismTest {
    private static final float DT = 1f / 120f;
    private static final MatchFormat FORMAT = new MatchFormat(1, 8, 8, 2, RuleMode.OFFICIAL, 8f);

    /** A fingerprint of the match after a minute of play: the score, the ball and every player. */
    private static String play(long seed) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
        MatchController match = new MatchController(world, FORMAT, seed, Difficulty.PROFESSIONAL, Difficulty.INTERMEDIATE);
        match.start(CourtSide.HOME);
        for (int i = 0; i < 120 * 60 && match.phase() != MatchController.Phase.FINISHED; i++) {
            world.step(DT);
            match.update(DT, List.of());
        }
        StringBuilder b = new StringBuilder();
        b.append(match.rules().score().points(CourtSide.HOME)).append(':').append(match.rules().score().points(CourtSide.AWAY));
        b.append(' ').append(world.ball().position).append(world.ball().velocity);
        for (VolleyballPlayer p : world.players()) b.append(' ').append(p.id).append(p.position);
        return b.toString();
    }

    @Test
    void theSameSeedPlaysTheSameMatch() {
        assertEquals(play(5), play(5));
    }

    @Test
    void anotherSeedPlaysAnotherMatch() {
        assertNotEquals(play(5), play(6));
    }
}
