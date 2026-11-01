package com.volleyball.game;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.volleyball.game.config.ConfigStore;
import com.volleyball.game.config.GameConfig;
import com.volleyball.game.config.LaunchOptions;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.audio.GdxSoundSink;
import com.volleyball.game.audio.SoundSink;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.screen.MainMenuScreen;
import com.volleyball.game.screen.MatchScreen;
import com.volleyball.game.screen.MatchSetup;
import com.volleyball.game.training.Drill;
import com.volleyball.game.ui.UiFonts;
import com.volleyball.game.screen.PoseGalleryScreen;

/** Application entry point shared by all platform launchers. Owns settings and screen switching. */
public class VolleyballGame extends Game {
    private final ConfigStore configStore;
    private final GameConfig config;
    private final LaunchOptions launchOptions;
    private UiFonts fonts;
    private SoundSink sound;
    /** Screen to switch to at the start of the next frame (never inside another screen's render). */
    private Screen pendingScreen;

    public VolleyballGame(ConfigStore configStore, GameConfig config) {
        this(configStore, config, LaunchOptions.DEFAULT);
    }

    public VolleyballGame(ConfigStore configStore, GameConfig config, LaunchOptions launchOptions) {
        this.configStore = configStore;
        this.config = config;
        this.launchOptions = launchOptions;
    }

    public LaunchOptions launchOptions() {
        return launchOptions;
    }

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_INFO);
        Gdx.app.log("Game", "Renderer: " + Gdx.gl.glGetString(GL20.GL_RENDERER)
                + ", buffer: " + Gdx.graphics.getBufferFormat()
                + ", window: " + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight()
                + " (backbuffer " + Gdx.graphics.getBackBufferWidth() + "x" + Gdx.graphics.getBackBufferHeight() + ")");
        // A --lang option applies to this run only; the saved setting stays as it was.
        com.volleyball.game.i18n.Lang.set(com.volleyball.game.i18n.Lang.parse(
                launchOptions.language() != null ? launchOptions.language() : config.language));
        if (launchOptions.venue() != null) config.venue = com.volleyball.game.world.Venue.fromName(launchOptions.venue()).name();
        fonts = new UiFonts();
        UiFonts.setUserScale(config.uiScale);
        sound = GdxSoundSink.create(config);
        if (launchOptions.playbook()) setScreen(new com.volleyball.game.screen.PlaybookScreen(this));
        else if (launchOptions.poseGallery()) setScreen(new PoseGalleryScreen(this));
        else if (launchOptions.drill() != null || launchOptions.autoplay()) setScreen(new MatchScreen(this, setupFromLaunchOptions()));
        else setScreen(new MainMenuScreen(this));
    }

    /** A game started from the command line (e.g. {@code --drill=match --difficulty=pro --role=setter}). */
    private MatchSetup setupFromLaunchOptions() {
        Drill drill = Drill.SERVE;
        if (launchOptions.drill() != null) {
            for (Drill d : Drill.values()) if (d.name().equals(launchOptions.drill())) drill = d;
        }
        Difficulty difficulty = Difficulty.parse(launchOptions.difficulty());
        SessionMode players = switch (launchOptions.players() == null ? "" : launchOptions.players()) {
            case "versus", "2", "vs" -> SessionMode.LOCAL_VERSUS;
            case "coop", "co-op", "team" -> SessionMode.LOCAL_COOP;
            default -> SessionMode.SINGLE;
        };
        PlayerRole role = PlayerRole.OUTSIDE_HITTER;
        if (launchOptions.role() != null) {
            for (PlayerRole r : PlayerRole.values()) {
                if (r.name().replace("_", "").startsWith(launchOptions.role().toUpperCase(java.util.Locale.ROOT).replace("_", "").replace("-", ""))) {
                    role = r;
                    break;
                }
            }
        }
        return new MatchSetup(drill, launchOptions.autoplay(), difficulty == null ? Difficulty.INTERMEDIATE : difficulty,
                players, role, MatchFormat.official(), OffenseSystem.FIVE_ONE, launchOptions.home(), launchOptions.away());
    }

    public UiFonts fonts() {
        return fonts;
    }

    /** The game's sound output (shared by all screens). */
    public SoundSink sound() {
        return sound;
    }

    /** Starts a match or a drill (from the main menu). */
    public void startGame(MatchSetup setup) {
        pendingScreen = new MatchScreen(this, setup);
    }

    /** Opens the playbook editor (at the start of the next frame). */
    public void showPlaybook() {
        pendingScreen = new com.volleyball.game.screen.PlaybookScreen(this);
    }

    public void showMainMenu() {
        pendingScreen = new MainMenuScreen(this);
    }

    public GameConfig config() {
        return config;
    }

    public void saveConfig() {
        configStore.saveQuietly(config);
    }

    private float dumpTimer;

    /** Developer aid: writes the texts that had no Russian translation to the file in I18N_DUMP. */
    private static void dumpMissingTexts() {
        String dump = System.getenv("I18N_DUMP");
        if (dump == null) return;
        try {
            java.util.List<String> lines;
            synchronized (com.volleyball.game.i18n.Lang.missing()) {
                lines = new java.util.ArrayList<>(com.volleyball.game.i18n.Lang.missing());
            }
            java.nio.file.Files.write(java.nio.file.Path.of(dump), lines, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ignored) {
            // a developer aid only
        }
    }

    @Override
    public void render() {
        dumpTimer += Gdx.graphics.getDeltaTime();
        if (dumpTimer > 3f) {
            dumpTimer = 0f;
            dumpMissingTexts();
        }
        // Global keys work on every screen.
        boolean alt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11) || (alt && Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
            toggleFullscreen();
        }
        if (pendingScreen != null) {
            Screen old = getScreen();
            setScreen(pendingScreen);
            pendingScreen = null;
            if (old != null) old.dispose();
        }
        if (sound instanceof GdxSoundSink gdx) gdx.update();
        super.render();
    }

    /**
     * Switches between fullscreen (current monitor's native mode) and a window of the saved size,
     * and remembers the choice for the next launch.
     */
    public void toggleFullscreen() {
        if (Gdx.graphics.isFullscreen()) {
            Graphics.DisplayMode display = Gdx.graphics.getDisplayMode();
            int width = Math.min(config.windowWidth, Math.round(display.width * 0.95f));
            int height = Math.min(config.windowHeight, Math.round(display.height * 0.9f));
            if (!Gdx.graphics.setWindowedMode(width, height)) {
                Gdx.app.error("Game", "Could not switch to a " + width + "x" + height + " window");
                return;
            }
            config.fullscreen = false;
        } else {
            Graphics.DisplayMode mode = Gdx.graphics.getDisplayMode();
            if (!Gdx.graphics.setFullscreenMode(mode)) {
                Gdx.app.error("Game", "Could not switch to fullscreen " + mode);
                return;
            }
            config.fullscreen = true;
        }
        Gdx.app.log("Game", "Display: " + (config.fullscreen ? "fullscreen " : "window ")
                + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight());
        saveConfig();
    }

    /**
     * Applies the display settings that can change while the game runs: the window size (in
     * window mode), fullscreen, vertical sync and the frame cap. Anti-aliasing needs a restart.
     */
    public void applyDisplay(GameConfig previous) {
        if (config.fullscreen != Gdx.graphics.isFullscreen()) {
            // toggleFullscreen flips the flag itself.
            config.fullscreen = !config.fullscreen;
            toggleFullscreen();
        } else if (!config.fullscreen && (config.windowWidth != previous.windowWidth || config.windowHeight != previous.windowHeight)) {
            Graphics.DisplayMode display = Gdx.graphics.getDisplayMode();
            int width = Math.min(config.windowWidth, display.width);
            int height = Math.min(config.windowHeight, display.height);
            if (!Gdx.graphics.setWindowedMode(width, height)) Gdx.app.error("Game", "Could not set a " + width + "x" + height + " window");
        }
        if (config.vsync != previous.vsync) Gdx.graphics.setVSync(config.vsync);
        if (config.fpsLimit != previous.fpsLimit) Gdx.graphics.setForegroundFPS(config.fpsLimit);
        UiFonts.setUserScale(config.uiScale);
    }

    @Override
    public void dispose() {
        dumpMissingTexts();
        if (screen != null) screen.dispose();
        if (sound instanceof Disposable disposable) disposable.dispose();
        if (fonts != null) fonts.dispose();
        saveConfig();
    }
}
