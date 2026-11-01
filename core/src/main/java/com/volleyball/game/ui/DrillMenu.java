package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.training.Drill;

/**
 * In-game drill and match overlay (key M): every drill with its number key, and the quick
 * match options - AI level (D), players (P), your role (O) and the system (Y). The game is paused while it is
 * open.
 */
public final class DrillMenu implements Disposable {
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private boolean open;
    private int width;
    private int height;

    public DrillMenu(UiFonts fonts) {
        this.fonts = fonts;
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        open = !open;
    }

    public void close() {
        open = false;
    }

    /** Drill for number key {@code n} (1-based), or null. */
    public static Drill drillForNumber(int n) {
        Drill[] drills = Drill.values();
        return n >= 1 && n <= drills.length ? drills[n - 1] : null;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    public void render(Drill current, Difficulty difficulty, SessionMode mode, PlayerRole role) {
        render(current, difficulty, mode, role, null);
    }

    public void render(Drill current, Difficulty difficulty, SessionMode mode, PlayerRole role, OffenseSystem system) {
        if (!open) return;
        float s = UiFonts.uiScale(height);
        Drill[] drills = Drill.values();
        BitmapFont title = fonts.get(UiFonts.Weight.EXTRABOLD, 24f * s);
        BitmapFont name = fonts.get(UiFonts.Weight.BOLD, 17f * s);
        BitmapFont body = fonts.get(UiFonts.Weight.MEDIUM, 13.5f * s);
        BitmapFont number = fonts.get(UiFonts.Weight.EXTRABOLD, 15f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 12.5f * s);
        float pad = 30f * s;
        float rowH = 50f * s;
        float panelW = Math.min(width - 60f * s, 1040f * s);
        float optionsH = 150f * s;
        float panelH = 70f * s + drills.length * rowH + optionsH + pad;
        float x0 = (width - panelW) / 2f;
        float y0 = (height - panelH) / 2f;
        float top = y0 + panelH - pad;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.dim(shapes, width, height, 0.5f);
        UiKit.panel(shapes, x0, y0, panelW, panelH, 0.97f, 0f);
        for (int i = 0; i < drills.length; i++) {
            float rowTop = top - 60f * s - i * rowH;
            if (drills[i] == current) {
                UiKit.rowHighlight(shapes, x0 + 12f * s, rowTop - rowH + 6f * s, panelW - 24f * s, rowH - 6f * s, 1f, s, 1f);
            }
        }
        float optionsTop = top - 60f * s - drills.length * rowH - 14f * s;
        UiKit.divider(shapes, x0 + pad, optionsTop + 4f * s, panelW - 2 * pad, 1f);
        shapes.end();

        batch.begin();
        UiKit.text(batch, title, "DRILLS AND MATCH", x0 + pad, top, UiKit.TEXT, 1f);
        String hint = "PRESS A NUMBER  ·  M / ESC TO CLOSE";
        UiKit.text(batch, small, hint, x0 + panelW - pad - UiKit.width(small, hint), top - 5f * s, UiKit.TEXT_FAINT, 1f);
        for (int i = 0; i < drills.length; i++) {
            Drill drill = drills[i];
            float rowTop = top - 60f * s - i * rowH;
            boolean cur = drill == current;
            UiKit.text(batch, number, String.valueOf(i + 1), x0 + pad, rowTop - 9f * s, cur ? UiKit.JADE : UiKit.TEXT_FAINT, 1f);
            UiKit.text(batch, name, drill.label + (cur ? "   ·   current" : ""), x0 + pad + 34f * s, rowTop - 7f * s,
                    cur ? UiKit.TEXT : UiKit.TEXT, 1f);
            UiKit.text(batch, body, drill.instructions, x0 + pad + 34f * s, rowTop - 29f * s, panelW - 2 * pad - 34f * s,
                    Align.left, false, UiKit.TEXT_DIM, 1f);
        }
        float y = optionsTop - 16f * s;
        UiKit.text(batch, name, "QUICK MATCH OPTIONS", x0 + pad, y, UiKit.TEXT, 1f);
        UiKit.text(batch, small, "applied when the match (8) starts", x0 + pad + 250f * s, y - 2f * s, UiKit.TEXT_FAINT, 1f);
        y -= 32f * s;
        option(small, body, x0 + pad, y, "D", "AI level", difficulty.label, s);
        option(small, body, x0 + pad + panelW * 0.25f, y, "P", "Players", mode.label, s);
        option(small, body, x0 + pad + panelW * 0.53f, y, "O", "Your role", RoleGuide.of(role).title(), s);
        if (system != null) option(small, body, x0 + pad + panelW * 0.76f, y, "Y", "System", system.label, s);
        y -= 36f * s;
        if (mode.twoLocalPlayers) {
            UiKit.text(batch, body, "Player 2: arrows move, Right Ctrl jump, / play, . overhead or tip, , low stance, ' switch player",
                    x0 + pad, y, UiKit.TEXT_DIM, 1f);
        } else {
            UiKit.text(batch, body, RoleGuide.of(role).motto(), x0 + pad, y, UiKit.TEXT_DIM, 1f);
        }
        batch.end();
    }

    private void option(BitmapFont small, BitmapFont body, float x, float y, String key, String label, String value, float s) {
        UiKit.text(batch, small, key, x, y, UiKit.JADE, 1f);
        UiKit.text(batch, small, label.toUpperCase(java.util.Locale.ROOT), x + 22f * s, y, UiKit.TEXT_FAINT, 1f);
        UiKit.text(batch, body, value, x + 22f * s, y - 18f * s, UiKit.TEXT, 1f);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
