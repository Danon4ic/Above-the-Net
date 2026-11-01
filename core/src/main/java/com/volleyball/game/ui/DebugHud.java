package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;

import java.util.List;

/**
 * The status panel in the top-left corner: the first line is a heading (drill or match mode),
 * the others are status lines. Drawn on a soft glass panel in the game's typeface.
 */
public final class DebugHud implements Disposable {
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private int width;
    /** Space kept free at the top (e.g. for the match HUD card). */
    private float topOffset;

    public DebugHud(UiFonts fonts) {
        this.fonts = fonts;
    }

    public void resize(int width, int height) {
        this.width = width;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    public void setTopOffset(float offset) {
        topOffset = offset;
    }

    public void render(int screenHeight, List<String> status) {
        if (status.isEmpty()) return;
        float s = UiFonts.uiScale(screenHeight);
        BitmapFont heading = fonts.get(UiFonts.Weight.BOLD, 15f * s);
        BitmapFont body = fonts.get(UiFonts.Weight.MEDIUM, 13f * s);
        float pad = 14f * s;
        float line = body.getLineHeight() * 1.08f;
        float maxWidth = UiKit.width(heading, status.get(0));
        for (int i = 1; i < status.size(); i++) maxWidth = Math.max(maxWidth, UiKit.width(body, status.get(i)));
        maxWidth = Math.min(maxWidth, width * 0.62f);
        float panelH = heading.getLineHeight() * 1.2f + line * (status.size() - 1) + pad * 1.4f;
        float x = 16f * s;
        float top = screenHeight - 16f * s - topOffset;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, x, top - panelH, maxWidth + pad * 2, panelH, 0.62f, 0f);
        shapes.setColor(UiKit.JADE);
        shapes.rect(x, top - panelH, 3f * s, panelH);
        shapes.end();

        batch.begin();
        float y = top - pad * 0.8f;
        UiKit.text(batch, heading, status.get(0), x + pad, y, UiKit.TEXT, 1f);
        y -= heading.getLineHeight() * 1.2f;
        for (int i = 1; i < status.size(); i++) {
            String text = status.get(i);
            boolean indented = text.startsWith("  ");
            UiKit.text(batch, body, text.strip(), x + pad + (indented ? 12f * s : 0f), y, indented ? UiKit.TEXT_DIM : UiKit.TEXT, 0.95f);
            y -= line;
        }
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
