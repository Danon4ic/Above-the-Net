package com.volleyball.game.match;

import com.badlogic.gdx.math.Vector2;
import com.volleyball.game.ai.CoachAI;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.rules.SubstitutionManager;
import com.volleyball.game.ai.TeamAI;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RallyManager;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.CourtZones;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Runs a match in the game world: places both teams in their rotation for each serve, gives
 * the ball to the server, lets the rally be played and refereed, applies the result to the
 * score and rotation, pauses, and goes on until the match is won.
 *
 * <p>Every player not controlled by a person is played by the team's {@link TeamAI}. Any number
 * of players can be human-controlled, on one team (co-op) or on both (versus); with none, the
 * computer plays the whole match.
 */
public final class MatchController {
    public enum Phase { PREPARE, RALLY, POINT_BREAK, TIMEOUT, FINISHED }

    public static final float POINT_BREAK = 2.5f;
    public static final float SET_BREAK = 4.5f;
    /** A timeout (30 s in the rules, shortened for the game; it can be skipped). */
    public static final float TIMEOUT_TIME = 12f;
    public static final int TIMEOUTS_PER_SET = 2;
    /** A computer coach calls a timeout when the opponent scores this many points in a row. */
    static final int RUN_FOR_TIMEOUT = 4;

    private final GameWorld world;
    private final MatchRules rules;
    private final TeamAI homeAI;
    private final TeamAI awayAI;
    private final List<MatchListener> listeners = new ArrayList<>();
    private final Vector2 spot = new Vector2();

    private Phase phase = Phase.FINISHED;
    private float timer;
    private MatchRules.PointSummary lastPoint;
    private int rallies;
    private boolean liberoEnabled = true;
    private final OffenseSystem system;
    private final CoachAI homeCoach;
    private final CoachAI awayCoach;
    /** Substitutions asked for during play; made before the next serve. */
    private final List<SubstitutionRequest> pending = new ArrayList<>();
    private Collection<VolleyballPlayer> lastHumans = List.of();
    private final MatchStatistics statistics;
    private final int[] timeoutsUsed = new int[2];
    /** A timeout asked for (taken at the next break), the team in a timeout now. */
    private CourtSide timeoutRequest;
    private CourtSide timeoutSide;
    /** The side that won the last points and how many in a row. */
    private CourtSide runSide;
    private int runLength;

    private record SubstitutionRequest(CourtSide side, int outId, int inId) {
    }

    public MatchController(GameWorld world, MatchFormat format, long seed) {
        this(world, format, seed, Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
    }

    public MatchController(GameWorld world, MatchFormat format, long seed, Difficulty home, Difficulty away) {
        this(world, format, seed, home, away, OffenseSystem.FIVE_ONE);
    }

    public MatchController(GameWorld world, MatchFormat format, long seed, Difficulty home, Difficulty away,
                           OffenseSystem system) {
        this.world = world;
        this.system = system;
        RotationManager rotation = new RotationManager(lineUp(world.home(), system), lineUp(world.away(), system));
        this.rules = new MatchRules(format, rotation, world.players(), world.court().net(), seed);
        RallyManager rally = rules.rally();
        world.ballHandling().addListener(rally);
        world.addContactListener(rally);
        // Independent streams for the two teams (adjacent java.util.Random seeds start out correlated).
        java.util.SplittableRandom seeds = new java.util.SplittableRandom(seed);
        homeAI = new TeamAI(world, world.home(), home, seeds.nextLong());
        awayAI = new TeamAI(world, world.away(), away, seeds.nextLong());
        world.ballHandling().addListener(homeAI);
        world.ballHandling().addListener(awayAI);
        homeAI.setSystem(system);
        awayAI.setSystem(system);
        homeCoach = new CoachAI(world.home());
        awayCoach = new CoachAI(world.away());
        statistics = new MatchStatistics(world.home().squad(), world.away().squad());
        world.ballHandling().addListener(statistics);
    }

    /** The match statistics so far. */
    public MatchStatistics statistics() {
        return statistics;
    }

    /**
     * Asks for a timeout for a team; it is taken at the next break in play.
     *
     * @return false if the team has none left this set (or one is already asked for)
     */
    public boolean requestTimeout(CourtSide side) {
        if (phase == Phase.FINISHED || phase == Phase.TIMEOUT || timeoutRequest != null) return false;
        if (timeoutsUsed[side.ordinal()] >= TIMEOUTS_PER_SET) return false;
        timeoutRequest = side;
        return true;
    }

    public int timeoutsLeft(CourtSide side) {
        return TIMEOUTS_PER_SET - timeoutsUsed[side.ordinal()];
    }

    /** The team in a timeout now, or null. */
    public CourtSide timeoutSide() {
        return phase == Phase.TIMEOUT ? timeoutSide : null;
    }

    /** True if a timeout has been asked for and waits for the break. */
    public boolean timeoutRequested() {
        return timeoutRequest != null;
    }

    /** Seconds left in the current timeout (or break). */
    public float timeLeft() {
        return timer;
    }

    /** Ends the current timeout now (the person does not want to wait). */
    public void skipTimeout() {
        if (phase == Phase.TIMEOUT) timer = 0f;
    }

    /**
     * Starting line-up in zones 1..6: the listed players (5-1); for 4-2 and 6-2 the second setter
     * from the bench takes the opposite's place, across from the first setter.
     */
    static int[] lineUp(Team team, OffenseSystem system) {
        List<VolleyballPlayer> players = team.players();
        int[] ids = new int[6];
        for (int i = 0; i < 6; i++) ids[i] = players.get(i).id;
        if (system.twoSetters()) {
            for (int i = 0; i < 6; i++) {
                if (players.get(i).role != PlayerRole.OPPOSITE) continue;
                for (VolleyballPlayer sub : team.substitutes()) {
                    if (sub.role == PlayerRole.SETTER) {
                        ids[i] = sub.id;
                        break;
                    }
                }
            }
        }
        return ids;
    }

    public OffenseSystem system() {
        return system;
    }

    public CoachAI coach(CourtSide side) {
        return side == CourtSide.HOME ? homeCoach : awayCoach;
    }

    /**
     * Asks for a substitution; it is made before the next serve (the ball must be out of play).
     *
     * @return the check of the rules now (OK means it will be made)
     */
    public SubstitutionManager.Result requestSubstitution(CourtSide side, VolleyballPlayer out, VolleyballPlayer in) {
        Team team = side == CourtSide.HOME ? world.home() : world.away();
        int liberoId = team.libero() == null ? -1 : team.libero().id;
        int queued = 0;
        for (SubstitutionRequest r : pending) if (r.side() == side) queued++;
        if (rules.substitutions().remaining(side) - queued <= 0) return SubstitutionManager.Result.LIMIT;
        for (SubstitutionRequest r : pending) {
            if (r.side() == side && (r.outId() == out.id || r.inId() == in.id)) return SubstitutionManager.Result.ALREADY_ON_COURT;
        }
        SubstitutionManager.Result result = rules.substitutions().check(side, out.id, in.id, rules.rotation(), liberoId);
        if (result == SubstitutionManager.Result.OK) pending.add(new SubstitutionRequest(side, out.id, in.id));
        return result;
    }

    /** Substitutions waiting for the next break in play. */
    public int pendingSubstitutions(CourtSide side) {
        int n = 0;
        for (SubstitutionRequest r : pending) if (r.side() == side) n++;
        return n;
    }

    private void makeSubstitutions() {
        for (SubstitutionRequest r : pending) {
            Team team = r.side() == CourtSide.HOME ? world.home() : world.away();
            int liberoId = team.libero() == null ? -1 : team.libero().id;
            if (rules.substitutions().substitute(r.side(), r.outId(), r.inId(), rules.rotation(), liberoId)
                    == SubstitutionManager.Result.OK) {
                VolleyballPlayer out = world.player(r.outId());
                VolleyballPlayer in = world.player(r.inId());
                for (MatchListener listener : listeners) listener.onSubstitution(r.side(), out, in, rules.substitutions().used(r.side()));
            }
        }
        pending.clear();
    }

    /** Unsubscribes the referee and the team AIs from the world (when the match is replaced). */
    public void detach() {
        world.ballHandling().removeListener(rules.rally());
        world.removeContactListener(rules.rally());
        world.ballHandling().removeListener(homeAI);
        world.ballHandling().removeListener(awayAI);
        world.ballHandling().removeListener(statistics);
    }

    public void addListener(MatchListener listener) {
        listeners.add(listener);
    }

    public void setDifficulty(CourtSide side, Difficulty difficulty) {
        ai(side).setDifficulty(difficulty);
    }

    public TeamAI ai(CourtSide side) {
        return side == CourtSide.HOME ? homeAI : awayAI;
    }

    public void start(CourtSide firstServer) {
        rules.startMatch(firstServer);
        world.ballHandling().setNextServer(world.player(rules.currentServer()));
        rallies = 0;
        lastPoint = null;
        pending.clear();
        timeoutsUsed[0] = 0;
        timeoutsUsed[1] = 0;
        timeoutRequest = null;
        runSide = null;
        runLength = 0;
        homeCoach.newSet();
        awayCoach.newSet();
        sidesSwapped = false;
        decidingSwapDone = false;
        world.setBenchSign(1f);
        phase = Phase.PREPARE;
    }

    /** True while the teams play on the ends opposite to where they started (see {@link #changeEnds}). */
    public boolean sidesSwapped() {
        return sidesSwapped;
    }

    /**
     * The teams change ends (rule 18.2): after every set but the one before the deciding set, and in
     * the deciding set when the leading team reaches 8 points (half of the set). The benches go with them.
     */
    private void changeEnds() {
        sidesSwapped = !sidesSwapped;
        world.setBenchSign(sidesSwapped ? -1f : 1f);
        for (MatchListener listener : listeners) listener.onChangeOfEnds(sidesSwapped);
    }

    private boolean sidesSwapped;
    private boolean decidingSwapDone;

    /** Advances the match flow with at most one human-controlled player (null = computer only). */
    public void update(float dt, VolleyballPlayer human) {
        update(dt, human == null ? List.of() : List.of(human));
    }

    /** Advances the match flow; call once per simulation step after the world step. */
    public void update(float dt, Collection<VolleyballPlayer> humans) {
        double now = world.time();
        lastHumans = humans;
        MatchScore score = rules.score();
        world.ballHandling().setPressure(score.isSetPoint(CourtSide.HOME) || score.isSetPoint(CourtSide.AWAY));
        switch (phase) {
            case PREPARE -> prepareServe(now);
            case RALLY -> {
                homeAI.update(now, rules.rotation(), rules.score(), humans);
                awayAI.update(now, rules.rotation(), rules.score(), humans);
                RallyManager rally = rules.rally();
                rally.update(world.ball(), now);
                if (rally.state() == RallyManager.State.OVER) finishRally(rally, now);
            }
            case POINT_BREAK -> {
                homeAI.betweenRallies(now, rules.rotation(), humans);
                awayAI.betweenRallies(now, rules.rotation(), humans);
                timer -= dt;
                if (timer <= 0f) {
                    if (timeoutRequest != null) startTimeout();
                    else phase = Phase.PREPARE;
                }
            }
            case TIMEOUT -> {
                // Both teams gather at their benches; the rest restores stamina.
                homeAI.huddle(rules.rotation(), humans);
                awayAI.huddle(rules.rotation(), humans);
                for (VolleyballPlayer p : world.players()) p.rest(0.08f * dt);
                timer -= dt;
                if (timer <= 0f) phase = Phase.PREPARE;
            }
            case FINISHED -> {
                homeAI.betweenRallies(now, rules.rotation(), humans);
                awayAI.betweenRallies(now, rules.rotation(), humans);
            }
        }
    }

    private void startTimeout() {
        timeoutSide = timeoutRequest;
        timeoutRequest = null;
        timeoutsUsed[timeoutSide.ordinal()]++;
        phase = Phase.TIMEOUT;
        timer = TIMEOUT_TIME;
        for (MatchListener listener : listeners) listener.onTimeout(timeoutSide, timeoutsUsed[timeoutSide.ordinal()]);
    }

    private void finishRally(RallyManager rally, double now) {
        lastPoint = rules.applyOutcome(rally.outcome());
        rallies++;
        statistics.onPoint(lastPoint);
        // A breather between rallies (more at the end of a set).
        for (VolleyballPlayer p : world.players()) p.rest(lastPoint.setWon() ? 0.8f : 0.3f);
        world.ballHandling().clearServe();
        world.ballHandling().setNextServer(lastPoint.matchWon() ? null : world.player(rules.currentServer()));
        CourtSide winner = lastPoint.outcome().winner();
        if (winner != null) {
            homeAI.rallyFinished(winner == CourtSide.HOME, now);
            awayAI.rallyFinished(winner == CourtSide.AWAY, now);
        }
        for (MatchListener listener : listeners) listener.onPoint(lastPoint, rules.score());
        updateRun(winner);
        if (lastPoint.setWon()) {
            // New set: line-ups, substitution counts and timeouts start again.
            pending.clear();
            timeoutsUsed[0] = 0;
            timeoutsUsed[1] = 0;
            timeoutRequest = null;
            runSide = null;
            runLength = 0;
            homeCoach.newSet();
            awayCoach.newSet();
            // The teams change ends after every set except before the deciding one (rule 18.2.1).
            MatchScore score = rules.score();
            boolean decidingNext = score.format().maxSets() > 1 && score.setNumber() == score.format().maxSets();
            if (!lastPoint.matchWon() && !decidingNext) changeEnds();
        } else if (!lastPoint.matchWon()) {
            MatchScore score = rules.score();
            MatchFormat format = score.format();
            boolean deciding = format.maxSets() > 1 && score.setNumber() == format.maxSets();
            int half = (format.pointsForSet(score.setNumber()) + 1) / 2;
            if (deciding && !decidingSwapDone && Math.max(score.points(CourtSide.HOME), score.points(CourtSide.AWAY)) >= half) {
                decidingSwapDone = true;
                changeEnds();
            }
            for (CoachAI coach : List.of(homeCoach, awayCoach)) {
                CoachAI.Request request = coach.afterRally(lastPoint.outcome(), rules.rotation(), rules.substitutions(), lastHumans);
                if (request != null) {
                    CourtSide side = request.out().side;
                    requestSubstitution(side, request.out(), request.in());
                }
            }
        }
        if (lastPoint.matchWon()) {
            phase = Phase.FINISHED;
        } else {
            phase = Phase.POINT_BREAK;
            timer = lastPoint.setWon() ? SET_BREAK : POINT_BREAK;
        }
    }

    /**
     * Points in a row, and the computer coach's timeout: a team without people on it calls one
     * when the opponent has scored {@link #RUN_FOR_TIMEOUT} in a row (from the middle of the set).
     */
    private void updateRun(CourtSide winner) {
        if (winner == null || lastPoint.setWon() || lastPoint.matchWon()) return;
        if (winner == runSide) runLength++;
        else {
            runSide = winner;
            runLength = 1;
        }
        CourtSide loser = winner.opposite();
        boolean peopleOnLoser = false;
        for (VolleyballPlayer h : lastHumans) peopleOnLoser |= h.side == loser;
        int score = rules.score().points(winner) + rules.score().points(loser);
        if (!peopleOnLoser && runLength >= RUN_FOR_TIMEOUT && score >= 8 && requestTimeout(loser)) runLength = 0;
    }

    /**
     * Libero replacements before each serve (rule 19.3.2): the libero comes in for the middle
     * blocker in the back row - except when that middle serves - and leaves when the middle
     * rotates to the front row. Players off the court wait at the bench.
     */
    private void updateLiberos() {
        RotationManager rotation = rules.rotation();
        for (Team team : List.of(world.home(), world.away())) {
            CourtSide side = team.side;
            rotation.releaseLibero(side);
            VolleyballPlayer libero = team.libero();
            if (libero != null && liberoEnabled) {
                for (int zone : new int[]{5, 6, 1}) {
                    VolleyballPlayer p = world.player(rotation.slotPlayer(side, zone));
                    boolean serves = zone == 1 && rules.servingSide() == side;
                    if (p != null && p.role == PlayerRole.MIDDLE_BLOCKER && !serves) {
                        rotation.replaceWithLibero(side, libero.id, p.id);
                        break;
                    }
                }
            }
            int bench = 0;
            for (VolleyballPlayer p : team.squad()) {
                boolean on = rotation.zoneOf(side, p.id) > 0;
                p.setOnCourt(on);
                if (!on) {
                    CourtZones.benchPosition(side, bench++, world.benchSign(), spot);
                    p.sitOnBench(spot.x, spot.y);
                    world.intent(p).clear();
                    world.ballHandling().cancel(p);
                }
            }
        }
    }

    /** Whether the teams use their libero (on by default). */
    public void setLiberoEnabled(boolean enabled) {
        liberoEnabled = enabled;
    }

    /** Everyone to their rotation zone, the server behind the end line with the ball. */
    private void prepareServe(double now) {
        BallHandling handling = world.ballHandling();
        RotationManager rotation = rules.rotation();
        makeSubstitutions();
        updateLiberos();
        for (VolleyballPlayer p : world.players()) {
            int zone = rotation.zoneOf(p.side, p.id);
            if (zone < 0) continue;
            CourtZones.basePosition(zone, p.side, spot);
            p.placeAt(spot.x, spot.y);
            world.intent(p).clear();
            handling.cancel(p);
        }
        CourtSide serving = rules.servingSide();
        VolleyballPlayer server = world.player(rules.currentServer());
        // Right half of the service zone, a metre behind the end line.
        server.placeAt(serving.sign() * (CourtDimensions.HALF_LENGTH + 1.0f), -serving.sign() * 2f);
        homeAI.newRally();
        awayAI.newRally();
        handling.prepareServe(server, handling.serveType(server) == null ? ServeType.FLOAT : handling.serveType(server));
        rules.startRally(now);
        phase = Phase.RALLY;
        for (MatchListener listener : listeners) listener.onServeWhistle(server, rules.score());
    }

    public Phase phase() {
        return phase;
    }

    public MatchRules rules() {
        return rules;
    }

    public MatchRules.PointSummary lastPoint() {
        return lastPoint;
    }

    public int rallies() {
        return rallies;
    }

    public VolleyballPlayer currentServer() {
        return world.player(rules.currentServer());
    }
}
