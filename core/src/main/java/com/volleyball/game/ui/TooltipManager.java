package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;

import java.util.Objects;

/**
 * The one tooltip of the interface. A screen tells it every frame what the pointer is over
 * ({@link #hover}); the tooltip appears after a short delay, fades in, and stays put while the
 * pointer stays on the element. Moving quickly from one element to the next keeps it "warm": the
 * next tip follows after a much shorter delay and glides to its place instead of blinking.
 *
 * <p>Placement: to the right of the element, or to the left when there is no room; when neither
 * side fits, below it, or above it near the bottom of the window. It never leaves the window.
 *
 * <p>Kinds: a plain tip, an extended one (several lines and a note), one with a hot key, a locked
 * mode with the reason, a setting (what it does, and whether it needs a restart) and a context
 * tip during a match. Each kind has its small indicator on the left edge.
 */
public final class TooltipManager {
    /** What the tip is about: sets the indicator colour. */
    public enum Kind { INFO, ACTION, LOCKED, SETTING, CONTEXT }

    /**
     * The content: a title, a short description, an optional hot key with what it does, an
     * optional extra note and, for locked elements, the reason.
     */
    public record Tip(Kind kind, String title, String body, String hotkey, String hotkeyAction, String note) {
        public static Tip info(String title, String body) {
            return new Tip(Kind.INFO, title, body, null, null, null);
        }

        public static Tip action(String title, String body, String hotkey, String hotkeyAction) {
            return new Tip(Kind.ACTION, title, body, hotkey, hotkeyAction, null);
        }

        public static Tip locked(String title, String body, String reason) {
            return new Tip(Kind.LOCKED, title, body, null, null, reason);
        }

        public static Tip setting(String title, String body, String note) {
            return new Tip(Kind.SETTING, title, body, "← →", "Change", note);
        }

        public static Tip context(String title, String body, String hotkey, String hotkeyAction) {
            return new Tip(Kind.CONTEXT, title, body, hotkey, hotkeyAction, null);
        }
    }

    /** Hover time before the first tip appears, s. */
    static final float DELAY = 0.45f;
    /** Delay when moving straight from one tip to another (the tooltip is "warm"), s. */
    static final float WARM_DELAY = 0.1f;
    /** How long it stays warm after the pointer leaves, s. */
    static final float WARM_TIME = 0.35f;
    static final float FADE_IN = 0.16f;
    static final float FADE_OUT = 0.12f;
    static final float GLIDE = 0.14f;

    private final UiFonts fonts;
    private Object key;
    private Tip tip;
    private final float[] anchor = new float[4];
    private boolean hoveredThisFrame;
    private float hoverTime;
    private float sinceLeft = 99f;
    private float alpha;
    private boolean visible;
    // Drawn position (glides to the target when the tip moves to another element).
    private float drawX = Float.NaN;
    private float drawY = Float.NaN;
    private float scale = 1f;

    public TooltipManager(UiFonts fonts) {
        this.fonts = fonts;
    }

    /** Size of the tips relative to the interface (a setting). */
    public void setScale(float scale) {
        this.scale = Math.max(0.7f, Math.min(1.6f, scale));
    }

    /**
     * The pointer is over an element this frame: {@code key} identifies it (equal keys = the same
     * element), the rectangle is its bounds in screen pixels (y up).
     */
    public void hover(Object key, float x, float y, float w, float h, Tip tip) {
        hoveredThisFrame = true;
        if (!Objects.equals(key, this.key)) {
            boolean warm = visible || sinceLeft < WARM_TIME;
            this.key = key;
            // Moving on from a visible tip: the new one comes quickly; from nothing: the full delay.
            hoverTime = warm ? DELAY - WARM_DELAY : 0f;
            if (!warm) {
                drawX = Float.NaN;
                alpha = 0f;
            }
        }
        this.tip = tip;
        anchor[0] = x;
        anchor[1] = y;
        anchor[2] = w;
        anchor[3] = h;
    }

    /** Hides the tip at once (a click, a page change). */
    public void dismiss() {
        key = null;
        tip = null;
        visible = false;
        alpha = 0f;
        hoverTime = 0f;
        sinceLeft = 99f;
        drawX = Float.NaN;
    }

    /** Advances the timers; call once per frame after the {@link #hover} calls. */
    public void update(float delta) {
        if (hoveredThisFrame) {
            hoverTime += delta;
            sinceLeft = 0f;
            if (hoverTime >= DELAY) visible = true;
        } else {
            if (key != null) {
                key = null;
                hoverTime = 0f;
            }
            sinceLeft += delta;
            visible = false;
        }
        hoveredThisFrame = false;
        alpha = visible ? Math.min(1f, alpha + delta / FADE_IN) : Math.max(0f, alpha - delta / FADE_OUT);
    }

    public boolean isShowing() {
        return alpha > 0f && tip != null;
    }

    /** Draws the tip (call with neither the batch nor the shapes begun); {@code s} is the interface scale. */
    public void render(ShapeRenderer shapes, SpriteBatch batch, float s, int screenWidth, int screenHeight, float delta) {
        if (alpha <= 0f || tip == null) return;
        float k = s * scale;
        BitmapFont titleFont = fonts.get(UiFonts.Weight.BOLD, 16f * k);
        BitmapFont bodyFont = fonts.get(UiFonts.Weight.MEDIUM, 14f * k);
        BitmapFont keyFont = fonts.get(UiFonts.Weight.BOLD, 12f * k);
        float pad = 14f * k;
        float indicator = 3f * k;
        float textW = 300f * k;
        // Size from the content.
        float h = pad + titleFont.getCapHeight();
        float bodyH = tip.body() == null ? 0f : UiKit.wrappedHeight(bodyFont, tip.body(), textW);
        if (bodyH > 0f) h += 10f * k + bodyH;
        float noteH = tip.note() == null ? 0f : UiKit.wrappedHeight(bodyFont, noteText(), textW);
        if (noteH > 0f) h += 10f * k + noteH;
        float keyH = tip.hotkey() == null ? 0f : 22f * k;
        if (keyH > 0f) h += 12f * k + keyH;
        h += pad;
        float contentW = Math.max(UiKit.width(titleFont, tip.title()), Math.min(textW, Math.max(
                tip.body() == null ? 0f : UiKit.width(bodyFont, tip.body()), tip.note() == null ? 0f : UiKit.width(bodyFont, noteText()))));
        if (tip.hotkey() != null) {
            contentW = Math.max(contentW, UiKit.keycapWidth(keyFont, tip.hotkey()) + 10f * k
                    + UiKit.width(bodyFont, tip.hotkeyAction() == null ? "" : tip.hotkeyAction()));
        }
        float w = pad * 2f + indicator + 6f * k + contentW;

        // Placement: right, left, below, above; then clamped into the window.
        float gap = 12f * k;
        float margin = 8f * k;
        float ax = anchor[0];
        float ay = anchor[1];
        float aw = anchor[2];
        float ah = anchor[3];
        float tx;
        float ty;
        if (ax + aw + gap + w <= screenWidth - margin) {
            tx = ax + aw + gap;
            ty = ay + ah - h;
        } else if (ax - gap - w >= margin) {
            tx = ax - gap - w;
            ty = ay + ah - h;
        } else if (ay - gap - h >= margin) {
            tx = ax + (aw - w) / 2f;
            ty = ay - gap - h;
        } else {
            tx = ax + (aw - w) / 2f;
            ty = ay + ah + gap;
        }
        tx = Math.max(margin, Math.min(screenWidth - margin - w, tx));
        ty = Math.max(margin, Math.min(screenHeight - margin - h, ty));
        if (Float.isNaN(drawX)) {
            drawX = tx;
            drawY = ty;
        } else {
            float f = Math.min(1f, delta / GLIDE * 3f);
            drawX += (tx - drawX) * f;
            drawY += (ty - drawY) * f;
        }
        float a = UiKit.ease(alpha);
        // A small lift as it appears.
        float x = Math.round(drawX);
        float y = Math.round(drawY - (1f - a) * 6f * k);

        Color accent = indicatorColor();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float r = 6f * k;
        UiKit.shadow(shapes, x, y, w, h, r, a);
        // The thin gold border, then the dark navy surface inside it.
        Color border = new Color(UiKit.JADE.r, UiKit.JADE.g, UiKit.JADE.b, 0.45f * a);
        UiKit.roundedRect(shapes, x - 1f, y - 1f, w + 2f, h + 2f, r + 1f, border);
        UiKit.roundedRect(shapes, x, y, w, h, r, new Color(0.04f, 0.055f, 0.11f, 0.97f * a), new Color(0.11f, 0.14f, 0.24f, 0.97f * a));
        shapes.setColor(accent.r, accent.g, accent.b, a);
        shapes.rect(x + pad * 0.6f, y + pad * 0.8f, indicator, h - pad * 1.6f);
        float textX = x + pad + indicator + 6f * k;
        float keyY = y + pad;
        if (tip.hotkey() != null) UiKit.keycap(shapes, textX, keyY, UiKit.keycapWidth(keyFont, tip.hotkey()), keyH, false, a);
        shapes.end();

        batch.begin();
        float cy = y + h - pad;
        UiKit.text(batch, titleFont, tip.title(), textX, cy, UiKit.TEXT, a);
        cy -= titleFont.getCapHeight() + 10f * k;
        if (tip.body() != null) {
            UiKit.text(batch, bodyFont, tip.body(), textX, cy, textW, Align.left, true, UiKit.TEXT_DIM, a);
            cy -= bodyH + 10f * k;
        }
        if (tip.note() != null) {
            UiKit.text(batch, bodyFont, noteText(), textX, cy, textW, Align.left, true,
                    tip.kind() == Kind.LOCKED ? UiKit.RED : UiKit.MINT, a);
        }
        if (tip.hotkey() != null) {
            float kw = UiKit.keycapLabel(batch, keyFont, tip.hotkey(), textX, keyY, keyH, false, a);
            if (tip.hotkeyAction() != null) {
                UiKit.text(batch, bodyFont, tip.hotkeyAction(), textX + kw + 10f * k, keyY + keyH / 2f + bodyFont.getCapHeight() / 2f,
                        UiKit.TEXT_DIM, a);
            }
        }
        batch.end();
    }

    private String noteText() {
        return tip.kind() == Kind.LOCKED ? "Not available yet: " + tip.note() : tip.note();
    }

    private Color indicatorColor() {
        return switch (tip.kind()) {
            case ACTION -> UiKit.ACCENT;
            case LOCKED -> UiKit.TEXT_FAINT;
            case SETTING -> UiKit.JADE;
            case CONTEXT -> UiKit.GOLD;
            default -> UiKit.MINT;
        };
    }
}
