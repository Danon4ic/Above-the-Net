package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * The match HUD of the person's player: a compact card in the top-left corner (number, name and
 * role, stamina, the team's touches, the rally time, the server, the own rotation and the team
 * commands) and the key hints in the bottom-left corner. Both stay at the edges, off the court.
 */
public final class MatchHud implements Disposable {
    /** What the card shows (filled by the match screen every frame). */
    public static final class Model {
        public String mode = "";
        public int number;
        public String name = "";
        public String role = "";
        /** The player's traits, e.g. "Cannon · Clutch". */
        public String traits = "";
        public final Color teamColor = new Color(Color.WHITE);
        /** 0..1, or NaN when fatigue is off. */
        public float stamina = Float.NaN;
        public int touches;
        /** Seconds since the serve, or a negative number between rallies. */
        public float rallyTime = -1f;
        public String server = "";
        public boolean youServe;
        /** Shirt numbers in zones 1..6 of the own team (index 0 = zone 1); 0 = empty. */
        public final int[] rotation = new int[6];
        /** Zone (1..6) of the person's player and of the server (0: none). */
        public int yourZone;
        public int serverZone;
        /** Active team commands per group, null = the team decides. */
        public String offense;
        public String defense;
        public String serve;
        public int timeoutsLeft = -1;
    }

    private static final String[][] HINTS = {
            {"F1", "Help"}, {"Esc", "Pause"}, {"K", "Stats"}, {"U", "Timeout"}, {"G", "Tactics"}, {"B", "Bench"},
            {"V", "Replay"}, {"C", "Camera"}, {"H", "Details"}, {"I", "AI view"}};

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private int width;
    private int height;
    private float cardBottom;
    /** The full key hints are shown (J); otherwise only a small "J Keys" chip, after the first seconds. */
    private boolean hintsExpanded;
    /** Seconds the hints have been on screen since the match screen opened (they fold away after a while). */
    private float hintsTime;
    private static final float HINTS_INTRO = 8f;
    // The bottom-left corner the key hints took this frame (0 when none), for the bars beside them.
    private float hintsRight;
    private float hintsTop;

    public MatchHud(UiFonts fonts) {
        this.fonts = fonts;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    /** The key hints start folded (the "J Keys" chip only), as chosen in the settings. */
    public void startFolded() {
        hintsExpanded = false;
        hintsTime = HINTS_INTRO;
    }

    /** J: shows or hides the full key hints. */
    public void toggleHints() {
        hintsExpanded = !hintsExpanded;
        hintsTime = HINTS_INTRO;
    }

    /** Right edge of the key hints in the bottom-left corner this frame (0: none shown). */
    public float hintsRight() {
        return hintsRight;
    }

    /** Top edge of the key hints in the bottom-left corner this frame (0: none shown). */
    public float hintsTop() {
        return hintsTop;
    }

    /** The card's lower edge (to place other panels under it). */
    public float cardBottom() {
        return cardBottom;
    }

    public void render(Model m, boolean showHints) {
        float s = UiFonts.uiScale(height);
        BitmapFont modeFont = fonts.get(UiFonts.Weight.SEMIBOLD, 10.5f * s);
        BitmapFont nameFont = fonts.get(UiFonts.Weight.EXTRABOLD, 15f * s);
        BitmapFont roleFont = fonts.get(UiFonts.Weight.SEMIBOLD, 11f * s);
        BitmapFont numberFont = fonts.get(UiFonts.Weight.BLACK, 16f * s);
        BitmapFont label = fonts.get(UiFonts.Weight.BOLD, 10f * s);
        BitmapFont value = fonts.get(UiFonts.Weight.EXTRABOLD, 13f * s);
        BitmapFont zoneFont = fonts.get(UiFonts.Weight.EXTRABOLD, 10.5f * s);

        // Layout, top down: mode line, number + name + role, traits, stamina, the three values,
        // the rotation and the team commands. Compact, so the card keeps clear of the court.
        float pad = 12f * s;
        float cardW = 252f * s;
        float x = 16f * s;
        float top = height - 16f * s;
        float cy = top - pad;
        float modeY = cy;
        cy -= 18f * s;
        float badge = 32f * s;
        float by = cy - badge;
        cy = by - 12f * s;
        float traitsY = cy;
        if (!m.traits.isEmpty()) cy -= 18f * s;
        float staminaY = cy;
        float barY = cy - 18f * s;
        cy = barY - 12f * s;
        float statsY = cy;
        cy -= 34f * s;
        float cell = 18f * s;
        float gap = 3f * s;
        float netY = cy - 2f * s;
        float gridY = netY - 5f * s - 2f * cell - gap;
        cy = gridY - 12f * s;
        float tacticsY = cy;
        cy -= 18f * s * countTactics(m);
        float y0 = cy;
        cardBottom = y0;
        float colW = (cardW - 2f * pad) / 3f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, x, y0, cardW, top - y0, 0.92f, 0f);
        // Number badge: a flat square in the team colour.
        UiKit.roundedRect(shapes, x + pad, by, badge, badge, 3f * s, m.teamColor);
        if (!Float.isNaN(m.stamina)) {
            UiKit.bar(shapes, x + pad, barY, cardW - 2f * pad, 4f * s, m.stamina, staminaColor(m.stamina), 1f);
        }
        // Rotation: front row 4 3 2 on top (under the net line), back row 5 6 1 below.
        shapes.setColor(UiKit.TEXT_FAINT);
        shapes.rect(x + pad, netY, 3f * cell + 2f * gap, Math.max(1f, s));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int zone = ROTATION_LAYOUT[row][col];
                float zx = x + pad + col * (cell + gap);
                float zy = gridY + (1 - row) * (cell + gap);
                UiKit.roundedRect(shapes, zx, zy, cell, cell, 2f * s, zone == m.yourZone ? UiKit.PRIMARY : UiKit.DEEP);
                if (zone == m.serverZone) {
                    // The server: a bright bar along the bottom of the cell.
                    shapes.setColor(UiKit.TEXT);
                    shapes.rect(zx + 3f * s, zy + 1.5f * s, cell - 6f * s, Math.max(1f, 1.5f * s));
                }
            }
        }
        shapes.end();

        batch.begin();
        UiKit.text(batch, modeFont, m.mode, x + pad, modeY, cardW - 2f * pad, Align.left, false, UiKit.TEXT_FAINT, 1f);
        UiKit.text(batch, numberFont, String.valueOf(m.number), x + pad, by + badge / 2f + numberFont.getCapHeight() / 2f, badge,
                Align.center, false, isLight(m.teamColor) ? UiKit.NAVY : UiKit.TEXT, 1f);
        float tx = x + pad + badge + 10f * s;
        UiKit.text(batch, nameFont, m.name, tx, by + badge - 1f * s, cardW - pad - (tx - x), Align.left, false, UiKit.TEXT, 1f);
        UiKit.text(batch, roleFont, m.role.toUpperCase(java.util.Locale.ROOT) + (m.youServe ? "  ·  YOUR SERVE" : ""), tx,
                by + roleFont.getCapHeight() + 1f * s, m.youServe ? UiKit.JADE : UiKit.TEXT_DIM, 1f);
        if (!m.traits.isEmpty()) {
            UiKit.text(batch, roleFont, m.traits, x + pad, traitsY, cardW - 2f * pad, Align.left, false, UiKit.TEXT_DIM, 1f);
        }
        if (!Float.isNaN(m.stamina)) {
            UiKit.text(batch, label, "STAMINA", x + pad, staminaY, UiKit.TEXT_FAINT, 1f);
            String pct = Math.round(m.stamina * 100f) + "%";
            UiKit.text(batch, label, pct, x + cardW - pad - UiKit.width(label, pct), staminaY, staminaColor(m.stamina), 1f);
        } else {
            UiKit.text(batch, label, "FATIGUE OFF (ARCADE)", x + pad, staminaY, UiKit.TEXT_FAINT, 1f);
        }
        String rally = m.rallyTime < 0f ? "-" : String.format(java.util.Locale.ROOT, "%d:%02d", (int) m.rallyTime / 60, (int) m.rallyTime % 60);
        String[] values = {m.touches + " / 3", rally, m.server};
        for (int i = 0; i < 3; i++) {
            float cx = x + pad + i * colW;
            UiKit.text(batch, label, STAT_LABELS[i], cx, statsY, UiKit.TEXT_FAINT, 1f);
            UiKit.text(batch, value, values[i], cx, statsY - 15f * s, i == 0 && m.touches == 3 ? UiKit.RED : UiKit.TEXT, 1f);
        }
        float infoX = x + pad + 3f * cell + 2f * gap + 14f * s;
        UiKit.text(batch, label, "ROTATION", infoX, netY + 1f * s, UiKit.TEXT_FAINT, 1f);
        if (m.timeoutsLeft >= 0) {
            UiKit.text(batch, roleFont, "Timeouts left: " + m.timeoutsLeft, infoX, netY - 16f * s, UiKit.TEXT_DIM, 1f);
        }
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int zone = ROTATION_LAYOUT[row][col];
                int number = m.rotation[zone - 1];
                if (number <= 0) continue;
                float zx = x + pad + col * (cell + gap);
                float zy = gridY + (1 - row) * (cell + gap);
                UiKit.text(batch, zoneFont, String.valueOf(number), zx, zy + cell / 2f + zoneFont.getCapHeight() / 2f, cell,
                        Align.center, false, UiKit.TEXT, 1f);
            }
        }
        float ty = tacticsY;
        for (int i = 0; i < 3; i++) {
            String command = i == 0 ? m.offense : i == 1 ? m.defense : m.serve;
            if (command == null) continue;
            UiKit.text(batch, label, TACTIC_LABELS[i], x + pad, ty, UiKit.TEXT_FAINT, 1f);
            UiKit.text(batch, roleFont, command, x + pad + 70f * s, ty, UiKit.JADE, 1f);
            ty -= 18f * s;
        }
        batch.end();

        hintsRight = 0f;
        hintsTop = 0f;
        if (showHints) {
            hintsTime += Gdx.graphics.getDeltaTime();
            // At first the full hints, fading into the chip; later the chip unless J opened them.
            float full = hintsExpanded ? 1f : 1f - UiKit.ease((hintsTime - HINTS_INTRO + 0.6f) / 0.6f);
            if (full > 0.01f) drawHints(s, full);
            if (full < 0.99f) drawHintsChip(s, 1f - full);
        }
    }

    private static final int[][] ROTATION_LAYOUT = {{4, 3, 2}, {5, 6, 1}};
    private static final String[] STAT_LABELS = {"TOUCHES", "RALLY", "SERVE"};
    private static final String[] TACTIC_LABELS = {"OFFENSE", "DEFENSE", "SERVE"};

    private static int countTactics(Model m) {
        return (m.offense != null ? 1 : 0) + (m.defense != null ? 1 : 0) + (m.serve != null ? 1 : 0);
    }

    /** A light team colour takes a dark number on the badge. */
    private static boolean isLight(Color c) {
        return 0.299f * c.r + 0.587f * c.g + 0.114f * c.b > 0.6f;
    }

    /** Key hints in the bottom-left corner: two short rows on a soft panel (clear of the shot bar in the middle). */
    private void drawHints(float s, float alpha) {
        BitmapFont key = fonts.get(UiFonts.Weight.EXTRABOLD, 11f * s);
        BitmapFont label = fonts.get(UiFonts.Weight.SEMIBOLD, 12f * s);
        float h = 20f * s;
        int perRow = (HINTS.length + 1) / 2;
        float rowGap = 28f * s;
        float x0 = 18f * s;
        float baseY = 16f * s;
        List<float[]> caps = new ArrayList<>();
        float maxX = 0f;
        for (int i = 0; i < HINTS.length; i++) {
            int row = i / perRow;
            float x = i % perRow == 0 ? x0 + 12f * s : caps.get(i - 1)[0] + caps.get(i - 1)[1] + 6f * s
                    + UiKit.width(label, HINTS[i - 1][1]) + 14f * s;
            float w = UiKit.keycapWidth(key, HINTS[i][0]);
            caps.add(new float[]{x, w, baseY + (1 - row) * rowGap + 10f * s});
            maxX = Math.max(maxX, x + w + 6f * s + UiKit.width(label, HINTS[i][1]));
        }
        if (maxX > width * 0.36f) return; // too narrow: the help (F1) lists them all
        hintsRight = Math.max(hintsRight, maxX + 12f * s);
        hintsTop = Math.max(hintsTop, baseY + 2f * rowGap + 12f * s);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, x0, baseY, maxX - x0 + 12f * s, 2f * rowGap + 12f * s, 0.55f * alpha, 0f);
        for (float[] c : caps) UiKit.keycap(shapes, c[0], c[2], c[1], h, false, 0.95f * alpha);
        shapes.end();
        batch.begin();
        for (int i = 0; i < HINTS.length; i++) {
            float[] c = caps.get(i);
            UiKit.keycapLabel(batch, key, HINTS[i][0], c[0], c[2], h, false, alpha);
            UiKit.text(batch, label, HINTS[i][1], c[0] + c[1] + 6f * s, c[2] + h / 2f + label.getCapHeight() / 2f, UiKit.TEXT,
                    0.92f * alpha);
        }
        batch.end();
    }

    /** The folded hints: two small key caps in the corner, "J Keys" and "F1 Help". */
    private void drawHintsChip(float s, float alpha) {
        BitmapFont key = fonts.get(UiFonts.Weight.EXTRABOLD, 11f * s);
        BitmapFont label = fonts.get(UiFonts.Weight.SEMIBOLD, 12f * s);
        String[][] chip = {{"J", "Keys"}, {"F1", "Help"}};
        float h = 20f * s;
        float x = 18f * s;
        float y = 16f * s;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float cx = x;
        for (String[] c : chip) {
            float w = UiKit.keycapWidth(key, c[0]);
            UiKit.keycap(shapes, cx, y, w, h, false, 0.8f * alpha);
            cx += w + 6f * s + UiKit.width(label, c[1]) + 14f * s;
        }
        hintsRight = Math.max(hintsRight, cx);
        hintsTop = Math.max(hintsTop, y + h);
        shapes.end();
        batch.begin();
        cx = x;
        for (String[] c : chip) {
            float w = UiKit.keycapLabel(batch, key, c[0], cx, y, h, false, alpha);
            UiKit.text(batch, label, c[1], cx + w + 6f * s, y + h / 2f + label.getCapHeight() / 2f, UiKit.TEXT_DIM, alpha);
            cx += w + 6f * s + UiKit.width(label, c[1]) + 14f * s;
        }
        batch.end();
    }

    private static Color staminaColor(float stamina) {
        if (stamina > 0.6f) return UiKit.GREEN;
        if (stamina > 0.35f) return UiKit.TEXT_DIM;
        return UiKit.RED;
    }

    /** First-person mouse look: the aim in the centre of the view (blue over the court, grey elsewhere). */
    public void renderCrosshair(boolean onCourt) {
        float s = UiFonts.uiScale(height);
        float cx = width / 2f;
        float cy = height / 2f;
        float gap = 5f * s;
        float len = 8f * s;
        float t = 2f * s;
        float o = 1f * s;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // A dark outline first, so the cross reads on the bright floor and on the dark stands.
        shapes.setColor(0f, 0f, 0f, 0.5f);
        crossArms(cx, cy, gap - o, len + 2f * o, t + 2f * o);
        shapes.circle(cx, cy, 1.8f * s + o, 12);
        Color c = onCourt ? UiKit.ACCENT : UiKit.TEXT_DIM;
        shapes.setColor(c.r, c.g, c.b, 0.95f);
        crossArms(cx, cy, gap, len, t);
        shapes.circle(cx, cy, 1.8f * s, 12);
        shapes.end();
    }

    private void crossArms(float cx, float cy, float gap, float len, float t) {
        shapes.rect(cx + gap, cy - t / 2f, len, t);
        shapes.rect(cx - gap - len, cy - t / 2f, len, t);
        shapes.rect(cx - t / 2f, cy + gap, t, len);
        shapes.rect(cx - t / 2f, cy - gap - len, t, len);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
