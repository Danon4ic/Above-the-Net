package com.volleyball.game.match;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.physics.ColliderKind;
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

/** Developer probe (PROBE=1): how many attacks go into the net after each kind of set. */
class PipeProbe {
    private static final MatchController[] matchRef = {null};
    private static final Map<String, Integer> statuses = new TreeMap<>();
    private static final Map<String, int[]> setNet = new TreeMap<>();
    private static final StringBuilder setDetail = new StringBuilder();
    private static final BallTouch[] lastTouch = {null};
    @Test
    void probe() {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        Difficulty level = Difficulty.parse(System.getenv().getOrDefault("PROBE_LEVEL", "pro"));
        Map<String, int[]> bySet = new TreeMap<>();
        StringBuilder detail = new StringBuilder();
        for (long seed = 1; seed <= Integer.parseInt(System.getenv().getOrDefault("PROBE_SEEDS", "8")); seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
            MatchController match = new MatchController(world, new MatchFormat(1, 25, 15, 2, RuleMode.OFFICIAL, 8f), seed, level, level);
            matchRef[0] = match;
            String[] lastSet = {""};
            BallTouch[] attack = {null};
            String[] attackKey = {""};
            world.ballHandling().addListener(t -> {
                // Second touches (sets, dumps) and whether the net comes next.
                lastTouch[0] = t;
                if (t.teamTouchNumber() == 2) {
                    String kind = (t.type() == ContactType.ATTACK ? "dump " : "set ") + world.player(t.playerId()).role;
                    setNet.computeIfAbsent(kind, k -> new int[2])[0]++;
                }
                if (t.type() == ContactType.ATTACK) {
                    attack[0] = t;
                    attackKey[0] = lastSet[0].isEmpty() ? "(no set)" : lastSet[0];
                    bySet.computeIfAbsent(attackKey[0], k -> new int[2])[0]++;
                } else {
                    attack[0] = null;
                }
                lastSet[0] = t.teamTouchNumber() == 2 && t.type() != ContactType.ATTACK ? t.variant() : "";
                if (t.teamTouchNumber() == 2 && t.type() != ContactType.ATTACK) {
                    String st = matchRef[0].ai(t.side()).playStatus().replaceAll("[(][^)]*[)]", "");
                    statuses.merge(st, 1, Integer::sum);
                }
            });
            world.addContactListener(c -> {
                BallTouch lt = lastTouch[0];
                if (lt != null && lt.teamTouchNumber() == 2 && (c.kind() == ColliderKind.NET_MESH || c.kind() == ColliderKind.NET_BAND)) {
                    String kind = (lt.type() == ContactType.ATTACK ? "dump " : "set ") + world.player(lt.playerId()).role;
                    setNet.get(kind)[1]++;
                    if (setDetail.length() < 3000) setDetail.append(String.format("  %s %s q %.2f contact (%.2f %.2f %.2f) v(%.1f %.1f %.1f) net y %.2f%n",
                            kind, lt.variant(), lt.quality(), lt.contactPoint().x, lt.contactPoint().y, lt.contactPoint().z,
                            lt.outgoingVelocity().x, lt.outgoingVelocity().y, lt.outgoingVelocity().z, c.position().y));
                    lastTouch[0] = null;
                }
                BallTouch a = attack[0];
                if (a == null) return;
                if ((c.kind() == ColliderKind.NET_MESH || c.kind() == ColliderKind.NET_BAND) && a.side().sign() * c.position().x < 0.3f) {
                    bySet.get(attackKey[0])[1]++;
                    String k = attackKey[0];
                    if (k.contains("Pipe") || k.contains("Bic") || k.contains("D 1") || k.contains("zone 1") || k.contains("zone 6")) {
                        detail.append(String.format("%s [%s]: contact x %.2f y %.2f q %.2f air %s v(%.1f %.1f %.1f) net y %.2f%n", k,
                                a.variant(), a.contactPoint().x, a.contactPoint().y, a.quality(), a.airborne(), a.outgoingVelocity().x,
                                a.outgoingVelocity().y, a.outgoingVelocity().z, c.position().y));
                    }
                    attack[0] = null;
                }
            });
            if (System.getenv("PROBE_CMD") != null) {
                com.volleyball.game.tactics.TeamCommand cmd = com.volleyball.game.tactics.TeamCommand.valueOf(System.getenv("PROBE_CMD"));
                match.ai(CourtSide.HOME).setCoaching(false);
                match.ai(CourtSide.AWAY).setCoaching(false);
                match.ai(CourtSide.HOME).tactics().set(cmd);
                match.ai(CourtSide.AWAY).tactics().set(cmd);
            }
            match.start(CourtSide.HOME);
            for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 25; i++) {
                world.step(1f / 120f);
                match.update(1f / 120f, List.of());
            }
        }
        StringBuilder out = new StringBuilder("attacks / into the net, by the set before:\n");
        bySet.forEach((k, v) -> out.append(String.format("  %-34s %4d  net %3d (%.0f%%)%n", k, v[0], v[1],
                v[0] == 0 ? 0f : 100f * v[1] / v[0])));
        setNet.forEach((k, v) -> out.append(String.format("  %-28s %4d  net %3d%n", k, v[0], v[1])));
        out.append(setDetail);
        statuses.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(25)
                .forEach(e -> out.append(String.format("  [%3d] %s%n", e.getValue(), e.getKey())));
        System.out.println(out + detail.toString());
    }
}
