package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;

/** The replay badge: "REPLAY" with a blinking dot, the camera angle, the progress and the keys. */
public final class ReplayOverlay implements Disposable {
    private static final String CONTROLS = "Space: slow motion   ·   C: camera angle   ·   R: restart   ·   V / Esc: back to the match";
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private int width;
    private int height;

    public ReplayOverlay(UiFonts fonts) {
        this.fonts = fonts;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    public void render(float delta, float progress, String angle, boolean slowMotion) {
        float s = UiFonts.uiScale(height);
        BitmapFont title = fonts.get(UiFonts.Weight.BLACK, 22f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 12.5f * s);
        float w = Math.min(width - 24f * s, 480f * s);
        float pad = 18f * s;
        float controlsWidth = w - 2f * pad;
        float controlsHeight = UiKit.wrappedHeight(small, CONTROLS, controlsWidth - 1f);
        float barY = controlsHeight + 21f * s;
        float h = barY + 53f * s;
        float x = (width - w) / 2f;
        float y = height - h - 22f * s;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, x, y, w, h, 0.92f, 0f);
        shapes.setColor(UiKit.RED);
        shapes.circle(x + 26f * s, y + h - 30f * s, 7f * s, 16);
        UiKit.bar(shapes, x + pad, y + barY, controlsWidth, 6f * s, progress, UiKit.PRIMARY, 1f);
        shapes.end();
        batch.begin();
        float titleX = x + 42f * s;
        float titleW = Math.min(UiKit.width(title, "REPLAY") + 1f, controlsWidth * 0.44f);
        UiKit.text(batch, title, "REPLAY", titleX, y + h - 20f * s, titleW, Align.left, false, UiKit.TEXT, 1f);
        String right = angle + (slowMotion ? "   ·   SLOW MOTION" : "");
        float angleX = titleX + titleW + 12f * s;
        UiKit.text(batch, small, right.toUpperCase(java.util.Locale.ROOT), angleX, y + h - 26f * s,
                x + w - pad - angleX, Align.right, false, UiKit.JADE, 1f);
        UiKit.text(batch, small, CONTROLS, x + pad, y + 10f * s + controlsHeight, controlsWidth,
                Align.center, true, UiKit.TEXT_DIM, 1f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
