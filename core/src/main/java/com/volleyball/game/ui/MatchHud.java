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
 * The match HUD of the person's player: a card in the top-left corner (number, name and role,
 * stamina, the team's touches, the rally time, the server, the own rotation and the team
 * commands) and a row of key hints along the bottom-left edge.
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
        /** A short line under the card (e.g. the last point), or null. */
        public String footer;
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

    /** The card's lower edge (to place other panels under it). */
    public float cardBottom() {
        return cardBottom;
    }

    public void render(Model m, boolean showHints) {
        float s = UiFonts.uiScale(height);
        BitmapFont modeFont = fonts.get(UiFonts.Weight.EXTRABOLD, 11.5f * s);
        BitmapFont nameFont = fonts.get(UiFonts.Weight.EXTRABOLD, 19f * s);
        BitmapFont roleFont = fonts.get(UiFonts.Weight.SEMIBOLD, 12.5f * s);
        BitmapFont numberFont = fonts.get(UiFonts.Weight.BLACK, 22f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.BOLD, 11.5f * s);
        BitmapFont chipFont = fonts.get(UiFonts.Weight.EXTRABOLD, 13f * s);
        BitmapFont zoneFont = fonts.get(UiFonts.Weight.EXTRABOLD, 12f * s);

        float pad = 16f * s;
        float cardW = 330f * s;
        boolean tactics = m.offense != null || m.defense != null || m.serve != null;
        float cardH = 290f * s + (tactics ? 22f * s * countTactics(m) + 10f * s : 0f);
        float x = 18f * s;
        float top = height - 18f * s;
        float y0 = top - cardH;
        cardBottom = y0;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, x, y0, cardW, cardH, 0.9f, 3f * s);
        // Number badge in the team colour.
        float badge = 46f * s;
        float by = top - 30f * s - badge;
        Color dark = new Color(m.teamColor).mul(0.55f, 0.55f, 0.55f, 1f);
        UiKit.roundedRect(shapes, x + pad, by, badge, badge, 8f * s, dark, m.teamColor);
        // Stamina.
        float barY = by - 54f * s;
        if (!Float.isNaN(m.stamina)) {
            UiKit.bar(shapes, x + pad, barY, cardW - 2f * pad, 8f * s, m.stamina, staminaColor(m.stamina), 1f);
        }
        // Chips: touches, rally time, server.
        float chipY = barY - 58f * s;
        float chipH = 30f * s;
        float chipW = (cardW - 2f * pad - 2f * 8f * s) / 3f;
        for (int i = 0; i < 3; i++) {
            Color c = new Color(1f, 1f, 1f, 0.06f);
            UiKit.roundedRect(shapes, x + pad + i * (chipW + 8f * s), chipY, chipW, chipH, 6f * s, c, new Color(1f, 1f, 1f, 0.1f));
        }
        // Rotation: front row 4 3 2 on top, back row 5 6 1 below.
        float cell = 26f * s;
        float gridX = x + pad;
        float gridY = chipY - 18f * s - 2f * cell - 4f * s;
        int[][] layout = {{4, 3, 2}, {5, 6, 1}};
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int zone = layout[row][col];
                float cx = gridX + col * (cell + 4f * s);
                float cy = gridY + (1 - row) * (cell + 4f * s);
                boolean you = zone == m.yourZone;
                Color fill = you ? new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.85f) : new Color(1f, 1f, 1f, 0.08f);
                UiKit.roundedRect(shapes, cx, cy, cell, cell, 4f * s, fill, fill);
                if (zone == m.serverZone) {
                    shapes.setColor(UiKit.MINT);
                    shapes.circle(cx + cell - 4f * s, cy + cell - 4f * s, 3f * s, 10);
                }
            }
        }
        // The net above the grid.
        shapes.setColor(1f, 1f, 1f, 0.35f);
        shapes.rect(gridX - 3f * s, gridY + 2f * cell + 8f * s, 3f * cell + 14f * s, 2f * s);
        shapes.end();

        batch.begin();
        UiKit.text(batch, modeFont, m.mode.toUpperCase(java.util.Locale.ROOT), x + pad, top - 12f * s, UiKit.JADE, 1f);
        UiKit.text(batch, numberFont, String.valueOf(m.number), x + pad, by + badge / 2f + numberFont.getCapHeight() / 2f, badge,
                Align.center, false, UiKit.TEXT, 1f);
        float tx = x + pad + badge + 12f * s;
        UiKit.text(batch, nameFont, m.name, tx, by + badge - 6f * s, UiKit.TEXT, 1f);
        UiKit.text(batch, roleFont, m.role.toUpperCase(java.util.Locale.ROOT) + (m.youServe ? "   ·   YOUR SERVE" : ""), tx, by + 16f * s,
                m.youServe ? UiKit.MINT : UiKit.TEXT_DIM, 1f);
        if (!m.traits.isEmpty()) {
            UiKit.text(batch, roleFont, m.traits, x + pad, by - 12f * s, cardW - 2f * pad, Align.left, false, UiKit.JADE, 1f);
        }
        if (!Float.isNaN(m.stamina)) {
            UiKit.text(batch, small, "STAMINA", x + pad, barY + 22f * s, UiKit.TEXT_DIM, 1f);
            String pct = Math.round(m.stamina * 100f) + "%";
            UiKit.text(batch, small, pct, x + cardW - pad - UiKit.width(small, pct), barY + 22f * s, staminaColor(m.stamina), 1f);
        } else {
            UiKit.text(batch, small, "FATIGUE OFF (ARCADE)", x + pad, barY + 18f * s, UiKit.TEXT_FAINT, 1f);
        }
        String[] chipLabels = {"TOUCHES", "RALLY", "SERVE"};
        String rally = m.rallyTime < 0f ? "-" : String.format(java.util.Locale.ROOT, "%d:%02d", (int) m.rallyTime / 60, (int) m.rallyTime % 60);
        String[] chipValues = {m.touches + " / 3", rally, m.server};
        for (int i = 0; i < 3; i++) {
            float cx = x + pad + i * (chipW + 8f * s);
            UiKit.text(batch, small, chipLabels[i], cx + 8f * s, chipY + chipH + 14f * s, UiKit.TEXT_FAINT, 1f);
            UiKit.text(batch, chipFont, chipValues[i], cx, chipY + chipH / 2f + chipFont.getCapHeight() / 2f, chipW, Align.center,
                    false, i == 0 && m.touches == 3 ? UiKit.RED : UiKit.TEXT, 1f);
        }
        UiKit.text(batch, small, "ROTATION", gridX + 3f * cell + 22f * s, gridY + 2f * cell, UiKit.TEXT_FAINT, 1f);
        UiKit.text(batch, roleFont, m.timeoutsLeft >= 0 ? "Timeouts left: " + m.timeoutsLeft : "", gridX + 3f * cell + 22f * s,
                gridY + cell + 6f * s, UiKit.TEXT_DIM, 1f);
        UiKit.text(batch, roleFont, "Net at the top", gridX + 3f * cell + 22f * s, gridY + 10f * s, UiKit.TEXT_FAINT, 1f);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int zone = layout[row][col];
                int number = m.rotation[zone - 1];
                if (number <= 0) continue;
                float cx = gridX + col * (cell + 4f * s);
                float cy = gridY + (1 - row) * (cell + 4f * s);
                UiKit.text(batch, zoneFont, String.valueOf(number), cx, cy + cell / 2f + zoneFont.getCapHeight() / 2f, cell,
                        Align.center, false, zone == m.yourZone ? UiKit.NAVY : UiKit.TEXT, 1f);
            }
        }
        if (tactics) {
            float ty = gridY - 16f * s;
            for (String[] t : new String[][]{{"OFFENSE", m.offense}, {"DEFENSE", m.defense}, {"SERVE", m.serve}}) {
                if (t[1] == null) continue;
                UiKit.text(batch, small, t[0], x + pad, ty, UiKit.TEXT_FAINT, 1f);
                UiKit.text(batch, roleFont, t[1], x + pad + 70f * s, ty, UiKit.JADE, 1f);
                ty -= 22f * s;
            }
        }
        batch.end();

        if (m.footer != null) drawFooter(m.footer, x, y0 - 10f * s, cardW, s);
        if (showHints) {
            hintsTime += Gdx.graphics.getDeltaTime();
            // At first the full hints, fading into the chip; later the chip unless J opened them.
            float full = hintsExpanded ? 1f : 1f - UiKit.ease((hintsTime - HINTS_INTRO + 0.6f) / 0.6f);
            if (full > 0.01f) drawHints(s, full);
            if (full < 0.99f) drawHintsChip(s, 1f - full);
        }
    }

    private static int countTactics(Model m) {
        return (m.offense != null ? 1 : 0) + (m.defense != null ? 1 : 0) + (m.serve != null ? 1 : 0);
    }

    private void drawFooter(String text, float x, float top, float w, float s) {
        BitmapFont f = fonts.get(UiFonts.Weight.MEDIUM, 12.5f * s);
        float h = UiKit.wrappedHeight(f, text, w - 24f * s) + 18f * s;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, x, top - h, w, h, 0.7f, 0f);
        shapes.end();
        batch.begin();
        UiKit.text(batch, f, text, x + 12f * s, top - 9f * s, w - 24f * s, Align.left, true, UiKit.TEXT_DIM, 1f);
        batch.end();
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
        if (stamina > 0.35f) return UiKit.JADE;
        return UiKit.RED;
    }

    /** First-person mouse look: the aim in the centre of the view (gold over the court, pale elsewhere). */
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
