package com.volleyball.game.match;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Developer probe (PROBE=1): attacks into a court that has a libero on it. Where the ball came
 * (in the defending team's frame), who touched it first, and where the libero was.
 */
class DigProbe {
    static final Vector3 setterAt = new Vector3();
    static final boolean[] setterBack = {false};
    @Test
    void probe() {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        Difficulty level = Difficulty.parse(System.getenv().getOrDefault("PROBE_LEVEL", "pro"));
        Map<String, Integer> counts = new TreeMap<>();
        StringBuilder rows = new StringBuilder();
        for (long seed = 1; seed <= 6; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
            MatchController match = new MatchController(world, new MatchFormat(1, 25, 15, 2, RuleMode.OFFICIAL, 8f), seed, level, level);
            BallTouch[] attack = {null};
            Vector3 liberoAt = new Vector3();
            Vector3 setterAt = DigProbe.setterAt;
            boolean[] liberoOn = {false};
            world.ballHandling().addListener(t -> {
                BallTouch a = attack[0];
                if (a != null && t.side() != a.side() && t.type() != ContactType.BLOCK) {
                    record(world, a, t.contactPoint(), world.player(t.playerId()), liberoAt, liberoOn[0], counts, rows);
                    attack[0] = null;
                } else if (a != null && t.side() == a.side()) {
                    attack[0] = null;
                }
                if (t.type() == ContactType.ATTACK && t.purpose() != null) {
                    attack[0] = t;
                    VolleyballPlayer libero = (t.side() == CourtSide.HOME ? world.away() : world.home()).libero();
                    liberoOn[0] = libero != null && libero.isOnCourt();
                    if (liberoOn[0]) liberoAt.set(libero.position);
                    var team = t.side() == CourtSide.HOME ? world.away() : world.home();
                    var setter = team.players().stream().filter(x -> x.role == PlayerRole.SETTER).findFirst().orElse(null);
                    if (setter != null) setterAt.set(setter.position);
                    var rot = match.rules().rotation();
                    setterBack[0] = setter != null && !rot.isFrontRow(team.side, setter.id);
                }
            });
            world.addContactListener(c -> {
                BallTouch a = attack[0];
                if (a != null && c.kind() == ColliderKind.FLOOR) {
                    record(world, a, c.position(), null, liberoAt, liberoOn[0], counts, rows);
                    attack[0] = null;
                }
            });
            match.start(CourtSide.HOME);
            for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 25; i++) {
                world.step(1f / 120f);
                match.update(1f / 120f, List.of());
            }
        }
        StringBuilder out = new StringBuilder("attacks into a court with the libero on it:\n");
        counts.forEach((k, v) -> out.append(String.format("  %-46s %4d%n", k, v)));
        System.out.println(out.append(rows));
    }

    private static void record(GameWorld world, BallTouch attack, Vector3 at, VolleyballPlayer toucher, Vector3 liberoAt,
                               boolean liberoOn, Map<String, Integer> counts, StringBuilder rows) {
        CourtSide defending = attack.side().opposite();
        if (!liberoOn || CourtDimensions.sideOf(at.x) != defending || !CourtDimensions.isInsideCourt(at.x, at.z, 0.3f)) return;
        // Defending team's frame: depth from the net, lateral + = right facing the net.
        float depth = at.x * defending.sign();
        float lateral = -at.z * defending.sign();
        String zone = depth < 3f ? "front" : (lateral < -1.5f ? "left back (5)" : lateral > 1.5f ? "right back (1)" : "middle back (6)");
        float dLib = (float) Math.hypot(at.x - liberoAt.x, at.z - liberoAt.z);
        String who = toucher == null ? "KILL" : toucher.role.name();
        counts.merge(zone + "  " + who, 1, Integer::sum);
        counts.merge("libero distance " + (dLib < 1f ? "<1" : dLib < 2f ? "1-2" : dLib < 3f ? "2-3" : ">3") + "  " + (toucher != null && toucher.role == PlayerRole.LIBERO ? "LIBERO" : who.equals("KILL") ? "KILL" : "other"), 1, Integer::sum);
        if (zone.startsWith("right") && toucher == null && setterBack[0] && rows.length() < 4000) {
            rows.append(String.format("  zone 1 kill: ball (%.1f, %.1f) setter at (%.1f, %.1f)%n", depth, lateral,
                    setterAt.x * defending.sign(), -setterAt.z * defending.sign()));
        }
        if (rows.length() < 2500 && toucher == null && dLib < 2f) {
            float ld = liberoAt.x * defending.sign();
            float ll = -liberoAt.z * defending.sign();
            rows.append(String.format("  kill near the libero: ball (%.1f, %.1f) libero (%.1f, %.1f) %s speed %.0f%n", depth, lateral, ld, ll,
                    attack.variant(), attack.outgoingVelocity().len()));
        }
    }
}
