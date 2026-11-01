package com.volleyball.game.match;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.rules.MatchFormat;
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

/** Developer probe (PROBE=1): who makes the first touch (serve reception, digs, free balls), and how the possession goes on. */
class FirstTouchProbe {
    @Test
    void probe() {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        Difficulty level = Difficulty.parse(System.getenv().getOrDefault("PROBE_LEVEL", "pro"));
        Map<String, int[]> counts = new TreeMap<>(); // touches, then: setter made the 2nd touch
        int seeds = Integer.parseInt(System.getenv().getOrDefault("PROBE_SEEDS", "6"));
        for (long seed = 1; seed <= seeds; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
            MatchController match = new MatchController(world, new MatchFormat(1, 25, 15, 2, RuleMode.OFFICIAL, 8f), seed, level, level);
            BallTouch[] previous = {null};
            String[] firstKey = {null};
            world.ballHandling().addListener(t -> {
                BallTouch prev = previous[0];
                if (t.teamTouchNumber() == 1 && t.type() != ContactType.SERVE && t.type() != ContactType.BLOCK) {
                    String what = prev == null ? "?" : prev.type() == ContactType.SERVE ? "reception" : prev.type() == ContactType.ATTACK ? "dig" : "free ball";
                    firstKey[0] = what + " " + world.player(t.playerId()).role;
                    counts.computeIfAbsent(firstKey[0], k -> new int[3])[0]++;
                } else if (t.teamTouchNumber() == 2 && firstKey[0] != null) {
                    boolean setter = world.player(t.playerId()).role == com.volleyball.game.entities.PlayerRole.SETTER;
                    counts.get(firstKey[0])[setter ? 1 : 2]++;
                    firstKey[0] = null;
                }
                previous[0] = t;
            });
            match.start(CourtSide.HOME);
            for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 25; i++) {
                world.step(1f / 120f);
                match.update(1f / 120f, List.of());
            }
        }
        StringBuilder out = new StringBuilder("first touches: count / then the setter set / someone else set\\n");
        counts.forEach((k, v) -> out.append(String.format("  %-34s %4d %4d %4d%n", k, v[0], v[1], v[2])));
        System.out.println(out);
    }
}
