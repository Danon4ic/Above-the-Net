package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.volleyball.game.config.GameConfig;
import com.volleyball.game.i18n.Lang;
import com.volleyball.game.render.CameraMode;
import com.volleyball.game.render.GraphicsQuality;
import com.volleyball.game.world.CourtTheme;
import com.volleyball.game.world.LightingPreset;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.Venue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The settings screen: five categories (graphics, sound, controls, interface, gameplay), each a
 * list of settings with fitting controls — switches, sliders and drop-down lists. Changes go to
 * a draft; Apply puts them into effect and saves them, Cancel returns to the saved values,
 * Defaults resets the shown category, Back leaves (asking first when there are unapplied
 * changes). Settings that need a restart say so.
 *
 * <p>Keys: Tab / Shift+Tab (or Q / E) change the category, up and down choose, left and right
 * change the value, Enter switches, opens a list or presses a button, Esc goes back. Mouse:
 * click the tabs, switches and lists, drag the sliders; every setting has a tooltip.
 */
public final class SettingsView {
    /** What the screen around it has to do. */
    public interface Host {
        /** The committed settings have changed (they are in the config now): put them into effect. */
        void apply(GameConfig previous);
    }

    enum Category {
        GRAPHICS("Graphics"), SOUND("Sound"), CONTROLS("Controls"), INTERFACE("Interface"), GAMEPLAY("Gameplay");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    // ------------------------------------------------------------------ settings model

    /** One setting: how to show, change, compare and copy its value in a config. */
    abstract static class Setting<T> {
        final Category category;
        final String label;
        final Function<GameConfig, String> help;
        final boolean restart;
        final Function<GameConfig, T> getter;
        final BiConsumer<GameConfig, T> setter;

        Setting(Category category, String label, Function<GameConfig, String> help, boolean restart, Function<GameConfig, T> getter,
                BiConsumer<GameConfig, T> setter) {
            this.category = category;
            this.label = label;
            this.help = help;
            this.restart = restart;
            this.getter = getter;
            this.setter = setter;
        }

        abstract String display(GameConfig c);

        abstract void step(GameConfig c, int direction);

        boolean differs(GameConfig a, GameConfig b) {
            return !Objects.equals(getter.apply(a), getter.apply(b));
        }

        void copy(GameConfig from, GameConfig to) {
            setter.accept(to, getter.apply(from));
        }
    }

    static final class Toggle extends Setting<Boolean> {
        Toggle(Category category, String label, Function<GameConfig, String> help, boolean restart, Function<GameConfig, Boolean> getter,
               BiConsumer<GameConfig, Boolean> setter) {
            super(category, label, help, restart, getter, setter);
        }

        @Override
        String display(GameConfig c) {
            return getter.apply(c) ? "On" : "Off";
        }

        @Override
        void step(GameConfig c, int direction) {
            setter.accept(c, !getter.apply(c));
        }
    }

    static final class Slider extends Setting<Float> {
        final float min;
        final float max;
        final float increment;
        final Function<Float, String> format;

        Slider(Category category, String label, Function<GameConfig, String> help, float min, float max, float increment,
               Function<Float, String> format, Function<GameConfig, Float> getter, BiConsumer<GameConfig, Float> setter) {
            super(category, label, help, false, getter, setter);
            this.min = min;
            this.max = max;
            this.increment = increment;
            this.format = format;
        }

        @Override
        String display(GameConfig c) {
            return format.apply(getter.apply(c));
        }

        @Override
        void step(GameConfig c, int direction) {
            set(c, getter.apply(c) + direction * increment);
        }

        float fraction(GameConfig c) {
            return (getter.apply(c) - min) / (max - min);
        }

        void setFraction(GameConfig c, float f) {
            set(c, min + Math.max(0f, Math.min(1f, f)) * (max - min));
        }

        private void set(GameConfig c, float value) {
            float snapped = Math.round((value - min) / increment) * increment + min;
            snapped = Math.max(min, Math.min(max, snapped));
            // Keep the stored value tidy (no 0.70000005).
            setter.accept(c, Math.round(snapped * 1000f) / 1000f);
        }
    }

    static final class Choice<T> extends Setting<T> {
        final List<T> values;
        final List<String> labels;

        Choice(Category category, String label, Function<GameConfig, String> help, boolean restart, List<T> values, List<String> labels,
               Function<GameConfig, T> getter, BiConsumer<GameConfig, T> setter) {
            super(category, label, help, restart, getter, setter);
            this.values = values;
            this.labels = labels;
        }

        int index(GameConfig c) {
            return values.indexOf(getter.apply(c));
        }

        @Override
        String display(GameConfig c) {
            int i = index(c);
            return i >= 0 ? labels.get(i) : String.valueOf(getter.apply(c));
        }

        @Override
        void step(GameConfig c, int direction) {
            int i = Math.max(0, index(c));
            setter.accept(c, values.get((i + direction + values.size()) % values.size()));
        }

        void pick(GameConfig c, int index) {
            setter.accept(c, values.get(index));
        }
    }

    /** Every setting of the screen, in the order shown. */
    static List<Setting<?>> buildSettings(List<int[]> resolutions) {
        List<Setting<?>> list = new ArrayList<>();
        // Graphics
        list.add(new Choice<>(Category.GRAPHICS, "Graphics quality", c -> switch (GraphicsQuality.fromName(c.graphicsQuality)) {
            case LOW -> "Standard per-vertex lighting and blob shadows - the fastest.";
            case MEDIUM -> "Per-pixel lighting from the venue's real light sources, shadows from everything, ambient occlusion, "
                    + "an animated crowd.";
            case HIGH -> "Medium with sharper shadows and the reflection of the venue in the floor (half resolution).";
            case ULTRA -> "The sharpest shadows and the floor reflection at full resolution.";
        }, false, names(GraphicsQuality.values()), labels(GraphicsQuality.values()), c -> c.graphicsQuality, (c, v) -> c.graphicsQuality = v));
        list.add(new Choice<>(Category.GRAPHICS, "Shadow quality", c -> "Sharper shadows cost more. Low graphics has no shadow map.",
                false, List.of(0, 1024, 2048, 4096), List.of("By graphics preset", "Low", "Medium", "High"),
                c -> c.shadowMapSize, (c, v) -> c.shadowMapSize = v));
        List<String> res = new ArrayList<>();
        List<String> resLabels = new ArrayList<>();
        for (int[] r : resolutions) {
            res.add(r[0] + "x" + r[1]);
            resLabels.add(r[0] + " × " + r[1]);
        }
        list.add(new Choice<>(Category.GRAPHICS, "Resolution", c -> "The size of the window. Fullscreen always uses the monitor's "
                + "own resolution.", false, res, resLabels, c -> c.windowWidth + "x" + c.windowHeight, (c, v) -> {
                    String[] p = v.split("x");
                    c.windowWidth = Integer.parseInt(p[0]);
                    c.windowHeight = Integer.parseInt(p[1]);
                }));
        list.add(new Toggle(Category.GRAPHICS, "Fullscreen", c -> "Fullscreen at the monitor's resolution, or a window. F11 or "
                + "Alt+Enter switch it at any time.", false, c -> c.fullscreen, (c, v) -> c.fullscreen = v));
        list.add(new Toggle(Category.GRAPHICS, "Vertical sync", c -> "Matches the frames to the monitor: no tearing, the frame rate "
                + "follows the screen.", false, c -> c.vsync, (c, v) -> c.vsync = v));
        list.add(new Choice<>(Category.GRAPHICS, "FPS limit", c -> "The highest frame rate. A lower cap keeps the computer cooler.",
                false, List.of(30, 60, 120, 144, 165, 240, 0), List.of("30", "60", "120", "144", "165", "240", "Unlimited"),
                c -> c.fpsLimit, (c, v) -> c.fpsLimit = v));
        list.add(new Choice<>(Category.GRAPHICS, "Anti-aliasing", c -> "Smooths the jagged edges of the 3D scene.", true,
                List.of(0, 2, 4, 8), List.of("Off", "2x", "4x", "8x"), c -> c.msaaSamples, (c, v) -> c.msaaSamples = v));
        list.add(new Slider(Category.GRAPHICS, "Field of view", c -> "The camera's angle of view in a match.", 35f, 90f, 5f,
                v -> Math.round(v) + "°", c -> c.fieldOfView, (c, v) -> c.fieldOfView = v));
        // Sound
        list.add(new Toggle(Category.SOUND, "Sound", c -> "All sound on or off. N mutes it in a match.", false,
                c -> c.soundEnabled, (c, v) -> c.soundEnabled = v));
        list.add(volume("Master volume", "Scales every other volume.", c -> c.masterVolume, (c, v) -> c.masterVolume = v));
        list.add(volume("Effects", "The ball, the shoes, the whistle and the players.", c -> c.effectsVolume, (c, v) -> c.effectsVolume = v));
        list.add(volume("Crowd", "The stands: chants, cheers and the noise of the hall.", c -> c.crowdVolume, (c, v) -> c.crowdVolume = v));
        list.add(volume("Music", "The music of the menu.", c -> c.musicVolume, (c, v) -> c.musicVolume = v));
        // Controls
        list.add(new Slider(Category.CONTROLS, "Mouse sensitivity", c -> "How fast the mouse turns your player and the cameras.",
                0.2f, 5f, 0.1f, v -> String.format(java.util.Locale.ROOT, "%.1f", v), c -> c.mouseSensitivity, (c, v) -> c.mouseSensitivity = v));
        list.add(new Toggle(Category.CONTROLS, "Invert mouse Y", c -> "Dragging the camera up looks down.", false,
                c -> c.invertMouseY, (c, v) -> c.invertMouseY = v));
        list.add(new Toggle(Category.CONTROLS, "Face the mouse", c -> "Your player faces the mouse cursor and moves sideways; off: "
                + "turns into the direction of movement.", false, c -> c.mouseFacing, (c, v) -> c.mouseFacing = v));
        list.add(new Toggle(Category.CONTROLS, "First-person mouse look", c -> "First person: the view follows the mouse without a "
                + "button, the cursor hides and the aim is the centre of the screen. Off: the middle button turns the view.", false,
                c -> c.firstPersonMouseLook, (c, v) -> c.firstPersonMouseLook = v));
        List<String> cameras = new ArrayList<>();
        List<String> cameraLabels = new ArrayList<>();
        for (CameraMode m : CameraMode.values()) {
            cameras.add(m.name());
            cameraLabels.add(m.label);
        }
        list.add(new Choice<>(Category.CONTROLS, "Match camera", c -> "The camera a match starts with. C changes it in a match.", false,
                cameras, cameraLabels, c -> c.defaultCamera, (c, v) -> c.defaultCamera = v));
        // Interface
        list.add(new Choice<>(Category.INTERFACE, "Language", c -> "English or Russian for every text of the game.", false,
                List.of("EN", "RU"), List.of(Lang.Language.EN.label, Lang.Language.RU.label), c -> c.language, (c, v) -> c.language = v));
        list.add(new Slider(Category.INTERFACE, "Interface size", c -> "The size of menus, panels and the match interface.",
                0.8f, 1.4f, 0.05f, v -> Math.round(v * 100f) + "%", c -> c.uiScale, (c, v) -> c.uiScale = v));
        list.add(new Slider(Category.INTERFACE, "Tooltip size", c -> "The size of the tooltips like this one.",
                0.8f, 1.4f, 0.05f, v -> Math.round(v * 100f) + "%", c -> c.tooltipScale, (c, v) -> c.tooltipScale = v));
        list.add(new Toggle(Category.INTERFACE, "Key hints in matches", c -> "The key hints are open for the first seconds of a "
                + "match; off: only the small J chip. J shows them at any time.", false, c -> c.showMatchHints, (c, v) -> c.showMatchHints = v));
        list.add(new Toggle(Category.INTERFACE, "UI animations", c -> "Menu particles, pulses and smooth page changes. Off: a still, "
                + "instant interface.", false, c -> c.uiAnimations, (c, v) -> c.uiAnimations = v));
        // Gameplay
        list.add(new Toggle(Category.GAMEPLAY, "Fatigue", c -> "Running, jumping and diving tire the players a little; off = arcade.",
                false, c -> c.fatigue, (c, v) -> c.fatigue = v));
        list.add(new Toggle(Category.GAMEPLAY, "Landing marker", c -> "A circle where a ball coming to you drops to passing height.",
                false, c -> c.landingMarker, (c, v) -> c.landingMarker = v));
        list.add(new Choice<>(Category.GAMEPLAY, "Venue", c -> switch (Venue.fromName(c.venue)) {
            case BEACH -> "An open-air beach stadium: sand, bleachers on scaffolding, the sun and the sea, palm trees.";
            case SCHOOL_GYM -> "A big school gym: daylight through the high windows, wall bars, ropes and fold-out bleachers.";
            case SPORTS_PALACE -> "An old sports palace: a steep bowl of wooden benches, a ring of round lamps and a mosaic.";
            case ROOFTOP -> "A court on a skyscraper roof at night, the city's lights all around.";
            default -> "A championship arena: a two-tier bowl, floodlight trusses, a video cube and a cable camera.";
        }, false, names(Venue.values()), venueLabels(), c -> c.venue, (c, v) -> c.venue = v));
        list.add(new Choice<>(Category.GAMEPLAY, "Court", c -> "The colours of the court and the hall.", false,
                names(CourtTheme.values()), themeLabels(), c -> c.courtTheme, (c, v) -> c.courtTheme = v));
        list.add(new Choice<>(Category.GAMEPLAY, "Lighting", c -> "The lighting of the venue (the time of day outdoors).", false,
                names(LightingPreset.values()), lightingLabels(), c -> c.lighting, (c, v) -> c.lighting = v));
        list.add(new Choice<>(Category.GAMEPLAY, "Net height", c -> "Men 2.43 m or women 2.24 m.", false,
                names(NetCategory.values()), List.of("Men (2.43 m)", "Women (2.24 m)"), c -> c.netCategory, (c, v) -> c.netCategory = v));
        return list;
    }

    private static Slider volume(String label, String help, Function<GameConfig, Float> getter, BiConsumer<GameConfig, Float> setter) {
        return new Slider(Category.SOUND, label, c -> help, 0f, 1f, 0.05f, v -> Math.round(v * 100f) + "%", getter, setter);
    }

    private static List<String> names(Enum<?>[] values) {
        List<String> list = new ArrayList<>();
        for (Enum<?> v : values) list.add(v.name());
        return list;
    }

    private static List<String> labels(GraphicsQuality[] values) {
        List<String> list = new ArrayList<>();
        for (GraphicsQuality v : values) list.add(v.label);
        return list;
    }

    private static List<String> venueLabels() {
        List<String> list = new ArrayList<>();
        for (Venue v : Venue.values()) list.add(v.label);
        return list;
    }

    private static List<String> themeLabels() {
        List<String> list = new ArrayList<>();
        for (CourtTheme v : CourtTheme.values()) list.add(v.label);
        return list;
    }

    private static List<String> lightingLabels() {
        List<String> list = new ArrayList<>();
        for (LightingPreset v : LightingPreset.values()) list.add(v.label);
        return list;
    }

    /** Standard window sizes that fit the monitor, with the current one included. */
    public static List<int[]> resolutions(int monitorWidth, int monitorHeight, int currentWidth, int currentHeight) {
        int[][] standard = {{1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3200, 1800}, {3840, 2160}};
        List<int[]> list = new ArrayList<>();
        boolean current = false;
        for (int[] r : standard) {
            if (r[0] > monitorWidth || r[1] > monitorHeight) continue;
            list.add(r);
            if (r[0] == currentWidth && r[1] == currentHeight) current = true;
        }
        if (!current) list.add(new int[]{currentWidth, currentHeight});
        list.sort((a, b) -> Integer.compare(a[0] * a[1], b[0] * b[1]));
        return list;
    }

    // ------------------------------------------------------------------ state

    private static final String[] BUTTONS = {"Apply", "Cancel", "Defaults", "Back"};
    private static final int APPLY = 0;
    private static final int CANCEL = 1;
    private static final int DEFAULTS = 2;
    private static final int BACK = 3;

    private final UiFonts fonts;
    private final TooltipManager tooltips;
    private final DropdownMenu dropdown;
    private final Host host;
    private final List<Setting<?>> settings;
    private GameConfig committed;
    private GameConfig draft;
    private Category category = Category.GRAPHICS;
    /** Focus: a row of the category (0..n-1) or a button (n..n+3). */
    private int focus;
    private boolean keyboard;
    private int hoverRow = -1;
    private int hoverButton = -1;
    private int hoverTab = -1;
    private int pressedButton = -1;
    private Slider dragging;
    private float time;
    private float categoryTime;
    private String status;
    private float statusTime = -10f;
    private boolean statusWarning;
    // Confirmation when leaving with changes that were not applied.
    private boolean confirming;
    private int confirmFocus;
    private float confirmTime;
    private static final String[] CONFIRM = {"Apply", "Discard", "Stay"};
    // Animations: per-row highlight, switch knobs, slider knobs, the tab underline.
    private final float[] rowHighlight = new float[16];
    private final float[] knob = new float[64];
    private final float[] sliderShown = new float[64];
    private final float[] buttonHighlight = new float[BUTTONS.length];
    private final float[] buttonPress = new float[BUTTONS.length];
    private float tabLineX = Float.NaN;
    private float tabLineW;

    // Layout of the last frame (for the mouse), y up.
    private final List<float[]> rowRects = new ArrayList<>();
    private final List<float[]> controlRects = new ArrayList<>();
    private final float[][] tabRects = new float[Category.values().length][];
    private final float[][] buttonRects = new float[BUTTONS.length][];
    private final float[][] confirmRects = new float[CONFIRM.length][];
    private float s = 1f;
    private int screenHeight;

    public SettingsView(UiFonts fonts, TooltipManager tooltips, Host host, List<int[]> resolutions) {
        this.fonts = fonts;
        this.tooltips = tooltips;
        this.host = host;
        this.dropdown = new DropdownMenu(fonts);
        this.settings = buildSettings(resolutions);
    }

    /** Opens the screen on the saved settings. */
    public void open(GameConfig config) {
        committed = config;
        draft = config.copy();
        category = Category.GRAPHICS;
        focus = 0;
        confirming = false;
        dropdown.close();
        categoryTime = 0f;
        tabLineX = Float.NaN;
        initKnobs();
    }

    private void initKnobs() {
        for (int i = 0; i < settings.size(); i++) {
            Setting<?> st = settings.get(i);
            if (st instanceof Toggle t) knob[i] = t.getter.apply(draft) ? 1f : 0f;
            if (st instanceof Slider sl) sliderShown[i] = sl.fraction(draft);
        }
    }

    private List<Setting<?>> rows() {
        List<Setting<?>> rows = new ArrayList<>();
        for (Setting<?> st : settings) if (st.category == category) rows.add(st);
        return rows;
    }

    public boolean isDirty() {
        for (Setting<?> st : settings) if (st.differs(draft, committed)) return true;
        return false;
    }

    private boolean restartPending(GameConfig previous) {
        for (Setting<?> st : settings) if (st.restart && st.differs(previous, committed)) return true;
        return false;
    }

    /** Key hints for the footer. */
    public String footerHints() {
        return "TAB  category      ↑ ↓  choose      ← →  change      ENTER  select      ESC  back";
    }

    // ------------------------------------------------------------------ actions

    private void apply() {
        if (!isDirty()) return;
        GameConfig previous = committed.copy();
        committed.copyFrom(draft);
        host.apply(previous);
        draft = committed.copy();
        boolean restart = restartPending(previous);
        showStatus(restart ? "Applied. Anti-aliasing changes after a restart." : "Settings applied and saved", restart);
    }

    private void cancel() {
        if (!isDirty()) return;
        draft = committed.copy();
        initKnobs();
        showStatus("Changes discarded", false);
    }

    private void defaults() {
        GameConfig defaults = new GameConfig();
        // Keep the window size as it is: the default may not fit this monitor.
        for (Setting<?> st : rows()) if (!"Resolution".equals(st.label)) st.copy(defaults, draft);
        showStatus(Lang.f("%s: default values (not applied yet)", Lang.t(category.label)), false);
    }

    private void showStatus(String text, boolean warning) {
        status = text;
        statusWarning = warning;
        statusTime = time;
    }

    /** Back: leaves at once, or asks first when there are changes that were not applied. Returns true to leave. */
    private boolean back() {
        dropdown.close();
        if (isDirty()) {
            confirming = true;
            confirmFocus = 0;
            confirmTime = 0f;
            return false;
        }
        return true;
    }

    private boolean pressButton(int button) {
        switch (button) {
            case APPLY -> apply();
            case CANCEL -> cancel();
            case DEFAULTS -> defaults();
            case BACK -> {
                return back();
            }
            default -> {
            }
        }
        return false;
    }

    /** The confirmation's choice; returns true to leave. */
    private boolean confirm(int choice) {
        confirming = false;
        switch (choice) {
            case 0 -> {
                apply();
                return true;
            }
            case 1 -> {
                draft = committed.copy();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void setCategory(Category next) {
        if (next == category) return;
        category = next;
        focus = 0;
        categoryTime = 0f;
        dropdown.close();
        tooltips.dismiss();
    }

    private void changeFocused(int direction) {
        List<Setting<?>> rows = rows();
        if (focus < rows.size()) {
            rows.get(focus).step(draft, direction);
        } else {
            int b = focus - rows.size();
            focus = rows.size() + Math.max(0, Math.min(BUTTONS.length - 1, b + direction));
        }
    }

    private void activateFocused() {
        List<Setting<?>> rows = rows();
        if (focus < rows.size()) {
            Setting<?> st = rows.get(focus);
            if (st instanceof Choice<?> ch) openDropdown(ch, controlRects.get(focus));
            else st.step(draft, 1);
        }
    }

    private void openDropdown(Choice<?> ch, float[] r) {
        dropdown.open(ch, ch.labels, ch.index(draft), r[0], r[1], r[2], r[3], s, index -> ch.pick(draft, index));
    }

    // ------------------------------------------------------------------ keyboard

    /** Keyboard; returns true when the screen should be left. */
    public boolean handleKeys() {
        if (confirming) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) confirmFocus = Math.max(0, confirmFocus - 1);
            if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) confirmFocus = Math.min(2, confirmFocus + 1);
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) return confirm(confirmFocus);
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) confirming = false;
            return false;
        }
        if (dropdown.handleKeys()) {
            keyboard = true;
            return false;
        }
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
        Category[] all = Category.values();
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) setCategory(all[(category.ordinal() + (shift ? all.length - 1 : 1)) % all.length]);
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q) || Gdx.input.isKeyJustPressed(Input.Keys.PAGE_UP)) {
            setCategory(all[(category.ordinal() + all.length - 1) % all.length]);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E) || Gdx.input.isKeyJustPressed(Input.Keys.PAGE_DOWN)) {
            setCategory(all[(category.ordinal() + 1) % all.length]);
        }
        int count = rows().size() + BUTTONS.length;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            focus = focus >= rows().size() ? rows().size() - 1 : (focus - 1 + count) % count;
            keyboard = true;
            tooltips.dismiss();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            focus = focus >= rows().size() ? focus : focus + 1;
            keyboard = true;
            tooltips.dismiss();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            changeFocused(-1);
            keyboard = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            changeFocused(1);
            keyboard = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            keyboard = true;
            if (focus >= rows().size()) return pressButton(focus - rows().size());
            activateFocused();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) return back();
        return false;
    }

    // ------------------------------------------------------------------ mouse

    public void mouseMoved(float x, float y) {
        keyboard = false;
        if (dropdown.isOpen()) {
            dropdown.mouseMoved(x, y);
            return;
        }
        hoverRow = indexAt(rowRects, x, y);
        hoverButton = indexAt(buttonRects, x, y);
        hoverTab = indexAt(tabRects, x, y);
        if (hoverRow >= 0) focus = hoverRow;
        else if (hoverButton >= 0) focus = rows().size() + hoverButton;
    }

    /** A press; returns true when the screen should be left. */
    public boolean touchDown(float x, float y, int button) {
        keyboard = false;
        if (confirming) {
            int c = indexAt(confirmRects, x, y);
            return c >= 0 && confirm(c);
        }
        if (dropdown.isOpen()) {
            int row = indexAt(controlRects, x, y);
            boolean sameField = row >= 0 && row < rows().size() && dropdown.isOpenFor(rows().get(row));
            if (sameField) dropdown.close();
            else dropdown.touchDown(x, y);
            return false;
        }
        int tab = indexAt(tabRects, x, y);
        if (tab >= 0) {
            setCategory(Category.values()[tab]);
            return false;
        }
        int b = indexAt(buttonRects, x, y);
        if (b >= 0) {
            pressedButton = b;
            return false;
        }
        int row = indexAt(rowRects, x, y);
        if (row < 0) return false;
        focus = row;
        tooltips.dismiss();
        Setting<?> st = rows().get(row);
        float[] c = controlRects.get(row);
        int dir = button == Input.Buttons.RIGHT ? -1 : 1;
        if (st instanceof Slider sl) {
            if (inside(c, x, y)) {
                dragging = sl;
                dragSlider(x);
            } else sl.step(draft, dir);
        } else if (st instanceof Choice<?> ch) {
            if (button == Input.Buttons.RIGHT) ch.step(draft, -1);
            else openDropdown(ch, c);
        } else {
            st.step(draft, dir);
        }
        return false;
    }

    public void touchDragged(float x, float y) {
        if (dragging != null) dragSlider(x);
        if (pressedButton >= 0 && indexAt(buttonRects, x, y) != pressedButton) pressedButton = -1;
    }

    /** A release; returns true when the screen should be left. */
    public boolean touchUp(float x, float y) {
        dragging = null;
        int b = pressedButton;
        pressedButton = -1;
        if (b >= 0 && indexAt(buttonRects, x, y) == b) return pressButton(b);
        return false;
    }

    private void dragSlider(float x) {
        int row = rows().indexOf(dragging);
        if (row < 0) return;
        float[] c = controlRects.get(row);
        float trackW = c[2] - 64f * s;
        dragging.setFraction(draft, (x - c[0]) / trackW);
    }

    private static int indexAt(List<float[]> rects, float x, float y) {
        for (int i = 0; i < rects.size(); i++) if (inside(rects.get(i), x, y)) return i;
        return -1;
    }

    private static int indexAt(float[][] rects, float x, float y) {
        for (int i = 0; i < rects.length; i++) if (rects[i] != null && inside(rects[i], x, y)) return i;
        return -1;
    }

    private static boolean inside(float[] r, float x, float y) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the screen in the left part of the window ({@code left}, between {@code bottom} and
     * {@code top}); {@code alpha} fades it with the page.
     */
    public void render(ShapeRenderer shapes, SpriteBatch batch, float scale, int width, int height, float left, float top, float bottom,
                       float alpha, float delta) {
        this.s = scale;
        this.screenHeight = height;
        time += delta;
        categoryTime += delta;
        confirmTime += delta;
        dropdown.update(delta);
        float panelW = Math.min(800f * s, width * 0.6f);
        float panelX = left;
        float panelH = top - bottom;
        float pad = 28f * s;
        List<Setting<?>> rows = rows();
        if (focus >= rows.size() + BUTTONS.length) focus = rows.size() + BUTTONS.length - 1;

        BitmapFont tabFont = fonts.get(UiFonts.Weight.BOLD, 15f * s);
        BitmapFont labelFont = fonts.get(UiFonts.Weight.SEMIBOLD, 18f * s);
        BitmapFont valueFont = fonts.get(UiFonts.Weight.SEMIBOLD, 15f * s);
        BitmapFont tagFont = fonts.get(UiFonts.Weight.EXTRABOLD, 10f * s);
        BitmapFont helpFont = fonts.get(UiFonts.Weight.MEDIUM, 14f * s);
        BitmapFont buttonFont = fonts.get(UiFonts.Weight.BOLD, 15f * s);

        // Tabs.
        float tabY = top - pad - 6f * s;
        float tx = panelX + pad;
        float activeX = 0f;
        float activeW = 0f;
        for (Category c : Category.values()) {
            String label = Lang.t(c.label).toUpperCase(Lang.current() == Lang.Language.RU ? java.util.Locale.forLanguageTag("ru") : java.util.Locale.ROOT);
            float w = UiKit.width(tabFont, label);
            tabRects[c.ordinal()] = new float[]{tx - 8f * s, tabY - 26f * s, w + 16f * s, 36f * s};
            if (c == category) {
                activeX = tx;
                activeW = w;
            }
            tx += w + 30f * s;
        }
        if (Float.isNaN(tabLineX)) {
            tabLineX = activeX;
            tabLineW = activeW;
        }
        float f = Math.min(1f, delta / 0.18f * 3f);
        tabLineX += (activeX - tabLineX) * f;
        tabLineW += (activeW - tabLineW) * f;

        // Rows.
        float rowH = 46f * s;
        float rowsTop = tabY - 44f * s;
        float controlW = Math.min(300f * s, panelW * 0.42f);
        float controlX = panelX + panelW - pad - controlW;
        rowRects.clear();
        controlRects.clear();
        float appear = UiKit.ease(categoryTime / 0.22f) * alpha;
        for (int i = 0; i < rows.size(); i++) {
            float ry = rowsTop - (i + 1) * rowH;
            rowRects.add(new float[]{panelX + pad - 12f * s, ry, panelW - 2f * pad + 24f * s, rowH - 4f * s});
            float ch = rows.get(i) instanceof Choice<?> ? 32f * s : rowH - 4f * s;
            controlRects.add(new float[]{controlX, ry + (rowH - 4f * s - ch) / 2f, controlW, ch});
            rowHighlight[i] = approach(rowHighlight[i], i == focus ? 1f : 0f, delta / 0.14f);
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            boolean focused = focus == rows.size() + i;
            buttonHighlight[i] = approach(buttonHighlight[i], focused || i == hoverButton ? 1f : 0f, delta / 0.14f);
            buttonPress[i] = approach(buttonPress[i], i == pressedButton ? 1f : 0f, delta / 0.08f);
        }

        // Buttons along the bottom.
        float buttonH = 40f * s;
        float by = bottom + pad;
        float bx = panelX + pad;
        boolean dirty = isDirty();
        for (int i = 0; i < BUTTONS.length; i++) {
            float bw = Math.max(120f * s, UiKit.width(buttonFont, Lang.t(BUTTONS[i]).toUpperCase(java.util.Locale.ROOT)) + 40f * s);
            if (i == BACK) bx = panelX + panelW - pad - bw;
            buttonRects[i] = new float[]{bx, by, bw, buttonH};
            bx += bw + 12f * s;
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, panelX, bottom, panelW, panelH, 0.94f * alpha, 3f * s);
        // Tab underline and the divider under the tabs.
        shapes.setColor(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, alpha);
        shapes.rect(tabLineX, tabY - 24f * s, tabLineW, 3f * s);
        UiKit.divider(shapes, panelX + pad, tabY - 24f * s, panelW - 2f * pad, alpha);
        for (int i = 0; i < rows.size(); i++) drawRowShapes(shapes, rows.get(i), i, alpha * appear, delta);
        UiKit.divider(shapes, panelX + pad, by + buttonH + 52f * s, panelW - 2f * pad, alpha);
        for (int i = 0; i < BUTTONS.length; i++) drawButtonShape(shapes, i, enabled(i, dirty), alpha);
        shapes.end();

        batch.begin();
        for (Category c : Category.values()) {
            float[] r = tabRects[c.ordinal()];
            String label = Lang.t(c.label).toUpperCase(java.util.Locale.forLanguageTag("ru"));
            Color col = c == category ? UiKit.TEXT : c.ordinal() == hoverTab ? UiKit.MINT : UiKit.TEXT_DIM;
            UiKit.text(batch, tabFont, label, r[0] + 8f * s, tabY, col, alpha);
        }
        for (int i = 0; i < rows.size(); i++) {
            Setting<?> st = rows.get(i);
            float[] r = rowRects.get(i);
            float[] c = controlRects.get(i);
            float ty = r[1] + r[3] / 2f + labelFont.getCapHeight() / 2f;
            float h = rowHighlight[i];
            float lx = r[0] + 22f * s + h * 4f * s;
            UiKit.text(batch, labelFont, st.label, lx, ty, h > 0.5f ? UiKit.TEXT : UiKit.TEXT_DIM, alpha * appear);
            float after = lx + UiKit.width(labelFont, st.label) + 10f * s;
            if (st.restart) {
                UiKit.text(batch, tagFont, "RESTART", after, ty - 3f * s, UiKit.GOLD, alpha * appear);
            }
            drawRowText(batch, st, i, c, valueFont, alpha * appear);
        }
        // The focused setting's explanation (for the keyboard; the mouse also gets the tooltip).
        String help = focusHelp(rows);
        if (help != null) {
            UiKit.text(batch, helpFont, help, panelX + pad, by + buttonH + 40f * s, panelW - 2f * pad, Align.left, true,
                    UiKit.TEXT_DIM, alpha);
        }
        // Status: unapplied changes, or the result of the last action.
        float age = time - statusTime;
        if (status != null && age < 3.5f) {
            float a = Math.min(1f, Math.min(age / 0.15f, (3.5f - age) / 0.4f));
            UiKit.text(batch, helpFont, status, panelX + pad, by + buttonH + 16f * s, statusWarning ? UiKit.GOLD : UiKit.JADE, alpha * a);
        } else if (dirty) {
            UiKit.text(batch, helpFont, "Changes not applied yet", panelX + pad, by + buttonH + 16f * s, UiKit.MINT, alpha);
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            float[] r = buttonRects[i];
            boolean on = enabled(i, dirty);
            Color col = !on ? UiKit.TEXT_FAINT : i == APPLY ? UiKit.NAVY : UiKit.TEXT;
            float press = buttonPress[i] * 1.5f * s;
            UiKit.text(batch, buttonFont, Lang.t(BUTTONS[i]).toUpperCase(java.util.Locale.forLanguageTag("ru")), r[0],
                    r[1] + r[3] / 2f + buttonFont.getCapHeight() / 2f - press, r[2], Align.center, false, col, alpha);
        }
        batch.end();

        hoverTooltips(rows);
        dropdown.render(shapes, batch, s);
        if (confirming) drawConfirm(shapes, batch, width, height);
    }

    private boolean enabled(int button, boolean dirty) {
        return switch (button) {
            case APPLY, CANCEL -> dirty;
            default -> true;
        };
    }

    private String focusHelp(List<Setting<?>> rows) {
        if (focus < rows.size()) {
            Setting<?> st = rows.get(focus);
            String help = Lang.t(st.help.apply(draft));
            return st.restart ? help + " " + Lang.t("Needs a restart.") : help;
        }
        return switch (focus - rows.size()) {
            case APPLY -> Lang.t("Puts the changes into effect and saves them.");
            case CANCEL -> Lang.t("Returns to the saved values.");
            case DEFAULTS -> Lang.t("Default values for this category (apply them to keep them).");
            default -> Lang.t("Back to the menu.");
        };
    }

    private void hoverTooltips(List<Setting<?>> rows) {
        if (keyboard || dropdown.isOpen() || confirming || dragging != null) return;
        if (hoverRow >= 0 && hoverRow < rows.size()) {
            Setting<?> st = rows.get(hoverRow);
            float[] r = rowRects.get(hoverRow);
            tooltips.hover("setting:" + st.label, r[0], r[1], r[2], r[3],
                    TooltipManager.Tip.setting(st.label, st.help.apply(draft), st.restart ? "Needs a restart." : null));
        } else if (hoverButton >= 0) {
            float[] r = buttonRects[hoverButton];
            String body = switch (hoverButton) {
                case APPLY -> "Puts the changes into effect and saves them.";
                case CANCEL -> "Returns to the saved values.";
                case DEFAULTS -> "Default values for this category (apply them to keep them).";
                default -> "Back to the menu.";
            };
            tooltips.hover("button:" + hoverButton, r[0], r[1], r[2], r[3], TooltipManager.Tip.info(BUTTONS[hoverButton], body));
        }
    }

    private void drawRowShapes(ShapeRenderer shapes, Setting<?> st, int i, float a, float delta) {
        float[] r = rowRects.get(i);
        float[] c = controlRects.get(i);
        float h = rowHighlight[i];
        if (h > 0.01f) {
            Color glow = new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0.18f * h * a);
            Color none = new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0f);
            shapes.rect(r[0], r[1], r[2], r[3], glow, none, none, glow);
            shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, h * a);
            shapes.rect(r[0] - (1f - h) * 6f * s, r[1], 3f * s, r[3]);
            if (keyboard && i == focus) {
                shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.45f * a);
                float t = Math.max(1f, s);
                shapes.rect(r[0], r[1], r[2], t);
                shapes.rect(r[0], r[1] + r[3] - t, r[2], t);
                shapes.rect(r[0] + r[2] - t, r[1], t, r[3]);
            }
        }
        // A changed (not applied) value: a small gold dot before the label.
        int global = settings.indexOf(st);
        if (st.differs(draft, committed)) {
            shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, a);
            shapes.circle(r[0] + 11f * s, r[1] + r[3] / 2f, 3f * s, 10);
        }
        if (st instanceof Toggle t) {
            boolean on = t.getter.apply(draft);
            knob[global] = approach(knob[global], on ? 1f : 0f, delta / 0.14f);
            float k = knob[global];
            float w = 46f * s;
            float hh = 24f * s;
            float x = c[0] + c[2] - w;
            float y = c[1] + (c[3] - hh) / 2f;
            Color off = new Color(0.17f, 0.21f, 0.33f, a);
            Color onC = new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, a);
            UiKit.roundedRect(shapes, x, y, w, hh, hh / 2f, new Color(off).lerp(onC, k));
            shapes.setColor(k > 0.5f ? UiKit.TEXT.r : UiKit.TEXT_DIM.r, k > 0.5f ? UiKit.TEXT.g : UiKit.TEXT_DIM.g,
                    k > 0.5f ? UiKit.TEXT.b : UiKit.TEXT_DIM.b, a);
            shapes.circle(x + hh / 2f + k * (w - hh), y + hh / 2f, hh / 2f - 3f * s, 20);
        } else if (st instanceof Slider sl) {
            float target = sl.fraction(draft);
            sliderShown[global] = dragging == sl ? target : approach(sliderShown[global], target, delta / 0.12f);
            float fr = sliderShown[global];
            float trackW = c[2] - 64f * s;
            float ty = c[1] + c[3] / 2f;
            UiKit.roundedRect(shapes, c[0], ty - 2f * s, trackW, 4f * s, 2f * s, new Color(0.17f, 0.21f, 0.33f, a));
            UiKit.roundedRect(shapes, c[0], ty - 2f * s, Math.max(4f * s, trackW * fr), 4f * s, 2f * s,
                    new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, a));
            boolean active = dragging == sl || i == hoverRow;
            float kr = (active ? 9f : 7.5f) * s;
            shapes.setColor(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0.25f * a * (active ? 1f : 0f));
            shapes.circle(c[0] + trackW * fr, ty, kr + 5f * s, 20);
            shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, a);
            shapes.circle(c[0] + trackW * fr, ty, kr, 20);
        } else if (st instanceof Choice<?>) {
            boolean open = dropdown.isOpenFor(st);
            float border = open || i == hoverRow || (keyboard && i == focus) ? 0.6f : 0.22f;
            UiKit.roundedRect(shapes, c[0] - 1f, c[1] - 1f, c[2] + 2f, c[3] + 2f, 6f * s,
                    new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, border * a));
            UiKit.roundedRect(shapes, c[0], c[1], c[2], c[3], 5f * s, new Color(0.04f, 0.055f, 0.11f, 0.96f * a),
                    new Color(0.08f, 0.1f, 0.18f, 0.96f * a));
            // The chevron: down when closed, up when open.
            float cx = c[0] + c[2] - 18f * s;
            float cy = c[1] + c[3] / 2f;
            float w = 5f * s;
            shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, a);
            if (open) shapes.triangle(cx - w, cy - w * 0.5f, cx + w, cy - w * 0.5f, cx, cy + w * 0.6f);
            else shapes.triangle(cx - w, cy + w * 0.5f, cx + w, cy + w * 0.5f, cx, cy - w * 0.6f);
        }
    }

    private void drawRowText(SpriteBatch batch, Setting<?> st, int i, float[] c, BitmapFont font, float a) {
        float ty = c[1] + c[3] / 2f + font.getCapHeight() / 2f;
        if (st instanceof Toggle t) {
            String v = t.display(draft);
            float w = UiKit.width(font, v);
            UiKit.text(batch, font, v, c[0] + c[2] - 46f * s - 12f * s - w, ty, t.getter.apply(draft) ? UiKit.JADE : UiKit.TEXT_FAINT, a);
        } else if (st instanceof Slider sl) {
            String v = sl.display(draft);
            float w = UiKit.width(font, v);
            UiKit.text(batch, font, v, c[0] + c[2] - w, ty, UiKit.TEXT, a);
        } else if (st instanceof Choice<?> ch) {
            UiKit.text(batch, font, UiKit.fit(font, ch.display(draft), c[2] - 44f * s), c[0] + 14f * s, ty,
                    dropdown.isOpenFor(st) ? UiKit.JADE : UiKit.TEXT, a);
        }
    }

    private void drawButtonShape(ShapeRenderer shapes, int i, boolean on, float alpha) {
        float[] r = buttonRects[i];
        float h = on ? buttonHighlight[i] : 0f;
        float press = buttonPress[i] * 2f * s;
        float x = r[0] + press;
        float y = r[1] + press;
        float w = r[2] - 2f * press;
        float hh = r[3] - 2f * press;
        float rad = 6f * s;
        if (i == APPLY) {
            Color low = on ? new Color(UiKit.ACCENT).mul(0.8f + 0.2f * h, 0.8f + 0.2f * h, 0.8f + 0.2f * h, alpha)
                    : new Color(0.12f, 0.14f, 0.22f, alpha);
            Color high = on ? new Color(UiKit.JADE).lerp(UiKit.ACCENT, 0.5f - 0.3f * h).mul(1f, 1f, 1f, alpha)
                    : new Color(0.15f, 0.18f, 0.28f, alpha);
            if (on && h > 0.01f) {
                UiKit.roundedRect(shapes, x - 4f * s, y - 4f * s, w + 8f * s, hh + 8f * s, rad + 4f * s,
                        new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0.18f * h * alpha));
            }
            UiKit.roundedRect(shapes, x, y, w, hh, rad, low, high);
        } else {
            float border = on ? 0.3f + 0.4f * h : 0.12f;
            UiKit.roundedRect(shapes, x - 1f, y - 1f, w + 2f, hh + 2f, rad + 1f, new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, border * alpha));
            Color fill = new Color(0.045f, 0.06f, 0.12f, alpha).lerp(new Color(UiKit.DEEP.r, UiKit.DEEP.g, UiKit.DEEP.b, alpha), h * 0.8f);
            UiKit.roundedRect(shapes, x, y, w, hh, rad, fill);
        }
        if (keyboard && focus - rowRects.size() == i) {
            shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.7f * alpha);
            float t = Math.max(1f, s);
            shapes.rect(r[0] - 3f * s, r[1] - 3f * s - t, r[2] + 6f * s, t);
        }
    }

    private void drawConfirm(ShapeRenderer shapes, SpriteBatch batch, int width, int height) {
        float a = UiKit.ease(confirmTime / 0.18f);
        BitmapFont title = fonts.get(UiFonts.Weight.EXTRABOLD, 22f * s);
        BitmapFont body = fonts.get(UiFonts.Weight.MEDIUM, 15f * s);
        BitmapFont buttonFont = fonts.get(UiFonts.Weight.BOLD, 15f * s);
        float w = 520f * s;
        float h = 210f * s;
        float x = (width - w) / 2f;
        float y = (height - h) / 2f + (1f - a) * 10f * s;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.dim(shapes, width, height, 0.55f * a);
        UiKit.panel(shapes, x, y, w, h, a, 3f * s);
        float bw = (w - 2f * 28f * s - 2f * 12f * s) / 3f;
        for (int i = 0; i < CONFIRM.length; i++) {
            float bx = x + 28f * s + i * (bw + 12f * s);
            confirmRects[i] = new float[]{bx, y + 26f * s, bw, 40f * s};
            boolean focused = i == confirmFocus;
            Color fill = i == 0 ? new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, a)
                    : new Color(0.045f, 0.06f, 0.12f, a).lerp(new Color(UiKit.DEEP.r, UiKit.DEEP.g, UiKit.DEEP.b, a), focused ? 0.8f : 0f);
            UiKit.roundedRect(shapes, bx - 1f, y + 26f * s - 1f, bw + 2f, 40f * s + 2f, 7f * s,
                    new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, (focused ? 0.8f : 0.3f) * a));
            UiKit.roundedRect(shapes, bx, y + 26f * s, bw, 40f * s, 6f * s, fill);
        }
        shapes.end();
        batch.begin();
        UiKit.text(batch, title, "Changes not applied", x + 28f * s, y + h - 28f * s, UiKit.TEXT, a);
        UiKit.text(batch, body, "Apply the changes before leaving, or discard them?", x + 28f * s, y + h - 72f * s, w - 56f * s,
                Align.left, true, UiKit.TEXT_DIM, a);
        for (int i = 0; i < CONFIRM.length; i++) {
            float[] r = confirmRects[i];
            UiKit.text(batch, buttonFont, Lang.t(CONFIRM[i]).toUpperCase(java.util.Locale.forLanguageTag("ru")), r[0],
                    r[1] + r[3] / 2f + buttonFont.getCapHeight() / 2f, r[2], Align.center, false, i == 0 ? UiKit.NAVY : UiKit.TEXT, a);
        }
        batch.end();
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    /** Height of the window in the last frame (the drop-down list uses it to open upwards). */
    int screenHeight() {
        return screenHeight;
    }
}
