package com.volleyball.game.match;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.rules.Fault;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
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
 * Developer statistics of computer matches (not a regular test): run with the environment
 * variable AI_STATS=1, e.g. {@code AI_STATS=1 gradle test --tests '*AiStatsRun*'}. Prints, per
 * pairing of difficulties: points, rally length, attack shots, what happened after block touches
 * (covered / dug / dropped) and setter dumps (point / error / in play).
 */
@EnabledIfEnvironmentVariable(named = "AI_STATS", matches = "1")
class AiStatsRun {
    /** Where the active setter is and what it does (court frame of its team). */
    private static String setterInfo(MatchController match, GameWorld world, CourtSide side) {
        VolleyballPlayer st = match.ai(side).activeSetter(match.rules().rotation());
        if (st == null) return " | setter -";
        return String.format(" | setter at(d%.1f l%.1f) y%.2f %s v%.1f", st.position.x * side.sign(), -st.position.z * side.sign(),
                st.position.y, match.ai(side).state(st), st.groundSpeed());
    }

    private static final int SEEDS = Integer.parseInt(System.getenv().getOrDefault("AI_STATS_SEEDS", "6"));

    @Test
    void printStatistics() {
        StringBuilder out = new StringBuilder();
        // AI_STATS_PAIRS: comma-separated pairs of difficulty initials, home first (default PP,PI,IB).
        for (String pair : System.getenv().getOrDefault("AI_STATS_PAIRS", "PP,PI,IB").split(",")) {
            run(out, initial(pair.charAt(0)), initial(pair.charAt(1)));
        }
        System.out.println(out);
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv().getOrDefault("AI_STATS_OUT", "ai_stats.txt")),
                    out.toString());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Difficulty initial(char c) {
        return switch (Character.toUpperCase(c)) {
            case 'B' -> Difficulty.BEGINNER;
            case 'I' -> Difficulty.INTERMEDIATE;
            default -> Difficulty.PROFESSIONAL;
        };
    }

    private static final class Stats {
        int homePoints, awayPoints, rallies, touches, attacks;
        final Map<String, Integer> shots = new TreeMap<>();
        final Map<String, Integer> sets = new TreeMap<>();
        final Map<String, Integer> endings = new TreeMap<>();
        // After a block touch: the attacking team plays it again / the blocking team plays it / it drops.
        int blockTouches, blockCovered, blockDug, blockDroppedAttackerSide, blockDroppedBlockerSide, blockOut;
        int dumps, dumpPoints, dumpErrors, dumpInPlay;
        final Map<String, Integer> dumpErrorKinds = new TreeMap<>();
        int attackKills, attackErrors, aces, serveErrors;
        final Map<String, Integer> aceSpots = new TreeMap<>();
        final Map<String, Integer> aceNearest = new TreeMap<>();
        final Map<String, Integer> aceReach = new TreeMap<>();
        final Map<String, Integer> bodyHits = new TreeMap<>();
        final Map<String, Integer> killKinds = new TreeMap<>();
        final Map<String, int[]> shotOutcomes = new TreeMap<>();
        final Map<String, int[]> setOutcomes = new TreeMap<>();
        final Map<String, int[]> playOutcomes = new TreeMap<>();
        final Map<String, Integer> playEvents = new TreeMap<>();
        final java.util.List<String> setFailures = new java.util.ArrayList<>();
        final Map<String, Integer> homeWays = new TreeMap<>();
        final Map<String, Integer> ownErrors = new TreeMap<>();
        final java.util.List<String> shanks = new java.util.ArrayList<>();
        final Map<String, Integer> awayWays = new TreeMap<>();
        final java.util.List<String> killDetails = new java.util.ArrayList<>();
        final java.util.List<String> bodyHitDetails = new java.util.ArrayList<>();
        final java.util.List<String> aceDetails = new java.util.ArrayList<>();

        void add(Map<String, Integer> map, String key) {
            map.merge(key, 1, Integer::sum);
        }
    }

    private void run(StringBuilder out, Difficulty home, Difficulty away) {
        Stats st = new Stats();
        for (int seed = 1; seed <= SEEDS; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 7L);
            MatchFormat format = new MatchFormat(1, 25, 15, 2, RuleMode.OFFICIAL, 8f);
            MatchController match = new MatchController(world, format, seed, home, away);
            // Pending: the last block touch (its blocked attacker's side) and the last dump.
            final BallTouch[] block = {null};
            final BallTouch[] dump = {null};
            final BallTouch[] attack = {null};
            final BallTouch[] last = {null};
            final BallTouch[] prev = {null};
            final String[] setOfAttack = {null};
            final String[] playOfAttack = {null};
            final String[] lastSetVariant = {null};
            // Whether a receiver armed a contact while the served ball came (and the closest the ball got to them).
            final boolean[] armed = {false};
            // Served ball: the smallest contact offset any receiver had, and whether they were armed at that moment.
            final float[] minOffset = {Float.MAX_VALUE};
            final boolean[] armedAtMin = {false};
            final float[] minHeight = {0f};
            final String[] whoAtMin = {""};
            final float[] respAtServe = {0f, 0f};
            final int[] respId = {-1};
            final double[] serveTime = {0};
            final boolean[] bodyLogged = {false};
            final com.badlogic.gdx.math.Vector3 tmpV = new com.badlogic.gdx.math.Vector3();
            world.ballHandling().addListener(t -> {
                st.touches++;
                if (block[0] != null && t.type() != ContactType.BLOCK) {
                    if (t.side() == block[0].side()) st.blockDug++;
                    else st.blockCovered++;
                    block[0] = null;
                }
                if (dump[0] != null && t.side() != dump[0].side()) {
                    st.dumpInPlay++;
                    dump[0] = null;
                }
                if (attack[0] != null && t.type() != ContactType.BLOCK) {
                    st.shotOutcomes.computeIfAbsent(attack[0].variant(), k -> new int[3])[2]++;
                    st.setOutcomes.computeIfAbsent(setOfAttack[0], k -> new int[3])[2]++;
                    st.playOutcomes.computeIfAbsent(playOfAttack[0], k -> new int[3])[2]++;
                    attack[0] = null;
                }
                if (t.type() == ContactType.BLOCK) {
                    st.blockTouches++;
                    block[0] = t;
                }
                if (t.type() == ContactType.SERVE) {
                    serveTime[0] = t.time();
                    bodyLogged[0] = false;
                    respId[0] = -1;
                    armed[0] = false;
                    minOffset[0] = Float.MAX_VALUE;
                }
                if (t.teamTouchNumber() == 2 && t.variant() != null && !t.variant().isEmpty()) lastSetVariant[0] = t.variant();
                if (t.type() == ContactType.ATTACK) {
                    com.volleyball.game.tactics.Combination play = match.ai(t.side()).currentPlay();
                    String status = match.ai(t.side()).playStatus();
                    playOfAttack[0] = play == null ? "no play" : play.name();
                    if (status.contains("cancelled")) st.add(st.playEvents, "cancelled");
                    else if (status.contains("emergency")) st.add(st.playEvents, "emergency at the set");
                    else if (status.contains("developed")) st.add(st.playEvents, "developed");
                    else st.add(st.playEvents, "as planned");
                    setOfAttack[0] = (t.side() == CourtSide.HOME ? "H " : "A ") + (last[0] != null && last[0].side() == t.side() && last[0].teamTouchNumber() == 2 ? last[0].variant() : "no set");
                    st.attacks++;
                    st.add(st.shots, t.variant());
                    attack[0] = t;
                    VolleyballPlayer p = world.player(t.playerId());
                    if (p != null && p.role == PlayerRole.SETTER && t.teamTouchNumber() == 2) {
                        st.dumps++;
                        dump[0] = t;
                    }
                }
                if (t.type() == ContactType.OVERHEAD_PASS && t.teamTouchNumber() == 2 && t.variant() != null && !t.variant().isEmpty()) {
                    st.add(st.sets, t.variant());
                }
                prev[0] = last[0];
                last[0] = t;
            });
            world.addContactListener(c -> {
                if (c.kind() != com.volleyball.game.physics.ColliderKind.PLAYER_BODY || last[0] == null) return;
                VolleyballPlayer p = world.player(c.playerId());
                if (p == null) return;
                String after = last[0].type() == ContactType.SERVE ? "serve" : last[0].type() == ContactType.ATTACK ? "attack"
                        : last[0].type() == ContactType.BLOCK ? "block" : "other";
                float off = com.volleyball.game.mechanics.ContactZone.normalizedOffset(p, ContactType.FOREARM_PASS, null, c.position(), tmpV);
                int resp = match.ai(p.side).responsibleId();
                if (after.equals("serve") && p.side != last[0].side() && st.bodyHitDetails.size() < 30 && !bodyLogged[0]) {
                    bodyLogged[0] = true;
                    float rx = c.position().x - p.position.x;
                    float rz = c.position().z - p.position.z;
                    float forward = rx * p.facingX() + rz * p.facingZ();
                    float right = rx * -p.facingZ() + rz * p.facingX();
                    st.bodyHitDetails.add(String.format("%s fwd %.2f right %.2f h %.2f speed(ground) %.1f crouch %s ballv %.1f vy %.1f",
                            p.role, forward, right, c.position().y - p.position.y, p.groundSpeed(), p.isCrouching(),
                            world.ball().velocity.len(), world.ball().velocity.y));
                }
                st.add(st.bodyHits, after + (p.side == last[0].side() ? " own-team" : "") + (p.id == resp ? " responsible" : " other")
                        + (world.ballHandling().isArmed(p) ? " armed" : " not-armed") + (off <= 1f ? " in-reach" : " out-of-reach"));
            });
            match.addListener(new MatchListener() {
                @Override
                public void onPoint(MatchRules.PointSummary point, MatchScore score) {
                    st.rallies++;
                    if (point.outcome().isReplay()) return;
                    CourtSide winner = point.outcome().winner();
                    if (winner == CourtSide.HOME) st.homePoints++;
                    else st.awayPoints++;
                    Fault reason = point.outcome().reason();
                    if (last[0] != null && last[0].type() == ContactType.OVERHEAD_PASS && last[0].side() != winner
                            && last[0].teamTouchNumber() == 2 && st.setFailures.size() < 25) {
                        CourtSide team = last[0].side();
                        int a = match.ai(team).attackerId();
                        VolleyballPlayer ap = a < 0 ? null : world.player(a);
                        int r = match.ai(team).responsibleId();
                        VolleyballPlayer rp = r < 0 ? null : world.player(r);
                        VolleyballPlayer faulty = point.outcome().playerId() >= 0 ? world.player(point.outcome().playerId()) : null;
                        st.setFailures.add(String.format("%s %s by %s land(d%.1f l%.1f) attacker %s at(d%.1f l%.1f) %s | resp %s %s | %s",
                                last[0].variant(), reason, faulty == null ? "-" : faulty.role + (faulty.isAirborne() ? " air" : " ground") + String.format(" v%.1f", faulty.velocity.x * -team.sign()), world.ball().position.x * team.sign(), -world.ball().position.z * team.sign(),
                                ap == null ? "-" : ap.role, ap == null ? 0 : ap.position.x * team.sign(), ap == null ? 0 : -ap.position.z * team.sign(),
                                ap == null ? "" : match.ai(team).state(ap), rp == null ? "-" : rp.role, rp == null ? "" : match.ai(team).state(rp),
                                match.ai(team).playStatus()));
                    }
                    String way;
                    if (last[0] == null) way = "other";
                    else {
                        boolean own = last[0].side() == winner;
                        way = switch (last[0].type()) {
                            case SERVE -> own ? "ace" : "opp serve error";
                            case ATTACK -> own ? "attack kill" : "opp attack error";
                            case BLOCK -> own ? "block point" : "opp block error";
                            default -> own ? "own " + last[0].type() : "opp " + last[0].type() + " error";
                        };
                    }
                    st.add(winner == CourtSide.HOME ? st.homeWays : st.awayWays, way);
                    // A lost ball after the team's own touch other than serve or attack: what came before it.
                    if (last[0] != null && last[0].side() != winner && last[0].type() != ContactType.SERVE
                            && last[0].type() != ContactType.ATTACK && last[0].type() != ContactType.BLOCK) {
                        String before = prev[0] == null ? "none" : (prev[0].side() == last[0].side() ? "own " : "opp ") + prev[0].type();
                        st.add(st.ownErrors, last[0].type() + " touch" + last[0].teamTouchNumber() + " " + reason + " after " + before);
                        if (prev[0] != null && prev[0].type() == ContactType.SERVE && st.shanks.size() < 20) {
                            VolleyballPlayer rp = world.player(last[0].playerId());
                            com.badlogic.gdx.math.Vector3 ov = last[0].outgoingVelocity();
                            st.shanks.add(String.format("%s q%.2f miss%.2f %s rating %s serve %s %.1fm/s out(%.1f,%.1f,%.1f) land(d%.1f l%.1f) state %s",
                                    rp == null ? "-" : rp.role, last[0].quality(), last[0].missDistance(), last[0].airborne() ? "air" : "ground",
                                    last[0].rating(), prev[0].variant(), prev[0].outgoingVelocity().len(), ov.x * last[0].side().sign(), ov.y,
                                    -ov.z * last[0].side().sign(), world.ball().position.x * last[0].side().sign(),
                                    -world.ball().position.z * last[0].side().sign(), rp == null ? "" : match.ai(last[0].side()).state(rp))
                                    + setterInfo(match, world, last[0].side()));
                        }
                    }
                    st.add(st.endings, reason.name() + (last[0] == null ? "" : " after " + last[0].type()));
                    if (last[0] != null && last[0].type() == ContactType.SERVE) {
                        if (winner == last[0].side()) st.aces++;
                        else st.serveErrors++;
                    }
                    if (reason == Fault.BALL_IN && last[0] != null && last[0].type() == ContactType.SERVE && winner == last[0].side()) {
                        // Where the ace landed, in the receiving team's frame: depth band and lateral band.
                        CourtSide receiver = winner.opposite();
                        float depth = world.ball().position.x * receiver.sign();
                        float lateral = -world.ball().position.z * receiver.sign();
                        st.add(st.aceSpots, String.format("d%d l%+d", (int) Math.floor(depth / 3f) * 3, Math.round(lateral / 2.5f)));
                        float nearest = Float.MAX_VALUE;
                        String who = "";
                        for (VolleyballPlayer p : (receiver == CourtSide.HOME ? world.home() : world.away()).onCourt()) {
                            float dx = p.position.x - world.ball().position.x;
                            float dz = p.position.z - world.ball().position.z;
                            float d = (float) Math.sqrt(dx * dx + dz * dz);
                            if (d < nearest) {
                                nearest = d;
                                who = p.role.name() + (p.isUpright() ? "" : "(down)");
                            }
                        }
                        st.add(st.aceNearest, (nearest < 1f ? "<1m " : nearest < 2f ? "1-2m " : nearest < 3f ? "2-3m " : ">3m ") + who
                                + (armed[0] ? " armed" : " not-armed"));
                        String reach = minOffset[0] <= 1f ? (armedAtMin[0] ? "in-reach-armed" : "in-reach-NOT-armed")
                                : minOffset[0] < 1.5f ? "near-miss" : minOffset[0] < 2.5f ? "miss" : "far";
                        if (minOffset[0] <= 1f) st.add(st.aceReach, "  who: " + whoAtMin[0]);
                        if (st.aceDetails.size() < 25) {
                            VolleyballPlayer rp = respId[0] < 0 ? null : world.player(respId[0]);
                            float flight = (float) (world.time() - serveTime[0]);
                            st.aceDetails.add(String.format("%s %.1fm/s flight %.2fs land(d%.1f l%.1f) resp %s start(d%.1f l%.1f) end(d%.1f l%.1f) dist %.2f %s",
                                    last[0].variant(), last[0].outgoingVelocity().len(), flight, depth, lateral,
                                    rp == null ? "-" : rp.role, rp == null ? 0 : respAtServe[0] * receiver.sign(), rp == null ? 0 : -respAtServe[1] * receiver.sign(),
                                    rp == null ? 0 : rp.position.x * receiver.sign(), rp == null ? 0 : -rp.position.z * receiver.sign(),
                                    rp == null ? 0 : (float) Math.hypot(rp.position.x - world.ball().position.x, rp.position.z - world.ball().position.z),
                                    rp == null ? "" : (world.ballHandling().isArmed(rp) ? "armed" : "") + " " + match.ai(receiver).state(rp)));
                        }
                        st.add(st.aceReach, reach + (minOffset[0] <= 2.5f ? (minHeight[0] > 0.15f ? " ball-high" : minHeight[0] < -0.15f ? " ball-low" : " level") : ""));
                    }
                    if (block[0] != null) {
                        boolean landedIn = reason == Fault.BALL_IN;
                        if (!landedIn) st.blockOut++;
                        // The block's team wins: the ball dropped on the attacker's side (or went out off the attacker).
                        else if (winner == block[0].side()) st.blockDroppedAttackerSide++;
                        else st.blockDroppedBlockerSide++;
                        block[0] = null;
                    }
                    if (dump[0] != null) {
                        if (winner == dump[0].side()) st.dumpPoints++;
                        else {
                            st.dumpErrors++;
                            st.add(st.dumpErrorKinds, reason.name());
                        }
                        dump[0] = null;
                    }
                    if (attack[0] != null) {
                        if (winner == attack[0].side()) {
                            CourtSide defender = attack[0].side().opposite();
                            float depth = world.ball().position.x * defender.sign();
                            float nearest = Float.MAX_VALUE;
                            for (VolleyballPlayer p : (defender == CourtSide.HOME ? world.home() : world.away()).onCourt()) {
                                nearest = Math.min(nearest, (float) Math.hypot(p.position.x - world.ball().position.x, p.position.z - world.ball().position.z));
                            }
                            st.add(st.killKinds, attack[0].variant() + " " + reason.name() + (reason == Fault.BALL_IN
                                    ? (depth < 3f ? " short" : depth < 6f ? " mid" : " deep") + (nearest < 1f ? " <1m" : nearest < 2f ? " 1-2m" : " >2m") : ""));
                        }
                        if (winner == attack[0].side() && reason == Fault.BALL_IN && st.killDetails.size() < 40) {
                            CourtSide defender = attack[0].side().opposite();
                            int r = match.ai(defender).responsibleId();
                            VolleyballPlayer rp = r < 0 ? null : world.player(r);
                            float flight = (float) (world.time() - attack[0].time());
                            st.killDetails.add(String.format("%s flight %.2fs land(d%.1f l%.1f) resp %s at(d%.1f l%.1f) dist %.2f %s%s%s",
                                    attack[0].variant(), flight, world.ball().position.x * defender.sign(), -world.ball().position.z * defender.sign(),
                                    rp == null ? "-" : rp.role, rp == null ? 0 : rp.position.x * defender.sign(), rp == null ? 0 : -rp.position.z * defender.sign(),
                                    rp == null ? 0 : (float) Math.hypot(rp.position.x - world.ball().position.x, rp.position.z - world.ball().position.z),
                                    rp == null ? "" : match.ai(defender).state(rp), rp != null && rp.isAirborne() ? " airborne" : "",
                                    rp != null && !rp.isUpright() ? " down" : ""));
                        }
                        st.shotOutcomes.computeIfAbsent(attack[0].variant(), k -> new int[3])[winner == attack[0].side() ? 0 : 1]++;
                        st.setOutcomes.computeIfAbsent(setOfAttack[0], k -> new int[3])[winner == attack[0].side() ? 0 : 1]++;
                        st.playOutcomes.computeIfAbsent(playOfAttack[0], k -> new int[3])[winner == attack[0].side() ? 0 : 1]++;
                        if (winner != attack[0].side()) st.add(st.killKinds, "ERR " + attack[0].variant() + " " + reason.name() + " after " + last[0].type());
                        if (winner == attack[0].side()) st.attackKills++;
                        else st.attackErrors++;
                        attack[0] = null;
                    }
                }
            });
            match.start(CourtSide.HOME);
            long steps = 0;
            while (match.phase() != MatchController.Phase.FINISHED && ++steps < 120L * 60 * 30) {
                world.step(1f / 120f);
                if (last[0] != null && last[0].type() == ContactType.SERVE) {
                    int r = match.ai(last[0].side().opposite()).responsibleId();
                    if (r >= 0 && respId[0] != r) {
                        VolleyballPlayer rp = world.player(r);
                        if (respId[0] < 0) {
                            respAtServe[0] = rp.position.x;
                            respAtServe[1] = rp.position.z;
                        }
                        respId[0] = r;
                    }
                    for (VolleyballPlayer p : world.players()) {
                        if (p.side != last[0].side() && world.ballHandling().isArmed(p)) armed[0] = true;
                        if (p.side == last[0].side() || !p.isOnCourt()) continue;
                        for (ContactType ct : new ContactType[]{ContactType.FOREARM_PASS, ContactType.ONE_ARM, ContactType.OVERHEAD_PASS}) {
                            float off = com.volleyball.game.mechanics.ContactZone.normalizedOffset(p, ct, null, world.ball().position, tmpV);
                            if (off < minOffset[0]) {
                                minOffset[0] = off;
                                armedAtMin[0] = world.ballHandling().isArmed(p);
                                int resp = match.ai(p.side).responsibleId();
                                whoAtMin[0] = (p.id == resp ? "responsible " : resp < 0 ? "no-one-responsible " : "other ") + p.role;
                                com.volleyball.game.mechanics.ContactZone.idealPoint(p, ct, null, tmpV);
                                minHeight[0] = world.ball().position.y - tmpV.y;
                            }
                        }
                    }
                }
                match.update(1f / 120f, List.of());
            }
        }
        out.append(String.format("%s vs %s (%d sets): points %d:%d  rallies %d  touches/rally %.2f  attacks/rally %.2f%n",
                home, away, SEEDS, st.homePoints, st.awayPoints, st.rallies, st.touches / (float) st.rallies,
                st.attacks / (float) st.rallies));
        out.append(String.format("  serves: aces %d (%.0f%%), serve errors %d (%.0f%%)%n", st.aces, 100f * st.aces / Math.max(1, st.rallies),
                st.serveErrors, 100f * st.serveErrors / Math.max(1, st.rallies)));
        out.append(String.format("  attacks %d: direct kills %d (%.0f%%), direct errors %d (%.0f%%)  shots %s%n", st.attacks,
                st.attackKills, 100f * st.attackKills / Math.max(1, st.attacks), st.attackErrors,
                100f * st.attackErrors / Math.max(1, st.attacks), st.shots));
        out.append("  home points ").append(st.homeWays).append('\n');
        out.append("  lost after own touch ").append(st.ownErrors).append('\n');
        for (String sh : st.shanks) out.append("    shank: ").append(sh).append('\n');
        out.append("  away points ").append(st.awayWays).append('\n');
        StringBuilder so = new StringBuilder();
        for (Map.Entry<String, int[]> e : st.shotOutcomes.entrySet()) {
            int n = e.getValue()[0] + e.getValue()[1] + e.getValue()[2];
            so.append(String.format(" %s: %d (kill %.0f%%, err %.0f%%)", e.getKey(), n, 100f * e.getValue()[0] / n, 100f * e.getValue()[1] / n));
        }
        out.append("  shot outcomes").append(so).append('\n');
        StringBuilder se = new StringBuilder();
        for (Map.Entry<String, int[]> e : st.setOutcomes.entrySet()) {
            int n = e.getValue()[0] + e.getValue()[1] + e.getValue()[2];
            se.append(String.format(" %s: %d (kill %.0f%%, err %.0f%%)", e.getKey(), n, 100f * e.getValue()[0] / n, 100f * e.getValue()[1] / n));
        }
        out.append("  attack by set").append(se).append('\n');
        StringBuilder pl = new StringBuilder();
        for (Map.Entry<String, int[]> e : st.playOutcomes.entrySet()) {
            int n = e.getValue()[0] + e.getValue()[1] + e.getValue()[2];
            pl.append(String.format(" %s: %d (kill %.0f%%, err %.0f%%)", e.getKey(), n, 100f * e.getValue()[0] / n, 100f * e.getValue()[1] / n));
        }
        out.append("  attack by play").append(pl).append('\n');
        out.append("  plays ").append(st.playEvents).append('\n');
        for (String f : st.setFailures) out.append("    set failed: ").append(f).append('\n');
        out.append("  kills ").append(st.killKinds).append('\n');
        for (String d : st.killDetails) out.append("    kill: ").append(d).append('\n');
        out.append("  sets ").append(st.sets).append('\n');
        out.append(String.format("  block touches %d: covered by attackers %d, dug by blockers %d, dropped on attacker side %d, "
                        + "dropped on blocker side %d, out/other %d%n", st.blockTouches, st.blockCovered, st.blockDug,
                st.blockDroppedAttackerSide, st.blockDroppedBlockerSide, st.blockOut));
        out.append(String.format("  setter dumps %d: points %d, errors %d %s, in play %d%n", st.dumps, st.dumpPoints, st.dumpErrors,
                st.dumpErrorKinds, st.dumpInPlay));
        out.append("  ace spots (depth band m, lateral band x2.5 m) ").append(st.aceSpots).append('\n');
        out.append("  ace nearest player ").append(st.aceNearest).append('\n');
        out.append("  ace contact reach ").append(st.aceReach).append('\n');
        out.append("  body hits ").append(st.bodyHits).append('\n');
        for (String d : st.bodyHitDetails) out.append("    body: ").append(d).append('\n');
        for (String d : st.aceDetails) out.append("    ace: ").append(d).append('\n');
        out.append("  endings ").append(st.endings).append("\n\n");
    }
}
