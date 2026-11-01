package com.volleyball.game.match;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.Fault;
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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Developer probe (PROBE=1): the last second of every computer player that touched the net (PROBE_OUT file). */
class NetTouchProbe {
    @Test
    void probe() throws Exception {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        Difficulty level = Difficulty.parse(System.getenv().getOrDefault("PROBE_LEVEL", "pro"));
        int seeds = Integer.parseInt(System.getenv().getOrDefault("PROBE_SEEDS", "8"));
        StringBuilder out = new StringBuilder();
        int[] touches = {0};
        for (long seed = 1; seed <= seeds; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
            MatchController match = new MatchController(world, new MatchFormat(1, 25, 15, 2, RuleMode.OFFICIAL, 8f), seed, level, level);
            Map<Integer, ArrayDeque<String>> history = new HashMap<>();
            match.addListener(new MatchListener() {
                @Override
                public void onPoint(MatchRules.PointSummary point, MatchScore score) {
                    if (point.outcome().reason() != Fault.PLAYER_NET_TOUCH) return;
                    touches[0]++;
                    VolleyballPlayer p = world.player(point.outcome().playerId());
                    out.append("NET TOUCH ").append(p.role).append(" #").append(p.number).append(" play ")
                            .append(match.ai(p.side).playStatus()).append('\n');
                    for (String h : history.getOrDefault(p.id, new ArrayDeque<>())) out.append("    ").append(h).append('\n');
                }
            });
            match.start(CourtSide.HOME);
            int frame = 0;
            for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 25; i++) {
                world.step(1f / 120f);
                match.update(1f / 120f, List.of());
                if (++frame % 6 != 0) continue;
                for (VolleyballPlayer p : world.players()) {
                    if (!p.isOnCourt()) continue;
                    ArrayDeque<String> h = history.computeIfAbsent(p.id, k -> new ArrayDeque<>());
                    float sg = p.side.sign();
                    h.addLast(String.format("t%.2f d%.2f l%.1f vx(net)%.2f %s %s intent %.2f touches %d", world.time() % 1000,
                            p.position.x * sg, -p.position.z * sg, p.velocity.x * -sg, p.isAirborne() ? "AIR" : "ground",
                            match.ai(p.side).state(p), world.intent(p).direction.x * -sg, world.ballHandling().teamTouches(p.side)));
                    if (h.size() > 14) h.removeFirst();
                }
            }
        }
        out.insert(0, "net touches " + touches[0] + "\n");
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv().getOrDefault("PROBE_OUT", "net_probe.txt")), out.toString());
    }
}
