package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
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
        float s = Math.min(UiFonts.uiScale(height), Math.min(width / 880f, height / 650f));
        BitmapFont label = fonts.get(UiFonts.Weight.BOLD, 15f * s);
        BitmapFont small = fonts.get(UiFonts.Weight.MEDIUM, 12f * s);
        BitmapFont keyFont = fonts.get(UiFonts.Weight.EXTRABOLD, 13f * s);
        BitmapFont titleFont = fonts.get(UiFonts.Weight.EXTRABOLD, 13f * s);
        float gap = 8f * s;
        float pad = 12f * s;
        float keyW = 22f * s;
        float chipH = 50f * s;
        float rowStep = chipH + gap;
        float contentW = Math.max(1f, width - 32f * s);
        chips.clear();
        float naturalW = -gap;
        for (Option o : options) {
            float textW = Math.max(UiKit.width(label, o.label), o.detail == null ? 0f : UiKit.width(small, o.detail));
            float w = Math.min(contentW, textW + keyW + pad * 2f + 6f * s);
            naturalW += w + gap;
            chips.add(new float[]{0f, 0f, w});
        }
        int columns = (options.size() + 2) / 3;
        float limitW = naturalW > 2f * contentW ? (contentW - (columns - 1) * gap) / columns : contentW;
        int row = 0;
        int firstInRow = 0;
        float rowW = 0f;
        for (int i = 0; i < chips.size(); i++) {
            float[] chip = chips.get(i);
            float w = Math.min(chip[2], limitW);
            if (rowW > 0f && rowW + gap + w > contentW) {
                centerRow(firstInRow, i, rowW);
                firstInRow = i;
                row++;
                rowW = 0f;
            }
            float x = rowW > 0f ? rowW + gap : 0f;
            chip[0] = x;
            chip[1] = row;
            chip[2] = w;
            rowW = x + w;
        }
        centerRow(firstInRow, chips.size(), rowW);
        float y = height * 0.025f;
        // Keep the lowest row clear of the key hints in the bottom-left corner.
        float reserved = keepClearRight > 0f ? keepClearRight + 16f * s : 0f;
        boolean overlapsHints = false;
        for (float[] chip : chips) {
            if (chip[0] < reserved && y + (row - chip[1]) * rowStep < keepClearTop + 10f * s) {
                overlapsHints = true;
                break;
            }
        }
        if (overlapsHints) {
            float shift = reserved - chips.get(firstInRow)[0];
            float[] last = chips.getLast();
            if (row == 0 && last[0] + last[2] + shift <= width - 16f * s) {
                for (float[] chip : chips) chip[0] += shift;
            } else y = Math.max(y, keepClearTop + 10f * s);
        }
        float headerY = y + row * rowStep + chipH + 21f * s;
        top = headerY + 7f * s;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            float[] chip = chips.get(i);
            float x = chip[0];
            float cy = y + (row - chip[1]) * rowStep;
            float w = chip[2];
            float alpha = o.enabled ? (hot ? 0.95f : 0.82f) : 0.45f;
            UiKit.panel(shapes, x, cy, w, chipH, alpha, 0f);
            if (o.selected) {
                // The selected option: a 1 px accent outline and a blue base line.
                float t = Math.max(1f, s);
                shapes.setColor(UiKit.ACCENT.r, UiKit.ACCENT.g, UiKit.ACCENT.b, alpha);
                shapes.rect(x, cy + chipH - t, w, t);
                shapes.rect(x, cy, t, chipH);
                shapes.rect(x + w - t, cy, t, chipH);
                shapes.setColor(UiKit.PRIMARY.r, UiKit.PRIMARY.g, UiKit.PRIMARY.b, alpha);
                shapes.rect(x, cy, w, 3f * s);
            }
            if (o.recommended) {
                // The recommended option (what Auto plays): a blue dot in the corner.
                shapes.setColor(UiKit.ACCENT);
                shapes.circle(x + w - 7f * s, cy + chipH - 7f * s, 3.5f * s, 12);
            }
            UiKit.keycap(shapes, x + pad * 0.6f, cy + (chipH - keyW) / 2f, keyW, keyW, o.selected, o.enabled ? 1f : 0.5f);
        }
        shapes.end();

        batch.begin();
        float titleX = chips.getFirst()[0];
        float titleW = Math.min(UiKit.width(titleFont, title) + 2f, contentW * 0.32f);
        UiKit.text(batch, titleFont, title, titleX, headerY, titleW, Align.left, false, hot ? UiKit.JADE : UiKit.TEXT_DIM, 1f);
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            float[] chip = chips.get(i);
            float x = chip[0];
            float cy = y + (row - chip[1]) * rowStep;
            float a = o.enabled ? 1f : 0.45f;
            float kx = x + pad * 0.6f;
            UiKit.text(batch, keyFont, o.key, kx, cy + chipH / 2f + keyFont.getCapHeight() / 2f, keyW, Align.center, false, UiKit.TEXT, a);
            float tx = kx + keyW + 8f * s;
            float textW = x + chip[2] - pad - tx;
            float labelY = o.detail == null ? cy + chipH / 2f + label.getCapHeight() / 2f : cy + chipH * 0.66f + label.getCapHeight() / 2f;
            UiKit.text(batch, label, o.label, tx, labelY, textW, Align.left, false, UiKit.TEXT, a);
            if (o.detail != null) UiKit.text(batch, small, o.detail, tx, cy + chipH * 0.3f + small.getCapHeight() / 2f,
                    textW, Align.left, false, UiKit.TEXT_DIM, a);
        }
        if (hint != null) {
            BitmapFont hintFont = fonts.get(UiFonts.Weight.MEDIUM, 13f * s);
            float hintX = titleX + titleW + 14f * s;
            UiKit.text(batch, hintFont, hint, hintX, headerY, width - 16f * s - hintX, Align.left, false, UiKit.TEXT, 0.9f);
        }
        batch.end();
    }

    private void centerRow(int first, int end, float rowWidth) {
        float inset = (width - rowWidth) / 2f;
        for (int i = first; i < end; i++) chips.get(i)[0] += inset;
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
