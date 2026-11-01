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
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private int width;
    private int height;
    private float clock;

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
        clock += delta;
        float s = UiFonts.uiScale(height);
        BitmapFont title = fonts.get(UiFonts.Weight.BLACK, 22f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.SEMIBOLD, 12.5f * s);
        float w = 440f * s;
        float h = 92f * s;
        float x = (width - w) / 2f;
        float y = height - h - 22f * s;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        UiKit.panel(shapes, x, y, w, h, 0.92f, 3f * s);
        float blink = 0.55f + 0.45f * (float) Math.sin(clock * 5.0);
        shapes.setColor(UiKit.RED.r, UiKit.RED.g, UiKit.RED.b, blink);
        shapes.circle(x + 26f * s, y + h - 30f * s, 7f * s, 16);
        UiKit.bar(shapes, x + 20f * s, y + 34f * s, w - 40f * s, 6f * s, progress, UiKit.JADE, 1f);
        shapes.end();
        batch.begin();
        UiKit.text(batch, title, "REPLAY", x + 42f * s, y + h - 20f * s, UiKit.TEXT, 1f);
        String right = angle + (slowMotion ? "   ·   SLOW MOTION" : "");
        UiKit.text(batch, small, right.toUpperCase(java.util.Locale.ROOT), x, y + h - 26f * s, w - 20f * s, Align.right, false, UiKit.JADE, 1f);
        UiKit.text(batch, small, "Space: slow motion   ·   C: camera angle   ·   V / Esc: back to the match", x, y + 22f * s, w,
                Align.center, false, UiKit.TEXT_DIM, 1f);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
