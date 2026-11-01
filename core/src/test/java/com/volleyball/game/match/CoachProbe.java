package com.volleyball.game.match;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Developer probe (PROBE=1): who makes the errors that end rallies (beginners). */
class CoachProbe {
    @Test
    void probe() {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        Map<String, Integer> errors = new TreeMap<>();
        int[] subs = {0};
        for (long seed = 1; seed <= 3; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 5);
            MatchController match = new MatchController(world, new MatchFormat(2, 15, 15, 2, RuleMode.OFFICIAL, 8f), seed,
                    Difficulty.BEGINNER, Difficulty.BEGINNER);
            match.addListener(new MatchListener() {
                @Override
                public void onPoint(MatchRules.PointSummary point, MatchScore score) {
                    var o = point.outcome();
                    var p = o.playerId() < 0 ? null : world.player(o.playerId());
                    String who = p == null ? "-" : (p.side == o.winner() ? "winner " : "loser ") + p.role + " #" + p.number;
                    errors.merge(o.reason() + " " + who, 1, Integer::sum);
                }

                @Override
                public void onSubstitution(CourtSide side, com.volleyball.game.entities.VolleyballPlayer out,
                                           com.volleyball.game.entities.VolleyballPlayer in, int used) {
                    subs[0]++;
                }
            });
            match.start(CourtSide.HOME);
            for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 30; i++) {
                world.step(1f / 120f);
                match.update(1f / 120f, List.of());
            }
        }
        StringBuilder out = new StringBuilder("subs " + subs[0] + "\n");
        errors.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(30)
                .forEach(e -> out.append(String.format("  %3d %s%n", e.getValue(), e.getKey())));
        System.out.println(out);
    }
}
