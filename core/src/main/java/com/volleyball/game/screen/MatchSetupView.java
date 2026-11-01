package com.volleyball.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.i18n.Lang;
import com.volleyball.game.match.FirstServe;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.TeamCatalog;
import com.volleyball.game.simulation.TeamFactory;
import com.volleyball.game.training.Drill;
import com.volleyball.game.ui.DropdownMenu;
import com.volleyball.game.ui.RoleGuide;
import com.volleyball.game.ui.TooltipManager;
import com.volleyball.game.ui.UiFonts;
import com.volleyball.game.ui.UiKit;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The quick-match set-up: on the left the options (role, AI level, players, format, system, the
 * two clubs, the kit and the first serve) with drop-down lists, and the Start and Back buttons;
 * on the right the two clubs as cards facing each other (or the role guide while the role is
 * being chosen) and a summary of the match that follows every change.
 *
 * <p>Only what the game really plays is offered: the clubs of the catalog with their real kits
 * and squads, the formats, systems and AI levels of the match engine. Teams change ends after
 * every set anyway, so instead of a court side there is the first serve (a coin toss, you, the
 * opponent).
 */
final class MatchSetupView {
    /** What the menu does with the choices. */
    interface Host {
        void start(MatchSetup setup);
    }

    static final MatchFormat[] FORMATS = {
            MatchFormat.official(),
            new MatchFormat(2, 25, 15, 2, RuleMode.OFFICIAL, 8f),
            new MatchFormat(2, 15, 15, 2, RuleMode.OFFICIAL, 8f),
            new MatchFormat(1, 25, 25, 2, RuleMode.OFFICIAL, 8f),
            MatchFormat.simplified()};
    static final String[] FORMAT_NAMES = {
            "Best of five - official", "Best of three - 25 points", "Best of three - 15 points", "One set to 25",
            "Simplified rules - best of three"};
    private static final SessionMode[] PLAYER_MODES = {SessionMode.SINGLE, SessionMode.LOCAL_VERSUS, SessionMode.LOCAL_COOP};

    static final int ROLE = 0;
    static final int LEVEL = 1;
    static final int PLAYERS = 2;
    static final int FORMAT = 3;
    static final int SYSTEM = 4;
    static final int YOUR_TEAM = 5;
    static final int OPPONENT = 6;
    static final int KIT = 7;
    static final int FIRST_SERVE = 8;
    private static final String[] LABELS = {"Your role", "AI level", "Players", "Format", "System", "Your team", "Opponent",
            "Your kit", "First serve"};
    private static final String[] BUTTONS = {"Start match", "Back"};

    private final UiFonts fonts;
    private final TooltipManager tooltips;
    private final DropdownMenu dropdown;
    private final Host host;
    private final Map<String, Team> teamPreviews = new HashMap<>();

    // The choices.
    int roleIndex;
    Difficulty difficulty = Difficulty.INTERMEDIATE;
    SessionMode players = SessionMode.SINGLE;
    int formatIndex;
    OffenseSystem system = OffenseSystem.FIVE_ONE;
    /** Catalog index of the person's club and of the opponent; -1 = drawn at random. */
    int homeIndex = 0;
    int awayIndex = 1;
    boolean awayKit;
    FirstServe firstServe = FirstServe.TOSS;

    /** Focus: a row (0..8) or a button (9, 10). */
    private int focus;
    private boolean keyboard;
    private int hoverRow = -1;
    private int hoverButton = -1;
    private int pressedButton = -1;
    private float time;
    private float openTime;
    /** When the summary last changed (it flashes softly). */
    private float changedAt = -10f;
    private final float[] rowHighlight = new float[LABELS.length];
    private final float[] buttonHighlight = new float[BUTTONS.length];
    private final float[] buttonPress = new float[BUTTONS.length];
    private final float[][] rowRects = new float[LABELS.length][];
    private final float[][] controlRects = new float[LABELS.length][];
    private final float[][] buttonRects = new float[BUTTONS.length][];
    private float s = 1f;

    MatchSetupView(UiFonts fonts, TooltipManager tooltips, Host host) {
        this.fonts = fonts;
        this.tooltips = tooltips;
        this.host = host;
        this.dropdown = new DropdownMenu(fonts);
    }

    /** Opens the screen; {@code mode} preselects the players, {@code row} the focused row. */
    void open(SessionMode mode, int row) {
        players = mode;
        focus = row;
        openTime = 0f;
        dropdown.close();
    }

    MatchSetup setup() {
        return new MatchSetup(Drill.MATCH, false, difficulty, players, RoleGuide.ORDER.get(roleIndex), FORMATS[formatIndex], system,
                teamId(homeIndex), teamId(awayIndex), firstServe, awayKit);
    }

    String footerHints() {
        return "↑ ↓  choose      ← →  change      ENTER  select      ESC  back";
    }

    // ------------------------------------------------------------------ options

    private static String teamId(int index) {
        return index < 0 ? MatchSetup.RANDOM : TeamCatalog.all().get(index).id();
    }

    private static String teamName(int index) {
        return index < 0 ? "Random" : TeamCatalog.all().get(index).name();
    }

    /** The values a row offers, as shown in its list. */
    private List<String> options(int row) {
        List<String> list = new ArrayList<>();
        switch (row) {
            case ROLE -> {
                for (PlayerRole r : RoleGuide.ORDER) list.add(RoleGuide.of(r).title());
            }
            case LEVEL -> {
                for (Difficulty d : Difficulty.values()) list.add(d.label);
            }
            case PLAYERS -> {
                for (SessionMode m : PLAYER_MODES) list.add(m.label);
            }
            case FORMAT -> list.addAll(List.of(FORMAT_NAMES));
            case SYSTEM -> {
                for (OffenseSystem o : OffenseSystem.values()) list.add(o.label);
            }
            case YOUR_TEAM, OPPONENT -> {
                list.add("Random");
                for (TeamCatalog.Entry e : TeamCatalog.all()) list.add(e.name());
            }
            case KIT -> list.addAll(List.of("Home kit", "Away kit"));
            case FIRST_SERVE -> {
                for (FirstServe f : FirstServe.values()) list.add(f.label);
            }
            default -> {
            }
        }
        return list;
    }

    private int index(int row) {
        return switch (row) {
            case ROLE -> roleIndex;
            case LEVEL -> difficulty.ordinal();
            case PLAYERS -> java.util.Arrays.asList(PLAYER_MODES).indexOf(players);
            case FORMAT -> formatIndex;
            case SYSTEM -> system.ordinal();
            case YOUR_TEAM -> homeIndex + 1;
            case OPPONENT -> awayIndex + 1;
            case KIT -> awayKit ? 1 : 0;
            case FIRST_SERVE -> firstServe.ordinal();
            default -> 0;
        };
    }

    private void set(int row, int index) {
        switch (row) {
            case ROLE -> roleIndex = index;
            case LEVEL -> difficulty = Difficulty.values()[index];
            case PLAYERS -> players = PLAYER_MODES[index];
            case FORMAT -> formatIndex = index;
            case SYSTEM -> system = OffenseSystem.values()[index];
            case YOUR_TEAM -> homeIndex = index - 1;
            case OPPONENT -> awayIndex = index - 1;
            case KIT -> awayKit = index == 1;
            case FIRST_SERVE -> firstServe = FirstServe.values()[index];
            default -> {
            }
        }
        changedAt = time;
    }

    private void step(int row, int direction) {
        int n = options(row).size();
        set(row, (index(row) + direction + n) % n);
    }

    private String help(int row) {
        return switch (row) {
            case ROLE -> "The position you play. The role guide on the right tells what you do in each phase.";
            case LEVEL -> switch (difficulty) {
                case BEGINNER -> "Beginner: slow reactions, simple tactics, mistakes in positioning.";
                case INTERMEDIATE -> "Intermediate: steady reception, basic combinations, sensible blocks.";
                case PROFESSIONAL -> "Professional: reads the setter, learns your habits, group blocks, changes tactics.";
            };
            case PLAYERS -> "One person, or two on one keyboard (against each other or in the same team).";
            case FORMAT -> "Number of sets and points; official or simplified rules.";
            case SYSTEM -> "System " + system.label + ": " + system.description;
            case YOUR_TEAM -> "The club you play for: its players, traits and kit are on its card.";
            case OPPONENT -> "The club you play against.";
            case KIT -> "Your team's shirts. The opponent changes to its away kit if the colours clash.";
            case FIRST_SERVE -> "Who serves the first rally. A coin toss is drawn at every start.";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ input

    /** Keyboard; returns true when the screen should be left. */
    boolean handleKeys() {
        if (dropdown.handleKeys()) {
            keyboard = true;
            return false;
        }
        int count = LABELS.length + BUTTONS.length;
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            focus = focus > LABELS.length ? focus - 1 : (focus - 1 + count) % count;
            keyboard = true;
            tooltips.dismiss();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            focus = (focus + 1) % count;
            keyboard = true;
            tooltips.dismiss();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            keyboard = true;
            if (focus < LABELS.length) step(focus, -1);
            else focus = LABELS.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            keyboard = true;
            if (focus < LABELS.length) step(focus, 1);
            else focus = LABELS.length + 1;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            keyboard = true;
            if (focus < LABELS.length) openDropdown(focus);
            else return press(focus - LABELS.length);
        }
        return Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE);
    }

    private boolean press(int button) {
        if (button == 0) {
            host.start(setup());
            return false;
        }
        return true;
    }

    private void openDropdown(int row) {
        float[] c = controlRects[row];
        if (c == null) return;
        dropdown.open(row, options(row), index(row), c[0], c[1], c[2], c[3], s, i -> set(row, i));
    }

    void mouseMoved(float x, float y) {
        keyboard = false;
        if (dropdown.isOpen()) {
            dropdown.mouseMoved(x, y);
            return;
        }
        hoverRow = indexAt(rowRects, x, y);
        hoverButton = indexAt(buttonRects, x, y);
        if (hoverRow >= 0) focus = hoverRow;
        else if (hoverButton >= 0) focus = LABELS.length + hoverButton;
    }

    void touchDown(float x, float y, int button) {
        keyboard = false;
        if (dropdown.isOpen()) {
            int row = indexAt(controlRects, x, y);
            if (row >= 0 && dropdown.isOpenFor(row)) dropdown.close();
            else dropdown.touchDown(x, y);
            return;
        }
        int b = indexAt(buttonRects, x, y);
        if (b >= 0) {
            pressedButton = b;
            return;
        }
        int row = indexAt(rowRects, x, y);
        if (row < 0) return;
        focus = row;
        tooltips.dismiss();
        if (button == Input.Buttons.RIGHT) step(row, -1);
        else openDropdown(row);
    }

    void touchDragged(float x, float y) {
        if (pressedButton >= 0 && indexAt(buttonRects, x, y) != pressedButton) pressedButton = -1;
    }

    /** A release; returns true when the screen should be left. */
    boolean touchUp(float x, float y) {
        int b = pressedButton;
        pressedButton = -1;
        return b >= 0 && indexAt(buttonRects, x, y) == b && press(b);
    }

    private static int indexAt(float[][] rects, float x, float y) {
        for (int i = 0; i < rects.length; i++) {
            float[] r = rects[i];
            if (r != null && x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    void render(ShapeRenderer shapes, SpriteBatch batch, float scale, int width, int height, float left, float top, float bottom,
                float alpha, float delta) {
        s = scale;
        time += delta;
        openTime += delta;
        dropdown.update(delta);
        float appear = UiKit.ease(openTime / 0.24f) * alpha;
        float panelW = Math.min(560f * s, width * 0.42f);
        drawOptions(shapes, batch, left, top, bottom, panelW, appear, delta);
        float rightX = left + panelW + 28f * s;
        float rightW = width - rightX - 50f * s;
        if (rightW > 360f * s) {
            float summaryH = 196f * s;
            float cardsTop = top;
            float cardsBottom = bottom + summaryH + 20f * s;
            if (focus == ROLE) drawRoleGuide(shapes, batch, rightX, cardsTop, rightW, cardsTop - cardsBottom, appear);
            else drawTeams(shapes, batch, rightX, cardsTop, rightW, cardsTop - cardsBottom, appear);
            drawSummary(shapes, batch, rightX, bottom, rightW, summaryH, appear);
        }
        hoverTooltips();
        dropdown.render(shapes, batch, s);
    }

    private void drawOptions(ShapeRenderer shapes, SpriteBatch batch, float x, float top, float bottom, float w, float a, float delta) {
        float pad = 26f * s;
        BitmapFont title = fonts.get(UiFonts.Weight.EXTRABOLD, 22f * s);
        BitmapFont label = fonts.get(UiFonts.Weight.SEMIBOLD, 17f * s);
        BitmapFont value = fonts.get(UiFonts.Weight.SEMIBOLD, 15f * s);
        BitmapFont help = fonts.get(UiFonts.Weight.MEDIUM, 13.5f * s);
        BitmapFont buttonFont = fonts.get(UiFonts.Weight.BOLD, 16f * s);
        float rowH = Math.min(44f * s, (top - bottom - 230f * s) / LABELS.length);
        float rowsTop = top - pad - 40f * s;
        float controlW = Math.min(250f * s, w * 0.48f);
        float controlX = x + w - pad - controlW;
        for (int i = 0; i < LABELS.length; i++) {
            float ry = rowsTop - (i + 1) * rowH;
            rowRects[i] = new float[]{x + pad - 12f * s, ry, w - 2f * pad + 24f * s, rowH - 4f * s};
            controlRects[i] = new float[]{controlX, ry + (rowH - 4f * s - 30f * s) / 2f, controlW, 30f * s};
            rowHighlight[i] = approach(rowHighlight[i], i == focus ? 1f : 0f, delta / 0.14f);
        }
        float buttonH = 46f * s;
        float by = bottom + pad;
        float backW = 130f * s;
        buttonRects[0] = new float[]{x + pad, by, w - 2f * pad - backW - 12f * s, buttonH};
        buttonRects[1] = new float[]{x + w - pad - backW, by, backW, buttonH};
        for (int i = 0; i < BUTTONS.length; i++) {
            boolean focused = focus == LABELS.length + i;
            buttonHighlight[i] = approach(buttonHighlight[i], focused || i == hoverButton ? 1f : 0f, delta / 0.14f);
            buttonPress[i] = approach(buttonPress[i], i == pressedButton ? 1f : 0f, delta / 0.08f);
        }

        begin(shapes);
        UiKit.panel(shapes, x, bottom, w, top - bottom, 0.94f * a, 3f * s);
        for (int i = 0; i < LABELS.length; i++) {
            float[] r = rowRects[i];
            float[] c = controlRects[i];
            float h = rowHighlight[i];
            if (h > 0.01f) {
                Color glow = new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0.16f * h * a);
                Color none = new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0f);
                shapes.rect(r[0], r[1], r[2], r[3], glow, none, none, glow);
                shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, h * a);
                shapes.rect(r[0] - (1f - h) * 6f * s, r[1], 3f * s, r[3]);
            }
            boolean open = dropdown.isOpenFor(i);
            float border = open || i == hoverRow || (keyboard && i == focus) ? 0.6f : 0.22f;
            UiKit.roundedRect(shapes, c[0] - 1f, c[1] - 1f, c[2] + 2f, c[3] + 2f, 6f * s,
                    new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, border * a));
            UiKit.roundedRect(shapes, c[0], c[1], c[2], c[3], 5f * s, new Color(0.04f, 0.055f, 0.11f, 0.96f * a),
                    new Color(0.08f, 0.1f, 0.18f, 0.96f * a));
            float cx = c[0] + c[2] - 16f * s;
            float cy = c[1] + c[3] / 2f;
            float tw = 5f * s;
            shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, a);
            if (open) shapes.triangle(cx - tw, cy - tw * 0.5f, cx + tw, cy - tw * 0.5f, cx, cy + tw * 0.6f);
            else shapes.triangle(cx - tw, cy + tw * 0.5f, cx + tw, cy + tw * 0.5f, cx, cy - tw * 0.6f);
            // The team rows carry a chip in the club's colour.
            if (i == YOUR_TEAM || i == OPPONENT) {
                int idx = i == YOUR_TEAM ? homeIndex : awayIndex;
                Color chip = idx < 0 ? UiKit.TEXT_FAINT : TeamCatalog.all().get(idx).shirt();
                shapes.setColor(chip.r, chip.g, chip.b, a);
                shapes.rect(c[0] + 9f * s, c[1] + 8f * s, 4f * s, c[3] - 16f * s);
            }
        }
        UiKit.divider(shapes, x + pad, by + buttonH + 66f * s, w - 2f * pad, a);
        drawButtons(shapes, a);
        shapes.end();

        batch.begin();
        UiKit.text(batch, title, "MATCH SET-UP", x + pad, top - pad, UiKit.TEXT, a);
        for (int i = 0; i < LABELS.length; i++) {
            float[] r = rowRects[i];
            float[] c = controlRects[i];
            float h = rowHighlight[i];
            UiKit.text(batch, label, LABELS[i], r[0] + 18f * s + h * 4f * s, r[1] + r[3] / 2f + label.getCapHeight() / 2f,
                    h > 0.5f ? UiKit.TEXT : UiKit.TEXT_DIM, a);
            boolean team = i == YOUR_TEAM || i == OPPONENT;
            float textX = c[0] + (team ? 20f : 12f) * s;
            String shown = UiKit.fit(value, options(i).get(index(i)), c[0] + c[2] - 30f * s - textX);
            UiKit.text(batch, value, shown, textX, c[1] + c[3] / 2f + value.getCapHeight() / 2f, dropdown.isOpenFor(i) ? UiKit.JADE : UiKit.TEXT, a);
        }
        String text = focus < LABELS.length ? help(focus) : focus == LABELS.length
                ? "Play with these settings." : "Back to the menu.";
        UiKit.text(batch, help, text, x + pad, by + buttonH + 54f * s, w - 2f * pad, Align.left, true, UiKit.TEXT_DIM, a);
        for (int i = 0; i < BUTTONS.length; i++) {
            float[] r = buttonRects[i];
            float press = buttonPress[i] * 1.5f * s;
            String t = Lang.t(BUTTONS[i]).toUpperCase(Locale.forLanguageTag("ru"));
            UiKit.text(batch, buttonFont, t, r[0], r[1] + r[3] / 2f + buttonFont.getCapHeight() / 2f - press, r[2], Align.center, false,
                    i == 0 ? UiKit.NAVY : UiKit.TEXT, a);
        }
        batch.end();
    }

    private void drawButtons(ShapeRenderer shapes, float a) {
        for (int i = 0; i < BUTTONS.length; i++) {
            float[] r = buttonRects[i];
            float h = buttonHighlight[i];
            float press = buttonPress[i] * 2f * s;
            float x = r[0] + press;
            float y = r[1] + press;
            float w = r[2] - 2f * press;
            float bh = r[3] - 2f * press;
            float rad = 7f * s;
            if (i == 0) {
                if (h > 0.01f) {
                    UiKit.roundedRect(shapes, x - 5f * s, y - 5f * s, w + 10f * s, bh + 10f * s, rad + 5f * s,
                            new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0.2f * h * a));
                }
                Color low = new Color(UiKit.ACCENT).mul(0.78f + 0.22f * h, 0.78f + 0.22f * h, 0.78f + 0.22f * h, a);
                Color high = new Color(UiKit.ACCENT).lerp(Color.WHITE, 0.12f * h).mul(1f, 1f, 1f, a);
                UiKit.roundedRect(shapes, x, y, w, bh, rad, low, high);
            } else {
                UiKit.roundedRect(shapes, x - 1f, y - 1f, w + 2f, bh + 2f, rad + 1f,
                        new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, (0.3f + 0.4f * h) * a));
                UiKit.roundedRect(shapes, x, y, w, bh, rad, new Color(0.045f, 0.06f, 0.12f, a)
                        .lerp(new Color(UiKit.DEEP.r, UiKit.DEEP.g, UiKit.DEEP.b, a), h * 0.8f));
            }
            if (keyboard && focus == LABELS.length + i) {
                shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.7f * a);
                shapes.rect(r[0] - 3f * s, r[1] - 4f * s, r[2] + 6f * s, Math.max(1f, s));
            }
        }
    }

    /** The two clubs facing each other; the one being chosen is outlined. */
    private void drawTeams(ShapeRenderer shapes, SpriteBatch batch, float x, float top, float w, float h, float a) {
        float vsW = 56f * s;
        float cardW = (w - vsW) / 2f;
        drawTeamCard(shapes, batch, homeIndex, x, top, cardW, h, true, focus == YOUR_TEAM || focus == KIT, a);
        drawTeamCard(shapes, batch, awayIndex, x + cardW + vsW, top, cardW, h, false, focus == OPPONENT, a);
        batch.begin();
        BitmapFont vs = fonts.get(UiFonts.Weight.BLACK, 26f * s);
        UiKit.text(batch, vs, "VS", x + cardW, top - h / 2f + vs.getCapHeight() / 2f, vsW, Align.center, false, UiKit.GOLD, a);
        batch.end();
    }

    private void drawTeamCard(ShapeRenderer shapes, SpriteBatch batch, int index, float x, float top, float w, float h, boolean yours,
                              boolean chosen, float a) {
        float pad = 22f * s;
        float y = top - h;
        BitmapFont tag = fonts.get(UiFonts.Weight.EXTRABOLD, 11f * s);
        BitmapFont big = fonts.get(UiFonts.Weight.BLACK, 40f * s);
        BitmapFont name = fonts.get(UiFonts.Weight.BOLD, 17f * s);
        BitmapFont body = fonts.get(UiFonts.Weight.MEDIUM, 13.5f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 12f * s);
        TeamCatalog.Entry entry = index < 0 ? null : TeamCatalog.all().get(index);
        Color[] kit = entry == null ? null : kitOf(entry, yours);

        begin(shapes);
        if (chosen) {
            UiKit.roundedRect(shapes, x - 2f, y - 2f, w + 4f, h + 4f, 12f * s + 2f,
                    new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.7f * a));
        }
        UiKit.panel(shapes, x, y, w, h, 0.94f * a, 0f);
        // The club's colour band along the top, fading into the card.
        Color band = kit == null ? new Color(0.3f, 0.33f, 0.42f, a) : new Color(kit[0].r, kit[0].g, kit[0].b, 0.9f * a);
        Color bandNone = new Color(band.r, band.g, band.b, 0f);
        shapes.rect(x + 6f * s, top - 96f * s, w - 12f * s, 90f * s, bandNone, bandNone, band, band);
        if (entry != null) {
            // Kit swatches and the strength bar.
            float swY = y + pad + 58f * s;
            UiKit.roundedRect(shapes, x + pad, swY, 30f * s, 20f * s, 4f * s, new Color(kit[0].r, kit[0].g, kit[0].b, a));
            UiKit.roundedRect(shapes, x + pad + 36f * s, swY, 20f * s, 20f * s, 4f * s, new Color(kit[1].r, kit[1].g, kit[1].b, a));
            UiKit.bar(shapes, x + pad, y + pad + 14f * s, w - 2f * pad, 6f * s, (entry.strength() + 8) / 16f, UiKit.ACCENT, a);
        }
        shapes.end();

        batch.begin();
        float ty = top - 18f * s;
        UiKit.text(batch, tag, yours ? "YOUR TEAM" : "OPPONENT", x + pad, ty, UiKit.TEXT, a);
        if (entry == null) {
            UiKit.text(batch, big, "?", x + pad, ty - 30f * s, UiKit.TEXT, a);
            UiKit.text(batch, name, "Random club", x + pad, ty - 98f * s, w - 2f * pad, Align.left, false, UiKit.TEXT, a);
            UiKit.text(batch, body, "A club is drawn when the match starts - never the same as the other side.", x + pad, ty - 128f * s,
                    w - 2f * pad, Align.left, true, UiKit.TEXT_DIM, a);
            batch.end();
            return;
        }
        UiKit.text(batch, big, entry.shortName(), x + pad, ty - 30f * s, UiKit.TEXT, a);
        UiKit.text(batch, name, entry.name().toUpperCase(Locale.ROOT), x + pad, ty - 98f * s, w - 2f * pad, Align.left, false, UiKit.TEXT, a);
        float yy = ty - 126f * s;
        yy -= UiKit.text(batch, body, entry.style(), x + pad, yy, w - 2f * pad, Align.left, true, UiKit.MINT, a) + 10f * s;
        UiKit.text(batch, body, Lang.f("Head coach: %s", entry.coach()), x + pad, yy, w - 2f * pad, Align.left, false, UiKit.TEXT_DIM, a);
        yy -= 24f * s;
        Team team = teamPreviews.computeIfAbsent(entry.id(), id -> TeamFactory.build(entry, CourtSide.HOME, 0, entry));
        // Up to three players with their traits (as many as fit above the kit).
        float limit = y + pad + 96f * s;
        for (VolleyballPlayer p : team.players()) {
            if (yy < limit) break;
            String traits = p.traitLabel();
            if (traits == null || traits.isBlank()) continue;
            UiKit.text(batch, small, "#" + p.number + " " + p.name, x + pad, yy, UiKit.TEXT, a);
            UiKit.text(batch, small, traits, x + pad + 120f * s, yy, w - 2f * pad - 120f * s, Align.left, false, UiKit.JADE, a);
            yy -= 20f * s;
        }
        UiKit.text(batch, small, awayKit && yours ? "AWAY KIT" : kitLabel(entry, yours), x + pad + 66f * s, y + pad + 73f * s,
                UiKit.TEXT_DIM, a);
        UiKit.text(batch, small, "STRENGTH", x + pad, y + pad + 40f * s, UiKit.TEXT_DIM, a);
        String strength = entry.strength() > 0 ? "+" + entry.strength() : String.valueOf(entry.strength());
        float sw = UiKit.width(small, strength);
        UiKit.text(batch, small, strength, x + w - pad - sw, y + pad + 40f * s, UiKit.TEXT, a);
        batch.end();
    }

    /** The kit a club will wear in this match (the person's choice; the opponent changes on a clash). */
    private Color[] kitOf(TeamCatalog.Entry entry, boolean yours) {
        if (yours) return entry.kitAgainst(Color.CLEAR, true, awayKit);
        Color[] mine = homeIndex < 0 ? null : TeamCatalog.all().get(homeIndex).kitAgainst(Color.CLEAR, true, awayKit);
        return entry.kitAgainst(mine == null ? Color.CLEAR : mine[0], false, false);
    }

    private String kitLabel(TeamCatalog.Entry entry, boolean yours) {
        if (yours) return awayKit ? "AWAY KIT" : "HOME KIT";
        Color[] k = kitOf(entry, false);
        return k[0].equals(entry.shirt()) ? "HOME KIT" : "AWAY KIT";
    }

    private void drawRoleGuide(ShapeRenderer shapes, SpriteBatch batch, float x, float top, float w, float h, float a) {
        RoleGuide guide = RoleGuide.of(RoleGuide.ORDER.get(roleIndex));
        float pad = 26f * s;
        BitmapFont label = fonts.get(UiFonts.Weight.SEMIBOLD, 12f * s);
        BitmapFont titleFont = fonts.get(UiFonts.Weight.BLACK, 38f * s);
        BitmapFont motto = fonts.get(UiFonts.Weight.SEMIBOLD, 16f * s);
        BitmapFont topic = fonts.get(UiFonts.Weight.BOLD, 13f * s);
        BitmapFont body = fonts.get(UiFonts.Weight.MEDIUM, 13.5f * s);
        begin(shapes);
        UiKit.panel(shapes, x, top - h, w, h, 0.94f * a, 3f * s);
        for (int i = 0; i < RoleGuide.ORDER.size(); i++) {
            shapes.setColor(i == roleIndex ? UiKit.GOLD : new Color(1f, 1f, 1f, 0.12f));
            shapes.rect(x + pad + i * 26f * s, top - h + pad * 0.6f, 18f * s, 4f * s);
        }
        shapes.end();
        batch.begin();
        float y = top - pad;
        UiKit.text(batch, label, "ROLE GUIDE  ·  " + (roleIndex + 1) + " / " + RoleGuide.ORDER.size(), x + pad, y, UiKit.JADE, a);
        y -= 24f * s;
        UiKit.text(batch, titleFont, guide.title().toUpperCase(Locale.ROOT), x + pad, y, UiKit.TEXT, a);
        y -= 46f * s;
        UiKit.text(batch, motto, guide.motto(), x + pad, y, UiKit.MINT, a);
        y -= 34f * s;
        float textW = w - 2f * pad - 110f * s;
        for (RoleGuide.Item item : guide.items()) {
            if (y < top - h + pad + 20f * s) break;
            UiKit.text(batch, topic, item.topic().toUpperCase(Locale.ROOT), x + pad, y, UiKit.JADE, a);
            float th = UiKit.text(batch, body, item.text(), x + pad + 110f * s, y, textW, Align.left, true, UiKit.TEXT, a);
            y -= Math.max(20f * s, th) + 12f * s;
        }
        batch.end();
    }

    /** What will be played, in a few lines; it flashes softly when something changes. */
    private void drawSummary(ShapeRenderer shapes, SpriteBatch batch, float x, float y, float w, float h, float a) {
        float pad = 24f * s;
        float flash = Math.max(0f, 1f - (time - changedAt) / 0.4f);
        BitmapFont head = fonts.get(UiFonts.Weight.EXTRABOLD, 12f * s);
        BitmapFont match = fonts.get(UiFonts.Weight.BOLD, 19f * s);
        BitmapFont line = fonts.get(UiFonts.Weight.MEDIUM, 14.5f * s);
        begin(shapes);
        UiKit.panel(shapes, x, y, w, h, 0.94f * a, 3f * s);
        if (flash > 0f) {
            UiKit.roundedRect(shapes, x, y, w, h, 12f * s, new Color(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, 0.07f * flash * a));
        }
        shapes.end();
        batch.begin();
        float ty = y + h - pad;
        UiKit.text(batch, head, "MATCH SUMMARY", x + pad, ty, UiKit.JADE, a);
        ty -= 30f * s;
        String home = teamName(homeIndex);
        String away = teamName(awayIndex);
        UiKit.text(batch, match, Lang.f("%s against %s", Lang.t(home), Lang.t(away)), x + pad, ty, w - 2f * pad, Align.left, false,
                UiKit.TEXT, a);
        ty -= 32f * s;
        String[] lines = {
                Lang.f("Format: %s", Lang.t(FORMAT_NAMES[formatIndex])),
                Lang.f("You: %s, system %s", Lang.t(RoleGuide.of(RoleGuide.ORDER.get(roleIndex)).title()), system.label),
                Lang.f("AI level: %s   ·   Players: %s", Lang.t(difficulty.label), Lang.t(players.label)),
                Lang.f("First serve: %s   ·   Your kit: %s", Lang.t(firstServe.label), Lang.t(awayKit ? "Away kit" : "Home kit"))};
        for (String l : lines) {
            UiKit.text(batch, line, l, x + pad, ty, w - 2f * pad, Align.left, false, UiKit.TEXT_DIM, a);
            ty -= 25f * s;
        }
        batch.end();
    }

    private void hoverTooltips() {
        if (keyboard || dropdown.isOpen()) return;
        if (hoverRow >= 0) {
            float[] r = rowRects[hoverRow];
            tooltips.hover("setup:" + hoverRow, r[0], r[1], r[2], r[3], TooltipManager.Tip.setting(LABELS[hoverRow], help(hoverRow), null));
        } else if (hoverButton == 0) {
            float[] r = buttonRects[0];
            tooltips.hover("setup:start", r[0], r[1], r[2], r[3],
                    TooltipManager.Tip.action("Start match", "Play with these settings.", "ENTER", "Start"));
        }
    }

    private static void begin(ShapeRenderer shapes) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
