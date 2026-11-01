package com.volleyball.game.match;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
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

/**
 * Developer probe (PROBE=1): after a serve reception, does the setter make the second touch? For
 * the rallies where the received ball drops untouched, prints the setter's trace (PROBE_OUT file).
 */
class SetterProbe {
    @Test
    void probe() throws Exception {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        Difficulty level = Difficulty.parse(System.getenv().getOrDefault("PROBE_LEVEL", "pro"));
        int seeds = Integer.parseInt(System.getenv().getOrDefault("PROBE_SEEDS", "6"));
        StringBuilder out = new StringBuilder();
        int[] receptions = {0};
        int[] setterSecond = {0};
        int[] dropped = {0};
        for (long seed = 1; seed <= seeds; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
            MatchController match = new MatchController(world, new MatchFormat(1, 25, 15, 2, RuleMode.OFFICIAL, 8f), seed, level, level);
            BallTouch[] last = {null};
            BallTouch[] reception = {null};
            StringBuilder trace = new StringBuilder();
            world.ballHandling().addListener(t -> {
                if (t.type() == ContactType.SERVE) {
                    trace.setLength(0);
                    reception[0] = null;
                } else if (last[0] != null && last[0].type() == ContactType.SERVE && t.side() != last[0].side()) {
                    reception[0] = t;
                    receptions[0]++;
                    trace.append(String.format("    reception %s by %s q%.2f %s%n", t.rating(), world.player(t.playerId()).role, t.quality(),
                            t.variant()));
                } else if (reception[0] != null && t.teamTouchNumber() == 2 && t.side() == reception[0].side()) {
                    if (world.player(t.playerId()).role == com.volleyball.game.entities.PlayerRole.SETTER) setterSecond[0]++;
                    reception[0] = null;
                }
                last[0] = t;
            });
            match.addListener(new MatchListener() {
                @Override
                public void onPoint(MatchRules.PointSummary point, MatchScore score) {
                    if (reception[0] != null && last[0] == reception[0]) {
                        dropped[0]++;
                        if (dropped[0] <= 8) out.append("DROPPED ").append(point.outcome().reason()).append('\n').append(trace);
                    }
                    reception[0] = null;
                }
            });
            match.start(CourtSide.HOME);
            float clock = 0f;
            com.volleyball.game.physics.TrajectoryPredictor.Prediction pred = new com.volleyball.game.physics.TrajectoryPredictor.Prediction();
            for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 25; i++) {
                world.step(1f / 120f);
                match.update(1f / 120f, List.of());
                clock += 1f / 120f;
                if (clock >= 0.1f && last[0] != null && world.ball().isActive()) {
                    clock = 0f;
                    CourtSide recv = last[0].type() == ContactType.SERVE ? last[0].side().opposite() : reception[0] != null ? reception[0].side() : null;
                    if (recv == null) continue;
                    VolleyballPlayer st = match.ai(recv).activeSetter(match.rules().rotation());
                    if (st == null) continue;
                    float sg = recv.sign();
                    pred.watchHeight = 1.0f;
                    world.predictor().predict(world.ball(), 6f, pred);
                    trace.append(String.format("      forecast reaches %s descent(d%.2f l%.1f) in %.2fs net(d%.2f h%.2f)%n", pred.reachesWatchHeight,
                            pred.descentPoint.x * sg, -pred.descentPoint.z * sg, pred.timeToDescent, pred.netCrossing.x * sg, pred.netCrossing.y));
                    trace.append(String.format("    t%.1f setter(d%.1f l%.1f) %s v%.1f resp %s | ball(d%.1f l%.1f h%.1f) touches %d%n",
                            world.time() % 1000, st.position.x * sg, -st.position.z * sg, match.ai(recv).state(st), st.groundSpeed(),
                            match.ai(recv).responsibleId() == st.id ? "SETTER" : String.valueOf(match.ai(recv).responsibleId()),
                            world.ball().position.x * sg, -world.ball().position.z * sg, world.ball().position.y,
                            world.ballHandling().teamTouches(recv)));
                }
            }
        }
        out.insert(0, String.format("receptions %d, setter 2nd touch %d, dropped after reception %d%n", receptions[0], setterSecond[0], dropped[0]));
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv().getOrDefault("PROBE_OUT", "setter_probe.txt")), out.toString());
    }
}
