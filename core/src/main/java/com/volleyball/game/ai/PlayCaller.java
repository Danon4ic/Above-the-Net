package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.mechanics.TacticalTargets;
import com.volleyball.game.tactics.Combination;
import com.volleyball.game.tactics.Playbook;
import com.volleyball.game.tactics.TeamCommand;
import com.volleyball.game.tactics.TeamTactics;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Calls and runs the team's attacking combinations ({@link Combination}), the way a setter and
 * the hitters do it:
 * <ol>
 *   <li>When the pass is played, a play is <b>called</b>: among the plays the team can run
 *       (difficulty), whose preconditions hold (the pass, the hitters in their lanes), weighted by
 *       the coach's commands, what has been scoring and some variety.</li>
 *   <li>While the pass travels, the hitters <b>run</b> it: each to the start of the approach,
 *       then (at their tempo) towards the hitting spot; decoys run and jump too.</li>
 *   <li>The play is <b>cancelled</b> if the setter cannot get to the ball properly: then the
 *       emergency high ball goes to the most convenient hitter.</li>
 *   <li>Just before the setter's contact the final option is <b>chosen</b>: the first one whose
 *       hitter is ready (upright, on time, not tangled with another runner), or — in plays that
 *       read the block — the one with the fewest blockers in front.</li>
 * </ol>
 * Nothing is perfect: a late hitter, a collision or a poor pass changes the play.
 */
final class PlayCaller {
    /** A hitter farther than this from the hitting spot when the set is decided is not on time. */
    static final float READY_DISTANCE = 3.2f;
    /** Two runners closer than this have run into each other. */
    static final float COLLISION_DISTANCE = 0.55f;
    /** A fast set (quick, shoot, slide, bic) is set from at most this far from the net (m). */
    static final float FAST_SET_MAX_DISTANCE = 2.3f;

    private final CourtSide side;
    private final Random random;
    private final Vector3 tmp = new Vector3();
    private final Map<String, float[]> results = new HashMap<>();

    private Combination play;
    private final EnumMap<SetType.Lane, VolleyballPlayer> runners = new EnumMap<>(SetType.Lane.class);
    private Combination.Option chosen;
    private boolean decided;
    private boolean cancelled;
    private String status = "";
    private String lastPlayId;
    /** The coach's offensive command when the play was called (null: none). */
    private TeamCommand command;
    /** Who sets this time (their traits colour the choice), or null. */
    private VolleyballPlayer setter;
    private String playBeforeLast;

    PlayCaller(CourtSide side, Random random) {
        this.side = side;
        this.random = random;
    }

    /** Forgets the current play (a new possession). */
    void reset() {
        play = null;
        runners.clear();
        chosen = null;
        decided = false;
        cancelled = false;
        status = "";
    }

    Combination play() {
        return play;
    }

    Combination.Option chosen() {
        return chosen;
    }

    boolean decided() {
        return decided;
    }

    /** The player who runs a lane of the current play, or null. */
    VolleyballPlayer runner(SetType.Lane lane) {
        return runners.get(lane);
    }

    Map<SetType.Lane, VolleyballPlayer> runners() {
        return runners;
    }

    /** Who sets the coming ball: a tempo setter calls fast plays, a high-ball setter high ones. */
    void setSetter(VolleyballPlayer setter) {
        this.setter = setter;
    }

    /** A line for the AI view: the play and what happened to it. */
    String status() {
        return status;
    }

    /** The hitter the current option goes to, or null. */
    VolleyballPlayer chosenHitter(VolleyballPlayer setter) {
        if (chosen == null) return null;
        return chosen.dump() ? setter : runners.get(chosen.hitter());
    }

    // ------------------------------------------------------------------ calling the play

    /**
     * Calls a play when the pass is made.
     *
     * @param lanes   the hitter in each lane (see {@link ShotAdvisor#hitters})
     * @param pass    the pass's rating (null: unknown, treated as good)
     * @param setterFront the setter is in the front row (may dump)
     */
    Combination call(Difficulty difficulty, Playbook book, TeamTactics tactics, Map<SetType.Lane, VolleyballPlayer> lanes,
                     PassRating pass, boolean setterFront, Collection<VolleyballPlayer> humans) {
        reset();
        PassRating rating = pass == null ? PassRating.GOOD : pass;
        Map<Combination, Float> weights = new HashMap<>();
        TeamCommand offense = tactics == null ? null : tactics.active(TeamCommand.Group.OFFENSE);
        command = offense;
        for (Combination c : book.all()) {
            if (c.emergency()) continue;
            // The coach's plays are tried off a pass one grade worse than they need.
            boolean commandedPlay = offense != null && commandBias(offense, c) > 1f;
            int allowed = c.minPass().ordinal() + (commandedPlay ? 1 : 0);
            if (rating.ordinal() > allowed) continue;
            if (c.isDump() && (!setterFront || rating.ordinal() > PassRating.GOOD.ordinal())) continue;
            if (!c.isDump() && !lanes.containsKey(c.options().get(0).hitter())) continue;
            float w = baseWeight(difficulty, c);
            if (offense != null) w *= commandBias(offense, c);
            if (w <= 0f) continue;
            // The human hitter's plays: it is their game.
            Combination.Option first = c.options().get(0);
            VolleyballPlayer hitter = first.dump() ? null : lanes.get(first.hitter());
            // (With a command the coach's plan comes first, the person's own plays a little less so.)
            if (hitter != null && humans.contains(hitter)) w *= offense != null ? 1.5f : 3f;
            w *= traitBias(first, hitter);
            if (difficulty.adapts) {
                w *= 0.5f + success(c.id()) * 1.5f;
                // No patterns: the same play again is less likely, three in a row almost never.
                if (c.id().equals(lastPlayId)) w *= c.id().equals(playBeforeLast) ? 0.1f : 0.5f;
            }
            weights.put(c, w);
        }
        Combination picked = pick(weights);
        if (picked == null) picked = Playbook.emergency();
        play = picked;
        playBeforeLast = lastPlayId;
        lastPlayId = picked.id();
        for (Combination.Run r : picked.runs()) {
            VolleyballPlayer p = lanes.get(r.lane());
            if (p != null) runners.put(r.lane(), p);
        }
        // The emergency play and the options' hitters without a run still need to be known.
        for (Combination.Option o : picked.options()) {
            if (!o.dump() && !runners.containsKey(o.hitter()) && lanes.containsKey(o.hitter())) runners.put(o.hitter(), lanes.get(o.hitter()));
        }
        chosen = firstAvailable(picked);
        status = "Play: " + picked.name() + (chosen == null ? "" : "  ->  " + chosen.label());
        return picked;
    }

    /**
     * A person sets {@code set} (chosen on the bar, or changed late): the team switches to the
     * simplest play that has this set, so its hitter runs the right route at the right tempo
     * (a quick or a slide needs the run before the set) instead of the play the team had called.
     * The set is then fixed: the person decides, not the team.
     */
    void followSet(SetType set, Playbook book, Map<SetType.Lane, VolleyballPlayer> lanes) {
        if (set == null || (chosen != null && !chosen.dump() && chosen.set() == set && decided)) return;
        Combination best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Combination c : book.all()) {
            if (c.emergency()) continue;
            for (int i = 0; i < c.options().size(); i++) {
                Combination.Option o = c.options().get(i);
                if (o.dump() || o.set() != set || !lanes.containsKey(o.hitter())) continue;
                // The set as the play's first option, few runners and no decoys: the plainest version.
                int score = i * 10 + c.runs().size() + (int) c.runs().stream().filter(Combination.Run::decoy).count() * 3;
                if (score < bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
        }
        if (best == null) return;
        play = best;
        runners.clear();
        for (Combination.Run r : best.runs()) {
            VolleyballPlayer p = lanes.get(r.lane());
            if (p != null) runners.put(r.lane(), p);
        }
        chosen = null;
        for (Combination.Option o : best.options()) {
            if (!o.dump() && lanes.containsKey(o.hitter()) && !runners.containsKey(o.hitter())) runners.put(o.hitter(), lanes.get(o.hitter()));
            if (chosen == null && !o.dump() && o.set() == set) chosen = o;
        }
        decided = true;
        cancelled = false;
        status = "Play (your set): " + best.name() + "  ->  " + chosen.label();
    }

    /** How often a team of this level calls a play (0 = never). */
    static float baseWeight(Difficulty difficulty, Combination c) {
        if (!c.builtIn()) return difficulty == Difficulty.PROFESSIONAL ? 0.3f : difficulty == Difficulty.INTERMEDIATE ? 0.15f : 0f;
        return switch (difficulty) {
            case BEGINNER -> switch (c.id()) {
                case Playbook.HIGH_OUTSIDE -> 1f;
                case Playbook.ZONE_TWO -> 0.3f;
                default -> 0f;
            };
            case INTERMEDIATE -> switch (c.id()) {
                case Playbook.HIGH_OUTSIDE -> 1f;
                case Playbook.ZONE_TWO -> 0.6f;
                case Playbook.FIRST_TEMPO -> 0.55f;
                case Playbook.SECOND_TEMPO -> 0.25f;
                case Playbook.QUICK_BACK -> 0.15f;
                case Playbook.PIPE, Playbook.DECOY -> 0.1f;
                case Playbook.SETTER_DUMP -> 0.05f;
                default -> 0f;
            };
            case PROFESSIONAL -> switch (c.id()) {
                case Playbook.HIGH_OUTSIDE -> 1f;
                case Playbook.ZONE_TWO -> 0.75f;
                case Playbook.FIRST_TEMPO -> 0.6f;
                case Playbook.SECOND_TEMPO, Playbook.QUICK_BACK -> 0.3f;
                case Playbook.DECOY, Playbook.QUICK_FAKE -> 0.25f;
                case Playbook.CROSS, Playbook.TANDEM, Playbook.PIPE -> 0.15f;
                case Playbook.BIC, Playbook.SETTER_DUMP, Playbook.SLIDE -> 0.1f;
                default -> 0f;
            };
        };
    }

    /** The coach's offensive command makes some plays much more (or less) likely. */
    static float commandBias(TeamCommand command, Combination c) {
        String id = c.id();
        return switch (command) {
            case QUICK_ATTACK -> switch (id) {
                case Playbook.FIRST_TEMPO -> 10f;
                case Playbook.SECOND_TEMPO, Playbook.SLIDE -> 5f;
                default -> 0.2f;
            };
            case HIGH_OUTSIDE -> id.equals(Playbook.HIGH_OUTSIDE) ? 12f : 0.15f;
            case ZONE_TWO -> id.equals(Playbook.ZONE_TWO) ? 10f : id.equals(Playbook.QUICK_BACK) ? 4f : 0.2f;
            case PIPE -> id.equals(Playbook.PIPE) ? 10f : id.equals(Playbook.BIC) ? 6f : 0.2f;
            case DECOY -> switch (id) {
                case Playbook.DECOY, Playbook.CROSS, Playbook.TANDEM, Playbook.QUICK_FAKE -> 8f;
                case Playbook.QUICK_BACK -> 5f;
                default -> 0.2f;
            };
            case VS_SINGLE_BLOCK -> c.readBlock() || c.runs().stream().anyMatch(Combination.Run::decoy) ? 2.5f : 1f;
            case CAUTIOUS -> c.minPass().ordinal() >= PassRating.LIMITED.ordinal() ? 2f : 0f;
            default -> 1f;
        };
    }

    // ------------------------------------------------------------------ while the pass travels

    /**
     * Cancels the play if the setter cannot get to the ball properly (judged from the chance to
     * reach its descent in time): the emergency high ball replaces it.
     */
    void checkSetter(float setterReach) {
        if (play == null || cancelled || decided || play.emergency()) return;
        if (setterReach < 0.35f) cancel("setter late");
    }

    /** Switches to the emergency play. */
    void cancel(String reason) {
        if (play == null) return;
        String was = play.name();
        cancelled = true;
        Map<SetType.Lane, VolleyballPlayer> keep = new EnumMap<>(runners);
        play = Playbook.emergency();
        runners.clear();
        runners.putAll(keep);
        chosen = firstAvailable(play);
        status = "Play: " + was + " cancelled (" + reason + ")  ->  " + (chosen == null ? "high ball" : chosen.label());
    }

    boolean cancelled() {
        return cancelled;
    }

    /**
     * The spot a runner should be heading for now: the start of the approach until the run's
     * tempo begins, then the hitting spot. Null if the player has no run.
     *
     * @param timeToSet seconds until the setter's contact (estimated)
     */
    Vector3 runTarget(VolleyballPlayer p, float timeToSet, Vector3 out) {
        if (play == null) return null;
        for (Map.Entry<SetType.Lane, VolleyballPlayer> e : runners.entrySet()) {
            if (e.getValue() != p) continue;
            Combination.Run run = play.runOf(e.getKey());
            if (run == null) return null;
            boolean started = -timeToSet >= run.tempo().startOffset;
            return started ? TacticalTargets.teamPoint(side, run.targetDepth(), run.targetLateral(), 0f, out)
                    : TacticalTargets.teamPoint(side, run.startDepth(), run.startLateral(), 0f, out);
        }
        return null;
    }

    /** True if the player runs a decoy in this play (or ran for an option that was not chosen). */
    boolean isDecoy(VolleyballPlayer p) {
        if (play == null) return false;
        for (Map.Entry<SetType.Lane, VolleyballPlayer> e : runners.entrySet()) {
            if (e.getValue() != p) continue;
            Combination.Run run = play.runOf(e.getKey());
            if (run == null) return false;
            if (run.decoy()) return true;
            return decided && chosen != null && (chosen.dump() || chosen.hitter() != e.getKey());
        }
        return false;
    }

    /** The tempo of a runner (null if none). */
    com.volleyball.game.tactics.Tempo tempoOf(VolleyballPlayer p) {
        if (play == null) return null;
        for (Map.Entry<SetType.Lane, VolleyballPlayer> e : runners.entrySet()) {
            if (e.getValue() != p) continue;
            Combination.Run run = play.runOf(e.getKey());
            return run == null ? null : run.tempo();
        }
        return null;
    }

    // ------------------------------------------------------------------ the set

    /**
     * Chooses the final option just before the set: the plan if its hitter is ready, otherwise
     * the next development; plays that read the block (and the "attack the single block"
     * command) prefer the option with the fewest blockers in front; no ready hitter at all means
     * the emergency high ball to the most convenient one.
     *
     * @param setterQuality 0..1: how well the setter is placed under the ball (fast sets need a good one)
     * @param readBlock     also read the block (command)
     * @param opponents     the opponent's players (blockers)
     */
    Combination.Option decide(VolleyballPlayer setter, float setterQuality, boolean readBlock, List<VolleyballPlayer> opponents) {
        if (play == null) return null;
        if (decided) return chosen;
        decided = true;
        List<Combination.Option> order = new ArrayList<>(play.options());
        if (play.readBlock() || readBlock) {
            // Reading the block: the plan's own option (and the one the coach asked for) stays the
            // choice unless clearly more blockers wait there; otherwise the freest side gets it.
            Map<Combination.Option, Float> score = new HashMap<>();
            for (int i = 0; i < order.size(); i++) {
                Combination.Option o = order.get(i);
                float s = blockersAt(o, opponents) - (i == 0 ? 0.6f : 0f) - (commanded(o) ? 2.5f : 0f);
                score.put(o, s);
            }
            order.sort((a, b) -> Float.compare(score.get(a), score.get(b)));
        }
        String firstWhy = null;
        for (Combination.Option o : order) {
            String why = unavailable(o, setter, setterQuality);
            if (why == null) {
                chosen = o;
                status = status + (o == play.options().get(0) ? "" : "  ->  developed: " + o.label() + " [" + firstWhy + "]");
                return o;
            }
            if (firstWhy == null) firstWhy = why;
        }
        // Nobody is ready for the plan: a high ball to whoever is most convenient.
        Combination.Option best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Combination.Option o : Playbook.emergency().options()) {
            VolleyballPlayer h = runners.get(o.hitter());
            if (h == null || !h.isUpright()) continue;
            o.set().target(side, tmp);
            float d = (float) Math.hypot(h.position.x - tmp.x, h.position.z - tmp.z);
            if (d < bestDistance) {
                bestDistance = d;
                best = o;
            }
        }
        chosen = best != null ? best : Playbook.emergency().options().get(0);
        status = status + "  ->  emergency: " + chosen.label();
        return chosen;
    }

    /** Whether the coach's offensive command asks for this kind of set. */
    private boolean commanded(Combination.Option o) {
        if (command == null || o.dump()) return false;
        return switch (command) {
            case QUICK_ATTACK -> o.set() == SetType.QUICK_MIDDLE || o.set() == SetType.SHORT_MIDDLE || o.set() == SetType.SLIDE;
            case HIGH_OUTSIDE -> o.set() == SetType.HIGH_OUTSIDE;
            case ZONE_TWO -> o.set() == SetType.BACK_SET || o.set() == SetType.BACK_ROW_RIGHT;
            case PIPE -> o.set() == SetType.PIPE || o.set() == SetType.BIC;
            default -> false;
        };
    }

    /** Why an option cannot be played now (null: it can). */
    private String unavailable(Combination.Option o, VolleyballPlayer setter, float setterQuality) {
        if (o.dump()) return setter.isAirborne() || setterQuality > 0.6f ? null : "setter not ready";
        VolleyballPlayer h = runners.get(o.hitter());
        if (h == null) return "no hitter";
        if (!h.isUpright()) return "hitter down";
        if (o.set().fast() && setterQuality < 0.55f) return "no fast set off this ball";
        // A quick from deep in the court runs low into the net: only from close to it.
        if (o.set().fast() && Math.abs(setter.position.x) > FAST_SET_MAX_DISTANCE) return "too far off the net for a fast set";
        o.set().target(side, tmp);
        if (Math.hypot(h.position.x - tmp.x, h.position.z - tmp.z) > READY_DISTANCE) return "hitter late";
        for (VolleyballPlayer other : runners.values()) {
            if (other == h || !other.isUpright()) continue;
            if (Math.hypot(other.position.x - h.position.x, other.position.z - h.position.z) < COLLISION_DISTANCE) return "runners collided";
        }
        return null;
    }

    private int blockersAt(Combination.Option o, List<VolleyballPlayer> opponents) {
        if (o.dump()) return 1;
        o.set().target(side, tmp);
        int n = 0;
        for (VolleyballPlayer b : opponents) {
            if (Math.abs(b.position.x) < 2.2f && Math.abs(b.position.z - tmp.z) < 1.8f) n++;
        }
        return n;
    }

    private Combination.Option firstAvailable(Combination c) {
        for (Combination.Option o : c.options()) {
            if (o.dump() || runners.containsKey(o.hitter())) return o;
        }
        return null;
    }

    // ------------------------------------------------------------------ learning

    /** The rally ended after an attack of this possession: remember whether the play scored. */
    void result(String playId, boolean won) {
        float[] r = results.computeIfAbsent(playId, k -> new float[]{1f, 2f});
        r[1] += 1f;
        if (won) r[0] += 1f;
    }

    /** How often a play was the last attack of a rally (without the prior). */
    int tries(String playId) {
        float[] r = results.get(playId);
        return r == null ? 0 : Math.round(r[1] - 2f);
    }

    /** How many of those rallies it won. */
    int points(String playId) {
        float[] r = results.get(playId);
        return r == null ? 0 : Math.round(r[0] - 1f);
    }

    /** Share of points won with a play (neutral prior: one of two). */
    float success(String playId) {
        float[] r = results.get(playId);
        return r == null ? 0.5f : r[0] / r[1];
    }

    /** The setter's and the hitter's traits: setters run their kind of ball and feed the cannons. */
    private float traitBias(Combination.Option first, VolleyballPlayer hitter) {
        float bias = 1f;
        if (setter != null && !first.dump()) {
            if (setter.has(PlayerTrait.TEMPO_SETTER)) bias *= first.set().fast() ? 1.7f : 0.8f;
            if (setter.has(PlayerTrait.HIGH_BALL_SETTER)) bias *= first.set().fast() ? 0.6f : 1.5f;
        }
        if (setter != null && first.dump() && setter.has(PlayerTrait.CRAFTY)) bias *= 2f;
        if (hitter != null && hitter.has(PlayerTrait.CANNON)) bias *= 1.3f;
        if (hitter != null && hitter.has(PlayerTrait.NERVOUS)) bias *= 0.85f;
        return bias;
    }

    private Combination pick(Map<Combination, Float> weights) {
        float total = 0f;
        for (float w : weights.values()) total += w;
        if (total <= 0f) return null;
        float r = random.nextFloat() * total;
        // Iterate in playbook order for determinism.
        List<Combination> order = new ArrayList<>(weights.keySet());
        order.sort((a, b) -> a.id().compareTo(b.id()));
        for (Combination c : order) {
            r -= weights.get(c);
            if (r <= 0f) return c;
        }
        return order.get(order.size() - 1);
    }
}
