package com.volleyball.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.volleyball.game.VolleyballGame;
import com.volleyball.game.config.ConfigStore;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.tactics.Combination;
import com.volleyball.game.tactics.Playbook;
import com.volleyball.game.tactics.PlaybookStore;
import com.volleyball.game.tactics.Tempo;
import com.volleyball.game.ui.UiFonts;
import com.volleyball.game.ui.UiKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The playbook editor: every attacking combination drawn on the own half of the court (net at the
 * top) — each hitter's approach from its start to its hitting spot, decoys dashed, the planned
 * set's spot — with its preconditions and developments. Built-in plays can be studied and copied;
 * custom plays are edited with the mouse (drag the start or the end of a run) and the keys, and
 * saved to {@code playbook.json}; the computer teams use them in matches.
 */
public final class PlaybookScreen extends ScreenAdapter {
    /** Labels drawn on the wooden court. */
    private static final Color COURT_INK = new Color(0.2f, 0.12f, 0.06f, 1f);
    private static final Color[] LANE_COLORS = {
            new Color(0.35f, 0.75f, 1f, 1f),   // outside
            new Color(1f, 0.78f, 0.3f, 1f),    // middle
            new Color(0.55f, 1f, 0.55f, 1f),   // right
            new Color(1f, 0.45f, 0.75f, 1f),   // back middle
            new Color(0.8f, 0.6f, 1f, 1f)      // back right
    };
    private static final float HALF_WIDTH = 4.5f;
    private static final float DEPTH = 9f;

    /** A play being edited (combinations themselves are immutable). */
    private static final class Draft {
        String id;
        String name;
        String description;
        PassRating minPass;
        boolean readBlock;
        final List<RunDraft> runs = new ArrayList<>();
        final List<OptionDraft> options = new ArrayList<>();
        boolean builtIn;

        static Draft of(Combination c) {
            Draft d = new Draft();
            d.id = c.id();
            d.name = c.name();
            d.description = c.description();
            d.minPass = c.minPass();
            d.readBlock = c.readBlock();
            d.builtIn = c.builtIn();
            for (Combination.Run r : c.runs()) d.runs.add(new RunDraft(r));
            for (Combination.Option o : c.options()) d.options.add(new OptionDraft(o.set(), o.hitter()));
            return d;
        }

        Combination toCombination() {
            List<Combination.Run> rs = new ArrayList<>();
            for (RunDraft r : runs) rs.add(new Combination.Run(r.lane, r.startDepth, r.startLateral, r.targetDepth, r.targetLateral, r.tempo, r.decoy));
            List<Combination.Option> os = new ArrayList<>();
            for (OptionDraft o : options) os.add(new Combination.Option(o.set, o.hitter));
            return new Combination(id, name, description, minPass, List.copyOf(rs), List.copyOf(os), readBlock, false, false);
        }
    }

    private static final class RunDraft {
        SetType.Lane lane;
        float startDepth;
        float startLateral;
        float targetDepth;
        float targetLateral;
        Tempo tempo;
        boolean decoy;

        RunDraft(Combination.Run r) {
            lane = r.lane();
            startDepth = r.startDepth();
            startLateral = r.startLateral();
            targetDepth = r.targetDepth();
            targetLateral = r.targetLateral();
            tempo = r.tempo();
            decoy = r.decoy();
        }

        RunDraft(SetType.Lane lane) {
            this.lane = lane;
            boolean back = lane == SetType.Lane.BACK_MIDDLE || lane == SetType.Lane.BACK_RIGHT;
            float lat = switch (lane) {
                case OUTSIDE -> -3.3f;
                case MIDDLE -> 0.3f;
                case RIGHT, BACK_RIGHT -> 3.2f;
                case BACK_MIDDLE -> 0f;
            };
            startDepth = back ? 6.2f : 3.8f;
            startLateral = lat;
            targetDepth = back ? 3.6f : 0.9f;
            targetLateral = lat;
            tempo = Tempo.THIRD;
        }
    }

    private static final class OptionDraft {
        SetType set;
        SetType.Lane hitter;

        OptionDraft(SetType set, SetType.Lane hitter) {
            this.set = set;
            this.hitter = hitter;
        }
    }

    private final VolleyballGame game;
    private final UiFonts fonts;
    private final PlaybookStore store = new PlaybookStore(ConfigStore.userDataDirectory());
    private Playbook book;
    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private int width;
    private int height;
    private float s;

    private int selectedPlay;
    private Draft draft;
    private int selectedRun = -1;
    private int selectedOption;
    /** Dragging: the run index and which end (true = the hitting spot). */
    private int dragRun = -1;
    private boolean dragTarget;
    private boolean renaming;
    private final StringBuilder nameInput = new StringBuilder();
    private String toast;
    private float toastTime;
    private boolean dirty;

    // Court rectangle on screen.
    private float courtX;
    private float courtY;
    private float courtW;
    private float courtH;

    public PlaybookScreen(VolleyballGame game) {
        this.game = game;
        this.fonts = game.fonts();
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        book = store.load();
        select(0);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(new EditorInput());
    }

    @Override
    public void resize(int width, int height) {
        // A fullscreen window can still be 0 x 0 at start-up: wait for its real size.
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        s = UiFonts.uiScale(height);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        courtH = height - 260f * s;
        courtW = courtH * (2f * HALF_WIDTH / DEPTH);
        courtX = (width - courtW) / 2f;
        courtY = 110f * s;
    }

    private List<Combination> plays() {
        return book.all();
    }

    private void select(int index) {
        commit();
        List<Combination> all = plays();
        selectedPlay = Math.max(0, Math.min(all.size() - 1, index));
        draft = Draft.of(all.get(selectedPlay));
        selectedRun = draft.runs.isEmpty() ? -1 : 0;
        selectedOption = 0;
        renaming = false;
    }

    /** Stores the draft (custom plays only) in the playbook. */
    private void commit() {
        if (draft == null || draft.builtIn || !dirty) return;
        book.putCustom(draft.toCombination());
        dirty = false;
    }

    private void save() {
        commit();
        show(store.saveQuietly(book) ? "Playbook saved" : "Could not save the playbook");
    }

    private void show(String message) {
        toast = message;
        toastTime = 2.5f;
    }

    // ------------------------------------------------------------------ editing

    private boolean editable() {
        if (draft.builtIn) {
            show("Built-in play: press C to copy it as your own");
            return false;
        }
        return true;
    }

    private void copyAsCustom() {
        commit();
        Draft copy = Draft.of(plays().get(selectedPlay));
        copy.builtIn = false;
        copy.id = freeId();
        copy.name = (copy.name + " (my)").length() > 40 ? copy.name : copy.name + " (my)";
        book.putCustom(copy.toCombination());
        selectById(copy.id);
        show("Copied: edit it, then Ctrl+S to save");
    }

    private void newPlay() {
        commit();
        Draft d = new Draft();
        d.id = freeId();
        d.name = "My play";
        d.description = "";
        d.minPass = PassRating.GOOD;
        d.runs.add(new RunDraft(SetType.Lane.OUTSIDE));
        d.options.add(new OptionDraft(SetType.HIGH_OUTSIDE, SetType.Lane.OUTSIDE));
        book.putCustom(d.toCombination());
        selectById(d.id);
        show("New play: drag the runs, choose the sets");
    }

    private void deletePlay() {
        if (!editable()) return;
        book.removeCustom(draft.id);
        draft = null;
        dirty = false;
        store.saveQuietly(book);
        select(Math.min(selectedPlay, plays().size() - 1));
        show("Play deleted");
    }

    private void selectById(String id) {
        List<Combination> all = plays();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                dirty = false;
                draft = null;
                select(i);
                return;
            }
        }
    }

    private String freeId() {
        for (int i = 1; ; i++) {
            String id = "custom-" + i;
            if (book.find(id) == null) return id;
        }
    }

    private void toggleRun(SetType.Lane lane) {
        if (!editable()) return;
        for (int i = 0; i < draft.runs.size(); i++) {
            if (draft.runs.get(i).lane == lane) {
                draft.runs.remove(i);
                selectedRun = draft.runs.isEmpty() ? -1 : 0;
                dirty = true;
                return;
            }
        }
        draft.runs.add(new RunDraft(lane));
        selectedRun = draft.runs.size() - 1;
        dirty = true;
    }

    private void handleKeys() {
        if (renaming) return;
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            commit();
            store.saveQuietly(book);
            game.showMainMenu();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) select(selectedPlay - 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) select(selectedPlay + 1);
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.S)) save();
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) copyAsCustom();
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) newPlay();
        if (Gdx.input.isKeyJustPressed(Input.Keys.FORWARD_DEL)) deletePlay();
        if (Gdx.input.isKeyJustPressed(Input.Keys.R) && editable()) {
            renaming = true;
            nameInput.setLength(0);
            nameInput.append(draft.name);
        }
        SetType.Lane[] lanes = SetType.Lane.values();
        for (int i = 0; i < lanes.length; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) toggleRun(lanes[i]);
        }
        RunDraft run = selectedRun >= 0 && selectedRun < draft.runs.size() ? draft.runs.get(selectedRun) : null;
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB) && !draft.runs.isEmpty()) selectedRun = (selectedRun + 1) % draft.runs.size();
        if (run != null && Gdx.input.isKeyJustPressed(Input.Keys.T) && editable()) {
            run.tempo = Tempo.values()[(run.tempo.ordinal() + 1) % Tempo.values().length];
            dirty = true;
        }
        if (run != null && Gdx.input.isKeyJustPressed(Input.Keys.D) && editable()) {
            run.decoy = !run.decoy;
            dirty = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.P) && editable()) {
            draft.minPass = PassRating.values()[(draft.minPass.ordinal() + 1) % PassRating.values().length];
            dirty = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.B) && editable()) {
            draft.readBlock = !draft.readBlock;
            dirty = true;
        }
        // Options: Page up / down selects, Q / E changes the set, W / S the hitter, Insert adds, Delete removes.
        if (Gdx.input.isKeyJustPressed(Input.Keys.PAGE_UP)) selectedOption = Math.max(0, selectedOption - 1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.PAGE_DOWN)) selectedOption = Math.min(draft.options.size() - 1, selectedOption + 1);
        OptionDraft option = selectedOption < draft.options.size() ? draft.options.get(selectedOption) : null;
        int setStep = (Gdx.input.isKeyJustPressed(Input.Keys.E) ? 1 : 0) - (Gdx.input.isKeyJustPressed(Input.Keys.Q) ? 1 : 0);
        if (option != null && setStep != 0 && editable()) {
            // The list of sets plus the setter's dump (null) at the end.
            SetType[] sets = SetType.values();
            int index = option.set == null ? sets.length : option.set.ordinal();
            index = Math.floorMod(index + setStep, sets.length + 1);
            option.set = index == sets.length ? null : sets[index];
            if (option.set != null) option.hitter = option.set.lane;
            dirty = true;
        }
        int hitterStep = (Gdx.input.isKeyJustPressed(Input.Keys.S) && !ctrl ? 1 : 0) - (Gdx.input.isKeyJustPressed(Input.Keys.W) ? 1 : 0);
        if (option != null && hitterStep != 0 && option.set != null && editable()) {
            option.hitter = lanes[Math.floorMod(option.hitter.ordinal() + hitterStep, lanes.length)];
            dirty = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.INSERT) && draft.options.size() < 4 && editable()) {
            draft.options.add(new OptionDraft(SetType.HIGH_OUTSIDE, SetType.Lane.OUTSIDE));
            selectedOption = draft.options.size() - 1;
            dirty = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DEL) && draft.options.size() > 1 && editable()) {
            draft.options.remove(selectedOption);
            selectedOption = Math.min(selectedOption, draft.options.size() - 1);
            dirty = true;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(float delta) {
        if (height <= 0) {
            resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            return;
        }
        handleKeys();
        if (draft == null) return;
        toastTime -= delta;
        ScreenUtils.clear(UiKit.NAVY);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        drawCourt();
        drawRuns();
        drawSidePanels();
    }

    private float sx(float lateral) {
        return courtX + (lateral + HALF_WIDTH) / (2f * HALF_WIDTH) * courtW;
    }

    private float sy(float depth) {
        // The net at the top of the drawing.
        return courtY + courtH - depth / DEPTH * courtH;
    }

    private float depthAt(float screenY) {
        return (courtY + courtH - screenY) / courtH * DEPTH;
    }

    private float lateralAt(float screenX) {
        return (screenX - courtX) / courtW * 2f * HALF_WIDTH - HALF_WIDTH;
    }

    private void drawCourt() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.86f, 0.55f, 0.28f, 1f);
        shapes.rect(courtX, courtY, courtW, courtH);
        shapes.setColor(0.95f, 0.95f, 0.95f, 1f);
        float line = 3f * s;
        shapes.rect(courtX, courtY, line, courtH);
        shapes.rect(courtX + courtW - line, courtY, line, courtH);
        shapes.rect(courtX, courtY, courtW, line);
        shapes.rect(courtX, sy(3f) - line / 2f, courtW, line);
        // The net.
        shapes.setColor(0.08f, 0.08f, 0.1f, 1f);
        shapes.rect(courtX - 12f * s, sy(0f) - 4f * s, courtW + 24f * s, 8f * s);
        // The setter's spot.
        shapes.setColor(1f, 1f, 1f, 0.8f);
        shapes.circle(sx(1f), sy(1f), 9f * s, 20);
        shapes.end();
        batch.begin();
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 12f * s);
        UiKit.text(batch, small, "NET", courtX + courtW / 2f - 14f * s, sy(0f) + 22f * s, UiKit.TEXT_DIM, 1f);
        UiKit.text(batch, small, "3 m line", courtX + 6f * s, sy(3f) - 6f * s, COURT_INK, 1f);
        UiKit.text(batch, small, "setter", sx(1f) + 12f * s, sy(1f) + 5f * s, COURT_INK, 1f);
        batch.end();
    }

    private void drawRuns() {
        // SpriteBatch.end() switches blending off: on again for the translucent shapes.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // The plan's set spot.
        if (!draft.options.isEmpty() && draft.options.get(0).set != null) {
            SetType set = draft.options.get(0).set;
            shapes.setColor(1f, 1f, 1f, 0.9f);
            float x = sx(set.lateral);
            float y = sy(set.depth);
            shapes.rectLine(x - 10f * s, y - 10f * s, x + 10f * s, y + 10f * s, 3f * s);
            shapes.rectLine(x - 10f * s, y + 10f * s, x + 10f * s, y - 10f * s, 3f * s);
        }
        for (int i = 0; i < draft.runs.size(); i++) {
            RunDraft r = draft.runs.get(i);
            Color c = LANE_COLORS[r.lane.ordinal()];
            float x0 = sx(r.startLateral);
            float y0 = sy(r.startDepth);
            float x1 = sx(r.targetLateral);
            float y1 = sy(r.targetDepth);
            shapes.setColor(c.r, c.g, c.b, i == selectedRun ? 1f : 0.8f);
            // Decoys dashed.
            int dashes = r.decoy ? 9 : 1;
            for (int k = 0; k < dashes; k++) {
                float a = k / (float) dashes;
                float b = r.decoy ? (k + 0.55f) / dashes : 1f;
                shapes.rectLine(x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, x0 + (x1 - x0) * b, y0 + (y1 - y0) * b, 4f * s);
            }
            // Arrow head at the hitting spot.
            float dx = x1 - x0;
            float dy = y1 - y0;
            float len = (float) Math.max(1e-3, Math.hypot(dx, dy));
            float ux = dx / len;
            float uy = dy / len;
            shapes.triangle(x1, y1, x1 - ux * 16f * s - uy * 8f * s, y1 - uy * 16f * s + ux * 8f * s,
                    x1 - ux * 16f * s + uy * 8f * s, y1 - uy * 16f * s - ux * 8f * s);
            shapes.circle(x0, y0, (i == selectedRun ? 13f : 11f) * s, 24);
            shapes.setColor(UiKit.NAVY);
            shapes.circle(x0, y0, 6f * s, 16);
        }
        shapes.end();
        batch.begin();
        BitmapFont small = fonts.get(UiFonts.Weight.BOLD, 12f * s);
        for (RunDraft r : draft.runs) {
            String tag = shortLane(r.lane) + " · " + tempoShort(r.tempo) + (r.decoy ? " · decoy" : "");
            UiKit.text(batch, small, tag, sx(r.startLateral) + 15f * s, sy(r.startDepth) - 6f * s, UiKit.TEXT, 1f);
        }
        batch.end();
    }

    private void drawSidePanels() {
        float panelW = Math.min(420f * s, courtX - 50f * s);
        BitmapFont title = fonts.get(UiFonts.Weight.EXTRABOLD, 30f * s);
        BitmapFont head = fonts.get(UiFonts.Weight.EXTRABOLD, 15f * s);
        BitmapFont body = fonts.get(UiFonts.Weight.MEDIUM, 14f * s);
        BitmapFont item = fonts.get(UiFonts.Weight.SEMIBOLD, 14f * s);
        List<Combination> all = plays();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, 25f * s, courtY, panelW, courtH, 0.9f, 0f);
        float rightX = courtX + courtW + 25f * s;
        float rightW = Math.min(460f * s, width - rightX - 25f * s);
        UiKit.panel(shapes, rightX, courtY, rightW, courtH, 0.9f, 0f);
        float rowH = 26f * s;
        float listTop = courtY + courtH - 60f * s;
        shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.18f);
        shapes.rect(30f * s, listTop - (selectedPlay + 1) * rowH + 4f * s, panelW - 10f * s, rowH);
        shapes.end();

        batch.begin();
        UiKit.text(batch, title, "PLAYBOOK", courtX, height - 40f * s, UiKit.TEXT, 1f);
        UiKit.text(batch, body, "Attacking combinations: the computer teams call them in matches (custom plays included).",
                courtX, height - 86f * s, UiKit.TEXT_DIM, 1f);
        UiKit.text(batch, head, "PLAYS  (Up / Down)", 40f * s, courtY + courtH - 24f * s, UiKit.JADE, 1f);
        for (int i = 0; i < all.size(); i++) {
            Combination c = all.get(i);
            float y = listTop - i * rowH;
            UiKit.text(batch, item, (c.builtIn() ? "" : "• ") + c.name(), 44f * s, y, i == selectedPlay ? UiKit.JADE : UiKit.TEXT, 1f);
        }

        float x = rightX + 18f * s;
        float y = courtY + courtH - 24f * s;
        float textW = rightW - 36f * s;
        String name = renaming ? nameInput + "_" : draft.name;
        UiKit.text(batch, head, name.toUpperCase(Locale.ROOT) + (draft.builtIn ? "   (built-in)" : "   (custom)"), x, y,
                renaming ? UiKit.TEXT : UiKit.JADE, 1f);
        y -= 26f * s;
        if (!draft.description.isEmpty()) {
            float h = UiKit.text(batch, body, draft.description, x, y, textW, Align.left, true, UiKit.TEXT_DIM, 1f);
            y -= h + 14f * s;
        }
        UiKit.text(batch, item, "Pass needed (P):  " + draft.minPass.label + " or better", x, y, UiKit.TEXT, 1f);
        y -= rowH;
        UiKit.text(batch, item, "Reads the block (B):  " + (draft.readBlock ? "yes - the set goes where the block is not" : "no"), x, y, UiKit.TEXT, 1f);
        y -= rowH * 1.4f;
        UiKit.text(batch, head, "RUNS  (1-5 add / remove, Tab select)", x, y, UiKit.JADE, 1f);
        y -= rowH;
        for (int i = 0; i < draft.runs.size(); i++) {
            RunDraft r = draft.runs.get(i);
            String line = String.format(Locale.ROOT, "%s  ·  %s  ·  %s", laneName(r.lane), r.tempo.label, r.decoy ? "decoy" : "hits");
            UiKit.text(batch, item, line, x, y, i == selectedRun ? LANE_COLORS[r.lane.ordinal()] : UiKit.TEXT, 1f);
            y -= rowH;
        }
        float hintH = UiKit.text(batch, body, "T tempo  ·  D decoy  ·  drag the circle (start) or the arrow (hitting spot)", x, y,
                textW, Align.left, true, UiKit.TEXT_DIM, 1f);
        y -= hintH + rowH * 0.8f;
        UiKit.text(batch, head, "SETS", x, y, UiKit.JADE, 1f);
        y -= rowH * 0.8f;
        BitmapFont tiny = fonts.get(UiFonts.Weight.MEDIUM, 12f * s);
        UiKit.text(batch, tiny, "PgUp / PgDn select  ·  Q / E set  ·  W / S hitter  ·  Ins add  ·  Del remove", x, y, UiKit.TEXT_DIM, 1f);
        y -= rowH;
        for (int i = 0; i < draft.options.size(); i++) {
            OptionDraft o = draft.options.get(i);
            String label = (i == 0 ? "Plan:  " : "Then:  ") + (o.set == null ? "Setter dump" : o.set.label + "  ->  " + laneName(o.hitter));
            UiKit.text(batch, item, label, x, y, i == selectedOption ? UiKit.JADE : UiKit.TEXT, 1f);
            y -= rowH;
        }
        y -= rowH * 0.4f;
        UiKit.text(batch, body, "If the pass is worse than needed, the setter is late or nobody is ready: "
                + "the next option, or the emergency high ball.", x, y, textW, Align.left, true, UiKit.TEXT_DIM, 1f);

        BitmapFont hints = fonts.get(UiFonts.Weight.SEMIBOLD, 13f * s);
        UiKit.text(batch, hints, "N new  ·  C copy as custom  ·  R rename  ·  Del delete play  ·  Ctrl+S save  ·  Esc back (saves)",
                0f, 52f * s, width, Align.center, false, UiKit.TEXT_DIM, 1f);
        if (toastTime > 0f && toast != null) {
            UiKit.text(batch, head, toast, 0f, 82f * s, width, Align.center, false, UiKit.TEXT, Math.min(1f, toastTime));
        }
        batch.end();
    }

    private static String laneName(SetType.Lane lane) {
        return switch (lane) {
            case OUTSIDE -> "Outside (zone 4)";
            case MIDDLE -> "Middle (zone 3)";
            case RIGHT -> "Right side (zone 2)";
            case BACK_MIDDLE -> "Back row (zone 6)";
            case BACK_RIGHT -> "Back row (zone 1)";
        };
    }

    private static String shortLane(SetType.Lane lane) {
        return switch (lane) {
            case OUTSIDE -> "OH";
            case MIDDLE -> "MB";
            case RIGHT -> "RS";
            case BACK_MIDDLE -> "BR6";
            case BACK_RIGHT -> "BR1";
        };
    }

    private static String tempoShort(Tempo tempo) {
        return switch (tempo) {
            case FIRST -> "1st tempo";
            case SECOND -> "2nd tempo";
            case THIRD -> "3rd tempo";
        };
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
    }

    /** Mouse: pick and drag the ends of the runs; keyboard text for renaming. */
    private final class EditorInput extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (draft == null) return false;
            float x = screenX;
            float y = height - screenY;
            float best = 22f * s;
            dragRun = -1;
            for (int i = 0; i < draft.runs.size(); i++) {
                RunDraft r = draft.runs.get(i);
                float ds = (float) Math.hypot(x - sx(r.startLateral), y - sy(r.startDepth));
                float dt = (float) Math.hypot(x - sx(r.targetLateral), y - sy(r.targetDepth));
                if (ds < best) {
                    best = ds;
                    dragRun = i;
                    dragTarget = false;
                }
                if (dt < best) {
                    best = dt;
                    dragRun = i;
                    dragTarget = true;
                }
            }
            if (dragRun >= 0) {
                selectedRun = dragRun;
                if (draft.builtIn) {
                    editable();
                    dragRun = -1;
                }
                return true;
            }
            // Clicking the list selects a play.
            float listTop = courtY + courtH - 60f * s;
            float rowH = 26f * s;
            float panelW = Math.min(420f * s, courtX - 50f * s);
            if (x > 25f * s && x < 25f * s + panelW && y < listTop + 4f * s) {
                int row = (int) ((listTop + 4f * s - y) / rowH);
                if (row >= 0 && row < plays().size()) select(row);
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (dragRun < 0 || draft == null) return false;
            RunDraft r = draft.runs.get(dragRun);
            float depth = Math.max(0.5f, Math.min(8.5f, depthAt(height - screenY)));
            float lateral = Math.max(-4.3f, Math.min(4.3f, lateralAt(screenX)));
            boolean back = r.lane == SetType.Lane.BACK_MIDDLE || r.lane == SetType.Lane.BACK_RIGHT;
            // Back-row hitters must take off behind the 3 m line.
            if (back && dragTarget) depth = Math.max(3.3f, depth);
            if (dragTarget) {
                r.targetDepth = depth;
                r.targetLateral = lateral;
            } else {
                r.startDepth = depth;
                r.startLateral = lateral;
            }
            dirty = true;
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            dragRun = -1;
            return false;
        }

        @Override
        public boolean keyTyped(char character) {
            if (!renaming) return false;
            if (character == '\r' || character == '\n') {
                String name = nameInput.toString().strip();
                if (!name.isEmpty()) {
                    draft.name = name.length() > 40 ? name.substring(0, 40) : name;
                    dirty = true;
                }
                renaming = false;
            } else if (character == 27) {
                renaming = false;
            } else if (character == '\b') {
                if (nameInput.length() > 0) nameInput.setLength(nameInput.length() - 1);
            } else if (character >= 32 && nameInput.length() < 40) {
                nameInput.append(character);
            }
            return true;
        }
    }
}
