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
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ServeSpin;
import com.volleyball.game.mechanics.BallTouchListener;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ContactZone;
import com.volleyball.game.mechanics.PassRating;
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
    static final float CELEBRATION_TIME = 1.3f;
    /** After a point the players come together for this long (high fives, or a word after a lost point). */
    static final float GATHER_TIME = 1.9f;
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
    /** Seconds until the setter's contact (estimated each step while the pass travels). */
    private float timeToSet = 1f;
    /** Decoys who have jumped in this possession. */
    private final java.util.Set<Integer> decoysJumped = new java.util.HashSet<>();
    /** The play the attack now in the air came from (to learn which plays score). */
    private String attackPlay;

    private Difficulty difficulty;
    private float risk;
    private OffenseSystem system = OffenseSystem.FIVE_ONE;

    // Per-rally decisions.
    private int responsibleId = -1;
    private int responsibleForToucher = Integer.MIN_VALUE;
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
    /** Our last touch was an attack: when it was hit, and by whom (the cover holds until the block's result). */
    private double ourAttackAt = -10;
    private int ourAttackerId = -1;

    // Between rallies.
    private double celebrationStart = -1;
    private boolean wonLastPoint;

    /** The state of one computer player. */
    static final class Brain {
        final AutoPlayer motor;
        AiState state = AiState.IDLE;
        final Vector2 targetPosition = new Vector2();
        /** Positioning mistake of this rally (difficulty), added to tactical positions. */
        final Vector2 positionError = new Vector2();
        float reachChance;
        boolean wasAirborne;
        /** World time of the last landing (the landing state lasts {@link #LANDING_TIME}). */
        double landedAt = Double.NEGATIVE_INFINITY;
        /** The player's own way of playing (shot preferences, risk), so no two hitters are alike. */
        Style style = Style.NEUTRAL;

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

    /** What the play asks of a player (for a person's on-screen hint), or null. */
    public String playAssignment(VolleyballPlayer p) {
        Combination play = playCaller.play();
        if (play == null) return null;
        for (Map.Entry<SetType.Lane, VolleyballPlayer> e : playCaller.runners().entrySet()) {
            if (e.getValue() != p) continue;
            Combination.Run run = play.runOf(e.getKey());
            Combination.Option o = playCaller.chosen();
            boolean yours = o != null && !o.dump() && o.hitter() == e.getKey();
            if (run != null && run.decoy()) return play.name() + ": DECOY - " + run.tempo().label.toLowerCase(java.util.Locale.ROOT) + " fake, jump with the set";
            if (yours && o != null) return play.name() + ": YOUR SET - " + o.set().label;
            if (run != null) return play.name() + ": run the " + run.tempo().label.toLowerCase(java.util.Locale.ROOT) + " approach";
        }
        return play.name();
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
        responsibleForToucher = Integer.MIN_VALUE;
        attackerId = -1;
        attackDecidedFor = -1;
        serveAimed = false;
        playCaller.reset();
        decoysJumped.clear();
        attackPlay = null;
        defense.newRally();
        celebrationStart = -1;
        receivingServe = false;
        receptionLanes.clear();
        dumpDecided = false;
        offBlock = false;
        ourAttackAt = -10;
        ourAttackerId = -1;
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
        memory.record(touch);
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
    public void rallyFinished(boolean won, double now) {
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

        if (handling.lastToucherId() != responsibleForToucher) {
            responsibleForToucher = handling.lastToucherId();
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
            cancelOthers(-1, humans);
            VolleyballPlayer inPath = ballInPath(humans);
            if (inPath != null) dodge(inPath);
        } else if (comingToUs && (!ourPossession || touches < 3)) {
            playOurBall(now, rotation, humans, ourPossession ? touches : 0);
        } else {
            defendOrWait(now, rotation, humans);
        }
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
        if (responsibleId < 0 || shouldReconsider(now, humans)) {
            int previous = responsibleId;
            responsibleId = chooseResponsible(touches, setter, rotation, humans);
            responsibleChosenAt = now;
            if (touches == 1 && previous < 0) prepareSet(rotation, setter, humans);
        }
        // A person setting chooses the set (or leaves it on Auto, which plays the team's combination);
        // the hitter that set goes to gets ready for it.
        VolleyballPlayer ballPlayer = world.player(responsibleId);
        if (touches == 1 && ballPlayer != null && humans.contains(ballPlayer)) {
            plannedSet = handling.setType(ballPlayer);
            // The hitters run for the person's set (route and tempo), not for the play the team called.
            if (!handling.isAutoSet(ballPlayer)) {
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
        if (touches == 1) runPlay(humans);
        // A ball flying straight at a teammate is played by that teammate (anyone else would be too late).
        VolleyballPlayer inPath = ballInPath(humans);
        // The person's ball stays theirs: a teammate in its path gets out of the way instead.
        VolleyballPlayer dodging = null;
        boolean personsBall = ballPlayer != null && humans.contains(ballPlayer);
        // A first ball flying at the setter: the setter gets out of the way (it is needed for the
        // second touch) when a teammate can take it instead.
        VolleyballPlayer chosen = world.player(responsibleId);
        boolean spareSetter = inPath != null && touches == 0 && inPath == setter && chosen != null && chosen != setter
                && reach(chosen, forecast.timeToDescent, forecast.descentPoint) > 0.5f;
        if (inPath != null && (personsBall || spareSetter)) {
            dodging = inPath;
        } else if (inPath != null && inPath.id != responsibleId && !(touches == 1 && inPath != setter && setter != null
                && setter.id == responsibleId && reach(setter, forecast.timeToDescent, forecast.descentPoint) > 0.5f)) {
            responsibleId = inPath.id;
            responsibleChosenAt = now;
            if (touches == 2) attackerId = inPath.id;
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
                            : setterDump(p, rotation) ? AutoPlayer.Behavior.ATTACK : AutoPlayer.Behavior.SET;
                    default -> canAttack(p, rotation) ? AutoPlayer.Behavior.ATTACK : AutoPlayer.Behavior.PASS;
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
                brain.motor.act(p, behavior, now);
                brain.state = switch (behavior) {
                    case SET -> AiState.SETTING;
                    case ATTACK -> p.isAirborne() ? AiState.ATTACKING : AiState.APPROACHING;
                    case SCRAMBLE -> handling.isArmed(p) ? AiState.PASSING : AiState.DEFENDING;
                    default -> handling.isArmed(p) ? AiState.PASSING : AiState.MOVING_TO_BALL;
                };
            } else if (p == setter && touches == 0) {
                // Priority 4: the setter runs to the setting spot while the pass is in the air.
                TacticalTargets.setterTarget(side, target);
                moveTo(p, target.x - side.sign() * 0.2f, target.z, false);
                brain.state = AiState.POSITIONING;
            } else if (touches == 0 && receivingServe && receptionLanes.containsKey(p.id)) {
                // A receiver whose lane the serve is not in holds the lane, ready.
                receptionSpot(p);
                brain.state = AiState.READY_TO_RECEIVE;
            } else if (touches == 1 && playCaller.runTarget(p, timeToSet, target) != null) {
                // Priority 5: running the play - the start of the approach, then (at the tempo) the hitting spot.
                runRoute(p);
            } else if (touches <= 1 && isAttackOption(p, rotation, setter)) {
                // Attackers get ready for the set.
                approachStart(p, rotation, touches);
                brain.state = AiState.APPROACHING;
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
    private boolean shouldReconsider(double now, Collection<VolleyballPlayer> humans) {
        double interval = difficulty == Difficulty.PROFESSIONAL ? 0.12 : 0.3;
        if (difficulty == Difficulty.BEGINNER || now - responsibleChosenAt < interval) return false;
        VolleyballPlayer current = world.player(responsibleId);
        if (current == null || humans.contains(current)) return false;
        if (!current.isUpright() && current.bodyState() != BodyState.DIVING) return true;
        if (world.ballHandling().isArmed(current) || current.isAirborne()) return false;
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
     * The player (AI, upright, not the last toucher) the ball is about to hit within the next
     * 0.4 s, judged from its current flight (gravity only): within half a metre of the body axis,
     * between the knees and the top of the head. Null if none.
     */
    private VolleyballPlayer ballInPath(Collection<VolleyballPlayer> humans) {
        Ball ball = world.ball();
        int lastToucher = world.ballHandling().lastToucherId();
        VolleyballPlayer hit = null;
        float first = Float.MAX_VALUE;
        for (VolleyballPlayer p : court) {
            if (p.id == lastToucher || humans.contains(p) || !p.isUpright() || p.isAirborne()) continue;
            for (float t = 0f; t <= 0.4f; t += 0.02f) {
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
        if (touches == 1 && setter != null && setter.id != lastToucher && setter.isUpright() && setter.isOnCourt()) return setter.id;
        // The setter took the first ball (or is down): the libero comes in and sets, as long as it can get there.
        VolleyballPlayer libero = team.libero();
        if (touches == 1 && libero != null && libero.isOnCourt() && libero.isUpright() && libero.id != lastToucher
                && !humans.contains(libero) && reach(libero, forecast.timeToDescent, forecast.descentPoint) > LIBERO_SETS_FROM) {
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
            if (offBlock && brain != null && brain.state == AiState.COVERING) score += 0.15f;
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
        float dx = point.x - p.position.x;
        float dz = point.z - p.position.z;
        float stretch = 0.6f + (p.role == PlayerRole.LIBERO ? LIBERO_DIVE_REACH : 0f);
        float distance = Math.max(0f, (float) Math.sqrt(dx * dx + dz * dz) - stretch);
        Brain brain = brains.get(p.id);
        float reaction = brain == null ? 0.1f : brain.motor.skill().reaction();
        float needed = reaction + 0.25f + distance / PlayerLocomotion.maxRunSpeed(p.attributes) + (p.isAirborne() ? 0.35f : 0f);
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
        dumpDecided = false;
        playCaller.setSetter(from);
        playCaller.call(difficulty, playbook, tactics, lanes, memory.lastOwnPass(), setterFront, humans);
        Combination.Option o = playCaller.chosen();
        if (o != null && !o.dump()) plannedSet = o.set();
        VolleyballPlayer hitter = playCaller.chosenHitter(from);
        attackerId = hitter == null ? -1 : hitter.id;
        if (whoSets != null && !humans.contains(whoSets) && o != null && !o.dump()) world.ballHandling().setSetType(whoSets, o.set());
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
        VolleyballPlayer whoSets = world.player(responsibleId);
        if (whoSets == null || playCaller.play() == null) return;
        setterRead.watchHeight = TacticalTargets.SETTER_HEIGHT;
        world.predictor().predict(world.ball(), 6f, setterRead);
        setterRead.watchHeight = Float.NaN;
        if (!setterRead.reachesWatchHeight) return;
        timeToSet = setterRead.timeToDescent;
        if (!humans.contains(whoSets)) playCaller.checkSetter(reach(whoSets, timeToSet, setterRead.descentPoint));
        if (playCaller.decided() || !(timeToSet < 0.3f || world.ballHandling().isArmed(whoSets) || whoSets.isAirborne())) return;
        float placement = (float) Math.hypot(whoSets.position.x - setterRead.descentPoint.x, whoSets.position.z - setterRead.descentPoint.z);
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
        float rise = PlayerLocomotion.riseTime(PlayerLocomotion.jumpHeight(p.attributes));
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
        float airTime = 2f * PlayerLocomotion.riseTime(PlayerLocomotion.jumpHeight(p.attributes));
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
        Style style = brain(a).style;
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
        float blockLateral = 0f;
        for (VolleyballPlayer o : opponents()) {
            if (Math.abs(o.position.x) < ShotAdvisor.BLOCK_NET_DISTANCE && Math.abs(o.position.z - a.position.z) < ShotAdvisor.BLOCK_LATERAL) {
                blockers++;
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

        shotWeights.clear();
        switch (difficulty) {
            case BEGINNER -> {
                shotWeights.put(AttackType.POWER, 1f);
                shotWeights.put(AttackType.TIP, 0.08f);
                shotWeights.put(AttackType.ROLL_SHOT, 0.05f);
            }
            case INTERMEDIATE -> {
                shotWeights.put(AttackType.POWER, style.power());
                shotWeights.put(AttackType.TIP, style.tip() * 0.4f);
                shotWeights.put(AttackType.ROLL_SHOT, style.roll() * 0.5f);
                shotWeights.put(AttackType.CUT, style.cut() * 0.3f);
                if (blockers >= 1) shotWeights.put(AttackType.FEINT, style.tip() * 0.15f);
                shotWeights.merge(advised, advised == AttackType.TIP ? 0.5f : 1.2f, Float::sum);
            }
            case PROFESSIONAL -> {
                shotWeights.put(AttackType.POWER, style.power() * 1.2f);
                shotWeights.put(AttackType.TIP, style.tip() * (blockers >= 2 ? 0.5f : 0.25f));
                shotWeights.put(AttackType.ROLL_SHOT, style.roll() * 0.4f);
                shotWeights.put(AttackType.CUT, style.cut() * (blockers >= 1 ? 0.4f : 0.15f));
                if (blockers >= 2 && Math.abs(a.position.z) > CourtDimensions.HALF_WIDTH - 1.2f) shotWeights.put(AttackType.TOOL, 0.2f);
                // A block in front: fake the swing and push it behind the block, or wipe it off a tall
                // block's fingertips (from the middle of the net, where a tool is no option).
                if (blockers >= 1) shotWeights.put(AttackType.FEINT, style.tip() * 0.3f);
                if (blockers >= 2 && Math.abs(a.position.z) <= CourtDimensions.HALF_WIDTH - 1.2f) shotWeights.put(AttackType.OFF_THE_TOP, 0.15f);
                shotWeights.merge(advised, advised == AttackType.TIP ? 0.5f : 1.2f, Float::sum);
                // Against defenders who read this hitter's tips, tip less.
                if (memory.habit(a.id) != null && memory.habit(a.id).tipShare() > 0.35f) shotWeights.computeIfPresent(AttackType.TIP, (k, v) -> v * 0.4f);
            }
        }
        // Against a single block the coach wants it hard.
        if (tactics.is(TeamCommand.VS_SINGLE_BLOCK) && blockers <= 1) shotWeights.merge(AttackType.POWER, 1.5f, Float::sum);
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
                        float[] best = bestGap(opponent, a, blockers, blockLateral, maxLateral, maxDepth, type == AttackType.ROLL_SHOT, style);
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
    }

    /**
     * The best spot to hit (professional): of a dozen candidate spots on the opponent's court, the
     * one farthest from every defender (blockers excluded), not behind the block (its shadow from
     * the hitter), preferring the hitter's favourite line or cross; a little randomness among the
     * best keeps it unpredictable.
     */
    private float[] bestGap(CourtSide opponent, VolleyballPlayer a, int blockers, float blockLateral, float maxLateral,
                            float maxDepth, boolean roll, Style style) {
        float hitterLateral = -a.position.z * opponent.sign();
        float bestScore = -Float.MAX_VALUE;
        float[] best = {maxDepth - 0.5f, 0f};
        for (int i = 0; i < 14; i++) {
            float d = roll ? (random.nextBoolean() ? 3f + random.nextFloat() * 1.5f : maxDepth - random.nextFloat() * 1.2f)
                    : 4.5f + random.nextFloat() * (maxDepth - 4.5f);
            float l = (random.nextFloat() * 2f - 1f) * maxLateral;
            float score = gapAt(opponent, d, l, true);
            if (blockers > 0 && !roll) {
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
    }

    /** Cover: a half circle 1.5–2.5 m behind the own hitter, for balls coming back off the block. */
    private void coverHitter(VolleyballPlayer p, VolleyballPlayer hitter) {
        if (hitter == null) {
            brain(p).state = AiState.POSITIONING;
            return;
        }
        if (brain(p).state == AiState.JUMPING && p.isAirborne()) return;
        // Professionals close in (1.2-2.2 m, a tight arc); the others stay looser.
        boolean pro = difficulty == Difficulty.PROFESSIONAL;
        float behind = (pro ? 1.3f : 1.8f) + (p.id % 3) * (pro ? 0.45f : 0.4f);
        float sideways = ((p.id % 3) - 1) * (pro ? 1.3f : 1.6f);
        float x = hitter.position.x + side.sign() * behind;
        float z = hitter.position.z + sideways;
        moveTo(p, x, clampZ(z), true);
        world.intent(p).crouch = true;
        brain(p).state = AiState.COVERING;
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

        for (VolleyballPlayer p : court) {
            if (humans.contains(p) || serving(p)) continue;
            Brain brain = brain(p);
            boolean front = rotation.isFrontRow(side, p.id);
            if (defense.blocks(p)) {
                if (!defense.blockReady() && !p.isAirborne()) {
                    brain.state = AiState.BLOCKING;
                    moveTo(p, side.sign() * 0.6f, p.position.z, false);
                    continue;
                }
                defense.prepareBlock(p);
                brain.motor.act(p, AutoPlayer.Behavior.BLOCK, now);
                brain.state = AiState.BLOCKING;
            } else if (opponentHas && front && p.role != PlayerRole.LIBERO) {
                Vector3 offNet = defense.offBlockerSpot(p);
                moveTo(p, offNet.x, offNet.z, true);
                world.intent(p).crouch = true;
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
        return random.nextFloat() < chance ? ServeType.JUMP_TOPSPIN : ServeType.POWER;
    }

    /** The placed serve: the jump float for good servers and float specialists, the standing float otherwise. */
    private ServeType floatServe(VolleyballPlayer server) {
        if (difficulty == Difficulty.BEGINNER) return ServeType.FLOAT;
        boolean good = server.attributes.serving >= 62 || server.has(PlayerTrait.FLOAT_SPECIALIST);
        float chance = good ? (difficulty == Difficulty.PROFESSIONAL ? 0.6f : 0.35f) : 0.1f;
        return random.nextFloat() < chance ? ServeType.JUMP_FLOAT : ServeType.FLOAT;
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

    /**
     * Between rallies: the winners celebrate (arms up, a little hop), then everyone walks back
     * to the zone positions.
     */
    public void betweenRallies(double now, RotationManager rotation, Collection<VolleyballPlayer> humans) {
        boolean celebrating = celebrationStart >= 0 && now - celebrationStart < CELEBRATION_TIME;
        court = team.onCourt();
        idleOnBench(humans);
        for (VolleyballPlayer p : court) {
            if (humans.contains(p)) continue;
            Brain brain = brain(p);
            MoveIntent intent = world.intent(p);
            intent.clear();
            if (!p.isUpright()) {
                brain.state = AiState.RECOVERING;
                continue;
            }
            double since = celebrationStart < 0 ? 99 : now - celebrationStart;
            if (celebrating && wonLastPoint && since < 0.6) {
                brain.state = AiState.CELEBRATING;
                if (p.actionPose() == ActionPose.NONE) p.setActionPose(ActionPose.CELEBRATE, 0.6f);
                boolean awayFromNet = Math.abs(p.position.x) > 2f;
                if (awayFromNet && !p.isAirborne() && since < 0.4 && (p.id + (int) celebrationStart) % 3 == 0) {
                    intent.jump = true;
                    intent.jumpHeightFactor = 0.35f;
                }
                continue;
            }
            if (celebrationStart >= 0 && since < GATHER_TIME && !p.isAirborne()) {
                gather(p, wonLastPoint);
                continue;
            }
            rotationPosition(p, rotation);
            world.intent(p).sprint = false;
            brain.state = AiState.POSITIONING;
        }
        updateBodyStates(humans);
    }

    /**
     * Between rallies the players come together in the middle of their half: after a point won
     * they slap hands with whoever is next to them, after a point lost they clap and encourage
     * each other. A person's player is gathered around too.
     */
    private void gather(VolleyballPlayer p, boolean won) {
        float cx = 0f;
        float cz = 0f;
        int n = 0;
        for (VolleyballPlayer o : court) {
            cx += o.position.x;
            cz += o.position.z;
            n++;
        }
        cx /= Math.max(1, n);
        cz /= Math.max(1, n);
        // Not right at the net: the middle of the own half.
        cx = side.sign() * Math.max(2.2f, Math.min(5f, Math.abs(cx)));
        float angle = (p.id % 6) * (float) (Math.PI * 2.0) / 6f;
        float ring = won ? 0.55f : 0.65f;
        moveTo(p, cx + (float) Math.cos(angle) * ring, cz + (float) Math.sin(angle) * ring, false);
        world.intent(p).sprint = false;
        // A lost point: first a moment of disappointment (hands to the head), then the team picks itself up.
        boolean justEnded = brain(p).state != AiState.CELEBRATING;
        if (!won && justEnded && p.actionPose() == ActionPose.NONE) p.setActionPose(ActionPose.DISAPPOINTED, DISAPPOINTED_TIME);
        brain(p).state = AiState.CELEBRATING;
        VolleyballPlayer mate = null;
        for (VolleyballPlayer o : court) {
            if (o == p || !o.isUpright()) continue;
            if (o.position.dst2(p.position) < 1.1f * 1.1f) mate = o;
        }
        if (mate == null || p.actionPose() != ActionPose.NONE) return;
        // Turn to the teammate and slap (or clap).
        world.intent(p).facingYaw = (float) Math.atan2(-(mate.position.z - p.position.z), mate.position.x - p.position.x);
        p.setActionPose(won ? ActionPose.HIGH_FIVE : ActionPose.CLAP, won ? 0.55f : 0.8f);
    }

    /** How long a player hangs its head after a lost point (s). */
    static final float DISAPPOINTED_TIME = 1.1f;

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

    /** The rotation zone (between rallies, before the next serve). */
    private void rotationPosition(VolleyballPlayer p, RotationManager rotation) {
        int zone = rotation.zoneOf(side, p.id);
        if (zone < 0) return;
        CourtZones.basePosition(zone, side, spot);
        moveTo(p, spot.x, spot.y, false);
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
        brain.targetPosition.set(x, z);
        brain.motor.moveTo(p, x, clampZ(z));
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
