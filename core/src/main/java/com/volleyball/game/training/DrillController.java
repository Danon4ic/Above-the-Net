package com.volleyball.game.training;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.match.MatchListener;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.BallTouchListener;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.mechanics.TacticalTargets;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.BallContactListener;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs practice repetitions: puts the players in place, feeds the ball with a ball machine
 * (serve from the far side, pass to the setter, set to the hitter), watches what the human does
 * and grades the outcome. Everything goes through the same mechanics and physics as a match.
 */
public final class DrillController implements BallTouchListener, BallContactListener {
    static final float READY_TIME = 1.2f;
    static final float RESULT_TIME = 2.2f;
    static final float LIVE_TIMEOUT = 10f;

    enum Phase { READY, LIVE, RESULT }

    private final GameWorld world;
    private final BallHandling handling;
    private final LaunchSolver solver;
    private final LaunchSolver.Solution solution = new LaunchSolver.Solution();
    private final RandomXS128 random;
    private final Map<Integer, Vector2> basePositions = new HashMap<>();

    private Drill drill = Drill.SERVE;
    private Phase phase = Phase.READY;
    private float timer;
    private VolleyballPlayer trainee;
    private BallTouch traineeTouch;
    private String fault;
    /** The trainee's ball touched the net (it is still in play: over and in counts, as in a match). */
    private boolean netTouched;
    private int attempts;
    private int successes;
    private String lastResult = "";

    // Opponent attacker for the block and defense drills, played by the demo logic.
    private final AutoTrainee opponentAttacker;
    private VolleyballPlayer attacker;
    private boolean attackerHit;
    private boolean traineeBlocked;
    private String shotLabel = "";

    // Quick match (created on first use).
    private MatchController match;
    private MatchSession session;
    private Difficulty difficulty = Difficulty.INTERMEDIATE;
    private SessionMode sessionMode = SessionMode.SINGLE;
    private PlayerRole role = PlayerRole.OUTSIDE_HITTER;
    private MatchFormat format = MatchFormat.official();
    private OffenseSystem system = OffenseSystem.FIVE_ONE;
    private final List<MatchListener> matchListeners = new ArrayList<>();

    public DrillController(GameWorld world) {
        this(world, System.nanoTime());
    }

    /** @param seed seed of the ball machine's and opponents' random choices (reproducible drills) */
    public DrillController(GameWorld world, long seed) {
        this.world = world;
        this.random = new RandomXS128(seed);
        this.handling = world.ballHandling();
        this.solver = new LaunchSolver(world.predictor());
        for (VolleyballPlayer p : world.players()) basePositions.put(p.id, new Vector2(p.position.x, p.position.z));
        handling.addListener(this);
        world.addContactListener(this);
        opponentAttacker = new AutoTrainee(world);
        attacker = world.away().players().get(3);
    }

    public void start(Drill newDrill) {
        world.setBenchSign(1f);
        drill = newDrill;
        attempts = 0;
        successes = 0;
        lastResult = "";
        resetSquads();
        trainee = defaultTrainee(newDrill);
        handling.setNextServer(newDrill == Drill.SERVE ? trainee : null);
        if (drill == Drill.MATCH) {
            if (match == null || !match.rules().format().equals(format) || match.system() != system) {
                if (match != null) match.detach();
                match = new MatchController(world, format, random.nextLong(), difficulty, difficulty, system);
                for (MatchListener listener : matchListeners) match.addListener(listener);
            }
            match.setDifficulty(CourtSide.HOME, difficulty);
            match.setDifficulty(CourtSide.AWAY, difficulty);
            session = MatchSession.create(sessionMode, world, role);
            match.start(switch (firstServe) {
                case YOU -> CourtSide.HOME;
                case OPPONENT -> CourtSide.AWAY;
                case TOSS -> random.nextBoolean() ? CourtSide.HOME : CourtSide.AWAY;
            });
            // The chosen role's player in the starting line-up (in a 4-2 / 6-2 the opposite is the second setter).
            for (MatchSession.Slot slot : session.slots()) {
                VolleyballPlayer p = slot.player();
                if (match.rules().rotation().slotZone(slot.side, p.id) > 0 || p.role == PlayerRole.LIBERO) continue;
                PlayerRole wanted = p.role == PlayerRole.OPPOSITE ? PlayerRole.SETTER : p.role;
                com.volleyball.game.entities.Team team = slot.side == CourtSide.HOME ? world.home() : world.away();
                for (VolleyballPlayer candidate : team.squad()) {
                    if (candidate.role == wanted && match.rules().rotation().slotZone(slot.side, candidate.id) > 0
                            && session.assign(slot.index, candidate)) break;
                }
            }
            trainee = session.slot(0).player();
            phase = Phase.LIVE;
            return;
        }
        beginRepetition();
    }

    /** The running quick match, or null outside the match mode. */
    public MatchController match() {
        return drill == Drill.MATCH ? match : null;
    }

    /** The people in the running match, or null outside the match mode. */
    public MatchSession session() {
        return drill == Drill.MATCH ? session : null;
    }

    /** Difficulty and players of the next quick match (applied by {@link #start}). */
    public void setMatchOptions(Difficulty difficulty, SessionMode mode) {
        this.difficulty = difficulty;
        this.sessionMode = mode;
    }

    private com.volleyball.game.match.FirstServe firstServe = com.volleyball.game.match.FirstServe.YOU;

    /** Who serves first in the next quick match (a coin toss is drawn at each start). */
    public void setFirstServe(com.volleyball.game.match.FirstServe firstServe) {
        this.firstServe = firstServe == null ? com.volleyball.game.match.FirstServe.YOU : firstServe;
    }

    /** Role person 1 plays and the match format of the next quick match. */
    public void setMatchSetup(PlayerRole role, MatchFormat format) {
        this.role = role;
        this.format = format;
    }

    public PlayerRole role() {
        return role;
    }

    public void setSystem(OffenseSystem system) {
        this.system = system;
    }

    public OffenseSystem system() {
        return system;
    }

    /**
     * Everyone of the line-ups back on court, the liberos on the bench (drills are played
     * without the libero; the match brings it in itself).
     */
    private void resetSquads() {
        for (com.volleyball.game.entities.Team team : java.util.List.of(world.home(), world.away())) {
            for (VolleyballPlayer p : team.players()) p.setOnCourt(true);
            int bench = 0;
            for (VolleyballPlayer p : team.substitutes()) {
                p.setOnCourt(false);
                Vector2 seat = com.volleyball.game.world.CourtZones.benchPosition(team.side, ++bench, world.benchSign(), new Vector2());
                p.sitOnBench(seat.x, seat.y);
            }
            VolleyballPlayer libero = team.libero();
            if (libero != null) {
                libero.setOnCourt(false);
                Vector2 seat = com.volleyball.game.world.CourtZones.benchPosition(team.side, 0, world.benchSign(), new Vector2());
                libero.sitOnBench(seat.x, seat.y);
            }
        }
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public SessionMode sessionMode() {
        return sessionMode;
    }

    /** Receives match events (sound, score graphics) of every quick match. */
    public void addMatchListener(MatchListener listener) {
        matchListeners.add(listener);
        if (match != null) match.addListener(listener);
    }

    /** Hands a person's player to the computer (demo / spectator) or back. */
    public void setSlotAutomatic(int slot, boolean automatic) {
        if (session != null) session.setAutomatic(slot, automatic);
    }

    /** Next player for a person: in the match within the own team (not the other person's). */
    public VolleyballPlayer switchPlayer(int slot) {
        if (drill == Drill.MATCH && session != null) {
            VolleyballPlayer p = session.switchPlayer(slot);
            if (slot == 0) trainee = p;
            return p;
        }
        if (drill == Drill.FREE && slot == 0) {
            List<VolleyballPlayer> home = world.home().players();
            trainee = home.get((home.indexOf(trainee) + 1) % home.size());
        }
        return trainee;
    }

    /** Restarts the current repetition (e.g. after the human messed up the positioning). */
    public void restartRepetition() {
        if (drill == Drill.MATCH) return;
        beginRepetition();
    }

    public Drill drill() {
        return drill;
    }

    public VolleyballPlayer trainee() {
        return trainee;
    }

    /** Lets the human pick another player in the free mode. */
    public void setTrainee(VolleyballPlayer player) {
        if (drill == Drill.FREE || drill == Drill.MATCH) trainee = player;
    }

    public int attempts() {
        return attempts;
    }

    public int successes() {
        return successes;
    }

    public String lastResult() {
        return lastResult;
    }

    public String phaseLabel() {
        return switch (phase) {
            case READY -> "get ready";
            case LIVE -> "live";
            case RESULT -> "result";
        };
    }

    private VolleyballPlayer defaultTrainee(Drill d) {
        return switch (d) {
            case SET -> firstOfRole(PlayerRole.SETTER, 0);
            case RECEIVE -> firstOfRole(PlayerRole.OUTSIDE_HITTER, 1);
            case ATTACK -> firstOfRole(PlayerRole.OUTSIDE_HITTER, 0);
            case BLOCK -> firstOfRole(PlayerRole.MIDDLE_BLOCKER, 0);
            case DEFENSE -> firstOfRole(PlayerRole.OUTSIDE_HITTER, 1);
            default -> trainee != null ? trainee : world.home().players().get(1);
        };
    }

    /** The n-th home player with the role (falls back to the first home player). */
    private VolleyballPlayer firstOfRole(PlayerRole role, int skip) {
        int seen = 0;
        for (VolleyballPlayer p : world.home().players()) {
            if (p.role == role && seen++ == skip) return p;
        }
        return world.home().players().get(0);
    }

    private void beginRepetition() {
        for (VolleyballPlayer p : world.players()) {
            Vector2 base = basePositions.get(p.id);
            p.placeAt(base.x, base.y);
            world.intent(p).clear();
            handling.cancel(p);
        }
        traineeTouch = null;
        fault = null;
        netTouched = false;
        attackerHit = false;
        traineeBlocked = false;
        handling.clearServe();
        switch (drill) {
            case SERVE -> {
                trainee.placeAt(-CourtDimensions.HALF_LENGTH - 1.2f, 1.5f);
                handling.prepareServe(trainee, handling.serveType(trainee));
                phase = Phase.LIVE;
                timer = 0f;
            }
            case SET -> {
                Vector3 target = TacticalTargets.setterTarget(CourtSide.HOME, new Vector3());
                trainee.placeAt(target.x - 0.6f, target.z);
                parkBall();
                phase = Phase.READY;
                timer = READY_TIME;
            }
            case ATTACK -> {
                trainee.placeAt(-3.8f, -3.3f);
                parkBall();
                phase = Phase.READY;
                timer = READY_TIME;
            }
            case RECEIVE -> {
                parkBall();
                phase = Phase.READY;
                timer = READY_TIME;
            }
            case BLOCK, DEFENSE -> {
                // Teammates step off to the side line so only the trainee plays the ball.
                int bench = 0;
                for (VolleyballPlayer p : world.home().players()) {
                    if (p != trainee) p.placeAt(-1.5f - 1.4f * bench++, -CourtDimensions.HALF_WIDTH - 1.2f);
                }
                if (drill == Drill.BLOCK) trainee.placeAt(-0.45f, 0f);
                else trainee.placeAt(-6.5f, -3f);
                // The opponent's hitter waits at the start of the approach for zone 4 (their left, +Z).
                attacker.placeAt(3.8f, 3.3f);
                chooseShot();
                parkBall();
                phase = Phase.READY;
                timer = READY_TIME;
            }
            case FREE -> {
                phase = Phase.LIVE;
                timer = 0f;
            }
            case MATCH -> {
                // The match has no repetitions: MatchController runs it.
            }
        }
    }

    private float range(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private void parkBall() {
        world.ball().hold(new Vector3(-CourtDimensions.HALF_LENGTH - 1.5f, 1.0f, CourtDimensions.HALF_WIDTH + 1f));
    }

    public void update(float dt) {
        if (drill == Drill.MATCH) {
            match.update(dt, session.humans());
            if (session.followReplacements(match.rules().rotation(), match.rules().substitutions())) trainee = session.slot(0).player();
            return;
        }
        timer += phase == Phase.LIVE ? dt : -dt;
        switch (phase) {
            case READY -> {
                if (timer <= 0f) {
                    feed();
                    phase = Phase.LIVE;
                    timer = 0f;
                }
            }
            case LIVE -> {
                if (drill == Drill.BLOCK || drill == Drill.DEFENSE) {
                    opponentAttacker.update(attacker, Drill.ATTACK, world.time());
                }
                if (drill != Drill.FREE && timer > LIVE_TIMEOUT) finish(null);
            }
            case RESULT -> {
                if (timer <= 0f) beginRepetition();
            }
        }
    }

    /** Ball machine feed for the current drill. */
    private void feed() {
        Ball ball = world.ball();
        switch (drill) {
            case RECEIVE -> {
                ServeType[] types = {ServeType.FLOAT, ServeType.POWER, ServeType.JUMP_TOPSPIN, ServeType.JUMP_FLOAT};
                ServeType type = types[random.nextInt(types.length)];
                Vector3 from = new Vector3(CourtDimensions.HALF_LENGTH + (type.jump ? 0.5f : 2f), type.jump ? 3.2f : 2.85f,
                        range(-3f, 3f));
                float tx = MathUtils.clamp(trainee.position.x + range(-1.5f, 1.5f), -8.3f, -3.5f);
                float tz = MathUtils.clamp(trainee.position.z + range(-1.5f, 1.5f), -4f, 4f);
                float speed = (type.minSpeed + type.maxSpeed) / 2f;
                solver.solveOverNet(from, tx, tz, speed * 0.8f, speed, type.topspin, 0f, 2.43f + Ball.RADIUS + 0.25f, solution);
                ball.launch(from, solution.velocity, solution.spin, random.nextLong());
                handling.registerFeed(CourtSide.AWAY, 1);
                lastResult = "Incoming: " + type.label;
            }
            case SET -> {
                // An imperfect pass: up to ~1 m off the setter target, so footwork matters.
                Vector3 target = TacticalTargets.setterTarget(CourtSide.HOME, new Vector3())
                        .add(range(-1f, 0.6f), 0f, range(-1f, 1f));
                Vector3 from = new Vector3(-6.5f, 0.9f, range(-2.5f, 2.5f));
                solver.solveArc(from, target, TacticalTargets.PASS_APEX, 0f, solution);
                ball.launch(from, solution.velocity, solution.spin, random.nextLong());
                handling.registerFeed(CourtSide.HOME, 1);
                lastResult = "Pass is coming";
            }
            case ATTACK -> {
                Vector3 spot = SetType.HIGH_OUTSIDE.target(CourtSide.HOME, new Vector3())
                        .add(range(-0.3f, 0.3f), 0f, range(-0.4f, 0.4f));
                Vector3 from = TacticalTargets.setterTarget(CourtSide.HOME, new Vector3());
                solver.solveArc(from, spot, SetType.HIGH_OUTSIDE.apex, 0f, solution);
                ball.launch(from, solution.velocity, solution.spin, random.nextLong());
                handling.registerFeed(CourtSide.HOME, 2);
                lastResult = "Set is coming";
            }
            case BLOCK, DEFENSE -> {
                // The opponent's setter (a machine) sets their hitter in zone 4.
                Vector3 spot = SetType.HIGH_OUTSIDE.target(CourtSide.AWAY, new Vector3())
                        .add(range(-0.2f, 0.2f), 0f, range(-0.3f, 0.3f));
                Vector3 from = TacticalTargets.setterTarget(CourtSide.AWAY, new Vector3());
                solver.solveArc(from, spot, SetType.HIGH_OUTSIDE.apex, 0f, solution);
                ball.launch(from, solution.velocity, solution.spin, random.nextLong());
                handling.registerFeed(CourtSide.AWAY, 2);
                lastResult = "Set to their hitter";
            }
            default -> {
            }
        }
    }

    /** Picks the hitter's shot for this repetition (unknown to the trainee, of course). */
    private void chooseShot() {
        AttackType type = AttackType.POWER;
        float x;
        float z;
        // Block: line or diagonal. Defense: shots into the zone-5 defender's area (the line is
        // another defender's job).
        int pick = drill == Drill.BLOCK ? random.nextInt(2) : 1 + random.nextInt(4);
        switch (pick) {
            case 0 -> { // line: along the sideline on the hitter's side
                x = -6.3f;
                z = 3.7f;
                shotLabel = "line";
            }
            case 1 -> { // diagonal, deep
                x = -6.6f;
                z = -3.0f;
                shotLabel = "diagonal";
            }
            case 2 -> { // short diagonal
                x = -4.3f;
                z = -1.8f;
                shotLabel = "short diagonal";
            }
            case 3 -> { // seam between the defenders
                x = -6.0f;
                z = -0.8f;
                shotLabel = "seam";
            }
            default -> { // tip behind the block
                x = -2.3f;
                z = -1.2f;
                type = AttackType.TIP;
                shotLabel = "tip";
            }
        }
        handling.setAim(attacker, x + range(-0.5f, 0.5f), z + range(-0.5f, 0.5f));
        handling.setAttackType(attacker, type);
    }

    @Override
    public void onBallTouch(BallTouch touch) {
        if (phase != Phase.LIVE || drill == Drill.MATCH) return;
        if (attacker != null && touch.playerId() == attacker.id && touch.type() == ContactType.ATTACK) attackerHit = true;
        if (trainee == null || touch.playerId() != trainee.id) return;
        if (touch.type() == ContactType.BLOCK) traineeBlocked = true;
        // The repetition is graded on the trainee's first contact; later ones are extra.
        if (traineeTouch != null) return;
        traineeTouch = touch;
        lastResult = describe(touch);
    }

    @Override
    public void onBallContact(BallContact contact) {
        if (phase != Phase.LIVE || drill == Drill.FREE || drill == Drill.MATCH) return;
        switch (contact.kind()) {
            case NET_MESH, NET_BAND -> {
                if (traineeTouch != null && contact.position().x * trainee.side.sign() < 0.5f) netTouched = true;
            }
            case ANTENNA -> fault = "antenna";
            case FLOOR -> finish(contact.position());
            case WALL, CEILING, POST -> finish(contact.position());
            default -> {
            }
        }
    }

    /** Ends the repetition and grades it. {@code landing} is null for a timeout. */
    private void finish(Vector3 landing) {
        if (phase != Phase.LIVE) return;
        if (drill == Drill.SERVE && handling.servePhase() == BallHandling.ServePhase.HOLDING) return;
        if (drill == Drill.BLOCK || drill == Drill.DEFENSE) {
            finishAgainstAttack(landing);
            return;
        }
        attempts++;
        boolean success = false;
        StringBuilder text = new StringBuilder();
        if (traineeTouch == null) {
            text.append(drill == Drill.RECEIVE ? "Ace - you did not touch the serve" : "Missed - no contact");
        } else {
            text.append(describe(traineeTouch));
            switch (drill) {
                case SERVE, ATTACK -> {
                    boolean in = landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.AWAY
                            && CourtDimensions.isInsideCourt(landing.x, landing.z, 0.025f) && fault == null;
                    success = in && (drill == Drill.SERVE || traineeTouch.type() == ContactType.ATTACK);
                    String miss = fault != null ? "FAULT (" + fault + ")"
                            : netTouched && (landing == null || CourtDimensions.sideOf(landing.x) != CourtSide.AWAY) ? "FAULT (into the net)" : "OUT";
                    text.append(" -> ").append(in ? (netTouched ? "IN off the net" : "IN") : miss);
                    if (landing != null) text.append(String.format(" at (%.1f, %.1f)", landing.x, landing.z));
                }
                case RECEIVE, SET -> success = traineeTouch.rating() != null && traineeTouch.rating().isPlayable()
                        && (drill == Drill.RECEIVE || traineeTouch.purpose() == ContactPurpose.SET);
                default -> {
                }
            }
        }
        if (success) successes++;
        lastResult = (success ? "SUCCESS: " : "FAIL: ") + text;
        phase = Phase.RESULT;
        timer = RESULT_TIME;
    }

    /**
     * Grades block and defense repetitions. Repetitions where the opponent's hitter failed
     * (missed the set, hit the net) are not counted.
     */
    private void finishAgainstAttack(Vector3 landing) {
        boolean landedHome = landing != null && CourtDimensions.sideOf(landing.x) == CourtSide.HOME;
        boolean landedIn = landing != null && CourtDimensions.isInsideCourt(landing.x, landing.z, 0.025f);
        boolean traineePlayed = traineeTouch != null && traineeTouch.type() != ContactType.BLOCK;
        if (!attackerHit || (!landedHome && !traineeBlocked && !traineePlayed)) {
            lastResult = "Not counted - the hitter made an error";
            phase = Phase.RESULT;
            timer = RESULT_TIME;
            return;
        }
        attempts++;
        boolean success;
        String text;
        if (drill == Drill.BLOCK) {
            success = traineeBlocked && !landedHome;
            if (success) text = "STUFF BLOCK";
            else if (traineeBlocked) text = landedIn ? "Block touch - the ball stayed alive on your side" : "Touched off the block - out";
            else text = landedIn ? "Not blocked - kill" : "Not blocked (the hit went out)";
            text += " (" + shotLabel + ")";
        } else if (traineePlayed) {
            success = traineeTouch.rating() != null && traineeTouch.rating() != PassRating.EMERGENCY;
            text = (success ? "Dig up: " : "Dig, but unplayable: ") + describe(traineeTouch) + " (" + shotLabel + ")";
        } else {
            success = !landedIn;
            text = success ? "Good read - you let an out ball go" : "Kill - no dig (" + shotLabel + ")";
        }
        if (success) successes++;
        lastResult = (success ? "SUCCESS: " : "FAIL: ") + text;
        phase = Phase.RESULT;
        timer = RESULT_TIME;
    }

    private static String describe(BallTouch t) {
        StringBuilder s = new StringBuilder();
        s.append(t.type().label);
        if (!t.variant().isEmpty()) s.append(" (").append(t.variant()).append(')');
        s.append(String.format(", contact %.0f%%", t.quality() * 100f));
        if (t.rating() != null) s.append(String.format(", %s - %.1f m off", t.rating().label, t.missDistance()));
        if (t.type() == ContactType.SERVE || t.type() == ContactType.ATTACK) {
            s.append(String.format(", %.0f km/h", t.outgoingVelocity().len() * 3.6f));
        }
        return s.toString();
    }
}
