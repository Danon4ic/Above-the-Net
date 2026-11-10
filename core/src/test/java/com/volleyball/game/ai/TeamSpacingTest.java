package com.volleyball.game.ai;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teammates spread over the court instead of bunching: in the defence every back-row player has
 * its own spot, the cover has a slot per player, and players run around each other.
 */
class TeamSpacingTest {
    static final float DT = 1f / 120f;
    static final MatchFormat SHORT = new MatchFormat(1, 15, 15, 2, RuleMode.OFFICIAL, 8f);

    /**
     * Share of rally time in which two teammates stand within 1 m of each other (not counting the
     * ball player, players in the air and blockers side by side at the net).
     */
    private static float crowdedShare(Difficulty difficulty, int sets) {
        double rally = 0;
        double crowded = 0;
        for (long seed = 1; seed <= sets; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 11);
            MatchController match = new MatchController(world, SHORT, seed, difficulty, difficulty);
            match.start(CourtSide.HOME);
            for (long steps = 0; match.phase() != MatchController.Phase.FINISHED; steps++) {
                world.step(DT);
                match.update(DT, List.of());
                assertTrue(steps < 120L * 60 * 15, "the set must finish");
                if (match.phase() != MatchController.Phase.RALLY || !world.ball().isActive()
                        || world.ballHandling().servePhase() != BallHandling.ServePhase.NONE) continue;
                for (CourtSide side : CourtSide.values()) {
                    TeamAI ai = match.ai(side);
                    List<VolleyballPlayer> court = (side == CourtSide.HOME ? world.home() : world.away()).onCourt();
                    rally += DT;
                    boolean close = false;
                    for (int i = 0; i < court.size() && !close; i++) {
                        VolleyballPlayer a = court.get(i);
                        for (int j = i + 1; j < court.size() && !close; j++) {
                            VolleyballPlayer b = court.get(j);
                            if (a.id == ai.responsibleId() || b.id == ai.responsibleId() || a.isAirborne() || b.isAirborne()) continue;
                            if (ai.state(a) == AiState.BLOCKING && ai.state(b) == AiState.BLOCKING) continue;
                            close = Math.hypot(a.position.x - b.position.x, a.position.z - b.position.z) < 1.0;
                        }
                    }
                    if (close) crowded += DT;
                }
            }
        }
        return (float) (crowded / rally);
    }

    @Test
    void teammatesDoNotBunchUp() {
        // Before the formation for every level, the cover slots and the detours: 33-49 % of the time.
        for (Difficulty d : Difficulty.values()) {
            float share = crowdedShare(d, 2);
            assertTrue(share < 0.15f, d + ": two teammates within 1 m for " + Math.round(share * 100) + " % of the rally time");
        }
    }
}
