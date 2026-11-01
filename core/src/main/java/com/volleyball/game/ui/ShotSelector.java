package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * The play caller: a row of options at the bottom of the screen for the decision at hand — the
 * serve, the set (with the hitter each set goes to), the attack shot or the block — each with
 * its number key. "0 Auto" leaves the choice to the game, which picks from the situation (the
 * recommended option, marked with a blue dot, is what Auto would play). Options without a hitter are
 * dimmed. While the decision is close (the ball is coming) the bar comes up to full opacity.
 */
public final class ShotSelector implements Disposable {
    /** One option of the bar. */
    public static final class Option {
        public final String key;
        public final String label;
        /** Small text under the label (e.g. the hitter), or null. */
        public final String detail;
        public final boolean enabled;
        public final boolean selected;
        public final boolean recommended;

        public Option(String key, String label, String detail, boolean enabled, boolean selected, boolean recommended) {
            this.key = key;
            this.label = label;
            this.detail = detail;
            this.enabled = enabled;
            this.selected = selected;
            this.recommended = recommended;
        }
    }


    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final UiFonts fonts;
    private final List<float[]> chips = new ArrayList<>();
    /** Top edge of the bar (with its hint line) in the last frame drawn, 0 when hidden. */
    private float top;
    private int width;
    private int height;

    public ShotSelector(UiFonts fonts) {
        this.fonts = fonts;
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    /**
     * Draws the bar.
     *
     * @param title   what is being chosen ("SET", "SHOT", "SERVE", "BLOCK")
     * @param hint    a line about the selected option (its description), or null
     * @param options the options, in key order
     * @param hot     the decision is close: highlight the bar
     * @param keepClearRight, keepClearTop the bottom-left corner taken by the key hints (0, 0: none)
     */
    public void render(float delta, String title, String hint, List<Option> options, boolean hot, float keepClearRight,
                       float keepClearTop) {
        top = 0f;
        if (options.isEmpty()) return;
        float s = UiFonts.uiScale(height);
        float labelSize = 15f * s;
        BitmapFont label;
        BitmapFont small;
        BitmapFont keyFont;
        float gap = 8f * s;
        float pad = 12f * s;
        float keyW = 22f * s;
        // Shrink the text until the row fits the screen.
        float total;
        do {
            label = fonts.get(UiFonts.Weight.BOLD, labelSize);
            small = fonts.get(UiFonts.Weight.MEDIUM, labelSize * 0.78f);
            keyFont = fonts.get(UiFonts.Weight.EXTRABOLD, labelSize * 0.85f);
            chips.clear();
            total = 0f;
            for (Option o : options) {
                float w = Math.max(UiKit.width(label, o.label),
                        o.detail == null ? 0f : UiKit.width(small, o.detail));
                w += keyW + pad * 2f + 6f * s;
                chips.add(new float[]{w});
                total += w + gap;
            }
            total -= gap;
            labelSize -= 1f;
        } while (total > width - 60f * s && labelSize > 9f);

        float chipH = 50f * s;
        float titleW = UiKit.width(fonts.get(UiFonts.Weight.EXTRABOLD, 13f * s), title) + 22f * s;
        float x0 = (width - total - titleW) / 2f + titleW;
        float y = height * 0.025f;
        // Keep clear of the key hints in the bottom-left corner: move right, or else up over them.
        float reserved = keepClearRight > 0f ? keepClearRight + 16f * s : 0f;
        if (x0 - titleW < reserved) {
            if (reserved + titleW + total <= width - 16f * s) x0 = reserved + titleW;
            else y = Math.max(y, keepClearTop + 10f * s);
        }
        top = y + chipH + (hint == null ? 0f : 34f * s);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float x = x0;
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            float w = chips.get(i)[0];
            float alpha = o.enabled ? (hot ? 0.95f : 0.82f) : 0.45f;
            UiKit.panel(shapes, x, y, w, chipH, alpha, 0f);
            if (o.selected) {
                // The selected option: a 1 px accent outline and a blue base line.
                float t = Math.max(1f, s);
                shapes.setColor(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, alpha);
                shapes.rect(x, y + chipH - t, w, t);
                shapes.rect(x, y, t, chipH);
                shapes.rect(x + w - t, y, t, chipH);
                shapes.setColor(UiKit.PRIMARY.r, UiKit.PRIMARY.g, UiKit.PRIMARY.b, alpha);
                shapes.rect(x, y, w, 3f * s);
            }
            if (o.recommended) {
                // The recommended option (what Auto plays): a blue dot in the corner.
                shapes.setColor(UiKit.ACCENT);
                shapes.circle(x + w - 7f * s, y + chipH - 7f * s, 3.5f * s, 12);
            }
            UiKit.keycap(shapes, x + pad * 0.6f, y + (chipH - keyW) / 2f, keyW, keyW, o.selected, o.enabled ? 1f : 0.5f);
            x += w + gap;
        }
        shapes.end();

        batch.begin();
        BitmapFont titleFont = fonts.get(UiFonts.Weight.EXTRABOLD, 13f * s);
        UiKit.text(batch, titleFont, title, x0 - titleW, y + chipH / 2f + titleFont.getCapHeight() / 2f, hot ? UiKit.JADE : UiKit.TEXT_DIM, 1f);
        x = x0;
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            float w = chips.get(i)[0];
            float a = o.enabled ? 1f : 0.45f;
            float kx = x + pad * 0.6f;
            UiKit.text(batch, keyFont, o.key, kx, y + chipH / 2f + keyFont.getCapHeight() / 2f, keyW,
                    com.badlogic.gdx.utils.Align.center, false, UiKit.TEXT, a);
            float tx = kx + keyW + 8f * s;
            float labelY = o.detail == null ? y + chipH / 2f + label.getCapHeight() / 2f : y + chipH * 0.66f + label.getCapHeight() / 2f;
            UiKit.text(batch, label, o.label, tx, labelY, UiKit.TEXT, a);
            if (o.detail != null) UiKit.text(batch, small, o.detail, tx, y + chipH * 0.3f + small.getCapHeight() / 2f, UiKit.TEXT_DIM, a);
            x += w + gap;
        }
        if (hint != null) {
            BitmapFont hintFont = fonts.get(UiFonts.Weight.MEDIUM, 13f * s);
            UiKit.text(batch, hintFont, hint, 0f, y + chipH + 22f * s, width, com.badlogic.gdx.utils.Align.center, false, UiKit.TEXT, 0.9f);
        }
        batch.end();
    }

    /** Forgets the last frame's bar (call before a frame that may not draw it). */
    public void clearTop() {
        top = 0f;
    }

    /** Top edge of the bar and its hint line in the last frame (0: not shown), to stack captions above it. */
    public float top() {
        return top;
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
    }
}
