package com.volleyball.game.match;

import com.volleyball.game.ai.AutoPlayer;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.mechanics.TacticalTargets;
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
import java.util.Random;
import java.util.TreeMap;

/**
 * Developer probe (PROBE=1): the home setter is "a person" (driven by its own auto player, sets of
 * random types). What happens after each set: an attack, a pass over, the ball dropping.
 */
class HumanSetterProbe {
    @Test
    void probe() {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        Map<String, int[]> result = new TreeMap<>(); // attack, into the net, other touch, dropped
        StringBuilder detail = new StringBuilder();
        Random random = new Random(3);
        SetType[] types = SetType.values();
        for (long seed = 1; seed <= 12; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
            MatchController match = new MatchController(world, new MatchFormat(1, 25, 15, 2, RuleMode.OFFICIAL, 8f), seed,
                    Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
            VolleyballPlayer setter = world.home().players().stream().filter(p -> p.role == PlayerRole.SETTER).findFirst().orElseThrow();
            AutoPlayer person = new AutoPlayer(world, seed);
            BallHandling handling = world.ballHandling();
            String[] pending = {null};
            BallTouch[] setTouch = {null};
            world.ballHandling().addListener(t -> {
                if (pending[0] != null && t.side() == CourtSide.HOME && t.playerId() != setter.id) {
                    String what = t.type() == ContactType.ATTACK ? "attack" : "other";
                    result.computeIfAbsent(pending[0], k -> new int[4])[what.equals("attack") ? 0 : 2]++;
                    if (!what.equals("attack")) detail.append(pending[0]).append(": ").append(t.type()).append(" by ")
                            .append(world.player(t.playerId()).role).append('\n');
                    if (what.equals("attack")) attackOf[0] = pending[0];
                    pending[0] = null;
                } else if (pending[0] != null) {
                    result.computeIfAbsent(pending[0], k -> new int[4])[2]++;
                    detail.append(pending[0]).append(": ").append(t.type()).append(" by the other side / setter\n");
                    pending[0] = null;
                }
                if (t.side() == CourtSide.HOME && t.teamTouchNumber() == 2) {
                    second.merge(world.player(t.playerId()).role + " " + t.type(), 1, Integer::sum);
                }
                if (t.playerId() == setter.id && t.teamTouchNumber() == 2 && t.type() == ContactType.OVERHEAD_PASS) {
                    pending[0] = t.variant().isEmpty() ? "(?)" : t.variant();
                    setTouch[0] = t;
                }
                if (t.type() != ContactType.ATTACK) attackOf[0] = null;
            });
            world.addContactListener(c -> {
                if (attackOf[0] != null && (c.kind() == ColliderKind.NET_MESH || c.kind() == ColliderKind.NET_BAND)
                        && c.position().x < 0.3f) {
                    result.get(attackOf[0])[1]++;
                    attackOf[0] = null;
                }
                if (pending[0] != null && c.kind() == ColliderKind.FLOOR) {
                    result.computeIfAbsent(pending[0], k -> new int[4])[3]++;
                    BallTouch s = setTouch[0];
                    detail.append(String.format("%s: DROPPED at (%.1f, %.1f), set q %.2f%n", pending[0], c.position().x, c.position().z,
                            s == null ? 0f : s.quality()));
                    pending[0] = null;
                }
            });
            match.addListener(new MatchListener() {
                @Override
                public void onPoint(com.volleyball.game.rules.MatchRules.PointSummary point, com.volleyball.game.rules.MatchScore score) {
                    second.merge("END " + point.outcome().reason(), 1, Integer::sum);
                }
            });
            match.start(CourtSide.HOME);
            com.badlogic.gdx.math.Vector3 spot = new com.badlogic.gdx.math.Vector3();
            int lastPossessionTouches = -1;
            for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 25; i++) {
                double now = world.time();
                int touches = handling.teamTouches(CourtSide.HOME);
                boolean ours = handling.lastTouchSide() == CourtSide.HOME;
                if (touches == 1 && ours && lastPossessionTouches != 1) {
                    handling.selectVariant(setter, types[random.nextInt(types.length)].ordinal() + 1);
                }
                lastPossessionTouches = ours ? touches : -1;
                if (handling.server() == setter || (ours && touches == 1)) {
                    person.act(setter, ours && touches == 1 ? AutoPlayer.Behavior.SET : AutoPlayer.Behavior.PASS, now);
                } else if (!world.ball().isActive() || handling.servePhase() != BallHandling.ServePhase.NONE) {
                    // Before the serve: in the rotation spot (a person would stand there too).
                    com.badlogic.gdx.math.Vector2 base = com.volleyball.game.world.CourtZones.basePosition(
                            match.rules().rotation().zoneOf(CourtSide.HOME, setter.id), CourtSide.HOME, new com.badlogic.gdx.math.Vector2());
                    person.moveTo(setter, base.x, base.y);
                } else {
                    TacticalTargets.setterTarget(CourtSide.HOME, spot);
                    person.moveTo(setter, spot.x, spot.z);
                }
                world.step(1f / 120f);
                match.update(1f / 120f, List.of(setter));
            }
        }
        StringBuilder out = new StringBuilder("after the person's set: attack / attack into the net / other touch / dropped\n");
        result.forEach((k, v) -> out.append(String.format("  %-34s %3d %3d %3d %3d%n", k, v[0], v[1], v[2], v[3])));
        System.out.println(out + "second touches: " + second + "\n" + detail);
    }

    private static final String[] attackOf = {null};
    private static final Map<String, Integer> second = new TreeMap<>();
}
