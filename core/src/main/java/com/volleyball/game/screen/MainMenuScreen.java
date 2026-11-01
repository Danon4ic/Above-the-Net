package com.volleyball.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.volleyball.game.VolleyballGame;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.audio.SoundDirector;
import com.volleyball.game.config.GameConfig;
import com.volleyball.game.core.FixedStepLoop;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.match.SidelineCoaches;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.render.ArenaScene;
import com.volleyball.game.render.BallRenderer;
import com.volleyball.game.render.PlayerRenderer;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.simulation.TeamCatalog;
import com.volleyball.game.simulation.TeamFactory;
import com.volleyball.game.training.Drill;
import com.volleyball.game.ui.HelpMenu;
import com.volleyball.game.ui.TooltipManager;
import com.volleyball.game.ui.UiFonts;
import com.volleyball.game.ui.UiKit;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.VolleyballCourt;

import java.util.ArrayList;
import java.util.List;

/**
 * The title screen. Behind the menu a real match between two professional AI teams is played
 * in the arena, filmed by a slowly circling camera, with the crowd and the menu theme.
 *
 * <p>Pages: the main list, the quick match set-up (role with its guide, AI level, players,
 * format), training drills, settings and the controls. Keyboard (arrows, Enter, Esc) and mouse
 * (hover, click; right click steps an option back) both work.
 */
public final class MainMenuScreen extends ScreenAdapter {
    enum Page { MAIN, MATCH, TRAINING, SETTINGS }

    /**
     * The main-menu entries. A locked entry has the reason it is not available yet: it is shown
     * as "in development" and explains itself in its tooltip, it never pretends to work.
     */
    private enum Item {
        CONTINUE("Continue", "Pick up where you left off: your career or the tournament in progress.",
                "Resume your saved game", "There is no saved career or tournament yet."),
        QUICK_MATCH("Quick match", "Choose your role and the AI level, then play a full match with official rules. Your "
                + "teammates and the opponents are computer players that read the game, cover for each other and adapt.",
                "A single match against the computer", null),
        TWO_PLAYERS("Two players", "Two people on one keyboard - against each other or in the same team. "
                + "Player 2 plays with the arrow keys, no mouse needed.", "Two people, one keyboard", null),
        CAREER("Career", "Start as a young player, train, choose your role and move between clubs.",
                "Your player's road from youth to the top", "The career mode is in development."),
        TOURNAMENT("Tournament", "Group stage and play-offs with several teams.", "Groups and play-offs",
                "The tournament mode is in development."),
        TRAINING("Training", "Practise every skill with a ball machine: serve, serve receive, setting, attack, block, "
                + "defense and a free ball lab.", "Drills for every skill", null),
        PLAYBOOK("Playbook", "The attacking combinations the teams play - first tempo, decoys, cross runs, tandem, pipe, "
                + "bic... Study them on the court and design your own: the computer teams call them in matches.",
                "Study and design attacking plays", null),
        ONLINE("Online", "Network play. The authoritative-server protocol is ready; the lobby comes later.",
                "Play over the network", "The lobby and joining a game are in development."),
        SETTINGS("Settings", "Sound, display, mouse and arena.", "Graphics, sound, controls, arena", null),
        CONTROLS("Controls", "All keys and the most useful hints.", "All keys at a glance", null),
        EXIT("Exit", "Leave the game.", "Close the game", null);

        final String title;
        final String description;
        final String tip;
        final String lockedReason;

        Item(String title, String description, String tip, String lockedReason) {
            this.title = title;
            this.description = description;
            this.tip = tip;
            this.lockedReason = lockedReason;
        }

        boolean locked() {
            return lockedReason != null;
        }
    }

    private static final List<Item> MAIN = List.of(Item.values());

    private final VolleyballGame game;
    private final GameConfig config;
    private final UiFonts fonts;

    // Background match.
    private final VolleyballCourt court;
    private final GameWorld world;
    private final MatchController demo;
    private final FixedStepLoop loop = new FixedStepLoop(MatchScreen.SIMULATION_HZ, 8);
    private ArenaScene scene;
    private PlayerRenderer playerRenderer;
    private final com.badlogic.gdx.math.Vector3 heldBall = new com.badlogic.gdx.math.Vector3();
    private final SidelineCoaches coaches;
    private final com.volleyball.game.match.Referee referee = new com.volleyball.game.match.Referee();
    private final List<VolleyballPlayer> figures = new ArrayList<>();
    private BallRenderer ballRenderer;
    private PerspectiveCamera camera;
    private SoundDirector sound;

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private HelpMenu controls;
    private int width;
    private int height;
    private float s = 1f;

    private Page page = Page.MAIN;
    private int selected = Item.QUICK_MATCH.ordinal();
    private float time;
    private float pageTime;
    // Row states: highlight (hover or focus) and press, each animated 0..1 with the frame time.
    private final float[] rowHighlight = new float[32];
    private final float[] rowPress = new float[32];
    private int pressedRow = -1;
    /** The last navigation was with the keyboard: the selected row shows a focus ring. */
    private boolean keyboardFocus;
    private float mouseX = -1f;
    private float mouseY = -1f;
    private TooltipManager tooltips;
    private com.volleyball.game.ui.SettingsView settingsView;
    private MatchSetupView setupView;
    // Page transition: the current page fades out, then the next one comes in.
    private Page pendingPage;
    private int pendingRow;
    private float leaveTime = -1f;
    /** Leaving the menu (a match, a drill, the playbook): the screen fades to black, then this runs. */
    private Runnable pendingExit;
    private float exitTime = -1f;
    private String techLine;
    private String toast;
    private float toastTime = -10f;
    private final List<float[]> rowBounds = new ArrayList<>();

    public MainMenuScreen(VolleyballGame game) {
        this.game = game;
        this.config = game.config();
        this.fonts = game.fonts();
        this.court = new VolleyballCourt(config.netCategory());
        // The demo match: two clubs drawn at random.
        TeamCatalog.Entry demoHome = TeamCatalog.random(MathUtils.random, null);
        TeamCatalog.Entry demoAway = TeamCatalog.random(MathUtils.random, demoHome);
        this.world = new GameWorld(court, TeamFactory.build(demoHome, CourtSide.HOME, 0, demoAway),
                TeamFactory.build(demoAway, CourtSide.AWAY, 6, demoHome));
        this.demo = new MatchController(world, MatchFormat.official(), MathUtils.random.nextLong(),
                Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        demo.start(CourtSide.HOME);
        coaches = new SidelineCoaches(world.home(), world.away(), MathUtils.random.nextLong());
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
        camera = new PerspectiveCamera(42f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 300f;
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        controls = new HelpMenu(config.keyBindings(), fonts);
        tooltips = new TooltipManager(fonts);
        tooltips.setScale(config.tooltipScale);
        com.badlogic.gdx.Graphics.DisplayMode display = Gdx.graphics.getDisplayMode();
        setupView = new MatchSetupView(fonts, tooltips, setup -> leaveMenu(() -> game.startGame(setup)));
        settingsView = new com.volleyball.game.ui.SettingsView(fonts, tooltips, this::applySettings,
                com.volleyball.game.ui.SettingsView.resolutions(display.width, display.height, config.windowWidth, config.windowHeight));
        String gpu = Gdx.gl.glGetString(GL20.GL_RENDERER);
        if (gpu != null) {
            // "Intel(R) Arc(TM) 140T GPU (8GB)" -> "Intel Arc 140T GPU".
            gpu = gpu.replace("(R)", "").replace("(TM)", "");
            if (gpu.contains(" (")) gpu = gpu.substring(0, gpu.indexOf(" ("));
            gpu = gpu.trim();
        }
        techLine = "v" + com.volleyball.game.GameInfo.VERSION + "   ·   Java " + Runtime.version().feature() + "   ·   LibGDX "
                + com.badlogic.gdx.Version.VERSION + (gpu == null || gpu.isBlank() ? "" : "   ·   " + gpu);
        sound = new SoundDirector(world, game.sound(), MathUtils.random.nextLong()).attach();
        demo.addListener(sound);
        demo.addListener(referee);
        // The crowd reacts to the demo match (forwarded, as the scene is rebuilt when the venue changes).
        demo.addListener(new com.volleyball.game.match.MatchListener() {
            @Override
            public void onServeWhistle(com.volleyball.game.entities.VolleyballPlayer server, MatchScore score) {
                scene.crowdMood().onServeWhistle(server, score);
            }

            @Override
            public void onPoint(com.volleyball.game.rules.MatchRules.PointSummary point, MatchScore score) {
                scene.crowdMood().onPoint(point, score);
            }
        });
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(new MenuInput());
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void render(float delta) {
        time += delta;
        pageTime += delta;
        advanceTransitions(delta);
        loop.advance(delta, dt -> {
            world.step(dt);
            demo.update(dt, List.of());
            coaches.update(dt, demo, world.ball(), world.benchSign());
            referee.update(dt, world.benchSign());
        });
        if (demo.phase() == MatchController.Phase.FINISHED) demo.start(CourtSide.HOME);
        if (exitTime < 0f) handleKeys();

        // A slow, low orbit around the court, slightly breathing in height.
        float angle = 0.6f + time * 0.045f;
        float radius = 19f;
        camera.position.set(MathUtils.cos(angle) * radius, 6.2f + MathUtils.sin(time * 0.13f) * 0.8f, MathUtils.sin(angle) * radius * 0.75f);
        camera.lookAt(world.ball().position.x * 0.25f, 1.2f, world.ball().position.z * 0.25f);
        camera.up.set(0f, 1f, 0f);
        camera.update();
        sound.setListener(camera.position, camera.direction);
        sound.setMatchActive(true);
        sound.setMenuOpen(true);
        sound.update(delta);
        updateScoreboard();
        scene.setSidesSwapped(world.benchSign() < 0f);
        scene.update(delta);

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        playerRenderer.setCamera(camera.position);
        boolean realShadows = scene.quality().arenaShader;
        playerRenderer.setBlobShadows(!realShadows);
        ballRenderer.setBlobShadow(!realShadows);
        figures.clear();
        figures.addAll(world.players());
        figures.addAll(coaches.coaches());
        figures.add(referee.figure());
        scene.setOccluders(figures, world.ball());
        float alpha = loop.alpha();
        scene.render(camera, (batch, environment, mirror) -> {
            if (mirror) {
                playerRenderer.drawLast(batch, environment);
                ballRenderer.drawLast(batch, environment);
                return;
            }
            playerRenderer.render(batch, environment, figures, alpha, null, world.ball().position);
            com.volleyball.game.mechanics.BallHandling bh = world.ballHandling();
            ballRenderer.setHeldAt(bh.servePhase() == com.volleyball.game.mechanics.BallHandling.ServePhase.HOLDING && bh.server() != null
                    ? playerRenderer.heldBallPoint(bh.server(), heldBall) : null);
            ballRenderer.render(batch, environment, world.ball(), alpha, null, false);
        });

        drawUi();
        controls.render();
        if (!controls.isOpen()) tooltips.render(shapes, batch, s, width, height, delta);
        drawExitFade();
    }

    /** Page changes and leaving the menu, timed by the frame time. */
    private void advanceTransitions(float delta) {
        if (leaveTime >= 0f) {
            leaveTime += delta;
            if (leaveTime >= PAGE_OUT) {
                leaveTime = -1f;
                showPage(pendingPage, pendingRow);
            }
        }
        if (exitTime >= 0f) {
            exitTime += delta;
            if (exitTime >= EXIT_FADE && pendingExit != null) {
                Runnable action = pendingExit;
                pendingExit = null;
                action.run();
            }
        }
    }

    /** Page fade-out and fade-in, and the fade to black when leaving the menu (s). */
    private static final float PAGE_OUT = 0.12f;
    private static final float PAGE_IN = 0.24f;
    private static final float EXIT_FADE = 0.28f;

    /** How visible the page is: fading out before a page change, then fading (and sliding) in. */
    private float pageFade() {
        return leaveTime >= 0f ? 1f - UiKit.ease(leaveTime / PAGE_OUT) : 1f;
    }

    /** Appearance of the current page's panels (0..1). */
    private float pageAppear() {
        return (config.uiAnimations ? UiKit.ease(pageTime / PAGE_IN) : 1f) * pageFade();
    }

    private void drawExitFade() {
        if (exitTime < 0f) return;
        beginShapes();
        shapes.setColor(0f, 0f, 0f, UiKit.ease(exitTime / EXIT_FADE));
        shapes.rect(0, 0, width, height);
        shapes.end();
    }

    /**
     * The settings screen applied its changes (they are in the config now): puts them into effect
     * in the game and in the arena behind the menu, and saves them.
     */
    private void applySettings(GameConfig previous) {
        game.applyDisplay(previous);
        if (!config.language.equals(previous.language)) {
            com.volleyball.game.i18n.Lang.set(com.volleyball.game.i18n.Lang.parse(config.language));
        }
        if (!config.venue.equals(previous.venue)) {
            ArenaScene old = scene;
            scene = new ArenaScene(court, config.venue(), config.courtTheme(), config.lighting(), config.graphicsQuality(),
                    ArenaScene.Matchup.of(world.home(), world.away()));
            old.dispose();
        } else {
            if (!config.graphicsQuality.equals(previous.graphicsQuality)) scene.setQuality(config.graphicsQuality());
            if (!config.courtTheme.equals(previous.courtTheme)) scene.applyTheme(config.courtTheme());
            if (!config.lighting.equals(previous.lighting)) scene.applyLighting(config.lighting());
        }
        scene.setShadowMapSize(config.shadowMapSize);
        // The arena may have fallen back to a lower preset (an old graphics driver).
        config.graphicsQuality = scene.quality().name();
        if (!config.netCategory.equals(previous.netCategory)) {
            court.net().setCategory(config.netCategory());
            scene.rebuildNet();
            world.onNetChanged();
        }
        tooltips.setScale(config.tooltipScale);
        if (config.uiScale != previous.uiScale) resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        game.saveConfig();
    }

    /** Leaves the menu for another screen after a short fade to black. */
    private void leaveMenu(Runnable action) {
        if (exitTime >= 0f) return;
        tooltips.dismiss();
        if (!config.uiAnimations) {
            action.run();
            return;
        }
        pendingExit = action;
        exitTime = 0f;
    }

    private void updateScoreboard() {
        MatchScore score = demo.rules().score();
        var board = scene.scoreboard();
        board.setTeams(world.home().shortName, world.away().shortName);
        board.setScore(score.points(CourtSide.HOME), score.points(CourtSide.AWAY), score.sets(CourtSide.HOME),
                score.sets(CourtSide.AWAY), score.setNumber());
        board.setCaption("EXHIBITION");
    }

    // ------------------------------------------------------------------ drawing

    private static final Color MENU_SIDE = new Color(UiKit.NAVY.r, UiKit.NAVY.g, UiKit.NAVY.b, 0.9f);
    private static final Color MENU_CLEAR = new Color(UiKit.NAVY.r, UiKit.NAVY.g, UiKit.NAVY.b, 0f);
    private final Color rowColour = new Color();
    private final Color numberColour = new Color();

    private void drawUi() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        float intro = UiKit.ease(time / 1.2f);
        float leftW = 560f * s;

        // The menu side in the background colour, opening into the arena behind (the text stays
        // readable; the match behind stays visible).
        beginShapes();
        shapes.rect(0, 0, leftW, height, MENU_SIDE, MENU_SIDE, MENU_SIDE, MENU_SIDE);
        shapes.rect(leftW, 0, leftW * 0.9f, height, MENU_SIDE, MENU_CLEAR, MENU_CLEAR, MENU_SIDE);
        shapes.end();

        float x = 80f * s;
        drawTitleBug(x - 20f * s - (1f - intro) * 30f * s, height - 52f * s, intro);

        float listTop = height - 215f * s;
        switch (page) {
            case MAIN -> drawMain(x, listTop);
            case MATCH -> setupView.render(shapes, batch, s, width, height, x - 20f * s, height - 172f * s, 72f * s,
                    pageAppear(), Gdx.graphics.getDeltaTime());
            case TRAINING -> drawTraining(x, listTop);
            case SETTINGS -> settingsView.render(shapes, batch, s, width, height, x - 20f * s, height - 172f * s, 72f * s,
                    pageAppear(), Gdx.graphics.getDeltaTime());
        }
        drawFooter();
        drawToast();
        hoverTooltip();
        tooltips.update(Gdx.graphics.getDeltaTime());
    }

    /**
     * The title: the name in heavy type beside a thin blue edge, and under it the score strip of
     * the demo match playing behind the menu (a flat surface with a border, team colour chips).
     */
    private void drawTitleBug(float x, float top, float alpha) {
        BitmapFont nameFont = fonts.get(UiFonts.Weight.BLACK, 44f * s);
        BitmapFont scoreFont = fonts.get(UiFonts.Weight.EXTRABOLD, 17f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.BOLD, 12f * s);
        String name = "ABOVE THE NET";
        MatchScore score = demo.rules().score();
        String home = world.home().shortName;
        String away = world.away().shortName;
        String points = score.points(CourtSide.HOME) + "    " + score.points(CourtSide.AWAY);
        String set = "SET " + score.setNumber();
        String sets = score.sets(CourtSide.HOME) + ":" + score.sets(CourtSide.AWAY);

        float bandH = 68f * s;
        float stripH = 32f * s;
        float edge = 3f * s;
        float pad = 22f * s;
        float nameW = UiKit.width(nameFont, name);
        float bandW = edge + pad + nameW + pad;

        // Score strip layout: [chip] HOME  18  21  AWAY [chip]  ·  SET 2  1:0
        float gap = 12f * s;
        float chip = 5f * s;
        float homeW = UiKit.width(scoreFont, home);
        float pointsW = UiKit.width(scoreFont, points);
        float awayW = UiKit.width(scoreFont, away);
        float setW = UiKit.width(small, set + "   " + sets);
        float dot = 4f * s;
        float stripW = edge + pad * 0.6f + chip + gap * 0.6f + homeW + gap + pointsW + gap + awayW + gap * 0.6f + chip + gap * 1.6f
                + dot * 2f + gap * 0.7f + setW + pad;
        float stripTop = top - bandH;

        beginShapes();
        shapes.setColor(UiKit.BORDER.r, UiKit.BORDER.g, UiKit.BORDER.b, alpha);
        shapes.rect(x, stripTop - stripH, stripW, stripH);
        shapes.setColor(UiKit.SURFACE.r, UiKit.SURFACE.g, UiKit.SURFACE.b, alpha);
        shapes.rect(x + 1f, stripTop - stripH + 1f, stripW - 2f, stripH - 2f);
        shapes.setColor(UiKit.PRIMARY.r, UiKit.PRIMARY.g, UiKit.PRIMARY.b, alpha);
        shapes.rect(x, stripTop - stripH, Math.max(2f, 3f * s), bandH + stripH);
        // Team colour chips beside the names.
        float cx = x + edge + pad * 0.6f;
        float chipY = stripTop - stripH + 7f * s;
        Color homeShirt = world.home().shirtColor;
        Color awayShirt = world.away().shirtColor;
        shapes.setColor(homeShirt.r, homeShirt.g, homeShirt.b, alpha);
        shapes.rect(cx, chipY, chip, stripH - 14f * s);
        float awayChipX = cx + chip + gap * 0.6f + homeW + gap + pointsW + gap + awayW + gap * 0.6f;
        shapes.setColor(awayShirt.r, awayShirt.g, awayShirt.b, alpha);
        shapes.rect(awayChipX, chipY, chip, stripH - 14f * s);
        // A thin divider between the points.
        float pointsX = cx + chip + gap * 0.6f + homeW + gap;
        float homePointsW = UiKit.width(scoreFont, String.valueOf(score.points(CourtSide.HOME)));
        float halfW = (homePointsW + UiKit.width(scoreFont, score.points(CourtSide.HOME) + "    ")) / 2f;
        shapes.setColor(UiKit.BORDER.r, UiKit.BORDER.g, UiKit.BORDER.b, alpha);
        shapes.rect(pointsX + halfW, chipY, Math.max(1f, s), stripH - 14f * s);
        // The red dot of a live broadcast: the match behind is being played.
        shapes.setColor(UiKit.RED.r, UiKit.RED.g, UiKit.RED.b, alpha);
        shapes.circle(awayChipX + chip + gap * 1.6f + dot, stripTop - stripH / 2f, dot, 14);
        shapes.end();

        batch.begin();
        UiKit.text(batch, nameFont, name, x + edge + pad, top - (bandH - nameFont.getCapHeight()) / 2f, UiKit.TEXT, alpha);
        float textY = stripTop - (stripH - scoreFont.getCapHeight()) / 2f;
        UiKit.text(batch, scoreFont, home, cx + chip + gap * 0.6f, textY, UiKit.TEXT, alpha);
        UiKit.text(batch, scoreFont, points, pointsX, textY, UiKit.JADE, alpha);
        UiKit.text(batch, scoreFont, away, pointsX + pointsW + gap, textY, UiKit.TEXT, alpha);
        UiKit.text(batch, small, set + "   " + sets, awayChipX + chip + gap * 1.6f + dot * 2f + gap * 0.7f,
                stripTop - (stripH - small.getCapHeight()) / 2f, UiKit.TEXT_DIM, alpha);
        batch.end();
    }

    /**
     * Draws a vertical list of rows and fills {@link #rowBounds} for the mouse. Every row has its
     * states: normal, highlighted (under the mouse or selected with the keys: a raised surface and
     * a blue bar), pressed (it gives a little), locked (faint, "soon") and, after keyboard
     * navigation, a focus outline. The states ease in and out with the frame time.
     */
    private void drawList(float x, float topY, List<String> labels, List<String> values, List<Boolean> locked, float rowH,
                          float rowW) {
        rowBounds.clear();
        // Many rows (the settings) close up so the list ends above the key hints.
        rowH = Math.min(rowH, (topY - 70f * s) / Math.max(1, labels.size()));
        if (values == null) {
            // The rows are as wide as the longest label needs (in the current language).
            BitmapFont labelFont = fonts.get(UiFonts.Weight.BOLD, 24f * s);
            float widest = 0f;
            for (String label : labels) widest = Math.max(widest, UiKit.width(labelFont, label.toUpperCase(java.util.Locale.ROOT)));
            rowW = Math.max(rowW, 20f * s + 36f * s + widest + 24f * s);
        }
        float delta = Gdx.graphics.getDeltaTime();
        float fade = pageFade();
        for (int i = 0; i < labels.size() && i < rowHighlight.length; i++) {
            float target = i == selected ? 1f : 0f;
            rowHighlight[i] = approach(rowHighlight[i], target, delta / 0.14f);
            rowPress[i] = approach(rowPress[i], i == pressedRow ? 1f : 0f, delta / 0.08f);
        }

        beginShapes();
        for (int i = 0; i < labels.size(); i++) {
            float h = rowHighlight[i] * fade;
            float press = rowPress[i];
            boolean isLocked = locked != null && locked.get(i);
            float rowY = topY - i * rowH;
            float bx = x - 20f * s;
            float by = rowY - rowH + 8f * s;
            float bh = rowH - 4f * s;
            // Pressed: the row shrinks a little towards its centre.
            float shrink = press * 3f * s;
            if (h > 0.01f) {
                // A raised surface, and the blue bar on the left (grey for a row that is not available).
                shapes.setColor(UiKit.DEEP.r, UiKit.DEEP.g, UiKit.DEEP.b, (0.85f + 0.15f * press) * h);
                shapes.rect(bx + shrink, by + shrink, rowW - 2f * shrink, bh - 2f * shrink);
                Color bar = isLocked ? UiKit.TEXT_FAINT : UiKit.PRIMARY;
                shapes.setColor(bar.r, bar.g, bar.b, h);
                shapes.rect(bx + shrink, by + shrink, 3f * s, bh - 2f * shrink);
            }
            if (keyboardFocus && i == selected && fade > 0.5f) {
                // The keyboard focus: a 1 px accent outline round the row.
                shapes.setColor(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0.7f * fade);
                float t = Math.max(1f, s);
                shapes.rect(bx, by, rowW, t);
                shapes.rect(bx, by + bh - t, rowW, t);
                shapes.rect(bx, by, t, bh);
                shapes.rect(bx + rowW - t, by, t, bh);
            }
        }
        shapes.end();

        batch.begin();
        BitmapFont font = fonts.get(UiFonts.Weight.BOLD, 24f * s);
        BitmapFont valueFont = fonts.get(UiFonts.Weight.SEMIBOLD, 19f * s);
        BitmapFont tag = fonts.get(UiFonts.Weight.EXTRABOLD, 11f * s);
        BitmapFont number = fonts.get(UiFonts.Weight.SEMIBOLD, 12f * s);
        for (int i = 0; i < labels.size(); i++) {
            float appear = (config.uiAnimations ? UiKit.ease((pageTime - i * 0.02f) / PAGE_IN) : 1f) * fade;
            float h = rowHighlight[i];
            float rowY = topY - i * rowH;
            float textY = rowY - (rowH - font.getCapHeight()) / 2f - rowPress[i] * 1.5f * s;
            float dx = (1f - appear) * -24f * s;
            boolean sel = i == selected;
            boolean isLocked = locked != null && locked.get(i);
            Color color = isLocked ? UiKit.TEXT_FAINT : rowColour.set(UiKit.TEXT_DIM).lerp(UiKit.TEXT, Math.min(1f, h));
            UiKit.text(batch, number, String.format(java.util.Locale.ROOT, "%02d", i + 1), x + dx, textY - 2f * s,
                    isLocked ? UiKit.TEXT_FAINT : numberColour.set(UiKit.TEXT_FAINT).lerp(UiKit.JADE, Math.min(1f, h)), appear);
            float labelX = x + 36f * s + dx;
            String label = labels.get(i).toUpperCase(java.util.Locale.ROOT);
            // Label and value share the row: if they would touch, both get a smaller type.
            BitmapFont labelFont = font;
            BitmapFont rowValueFont = valueFont;
            if (values != null && values.get(i) != null) {
                String shownValue = sel ? "‹  " + values.get(i) + "  ›" : values.get(i);
                float room = rowW - 36f * s - 46f * s;
                if (UiKit.width(font, label) + UiKit.width(valueFont, shownValue) + 24f * s > room) {
                    labelFont = fonts.get(UiFonts.Weight.BOLD, 18f * s);
                    rowValueFont = fonts.get(UiFonts.Weight.SEMIBOLD, 15f * s);
                }
            }
            UiKit.text(batch, labelFont, label, labelX, textY + 4f * s, color, appear);
            if (isLocked) {
                float w = UiKit.width(labelFont, label);
                UiKit.text(batch, tag, "SOON", labelX + w + 12f * s, textY + 1f * s, UiKit.TEXT_FAINT, appear);
            }
            if (values != null && values.get(i) != null) {
                String value = values.get(i);
                float valueRight = x - 20f * s + rowW - 18f * s;
                String shown = sel ? "‹  " + value + "  ›" : value;
                float w = UiKit.width(rowValueFont, shown);
                UiKit.text(batch, rowValueFont, shown, valueRight - w + dx, textY + 2f * s, sel ? UiKit.JADE : UiKit.TEXT_DIM, appear);
            }
            rowBounds.add(new float[]{x - 20f * s, rowY - rowH + 8f * s, rowW, rowH - 4f * s});
        }
        batch.end();
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    /** The tooltip of the row under the mouse (none while navigating with the keys). */
    private void hoverTooltip() {
        if (keyboardFocus || controls.isOpen() || exitTime >= 0f || leaveTime >= 0f) return;
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return;
        TooltipManager.Tip tip = tipFor(row);
        if (tip == null) return;
        float[] r = rowBounds.get(row);
        tooltips.hover(page.name() + row, r[0], r[1], r[2], r[3], tip);
    }

    private TooltipManager.Tip tipFor(int row) {
        return switch (page) {
            case MAIN -> {
                Item item = MAIN.get(row);
                if (item.locked()) yield TooltipManager.Tip.locked(item.title, item.tip, item.lockedReason);
                yield TooltipManager.Tip.action(item.title, item.tip, "ENTER", item == Item.EXIT ? "Close the game" : "Open");
            }
            case TRAINING, SETTINGS, MATCH -> null;
        };
    }

    private void drawMain(float x, float top) {
        List<String> labels = new ArrayList<>();
        List<Boolean> locked = new ArrayList<>();
        for (Item e : MAIN) {
            labels.add(e.title);
            locked.add(e.locked());
        }
        drawList(x, top, labels, null, locked, 46f * s, 440f * s);
        Item e = MAIN.get(selected);
        drawInfoCard(e.title, e.locked() ? "In development" : null, e.locked() ? e.description + "\n" + e.lockedReason : e.description,
                featuresFor(e));
    }

    private static List<String> featuresFor(Item entry) {
        return switch (entry) {
            case QUICK_MATCH -> List.of("Five roles, each with its own guide", "AI that reads the setter and learns your habits",
                    "5-1, 4-2 and 6-2 systems, libero, substitutions", "TV score graphics and a living crowd");
            case TRAINING -> List.of("Ball machine and an attacking opponent", "Graded repetitions with feedback",
                    "Demo mode shows how it is done (F10)");
            case TWO_PLAYERS -> List.of("Versus or co-op", "Player 2: arrows, Right Ctrl, / and .", "The AI plays the rest of both teams");
            case PLAYBOOK -> List.of("13 built-in plays with their preconditions and developments", "Drag the runs, pick the tempo and the sets",
                    "Custom plays are saved and used by the AI", "In a match: G and Alt+1-9 give your team commands");
            default -> List.of();
        };
    }

    /** The big card on the right: title, subtitle, description and a list of highlights. */
    private void drawInfoCard(String title, String subtitle, String text, List<String> bullets) {
        float cardW = Math.min(620f * s, width - 760f * s);
        if (cardW < 300f * s) return;
        float cardX = width - cardW - 70f * s;
        float cardTop = height - 210f * s;
        float appear = pageAppear();
        BitmapFont body = fonts.get(UiFonts.Weight.MEDIUM, 17f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 15f * s);
        float pad = 32f * s;
        // A long title (in the current language) takes a smaller size rather than leave the card.
        String heading = title.toUpperCase(java.util.Locale.ROOT);
        float titleSize = 34f * s;
        while (titleSize > 20f * s && UiKit.width(fonts.get(UiFonts.Weight.EXTRABOLD, titleSize), heading) > cardW - 2 * pad) {
            titleSize -= 1f * s;
        }
        BitmapFont titleFont = fonts.get(UiFonts.Weight.EXTRABOLD, titleSize);
        float textH = UiKit.wrappedHeight(body, text, cardW - 2 * pad);
        float cardH = pad * 2 + 50f * s + (subtitle != null ? 26f * s : 0f) + textH + 24f * s + bullets.size() * 30f * s;

        beginShapes();
        UiKit.panel(shapes, cardX, cardTop - cardH, cardW, cardH, 0.92f * appear, 0f);
        shapes.end();
        batch.begin();
        float y = cardTop - pad;
        UiKit.text(batch, titleFont, UiKit.fit(titleFont, heading, cardW - 2 * pad), cardX + pad, y, UiKit.TEXT, appear);
        y -= 50f * s;
        if (subtitle != null) {
            UiKit.text(batch, small, subtitle.toUpperCase(java.util.Locale.ROOT), cardX + pad, y, UiKit.TEXT_FAINT, appear);
            y -= 26f * s;
        }
        UiKit.text(batch, body, text, cardX + pad, y, cardW - 2 * pad, Align.left, true, UiKit.TEXT_DIM, appear);
        y -= textH + 24f * s;
        for (String b : bullets) {
            UiKit.text(batch, small, "•", cardX + pad, y, UiKit.JADE, appear);
            UiKit.text(batch, small, UiKit.fit(small, b, cardW - 2 * pad - 20f * s), cardX + pad + 20f * s, y, UiKit.TEXT, appear);
            y -= 30f * s;
        }
        batch.end();
    }

    private void drawTraining(float x, float top) {
        List<String> labels = new ArrayList<>();
        for (Drill d : trainingDrills()) labels.add(d.label);
        labels.add("Back");
        drawList(x, top, labels, null, null, 50f * s, 440f * s);
        if (selected < trainingDrills().size()) {
            Drill d = trainingDrills().get(selected);
            drawInfoCard(d.label, "Drill " + (selected + 1), d.instructions, List.of("F10 in the drill: watch a demo",
                    "R: restart the repetition", "M: switch drills at any time"));
        }
    }

    private static List<Drill> trainingDrills() {
        List<Drill> list = new ArrayList<>();
        for (Drill d : Drill.values()) if (d != Drill.MATCH) list.add(d);
        return list;
    }

    private void drawFooter() {
        batch.begin();
        BitmapFont font = fonts.get(UiFonts.Weight.SEMIBOLD, 14f * s);
        String hints = page == Page.MAIN ? "↑ ↓  choose      ENTER  confirm"
                : page == Page.SETTINGS ? settingsView.footerHints()
                : page == Page.MATCH ? setupView.footerHints()
                : "↑ ↓  choose      ← →  change      ENTER  confirm      ESC  back";
        UiKit.text(batch, font, hints, 80f * s, 42f * s, UiKit.TEXT_DIM, 1f);
        // Version and technical details, quietly in the corner.
        BitmapFont small = fonts.get(UiFonts.Weight.MEDIUM, 12f * s);
        float w = UiKit.width(small, techLine);
        UiKit.text(batch, small, techLine, width - 40f * s - w, 40f * s, UiKit.TEXT_FAINT, 1f);
        batch.end();
    }

    private void drawToast() {
        float age = time - toastTime;
        if (toast == null || age > 2.2f) return;
        float a = Math.min(1f, Math.min(age / 0.2f, (2.2f - age) / 0.4f));
        BitmapFont font = fonts.get(UiFonts.Weight.SEMIBOLD, 16f * s);
        float w = UiKit.width(font, toast) + 48f * s;
        float h = 44f * s;
        float x = (width - w) / 2f;
        float y = 110f * s;
        beginShapes();
        UiKit.panel(shapes, x, y, w, h, a, 0f);
        shapes.end();
        batch.begin();
        UiKit.text(batch, font, toast, x, y + h / 2f + font.getCapHeight() / 2f, w, Align.center, false, UiKit.TEXT, a);
        batch.end();
    }

    /** Shapes with alpha blending (a sprite batch's end() switches blending off). */
    private void beginShapes() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
    }

    // ------------------------------------------------------------------ input

    private int rowCount() {
        return switch (page) {
            case MAIN -> MAIN.size();
            case MATCH -> 1;
            case TRAINING -> trainingDrills().size() + 1;
            case SETTINGS -> 1;
        };
    }

    private void handleKeys() {
        if (controls.isOpen()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                    || Gdx.input.isKeyJustPressed(Input.Keys.F1)) controls.close();
            else controls.handleInput();
            return;
        }
        if (leaveTime >= 0f) return;
        if (page == Page.SETTINGS) {
            if (settingsView.handleKeys()) goTo(Page.MAIN, Item.SETTINGS.ordinal());
            return;
        }
        if (page == Page.MATCH) {
            if (setupView.handleKeys()) goTo(Page.MAIN, Item.QUICK_MATCH.ordinal());
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) move(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) move(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) change(-1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) change(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) activate();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            // Esc never closes the game: on the main page it only says how to leave.
            if (page == Page.MAIN) showToast("To leave the game, choose Exit");
            else goTo(Page.MAIN, (page == Page.SETTINGS ? Item.SETTINGS : page == Page.TRAINING ? Item.TRAINING : Item.QUICK_MATCH).ordinal());
        }
    }

    private void move(int direction) {
        selected = (selected + direction + rowCount()) % rowCount();
        keyboardFocus = true;
        tooltips.dismiss();
    }

    /** Goes to another page: the current one fades out first (unless the animations are off). */
    private void goTo(Page next, int row) {
        tooltips.dismiss();
        if (!config.uiAnimations) {
            showPage(next, row);
            return;
        }
        pendingPage = next;
        pendingRow = row;
        leaveTime = 0f;
    }

    private void showPage(Page next, int row) {
        if (next == Page.SETTINGS) settingsView.open(config);
        if (next == Page.MATCH) {
            // From "Two players" the set-up opens on the players row with a two-person mode.
            SessionMode mode = row == MatchSetupView.PLAYERS
                    ? (setupView.players.twoLocalPlayers ? setupView.players : SessionMode.LOCAL_VERSUS) : SessionMode.SINGLE;
            setupView.open(mode, row);
        }
        page = next;
        selected = row;
        pageTime = 0f;
        java.util.Arrays.fill(rowHighlight, 0f);
    }

    private void showToast(String text) {
        toast = text;
        toastTime = time;
    }

    private void activate() {
        switch (page) {
            case MAIN -> {
                Item e = MAIN.get(selected);
                if (e.locked()) {
                    showToast(e.lockedReason);
                    return;
                }
                switch (e) {
                    case QUICK_MATCH -> goTo(Page.MATCH, 0);
                    case TRAINING -> goTo(Page.TRAINING, 0);
                    case TWO_PLAYERS -> goTo(Page.MATCH, MatchSetupView.PLAYERS);
                    case PLAYBOOK -> leaveMenu(game::showPlaybook);
                    case SETTINGS -> goTo(Page.SETTINGS, 0);
                    case CONTROLS -> controls.toggle();
                    case EXIT -> Gdx.app.exit();
                    default -> {
                    }
                }
            }
            case MATCH -> {
            }
            case TRAINING -> {
                if (selected >= trainingDrills().size()) goTo(Page.MAIN, Item.TRAINING.ordinal());
                else {
                    Drill drill = trainingDrills().get(selected);
                    leaveMenu(() -> game.startGame(MatchSetup.drill(drill)));
                }
            }
            case SETTINGS -> {
            }
        }
    }

    private void change(int direction) {
        // Only the main and training lists remain here: they have no values to change.
    }

    /**
     * Mouse: hover highlights (and selects) a row; pressing shows the row pressed, releasing over
     * the same row activates it (on option rows: the next value); right click steps back.
     */
    private final class MenuInput extends InputAdapter {
        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            mouseX = screenX;
            mouseY = height - screenY;
            keyboardFocus = false;
            if (page == Page.SETTINGS) {
                settingsView.mouseMoved(mouseX, mouseY);
                return true;
            }
            if (page == Page.MATCH) {
                setupView.mouseMoved(mouseX, mouseY);
                return true;
            }
            int row = rowAt(mouseX, mouseY);
            if (row >= 0 && exitTime < 0f && leaveTime < 0f) selected = row;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            mouseX = screenX;
            mouseY = height - screenY;
            if (page == Page.SETTINGS) {
                settingsView.touchDragged(mouseX, mouseY);
                return true;
            }
            if (page == Page.MATCH) {
                setupView.touchDragged(mouseX, mouseY);
                return true;
            }
            if (pressedRow >= 0 && rowAt(mouseX, mouseY) != pressedRow) pressedRow = -1;
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            mouseX = screenX;
            mouseY = height - screenY;
            keyboardFocus = false;
            if (controls.isOpen()) {
                if (!controls.contains(screenX, height - screenY)) controls.close();
                return true;
            }
            if (exitTime >= 0f || leaveTime >= 0f) return true;
            if (page == Page.SETTINGS) {
                if (settingsView.touchDown(mouseX, mouseY, button)) goTo(Page.MAIN, Item.SETTINGS.ordinal());
                return true;
            }
            if (page == Page.MATCH) {
                setupView.touchDown(mouseX, mouseY, button);
                return true;
            }
            int row = rowAt(mouseX, mouseY);
            if (row < 0) return false;
            selected = row;
            tooltips.dismiss();
            if (button == Input.Buttons.RIGHT) change(-1);
            else pressedRow = row;
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (page == Page.SETTINGS && leaveTime < 0f && exitTime < 0f) {
                if (settingsView.touchUp(screenX, height - screenY)) goTo(Page.MAIN, Item.SETTINGS.ordinal());
                return true;
            }
            if (page == Page.MATCH && leaveTime < 0f && exitTime < 0f) {
                if (setupView.touchUp(screenX, height - screenY)) goTo(Page.MAIN, Item.QUICK_MATCH.ordinal());
                return true;
            }
            int row = pressedRow;
            pressedRow = -1;
            if (row < 0 || button != Input.Buttons.LEFT || exitTime >= 0f || leaveTime >= 0f) return false;
            if (rowAt(screenX, height - screenY) == row) {
                selected = row;
                activate();
            }
            return true;
        }
    }

    private int rowAt(float x, float y) {
        for (int i = 0; i < rowBounds.size(); i++) {
            float[] r = rowBounds.get(i);
            if (x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) return i;
        }
        return -1;
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        s = UiFonts.uiScale(height);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        controls.resize(width, height);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        game.sound().setLoop(com.volleyball.game.audio.SoundId.MENU_MUSIC, 0f);
    }

    @Override
    public void dispose() {
        if (scene != null) scene.dispose();
        if (playerRenderer != null) playerRenderer.dispose();
        if (ballRenderer != null) ballRenderer.dispose();
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        if (controls != null) controls.dispose();
    }
}
