package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ServeSpin;
import com.volleyball.game.mechanics.BallTouchListener;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ContactZone;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.QuickSetWindow;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.mechanics.TacticalTargets;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.physics.PhysicsConstants;
import com.volleyball.game.physics.PlayerLocomotion;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.CourtZones;
import com.volleyball.game.tactics.Combination;
import com.volleyball.game.tactics.DefenseScheme;
import com.volleyball.game.tactics.Playbook;
import com.volleyball.game.tactics.TeamCommand;
import com.volleyball.game.tactics.TeamTactics;
import com.volleyball.game.tactics.Tempo;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The AI of one team. Every computer player has a role, a state ({@link AiState}), a target
 * position, a read of the ball (forecast from observable state, with a difficulty-dependent
 * misjudgement), an estimate of its chance to reach the ball, a reaction time, its own ratings,
 * and the team shares a risk level and a memory of the rally and the match ({@link MatchMemory}).
 *
 * <p>Each step, decisions follow the priorities of the design:
 * <ol>
 *   <li>avoid collisions with teammates (applied last, to everyone but the ball player);</li>
 *   <li>mandatory actions: the serve;</li>
 *   <li>receive or save the ball: exactly one player — the one most likely to reach it — plays
 *       it, the others leave it (a human teammate can be that player);</li>
 *   <li>prepare the set: the setter goes to the setting spot and chooses the set;</li>
 *   <li>attack (attackers approach; the hitter chooses the shot against the block) or block
 *       (single, or a coordinated double block);</li>
 *   <li>everyone else takes a tactical position: reception, defense, cover of the own hitter.</li>
 * </ol>
 * Human-controlled players are never moved, but they count as teammates.
 */
public final class TeamAI implements BallTouchListener {
    /** Collision avoidance: teammates keep at least this distance unless one plays the ball. */
    static final float PERSONAL_SPACE = 1.0f;
    /** A ball coming down this close to a human teammate is theirs (the AI leaves it). */
    static final float HUMAN_AREA = 2.0f;
    /** The AI still takes a human's ball only if the human cannot make it and the AI clearly can. */
    static final float HUMAN_GIVES_UP = 0.15f;
    static final float AI_SURE = 0.5f;
    /** Depth of the serve reception line. */
    static final float RECEPTION_DEPTH = 6.0f;
    /** A serve coming down this close to a receiver is played by that receiver. */
    static final float BODY_AREA = 1.1f;

    private final GameWorld world;
    private final Team team;
    private final CourtSide side;
    private final Random random;
    private final Map<Integer, Brain> brains = new HashMap<>();
    private final MatchMemory memory;
    private final TrajectoryPredictor.Prediction forecast = new TrajectoryPredictor.Prediction();
    /** Where the ball will be at hitting height (back-row attack checks). */
    private final TrajectoryPredictor.Prediction hitRead = new TrajectoryPredictor.Prediction();
    private final Vector2 spot = new Vector2();
    private final Vector3 target = new Vector3();
    private final EnumMap<AttackType, Float> shotWeights = new EnumMap<>(AttackType.class);
    private final ShotAdvisor advisor;
    /** Block and back-court defense against the opponent's attack. */
    private final TeamDefense defense;
    /** Calls and runs the attacking combinations. */
    private final PlayCaller playCaller;
    /** The head coach on the bench: gives the team its commands when no person is in it. */
    private final TacticalCoach coach;
    /** A person plays in this team (then they give the commands, not the coach). */
    private boolean personInTeam;
    /** Whether the coach decides at all (off: the commands stay as they were set from outside). */
    private boolean coaching = true;
    /** The last touch was our own serve (a serve that falls back off the net is lost: nobody plays it). */
    private boolean ourServeInPlay;
    private MatchScore lastScore;
    /** The coach's (a person's) tactical commands for this team. */
    private final TeamTactics tactics = new TeamTactics();
    private Playbook playbook = Playbook.standard();
    private final TrajectoryPredictor.Prediction setterRead = new TrajectoryPredictor.Prediction();
    /** The set planned for this possession. */
    private SetType plannedSet = SetType.HIGH_OUTSIDE;
    private boolean playForCurrentPass;
    private SetType deliveredSet;
    /** Seconds until the setter's contact (estimated each step while the pass travels). */
    private float timeToSet = 1f;
    private int jumpSettingId = -1;
    private final QuickSetWindow quickWindow = new QuickSetWindow();
    private final Vector3 quickRelease = new Vector3();
    private final Vector3 quickPoint = new Vector3();
    private final Vector3 quickLane = new Vector3();
    private float quickLead = Float.NaN;
    private int quickSetterId = -1;
    private int earlyJumpFor = -1;
    private int quickHitterId = -1;
    private double quickUntil;
    /** Decoys who have jumped in this possession. */
    private final java.util.Set<Integer> decoysJumped = new java.util.HashSet<>();
    /** The play the attack now in the air came from (to learn which plays score). */
    private String attackPlay;

    private Difficulty difficulty;
    private float risk;
    private OffenseSystem system = OffenseSystem.FIVE_ONE;

    // Per-rally decisions.
    private int responsibleId = -1;
    private VolleyballPlayer calledBallPlayer;
    private int responsibleForToucher = Integer.MIN_VALUE;
    private CourtSide responsibleForSide;
    private int responsibleForCount = -1;
    /** Counts the ball changing hands (a new last toucher); keys per-touch random draws such as the jump timing. */
    private int ballPhase;
    private double responsibleChosenAt;
    private int attackerId = -1;
    private int attackDecidedFor = -1;
    private boolean serveAimed;
    private double lastTouchAt;
    /** The ball now coming was served (the first touch is a serve reception). */
    private boolean receivingServe;
    /** Reception lanes: receiver id → lateral centre of the lane (team frame), set when the serve is hit. */
    private final Map<Integer, Float> receptionLanes = new HashMap<>();
    /** Players on court this step (the libero replaces a back-row middle). */
    private List<VolleyballPlayer> court = List.of();
    private boolean dumpDecided;
    /** The last touch was a block (the ball is a rebound off the hands). */
    private boolean offBlock;
    private boolean attackRebound;
    private boolean reboundPass;
    /** Our last touch was an attack: when it was hit, and by whom (the cover holds until the block's result). */
    private double ourAttackAt = -10;
    private int ourAttackerId = -1;

    // Between rallies.
    private double celebrationStart = -1;
    private boolean wonLastPoint;
    private int pointPlayerId;

    /** The state of one computer player. */
    static final class Brain {
        final AutoPlayer motor;
        AiState state = AiState.IDLE;
        final Vector2 targetPosition = new Vector2();
        /** World time the target position was last given (this step's: the player is heading there). */
        double targetTime = Double.NaN;
        /** Positioning mistake of this rally (difficulty), added to tactical positions. */
        final Vector2 positionError = new Vector2();
        float reachChance;
        boolean wasAirborne;
        boolean reactedToPoint;
        int highFivePartner = -1;
        boolean highFiveDone;
        /** World time of the last landing (the landing state lasts {@link #LANDING_TIME}). */
        double landedAt = Double.NEGATIVE_INFINITY;
        /** The player's own way of playing (shot preferences, risk), so no two hitters are alike. */
        Style style = Style.NEUTRAL;
        AttackType lastAttack;

        Brain(AutoPlayer motor) {
            this.motor = motor;
        }
    }

    /**
     * A computer player's habits as a hitter: how much they like the power spike, the line, the
     * cut, the tip and the roll shot, and how close to the lines they dare to aim. Drawn once per
     * player and shaped by the player's traits (the cannon hits, the crafty hitter tips and rolls,
     * the line hitter goes down the line, the hothead aims at the lines), so every hitter has a
     * recognisable style that scouting can pick up.
     */
    record Style(float power, float line, float cut, float tip, float roll, float daring) {
        static final Style NEUTRAL = new Style(1f, 0.5f, 0.3f, 0.3f, 0.3f, 0f);

        static Style of(VolleyballPlayer p, Random random) {
            float power = 0.7f + random.nextFloat() * 0.6f;
            float line = 0.2f + random.nextFloat() * 0.6f;
            float cut = 0.1f + random.nextFloat() * 0.5f;
            float tip = 0.1f + random.nextFloat() * 0.5f;
            float roll = 0.1f + random.nextFloat() * 0.4f;
            float daring = (random.nextFloat() - 0.5f) * 0.5f;
            if (p.has(PlayerTrait.CANNON)) {
                power += 0.6f;
                tip *= 0.4f;
                roll *= 0.5f;
            }
            if (p.has(PlayerTrait.CRAFTY)) {
                power *= 0.7f;
                tip = tip * 1.6f + 0.2f;
                roll = roll * 1.6f + 0.15f;
                cut = cut * 1.5f + 0.15f;
            }
            if (p.has(PlayerTrait.LINE_HITTER)) line = 0.8f + random.nextFloat() * 0.15f;
            if (p.has(PlayerTrait.CROSS_HITTER)) line = 0.05f + random.nextFloat() * 0.15f;
            if (p.has(PlayerTrait.HOTHEAD)) daring += 0.4f;
            return new Style(power, line, cut, tip, roll, daring);
        }
    }

    /** Daring of this attack: the style's, plus how the hitter handles set point. */
    private float daring(VolleyballPlayer a, Style style) {
        float d = style.daring();
        if (world.ballHandling().pressure()) {
            if (a.has(PlayerTrait.CLUTCH)) d += 0.15f;
            if (a.has(PlayerTrait.NERVOUS)) d -= 0.4f;
        }
        return d;
    }

    public TeamAI(GameWorld world, Team team, Difficulty difficulty, long seed) {
        this.world = world;
        this.team = team;
        this.side = team.side;
        this.random = new Random(seed);
        this.memory = new MatchMemory(team.side);
        for (VolleyballPlayer p : team.squad()) {
            Brain brain = new Brain(new AutoPlayer(world, random.nextLong()));
            brain.style = Style.of(p, random);
            brains.put(p.id, brain);
        }
        this.advisor = new ShotAdvisor(world);
        this.playCaller = new PlayCaller(side, new Random(seed ^ 0x5EEDL));
        this.coach = new TacticalCoach(side, difficulty, tactics);
        this.defense = new TeamDefense(world, side, tactics, memory, random, brains);
        setDifficulty(difficulty);
    }

    /** Turns the bench coach on or off (off: nobody changes the team's commands but whoever set them). */
    public void setCoaching(boolean on) {
        coaching = on;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        coach.setDifficulty(difficulty);
        this.risk = difficulty.risk;
        for (VolleyballPlayer p : team.squad()) {
            // Individual reaction: the rating speeds up or slows down the difficulty's base reaction.
            float rating = PlayerAttributes.scale(p.attributes.reaction, 1.3f, 0.7f);
            // The reader moves first and judges the ball better; the digger is quick to the floor.
            float reader = p.has(PlayerTrait.READER) ? 0.75f : 1f;
            float digger = p.has(PlayerTrait.DIGGER) ? 0.8f : 1f;
            brains.get(p.id).motor.setSkill(new AutoPlayer.Skill(difficulty.reaction * rating * reader,
                    difficulty.defenseReaction * rating * reader * digger, difficulty.readError * (reader < 1f ? 0.6f : 1f),
                    difficulty.residualError));
        }
    }

    /** The team's tactical commands (given by the person playing for this team). */
    public TeamTactics tactics() {
        return tactics;
    }

    /** The plays this team can call (built-in and custom). */
    public void setPlaybook(Playbook playbook) {
        this.playbook = playbook;
    }

    public Playbook playbook() {
        return playbook;
    }

    /** The play now being run and what happened to it (for the AI view), or "". */
    public String playStatus() {
        return playCaller.status();
    }

    /** The play now being run, or null. */
    public Combination currentPlay() {
        return playCaller.play();
    }

    /**
     * The set the current play would give now (for a person setting on Auto), or null without a play.
     */
    public SetType playSet() {
        Combination.Option o = playCaller.chosen();
        return o == null || o.dump() ? null : o.set();
    }

    /** Intended hitter while a released quick ball is still playable during the same jump. */
    public VolleyballPlayer quickHitter() {
        if (quickHitterId < 0 || world.time() > quickUntil || !world.ball().isActive()
                || world.ballHandling().teamTouches(side) != 2 || world.ball().position.x * side.sign() <= 0.12f) return null;
        VolleyballPlayer hitter = world.player(quickHitterId);
        return hitter != null && hitter.isAirborne() && hitter.isUpright() && hitter.isOnCourt() ? hitter : null;
    }

    public boolean quickWindowFor(VolleyballPlayer hitter) {
        VolleyballPlayer setter = world.player(quickSetterId);
        BallHandling.QuickSet option = setter == null ? null : world.ballHandling().quickSet(setter);
        return option != null && option.hitter() == hitter;
    }

    /** The active branch of our attack and the player's part in it. */
    public record PlayAssignment(Combination play, SetType set, VolleyballPlayer hitter, Combination.Run run,
                                 VolleyballPlayer decoy, boolean setting, boolean committed, boolean alternate) {
    }

    public PlayAssignment playAssignment(VolleyballPlayer p) {
        Combination play = playCaller.play();
        Combination.Option option = playCaller.chosen();
        if (!playForCurrentPass || play == null || option == null || p.side != side || !p.isOnCourt()) return null;
        BallHandling handling = world.ballHandling();
        int touches = handling.teamTouches(side);
        if (handling.lastTouchSide() != side || touches < 1 || touches > 2 || !world.ball().isActive()) return null;
        boolean afterSet = touches == 2;
        if (afterSet && deliveredSet == null) return null;
        int lastToucher = handling.lastToucherId();
        SetType set = afterSet ? deliveredSet : option.set();
        VolleyballPlayer hitter;
        if (afterSet && responsibleForToucher == lastToucher && responsibleId >= 0 && responsibleId != lastToucher) {
            hitter = world.player(responsibleId);
        } else if (set != option.set()) {
            hitter = playCaller.runner(set.lane);
        } else {
            hitter = playCaller.chosenHitter(world.player(responsibleId));
        }
        Combination.Option first = play.options().get(0);
        boolean alternate = option != first || set != first.set();
        SetType.Lane hitterLane = set == option.set() ? option.hitter() : set.lane;
        Combination.Run run = null;
        VolleyballPlayer decoy = null;
        for (Combination.Run candidate : play.runs()) {
            VolleyballPlayer runner = playCaller.runner(candidate.lane());
            if (runner == null || !runner.isOnCourt()) continue;
            if (candidate.decoy() && runner != hitter && decoy == null) decoy = runner;
            if (runner == p && (run == null || candidate.lane() == hitterLane)) run = candidate;
        }
        return new PlayAssignment(play, set, hitter, run, decoy, !afterSet && p.id == responsibleId,
                afterSet || playCaller.decided(), alternate);
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public void setSystem(OffenseSystem system) {
        this.system = system;
    }

    public OffenseSystem system() {
        return system;
    }

    /** The player who sets in this rotation (see {@link OffenseSystem}), or null. */
    public VolleyballPlayer activeSetter(RotationManager rotation) {
        court = team.onCourt();
        return setter(rotation);
    }

    public MatchMemory memory() {
        return memory;
    }

    public float risk() {
        return risk;
    }

    /** Current state of a player of this team (IDLE for human-controlled players). */
    public AiState state(VolleyballPlayer p) {
        Brain brain = brains.get(p.id);
        return brain == null ? AiState.IDLE : brain.state;
    }

    /** The player chosen to play the current ball (-1 if none). */
    public int responsibleId() {
        return responsibleId;
    }

    /** Latest live ball call. May be empty when the team leaves the ball or is still reading it. */
    public VolleyballPlayer calledBallPlayer() {
        return calledBallPlayer;
    }

    /** The hitter the team plans to set (-1 if none). */
    public int attackerId() {
        return attackerId;
    }

    /** Estimated chance (0..1) that the player reaches the current ball, as last computed. */
    public float reachChance(VolleyballPlayer p) {
        Brain brain = brains.get(p.id);
        return brain == null ? 0f : brain.reachChance;
    }

    /** Resets per-rally decisions (call before each serve). */
    public void newRally() {
        responsibleId = -1;
        calledBallPlayer = null;
        responsibleForToucher = Integer.MIN_VALUE;
        responsibleForSide = null;
        responsibleForCount = -1;
        attackerId = -1;
        attackDecidedFor = -1;
        serveAimed = false;
        playCaller.reset();
        jumpSettingId = -1;
        quickSetterId = -1;
        quickHitterId = -1;
        earlyJumpFor = -1;
        playForCurrentPass = false;
        deliveredSet = null;
        decoysJumped.clear();
        attackPlay = null;
        defense.newRally();
        celebrationStart = -1;
        receivingServe = false;
        receptionLanes.clear();
        dumpDecided = false;
        offBlock = false;
        attackRebound = false;
        reboundPass = false;
        ourAttackAt = -10;
        ourAttackerId = -1;
        coverFor = -1;
        coverSlots.clear();
        memory.newRally();
        coach.newRally();
        ourServeInPlay = false;
        for (Brain brain : brains.values()) {
            brain.positionError.set((float) random.nextGaussian(), (float) random.nextGaussian()).scl(difficulty.positionError * 0.6f);
            brain.motor.newRally();
            brain.motor.setBlockOffset(0f);
            brain.motor.setDefenseAdjustment(0f, 0f);
            brain.motor.setDefenseSpot(Float.NaN, Float.NaN);
        }
    }

    @Override
    public void onBallTouch(BallTouch touch) {
        if (touch.teamTouchNumber() == 1) reboundPass = offBlock && touch.side() == side;
        attackRebound = touch.type() == ContactType.BLOCK && touch.side() != side && holdingCover(touch.time());
        calledBallPlayer = null;
        memory.record(touch);
        quickHitterId = touch.side() == side ? touch.quickHitterId() : -1;
        if (quickHitterId >= 0) quickUntil = touch.time() + 0.38;
        if (touch.side() != side || touch.teamTouchNumber() == 1) playForCurrentPass = false;
        deliveredSet = touch.side() == side && touch.teamTouchNumber() == 2 && touch.purpose() == ContactPurpose.SET
                ? memory.lastSet() : null;
        coach.record(touch);
        lastTouchAt = touch.time();
        offBlock = touch.type() == ContactType.BLOCK;
        ourServeInPlay = touch.type() == ContactType.SERVE && touch.side() == side;
        if (touch.type() == ContactType.ATTACK && touch.side() == side) {
            ourAttackAt = touch.time();
            ourAttackerId = touch.playerId();
            attackPlay = playCaller.play() == null ? null : playCaller.play().id();
        } else if (touch.side() == side && touch.teamTouchNumber() == 1) {
            attackPlay = null;
        }
        receivingServe = touch.type() == ContactType.SERVE && touch.side() != side;
        if (receivingServe) receptionLanes.clear();
        if (touch.side() != side && touch.type() == ContactType.ATTACK && AttackType.FEINT.label.equals(touch.variant())) {
            // The defence was set for the full swing: the soft ball is picked up late (readers less so).
            for (VolleyballPlayer p : team.squad()) {
                float late = FEINT_SURPRISE * (difficulty.readsPlay ? 0.6f : 1f) * (p.has(PlayerTrait.READER) ? 0.5f : 1f);
                brain(p).motor.surprise(late);
            }
        }
    }

    /** Extra reaction time (s) of defenders against a feint out of a full swing. */
    static final float FEINT_SURPRISE = 0.18f;

    /** After a rally: remember what worked (adaptive tactics) and start celebrating or regrouping. */
    public void rallyFinished(boolean won, double now, int pointPlayerId) {
        memory.rallyResult(won);
        // Which plays score: the rally ended with our attack from a play.
        List<MatchMemory.Touch> rallyTouches = memory.rallyTouches();
        MatchMemory.Touch last = rallyTouches.isEmpty() ? null : rallyTouches.get(rallyTouches.size() - 1);
        if (attackPlay != null && memory.decidedByOwnAttack()) playCaller.result(attackPlay, won);
        // The coach of a team without a person rethinks the plan (the person commands their own team).
        if (coaching && !personInTeam && lastScore != null) {
            coach.rallyFinished(won, last == null ? null : last.side(), last == null ? null : last.type(),
                    lastScore.points(side), lastScore.points(side.opposite()), lastScore.setNumber(),
                    new TacticalCoach.PlayResults() {
                        @Override
                        public int tries(String playId) {
                            return playCaller.tries(playId);
                        }

                        @Override
                        public int points(String playId) {
                            return playCaller.points(playId);
                        }
                    });
        }
        wonLastPoint = won;
        celebrationStart = now;
        this.pointPlayerId = pointPlayerId;
        for (Brain brain : brains.values()) {
            brain.reactedToPoint = false;
            brain.highFivePartner = -1;
            brain.highFiveDone = false;
        }
    }

    // ------------------------------------------------------------------ main update

    /**
     * Fills the intents of all team players except the human-controlled ones for the coming step.
     *
     * @param score  the match score (risk management), may be null
     * @param humans players controlled by people (skipped)
     */
    public void update(double now, RotationManager rotation, MatchScore score, Collection<VolleyballPlayer> humans) {
        BallHandling handling = world.ballHandling();
        Ball ball = world.ball();
        calledBallPlayer = null;
        court = team.onCourt();
        updateRisk(score);
        lastScore = score;
        personInTeam = false;
        for (VolleyballPlayer h : humans) if (h.side == side) personInTeam = true;
        idleOnBench(humans);

        VolleyballPlayer server = handling.server();
        boolean ourServe = server != null && server.side == side && handling.servePhase() != BallHandling.ServePhase.NONE;
        // Priority 2: the serve is mandatory.
        if (ourServe && !humans.contains(server)) {
            aimServe(server, handling);
            brain(server).motor.act(server, AutoPlayer.Behavior.PASS, now);
            brain(server).state = AiState.SERVING;
        }
        // Until the serve is hit everyone stays in rotation position (overlap rule at the serve).
        if (!ball.isActive() || handling.servePhase() != BallHandling.ServePhase.NONE) {
            boolean receiving = server != null && server.side != side;
            for (VolleyballPlayer p : court) {
                if (humans.contains(p) || p == server) continue;
                world.intent(p).clear();
                brain(p).state = receiving ? AiState.READY_TO_RECEIVE : AiState.IDLE;
            }
            updateBodyStates(humans);
            return;
        }

        forecast.watchHeight = 1.0f;
        world.predictor().predict(ball, 6f, forecast);
        TrajectoryPredictor.dropAtNet(forecast, world.court().net().topHeight(), ball.position.x);
        forecast.watchHeight = Float.NaN;
        // A ball already below that height but still in the air on our side (a steep one reaches
        // the forearms only at about 0.75 m) is still ours, coming down right here: the player
        // under it must not be called off at the last moment.
        if (!forecast.reachesWatchHeight && ball.isActive() && ball.velocity.y < 0f && ball.position.y > LOW_BALL_HEIGHT
                && CourtDimensions.sideOf(ball.position.x) == side) {
            forecast.reachesWatchHeight = true;
            forecast.descentPoint.set(ball.position);
            forecast.timeToDescent = 0f;
        }
        boolean comingToUs = forecast.reachesWatchHeight && CourtDimensions.sideOf(forecast.descentPoint.x) == side;
        boolean ourPossession = handling.lastTouchSide() == side;
        int touches = handling.teamTouches(side);
        setterRunningId = -1;

        CourtSide touchSide = handling.lastTouchSide();
        int touchCount = touchSide == null ? 0 : handling.teamTouches(touchSide);
        if (handling.lastToucherId() != responsibleForToucher || touchSide != responsibleForSide || touchCount != responsibleForCount) {
            responsibleForToucher = handling.lastToucherId();
            responsibleForSide = touchSide;
            responsibleForCount = touchCount;
            ballPhase++;
            responsibleId = -1;
            attackDecidedFor = -1;
            dumpDecided = false;
        }

        if (receivingServe && receptionLanes.isEmpty()) assignReceptionLanes();
        if (comingToUs && (ourServeInPlay || !ourPossession && clearlyOut())) {
            // The other team sends it out (a missed serve, a ball off their block): nobody plays
            // it, the player it flies at steps out of its way. A touch would make it our ball.
            // Our own serve coming back off the net is lost anyway: touching it is a fault too.
            defendOrWait(now, rotation, humans);
            calledBallPlayer = null;
            cancelOthers(-1, humans);
            VolleyballPlayer inPath = ballInPath(humans);
            if (inPath != null) dodge(inPath);
        } else if (quickHitter() != null || comingToUs && (!ourPossession || touches < 3)) {
            playOurBall(now, rotation, humans, ourPossession ? touches : 0);
            calledBallPlayer = world.player(responsibleId);
        } else {
            defendOrWait(now, rotation, humans);
        }
        separateSpots(humans);
        avoidCollisions(humans);
        keepOffTheNet(humans);
        updateBodyStates(humans);
    }

    /**
     * Last check of every computer player on the floor: close to the net nobody keeps running
     * into it (a decoy landing, a hitter whose set went elsewhere, a late cover). Only the player
     * playing the ball and the blockers may come this close.
     */
    private void keepOffTheNet(Collection<VolleyballPlayer> humans) {
        for (VolleyballPlayer p : court) {
            if (humans.contains(p) || p.isAirborne()) continue;
            Brain brain = brain(p);
            boolean allowed = p.id == responsibleId || brain.state == AiState.BLOCKING;
            float limit = allowed ? 0.42f : 0.75f;
            float distance = Math.abs(p.position.x);
            float towardsNet = p.velocity.x * -side.sign();
            MoveIntent intent = world.intent(p);
            if (distance < limit + 0.35f && intent.direction.x * -side.sign() > 0f && (distance < limit || towardsNet > 1.5f)) {
                intent.direction.x = 0f;
            }
        }
    }

    // ------------------------------------------------------------------ our ball

    private void playOurBall(double now, RotationManager rotation, Collection<VolleyballPlayer> humans, int touches) {
        BallHandling handling = world.ballHandling();
        VolleyballPlayer setter = setter(rotation);
        int previousOwner = responsibleId;
        if (responsibleId < 0 || shouldReconsider(now, humans, touches, rotation)) {
            responsibleId = chooseResponsible(touches, setter, rotation, humans);
            responsibleChosenAt = now;
        }
        VolleyballPlayer quickHitter = touches == 2 ? quickHitter() : null;
        if (quickHitter != null) {
            responsibleId = quickHitter.id;
            attackerId = quickHitter.id;
        }
        VolleyballPlayer chosen = world.player(responsibleId);
        VolleyballPlayer inPath = quickHitter == null ? ballInPath(humans, touches == 0 ? 0.40f : 0.20f) : null;
        VolleyballPlayer dodging = null;
        if (inPath != null && inPath != chosen) {
            boolean personsBall = chosen != null && humans.contains(chosen);
            boolean spareSetter = touches == 0 && inPath == setter && chosen != null
                    && reach(chosen, forecast.timeToDescent, forecast.descentPoint) > 0.5f;
            boolean keepSetter = touches == 1 && chosen != null
                    && (chosen == setter && reach(chosen, forecast.timeToDescent, forecast.descentPoint) > 0.3f
                    || chosen.role == PlayerRole.LIBERO && backupSettingReach(chosen) > 0.33f);
            boolean keepHitter = touches == 2 && chosen != null && chosen.id == attackerId
                    && (canRunIntoSet(chosen, rotation) || chosen.isAirborne() && ballNearHand(chosen));
            if (personsBall || spareSetter || keepSetter || keepHitter) {
                dodging = inPath;
            } else {
                responsibleId = inPath.id;
                responsibleChosenAt = now;
                if (touches == 2) attackerId = inPath.id;
            }
        }
        if (touches == 1 && previousOwner != responsibleId) prepareSet(rotation, setter, humans);
        // A person setting chooses the set (or leaves it on Auto, which plays the team's combination);
        // the hitter that set goes to gets ready for it.
        VolleyballPlayer ballPlayer = world.player(responsibleId);
        if (touches == 1 && ballPlayer != null && humans.contains(ballPlayer)) {
            plannedSet = handling.setType(ballPlayer);
            // The hitters run for the person's set (route and tempo), not for the play the team called.
            if (!handling.isAutoSet(ballPlayer) || advisor.passQuality(side) == ShotAdvisor.PassQuality.POOR) {
                playCaller.followSet(plannedSet, playbook, advisor.hitters(ballPlayer, rotation));
            }
            VolleyballPlayer hitter = null;
            Combination play = playCaller.play();
            if (play != null) {
                for (Combination.Option o : play.options()) {
                    if (!o.dump() && o.set() == plannedSet) hitter = playCaller.runner(o.hitter());
                }
            }
            if (hitter == null) hitter = advisor.hitters(ballPlayer, rotation).get(plannedSet.lane);
            if (hitter == null) hitter = nearestHitterFor(plannedSet, ballPlayer, rotation, humans);
            attackerId = hitter == null ? -1 : hitter.id;
        }
        if (touches == 1) {
            runPlay(humans);
            readQuickSet(ballPlayer, rotation, humans);
        }
        VolleyballPlayer attacker = world.player(attackerId);
        for (VolleyballPlayer p : court) {
            if (humans.contains(p) || serving(p)) continue;
            if (p == dodging) {
                dodge(p);
                continue;
            }
            Brain brain = brain(p);
            if (p.id == responsibleId) {
                // Priority 3/4/5: the ball player.
                AutoPlayer.Behavior behavior = switch (touches) {
                    case 0 -> offBlock ? AutoPlayer.Behavior.SCRAMBLE
                            : world.ball().speed() > TacticalTargets.DIG_SPEED ? AutoPlayer.Behavior.DEFEND : AutoPlayer.Behavior.PASS;
                    // The libero sets with the forearms (an overhead set in the front zone would forbid the attack).
                    case 1 -> p.role == PlayerRole.LIBERO ? AutoPlayer.Behavior.PASS
                            : setterDump(p, rotation) ? AutoPlayer.Behavior.ATTACK
                            : p.role == PlayerRole.SETTER && (p.id == jumpSettingId || p.isAirborne())
                                    ? AutoPlayer.Behavior.JUMP_SET : AutoPlayer.Behavior.SET;
                    default -> p == quickHitter || canAttack(p, rotation) ? AutoPlayer.Behavior.ATTACK : AutoPlayer.Behavior.PASS;
                };
                // The shot is chosen in the air; the better the hitter's reading, the later (when the
                // block has formed): beginners at the take-off, professionals near the top of the jump.
                float decideAt = switch (difficulty) {
                    case BEGINNER -> 99f;
                    case INTERMEDIATE -> 2.0f;
                    case PROFESSIONAL -> 0.8f;
                };
                if (behavior == AutoPlayer.Behavior.ATTACK && p.isAirborne()
                        && (p.velocity.y < decideAt || ballNearHand(p))) chooseAttack(p);
                if (behavior == AutoPlayer.Behavior.ATTACK && !p.isAirborne() && attackDecidedFor != p.id) {
                    // A new approach: this attack's take-off timing error (seconds, + = late).
                    float spread = switch (difficulty) {
                        case BEGINNER -> 0.07f;
                        case INTERMEDIATE -> 0.04f;
                        case PROFESSIONAL -> 0.015f;
                    };
                    if (brain.motor.jumpTimingFor() != ballPhase) {
                        brain.motor.setJumpTiming((float) random.nextGaussian() * spread, ballPhase);
                    }
                }
                brain.motor.setBackRow(!rotation.isFrontRow(side, p.id));
                if (touches == 1 && p.role == PlayerRole.LIBERO && !p.isUpright()) world.intent(p).clear();
                else if (p == quickHitter) {
                    world.intent(p).clear();
                    if (!handling.isArmed(p)) handling.arm(p, ActionKind.PRIMARY, now);
                }
                else brain.motor.act(p, behavior, now);
                brain.state = switch (behavior) {
                    case SET, JUMP_SET -> AiState.SETTING;
                    case ATTACK -> p.isAirborne() ? AiState.ATTACKING : AiState.APPROACHING;
                    case SCRAMBLE -> handling.isArmed(p) ? AiState.PASSING : AiState.DEFENDING;
                    default -> handling.isArmed(p) ? AiState.PASSING : AiState.MOVING_TO_BALL;
                };
            } else if (touches == 0 && setter != null && setter.id == responsibleId && p.role == PlayerRole.LIBERO) {
                // When the setter has to dig, release towards the backup setting lane while the ball is incoming.
                TacticalTargets.backupSetterTarget(side, target);
                moveTo(p, target.x, target.z, false);
                if (horizontalDistance(p, target) < 2f) watchNet(p, false);
                brain.state = AiState.POSITIONING;
            } else if (touches == 0 && attackRebound && p != setter && p.id != ourAttackerId) {
                // Keep the short and deep cover in place while the called defender chases the deflection.
                coverHitter(p, world.player(ourAttackerId));
            } else if (p == setter && touches == 0) {
                // Priority 4: the setter runs to the setting spot while the pass is in the air.
                TacticalTargets.setterTarget(side, target);
                moveTo(p, target.x - side.sign() * 0.2f, target.z, false);
                brain.state = AiState.POSITIONING;
                setterRunningId = p.id;
            } else if (touches == 0 && receivingServe && waitForServeRead(p, now)) {
                brain.state = AiState.READY_TO_RECEIVE;
            } else if (touches == 0 && receivingServe && receptionLanes.containsKey(p.id)) {
                // A receiver whose lane the serve is not in holds the lane, ready.
                receptionSpot(p);
                brain.state = AiState.READY_TO_RECEIVE;
            } else if (touches == 1 && playCaller.runTarget(p, approachClock(p), target) != null) {
                // Priority 5: running the play - the start of the approach, then (at the tempo) the hitting spot.
                runRoute(p);
                tryEarlyAttackJump(p, ballPlayer, rotation, humans);
            } else if (touches <= 1 && isAttackOption(p, rotation, setter)) {
                // Attackers get ready for the set.
                approachStart(p, rotation, touches);
                brain.state = AiState.APPROACHING;
                if (touches == 1) tryEarlyAttackJump(p, ballPlayer, rotation, humans);
            } else if (touches == 2 && attacker != null && p.id != responsibleId && difficulty != Difficulty.BEGINNER) {
                // Decoys finish their fake; everyone else covers the hitter.
                if (!finishDecoy(p)) coverHitter(p, world.player(responsibleId));
            } else {
                basePosition(p, rotation, 0.85f);
                brain.state = AiState.POSITIONING;
            }
        }
        cancelOthers(responsibleId, humans);
    }

    /**
     * Serve reception: the receivers (outside hitters and libero, plus any human) split the
     * court into lanes from left to right, in the order they stand; each lane belongs to one
     * receiver.
     */
    private void assignReceptionLanes() {
        List<VolleyballPlayer> receivers = new java.util.ArrayList<>();
        for (VolleyballPlayer p : court) if (RolePlan.receivesServe(p) && p.isUpright()) receivers.add(p);
        if (receivers.isEmpty()) return;
        receivers.sort((a, b) -> Float.compare(lateral(a.position.z), lateral(b.position.z)));
        float width = 7.2f;
        // Lanes in proportion to the receivers' shares: the libero takes half as much again as the others.
        float total = 0f;
        for (VolleyballPlayer r : receivers) total += receptionShare(r);
        float left = -width / 2f;
        for (VolleyballPlayer r : receivers) {
            float lane = width * receptionShare(r) / total;
            receptionLanes.put(r.id, receivers.size() == 1 ? 0f : left + lane / 2f);
            left += lane;
        }
    }

    private static float receptionShare(VolleyballPlayer p) {
        return p.role == PlayerRole.LIBERO ? 1.5f : 1f;
    }

    private void receptionSpot(VolleyballPlayer p) {
        Float lane = receptionLanes.get(p.id);
        TacticalTargets.teamPoint(side, RECEPTION_DEPTH, lane == null ? 0f : lane, 0f, target);
        moveTo(p, target.x, target.z, true);
        world.intent(p).crouch = true;
    }

    /** Lateral position in the team's frame (+ = right, facing the net). */
    private float lateral(float z) {
        return -z * side.sign();
    }

    /**
     * The setter's dump, when the play calls it (a front-row setter, a good pass): tip the second
     * ball short into the free side, decided once per possession.
     */
    private boolean setterDump(VolleyballPlayer p, RotationManager rotation) {
        Combination.Option o = playCaller.chosen();
        if (o == null || !o.dump() || p.role != PlayerRole.SETTER || !rotation.isFrontRow(side, p.id)) return false;
        if (world.ballHandling().quickSet(p) != null) return false;
        if (!dumpDecided) {
            dumpDecided = true;
            // Short, to whichever front side of theirs has nobody behind the net.
            float bestGap = -1f;
            float depth = 1.8f;
            float lateral = -1.5f;
            for (int i = 0; i < 6; i++) {
                float d = 1.4f + random.nextFloat() * 1.4f;
                float l = -3f + random.nextFloat() * 5f;
                float gap = gapAt(side.opposite(), d, l, false);
                if (gap > bestGap) {
                    bestGap = gap;
                    depth = d;
                    lateral = l;
                }
            }
            TacticalTargets.teamPoint(side.opposite(), depth, lateral, 0f, target);
            world.ballHandling().setAim(p, target.x, target.z);
            world.ballHandling().setAttackType(p, AttackType.TIP);
            attackDecidedFor = p.id;
            attackerId = p.id;
        }
        return true;
    }

    /** Players on the bench (e.g. the middle replaced by the libero) just wait. */
    private void idleOnBench(Collection<VolleyballPlayer> humans) {
        for (VolleyballPlayer p : team.squad()) {
            if (p.isOnCourt() || humans.contains(p)) continue;
            world.intent(p).clear();
            brain(p).state = AiState.IDLE;
        }
    }

    /**
     * The ball is going to land clearly out (judged from its flight now, as a player reads it).
     * The better the team, the closer to the line it still lets a ball go; nobody gambles on a
     * ball the line could catch.
     */
    private boolean clearlyOut() {
        if (!forecast.lands) return false;
        float outBy = Math.max(Math.abs(forecast.landing.x) - CourtDimensions.HALF_LENGTH,
                Math.abs(forecast.landing.z) - CourtDimensions.HALF_WIDTH) - OUT_CONTACT;
        return outBy > 0.15f + difficulty.readError * 0.6f;
    }

    /** A ball landing within this of the line still touches it (the contact patch). */
    private static final float OUT_CONTACT = 0.025f;
    /** How often a power server curves the serve. */
    private static final float CURVE_SERVE_CHANCE = 0.25f;

    /** Only the ball player may have a ball action armed: earlier requests of others are withdrawn. */
    private void cancelOthers(int ballPlayerId, Collection<VolleyballPlayer> humans) {
        BallHandling handling = world.ballHandling();
        for (VolleyballPlayer p : court) {
            if (p.id != ballPlayerId && !humans.contains(p) && !serving(p) && handling.isArmed(p)) handling.cancel(p);
        }
    }

    /**
     * Re-evaluates who plays the ball (not for beginners): when the chosen player has clearly lost
     * it, or a teammate has become clearly better placed (the ball drifted, a deflection). The
     * better the team, the more often it re-reads the ball.
     */
    private boolean shouldReconsider(double now, Collection<VolleyballPlayer> humans, int touches, RotationManager rotation) {
        double interval = difficulty == Difficulty.PROFESSIONAL ? 0.12 : 0.3;
        if (difficulty == Difficulty.BEGINNER || now - responsibleChosenAt < interval) return false;
        VolleyballPlayer current = world.player(responsibleId);
        if (current == null || humans.contains(current)) return false;
        if (!current.isUpright() && current.bodyState() != BodyState.DIVING) return true;
        if (world.ballHandling().isArmed(current) || current.isAirborne()) return false;
        if (touches == 2 && current.id == attackerId && canRunIntoSet(current, rotation)) return false;
        responsibleChosenAt = now;
        float mine = reach(current, forecast.timeToDescent, forecast.descentPoint);
        if (mine < 0.15f) return true;
        // Someone else (not the setter on a second ball) is now much more likely to get there.
        int lastToucher = world.ballHandling().lastToucherId();
        for (VolleyballPlayer p : court) {
            if (p == current || p.id == lastToucher || humans.contains(p) || !p.isUpright()) continue;
            if (reach(p, forecast.timeToDescent, forecast.descentPoint) > mine + 0.35f) return true;
        }
        return false;
    }

    /**
     * The player (AI, upright, not the last toucher) the ball is about to hit, judged from its
     * current flight (gravity only): within half a metre of the body axis,
     * between the knees and the top of the head. Null if none.
     */
    private VolleyballPlayer ballInPath(Collection<VolleyballPlayer> humans) {
        return ballInPath(humans, 0.40f);
    }

    private VolleyballPlayer ballInPath(Collection<VolleyballPlayer> humans, float horizon) {
        Ball ball = world.ball();
        int lastToucher = world.ballHandling().lastToucherId();
        VolleyballPlayer hit = null;
        float first = Float.MAX_VALUE;
        for (VolleyballPlayer p : court) {
            if (p.id == lastToucher || humans.contains(p) || !p.isUpright() || p.isAirborne()) continue;
            for (float t = 0f; t <= horizon; t += 0.02f) {
                float x = ball.position.x + ball.velocity.x * t;
                float z = ball.position.z + ball.velocity.z * t;
                float y = ball.position.y + ball.velocity.y * t - 0.5f * PhysicsConstants.GRAVITY * t * t;
                float dx = x - p.position.x;
                float dz = z - p.position.z;
                if (dx * dx + dz * dz < 0.25f && y > p.position.y + 0.4f && y < p.position.y + p.height + 0.15f) {
                    if (t < first) {
                        first = t;
                        hit = p;
                    }
                    break;
                }
            }
        }
        return hit;
    }

    /** Steps sideways out of the ball's path (the ball is a teammate's: a person's). */
    private void dodge(VolleyballPlayer p) {
        Ball ball = world.ball();
        float vx = ball.velocity.x;
        float vz = ball.velocity.z;
        float len = (float) Math.sqrt(vx * vx + vz * vz);
        if (len < 0.1f) return;
        // Perpendicular to the flight, to whichever side the player already is.
        float px = -vz / len;
        float pz = vx / len;
        float away = (p.position.x - ball.position.x) * px + (p.position.z - ball.position.z) * pz >= 0f ? 1f : -1f;
        moveTo(p, p.position.x + px * away * 1.2f, clampZ(p.position.z + pz * away * 1.2f), true);
        world.ballHandling().cancel(p);
        brain(p).state = AiState.POSITIONING;
    }

    /** How much the reception lane counts against the real chance to reach the ball. */
    private float laneBonus() {
        return switch (difficulty) {
            case BEGINNER -> 0.4f;
            case INTERMEDIATE -> 0.25f;
            case PROFESSIONAL -> 0.15f;
        };
    }

    /**
     * Who plays the ball. The second ball is the setter's. A ball coming down in a human
     * teammate's area (in the reception: their lane) is the human's, unless the human cannot make
     * it and an AI player clearly can. In the reception the lane's receiver plays it. Otherwise
     * the player most likely to reach the ball in time plays it; penalties keep the setter off the
     * first ball and give the attack to front-row hitters; beginners judge it less well.
     */
    private int chooseResponsible(int touches, VolleyballPlayer setter, RotationManager rotation,
                                  Collection<VolleyballPlayer> humans) {
        int lastToucher = world.ballHandling().lastToucherId();
        if (touches == 1 && setter != null && setter.id != lastToucher && setter.isUpright() && setter.isOnCourt()
                && (!reboundPass || reach(setter, forecast.timeToDescent, forecast.descentPoint) > 0.23f)) return setter.id;
        // The setter took the first ball (or is down): the libero comes in and sets, as long as it can get there.
        VolleyballPlayer libero = team.libero();
        if (touches == 1 && libero != null && libero.isOnCourt() && libero.id != lastToucher
                && !humans.contains(libero) && backupSettingReach(libero) > LIBERO_SETS_FROM) {
            return libero.id;
        }
        float bestAi = 0f;
        for (VolleyballPlayer p : court) {
            if (p.id == lastToucher || humans.contains(p)) continue;
            bestAi = Math.max(bestAi, reach(p, forecast.timeToDescent, forecast.descentPoint));
        }
        VolleyballPlayer human = humanOwner(humans, lastToucher, bestAi);
        if (human != null) {
            if (touches == 2) attackerId = human.id;
            return human.id;
        }
        VolleyballPlayer calledHitter = touches == 2 && deliveredSet != null ? world.player(attackerId) : null;
        if (calledHitter != null && calledHitter.id != lastToucher && !humans.contains(calledHitter)
                && canRunIntoSet(calledHitter, rotation)) return calledHitter.id;
        if (touches == 0 && receivingServe && !receptionLanes.isEmpty()) {
            // The lane's receiver has priority, but a serve on the seam or right at a neighbour
            // goes to whoever can really play it (the lane only breaks ties).
            VolleyballPlayer owner = laneOwner();
            VolleyballPlayer bestReceiver = null;
            float bestScore = -Float.MAX_VALUE;
            for (VolleyballPlayer p : court) {
                if (p.id == lastToucher || humans.contains(p) || !receptionLanes.containsKey(p.id)) continue;
                // A ball coming down at a receiver's body is theirs: nobody else should run into them.
                float distance = horizontalDistance(p, forecast.descentPoint);
                float score = reach(p, forecast.timeToDescent, forecast.descentPoint) + (p == owner ? laneBonus() : 0f)
                        + (p.role == PlayerRole.LIBERO ? 0.12f : 0f)
                        - 0.12f * distance + (distance < BODY_AREA ? 0.5f : 0f);
                if (score > bestScore) {
                    bestScore = score;
                    bestReceiver = p;
                }
            }
            if (bestReceiver != null) return bestReceiver.id;
        }
        VolleyballPlayer best = null;
        float bestScore = -Float.MAX_VALUE;
        for (VolleyballPlayer p : court) {
            if (p.id == lastToucher) continue;
            // A human whose ball it is not is not expected to play it either.
            if (humans.contains(p)) continue;
            float chance = reach(p, forecast.timeToDescent, forecast.descentPoint);
            Brain brain = brains.get(p.id);
            if (brain != null) brain.reachChance = chance;
            // When several players can make it, the closest one plays it (no running into each other).
            float score = chance - 0.08f * horizontalDistance(p, forecast.descentPoint);
            // The setter is kept for the second touch: anyone else who can play the first ball takes it.
            // (Against a hard attack the setter defends its zone like everybody else.)
            boolean hardAttack = world.ball().speed() > TacticalTargets.DIG_SPEED;
            if (touches == 0 && p == setter && !hardAttack) score -= offBlock ? 0.4f : 0.8f;
            // The libero does the dirty work: every first ball it can reach is its ball.
            if (touches == 0 && p.role == PlayerRole.LIBERO) score += 0.3f;
            if (touches == 0 && receivingServe && !RolePlan.receivesServe(p)) score -= 0.6f;
            // A blocker at the net would have to turn and run back for a tip behind them.
            if (touches == 0 && !offBlock && Math.abs(p.position.x) < 1.2f
                    && Math.abs(forecast.descentPoint.x) > Math.abs(p.position.x) + 1.2f) score -= 0.3f;
            // Covering players expect the rebound; the hitter still in the air cannot move yet.
            if (touches == 0 && attackRebound) {
                Integer slot = coverSlots.get(p.id);
                boolean shortRebound = forecast.descentPoint.x * side.sign() < coverPoint.x * side.sign() + 2.7f;
                if (slot != null && chance > 0.28f) score += (shortRebound == (slot < 2) ? 0.18f : 0.07f);
                if (p.id == ourAttackerId && chance < 0.62f) score -= 0.28f;
            }
            // The setter took the first ball: the libero (or the right-side player) sets.
            if (touches == 1 && p.role == PlayerRole.LIBERO) score += 0.25f;
            if (touches == 0) score += PlayerAttributes.normalized(p.attributes.receiving) * 0.1f;
            if (touches == 2) {
                if (p.id == attackerId) score += 0.3f;
                if (!canAttack(p, rotation)) score -= 0.25f;
                if (p == setter) score -= 0.2f;
            }
            if (difficulty == Difficulty.BEGINNER) score += (float) random.nextGaussian() * 0.15f;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (touches == 2 && best != null) attackerId = best.id;
        return best == null ? -1 : best.id;
    }

    /** The hitter can still meet this set at attacking height after a run and a jump. */
    private boolean canRunIntoSet(VolleyballPlayer p, RotationManager rotation) {
        if (!p.isOnCourt() || !p.isUpright() || p.role == PlayerRole.LIBERO) return false;
        float jump = PlayerLocomotion.jumpHeight(p);
        hitRead.watchHeight = p.standingReach() - 0.02f + (p.isAirborne() ? p.position.y : jump);
        world.predictor().predict(world.ball(), 6f, hitRead);
        hitRead.watchHeight = Float.NaN;
        if (!hitRead.reachesWatchHeight || CourtDimensions.sideOf(hitRead.descentPoint.x) != side) return false;
        float depth = hitRead.descentPoint.x * side.sign();
        if (!rotation.isFrontRow(side, p.id) && depth < CourtDimensions.ATTACK_LINE_DISTANCE - 0.2f) return false;
        float dx = hitRead.descentPoint.x + side.sign() * 0.82f - p.position.x;
        float dz = hitRead.descentPoint.z - p.position.z;
        float distance = (float) Math.hypot(dx, dz);
        if (p.isAirborne()) return distance < 1.25f;
        float time = hitRead.timeToDescent - PlayerLocomotion.riseTime(jump);
        return distance <= 0.72f + PlayerLocomotion.maxRunSpeed(p) * Math.max(0f, time - 0.10f);
    }

    /**
     * The human teammate whose ball this is: it comes down within {@link #HUMAN_AREA} of them
     * (or in their reception lane, or they are the closest player), and they can still make it
     * or no AI player can clearly do better.
     */
    private VolleyballPlayer humanOwner(Collection<VolleyballPlayer> humans, int lastToucher, float bestAi) {
        Vector3 point = forecast.descentPoint;
        VolleyballPlayer lane = receivingServe ? laneOwner() : null;
        for (VolleyballPlayer h : humans) {
            if (h.side != side || !h.isOnCourt() || !h.isUpright() || h.id == lastToucher) continue;
            float d = horizontalDistance(h, point);
            boolean closest = true;
            for (VolleyballPlayer p : court) {
                if (p != h && p.id != lastToucher && horizontalDistance(p, point) < d) closest = false;
            }
            boolean mine = d < HUMAN_AREA || closest || lane == h;
            if (!mine) continue;
            if (reach(h, forecast.timeToDescent, point) < HUMAN_GIVES_UP && bestAi > AI_SURE) continue;
            return h;
        }
        return null;
    }

    /** The receiver whose lane the serve comes down in. */
    private VolleyballPlayer laneOwner() {
        float ballLateral = lateral(forecast.descentPoint.z);
        VolleyballPlayer owner = null;
        float best = Float.MAX_VALUE;
        for (Map.Entry<Integer, Float> lane : receptionLanes.entrySet()) {
            VolleyballPlayer p = world.player(lane.getKey());
            if (p == null || !p.isOnCourt() || !p.isUpright()) continue;
            float d = Math.abs(lane.getValue() - ballLateral);
            if (d < best) {
                best = d;
                owner = p;
            }
        }
        return owner;
    }

    private static float horizontalDistance(VolleyballPlayer p, Vector3 point) {
        float dx = p.position.x - point.x;
        float dz = p.position.z - point.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** Below this height a falling ball is left to the floor (no contact technique reaches it). */
    static final float LOW_BALL_HEIGHT = 0.25f;
    /** The libero takes the second ball from the setter when its chance of getting there is above this. */
    static final float LIBERO_SETS_FROM = 0.25f;
    /** How much further the libero gets to a ball than its run alone (it dives for the last metre). */
    static final float LIBERO_DIVE_REACH = 1.2f;

    /** Chance (0..1) of getting to the ball in time: reaction + start-up + run against the time left. */
    private float reach(VolleyballPlayer p, float timeAvailable, Vector3 point) {
        if (!p.isUpright()) return 0f;
        return reachAfterGettingUp(p, timeAvailable, point);
    }

    /** A high dig can give the libero time to finish getting up and still take the second ball. */
    private float backupSettingReach(VolleyballPlayer libero) {
        float time = libero.bodyStateTime();
        float gettingUp = switch (libero.bodyState()) {
            case UPRIGHT -> 0f;
            case DIVING -> PlayerLocomotion.DIVE_TIME - time + PlayerLocomotion.FLOOR_TIME + PlayerLocomotion.GET_UP_TIME;
            case ON_FLOOR -> PlayerLocomotion.FLOOR_TIME - time + PlayerLocomotion.GET_UP_TIME;
            case GETTING_UP -> PlayerLocomotion.GET_UP_TIME - time;
        };
        return reachAfterGettingUp(libero, forecast.timeToDescent - Math.max(0f, gettingUp), forecast.descentPoint);
    }

    private float reachAfterGettingUp(VolleyballPlayer p, float timeAvailable, Vector3 point) {
        float dx = point.x - p.position.x;
        float dz = point.z - p.position.z;
        float stretch = 0.6f + (p.role == PlayerRole.LIBERO ? LIBERO_DIVE_REACH : 0f);
        float distance = Math.max(0f, (float) Math.sqrt(dx * dx + dz * dz) - stretch);
        Brain brain = brains.get(p.id);
        float reaction = brain == null ? 0.1f : brain.motor.skill().reaction();
        float needed = reaction + 0.25f + distance / PlayerLocomotion.maxRunSpeed(p) + (p.isAirborne() ? 0.35f : 0f);
        return (float) (1.0 / (1.0 + Math.exp(-(timeAvailable - needed) / 0.2)));
    }

    private boolean canAttack(VolleyballPlayer p, RotationManager rotation) {
        if (p.role == PlayerRole.LIBERO) return false;
        if (rotation.isFrontRow(side, p.id)) return true;
        // A back-row player may attack if the take-off can be behind the attack line: the ball is
        // hit at about 3 m and the broad jump carries the hitter a little forward to it.
        hitRead.watchHeight = 3.0f;
        world.predictor().predict(world.ball(), 6f, hitRead);
        hitRead.watchHeight = Float.NaN;
        float depth = (hitRead.reachesWatchHeight ? hitRead.descentPoint.x : forecast.descentPoint.x) * side.sign();
        return depth > CourtDimensions.ATTACK_LINE_DISTANCE - 0.2f;
    }

    /**
     * The player closest to the spot of {@code set} who may attack it (front row for a front set,
     * back row for a back-row set; never the setter or the libero): the person set it there, so
     * somebody goes for it.
     */
    private VolleyballPlayer nearestHitterFor(SetType set, VolleyballPlayer setter, RotationManager rotation,
                                              Collection<VolleyballPlayer> humans) {
        TacticalTargets.teamPoint(side, set.depth, set.lateral, 0f, target);
        boolean backSet = set.lane == SetType.Lane.BACK_MIDDLE || set.lane == SetType.Lane.BACK_RIGHT;
        VolleyballPlayer best = null;
        float bestDistance = Float.MAX_VALUE;
        for (VolleyballPlayer p : court) {
            if (p == setter || p.role == PlayerRole.LIBERO || humans.contains(p)) continue;
            if (rotation.isFrontRow(side, p.id) == backSet) continue;
            float d = horizontalDistance(p, target);
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return best;
    }

    private boolean isAttackOption(VolleyballPlayer p, RotationManager rotation, VolleyballPlayer setter) {
        if (p == setter || p.role == PlayerRole.LIBERO) return false;
        if (p.id == attackerId) return true;
        if (!rotation.isFrontRow(side, p.id)) return false;
        // Beginners only use the outside hitter; others bring every front-row attacker.
        return difficulty != Difficulty.BEGINNER || p.role == PlayerRole.OUTSIDE_HITTER;
    }

    /**
     * Calls the play when the pass is made (see {@link PlayCaller}): beginners only know the high
     * ball; intermediate teams add the quick, the second tempo, zone 2, rarely a pipe or a decoy;
     * professional teams run the whole playbook (decoys, cross runs, tandems, bic, dumps and the
     * custom plays), favour what scores and avoid patterns. The coach's commands weigh in; a poor
     * pass leaves only the safe plays.
     */
    private void prepareSet(RotationManager rotation, VolleyballPlayer setter, Collection<VolleyballPlayer> humans) {
        VolleyballPlayer whoSets = world.player(responsibleId);
        VolleyballPlayer from = whoSets != null ? whoSets : setter;
        if (from == null) return;
        EnumMap<SetType.Lane, VolleyballPlayer> lanes = new EnumMap<>(advisor.hitters(from, rotation));
        boolean setterFront = from.role == PlayerRole.SETTER && rotation.isFrontRow(side, from.id);
        decoysJumped.clear();
        earlyJumpFor = -1;
        dumpDecided = false;
        playCaller.setSetter(from);
        playCaller.call(difficulty, playbook, tactics, lanes, memory.lastOwnPass(), setterFront, humans);
        playForCurrentPass = true;
        Combination.Option o = playCaller.chosen();
        if (o != null && !o.dump()) plannedSet = o.set();
        VolleyballPlayer hitter = playCaller.chosenHitter(from);
        attackerId = hitter == null ? -1 : hitter.id;
        if (whoSets != null && !humans.contains(whoSets) && o != null && !o.dump()) world.ballHandling().setSetType(whoSets, o.set());
        boolean playerSetting = whoSets != null && humans.contains(whoSets);
        BallHandling handling = world.ballHandling();
        if (whoSets != null && whoSets.role == PlayerRole.SETTER
                && (playerSetting ? handling.isAutoSet(whoSets) : difficulty.readsPlay && tactics.active(TeamCommand.Group.OFFENSE) == null)
                && advisor.passQuality(side) == ShotAdvisor.PassQuality.POOR) {
            SetType bailOut = advisor.recommendSet(whoSets, rotation);
            VolleyballPlayer receiver = lanes.get(bailOut.lane);
            if (advisor.setReadiness(whoSets, bailOut, receiver) != ShotAdvisor.SetReadiness.UNAVAILABLE) {
                plannedSet = bailOut;
                playCaller.followSet(bailOut, playbook, lanes);
                attackerId = receiver.id;
                handling.setSetType(whoSets, bailOut);
            }
        }
        if (whoSets != null && whoSets.role != PlayerRole.SETTER) {
            // Anyone but the setter (the libero after the setter's own pass) puts up a high ball to
            // a wing: a bump set has no tempo for a quick or a combination.
            SetType high = lanes.get(SetType.Lane.OUTSIDE) != null ? SetType.HIGH_OUTSIDE : SetType.BACK_SET;
            plannedSet = high;
            playCaller.followSet(high, playbook, lanes);
            VolleyballPlayer wing = lanes.get(high.lane);
            attackerId = wing == null ? -1 : wing.id;
            if (!humans.contains(whoSets)) world.ballHandling().setSetType(whoSets, high);
        }
    }

    /**
     * While the pass travels: checks that the setter can play the ball properly (otherwise the
     * play is cancelled for the emergency high ball) and, just before the setter's contact,
     * chooses the final option of the play.
     */
    private void runPlay(Collection<VolleyballPlayer> humans) {
        jumpSettingId = -1;
        VolleyballPlayer whoSets = world.player(responsibleId);
        if (whoSets == null || playCaller.play() == null) return;
        setterRead.watchHeight = TacticalTargets.SETTER_HEIGHT;
        world.predictor().predict(world.ball(), 6f, setterRead);
        setterRead.watchHeight = Float.NaN;
        if (!setterRead.reachesWatchHeight) return;
        float groundTime = setterRead.timeToDescent;
        timeToSet = groundTime;
        if (!humans.contains(whoSets) && whoSets.role == PlayerRole.SETTER && difficulty.meaningfulBlock) {
            PassRating pass = memory.lastOwnPass();
            Combination.Option option = playCaller.chosen();
            if (option != null && !option.dump() && (pass == PassRating.PERFECT || pass == PassRating.GOOD
                    && (option.set().fast() || difficulty.readsPlay))) {
                float highTime = brain(whoSets).motor.jumpSetContactTime(whoSets);
                if (Float.isFinite(highTime)) {
                    jumpSettingId = whoSets.id;
                    timeToSet = highTime;
                }
            }
        }
        float placement = (float) Math.hypot(whoSets.position.x - setterRead.descentPoint.x, whoSets.position.z - setterRead.descentPoint.z);
        boolean fast = difficulty.readsPlay && plannedSet.fast();
        boolean armed = world.ballHandling().isArmed(whoSets);
        // A setter already under the ball doesn't need the movement and reaction time of a chase.
        if (!humans.contains(whoSets) && !(fast && (placement < 0.82f || jumpSettingId == whoSets.id || armed))) {
            playCaller.checkSetter(reach(whoSets, groundTime, setterRead.descentPoint));
        }
        if (playCaller.decided() || !(groundTime < (fast ? 0.20f : 0.30f) || armed || whoSets.isAirborne())) return;
        float quality = Math.max(0f, 1f - placement / 1.6f);
        boolean readBlock = difficulty.readsPlay || tactics.is(TeamCommand.VS_SINGLE_BLOCK);
        Combination.Option o = playCaller.decide(whoSets, quality, readBlock, opponents());
        if (o == null || humans.contains(whoSets)) return;
        if (!o.dump()) {
            world.ballHandling().setSetType(whoSets, o.set());
            plannedSet = o.set();
        }
        VolleyballPlayer hitter = playCaller.chosenHitter(whoSets);
        if (hitter != null) attackerId = hitter.id;
    }

    private void readQuickSet(VolleyballPlayer setter, RotationManager rotation, Collection<VolleyballPlayer> humans) {
        BallHandling handling = world.ballHandling();
        int setterId = setter == null ? -1 : setter.id;
        if (quickSetterId >= 0 && quickSetterId != setterId) handling.setQuickSet(world.player(quickSetterId), null);
        quickSetterId = setterId;
        quickLead = Float.NaN;
        if (setter == null) return;
        handling.setQuickSet(setter, null);
        boolean personSetting = humans.contains(setter);
        Ball ball = world.ball();
        PassRating pass = memory.lastOwnPass();
        if (setter.role != PlayerRole.SETTER || !setter.isUpright() || !setter.isOnCourt()
                || (personSetting ? handling.isAutoSet(setter) || !plannedSet.fast() : !difficulty.meaningfulBlock)
                || pass == PassRating.LIMITED || pass == PassRating.EMERGENCY) return;

        quickLead = quickWindow.passLead(setter, ball, world.predictor(), quickRelease);
        if (!Float.isFinite(quickLead)) return;
        float netTop = world.court().net().topHeight();
        VolleyballPlayer called = world.player(attackerId);
        if (plannedSet.fast() && viableQuickHitter(called, setter, plannedSet, rotation)
                && Float.isFinite(quickWindow.read(called, quickRelease, netTop, quickLead, quickPoint))) {
            boolean contested = blockAlreadyUp(rotation, quickPoint.z);
            if (personSetting || !contested) {
                handling.setQuickSet(setter, new BallHandling.QuickSet(called, plannedSet, contested));
                return;
            }
        }
        // A bot may turn an early jump into a real first tempo if the opposing block stayed
        // elsewhere. A manual set stays on the chosen lane.
        if (personSetting) return;
        var lanes = advisor.hitters(setter, rotation);
        TeamCommand offense = tactics.active(TeamCommand.Group.OFFENSE);
        boolean useDecoy = offense == TeamCommand.QUICK_ATTACK || offense == null && difficulty.readsPlay;
        BallHandling.QuickSet option = null;
        float best = Float.POSITIVE_INFINITY;
        for (SetType type : SetType.values()) {
            if (!type.fast()) continue;
            VolleyballPlayer hitter = lanes.get(type.lane);
            if (!viableQuickHitter(hitter, setter, type, rotation)
                    || !(humans.contains(hitter) || useDecoy && (decoysJumped.contains(hitter.id) || earlyJumpFor == hitter.id))) continue;
            float flight = quickWindow.read(hitter, quickRelease, netTop, quickLead, quickPoint);
            if (!Float.isFinite(flight) || blockAlreadyUp(rotation, quickPoint.z)) continue;
            type.target(side, quickLane);
            float score = flight + 0.16f * Math.abs(quickPoint.z - quickLane.z);
            if (score < best) {
                best = score;
                option = new BallHandling.QuickSet(hitter, type, false);
            }
        }
        handling.setQuickSet(setter, option);
    }

    private boolean blockAlreadyUp(RotationManager rotation, float z) {
        for (VolleyballPlayer blocker : opponents()) {
            if (blocker.isAirborne() && Math.abs(blocker.position.x) < 1.15f && Math.abs(blocker.position.z - z) < 0.85f
                    && rotation.isFrontRow(side.opposite(), blocker.id)) return true;
        }
        return false;
    }

    private boolean viableQuickHitter(VolleyballPlayer hitter, VolleyballPlayer setter, SetType type, RotationManager rotation) {
        if (hitter == null || hitter == setter || hitter.role == PlayerRole.LIBERO || !hitter.isOnCourt()
                || !hitter.isUpright() || !hitter.isAirborne() || hitter.id == world.ballHandling().lastToucherId()) return false;
        boolean front = rotation.isFrontRow(side, hitter.id);
        if (front == (type.depth >= CourtDimensions.ATTACK_LINE_DISTANCE)
                || !front && hitter.takeoffPosition.x * side.sign() <= CourtDimensions.ATTACK_LINE_DISTANCE + 0.03f) return false;
        type.target(side, quickLane);
        return horizontalDistance(hitter, quickLane) <= 1.45f;
    }

    private void tryEarlyAttackJump(VolleyballPlayer hitter, VolleyballPlayer setter, RotationManager rotation,
                                    Collection<VolleyballPlayer> humans) {
        float lead = quickLead;
        if (setter != null && (jumpSettingId == setter.id || !Float.isFinite(lead) && setterRead.reachesWatchHeight
                && horizontalDistance(setter, setterRead.descentPoint) < 0.9f)) lead = timeToSet;
        boolean personSetting = setter != null && humans.contains(setter);
        if (setter == null || hitter.id != attackerId || earlyJumpFor >= 0 || !plannedSet.fast()
                || !difficulty.meaningfulBlock || !personSetting && !difficulty.readsPlay && !tactics.is(TeamCommand.QUICK_ATTACK)
                || !Float.isFinite(lead) || lead < 0.12f || lead > 0.24f
                || hitter.isAirborne() || !hitter.isUpright() || hitter.recoveryTime() > 0f
                || personSetting && !world.ballHandling().isArmed(setter)) return;
        boolean front = rotation.isFrontRow(side, hitter.id);
        float depth = hitter.position.x * side.sign();
        float airTime = 2f * PlayerLocomotion.riseTime(PlayerLocomotion.jumpHeight(hitter));
        if (front == (plannedSet.depth >= CourtDimensions.ATTACK_LINE_DISTANCE)
                || !front && depth < CourtDimensions.ATTACK_LINE_DISTANCE + 0.12f
                || depth + hitter.velocity.x * side.sign() * PlayerLocomotion.TAKEOFF_CARRY * airTime < 0.52f) return;
        plannedSet.target(side, quickLane);
        if (horizontalDistance(hitter, quickLane) > 1.34f) return;
        MoveIntent intent = world.intent(hitter);
        intent.facingYaw = VolleyballPlayer.facingNetYaw(side);
        intent.jump = true;
        intent.jumpHeightFactor = 0.76f;
        hitter.setActionPose(ActionPose.QUICK_READY, 0.48f);
        earlyJumpFor = hitter.id;
        brain(hitter).state = AiState.JUMPING;
    }

    private float approachClock(VolleyballPlayer hitter) {
        return difficulty.readsPlay && hitter.id == attackerId && plannedSet.fast() ? timeToSet - 0.12f : timeToSet;
    }

    /** A runner of the play heads for the spot in {@link #target}; a decoy jumps as the set is released. */
    private void runRoute(VolleyballPlayer p) {
        Brain brain = brain(p);
        // Hitting spots stop a metre off the net: the run-up carries on in the jump.
        if (target.x * side.sign() < 1.0f) target.x = side.sign() * 1.0f;
        moveTo(p, target.x, target.z, false);
        brakeAtNet(p);
        brain.state = AiState.APPROACHING;
        Tempo tempo = playCaller.tempoOf(p);
        if (!playCaller.isDecoy(p) || tempo == null || tempo == Tempo.THIRD) return;
        if (p.isAirborne() || decoysJumped.contains(p.id) || !p.isUpright()) return;
        float rise = PlayerLocomotion.riseTime(PlayerLocomotion.jumpHeight(p));
        float jumpAt = tempo == Tempo.FIRST ? rise * 0.7f : 0.05f;
        if (timeToSet < jumpAt && Math.hypot(p.position.x - target.x, p.position.z - target.z) < 1.2f && !runningAtNet(p)) decoyJump(p);
    }

    /** Near the net a runner stops running towards it (a jump with that momentum ends in the net). */
    private void brakeAtNet(VolleyballPlayer p) {
        MoveIntent intent = world.intent(p);
        if (Math.abs(p.position.x) < 1.3f && intent.direction.x * -side.sign() > 0f) intent.direction.x = 0f;
    }

    /** A jump now would carry the player into the net (the run-up keeps going in the air). */
    private boolean runningAtNet(VolleyballPlayer p) {
        float towards = p.velocity.x * -side.sign();
        if (towards <= 0.3f) return false;
        float airTime = 2f * PlayerLocomotion.riseTime(PlayerLocomotion.jumpHeight(p));
        return Math.abs(p.position.x) - towards * airTime < 0.6f;
    }

    /**
     * After the set: a decoy of the play that has not jumped yet (a second-tempo fake, or a
     * runner whose option was not chosen) jumps now if it is at its spot. Returns true while the
     * decoy is busy (not covering).
     */
    private boolean finishDecoy(VolleyballPlayer p) {
        if (!playCaller.isDecoy(p) || playCaller.play() == null) return false;
        if (p.isAirborne()) return decoysJumped.contains(p.id);
        Tempo tempo = playCaller.tempoOf(p);
        if (decoysJumped.contains(p.id) || tempo == null || tempo == Tempo.THIRD || !p.isUpright()) return false;
        if (playCaller.runTarget(p, -1f, target) == null) return false;
        if (target.x * side.sign() < 1.0f) target.x = side.sign() * 1.0f;
        if (Math.hypot(p.position.x - target.x, p.position.z - target.z) > 1.2f || runningAtNet(p)) return false;
        decoyJump(p);
        return true;
    }

    private void decoyJump(VolleyballPlayer p) {
        world.intent(p).jump = true;
        world.intent(p).jumpHeightFactor = 0.85f;
        p.setActionPose(ActionPose.QUICK_READY, 0.42f);
        decoysJumped.add(p.id);
        brain(p).state = AiState.JUMPING;
    }

    /** The front-row player of {@code side} with the role (other than {@code setter}), or null. */
    static VolleyballPlayer frontRole(List<VolleyballPlayer> court, CourtSide side, RotationManager rotation,
                                      PlayerRole role, VolleyballPlayer setter) {
        for (VolleyballPlayer p : court) {
            if (p.role == role && p != setter && rotation.isFrontRow(side, p.id)) return p;
        }
        return null;
    }

    /**
     * The shot, chosen in the air when the block can be seen. Every hitter has a style (power,
     * line, cut, tip, roll); the situation reweighs it:
     * <ul>
     *   <li>beginners mostly hit hard to the middle of the court, now and then a tip;</li>
     *   <li>intermediate hitters lean towards what the situation calls for (see {@link ShotAdvisor})
     *       and hit away from the block;</li>
     *   <li>professionals add the cut and the tool, tip into an empty short zone, and aim at the gap
     *       in the defence: the spot farthest from every defender and out of the block's shadow.</li>
     * </ul>
     * A poor pass or a low contact means a safe roll shot or tip. Risk decides how close to the
     * lines the ball is aimed.
     */
    private void chooseAttack(VolleyballPlayer a) {
        if (attackDecidedFor == a.id) return;
        attackDecidedFor = a.id;
        BallHandling handling = world.ballHandling();
        CourtSide opponent = side.opposite();
        Brain hitter = brain(a);
        Style style = hitter.style;
        float margin = switch (difficulty) {
            case BEGINNER -> 1.3f;
            case INTERMEDIATE -> 0.95f;
            case PROFESSIONAL -> 1.0f - 0.5f * risk - daring(a, style) * 0.4f;
        };
        // The hothead goes for the lines at every level.
        if (difficulty != Difficulty.PROFESSIONAL && a.has(PlayerTrait.HOTHEAD)) margin -= 0.3f;
        margin = Math.max(0.35f, margin);
        float maxLateral = CourtDimensions.HALF_WIDTH - margin;
        float maxDepth = CourtDimensions.HALF_LENGTH - margin;
        float hitterLateral = -a.position.z * opponent.sign();
        int blockers = 0;
        int raisedBlockers = 0;
        float blockLateral = 0f;
        for (VolleyballPlayer o : opponents()) {
            if (Math.abs(o.position.x) < ShotAdvisor.BLOCK_NET_DISTANCE && Math.abs(o.position.z - a.position.z) < ShotAdvisor.BLOCK_LATERAL) {
                blockers++;
                if (o.isBlocking()) raisedBlockers++;
                blockLateral += -o.position.z * opponent.sign();
            }
        }
        if (blockers > 0) blockLateral /= blockers;
        PassRating pass = memory.lastOwnPass();
        boolean cautious = pass == PassRating.EMERGENCY || (pass == PassRating.LIMITED && difficulty != Difficulty.PROFESSIONAL)
                || (tactics.is(TeamCommand.CAUTIOUS) && pass != PassRating.PERFECT);
        if (tactics.is(TeamCommand.CAUTIOUS)) {
            margin += 0.3f;
            maxLateral = CourtDimensions.HALF_WIDTH - margin;
            maxDepth = CourtDimensions.HALF_LENGTH - margin;
        }
        AttackType advised = advisor.recommendAttack(a, false);
        boolean atPin = Math.abs(a.position.z) > CourtDimensions.HALF_WIDTH - 1.2f;

        shotWeights.clear();
        switch (difficulty) {
            case BEGINNER -> {
                shotWeights.put(AttackType.POWER, 1f);
                shotWeights.put(AttackType.TIP, 0.13f);
                shotWeights.put(AttackType.ROLL_SHOT, 0.14f);
            }
            case INTERMEDIATE -> {
                shotWeights.put(AttackType.POWER, style.power());
                shotWeights.put(AttackType.TIP, style.tip() * 0.4f);
                shotWeights.put(AttackType.ROLL_SHOT, style.roll() * 0.65f);
                shotWeights.put(AttackType.CUT, style.cut() * 0.42f);
                if (blockers >= 1) shotWeights.put(AttackType.FEINT, style.tip() * 0.25f);
                shotWeights.merge(advised, advised == AttackType.TIP ? 0.5f : 1.2f, Float::sum);
            }
            case PROFESSIONAL -> {
                shotWeights.put(AttackType.POWER, style.power() * 1.2f);
                shotWeights.put(AttackType.TIP, style.tip() * (blockers >= 2 ? 0.5f : 0.25f));
                shotWeights.put(AttackType.ROLL_SHOT, style.roll() * 0.4f);
                shotWeights.put(AttackType.CUT, style.cut() * (blockers >= 1 ? 0.4f : 0.15f));
                if (raisedBlockers >= 1 && atPin) shotWeights.put(AttackType.TOOL, 0.35f);
                // A block in front: fake the swing and push it behind the block, or wipe it off a tall
                // block's fingertips (from the middle of the net, where a tool is no option).
                if (blockers >= 1) shotWeights.put(AttackType.FEINT, style.tip() * 0.52f);
                if (raisedBlockers >= 1) shotWeights.put(AttackType.OFF_THE_TOP, atPin ? 0.18f : 0.45f);
                shotWeights.merge(advised, advised == AttackType.TIP ? 0.5f : 1.2f, Float::sum);
                // Against defenders who read this hitter's tips, tip less.
                if (memory.habit(a.id) != null && memory.habit(a.id).tipShare() > 0.35f) shotWeights.computeIfPresent(AttackType.TIP, (k, v) -> v * 0.4f);
            }
        }
        // Against a single block the coach wants it hard.
        if (tactics.is(TeamCommand.VS_SINGLE_BLOCK) && blockers <= 1) shotWeights.merge(AttackType.POWER, 1.5f, Float::sum);
        if (a.has(PlayerTrait.BLOCK_OUT) && raisedBlockers >= 1 && difficulty != Difficulty.BEGINNER) {
            shotWeights.merge(atPin ? AttackType.TOOL : AttackType.OFF_THE_TOP, 1.6f, Float::sum);
        }
        if (raisedBlockers == 0) {
            shotWeights.remove(AttackType.TOOL);
            shotWeights.remove(AttackType.OFF_THE_TOP);
        }
        if (hitter.lastAttack != null && difficulty != Difficulty.BEGINNER) {
            shotWeights.computeIfPresent(hitter.lastAttack, (k, v) -> v * 0.64f);
        }
        shotWeights.computeIfPresent(AttackType.POWER, (k, v) -> v * a.fatigueFactor(0.60f));
        AttackType type = cautious ? (random.nextFloat() < 0.5f ? AttackType.ROLL_SHOT : AttackType.TIP) : WeightedPick.pick(random, shotWeights, AttackType.POWER);
        if (advised == AttackType.TIP && cautious) type = AttackType.TIP;

        float depth;
        float lateral;
        switch (type) {
            case TIP, FEINT -> {
                // Just behind the block, where nobody stands (professionals look; others guess).
                // The feint comes out of a full swing, so it goes a little deeper than a tip.
                depth = (type == AttackType.FEINT ? 2.2f : 1.6f) + random.nextFloat() * 1.0f;
                lateral = hitterLateral + (random.nextFloat() - 0.5f) * 2f;
                if (difficulty.readsPlay) {
                    float bestGap = -1f;
                    for (int i = 0; i < 6; i++) {
                        float d = 1.4f + random.nextFloat() * 1.4f;
                        float l = blockLateral + (random.nextFloat() - 0.5f) * 3.5f;
                        float gap = gapAt(opponent, d, l, false);
                        if (gap > bestGap) {
                            bestGap = gap;
                            depth = d;
                            lateral = l;
                        }
                    }
                }
            }
            case CUT -> {
                // The sharp angle: short and wide on the far side from the hitter.
                float away = -Math.signum(hitterLateral == 0f ? (random.nextBoolean() ? 1f : -1f) : hitterLateral);
                depth = 2.6f + random.nextFloat() * 1.6f;
                lateral = away * maxLateral;
            }
            case ROLL_SHOT -> {
                depth = cautious ? 6.5f : 3.8f + random.nextFloat() * 2f;
                lateral = cautious ? 0f : hitterLateral * 0.5f;
                if (difficulty.readsPlay && !cautious) {
                    float far = Math.copySign(maxLateral * 0.72f, -hitterLateral);
                    for (int i = -1; i <= 1; i++) {
                        float lane = i * far;
                        if (gapAt(opponent, depth, lane, true) > gapAt(opponent, depth, lateral, true)) lateral = lane;
                    }
                }
            }
            case TOOL -> {
                // The mechanic aims at the block's outside hand; this is only the fallback.
                depth = maxDepth;
                lateral = Math.signum(hitterLateral == 0f ? 1f : hitterLateral) * (CourtDimensions.HALF_WIDTH + 0.8f);
            }
            case OFF_THE_TOP -> {
                // The mechanic aims through the block's fingertips; this is only the fallback.
                depth = CourtDimensions.HALF_LENGTH + 2f;
                lateral = blockers > 0 ? blockLateral : hitterLateral;
            }
            default -> {
                if (cautious) {
                    depth = 7f;
                    lateral = 0f;
                } else if (difficulty == Difficulty.BEGINNER) {
                    depth = 6.5f;
                    lateral = random.nextBoolean() ? -Math.signum(hitterLateral == 0f ? 1f : hitterLateral) * 2f : 0f;
                } else {
                    boolean lineClosed = blockers > 0 && Math.abs(blockLateral) > Math.abs(hitterLateral) - 0.2f;
                    boolean cross = blockers == 0 ? random.nextFloat() > style.line() : lineClosed;
                    if (cross) {
                        depth = 5.5f + random.nextFloat() * 1.5f;
                        lateral = -Math.signum(hitterLateral == 0f ? (random.nextBoolean() ? 1f : -1f) : hitterLateral) * maxLateral;
                    } else {
                        depth = maxDepth - random.nextFloat() * 0.6f;
                        lateral = Math.abs(hitterLateral) < 1f ? (random.nextBoolean() ? 1f : -1f) * maxLateral * 0.6f : hitterLateral;
                    }
                    // Professionals check the spot: a defender standing right there means the gap instead.
                    if (difficulty.readsPlay && gapAt(opponent, depth, lateral, true) < 1.3f) {
                        float[] best = bestGap(opponent, a, blockers, blockLateral, maxLateral, maxDepth, style);
                        depth = best[0];
                        lateral = best[1];
                    }
                }
            }
        }
        if (type != AttackType.TOOL && type != AttackType.OFF_THE_TOP) {
            lateral = Math.max(-maxLateral, Math.min(maxLateral, lateral));
            depth = Math.max(1.4f, Math.min(maxDepth, depth));
        }
        TacticalTargets.teamPoint(opponent, depth, lateral, 0f, target);
        handling.setAim(a, target.x, target.z);
        handling.setAttackType(a, type);
        hitter.lastAttack = type;
    }

    /**
     * The best spot to hit (professional): of a dozen candidate spots on the opponent's court, the
     * one farthest from every defender (blockers excluded), not behind the block (its shadow from
     * the hitter), preferring the hitter's favourite line or cross; a little randomness among the
     * best keeps it unpredictable.
     */
    private float[] bestGap(CourtSide opponent, VolleyballPlayer a, int blockers, float blockLateral, float maxLateral,
                            float maxDepth, Style style) {
        float hitterLateral = -a.position.z * opponent.sign();
        float bestScore = -Float.MAX_VALUE;
        float[] best = {maxDepth - 0.5f, 0f};
        for (int i = 0; i < 14; i++) {
            float d = 4.5f + random.nextFloat() * (maxDepth - 4.5f);
            float l = (random.nextFloat() * 2f - 1f) * maxLateral;
            float score = gapAt(opponent, d, l, true);
            if (blockers > 0) {
                // The block's shadow: spots in line with the block from the hitter are covered by it.
                float atNet = hitterLateral + (l - hitterLateral) * (0.6f / (d + 0.6f));
                if (Math.abs(atNet - blockLateral) < 0.45f * blockers + 0.3f) score -= 2f;
            }
            boolean line = Math.signum(l) == Math.signum(hitterLateral) && Math.abs(l) > 2f;
            score += (line ? style.line() : 1f - style.line()) * 0.8f + random.nextFloat() * 0.5f;
            if (score > bestScore) {
                bestScore = score;
                best[0] = d;
                best[1] = l;
            }
        }
        return best;
    }

    /** Distance (m) from a spot in the opponent's frame to the nearest defender (optionally ignoring players at the net). */
    private float gapAt(CourtSide opponent, float depth, float lateral, boolean ignoreNet) {
        TacticalTargets.teamPoint(opponent, depth, lateral, 0f, target);
        float nearest = 9f;
        for (VolleyballPlayer o : opponents()) {
            if (ignoreNet && Math.abs(o.position.x) < 1.4f) continue;
            nearest = Math.min(nearest, (float) Math.hypot(o.position.x - target.x, o.position.z - target.z));
        }
        return nearest;
    }

    /** The ball is about to reach the hitter's hand (the last moment to choose the shot). */
    private boolean ballNearHand(VolleyballPlayer p) {
        ContactZone.idealPoint(p, ContactType.ATTACK, null, target);
        return target.dst(world.ball().position) < 1.4f;
    }

    /** Back off from the net to the start of the approach for the expected set. */
    private void approachStart(VolleyballPlayer p, RotationManager rotation, int touches) {
        boolean front = rotation.isFrontRow(side, p.id);
        float lateral = front ? RolePlan.approachLateral(p) : 0f;
        // The planned hitter starts the approach towards the planned set's spot (a slide, the D from zone 1).
        if (p.id == attackerId && touches == 1) lateral = plannedSet.lateral * 0.85f;
        // Back-row hitters wait behind the attack line for the pipe; the middle comes closer for the quick.
        boolean middle = p.role == PlayerRole.MIDDLE_BLOCKER;
        float depth = front ? (middle && touches == 1 ? 2.4f : 3.6f) : 5.6f;
        TacticalTargets.teamPoint(side, depth, lateral, 0f, target);
        moveTo(p, target.x, target.z, true);
        // Open space for the approach while still facing the play.
        if (target.x * side.sign() > p.position.x * side.sign() + 0.15f) {
            watchNet(p, false);
        }
    }

    /**
     * Cover of the own hitter for balls coming back off the block. Two players cover the short
     * rebounds from the sides and three stay spread deeper. Every coverer gets its own slot,
     * kept for the whole attack, so nobody covers the same spot as a teammate.
     */
    private void coverHitter(VolleyballPlayer p, VolleyballPlayer hitter) {
        if (hitter == null) {
            brain(p).state = AiState.POSITIONING;
            return;
        }
        if (brain(p).state == AiState.JUMPING && p.isAirborne()) return;
        assignCover(hitter);
        Integer slot = coverSlots.get(p.id);
        if (slot == null) {
            brain(p).state = AiState.POSITIONING;
            return;
        }
        // Professionals close in tight; the others stay a little looser.
        boolean pro = difficulty == Difficulty.PROFESSIONAL;
        coverSpot(slot, pro ? 0f : 0.35f, target);
        moveTo(p, target.x, target.z, true);
        watchNet(p, horizontalDistance(p, target) < 0.9f);
        brain(p).state = AiState.COVERING;
    }

    /** Cover slots: depth behind the hitter and lateral offset (team frame), close cup first. */
    static final float[][] COVER_SLOTS = {{1.18f, -1.55f}, {1.18f, 1.55f}, {2.7f, 0f}, {4.2f, -2.15f}, {4.2f, 2.15f}};
    /** Coverer id → slot index, for the attack of {@link #coverFor}. */
    private final Map<Integer, Integer> coverSlots = new HashMap<>();
    private int coverFor = -1;
    private int coverPhase = Integer.MIN_VALUE;
    private final Vector3 coverPoint = new Vector3();

    /** Cover the expected touch, so the cup does not drift forward while the hitter runs. */
    private void coverSpot(int slot, float looser, Vector3 out) {
        float hitterDepth = Math.max(0.8f, coverPoint.x * side.sign());
        float hitterLateral = lateral(coverPoint.z);
        float depth = hitterDepth + COVER_SLOTS[slot][0] + looser;
        float lat = hitterLateral + COVER_SLOTS[slot][1];
        if (slot < 2 && Math.abs(hitterLateral) > 2.65f) {
            // At the pin, stagger the short coverers inside the court and away from the approach.
            float inward = -Math.signum(hitterLateral);
            lat = hitterLateral + inward * (slot == 0 ? 1.45f : 2.95f);
            depth += slot == 0 ? 0f : 0.85f;
        } else if (slot >= 2) {
            // The back line stays spread across the court even when the strike is at a pin.
            lat = hitterLateral * 0.28f + COVER_SLOTS[slot][1];
            if (slot == 2 && Math.abs(lat - hitterLateral) < 1.25f) depth = Math.max(depth, hitterDepth + 3.45f);
        }
        TacticalTargets.teamPoint(side, Math.min(depth, CourtDimensions.HALF_LENGTH - 0.5f),
                Math.max(-CourtDimensions.HALF_WIDTH + 0.4f, Math.min(CourtDimensions.HALF_WIDTH - 0.4f, lat)), 0f, out);
    }

    /**
     * Shares the cover slots out once per attack: the closest coverer to the close cup first
     * (cheapest total run, greedy), people included so nobody is sent to a person's spot.
     */
    private void assignCover(VolleyballPlayer hitter) {
        if (coverFor == hitter.id && (ballPhase == coverPhase
                || ourAttackerId == hitter.id && (holdingCover(world.time()) || attackRebound))) return;
        coverFor = hitter.id;
        coverPhase = ballPhase;
        coverPoint.set(hitter.position);
        if (world.ballHandling().teamTouches(side) == 2) {
            hitRead.watchHeight = 3.05f;
            world.predictor().predict(world.ball(), 6f, hitRead);
            hitRead.watchHeight = Float.NaN;
            if (hitRead.reachesWatchHeight && CourtDimensions.sideOf(hitRead.descentPoint.x) == side) {
                coverPoint.set(hitRead.descentPoint);
            }
        }
        coverSlots.clear();
        List<VolleyballPlayer> coverers = new java.util.ArrayList<>();
        for (VolleyballPlayer o : court) if (o != hitter) coverers.add(o);
        int n = Math.min(coverers.size(), COVER_SLOTS.length);
        if (n == 0) return;
        // Cost of each player to each slot: the squared run (no one gets sent across the court).
        float[][] cost = new float[n][COVER_SLOTS.length];
        Vector3 slotSpot = new Vector3();
        for (int s = 0; s < COVER_SLOTS.length; s++) {
            coverSpot(s, 0f, slotSpot);
            for (int i = 0; i < n; i++) {
                float d = horizontalDistance(coverers.get(i), slotSpot);
                VolleyballPlayer coverer = coverers.get(i);
                cost[i][s] = d * d + (s < 2 && (coverer.isAirborne() || !coverer.isUpright()) ? 9f : 0f);
            }
        }
        // Few players and slots: try every assignment, keep the one with the least running.
        int[] best = new int[n];
        int[] current = new int[n];
        float[] bestCost = {Float.MAX_VALUE};
        assignSlots(cost, 0, new boolean[COVER_SLOTS.length], current, 0f, best, bestCost);
        for (int i = 0; i < n; i++) coverSlots.put(coverers.get(i).id, best[i]);
    }

    private static void assignSlots(float[][] cost, int i, boolean[] used, int[] current, float sum, int[] best, float[] bestCost) {
        if (sum >= bestCost[0]) return;
        if (i == current.length) {
            bestCost[0] = sum;
            System.arraycopy(current, 0, best, 0, current.length);
            return;
        }
        for (int s = 0; s < used.length; s++) {
            if (used[s]) continue;
            used[s] = true;
            current[i] = s;
            assignSlots(cost, i + 1, used, current, sum + cost[i][s], best, bestCost);
            used[s] = false;
        }
    }

    // ------------------------------------------------------------------ opponent's ball

    /**
     * Opponent's ball (or ours going over): block at the net, read the hitter, dig what comes
     * (the decisions are {@link TeamDefense}'s); our own ball going over: cover, then back to base.
     */
    private void defendOrWait(double now, RotationManager rotation, Collection<VolleyballPlayer> humans) {
        defense.read(now, lastTouchAt, rotation, court, humans, difficulty);
        boolean opponentHas = defense.opponentHas();
        // Once the ball is on our side (e.g. off the block) only one defender plays it - not at
        // all if it is a human's ball.
        boolean ballHere = CourtDimensions.sideOf(world.ball().position.x) == side;
        VolleyballPlayer digger = opponentHas && ballHere ? closestDefender(forecast.descentPoint, humans) : null;
        calledBallPlayer = digger;

        for (VolleyballPlayer p : court) {
            if (humans.contains(p) || serving(p)) continue;
            Brain brain = brain(p);
            boolean front = rotation.isFrontRow(side, p.id);
            if (ourServeInPlay && waitForServeRead(p, now)) {
                brain.state = AiState.POSITIONING;
            } else if (defense.blocks(p)) {
                if (!defense.blockReady() && !p.isAirborne()) {
                    brain.state = AiState.BLOCKING;
                    moveTo(p, side.sign() * 0.6f, p.position.z, false);
                    watchNet(p, p.position.x * side.sign() < 0.9f);
                    continue;
                }
                defense.prepareBlock(p);
                brain.motor.act(p, AutoPlayer.Behavior.BLOCK, now);
                brain.state = AiState.BLOCKING;
            } else if (opponentHas && front && p.role != PlayerRole.LIBERO) {
                Vector3 offNet = defense.offBlockerSpot(p);
                moveTo(p, offNet.x, offNet.z, true);
                watchNet(p, horizontalDistance(p, offNet) < 0.9f);
                brain.state = AiState.DEFENDING;
            } else if (opponentHas && (!ballHere || p == digger)) {
                defense.readHitter(p);
                brain.motor.act(p, AutoPlayer.Behavior.DEFEND, now);
                brain.state = AiState.DEFENDING;
            } else if (opponentHas) {
                world.intent(p).clear();
                world.intent(p).crouch = true;
                brain.state = AiState.DEFENDING;
            } else if (holdingCover(now) && p.id != ourAttackerId) {
                // Our attack has just been hit: stay low around the hitter for a ball off the block.
                coverHitter(p, world.player(ourAttackerId));
            } else {
                // Our ball going over: back to the base positions.
                basePosition(p, rotation, 1f);
                brain.state = AiState.POSITIONING;
            }
        }
        cancelOthers(digger == null ? -1 : digger.id, humans);
    }

    /** Read the serve briefly before leaving the rotation. The setter and first-ball player release early. */
    private boolean waitForServeRead(VolleyballPlayer p, double now) {
        float release = p.role == PlayerRole.MIDDLE_BLOCKER ? 0.055f : 0.085f;
        release += brain(p).motor.skill().reaction() * 0.35f;
        if (now - lastTouchAt >= release) return false;
        world.intent(p).clear();
        watchNet(p, receivingServe);
        return true;
    }

    private void watchNet(VolleyballPlayer p, boolean ready) {
        MoveIntent intent = world.intent(p);
        intent.facingYaw = VolleyballPlayer.facingNetYaw(p.side);
        intent.sprint = false;
        intent.crouch = ready;
    }

    /**
     * The cover stays for a moment after our attack, while the ball is still at the net (it may
     * come back off the block). Beginners do not cover; intermediate teams leave sooner.
     */
    private boolean holdingCover(double now) {
        if (difficulty == Difficulty.BEGINNER || ourAttackerId < 0) return false;
        double hold = difficulty == Difficulty.PROFESSIONAL ? 0.7 : 0.4;
        return now - ourAttackAt < hold && Math.abs(world.ball().position.x) < 2.5f;
    }

    /**
     * The player is serving right now (holding the ball or after the toss). Once the serve is hit
     * the server is an ordinary player again and runs into the court to defend.
     */
    private boolean serving(VolleyballPlayer p) {
        BallHandling handling = world.ballHandling();
        return p == handling.server() && handling.servePhase() != BallHandling.ServePhase.NONE;
    }

    /** The defensive scheme being played (for the AI view). */
    public DefenseScheme defenseScheme() {
        return defense.scheme();
    }

    /**
     * The upright player closest to where the ball comes down; null if that is a human who can
     * play it (the AI leaves it to them).
     */
    private VolleyballPlayer closestDefender(Vector3 point, Collection<VolleyballPlayer> humans) {
        float bestAi = 0f;
        for (VolleyballPlayer p : court) {
            if (!humans.contains(p)) bestAi = Math.max(bestAi, reach(p, forecast.timeToDescent, point));
        }
        if (humanOwner(humans, world.ballHandling().lastToucherId(), bestAi) != null) return null;
        VolleyballPlayer best = null;
        float bestDistance = Float.MAX_VALUE;
        for (VolleyballPlayer p : court) {
            if (humans.contains(p) || !p.isUpright()) continue;
            float dx = p.position.x - point.x;
            float dz = p.position.z - point.z;
            // The libero takes the dig even from a little farther than the closest teammate.
            float d = (float) Math.sqrt(dx * dx + dz * dz) - (p.role == PlayerRole.LIBERO ? 0.8f : 0f);
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ serve

    /**
     * Serve: beginners float into the middle; intermediate teams pick a random zone and
     * sometimes serve hard; professional teams target the weakest receiver (and the zones that
     * broke the reception before) and choose power or safety by the score.
     */
    private void aimServe(VolleyballPlayer server, BallHandling handling) {
        if (serveAimed) return;
        serveAimed = true;
        CourtSide opponent = side.opposite();
        float margin = 1.6f - risk;
        float depth;
        float lateral;
        ServeType type = ServeType.FLOAT;
        switch (difficulty) {
            case BEGINNER -> {
                depth = 4.5f + random.nextFloat() * 2.5f;
                lateral = -2f + random.nextFloat() * 4f;
            }
            case INTERMEDIATE -> {
                depth = 3.5f + random.nextFloat() * 4.5f;
                lateral = -3.5f + random.nextFloat() * 7f;
                if (server.attributes.serving > 65 && random.nextFloat() < powerServeChance(server, 0.3f)) type = powerServe(server);
                else type = floatServe(server);
            }
            default -> {
                VolleyballPlayer weakest = weakestReceiver();
                int zone = pickServeZone();
                float r = random.nextFloat();
                Float seam = r >= 0.15f && r < 0.35f ? seamLateral(opponent) : null;
                if (r < 0.15f) {
                    // A shorter serve, in front of the receivers (a float cannot land much closer than ~5 m).
                    depth = 4.9f + random.nextFloat() * 0.8f;
                    lateral = -3f + random.nextFloat() * 6f;
                } else if (seam != null) {
                    // Into the seam between two receivers: neither is sure it is theirs.
                    depth = 5.5f + random.nextFloat() * 1.5f;
                    lateral = seam;
                } else if (weakest != null && random.nextFloat() < 0.6f) {
                    depth = weakest.position.x * opponent.sign() + (random.nextFloat() - 0.5f);
                    lateral = -weakest.position.z * opponent.sign() + (random.nextFloat() - 0.5f) * 1.2f;
                } else {
                    CourtZones.basePosition(zone, opponent, spot);
                    depth = spot.x * opponent.sign() + (random.nextFloat() - 0.5f) * 1.5f;
                    lateral = -spot.y * opponent.sign() + (random.nextFloat() - 0.5f) * 1.5f;
                }
                // Power only from good servers and not every time: a placed float at the weakest
                // receiver is the professional's bread and butter.
                if (server.attributes.serving > 60 && random.nextFloat() < powerServeChance(server, risk * 0.6f)) type = powerServe(server);
                else type = floatServe(server);
                // A power serve dips fast but cannot land short: it goes deep, the short ones are floats.
                if (type.topspin > 20f) depth = Math.max(depth, 6.8f + random.nextFloat() * 0.8f);
            }
        }
        // The coach's serve command overrides the team's own choice.
        TeamCommand command = tactics.active(TeamCommand.Group.SERVE);
        if (command != null) {
            switch (command) {
                case SERVE_ZONE_1, SERVE_ZONE_5, SERVE_ZONE_6 -> {
                    int zone = command == TeamCommand.SERVE_ZONE_1 ? 1 : command == TeamCommand.SERVE_ZONE_5 ? 5 : 6;
                    CourtZones.basePosition(zone, opponent, spot);
                    depth = Math.max(6.4f, spot.x * opponent.sign() + (random.nextFloat() - 0.3f) * 1.2f);
                    lateral = -spot.y * opponent.sign() + (random.nextFloat() - 0.5f) * 1.0f;
                }
                case SERVE_RECEIVER -> {
                    VolleyballPlayer receiver = world.player(tactics.serveTarget());
                    if (receiver == null || receiver.side != opponent || !receiver.isOnCourt()) receiver = weakestReceiver();
                    if (receiver != null) {
                        depth = receiver.position.x * opponent.sign() + (random.nextFloat() - 0.5f) * 0.6f;
                        lateral = -receiver.position.z * opponent.sign() + (random.nextFloat() - 0.5f) * 0.6f;
                    }
                }
                case POWER_SERVE -> {
                    if (server.attributes.serving > 35) type = powerServe(server);
                    depth = Math.max(depth, 6.8f + random.nextFloat() * 0.8f);
                }
                case SAFE_SERVE -> {
                    type = ServeType.FLOAT;
                    margin += 0.6f;
                    depth = 4.8f + random.nextFloat() * 2.2f;
                }
                case SHORT_SERVE -> {
                    type = ServeType.FLOAT;
                    depth = 4.9f + random.nextFloat() * 0.7f;
                    lateral = -3f + random.nextFloat() * 6f;
                }
                default -> {
                }
            }
            if (type.topspin > 20f) depth = Math.max(depth, 6.8f);
        }
        float maxLateral = CourtDimensions.HALF_WIDTH - Math.max(0.5f, margin);
        lateral = Math.max(-maxLateral, Math.min(maxLateral, lateral));
        depth = Math.max(2.5f, Math.min(CourtDimensions.HALF_LENGTH - Math.max(0.5f, margin), depth));
        TacticalTargets.teamPoint(opponent, depth, lateral, 0f, target);
        handling.setAim(server, target.x, target.z);
        handling.setServeType(server, type);
        // A power server curves about one serve in four, either way, to make it harder to read.
        ServeSpin spin = ServeSpin.TOPSPIN;
        if (ServeSpin.applies(type) && random.nextFloat() < CURVE_SERVE_CHANCE) {
            spin = random.nextBoolean() ? ServeSpin.CURVE_LEFT : ServeSpin.CURVE_RIGHT;
        }
        handling.setServeSpin(server, spin);
        memory.servedTo(zoneOf(depth, lateral));
    }

    /**
     * How often this server goes for the power serve: the power server most of the time, the
     * float specialist (almost) never, the nervous server not at set point.
     */
    private float powerServeChance(VolleyballPlayer server, float base) {
        if (server.has(PlayerTrait.FLOAT_SPECIALIST)) return base * 0.15f;
        float chance = base;
        if (server.has(PlayerTrait.POWER_SERVER)) chance = Math.max(0.5f, base + 0.3f);
        if (server.has(PlayerTrait.HOTHEAD)) chance += 0.15f;
        if (world.ballHandling().pressure() && server.has(PlayerTrait.NERVOUS)) chance *= 0.3f;
        return chance;
    }

    /**
     * The hard serve of this server: the jump topspin for strong servers and power servers (and
     * more often for professionals), the standing topspin otherwise.
     */
    private ServeType powerServe(VolleyballPlayer server) {
        if (difficulty == Difficulty.BEGINNER) return ServeType.POWER;
        boolean strong = server.attributes.serving >= 68 || server.has(PlayerTrait.POWER_SERVER);
        float chance = strong ? (difficulty == Difficulty.PROFESSIONAL ? 0.85f : 0.55f) : 0.15f;
        return random.nextFloat() < chance * server.fatigueFactor(0.55f) ? ServeType.JUMP_TOPSPIN : ServeType.POWER;
    }

    /** The placed serve: the jump float for good servers and float specialists, the standing float otherwise. */
    private ServeType floatServe(VolleyballPlayer server) {
        if (difficulty == Difficulty.BEGINNER) return ServeType.FLOAT;
        boolean good = server.attributes.serving >= 62 || server.has(PlayerTrait.FLOAT_SPECIALIST);
        float chance = good ? (difficulty == Difficulty.PROFESSIONAL ? 0.6f : 0.35f) : 0.1f;
        return random.nextFloat() < chance * server.fatigueFactor(0.55f) ? ServeType.JUMP_FLOAT : ServeType.FLOAT;
    }

    /** Lateral (opponent frame) of the gap between the two receivers standing closest together, or null. */
    private Float seamLateral(CourtSide opponent) {
        List<Float> laterals = new java.util.ArrayList<>();
        for (VolleyballPlayer o : opponents()) {
            if (RolePlan.receivesServe(o) && Math.abs(o.position.x) > 3f) laterals.add(-o.position.z * opponent.sign());
        }
        if (laterals.size() < 2) return null;
        java.util.Collections.sort(laterals);
        int i = random.nextInt(laterals.size() - 1);
        return (laterals.get(i) + laterals.get(i + 1)) / 2f;
    }

    /** The receiver with the lowest receiving rating among the opponent's back row and outside hitters. */
    private VolleyballPlayer weakestReceiver() {
        VolleyballPlayer weakest = null;
        for (VolleyballPlayer o : opponents()) {
            if (Math.abs(o.position.x) < 3f || o.role == PlayerRole.SETTER) continue;
            if (weakest == null || o.attributes.receiving < weakest.attributes.receiving) weakest = o;
        }
        return weakest;
    }

    private int pickServeZone() {
        float total = 0f;
        for (int z = 1; z <= 6; z++) total += memory.serveZoneWeight(z);
        float r = random.nextFloat() * total;
        for (int z = 1; z <= 6; z++) {
            r -= memory.serveZoneWeight(z);
            if (r <= 0f) return z;
        }
        return 6;
    }

    /** Zone (1..6) of a point given in a team's frame (depth from the net, lateral + = right). */
    static int zoneOf(float depth, float lateral) {
        boolean front = depth < CourtDimensions.ATTACK_LINE_DISTANCE;
        if (lateral < -1.5f) return front ? 4 : 5;
        if (lateral > 1.5f) return front ? 2 : 1;
        return front ? 3 : 6;
    }

    /**
     * Risk management (professional and intermediate): a team behind takes more risk, a team
     * at or facing set point plays safer.
     */
    private void updateRisk(MatchScore score) {
        if (score == null || !difficulty.meaningfulBlock) {
            risk = difficulty.risk;
            return;
        }
        int lead = score.points(side) - score.points(side.opposite());
        float r = difficulty.risk;
        if (lead <= -3) r += 0.15f;
        if (lead >= 4) r -= 0.1f;
        if (score.isSetPoint(side) || score.isSetPoint(side.opposite())) r -= 0.25f;
        risk = Math.max(0.1f, Math.min(0.95f, r));
    }

    // ------------------------------------------------------------------ between rallies

    /** Staggered reactions and opportunistic high fives while the ball is dead. */
    public void betweenRallies(double now, Collection<VolleyballPlayer> humans) {
        court = team.onCourt();
        idleOnBench(humans);
        double elapsed = celebrationStart < 0 ? 99 : now - celebrationStart;
        for (VolleyballPlayer p : team.squad()) {
            Brain bench = brain(p);
            if (p.isOnCourt() || bench.reactedToPoint || p.number % 3 == 1) continue;
            double cue = 0.36 + Math.floorMod(p.number, 3) * 0.08;
            if (elapsed >= cue && elapsed < 0.75) {
                bench.reactedToPoint = true;
                p.setActionPose(wonLastPoint ? ActionPose.CLAP : ActionPose.DISAPPOINTED, 0.73f);
            }
        }
        for (VolleyballPlayer p : court) {
            world.intent(p).clear();
            Brain brain = brain(p);
            if (!p.isUpright() || p.isAirborne()) continue;
            if (brain.highFivePartner >= 0) continue;
            boolean featured = p.id == pointPlayerId;
            int style = Math.floorMod(p.number, 3);
            double cue = featured ? 0.045 : 0.11 + style * 0.085;
            if (!brain.reactedToPoint && elapsed >= cue && elapsed < 0.95 && p.groundSpeed() < 1.4f) {
                brain.reactedToPoint = true;
                ActionPose pose = wonLastPoint
                        ? (featured ? ActionPose.CELEBRATE : style == 0 ? ActionPose.CELEBRATE : ActionPose.CLAP)
                        : (featured || style == 0 ? ActionPose.DISAPPOINTED : ActionPose.CLAP);
                p.setActionPose(pose, pose == ActionPose.CLAP ? 0.58f : 0.78f);
                if (!humans.contains(p)) brain.state = AiState.CELEBRATING;
            }
        }
        if (wonLastPoint && elapsed > 0.32 && elapsed < 0.65) {
            for (VolleyballPlayer p : court) {
                Brain brain = brain(p);
                if (p.id == pointPlayerId || brain.highFivePartner >= 0 || !calmForHighFive(p)) continue;
                VolleyballPlayer nearest = null;
                float best = 2.35f * 2.35f;
                for (VolleyballPlayer mate : court) {
                    if (mate.id <= p.id || mate.id == pointPlayerId || brain(mate).highFivePartner >= 0 || !calmForHighFive(mate)) continue;
                    float distance = p.position.dst2(mate.position);
                    if (distance >= 0.72f * 0.72f && distance < best) {
                        nearest = mate;
                        best = distance;
                    }
                }
                if (nearest != null) {
                    brain.highFivePartner = nearest.id;
                    brain(nearest).highFivePartner = p.id;
                }
            }
        }
        for (VolleyballPlayer p : court) {
            Brain brain = brain(p);
            if (brain.highFivePartner <= p.id) continue;
            VolleyballPlayer mate = world.player(brain.highFivePartner);
            if (brain.highFiveDone) {
                if (p.actionPose() == ActionPose.HIGH_FIVE) {
                    faceTeammate(p, mate);
                    faceTeammate(mate, p);
                }
                continue;
            }
            if (elapsed >= 0.99 || mate == null) continue;
            float dx = mate.position.x - p.position.x;
            float dz = mate.position.z - p.position.z;
            float distance = (float) Math.hypot(dx, dz);
            if (distance > 1.07f) {
                float share = Math.min(0.5f, (distance - 0.96f) / (2f * distance));
                moveTo(p, p.position.x + dx * share, p.position.z + dz * share, false);
                moveTo(mate, mate.position.x - dx * share, mate.position.z - dz * share, false);
                world.intent(p).sprint = false;
                world.intent(mate).sprint = false;
            } else if (calmForHighFive(p) && calmForHighFive(mate)
                    && p.facingX() * dx + p.facingZ() * dz >= 0.90f * distance
                    && mate.facingX() * dx + mate.facingZ() * dz <= -0.90f * distance) {
                brain.highFiveDone = true;
                p.setActionPose(ActionPose.HIGH_FIVE, 0.55f);
                mate.setActionPose(ActionPose.HIGH_FIVE, 0.55f);
                float reach = Math.min(p.height, mate.height) * 0.85f;
                float x = (p.position.x + mate.position.x) * 0.5f;
                float z = (p.position.z + mate.position.z) * 0.5f;
                p.recordActionContact(x, reach, z);
                mate.recordActionContact(x, reach, z);
            }
            faceTeammate(p, mate);
            faceTeammate(mate, p);
        }
        updateBodyStates(humans);
    }

    private static boolean calmForHighFive(VolleyballPlayer p) {
        return p.isUpright() && !p.isAirborne() && p.groundSpeed() < 0.8f;
    }

    private void faceTeammate(VolleyballPlayer p, VolleyballPlayer mate) {
        world.intent(p).facingYaw = (float) Math.atan2(p.position.z - mate.position.z, mate.position.x - p.position.x);
    }

    /** Walks around teammates towards a serving position or bench. */
    public void walkToLineup(VolleyballPlayer p, float x, float z) {
        court = team.onCourt();
        steer(p, x, z, true);
        MoveIntent intent = world.intent(p);
        float distance = (float) Math.hypot(x - p.position.x, z - p.position.z);
        intent.sprint = false;
        intent.direction.scl(distance > 4f ? 0.92f : 0.68f);
        if (distance < 0.55f) intent.facingYaw = VolleyballPlayer.facingNetYaw(side);
    }

    /** A timeout: the team gathers in a circle in front of its bench (standing, the arms down). */
    public void huddle(RotationManager rotation, Collection<VolleyballPlayer> humans) {
        court = team.onCourt();
        int n = 0;
        for (VolleyballPlayer p : court) {
            if (humans.contains(p)) continue;
            float angle = n++ * (float) (Math.PI * 2.0) / 6f;
            // Between the court and the bench (the bench is on the +z side).
            float cx = side.sign() * 5.5f;
            float cz = world.benchSign() * (CourtDimensions.HALF_WIDTH - 0.4f);
            moveTo(p, cx + (float) Math.cos(angle) * 1.1f, cz + (float) Math.sin(angle) * 1.1f, false);
            world.intent(p).sprint = false;
            brain(p).state = AiState.POSITIONING;
        }
        updateBodyStates(humans);
    }

    // ------------------------------------------------------------------ helpers

    /** Priority 1: players not playing the ball step out of each other's way. */
    private void avoidCollisions(Collection<VolleyballPlayer> humans) {
        List<VolleyballPlayer> players = court;
        for (VolleyballPlayer p : players) {
            if (humans.contains(p) || p.id == responsibleId || p.isAirborne() || !p.isUpright()) continue;
            if (world.ballHandling().server() == p) continue;
            float pushX = 0f;
            float pushZ = 0f;
            for (VolleyballPlayer q : players) {
                if (q == p) continue;
                float dx = p.position.x - q.position.x;
                float dz = p.position.z - q.position.z;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d < 1e-3f || d > PERSONAL_SPACE) continue;
                float strength = (1f - d / PERSONAL_SPACE) * (q.id == responsibleId ? 1.5f : 0.8f);
                pushX += dx / d * strength;
                pushZ += dz / d * strength;
            }
            if (pushX == 0f && pushZ == 0f) continue;
            MoveIntent intent = world.intent(p);
            intent.direction.add(pushX, pushZ);
            if (intent.direction.len2() > 1f) intent.direction.nor();
        }
    }

    /** How long after touching down a player counts as landing (s). */
    private static final double LANDING_TIME = 0.25;

    /** States that follow from the body: in the air, landing, diving, getting up. */
    private void updateBodyStates(Collection<VolleyballPlayer> humans) {
        for (VolleyballPlayer p : court) {
            Brain brain = brains.get(p.id);
            if (brain == null) continue;
            if (humans.contains(p)) {
                brain.state = AiState.IDLE;
                continue;
            }
            if (brain.wasAirborne && !p.isAirborne()) brain.landedAt = world.time();
            brain.wasAirborne = p.isAirborne();
            boolean landing = world.time() - brain.landedAt < LANDING_TIME;
            if (p.bodyState() == BodyState.DIVING) brain.state = AiState.DIVING;
            else if (!p.isUpright()) brain.state = AiState.RECOVERING;
            else if (p.isAirborne() && brain.state != AiState.ATTACKING && brain.state != AiState.BLOCKING
                    && brain.state != AiState.SERVING && brain.state != AiState.CELEBRATING) brain.state = AiState.JUMPING;
            else if (landing && !p.isAirborne() && brain.state != AiState.MOVING_TO_BALL
                    && brain.state != AiState.PASSING && brain.state != AiState.SETTING) brain.state = AiState.LANDING;
        }
    }

    /** The base position of the player's role (after the switch) while the ball is in play. */
    private void basePosition(VolleyballPlayer p, RotationManager rotation, float depthScale) {
        int zone = rotation.zoneOf(side, p.id);
        if (zone < 0) return;
        CourtZones.basePosition(RolePlan.roleZone(p, CourtZones.isFrontRow(zone)), side, spot);
        moveTo(p, spot.x * depthScale, spot.y * depthScale, true);
    }

    /** Walks/runs to a tactical spot, with this rally's positioning error if {@code withError}. */
    private void moveTo(VolleyballPlayer p, float x, float z, boolean withError) {
        Brain brain = brain(p);
        if (withError) {
            x += brain.positionError.x;
            z += brain.positionError.y;
        }
        // Never closer than 0.4 m to the net while positioning.
        if (x * side.sign() < 0.4f) x = side.sign() * 0.4f;
        z = clampZ(z);
        brain.targetPosition.set(x, z);
        brain.targetTime = world.time();
        steer(p, x, z);
    }

    /** A teammate this close (m) to the straight way to a spot is walked around, not through. */
    static final float DETOUR_CLEARANCE = 1.1f;
    /** How far to the side of that teammate the way goes (m). */
    static final float DETOUR_OFFSET = 1.4f;
    /** Two players' spots are never closer than this (m) unless both have a reason to be there. */
    static final float MIN_SPOT_GAP = 1.6f;

    /**
     * Runs to a spot the way players do: around a teammate standing in the way (on the side away
     * from them; when they stand right on the line, on the own right, so two players crossing pass
     * each other cleanly, as in the switch after the serve) instead of straight through them.
     */
    private void steer(VolleyballPlayer p, float x, float z) {
        steer(p, x, z, false);
    }

    private void steer(VolleyballPlayer p, float x, float z, boolean lineup) {
        Brain brain = brain(p);
        float dx = x - p.position.x;
        float dz = z - p.position.z;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float wx = x;
        float wz = z;
        if (len > 1.2f && p.isUpright() && !p.isAirborne()) {
            float ux = dx / len;
            float uz = dz / len;
            float first = Float.MAX_VALUE;
            for (VolleyballPlayer q : court) {
                if (q == p) continue;
                float qx = q.position.x - p.position.x;
                float qz = q.position.z - p.position.z;
                float along = qx * ux + qz * uz;
                if (along < 0.5f || along > len - 0.4f || along >= first) continue;
                // Signed distance from the way (+ = to the left of the run, (-uz, ux)).
                float across = -qx * uz + qz * ux;
                if (Math.abs(across) > DETOUR_CLEARANCE) continue;
                first = along;
                float pass = Math.abs(across) < 0.25f ? -1f : -Math.signum(across);
                wx = q.position.x - uz * pass * DETOUR_OFFSET;
                wz = q.position.z + ux * pass * DETOUR_OFFSET;
            }
            if (wx * side.sign() < 0.4f) wx = side.sign() * 0.4f;
            if (!lineup) wz = clampZ(wz);
        }
        brain.motor.moveTo(p, wx, wz);
        // The detour is part of a longer run: keep the pace of the whole way.
        if (wx != x || wz != z) world.intent(p).sprint = len > 2.5f;
    }

    /** The setter on its way to the setting spot this step (its spot is not to be taken), or -1. */
    private int setterRunningId = -1;

    /**
     * How firmly a player holds its spot this step: the ball player, people, players in the air
     * or on the floor, blockers, hitters on their approach and the setter on its way to the
     * setting spot keep theirs; receivers next, then covering and defending players, then the
     * rest. A player gives way to anyone holding their spot more firmly.
     */
    private int spotPriority(VolleyballPlayer p, Collection<VolleyballPlayer> humans) {
        if (p.id == responsibleId || humans.contains(p) || p.isAirborne() || !p.isUpright() || p.id == setterRunningId) return 10;
        return switch (brain(p).state) {
            case BLOCKING, SETTING, ATTACKING, PASSING, DIVING -> 6;
            case APPROACHING, JUMPING, MOVING_TO_BALL -> 5;
            case READY_TO_RECEIVE -> 3;
            case COVERING, DEFENDING -> 2;
            default -> 1;
        };
    }

    /**
     * Spreads the players over the court: when two players' spots this step are closer than
     * {@link #MIN_SPOT_GAP} (two base positions meeting a hitter's run-up, a cover spot on a
     * defender's), the one holding its spot less firmly moves its spot off. Players with an
     * unmovable spot (priority 5 and up) are only obstacles.
     */
    private void separateSpots(Collection<VolleyballPlayer> humans) {
        double now = world.time();
        List<VolleyballPlayer> order = new java.util.ArrayList<>(court);
        Map<Integer, Integer> priority = new HashMap<>();
        for (VolleyballPlayer p : order) priority.put(p.id, spotPriority(p, humans));
        order.sort((a, b) -> priority.get(a.id).equals(priority.get(b.id)) ? Integer.compare(a.id, b.id)
                : Integer.compare(priority.get(b.id), priority.get(a.id)));
        for (int i = 0; i < order.size(); i++) {
            VolleyballPlayer p = order.get(i);
            Brain brain = brain(p);
            if (brain == null || priority.get(p.id) >= 5 || brain.targetTime != now) continue;
            float tx = brain.targetPosition.x;
            float tz = brain.targetPosition.y;
            boolean moved = false;
            for (int pass = 0; pass < 2; pass++) {
                for (int j = 0; j < order.size(); j++) {
                    VolleyballPlayer q = order.get(j);
                    if (q == p) continue;
                    Brain qb = brain(q);
                    boolean qHasSpot = qb != null && qb.targetTime == now && priority.get(q.id) < 5;
                    // Players with a spot that give way to this one are moved later, not the other way round.
                    if (qHasSpot && j > i) continue;
                    float qx = qHasSpot ? qb.targetPosition.x : q.position.x;
                    float qz = qHasSpot ? qb.targetPosition.y : q.position.z;
                    float dx = tx - qx;
                    float dz = tz - qz;
                    float d = (float) Math.sqrt(dx * dx + dz * dz);
                    if (d >= MIN_SPOT_GAP) continue;
                    if (d < 0.05f) {
                        // Same spot: step aside towards where the player now is (or sideways).
                        dx = p.position.x - qx;
                        dz = p.position.z - qz;
                        d = (float) Math.sqrt(dx * dx + dz * dz);
                        if (d < 0.05f) {
                            dx = 0f;
                            dz = p.id % 2 == 0 ? 1f : -1f;
                            d = 1f;
                        }
                    }
                    tx = qx + dx / d * MIN_SPOT_GAP;
                    tz = clampZ(qz + dz / d * MIN_SPOT_GAP);
                    if (tx * side.sign() < 0.4f) tx = side.sign() * 0.4f;
                    moved = true;
                }
            }
            if (!moved) continue;
            boolean crouch = world.intent(p).crouch;
            brain.targetPosition.set(tx, tz);
            steer(p, tx, tz);
            world.intent(p).crouch = crouch;
        }
    }

    private static float clampZ(float z) {
        return Math.max(-CourtDimensions.HALF_WIDTH - 1f, Math.min(CourtDimensions.HALF_WIDTH + 1f, z));
    }

    private Brain brain(VolleyballPlayer p) {
        return brains.get(p.id);
    }

    /**
     * The setter of this rotation: in a 4-2 the one in the front row, in a 6-2 the one in the back
     * row, in a 5-1 the only one. The other setter plays as the right-side attacker.
     */
    private VolleyballPlayer setter(RotationManager rotation) {
        VolleyballPlayer first = null;
        VolleyballPlayer front = null;
        VolleyballPlayer back = null;
        for (VolleyballPlayer p : court) {
            if (p.role != PlayerRole.SETTER) continue;
            if (first == null) first = p;
            if (rotation.isFrontRow(side, p.id)) front = p;
            else back = p;
        }
        return switch (system) {
            case FOUR_TWO -> front != null ? front : first;
            case SIX_TWO -> back != null ? back : first;
            case FIVE_ONE -> first;
        };
    }

    private List<VolleyballPlayer> opponents() {
        return side == CourtSide.HOME ? world.away().onCourt() : world.home().onCourt();
    }
}
