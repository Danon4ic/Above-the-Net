package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.tactics.TeamCommand;
import com.volleyball.game.tactics.TeamTactics;

import java.util.List;

/**
 * The coach's panel: the team commands of one group (offense, defense, serve) with their keys
 * (hold Alt + number), the active one highlighted. G shows the next group. It appears while Alt
 * is held and for a moment after a command is given.
 */
public final class TacticsPanel implements Disposable {
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private int height;

    public TacticsPanel(UiFonts fonts) {
        this.fonts = fonts;
    }

    public void resize(int width, int height) {
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    /**
     * @param group   the group shown
     * @param tactics the team's commands
     * @param extra   a line under the list (e.g. the receiver a serve command targets), or null
     * @param alpha   fade (0..1)
     */
    public void render(TeamCommand.Group group, TeamTactics tactics, String extra, float alpha) {
        if (alpha <= 0f) return;
        float s = UiFonts.uiScale(height);
        List<TeamCommand> commands = TeamCommand.of(group);
        BitmapFont title = fonts.get(UiFonts.Weight.EXTRABOLD, 17f * s);
        BitmapFont item = fonts.get(UiFonts.Weight.SEMIBOLD, 14f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.MEDIUM, 12f * s);
        float pad = 16f * s;
        float rowH = 26f * s;
        float w = 360f * s;
        float h = pad * 2f + 34f * s + rowH * commands.size() + (extra == null ? 0f : rowH) + 30f * s;
        float x = 24f * s;
        float y = (height - h) / 2f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, x, y, w, h, 0.92f * alpha, 0f);
        float rowTop = y + h - pad - 34f * s;
        for (int i = 0; i < commands.size(); i++) {
            if (!tactics.is(commands.get(i))) continue;
            shapes.setColor(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.18f * alpha);
            shapes.rect(x + 6f * s, rowTop - (i + 1) * rowH + 3f * s, w - 12f * s, rowH - 2f * s);
        }
        BitmapFont keyFont = fonts.get(UiFonts.Weight.EXTRABOLD, 11.5f * s);
        float capH = 20f * s;
        for (int i = 0; i < commands.size(); i++) {
            float ry = rowTop - i * rowH - 6f * s;
            UiKit.keycap(shapes, x + pad, ry - capH + 4f * s, UiKit.keycapWidth(keyFont, "Alt+" + (i + 1)), capH,
                    tactics.is(commands.get(i)), alpha);
        }
        shapes.end();

        batch.begin();
        UiKit.text(batch, title, "TEAM TACTICS  ·  " + group.label.toUpperCase(java.util.Locale.ROOT), x + pad, y + h - pad, UiKit.JADE, alpha);
        for (int i = 0; i < commands.size(); i++) {
            TeamCommand c = commands.get(i);
            boolean on = tactics.is(c);
            float ry = rowTop - i * rowH - 6f * s;
            UiKit.keycapLabel(batch, keyFont, "Alt+" + (i + 1), x + pad, ry - capH + 4f * s, capH, on, alpha);
            UiKit.text(batch, item, c.label, x + pad + 66f * s, ry, on ? UiKit.JADE : UiKit.TEXT, alpha);
        }
        float fy = rowTop - commands.size() * rowH - 8f * s;
        if (extra != null) {
            UiKit.text(batch, small, extra, x + pad, fy, UiKit.TEXT_DIM, alpha);
            fy -= rowH;
        }
        UiKit.text(batch, small, "G: next group   ·   Alt+0: let the team decide", x + pad, fy, UiKit.TEXT_FAINT, alpha);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
