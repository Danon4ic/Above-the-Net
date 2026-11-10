package com.volleyball.game.match;

import com.volleyball.game.ai.AiState;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer statistics of how the teams spread over the court during rallies (not a regular
 * test): run with SPACING=1, e.g. {@code SPACING=1 gradle test --tests '*SpacingRun*'}. Prints how
 * long teammates stand within 1 m / 1.5 m of each other (by the states they are in), the mean
 * distance to the nearest teammate and how often three or more players bunch within 2.5 m.
 */
@EnabledIfEnvironmentVariable(named = "SPACING", matches = "1")
class SpacingRun {
    private static final int SEEDS = Integer.parseInt(System.getenv().getOrDefault("SPACING_SEEDS", "4"));

    @Test
    void printSpacing() {
        StringBuilder out = new StringBuilder();
        for (Difficulty d : Difficulty.values()) if (System.getenv().getOrDefault("SPACING_LEVELS", "BIP").indexOf(d.name().charAt(0)) >= 0) run(out, d);
        System.out.println(out);
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv().getOrDefault("SPACING_OUT", "spacing.txt")), out.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void run(StringBuilder out, Difficulty difficulty) {
        float dt = 1f / 120f;
        double rallyTime = 0;
        double close1 = 0;
        double close15 = 0;
        double bunch3 = 0;
        double nearestSum = 0;
        long nearestSamples = 0;
        Map<String, Double> byStates = new TreeMap<>();
        List<String> samples = new java.util.ArrayList<>();
        double[] lastSample = {-9};
        for (int seed = 1; seed <= SEEDS; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 7L);
            MatchFormat format = new MatchFormat(1, 25, 15, 2, RuleMode.OFFICIAL, 8f);
            MatchController match = new MatchController(world, format, seed, difficulty, difficulty);
            match.start(CourtSide.HOME);
            long steps = 0;
            while (match.phase() != MatchController.Phase.FINISHED && ++steps < 120L * 60 * 30) {
                world.step(dt);
                match.update(dt, List.of());
                if (match.phase() != MatchController.Phase.RALLY || !world.ball().isActive()
                        || world.ballHandling().servePhase() != com.volleyball.game.mechanics.BallHandling.ServePhase.NONE) continue;
                for (CourtSide side : CourtSide.values()) {
                    List<VolleyballPlayer> court = (side == CourtSide.HOME ? world.home() : world.away()).onCourt();
                    rallyTime += dt;
                    int ball = match.ai(side).responsibleId();
                    boolean any1 = false;
                    boolean any15 = false;
                    boolean any3 = false;
                    for (int i = 0; i < court.size(); i++) {
                        VolleyballPlayer a = court.get(i);
                        float nearest = Float.MAX_VALUE;
                        int within = 0;
                        for (int j = 0; j < court.size(); j++) {
                            if (i == j) continue;
                            VolleyballPlayer b = court.get(j);
                            float d = (float) Math.hypot(a.position.x - b.position.x, a.position.z - b.position.z);
                            nearest = Math.min(nearest, d);
                            if (d < 2.5f) within++;
                            if (j > i && d < 1.0f && a.id != ball && b.id != ball && !a.isAirborne() && !b.isAirborne()) {
                                any1 = true;
                                String k = key(match.ai(side).state(a), match.ai(side).state(b));
                                byStates.merge(k, (double) dt, Double::sum);
                                if (System.getenv("SPACING_DETAIL") != null && k.equals(System.getenv("SPACING_DETAIL"))) {
                                    var h = world.ballHandling();
                                    String ctx = String.format("  %s+%s touches%d %s ball%s", a.role, b.role,
                                            h.teamTouches(side), h.lastTouchSide() == side ? "own" : "opp",
                                            com.volleyball.game.world.CourtDimensions.sideOf(world.ball().position.x) == side ? "Here" : "There");
                                    byStates.merge(ctx, (double) dt, Double::sum);
                                    if (samples.size() < 40 && world.time() - lastSample[0] > 0.7) {
                                        lastSample[0] = world.time();
                                        samples.add(String.format("t%.1f %s(%d) d%.1f l%.1f v%.1f | %s(%d) d%.1f l%.1f v%.1f | ball d%.1f l%.1f h%.1f resp %s %s",
                                                world.time(), a.role, a.id, a.position.x * side.sign(), -a.position.z * side.sign(), a.groundSpeed(),
                                                b.role, b.id, b.position.x * side.sign(), -b.position.z * side.sign(), b.groundSpeed(),
                                                world.ball().position.x * side.sign(), -world.ball().position.z * side.sign(), world.ball().position.y,
                                                ball < 0 ? "-" : world.player(ball).role, ctx.trim()));
                                    }
                                }
                            }
                            if (j > i && d < 1.5f) any15 = true;
                        }
                        if (within >= 2) any3 = true;
                        nearestSum += nearest;
                        nearestSamples++;
                    }
                    if (any1) close1 += dt;
                    if (any15) close15 += dt;
                    if (any3) bunch3 += dt;
                }
            }
        }
        out.append(String.format("%s: team-rally time %.0fs  pair<1m (no ball player) %.1f%%  pair<1.5m %.1f%%  3+ within 2.5m %.1f%%  mean nearest %.2fm%n",
                difficulty, rallyTime, 100 * close1 / rallyTime, 100 * close15 / rallyTime, 100 * bunch3 / rallyTime,
                nearestSum / Math.max(1, nearestSamples)));
        byStates.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue())).limit(18)
                .forEach(e -> out.append(String.format("    %-34s %.1fs%n", e.getKey(), e.getValue())));
        for (String x : samples) out.append("      ").append(x).append(System.lineSeparator());
    }

    private static String key(AiState a, AiState b) {
        return a.compareTo(b) <= 0 ? a + "+" + b : b + "+" + a;
    }
}
