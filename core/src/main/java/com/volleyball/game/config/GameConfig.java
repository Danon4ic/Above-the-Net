package com.volleyball.game.config;

import com.volleyball.game.input.KeyBindings;
import com.volleyball.game.render.GraphicsQuality;
import com.volleyball.game.world.CourtTheme;
import com.volleyball.game.world.LightingPreset;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.Venue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;

/**
 * User settings persisted as JSON. Fields are public and mutable so libGDX {@code Json} can
 * (de)serialize them; always call {@link #sanitize()} after loading untrusted data.
 *
 * <p>Enum-valued settings are stored as strings so that an unknown or misspelled value in the
 * file falls back to a default instead of failing the whole load.
 */
public final class GameConfig {
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;

    // Graphics
    public int windowWidth = 1600;
    public int windowHeight = 900;
    public boolean fullscreen = false;
    public boolean vsync = true;
    /** Foreground frame cap; 0 means uncapped. */
    public int fpsLimit = 144;
    /** Multisample anti-aliasing samples (0, 2, 4 or 8). */
    public int msaaSamples = 4;
    public float fieldOfView = 50f;
    /** Graphics preset: LOW, MEDIUM, HIGH or ULTRA (see GraphicsQuality). */
    public String graphicsQuality = "HIGH";
    /** Shadow map size: 0 = as the graphics preset says, else 1024, 2048 or 4096 texels. */
    public int shadowMapSize = 0;

    // Controls
    public float mouseSensitivity = 1.0f;
    /** The controlled player faces the point under the mouse cursor (off: faces the direction of movement). */
    public boolean mouseFacing = true;
    public boolean invertMouseY = false;
    /** First person: the view follows the mouse without a button (the cursor hides, the aim is the centre of the screen). */
    public boolean firstPersonMouseLook = true;
    /** Action name -> key name, e.g. "JUMP": "Space". Missing or unknown entries fall back to defaults. */
    public LinkedHashMap<String, String> keyBindings = new LinkedHashMap<>(KeyBindings.defaults().toNames());

    /** Keys of the second person in local two-player matches (no mouse). */
    public LinkedHashMap<String, String> keyBindingsPlayer2 = new LinkedHashMap<>(KeyBindings.player2Defaults().toNames());

    /** Open the controls and hints menu at start-up; cleared after the first launch. */
    public boolean showHelpOnStart = true;

    // Sound (0..1 each; the channel volumes are multiplied by the master volume)
    public boolean soundEnabled = true;
    public float masterVolume = 0.8f;
    public float effectsVolume = 1.0f;
    public float crowdVolume = 0.7f;
    public float musicVolume = 0.5f;

    /** Players tire (running, jumping, diving) and recover between rallies; off = arcade. */
    public boolean fatigue = true;
    /** A soft marker on the floor where a ball coming to your side drops to passing height. */
    public boolean landingMarker = true;
    /** Decorative interface animations (menu particles, pulses, page transitions); off = still and instant. */
    public boolean uiAnimations = true;
    /** Size of the interface relative to the window height (0.8 .. 1.4). */
    public float uiScale = 1f;
    /** Size of the tooltips relative to the interface (0.8 .. 1.4). */
    public float tooltipScale = 1f;
    /** The key hints are open for the first seconds of a match (off: only the small "J Keys" chip). */
    public boolean showMatchHints = true;
    /** The camera a match starts with (a CameraMode name). */
    public String defaultCamera = "THIRD_PERSON";
    /** Interface language: "EN" or "RU". */
    public String language = "EN";

    // Arena
    /** CHAMPIONSHIP, BEACH, SCHOOL_GYM, SPORTS_PALACE or ROOFTOP (see {@link Venue}). */
    public String venue = Venue.CHAMPIONSHIP.name();
    public String courtTheme = CourtTheme.FIVB_ARENA.name();
    public String lighting = LightingPreset.ARENA_BRIGHT.name();
    public String netCategory = NetCategory.MEN.name();

    /** Clamps every value to a supported range and replaces unknown enum names with defaults. */
    public GameConfig sanitize() {
        version = CURRENT_VERSION;
        windowWidth = clamp(windowWidth, 800, 7680);
        windowHeight = clamp(windowHeight, 600, 4320);
        fpsLimit = fpsLimit <= 0 ? 0 : clamp(fpsLimit, 30, 360);
        shadowMapSize = switch (shadowMapSize) {
            case 1024, 2048, 4096 -> shadowMapSize;
            default -> 0;
        };
        uiScale = Float.isFinite(uiScale) ? clamp(uiScale, 0.8f, 1.4f) : 1f;
        tooltipScale = Float.isFinite(tooltipScale) ? clamp(tooltipScale, 0.8f, 1.4f) : 1f;
        defaultCamera = defaultCamera().name();
        msaaSamples = switch (msaaSamples) {
            case 0, 2, 4, 8 -> msaaSamples;
            default -> 4;
        };
        fieldOfView = Float.isFinite(fieldOfView) ? clamp(fieldOfView, 35f, 90f) : 50f;
        mouseSensitivity = Float.isFinite(mouseSensitivity) ? clamp(mouseSensitivity, 0.1f, 5f) : 1f;
        masterVolume = volume(masterVolume, 0.8f);
        effectsVolume = volume(effectsVolume, 1f);
        crowdVolume = volume(crowdVolume, 0.7f);
        musicVolume = volume(musicVolume, 0.5f);
        keyBindings = new LinkedHashMap<>(keyBindings().toNames());
        keyBindingsPlayer2 = new LinkedHashMap<>(keyBindingsPlayer2().toNames());
        venue = venue().name();
        courtTheme = courtTheme().name();
        graphicsQuality = graphicsQuality().name();
        lighting = lighting().name();
        netCategory = netCategory().name();
        language = com.volleyball.game.i18n.Lang.parse(language).name();
        return this;
    }

    /** The camera a match starts with. */
    public com.volleyball.game.render.CameraMode defaultCamera() {
        for (com.volleyball.game.render.CameraMode m : com.volleyball.game.render.CameraMode.values()) {
            if (m.name().equals(defaultCamera)) return m;
        }
        return com.volleyball.game.render.CameraMode.THIRD_PERSON;
    }

    /** An independent copy (a draft for the settings screen). */
    public GameConfig copy() {
        GameConfig c = new GameConfig();
        c.copyFrom(this);
        return c;
    }

    /** Takes every setting from {@code other} (the key bindings as copies). */
    public void copyFrom(GameConfig other) {
        try {
            for (Field f : GameConfig.class.getFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object value = f.get(other);
                if (value instanceof LinkedHashMap<?, ?> map) value = new LinkedHashMap<>(map);
                f.set(this, value);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public GraphicsQuality graphicsQuality() {
        return GraphicsQuality.fromName(graphicsQuality);
    }

    public Venue venue() {
        return Venue.fromName(venue);
    }

    public CourtTheme courtTheme() {
        return CourtTheme.fromName(courtTheme);
    }

    public KeyBindings keyBindings() {
        return KeyBindings.fromNames(keyBindings);
    }

    public KeyBindings keyBindingsPlayer2() {
        return KeyBindings.fromNames(keyBindingsPlayer2, KeyBindings.player2Defaults());
    }

    public LightingPreset lighting() {
        return LightingPreset.fromName(lighting);
    }

    public NetCategory netCategory() {
        for (NetCategory category : NetCategory.values()) if (category.name().equals(netCategory)) return category;
        return NetCategory.MEN;
    }

    private static float volume(float value, float fallback) {
        return Float.isFinite(value) ? clamp(value, 0f, 1f) : fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
