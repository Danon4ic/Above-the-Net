package com.volleyball.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.volleyball.game.VolleyballGame;
import com.volleyball.game.ai.AiState;
import com.volleyball.game.ai.TeamAI;
import com.volleyball.game.audio.SoundDirector;
import com.volleyball.game.audio.SoundSink;
import com.volleyball.game.multiplayer.ControllerKind;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.ui.ScoreOverlay;
import com.volleyball.game.ui.PauseMenu;
import com.volleyball.game.ui.SubstitutionMenu;
import com.volleyball.game.ui.RoleGuide;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.config.GameConfig;
import com.volleyball.game.core.FixedStepLoop;
import com.volleyball.game.debug.BallLab;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.input.GdxKeyState;
import com.volleyball.game.input.InputController;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.physics.PlayerLocomotion;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.render.ArenaScene;
import com.volleyball.game.render.BallRenderer;
import com.volleyball.game.render.CameraController;
import com.volleyball.game.render.CameraMode;
import com.volleyball.game.render.PlayerRenderer;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.simulation.TeamCatalog;
import com.volleyball.game.simulation.TeamFactory;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.match.SidelineCoaches;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RallyManager;
import com.volleyball.game.rules.RallyOutcome;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.training.AutoTrainee;
import com.volleyball.game.training.Drill;
import com.volleyball.game.training.DrillController;
import com.volleyball.game.ui.DebugHud;
import com.volleyball.game.ui.DrillMenu;
import com.volleyball.game.ui.HelpMenu;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.VolleyballCourt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The game screen: practice drills and the quick match (one person, or two on one keyboard in
 * versus / co-op), with the AI teams, sound and the score graphics.
 */
public final class MatchScreen extends ScreenAdapter {
    /** Physics/logic rate. 120 Hz keeps fast spikes (~30 m/s) under 25 cm of travel per step. */
    public static final int SIMULATION_HZ = 120;
    private static final int CONTACT_LOG_SIZE = 2;
    private static final Drill[] DRILL_KEYS_F5_TO_F9 = {Drill.SERVE, Drill.RECEIVE, Drill.SET, Drill.ATTACK, Drill.FREE};

    private final VolleyballGame game;
    private final GameConfig config;
    private final VolleyballCourt court;
    private final GameWorld world;
    private final FixedStepLoop loop = new FixedStepLoop(SIMULATION_HZ, 8);
    private final TrajectoryPredictor.Prediction forecast = new TrajectoryPredictor.Prediction();
    private final Deque<String> contactLog = new ArrayDeque<>();
    private final List<String> status = new ArrayList<>();
    private final BallLab ballLab;
    private final DrillController drills;
    private final AutoTrainee autoTrainee;
    private final Vector3 aim = new Vector3();
    private final Vector3 controlForward = new Vector3();
    private final Vector3 followPoint = new Vector3();
    private final Vector3 eyePoint = new Vector3();
    /** First-person mouse look is on: the cursor is hidden and the view follows the mouse. */
    private boolean mouseLook;
    /** The first mouse movement after hiding the cursor is the jump to the centre, not a look. */
    private boolean skipLookDelta;
    private boolean aimValid;
    private boolean autoplay;
    private boolean showAi;
    /** Show the technical status lines (ball physics, rotations, contacts). */
    private boolean showDetails;
    private final MatchSetup setup;
    private boolean roleAnnounced;

    private ArenaScene scene;
    private PlayerRenderer playerRenderer;
    private final com.badlogic.gdx.math.Vector3 heldBall = new com.badlogic.gdx.math.Vector3();
    /** The two head coaches on the sideline (figures only). */
    private final SidelineCoaches coaches;
    /** Seed of the crowd and commentary sounds, drawn with the others from the session seed. */
    private final long soundSeed;
    /** The first referee on the stand (hand signals). */
    private final com.volleyball.game.match.Referee referee = new com.volleyball.game.match.Referee();
    /** Players and coaches: everything the player renderer draws this frame. */
    private final List<VolleyballPlayer> figures = new java.util.ArrayList<>();
    private BallRenderer ballRenderer;
    private CameraController cameraController;
    private InputController input;
    /** Second person (local two-player matches). */
    private InputController input2;
    private SoundSink sound;
    private SoundDirector soundDirector;
    private ScoreOverlay scoreOverlay;
    private DebugHud hud;
    private HelpMenu helpMenu;
    private DrillMenu drillMenu;
    private PauseMenu pauseMenu;
    private SubstitutionMenu substitutionMenu;
    private com.volleyball.game.ui.ShotSelector shotSelector;
    private com.volleyball.game.ui.MatchHud matchHud;
    private final com.volleyball.game.ui.MatchHud.Model hudModel = new com.volleyball.game.ui.MatchHud.Model();
    private com.volleyball.game.ui.StatsPanel statsPanel;
    private com.volleyball.game.ui.ReplayOverlay replayOverlay;
    private com.volleyball.game.match.ReplayRecorder replay;
    private boolean replaying;
    private float replayTime;
    private boolean replaySlow;
    /** The automatic replay of a set point: the last seconds of the rally, at half speed. */
    private boolean autoReplay;
    /** Seconds until the set point's replay starts (negative: none pending). */
    private float autoReplayIn = -1f;
    /** Manually chosen replay angle (index into REPLAY_ANGLES), or -1 for the automatic sequence. */
    private int replayAngle = -1;
    private CameraMode cameraBeforeReplay;
    private static final CameraMode[] REPLAY_ANGLES = {CameraMode.TV_BROADCAST, CameraMode.ATTACKER, CameraMode.SIDELINE,
            CameraMode.OVERHEAD, CameraMode.BEHIND_BASELINE};
    /** World time of the serve (the rally clock), -1 between rallies. */
    private double rallyStartedAt = -1;
    private double matchFinishedAt = -1;
    private boolean finalStatsShown;
    private com.volleyball.game.ui.TacticsPanel tacticsPanel;
    private com.volleyball.game.tactics.TeamCommand.Group tacticsGroup = com.volleyball.game.tactics.TeamCommand.Group.OFFENSE;
    /** Seconds the tactics panel stays visible after a command (it is also shown while Alt is held). */
    private float tacticsShowTime;
    private com.volleyball.game.tactics.Playbook playbook;
    private com.volleyball.game.ai.ShotAdvisor advisor;
    private final List<com.volleyball.game.ui.ShotSelector.Option> selectorOptions = new java.util.ArrayList<>();
    private boolean showForecastPath;
    private float fpsLogTimer;

    public MatchScreen(VolleyballGame game) {
        this(game, MatchSetup.drill(Drill.SERVE));
    }

    public MatchScreen(VolleyballGame game, MatchSetup setup) {
        this.game = game;
        this.setup = setup;
        this.config = game.config();
        this.court = new VolleyballCourt(config.netCategory());
        // One seed drives every random choice of the session, so a match can be replayed with --seed.
        long seed = game.launchOptions().seed() != null ? game.launchOptions().seed() : System.nanoTime();
        Gdx.app.log("Match", "seed " + seed);
        java.util.Random seeds = new java.util.Random(seed);
        TeamCatalog.Entry[] teams = setup.teams(new java.util.Random(seeds.nextLong()));
        // The person's team in the chosen kit; the opponent changes to its away kit if the kits clash.
        com.volleyball.game.entities.Team homeTeam = TeamFactory.build(teams[0], CourtSide.HOME, 0, teams[1].shirt(), setup.awayKit());
        this.world = new GameWorld(court, homeTeam, TeamFactory.build(teams[1], CourtSide.AWAY, 6, homeTeam.shirtColor, false),
                seeds.nextLong());
        this.coaches = new SidelineCoaches(world.home(), world.away(), seeds.nextLong());
        this.ballLab = new BallLab(world);
        this.drills = new DrillController(world, seeds.nextLong());
        this.soundSeed = seeds.nextLong();
        world.addContactListener(ballLab);
        world.addContactListener(this::logContact);
        this.autoTrainee = new AutoTrainee(world);
        this.autoplay = setup.autoplay();
        drills.setMatchOptions(setup.difficulty(), setup.players());
        drills.setMatchSetup(setup.role(), setup.format());
        drills.setFirstServe(setup.firstServe());
        drills.setSystem(setup.system());
        drills.start(setup.drill());
    }

    /** The camera to start with: the one given for this run ({@code --camera}), else the saved one. */
    private com.volleyball.game.render.CameraMode launchCamera() {
        String name = game.launchOptions().camera();
        if (name != null) {
            for (com.volleyball.game.render.CameraMode m : com.volleyball.game.render.CameraMode.values()) {
                if (m.name().equals(name)) return m;
            }
        }
        return config.defaultCamera();
    }

    @Override
    public void show() {
        scene = new ArenaScene(court, config.venue(), config.courtTheme(), config.lighting(), config.graphicsQuality(),
                ArenaScene.Matchup.of(world.home(), world.away()));
        scene.setShadowMapSize(config.shadowMapSize);
        playerRenderer = new PlayerRenderer(List.of(world.home(), world.away()));
        playerRenderer.setBall(world.ball());
        playerRenderer.addCoach(coaches.coaches().get(0), world.home());
        playerRenderer.addCoach(coaches.coaches().get(1), world.away());
        playerRenderer.addReferee(referee.figure());
        ballRenderer = new BallRenderer();
        cameraController = new CameraController(config.fieldOfView, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cameraController.setSensitivity(config.mouseSensitivity);
        cameraController.setMode(launchCamera(), true);
        input = new InputController(new GdxKeyState(config.keyBindings()));
        input2 = new InputController(new GdxKeyState(config.keyBindingsPlayer2()));
        sound = game.sound();
        soundDirector = new SoundDirector(world, sound, soundSeed).attach();
        drills.addMatchListener(soundDirector);
        scoreOverlay = new ScoreOverlay(world, game.fonts());
        scoreOverlay.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        drills.addMatchListener(scoreOverlay);
        drills.addMatchListener(referee);
        drills.addMatchListener(scene.crowdMood());
        hud = new DebugHud(game.fonts());
        hud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shotSelector = new com.volleyball.game.ui.ShotSelector(game.fonts());
        shotSelector.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        matchHud = new com.volleyball.game.ui.MatchHud(game.fonts());
        if (!config.showMatchHints) matchHud.startFolded();
        matchHud.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        statsPanel = new com.volleyball.game.ui.StatsPanel(game.fonts());
        statsPanel.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        replayOverlay = new com.volleyball.game.ui.ReplayOverlay(game.fonts());
        replayOverlay.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        replay = new com.volleyball.game.match.ReplayRecorder(world.players(), world.ball());
        world.setFatigue(config.fatigue);
        // The rally clock and the replay start with every serve.
        drills.addMatchListener(new com.volleyball.game.match.MatchListener() {
            @Override
            public void onServeWhistle(VolleyballPlayer server, MatchScore score) {
                replay.start();
                rallyStartedAt = world.time();
            }

            @Override
            public void onPoint(MatchRules.PointSummary point, MatchScore score) {
                rallyStartedAt = -1;
                // The point that won a set is shown again once the banner has been read.
                if (point.setWon() && !point.matchWon() && !point.outcome().isReplay()) autoReplayIn = 1.6f;
            }
        });
        tacticsPanel = new com.volleyball.game.ui.TacticsPanel(game.fonts());
        tacticsPanel.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        // The plays: built-in plus the custom ones from the playbook editor.
        playbook = new com.volleyball.game.tactics.PlaybookStore(com.volleyball.game.config.ConfigStore.userDataDirectory()).load();
        advisor = new com.volleyball.game.ai.ShotAdvisor(world);
        helpMenu = new HelpMenu(config.keyBindings(), game.fonts());
        helpMenu.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        drillMenu = new DrillMenu(game.fonts());
        drillMenu.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        pauseMenu = new PauseMenu(game.fonts());
        pauseMenu.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        substitutionMenu = new SubstitutionMenu(world, game.fonts());
        substitutionMenu.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (config.showHelpOnStart) {
            // First launch: show the controls once, then only on F1.
            helpMenu.toggle();
            config.showHelpOnStart = false;
            game.saveConfig();
        }
        Gdx.input.setInputProcessor(new CameraInput());
        onDrillStarted();
    }

    @Override
    public void render(float delta) {
        if (replaying) updateReplay(delta);
        else if (statsPanel.isOpen()) handleStatsKeys();
        else handleMenuKeys();
        autoOpenFinalStats();
        VolleyballPlayer trainee = drills.trainee();
        // The game is paused while a menu is open (and during a replay): no gameplay input, no simulation.
        boolean paused = helpMenu.isOpen() || drillMenu.isOpen() || pauseMenu.isOpen() || substitutionMenu.isOpen()
                || statsPanel.isOpen() || replaying;
        updateAutoReplay(delta, paused);
        updateMouseLook(paused);
        if (!paused) {
            handleKeys(trainee);
            trainee = drills.trainee();
            input.poll(world.time());
            updateAim(trainee);
            handleBallActions(trainee);
            VolleyballPlayer second = secondPerson();
            if (second != null) {
                input2.poll(world.time());
                handleSecondPerson(second);
            }
            loop.advance(delta, this::simulationStep);
        }
        float alpha = loop.alpha();
        VolleyballPlayer second = secondPerson();

        // Two people share the screen: follow the point between them.
        followPoint.set(trainee.previousPosition).lerp(trainee.position, alpha);
        if (second != null) followPoint.add(second.position).scl(0.5f);
        cameraController.follow(followPoint, -trainee.side.sign());
        cameraController.setEyeHeight(trainee.height);
        // First person: the eyes are in the head as it was drawn (one frame back): down in a crouch, along in a dive or a fall.
        cameraController.setEyes(second == null && playerRenderer.eyes(trainee, eyePoint) ? eyePoint : null);
        if (!replaying) updateCameraSubject(trainee);
        cameraController.update(delta);
        updateSound(delta, paused);

        Ball ball = world.ball();
        if (ball.isActive()) world.predictor().predict(ball, 6f, forecast);

        updateScoreboard();
        // The teams change ends after a set (and at 8 in the deciding set): the hall turns around them.
        boolean swapped = world.benchSign() < 0f;
        scene.setSidesSwapped(swapped);
        cameraController.setSidesSwapped(swapped);
        scene.update(delta);
        fpsLogTimer += delta;
        if (fpsLogTimer > (System.getenv("FPS_TRACE") != null ? 1f : 5f)) {
            fpsLogTimer = 0f;
            Gdx.app.log("Perf", Gdx.graphics.getFramesPerSecond() + " FPS (" + scene.quality().label + ", " + scene.venue().label + ")");
        }
        playerRenderer.setCamera(cameraController.camera().position);
        boolean firstPerson = cameraController.mode() == CameraMode.FIRST_PERSON && !replaying;
        playerRenderer.setCameraClearance(firstPerson ? 0.45f : 1.8f);
        // First person: your own arms and legs are drawn (not the head the eyes are in).
        playerRenderer.setFirstPersonPlayer(firstPerson ? trainee : null);
        boolean realShadows = scene.quality().arenaShader;
        playerRenderer.setBlobShadows(!realShadows);
        ballRenderer.setBlobShadow(!realShadows);
        ballRenderer.setTrail(scene.quality() == com.volleyball.game.render.GraphicsQuality.HIGH
                || scene.quality() == com.volleyball.game.render.GraphicsQuality.ULTRA);
        figures.clear();
        figures.addAll(world.players());
        figures.addAll(coaches.coaches());
        figures.add(referee.figure());
        scene.setOccluders(figures, ball);
        VolleyballPlayer drawnTrainee = trainee;
        VolleyballPlayer drawnSecond = second;
        scene.render(cameraController.camera(), (batch, environment, mirror) -> {
            if (mirror) {
                playerRenderer.drawLast(batch, environment);
                ballRenderer.drawLast(batch, environment);
                return;
            }
            playerRenderer.render(batch, environment, figures, alpha, drawnTrainee, drawnSecond, ball.position);
            com.volleyball.game.mechanics.BallHandling bh = world.ballHandling();
            ballRenderer.setHeldAt(bh.servePhase() == com.volleyball.game.mechanics.BallHandling.ServePhase.HOLDING && bh.server() != null
                    ? playerRenderer.heldBallPoint(bh.server(), heldBall) : null);
            ballRenderer.render(batch, environment, ball, alpha, ball.isActive() ? forecast : null, showForecastPath);
            if (aimValid && showsAim()) ballRenderer.renderAim(batch, aim.x, aim.z);
            if (drawnTrainee != null && config.landingMarker && !autoplay && !replaying) receptionMarker(batch, drawnTrainee, ball);
        });

        renderMatchHud(trainee);
        if (mouseLook) matchHud.renderCrosshair(aimValid);
        hud.render(Gdx.graphics.getHeight(), buildStatus(trainee, ball));
        if (!autoplay && !paused) renderSelector(trainee, delta);
        if (!paused) handleTacticsKeys(trainee);
        renderTactics(trainee, delta);
        String prompt = replaying ? null : servePrompt();
        if (prompt == null && !replaying) prompt = playPrompt(trainee);
        scoreOverlay.setPrompt(prompt);
        scoreOverlay.setYourSide(autoplay ? null : trainee.side);
        scoreOverlay.setActions(paused ? java.util.List.of() : actionHints(trainee));
        scoreOverlay.render(drills.match(), paused ? 0f : delta);
        helpMenu.render();
        drillMenu.render(drills.drill(), drills.difficulty(), drills.sessionMode(), drills.role(), drills.system());
        substitutionMenu.render();
        pauseMenu.render(delta);
        if (replaying) replayOverlay.render(delta, replay.duration() <= 0f ? 1f : replayTime / replay.duration(),
                cameraController.mode().label, replaySlow);
        renderStats(delta);
        if (pendingAction != null) {
            PauseMenu.Action action = pendingAction;
            pendingAction = null;
            perform(action);
        }
    }

    private PauseMenu.Action pendingAction;

    // ------------------------------------------------------------------ match HUD, statistics, timeouts, replay

    /** The person's card: player, stamina, touches, rally time, server, rotation, commands. */
    private void renderMatchHud(VolleyballPlayer p) {
        MatchController match = drills.match();
        if (match == null || replaying) return;
        BallHandling handling = world.ballHandling();
        com.volleyball.game.ui.MatchHud.Model m = hudModel;
        m.mode = "Quick match  ·  " + match.system().label + "  ·  AI " + drills.difficulty().label + (autoplay ? "  ·  watching (F10)" : "");
        m.number = p.number;
        m.name = p.name;
        m.role = p.role.label;
        m.traits = p.traitLabel();
        m.teamColor.set((p.side == CourtSide.HOME ? world.home() : world.away()).shirtColor);
        m.stamina = config.fatigue ? p.stamina() : Float.NaN;
        m.touches = handling.lastTouchSide() == p.side && world.ball().isActive() ? handling.teamTouches(p.side) : 0;
        m.rallyTime = match.phase() == MatchController.Phase.RALLY && rallyStartedAt >= 0 ? (float) (world.time() - rallyStartedAt) : -1f;
        VolleyballPlayer server = match.currentServer();
        m.server = server == null ? "-" : "#" + server.number;
        m.youServe = server == p;
        RotationManager rotation = match.rules().rotation();
        for (int zone = 1; zone <= 6; zone++) {
            VolleyballPlayer z = world.player(rotation.playerInZone(p.side, zone));
            m.rotation[zone - 1] = z == null ? 0 : z.number;
        }
        int zone = rotation.zoneOf(p.side, p.id);
        m.yourZone = Math.max(0, zone);
        m.serverZone = server != null && server.side == p.side ? 1 : 0;
        com.volleyball.game.tactics.TeamTactics tactics = match.ai(p.side).tactics();
        com.volleyball.game.tactics.TeamCommand o = tactics.active(com.volleyball.game.tactics.TeamCommand.Group.OFFENSE);
        com.volleyball.game.tactics.TeamCommand d = tactics.active(com.volleyball.game.tactics.TeamCommand.Group.DEFENSE);
        com.volleyball.game.tactics.TeamCommand sv = tactics.active(com.volleyball.game.tactics.TeamCommand.Group.SERVE);
        m.offense = o == null ? null : o.label;
        m.defense = d == null ? null : d.label;
        m.serve = sv == null ? null : sv.label;
        m.timeoutsLeft = match.timeoutsLeft(p.side);
        m.footer = match.lastPoint() == null ? null : "Last point: " + describe(match.lastPoint());
        matchHud.render(m, true);
    }

    private void requestTimeout(VolleyballPlayer p) {
        MatchController match = drills.match();
        if (match == null) return;
        com.badlogic.gdx.graphics.Color color = (p.side == CourtSide.HOME ? world.home() : world.away()).shirtColor;
        if (match.timeoutRequested()) return;
        if (match.requestTimeout(p.side)) {
            scoreOverlay.announce("TIMEOUT REQUESTED", "It is taken at the next break in play", color, 2f);
        } else {
            scoreOverlay.announce("NO TIMEOUT", match.timeoutsLeft(p.side) <= 0 ? "Both timeouts of this set are used" : "Not now",
                    color, 2f);
        }
    }

    private void handleStatsKeys() {
        boolean finished = drills.match() != null && drills.match().phase() == MatchController.Phase.FINISHED;
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) statsPanel.switchTeam();
        if (finished && (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE))) {
            game.showMainMenu();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.K) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) statsPanel.close();
    }

    /** After the final point the statistics open by themselves (a moment after the winner's banner). */
    private void autoOpenFinalStats() {
        MatchController match = drills.match();
        if (match == null || match.phase() != MatchController.Phase.FINISHED) {
            matchFinishedAt = -1;
            finalStatsShown = false;
            return;
        }
        if (matchFinishedAt < 0) matchFinishedAt = world.time();
        if (!finalStatsShown && world.time() - matchFinishedAt > 3.0) {
            finalStatsShown = true;
            statsPanel.show(drills.trainee().side);
            statsPanel.open();
        }
    }

    private void renderStats(float delta) {
        MatchController match = drills.match();
        if (match == null || !statsPanel.isOpen()) return;
        MatchScore score = match.rules().score();
        boolean finished = match.phase() == MatchController.Phase.FINISHED;
        String title;
        if (finished) {
            CourtSide winner = score.matchWinner();
            title = (winner == CourtSide.HOME ? world.home().name : world.away().name).toUpperCase(java.util.Locale.ROOT) + " WIN";
        } else {
            title = "MATCH STATISTICS";
        }
        String subtitle = String.format(java.util.Locale.ROOT, "Sets %d : %d   ·   Set %d   ·   %d : %d", score.sets(CourtSide.HOME), score.sets(CourtSide.AWAY),
                score.setNumber(), score.points(CourtSide.HOME), score.points(CourtSide.AWAY));
        String footer = finished ? "Tab: the other team's players   ·   Enter: main menu"
                : "Tab: the other team's players   ·   K / Esc: back to the match";
        statsPanel.render(delta, match.statistics(), world.home().name, world.away().name, world.home().shirtColor,
                world.away().shirtColor, title, subtitle, footer);
    }

    /** The attacker and server cameras look over the right player. */
    private void updateCameraSubject(VolleyballPlayer trainee) {
        MatchController match = drills.match();
        CameraMode mode = cameraController.mode();
        if (mode == CameraMode.SERVER) {
            VolleyballPlayer server = match != null ? match.currentServer() : world.ballHandling().server();
            if (server == null) server = trainee;
            cameraController.setSubject(server.position, -server.side.sign());
        } else if (mode == CameraMode.ATTACKER) {
            VolleyballPlayer hitter = null;
            CourtSide side = world.ballHandling().lastTouchSide();
            if (match != null && side != null) {
                int id = match.ai(side).attackerId();
                hitter = id < 0 ? null : world.player(id);
            }
            if (hitter == null) hitter = trainee;
            cameraController.setSubject(hitter.position, -hitter.side.sign());
        }
    }

    /** A replay can be watched between rallies (and after the match). */
    private boolean canReplay() {
        MatchController match = drills.match();
        return match != null && replay != null && replay.hasReplay() && match.phase() != MatchController.Phase.RALLY
                && match.phase() != MatchController.Phase.PREPARE;
    }

    /** The set point's replay, when it is due (and nothing else is open). */
    private void updateAutoReplay(float delta, boolean paused) {
        if (autoReplayIn < 0f || replaying) return;
        if (paused) return;
        autoReplayIn -= delta;
        if (autoReplayIn > 0f) return;
        autoReplayIn = -1f;
        if (!canReplay()) return;
        startReplay();
        autoReplay = true;
        replayTime = Math.max(0f, replay.duration() - 3.5f);
    }

    private void startReplay() {
        autoReplay = false;
        replay.stop();
        replay.saveLive();
        replaying = true;
        replayTime = 0f;
        replaySlow = false;
        replayAngle = -1;
        cameraBeforeReplay = cameraController.mode();
        helpMenu.close();
        pauseMenu.close();
    }

    /** Plays the recorded rally: automatic angles (or the chosen one), slow motion on Space. */
    private void updateReplay(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) replaySlow = !replaySlow;
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) replayAngle = (replayAngle + 1) % REPLAY_ANGLES.length;
        if (Gdx.input.isKeyJustPressed(Input.Keys.V) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            endReplay();
            return;
        }
        replayTime += delta * (replaySlow ? 0.3f : autoReplay ? 0.5f : 1f);
        float duration = replay.duration();
        if (replayTime > duration + 0.8f) {
            endReplay();
            return;
        }
        replay.apply(replayTime);
        int angle = replayAngle >= 0 ? replayAngle : Math.min(2, (int) (replayTime / Math.max(1f, duration) * 3f));
        CameraMode mode = REPLAY_ANGLES[angle];
        if (cameraController.mode() != mode) cameraController.setMode(mode, false);
        Ball ball = world.ball();
        cameraController.setSubject(ball.position, ball.velocity.x == 0f ? 1f : Math.signum(ball.velocity.x));
    }

    private void endReplay() {
        replay.restoreLive();
        replaying = false;
        if (cameraBeforeReplay != null) cameraController.setMode(cameraBeforeReplay, false);
    }

    private static boolean altHeld() {
        return Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
    }

    /**
     * Team commands: G shows the next group, Alt + 1..9 gives a command of the shown group (the
     * same again takes it back), Alt + 0 leaves the group to the team. Only in a match, for the
     * person's team.
     */
    private void handleTacticsKeys(VolleyballPlayer p) {
        MatchController match = drills.match();
        if (match == null || autoplay) return;
        com.volleyball.game.tactics.TeamTactics tactics = match.ai(p.side).tactics();
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            tacticsGroup = tacticsGroup.next();
            tacticsShowTime = 3f;
        }
        if (!altHeld()) return;
        java.util.List<com.volleyball.game.tactics.TeamCommand> commands = com.volleyball.game.tactics.TeamCommand.of(tacticsGroup);
        for (int n = 0; n <= 9; n++) {
            if (!Gdx.input.isKeyJustPressed(Input.Keys.NUM_0 + n) && !Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_0 + n)) continue;
            tacticsShowTime = 3f;
            if (n == 0) {
                tactics.clear(tacticsGroup);
                continue;
            }
            if (n > commands.size()) continue;
            com.volleyball.game.tactics.TeamCommand command = commands.get(n - 1);
            if (command == com.volleyball.game.tactics.TeamCommand.SERVE_RECEIVER) {
                // Each press: the next receiver of the opponent.
                tactics.set(command);
                tactics.setServeTarget(nextReceiver(p.side.opposite(), tactics.serveTarget()));
            } else {
                tactics.toggle(command);
            }
        }
    }

    /** The next opponent receiver (outside hitters and libero on court) after {@code currentId}. */
    private int nextReceiver(CourtSide opponent, int currentId) {
        java.util.List<VolleyballPlayer> receivers = new java.util.ArrayList<>();
        for (VolleyballPlayer o : (opponent == CourtSide.HOME ? world.home() : world.away()).onCourt()) {
            if (o.role == com.volleyball.game.entities.PlayerRole.OUTSIDE_HITTER || o.role == com.volleyball.game.entities.PlayerRole.LIBERO) {
                receivers.add(o);
            }
        }
        if (receivers.isEmpty()) return -1;
        for (int i = 0; i < receivers.size(); i++) {
            if (receivers.get(i).id == currentId) return receivers.get((i + 1) % receivers.size()).id;
        }
        return receivers.get(0).id;
    }

    private void renderTactics(VolleyballPlayer p, float delta) {
        MatchController match = drills.match();
        if (match == null || autoplay) return;
        tacticsShowTime = Math.max(0f, tacticsShowTime - delta);
        float alpha = altHeld() ? 1f : Math.min(1f, tacticsShowTime / 0.5f);
        com.volleyball.game.tactics.TeamTactics tactics = match.ai(p.side).tactics();
        String extra = null;
        if (tacticsGroup == com.volleyball.game.tactics.TeamCommand.Group.SERVE && tactics.is(com.volleyball.game.tactics.TeamCommand.SERVE_RECEIVER)) {
            VolleyballPlayer target = world.player(tactics.serveTarget());
            extra = target == null ? "Target: the weakest receiver" : "Target: #" + target.number + " " + target.name;
        }
        tacticsPanel.render(tacticsGroup, tactics, extra, alpha);
    }

    /** The person's part in the team's play (a run, a decoy, the set coming to them), while it matters. */
    private String playPrompt(VolleyballPlayer p) {
        MatchController match = drills.match();
        if (match == null || autoplay || !world.ball().isActive()) return null;
        BallHandling handling = world.ballHandling();
        if (handling.lastTouchSide() != p.side || handling.teamTouches(p.side) < 1 || handling.teamTouches(p.side) > 2) return null;
        String assignment = match.ai(p.side).playAssignment(p);
        return assignment == null ? null : "PLAY  ·  " + assignment.toUpperCase(java.util.Locale.ROOT);
    }

    private final List<String[]> actionKeys = new ArrayList<>();
    private com.volleyball.game.input.KeyBindings bindings;

    /** The key of an action as shown on a key cap ("LMB", "Space", "Q"). */
    private String keyLabel(com.volleyball.game.input.PlayerAction action) {
        if (bindings == null) bindings = config.keyBindings();
        String name = com.volleyball.game.input.KeyBindings.nameOf(bindings.key(action));
        // Translated here: key labels are also joined ("Ctrl+LMB").
        return com.volleyball.game.i18n.Lang.t(switch (name) {
            case "Mouse Left" -> "LMB";
            case "Mouse Right" -> "RMB";
            case "Mouse Middle" -> "MMB";
            case "L-Shift" -> "Shift";
            case "L-Ctrl" -> "Ctrl";
            default -> name;
        });
    }

    /**
     * What the person's player can do right now, read from the rally's real state: the serve
     * (toss, then hit; the run-up and jump for a jump serve), the pass of a ball coming to them,
     * the set on the second touch, the attack on the third, the block against the other team's
     * attack. Nothing is offered that the player could not do at this moment (a libero is not
     * offered the attack or the block).
     */
    private List<String[]> actionHints(VolleyballPlayer p) {
        actionKeys.clear();
        MatchController match = drills.match();
        if (match == null || autoplay || replaying || p == null || !p.isOnCourt()) return actionKeys;
        BallHandling handling = world.ballHandling();
        Ball ball = world.ball();
        String primary = keyLabel(com.volleyball.game.input.PlayerAction.PRIMARY_ACTION);
        String secondary = keyLabel(com.volleyball.game.input.PlayerAction.SECONDARY_ACTION);
        String jump = keyLabel(com.volleyball.game.input.PlayerAction.JUMP);
        String variants = keyLabel(com.volleyball.game.input.PlayerAction.VARIANT_PREVIOUS) + " / "
                + keyLabel(com.volleyball.game.input.PlayerAction.VARIANT_NEXT);
        if (handling.server() == p && handling.servePhase() != BallHandling.ServePhase.NONE) {
            if (handling.servePhase() == BallHandling.ServePhase.HOLDING) {
                actionKeys.add(new String[]{primary, "Toss"});
                actionKeys.add(new String[]{variants, "Serve type"});
            } else {
                if (handling.serveType(p).jump && !p.isAirborne()) {
                    actionKeys.add(new String[]{keyLabel(com.volleyball.game.input.PlayerAction.MOVE_FORWARD), "Run-up"});
                    actionKeys.add(new String[]{jump, "Jump"});
                }
                actionKeys.add(new String[]{primary, "Hit"});
            }
            return actionKeys;
        }
        if (!ball.isActive()) {
            if (handling.nextServer() == p) actionKeys.add(new String[]{variants, "Serve type"});
            return actionKeys;
        }
        boolean ours = handling.lastTouchSide() == p.side;
        int touches = ours ? handling.teamTouches(p.side) : 0;
        boolean libero = p.role == PlayerRole.LIBERO;
        boolean front = match.rules().rotation().isFrontRow(p.side, p.id);
        boolean ballOnOurSide = CourtDimensions.sideOf(ball.position.x) == p.side;
        boolean comingHere = forecast.lands && CourtDimensions.sideOf(forecast.landing.x) == p.side;
        float toLanding = comingHere ? (float) Math.hypot(forecast.landing.x - p.position.x, forecast.landing.z - p.position.z) : 99f;
        float toBall = (float) Math.hypot(ball.position.x - p.position.x, ball.position.z - p.position.z);
        if (p.isAirborne()) {
            if (ballOnOurSide && toBall < 2.5f && !libero) {
                actionKeys.add(new String[]{primary, "Spike"});
                actionKeys.add(new String[]{secondary, "Tip"});
            }
            return actionKeys;
        }
        if (ours && touches == 1 && comingHere && toLanding < 3.5f) {
            actionKeys.add(new String[]{secondary, "Overhead set"});
            actionKeys.add(new String[]{primary, "Forearm set"});
            actionKeys.add(new String[]{"1-9", "Choose the set"});
            return actionKeys;
        }
        if (ours && touches == 2 && !libero && (comingHere || ballOnOurSide) && Math.min(toLanding, toBall) < 4.5f) {
            actionKeys.add(new String[]{jump, front ? "Jump" : "Jump behind the 3 m line"});
            actionKeys.add(new String[]{primary, "Spike"});
            actionKeys.add(new String[]{secondary, "Tip"});
            return actionKeys;
        }
        if (!ours && comingHere && toLanding < 4.5f) {
            VolleyballPlayer server = match.currentServer();
            boolean reception = server != null && handling.lastToucherId() == server.id;
            actionKeys.add(new String[]{primary, reception ? "Forearm pass" : "Dig"});
            actionKeys.add(new String[]{secondary, "Overhead pass"});
            if (!reception) actionKeys.add(new String[]{keyLabel(com.volleyball.game.input.PlayerAction.CROUCH) + "+" + primary, "Dive"});
            return actionKeys;
        }
        if (!ours && !ballOnOurSide && front && !libero && Math.abs(p.position.x) < 2.2f && handling.teamTouches(p.side.opposite()) >= 1) {
            actionKeys.add(new String[]{jump, "Block"});
            actionKeys.add(new String[]{variants, "Line / diagonal"});
        }
        return actionKeys;
    }

    /** "Your serve": the serve type and how to change it, while the person is (about to be) serving. */
    private String servePrompt() {
        MatchSession session = drills.session();
        if (session == null || autoplay) return null;
        BallHandling handling = world.ballHandling();
        VolleyballPlayer p = drills.trainee();
        boolean holding = handling.server() == p && handling.servePhase() == BallHandling.ServePhase.HOLDING;
        boolean next = handling.nextServer() == p && !world.ball().isActive() && handling.servePhase() == BallHandling.ServePhase.NONE;
        if (!holding && !next) return null;
        return holding ? "YOUR SERVE  ·  " + handling.serveType(p).label.toUpperCase(java.util.Locale.ROOT)
                : "YOU SERVE NEXT  ·  " + handling.serveType(p).label.toUpperCase(java.util.Locale.ROOT);
    }

    private void perform(PauseMenu.Action action) {
        pauseMenu.close();
        switch (action) {
            case RESUME -> {
            }
            case RESTART -> {
                for (VolleyballPlayer p : world.players()) world.intent(p).clear();
                drills.start(drills.drill());
                roleAnnounced = false;
                onDrillStarted();
            }
            case SUBSTITUTIONS -> openSubstitutions();
            case TIMEOUT -> requestTimeout(drills.trainee());
            case STATISTICS -> {
                statsPanel.show(drills.trainee().side);
                statsPanel.open();
            }
            case REPLAY -> {
                if (canReplay()) startReplay();
            }
            case ROLE_GUIDE -> helpMenu.openRoleGuide(drills.role());
            case CONTROLS -> {
                helpMenu.setRole(null);
                helpMenu.toggle();
            }
            case MAIN_MENU -> game.showMainMenu();
            case QUIT -> Gdx.app.exit();
        }
    }

    /** The second person's player in a local two-player match, or null. */
    private VolleyballPlayer secondPerson() {
        MatchSession session = drills.session();
        MatchSession.Slot slot = session == null ? null : session.slot(1);
        return slot != null && slot.controller == ControllerKind.KEYBOARD_ARROWS ? slot.player() : null;
    }

    /**
     * The second person has no mouse: ball actions come from the keys, and serves and attacks
     * aim with the movement keys (held while hitting): sideways for line or cross, towards the
     * net or away for short or deep, nothing for the middle of the opponent's court.
     */
    private void handleSecondPerson(VolleyballPlayer p) {
        InputController.BallActionPress press = input2.ballActionPressed();
        if (press != null) {
            world.ballHandling().arm(p, press == InputController.BallActionPress.PRIMARY ? ActionKind.PRIMARY : ActionKind.SECONDARY,
                    world.time());
        }
        int change = input2.variantChange();
        if (change != 0) world.ballHandling().cycleVariant(p, change);
        if (input2.switchPlayerPressed()) {
            world.intent(p).clear();
            drills.switchPlayer(1);
            input2.clearBuffer();
            return;
        }
        var direction = world.intent(p).direction;
        float opponentSign = -p.side.sign();
        float depth = 6.2f + Math.max(-2.5f, Math.min(2.5f, direction.x * opponentSign * 2.5f));
        world.ballHandling().setAim(p, opponentSign * depth, Math.max(-3.8f, Math.min(3.8f, direction.y * 3.8f)));
    }

    /** Camera position for panning, crowd and menu music, and the libGDX sound loading. */
    private void updateSound(float delta, boolean paused) {
        soundDirector.setListener(cameraController.camera().position, cameraController.camera().direction);
        soundDirector.setMatchActive(drills.drill() == Drill.MATCH);
        soundDirector.setMenuOpen(paused);
        soundDirector.update(paused ? 0f : delta);
    }

    /** After a drill or match (re)starts: the camera for two people is the TV view. */
    private void onDrillStarted() {
        if (cameraController != null && secondPerson() != null) cameraController.setMode(CameraMode.TV_BROADCAST, true);
        if (drills.match() != null && playbook != null) {
            for (CourtSide side : CourtSide.values()) drills.match().ai(side).setPlaybook(playbook);
        }
        if (scoreOverlay != null && drills.match() != null && !roleAnnounced) {
            roleAnnounced = true;
            RoleGuide guide = RoleGuide.of(drills.role());
            scoreOverlay.announce("YOUR ROLE: " + guide.title().toUpperCase(java.util.Locale.ROOT), guide.motto() + "   (Esc: role guide)",
                    world.home().shirtColor, 4.5f);
        }
        input.clearBuffer();
        if (input2 != null) input2.clearBuffer();
    }

    /** One fixed simulation step: human movement, the world, then the drill logic. */
    private void simulationStep(float dt) {
        VolleyballPlayer trainee = drills.trainee();
        MatchSession session = drills.session();
        cameraController.controlForward(controlForward);
        if (session != null) {
            // In the match the demo mode (F10) hands person 1's player over to the team AI.
            drills.setSlotAutomatic(0, autoplay);
            if (!autoplay) input.fillIntent(world.intent(trainee), trainee, controlForward, world.time());
            VolleyballPlayer second = secondPerson();
            if (second != null) input2.fillIntent(world.intent(second), second, controlForward, world.time());
        } else if (autoplay) autoTrainee.update(trainee, drills.drill(), world.time());
        else input.fillIntent(world.intent(trainee), trainee, controlForward, world.time());
        // The people's players get the bigger reach (see BallHandling.ASSIST_REACH); not in a demo.
        world.ballHandling().setAssisted(autoplay ? null : trainee, session != null ? secondPerson() : null);
        world.step(dt);
        coaches.update(dt, drills.match(), world.ball(), world.benchSign());
        referee.update(dt, world.benchSign());
        if (drills.match() != null) replay.capture(dt);
        drills.update(dt);
    }

    private final com.volleyball.game.physics.TrajectoryPredictor.Prediction receptionRead =
            new com.volleyball.game.physics.TrajectoryPredictor.Prediction();

    /** The landing marker: where a ball coming to the person's side drops to passing height (1 m). */
    private void receptionMarker(com.badlogic.gdx.graphics.g3d.ModelBatch batch, VolleyballPlayer p, Ball ball) {
        if (!ball.isActive() || world.ballHandling().lastTouchSide() == p.side) return;
        receptionRead.watchHeight = 1.0f;
        world.predictor().predict(ball, 6f, receptionRead);
        receptionRead.watchHeight = Float.NaN;
        if (!receptionRead.reachesWatchHeight || CourtDimensions.sideOf(receptionRead.descentPoint.x) != p.side) return;
        ballRenderer.renderReception(batch, receptionRead.descentPoint.x, receptionRead.descentPoint.z, receptionRead.timeToDescent);
    }

    /**
     * First person: the view follows the mouse's movement without a button (the cursor is hidden
     * meanwhile). A menu, the pause or a replay gives the cursor back.
     */
    private void updateMouseLook(boolean paused) {
        boolean look = config.firstPersonMouseLook && !autoplay && !paused && cameraController.mode() == CameraMode.FIRST_PERSON;
        if (look != mouseLook) {
            mouseLook = look;
            Gdx.input.setCursorCatched(look);
            skipLookDelta = true;
        }
        if (!look) return;
        int dx = Gdx.input.getDeltaX();
        int dy = Gdx.input.getDeltaY();
        if (skipLookDelta) {
            skipLookDelta = false;
            return;
        }
        cameraController.look(dx, config.invertMouseY ? -dy : dy);
    }

    /**
     * Projects the mouse cursor onto the floor: that point is the aim for serves, attacks and free
     * balls. With the first-person mouse look the aim is the centre of the screen.
     */
    private void updateAim(VolleyballPlayer trainee) {
        Ray ray = mouseLook
                ? cameraController.camera().getPickRay(Gdx.graphics.getWidth() / 2f, Gdx.graphics.getHeight() / 2f)
                : cameraController.camera().getPickRay(Gdx.input.getX(), Gdx.input.getY());
        aimValid = false;
        boolean onFloor = false;
        if (ray.direction.y < -1e-4f) {
            float t = -ray.origin.y / ray.direction.y;
            aim.set(ray.direction).scl(t).add(ray.origin);
            onFloor = t < 120f;
            aimValid = CourtDimensions.isInPlayingArea(aim.x, aim.z);
        }
        // The body faces the cursor's floor point (anywhere on the floor, not only in the court).
        input.setMouseFacing(config.mouseFacing && !autoplay);
        if (mouseLook && !onFloor) {
            // Looking up (at a high ball): the body still turns where the eyes look.
            cameraController.controlForward(controlForward);
            input.setFacingTarget(trainee.position.x + controlForward.x * 5f, trainee.position.z + controlForward.z * 5f, true);
        } else {
            input.setFacingTarget(aim.x, aim.z, onFloor);
        }
        if (aimValid) world.ballHandling().setAim(trainee, aim.x, aim.z);
        else world.ballHandling().clearAim(trainee);
    }

    private boolean showsAim() {
        // In a match too: the ring shows where the body faces and where serves and attacks go.
        return drills.drill() == Drill.SERVE || drills.drill() == Drill.ATTACK || drills.drill() == Drill.FREE
                || (drills.match() != null && !autoplay);
    }

    private void handleBallActions(VolleyballPlayer trainee) {
        InputController.BallActionPress press = autoplay ? null : input.ballActionPressed();
        if (press != null) {
            world.ballHandling().arm(trainee,
                    press == InputController.BallActionPress.PRIMARY ? ActionKind.PRIMARY : ActionKind.SECONDARY, world.time());
        }
        int change = input.variantChange();
        if (change != 0) world.ballHandling().cycleVariant(trainee, change);
        // Number keys: direct choice (0 = Auto). In the ball lab they launch test balls instead.
        if (drills.drill() != Drill.FREE && !altHeld()) {
            for (int n = 0; n <= 9; n++) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0 + n) || Gdx.input.isKeyJustPressed(Input.Keys.NUMPAD_0 + n)) {
                    world.ballHandling().selectVariant(trainee, n);
                }
            }
        }
        resolveAuto(trainee);
    }

    /** "Auto": the set or shot the situation calls for (see ShotAdvisor), updated until the contact. */
    private void resolveAuto(VolleyballPlayer p) {
        BallHandling handling = world.ballHandling();
        MatchController match = drills.match();
        com.volleyball.game.rules.RotationManager rotation = match == null ? null : match.rules().rotation();
        if (handling.isAutoSet(p)) {
            // A person setting on Auto plays the team's combination (or what the situation calls for).
            SetType playSet = match == null ? null : match.ai(p.side).playSet();
            handling.setSetType(p, playSet != null ? playSet : advisor.recommendSet(p, rotation));
        }
        if (handling.isAutoAttack(p)) {
            // A setter attacking the second ball dumps it.
            boolean secondBall = p.role == com.volleyball.game.entities.PlayerRole.SETTER && handling.teamTouches(p.side) == 1;
            handling.setAttackType(p, advisor.recommendAttack(p, secondBall));
        }
    }

    /** The play caller at the bottom: the options of the decision at hand, with their keys. */
    private void renderSelector(VolleyballPlayer p, float delta) {
        BallHandling handling = world.ballHandling();
        ContactPurpose context = handling.variantContext(p);
        // The libero may neither attack above the net nor block: no shot or block choices for it.
        if (p.role == PlayerRole.LIBERO && context != ContactPurpose.SET && context != ContactPurpose.SERVE) return;
        int selected = handling.selectedVariant(p, context);
        selectorOptions.clear();
        String title;
        String hint;
        boolean hot;
        boolean ballHere = world.ball().isActive() && handling.lastTouchSide() == p.side;
        switch (context) {
            case SERVE -> {
                title = "SERVE";
                ServeType[] types = ServeType.values();
                for (int i = 0; i < types.length; i++) {
                    selectorOptions.add(new com.volleyball.game.ui.ShotSelector.Option(String.valueOf(i + 1), types[i].label, null, true,
                            selected == i + 1, false));
                }
                hint = null;
                hot = handling.server() == p;
            }
            case BLOCK -> {
                title = "BLOCK";
                com.volleyball.game.mechanics.BlockStyle[] styles = com.volleyball.game.mechanics.BlockStyle.values();
                for (int i = 0; i < styles.length; i++) {
                    selectorOptions.add(new com.volleyball.game.ui.ShotSelector.Option(String.valueOf(i + 1),
                            styles[i].label.replace("Block: ", ""), null, true, selected == i + 1, false));
                }
                hint = null;
                hot = true;
            }
            case SET -> {
                title = "SET";
                MatchController match = drills.match();
                java.util.EnumMap<SetType.Lane, VolleyballPlayer> hitters = advisor.hitters(p, match == null ? null : match.rules().rotation());
                SetType recommended = advisor.recommendSet(p, match == null ? null : match.rules().rotation());
                selectorOptions.add(new com.volleyball.game.ui.ShotSelector.Option("0", "Auto",
                        selected == 0 ? handling.setType(p).shortLabel : null, true, selected == 0, false));
                SetType[] types = SetType.values();
                for (int i = 0; i < types.length; i++) {
                    VolleyballPlayer hitter = hitters.get(types[i].lane);
                    selectorOptions.add(new com.volleyball.game.ui.ShotSelector.Option(String.valueOf(i + 1), types[i].shortLabel,
                            hitter == null ? "no hitter" : "#" + hitter.number + " " + hitter.name, hitter != null, selected == i + 1,
                            types[i] == recommended));
                }
                hint = selected == 0 ? "Auto: " + handling.setType(p).label + " (picked from the pass and the block)" : handling.setType(p).label;
                hot = ballHere && handling.teamTouches(p.side) == 1;
            }
            default -> {
                title = "SHOT";
                boolean secondBall = p.role == com.volleyball.game.entities.PlayerRole.SETTER && handling.teamTouches(p.side) == 1;
                AttackType recommended = advisor.recommendAttack(p, secondBall);
                selectorOptions.add(new com.volleyball.game.ui.ShotSelector.Option("0", "Auto",
                        selected == 0 ? handling.attackType(p).shortLabel : null, true, selected == 0, false));
                AttackType[] types = AttackType.values();
                for (int i = 0; i < types.length; i++) {
                    selectorOptions.add(new com.volleyball.game.ui.ShotSelector.Option(String.valueOf(i + 1), types[i].shortLabel, null,
                            true, selected == i + 1, types[i] == recommended));
                }
                AttackType current = handling.attackType(p);
                hint = (selected == 0 ? "Auto: " : "") + current.label + "  -  " + current.description;
                hot = ballHere && handling.teamTouches(p.side) == 2 || p.isAirborne();
            }
        }
        shotSelector.render(delta, title, hint, selectorOptions, hot);
    }

    private List<String> buildStatus(VolleyballPlayer p, Ball ball) {
        BallHandling handling = world.ballHandling();
        MatchController match = drills.match();
        status.clear();
        hud.setTopOffset(match != null && !replaying ? Gdx.graphics.getHeight() - matchHud.cardBottom() + 4f : 0f);
        if (match != null) {
            if (replaying) return status;
            if (showDetails) status.add(String.format(java.util.Locale.ROOT, "DETAILS  ·  FPS %d  ·  camera %s", Gdx.graphics.getFramesPerSecond(),
                    cameraController.mode().label));
            else if (showAi) status.add("AI VIEW");
        }
        if (match != null && (showDetails || showAi)) {
            // (heading added above)
        } else if (match != null) {
            return status;
        } else {
            status.add(String.format(java.util.Locale.ROOT, "%s  ·  %s  ·  %d / %d%s", drills.drill().label.toUpperCase(java.util.Locale.ROOT), drills.phaseLabel(),
                    drills.successes(), drills.attempts(), autoplay ? "  ·  DEMO (F10 to play)" : ""));
            status.add("  " + drills.drill().instructions);
        }
        if (match == null) status.add(String.format(java.util.Locale.ROOT, "#%d %s  ·  %s%s%s", p.number, p.name, p.role.label,
                p.isAirborne() ? String.format(java.util.Locale.ROOT, "  ·  in the air %.2f m", p.position.y) : "",
                handling.isArmed(p) ? "  ·  ACTION ARMED" : ""));
        ContactPurpose context = handling.variantContext(p);
        String variant = switch (context) {
            case SERVE -> "Serve: " + handling.serveType(p).label;
            case SET -> "Set: " + handling.setType(p).label;
            case BLOCK -> handling.blockStyle(p).label;
            default -> "Shot: " + handling.attackType(p).label;
        };
        if (match == null) {
            status.add("1-9 choose  ·  0 Auto  ·  Q / E step  ·  " + variant);
            status.add("Result: " + drills.lastResult());
        }
        if (showDetails) {
            status.add(String.format(java.util.Locale.ROOT, "  FPS %d  ·  camera %s", Gdx.graphics.getFramesPerSecond(), cameraController.mode().label));
            status.add(String.format(java.util.Locale.ROOT, "  Serve %s  ·  Set %s  ·  Shot %s  ·  %s  ·  team touches %d", handling.serveType(p).label,
                    handling.setType(p).label, handling.attackType(p).label, handling.blockStyle(p).label, handling.teamTouches(p.side)));
            status.add(String.format(java.util.Locale.ROOT, "  %.1f m/s  ·  jump %.2f m", p.groundSpeed(), PlayerLocomotion.jumpHeight(p.attributes)));
            if (ball.isActive()) {
                status.add(String.format(java.util.Locale.ROOT, "  Ball %.0f km/h  ·  spin %.1f rev/s  ·  height %.2f m", ball.speed() * 3.6f,
                        ball.spinRevolutionsPerSecond(), ball.position.y));
            }
            if (match != null) addMatchDetails(match);
            if (drills.drill() == Drill.FREE) status.add("  Lab: " + ballLab.report());
            for (String line : contactLog) status.add("  " + line);
        }
        if (match != null && showAi) addAiStatus(match);
        if (match == null) {
            status.add(String.format(java.util.Locale.ROOT, "  F1 help  ·  M menu  ·  Esc pause  ·  H details  ·  C camera  ·  N sound %s (%d%%)",
                    config.soundEnabled ? "on" : "off", Math.round(config.masterVolume * 100)));
        }
        return status;
    }

    /** What every computer player is doing: state and chance of reaching the current ball. */
    private void addAiStatus(MatchController match) {
        for (CourtSide side : CourtSide.values()) {
            TeamAI ai = match.ai(side);
            StringBuilder line = new StringBuilder(String.format(java.util.Locale.ROOT, "AI %s [%s, risk %.2f]:", side == CourtSide.HOME
                    ? world.home().shortName : world.away().shortName, ai.difficulty().label, ai.risk()));
            for (VolleyballPlayer p : (side == CourtSide.HOME ? world.home() : world.away()).onCourt()) {
                AiState state = ai.state(p);
                line.append(String.format(java.util.Locale.ROOT, "  #%d %s", p.number, state.label));
                if (state == AiState.MOVING_TO_BALL || state == AiState.PASSING) line.append(String.format(java.util.Locale.ROOT, " %.0f%%", ai.reachChance(p) * 100f));
            }
            status.add(line.toString());
            // The other team's plan stays its coach's secret: only a team with a person in it (or
            // both, when watching) shows its play and defence here.
            VolleyballPlayer second = secondPerson();
            boolean yours = autoplay || drills.trainee().side == side || (second != null && second.side == side);
            if (!yours) {
                status.add("   Tactics: hidden");
                continue;
            }
            String play = ai.playStatus();
            status.add("   " + (play.isEmpty() ? "Play: -" : play) + "   ·   Defence: " + ai.defenseScheme().label);
        }
    }

    /** Technical match lines (H): score, server, touches, rally time and rotations. */
    private void addMatchDetails(MatchController match) {
        MatchScore score = match.rules().score();
        RallyManager rally = match.rules().rally();
        RotationManager rotation = match.rules().rotation();
        VolleyballPlayer server = match.currentServer();
        status.add(String.format(java.util.Locale.ROOT, "  %s %d : %d %s    sets %d : %d    set %d%s",
                world.home().shortName, score.points(CourtSide.HOME), score.points(CourtSide.AWAY), world.away().shortName,
                score.sets(CourtSide.HOME), score.sets(CourtSide.AWAY), score.setNumber(),
                score.isMatchOver() ? "   MATCH OVER - " + (score.matchWinner() == CourtSide.HOME ? world.home().name : world.away().name) + " win" : ""));
        status.add(String.format(java.util.Locale.ROOT, "  Serving: #%d %s (%s)   touches %s %d / %s %d   rally %.1f s   [%s]",
                server.number, server.name, server.side == CourtSide.HOME ? world.home().shortName : world.away().shortName,
                world.home().shortName, rally.touches(CourtSide.HOME), world.away().shortName, rally.touches(CourtSide.AWAY),
                rally.state() == RallyManager.State.OVER ? 0.0 : rally.elapsed(world.time()), match.rules().format().mode().label));
        status.add("  Rotation " + world.home().shortName + " " + rotationText(rotation, CourtSide.HOME)
                + "   " + world.away().shortName + " " + rotationText(rotation, CourtSide.AWAY));
    }

    private String rotationText(RotationManager rotation, CourtSide side) {
        StringBuilder text = new StringBuilder();
        for (int zone = 1; zone <= 6; zone++) {
            VolleyballPlayer p = world.player(rotation.playerInZone(side, zone));
            text.append(zone).append(':').append(p == null ? "?" : p.number).append(zone < 6 ? " " : "");
        }
        return text.toString();
    }

    private String describe(MatchRules.PointSummary point) {
        RallyOutcome outcome = point.outcome();
        VolleyballPlayer who = outcome.playerId() >= 0 ? world.player(outcome.playerId()) : null;
        String text = outcome.describe() + (who != null ? " (#" + who.number + " " + who.name + ")" : "");
        if (point.matchWon()) text += "  ·  MATCH!";
        else if (point.setWon()) text += "  ·  set won";
        else if (point.sideOut()) text += "  ·  side-out, rotation";
        return text;
    }

    private void updateScoreboard() {
        MatchController match = drills.match();
        var board = scene.scoreboard();
        board.setTeams(world.home().shortName, world.away().shortName);
        if (match == null) {
            board.setScore(0, 0, 0, 0, 1);
            board.setCaption("PRACTICE");
            return;
        }
        MatchScore score = match.rules().score();
        board.setScore(score.points(CourtSide.HOME), score.points(CourtSide.AWAY),
                score.sets(CourtSide.HOME), score.sets(CourtSide.AWAY), score.setNumber());
        board.setCaption(score.isMatchOver() ? "FINAL" : "MATCH");
    }

    private void logContact(BallContact contact) {
        if (contact.kind() == ColliderKind.FLOOR && contact.impactSpeed() < 2f) return;
        String what = contact.kind() == ColliderKind.PLAYER_BODY
                ? "body of #" + numberOf(contact.playerId())
                : contact.kind().name();
        contactLog.addFirst(String.format(java.util.Locale.ROOT, "%s at (%.1f, %.1f, %.1f) %.1f m/s", what,
                contact.position().x, contact.position().y, contact.position().z, contact.impactSpeed()));
        while (contactLog.size() > CONTACT_LOG_SIZE) contactLog.removeLast();
    }

    private String numberOf(int playerId) {
        VolleyballPlayer p = world.player(playerId);
        return p == null ? "?" : String.valueOf(p.number);
    }

    /**
     * F1 opens/closes the help menu, M the drill menu (number keys pick a drill there); Esc
     * closes an open menu, or quits when none is open.
     */
    private void handleMenuKeys() {
        if (pauseMenu.isOpen()) {
            pendingAction = pauseMenu.handleInput();
            return;
        }
        if (substitutionMenu.isOpen()) {
            substitutionMenu.handleInput();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.B) && drills.match() != null && !helpMenu.isOpen() && !drillMenu.isOpen()) {
            openSubstitutions();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            drillMenu.close();
            helpMenu.setRole(drills.match() != null ? drills.role() : null);
            helpMenu.toggle();
        }
        helpMenu.handleInput();
        if (Gdx.input.isKeyJustPressed(Input.Keys.J) && !helpMenu.isOpen()) matchHud.toggleHints();
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            helpMenu.close();
            drillMenu.toggle();
        }
        if (drillMenu.isOpen()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.D)) drills.setMatchOptions(drills.difficulty().next(), drills.sessionMode());
            if (Gdx.input.isKeyJustPressed(Input.Keys.P)) drills.setMatchOptions(drills.difficulty(), drills.sessionMode().nextLocal());
            if (Gdx.input.isKeyJustPressed(Input.Keys.Y)) drills.setSystem(drills.system().next());
            if (Gdx.input.isKeyJustPressed(Input.Keys.O)) {
                java.util.List<PlayerRole> order = RoleGuide.ORDER;
                drills.setMatchSetup(order.get((order.indexOf(drills.role()) + 1) % order.size()), setup.format());
            }
            for (int n = 1; n <= 9; n++) {
                Drill picked = DrillMenu.drillForNumber(n);
                if (picked != null && Gdx.input.isKeyJustPressed(Input.Keys.NUM_0 + n)) {
                    for (VolleyballPlayer p : world.players()) world.intent(p).clear();
                    drills.start(picked);
                    roleAnnounced = false;
                    onDrillStarted();
                    drillMenu.close();
                }
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (helpMenu.isOpen()) helpMenu.close();
            else if (drillMenu.isOpen()) drillMenu.close();
            else openPauseMenu();
        }
    }

    /** The bench of person 1's team. */
    private void openSubstitutions() {
        if (drills.match() == null) return;
        substitutionMenu.open(drills.match(), drills.trainee().side == CourtSide.HOME ? world.home() : world.away());
    }

    private void openPauseMenu() {
        java.util.List<PauseMenu.Action> actions = new java.util.ArrayList<>(java.util.List.of(PauseMenu.Action.values()));
        if (drills.match() == null) {
            actions.remove(PauseMenu.Action.ROLE_GUIDE);
            actions.remove(PauseMenu.Action.SUBSTITUTIONS);
            actions.remove(PauseMenu.Action.TIMEOUT);
            actions.remove(PauseMenu.Action.STATISTICS);
        }
        if (!canReplay()) actions.remove(PauseMenu.Action.REPLAY);
        String subtitle = drills.match() != null
                ? "Quick match  ·  " + RoleGuide.of(drills.role()).title() + "  ·  AI " + drills.difficulty().label
                : "Training  ·  " + drills.drill().label;
        pauseMenu.open(actions, subtitle);
    }

    private void handleKeys(VolleyballPlayer trainee) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) showDetails = !showDetails;
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) cameraController.cycleMode();
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) showAi = !showAi;
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            config.soundEnabled = !config.soundEnabled;
            game.saveConfig();
        }
        int volumeStep = (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS) || Gdx.input.isKeyJustPressed(Input.Keys.PLUS) ? 1 : 0)
                - (Gdx.input.isKeyJustPressed(Input.Keys.MINUS) ? 1 : 0);
        if (volumeStep != 0) {
            config.masterVolume = Math.max(0f, Math.min(1f, Math.round(config.masterVolume * 10f + volumeStep) / 10f));
            game.saveConfig();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) {
            if (canReplay()) startReplay();
            else cameraController.resetFollowView();
        }
        MatchController match = drills.match();
        if (match != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.K)) {
                statsPanel.show(trainee.side);
                statsPanel.open();
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.U)) requestTimeout(trainee);
            if (match.phase() == MatchController.Phase.TIMEOUT && Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) match.skipTimeout();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F10)) {
            autoplay = !autoplay;
            world.intent(trainee).clear();
        }
        for (int i = 0; i < DRILL_KEYS_F5_TO_F9.length; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F5 + i)) {
                world.intent(trainee).clear();
                drills.start(DRILL_KEYS_F5_TO_F9[i]);
                onDrillStarted();
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            if (drills.drill() == Drill.FREE) ballLab.park();
            else drills.restartRepetition();
        }
        if (drills.drill() == Drill.FREE || drills.drill() == Drill.MATCH) {
            if (input.switchPlayerPressed()) {
                world.intent(trainee).clear();
                drills.switchPlayer(0);
                input.clearBuffer();
            }
            BallLab.Preset[] presets = drills.drill() == Drill.FREE ? BallLab.Preset.values() : new BallLab.Preset[0];
            for (int i = 0; i < presets.length; i++) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                    world.ballHandling().clearServe();
                    ballLab.fire(presets[i], trainee);
                }
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) showForecastPath = !showForecastPath;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
            scene.applyTheme(scene.theme().next());
            config.courtTheme = scene.theme().name();
            game.saveConfig();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            scene.applyLighting(scene.lighting().next());
            config.lighting = scene.lighting().name();
            game.saveConfig();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
            scene.setQuality(scene.quality().next());
            config.graphicsQuality = scene.quality().name();
            game.saveConfig();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            court.net().setCategory(court.net().category().next());
            scene.rebuildNet();
            world.onNetChanged();
            config.netCategory = court.net().category().name();
            game.saveConfig();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return; // minimised window
        cameraController.resize(width, height);
        hud.resize(width, height);
        if (shotSelector != null) shotSelector.resize(width, height);
        if (matchHud != null) matchHud.resize(width, height);
        if (statsPanel != null) statsPanel.resize(width, height);
        if (replayOverlay != null) replayOverlay.resize(width, height);
        if (tacticsPanel != null) tacticsPanel.resize(width, height);
        scoreOverlay.resize(width, height);
        pauseMenu.resize(width, height);
        substitutionMenu.resize(width, height);
        helpMenu.resize(width, height);
        drillMenu.resize(width, height);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        if (mouseLook) Gdx.input.setCursorCatched(false);
        mouseLook = false;
    }

    @Override
    public void dispose() {
        if (scene != null) scene.dispose();
        if (playerRenderer != null) playerRenderer.dispose();
        if (ballRenderer != null) ballRenderer.dispose();
        if (hud != null) hud.dispose();
        if (shotSelector != null) shotSelector.dispose();
        if (matchHud != null) matchHud.dispose();
        if (statsPanel != null) statsPanel.dispose();
        if (replayOverlay != null) replayOverlay.dispose();
        if (tacticsPanel != null) tacticsPanel.dispose();
        if (helpMenu != null) helpMenu.dispose();
        if (drillMenu != null) drillMenu.dispose();
        if (scoreOverlay != null) scoreOverlay.dispose();
        if (pauseMenu != null) pauseMenu.dispose();
        if (substitutionMenu != null) substitutionMenu.dispose();
        // The sound output belongs to the game; only this screen's listeners go.
        sound.setLoop(com.volleyball.game.audio.SoundId.CROWD_AMBIENT, 0f);
    }

    /**
     * Middle-button drag rotates the camera (around the player in third person; left/right
     * buttons play the ball); the wheel zooms.
     */
    private final class CameraInput extends InputAdapter {
        private int lastX;
        private int lastY;
        private boolean orbiting;

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            orbiting = button == Input.Buttons.MIDDLE;
            lastX = screenX;
            lastY = screenY;
            return orbiting;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.MIDDLE) orbiting = false;
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            // The first-person mouse look already turns the view with every movement.
            if (!orbiting || mouseLook) return false;
            float dy = screenY - lastY;
            cameraController.orbit(screenX - lastX, config.invertMouseY ? -dy : dy);
            lastX = screenX;
            lastY = screenY;
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            // Third person: distance to the player; other views switch to the free orbit and zoom.
            cameraController.zoom(amountY);
            return true;
        }
    }
}
