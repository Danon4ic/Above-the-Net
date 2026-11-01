package com.volleyball.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.volleyball.game.i18n.Lang;

/**
 * The look of the interface ("premium blue sports"): near-black surfaces separated by thin
 * borders, blue used only where it means something (the selection, focus, an active value, a
 * progress fill), Montserrat type. No gradients, glows or layered shadows: hierarchy comes from
 * size, weight, contrast and spacing. Shared drawing helpers (panels, key caps, bars, dividers)
 * keep every screen consistent.
 *
 * <p>Roles of the colours: PRIMARY / ACCENT blue for lines and fills; JADE the accent for text
 * (a lighter tint of the accent, readable at 4.5:1 on the background); GREEN success, RED errors.
 * Team colours appear only as small marks (strips, badges), never as surfaces.
 */
public final class UiKit {
    // ---- palette
    /** Background. */
    public static final Color NAVY = rgb(0x0A0A0F);
    /** Surface of panels. */
    public static final Color SURFACE = rgb(0x111118);
    /** A raised surface (hovered rows and buttons). */
    public static final Color DEEP = rgb(0x1A1A24);
    /** Borders and dividers. */
    public static final Color BORDER = rgb(0x252535);
    /** Main blue: fills, selection bars, progress. */
    public static final Color PRIMARY = rgb(0x2563EB);
    /** Accent blue for lines and marks. */
    public static final Color ACCENT = rgb(0x4F6EF7);
    /** The accent for active text and values (a lighter tint of ACCENT, for contrast). */
    public static final Color JADE = rgb(0x7C93FA);
    public static final Color GREEN = rgb(0x22C55E);
    public static final Color RED = rgb(0xEF4444);
    public static final Color TEXT = rgb(0xF1F5F9);
    public static final Color TEXT_DIM = rgb(0x94A3B8);
    public static final Color TEXT_FAINT = rgb(0x64748B);

    // ---- geometry
    /** Spacing scale (px at the design size, multiply by the interface scale). */
    public static final float SPACE_XS = 4f;
    public static final float SPACE_S = 8f;
    public static final float SPACE_M = 12f;
    public static final float SPACE_L = 16f;
    public static final float SPACE_XL = 24f;
    /** Corner radius of panels and controls. */
    public static final float RADIUS = 4f;

    private static final GlyphLayout LAYOUT = new GlyphLayout();
    private static final Color TMP = new Color();
    private static final Color A = new Color();
    private static final Color B = new Color();
    // Used only by the controls.
    private static final Color K1 = new Color();
    private static final Color K2 = new Color();
    // Used only inside roundedRect (callers pass A..B, TMP).
    private static final Color R_LOW = new Color();
    private static final Color R_HIGH = new Color();
    private static final Color R_CORNER = new Color();

    private UiKit() {
    }

    private static Color rgb(int hex) {
        return new Color(((hex >> 16) & 0xff) / 255f, ((hex >> 8) & 0xff) / 255f, (hex & 0xff) / 255f, 1f);
    }

    /**
     * A panel: a flat surface with a 1 px border and a small radius; with {@code accent} > 0 a
     * thin blue line along the top marks it as the active or primary panel.
     */
    public static void panel(ShapeRenderer s, float x, float y, float w, float h, float alpha, float accent) {
        float r = Math.min(RADIUS, Math.min(w, h) * 0.1f);
        shadow(s, x, y, w, h, r, alpha);
        roundedRect(s, x, y, w, h, r, TMP.set(BORDER.r, BORDER.g, BORDER.b, alpha));
        roundedRect(s, x + 1f, y + 1f, w - 2f, h - 2f, Math.max(0f, r - 1f), TMP.set(SURFACE.r, SURFACE.g, SURFACE.b, 0.94f * alpha));
        if (accent > 0f) {
            s.setColor(ACCENT.r, ACCENT.g, ACCENT.b, alpha);
            s.rect(x + r, y + h - Math.min(2f, accent), w - 2f * r, Math.min(2f, accent));
        }
    }

    /** A single soft shadow under a panel (enough to lift it off the arena, nothing more). */
    public static void shadow(ShapeRenderer s, float x, float y, float w, float h, float r, float alpha) {
        Color c = A.set(0f, 0f, 0f, 0.28f * alpha * alpha);
        roundedRect(s, x - 1f, y - 3f, w + 2f, h + 2f, r + 1f, c, c);
    }

    /** A filled rectangle with rounded corners and a vertical gradient (bottom to top colour). */
    public static void roundedRect(ShapeRenderer s, float x, float y, float w, float h, float r, Color bottom, Color top) {
        r = Math.max(0f, Math.min(r, Math.min(w, h) / 2f));
        if (r < 0.5f) {
            s.rect(x, y, w, h, bottom, bottom, top, top);
            return;
        }
        Color lowMid = R_LOW.set(bottom).lerp(top, r / h);
        Color highMid = R_HIGH.set(bottom).lerp(top, (h - r) / h);
        s.rect(x, y + r, w, h - 2f * r, lowMid, lowMid, highMid, highMid);
        s.rect(x + r, y, w - 2f * r, r, bottom, bottom, lowMid, lowMid);
        s.rect(x + r, y + h - r, w - 2f * r, r, highMid, highMid, top, top);
        int segments = Math.max(4, (int) (r / 1.5f));
        s.setColor(R_CORNER.set(bottom).lerp(lowMid, 0.5f));
        s.arc(x + r, y + r, r, 180f, 90f, segments);
        s.arc(x + w - r, y + r, r, 270f, 90f, segments);
        s.setColor(R_CORNER.set(highMid).lerp(top, 0.5f));
        s.arc(x + r, y + h - r, r, 90f, 90f, segments);
        s.arc(x + w - r, y + h - r, r, 0f, 90f, segments);
    }

    /** A flat rounded rectangle. */
    public static void roundedRect(ShapeRenderer s, float x, float y, float w, float h, float r, Color color) {
        roundedRect(s, x, y, w, h, r, color, color);
    }

    /**
     * A key cap (the background of a key label such as "F1" or "Alt+3"): a small bordered box,
     * filled blue when highlighted.
     */
    public static void keycap(ShapeRenderer s, float x, float y, float w, float h, boolean highlighted, float alpha) {
        float r = Math.min(3f, h * 0.2f);
        if (highlighted) {
            roundedRect(s, x, y, w, h, r, A.set(PRIMARY.r, PRIMARY.g, PRIMARY.b, alpha));
            return;
        }
        roundedRect(s, x, y, w, h, r, A.set(BORDER.r, BORDER.g, BORDER.b, alpha));
        roundedRect(s, x + 1f, y + 1f, w - 2f, h - 2f, Math.max(0f, r - 1f), B.set(DEEP.r, DEEP.g, DEEP.b, alpha));
    }

    /** Width of a key cap that fits {@code label} in {@code font}. */
    public static float keycapWidth(BitmapFont font, String label) {
        return Math.max(font.getLineHeight() * 1.25f, width(font, label) + font.getLineHeight() * 0.7f);
    }

    /** A key cap with its label centred in it; returns the cap's width. Call between the shape and the text pass. */
    public static float keycapLabel(SpriteBatch batch, BitmapFont font, String label, float x, float y, float h, boolean highlighted,
                                    float alpha) {
        float w = keycapWidth(font, label);
        text(batch, font, label, x, y + h / 2f + font.getCapHeight() / 2f, w, Align.center, false,
                TEXT, alpha);
        return w;
    }

    /** A progress bar: a flat track and a flat fill of {@code fraction} in {@code color}. */
    public static void bar(ShapeRenderer s, float x, float y, float w, float h, float fraction, Color color, float alpha) {
        float r = Math.min(2f, h / 2f);
        roundedRect(s, x, y, w, h, r, A.set(BORDER.r, BORDER.g, BORDER.b, alpha));
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f <= 0f) return;
        roundedRect(s, x, y, Math.max(h, w * f), h, r, B.set(color.r, color.g, color.b, alpha));
    }

    /** A 1 px horizontal divider in the border colour. */
    public static void divider(ShapeRenderer s, float x, float y, float w, float alpha) {
        s.setColor(BORDER.r, BORDER.g, BORDER.b, alpha);
        s.rect(x, y, w, 1f);
    }

    // ---- controls (one look for every screen; the states ease in through the 0..1 amounts)

    /** A hovered or selected row: a raised surface with the blue bar on its left edge. */
    public static void rowHighlight(ShapeRenderer s, float x, float y, float w, float h, float amount, float scale, float alpha) {
        if (amount <= 0.01f) return;
        s.setColor(DEEP.r, DEEP.g, DEEP.b, amount * alpha);
        s.rect(x, y, w, h);
        s.setColor(PRIMARY.r, PRIMARY.g, PRIMARY.b, amount * alpha);
        s.rect(x, y, 3f * scale, h);
    }

    /** The keyboard focus: a 1 px accent outline round {@code x, y, w, h}. */
    public static void focusRing(ShapeRenderer s, float x, float y, float w, float h, float scale, float alpha) {
        float t = Math.max(1f, scale);
        s.setColor(ACCENT.r, ACCENT.g, ACCENT.b, 0.8f * alpha);
        s.rect(x, y, w, t);
        s.rect(x, y + h - t, w, t);
        s.rect(x, y, t, h);
        s.rect(x + w - t, y, t, h);
    }

    /**
     * A button. Primary: a blue fill, lighter under the mouse. Secondary: a bordered surface that
     * rises under the mouse. Disabled: a flat border with no fill change. {@code hover} is 0..1.
     */
    public static void button(ShapeRenderer s, float x, float y, float w, float h, boolean primary, boolean enabled, float hover,
                              float alpha) {
        float r = RADIUS;
        if (!enabled) {
            roundedRect(s, x, y, w, h, r, K1.set(BORDER.r, BORDER.g, BORDER.b, alpha));
            roundedRect(s, x + 1f, y + 1f, w - 2f, h - 2f, r - 1f, K2.set(SURFACE.r, SURFACE.g, SURFACE.b, alpha));
            return;
        }
        if (primary) {
            roundedRect(s, x, y, w, h, r, K1.set(PRIMARY).lerp(ACCENT, hover).mul(1f, 1f, 1f, alpha));
            return;
        }
        roundedRect(s, x, y, w, h, r, K1.set(BORDER).lerp(TEXT_FAINT, hover).mul(1f, 1f, 1f, alpha));
        roundedRect(s, x + 1f, y + 1f, w - 2f, h - 2f, r - 1f, K2.set(SURFACE).lerp(DEEP, hover).mul(1f, 1f, 1f, alpha));
    }

    /** The colour of a button's label. */
    public static Color buttonText(boolean enabled) {
        return enabled ? TEXT : TEXT_FAINT;
    }

    /** A field (the closed drop-down box): a bordered surface; the border turns blue while it is open. */
    public static void field(ShapeRenderer s, float x, float y, float w, float h, float hover, boolean active, float alpha) {
        Color border = active ? K1.set(ACCENT) : K1.set(BORDER).lerp(TEXT_FAINT, hover);
        roundedRect(s, x, y, w, h, RADIUS, border.mul(1f, 1f, 1f, alpha));
        roundedRect(s, x + 1f, y + 1f, w - 2f, h - 2f, RADIUS - 1f, K2.set(NAVY).lerp(DEEP, hover).mul(1f, 1f, 1f, alpha));
    }

    /** A drop-down chevron, pointing down (closed) or up (open). */
    public static void chevron(ShapeRenderer s, float cx, float cy, float size, boolean up, Color color, float alpha) {
        s.setColor(color.r, color.g, color.b, alpha);
        if (up) s.triangle(cx - size, cy - size * 0.5f, cx + size, cy - size * 0.5f, cx, cy + size * 0.6f);
        else s.triangle(cx - size, cy + size * 0.5f, cx + size, cy + size * 0.5f, cx, cy - size * 0.6f);
    }

    /** A switch: the track goes from the border colour to blue, the knob slides across ({@code on} 0..1). */
    public static void toggle(ShapeRenderer s, float x, float y, float w, float h, float on, float alpha) {
        roundedRect(s, x, y, w, h, h / 2f, K1.set(BORDER).lerp(PRIMARY, on).mul(1f, 1f, 1f, alpha));
        s.setColor(K2.set(TEXT_DIM).lerp(TEXT, on).mul(1f, 1f, 1f, alpha));
        s.circle(x + h / 2f + on * (w - h), y + h / 2f, h / 2f - 3f, 20);
    }

    /** A slider: a thin track, the blue fill and a round knob, a little larger while active. */
    public static void slider(ShapeRenderer s, float x, float cy, float w, float fraction, boolean active, float scale, float alpha) {
        float th = 4f * scale;
        roundedRect(s, x, cy - th / 2f, w, th, th / 2f, K1.set(BORDER.r, BORDER.g, BORDER.b, alpha));
        roundedRect(s, x, cy - th / 2f, Math.max(th, w * fraction), th, th / 2f, K1.set(PRIMARY.r, PRIMARY.g, PRIMARY.b, alpha));
        s.setColor(TEXT.r, TEXT.g, TEXT.b, alpha);
        s.circle(x + w * fraction, cy, (active ? 8f : 7f) * scale, 20);
    }

    /** A pop-up surface (drop-down list, tooltip): raised above the panels, with a border. */
    public static void popup(ShapeRenderer s, float x, float y, float w, float h, float alpha) {
        shadow(s, x, y, w, h, RADIUS, alpha);
        roundedRect(s, x, y, w, h, RADIUS, K1.set(BORDER.r, BORDER.g, BORDER.b, alpha));
        roundedRect(s, x + 1f, y + 1f, w - 2f, h - 2f, RADIUS - 1f, K2.set(DEEP.r, DEEP.g, DEEP.b, alpha));
    }

    /** A small section heading: secondary-text small caps. Text pass. */
    public static float sectionTitle(SpriteBatch batch, BitmapFont font, String title, float x, float y, float alpha) {
        text(batch, font, title.toUpperCase(java.util.Locale.ROOT), x, y, TEXT_DIM, alpha);
        return width(font, title.toUpperCase(java.util.Locale.ROOT));
    }

    /** Darkens the screen behind a modal overlay (flat, the background colour). */
    public static void dim(ShapeRenderer s, float width, float height, float alpha) {
        s.setColor(NAVY.r, NAVY.g, NAVY.b, alpha * 0.86f);
        s.rect(0, 0, width, height);
    }

    /** Text with a faint 1 px shadow (readable over the arena); returns the drawn height. */
    public static float text(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color, float alpha) {
        return text(batch, font, text, x, y, 0f, Align.left, false, color, alpha);
    }

    public static float text(SpriteBatch batch, BitmapFont font, String text, float x, float y, float width, int align,
                             boolean wrap, Color color, float alpha) {
        text = Lang.t(text);
        float shadow = 1f;
        font.setColor(0f, 0f, 0f, 0.35f * alpha * color.a);
        font.draw(batch, text, x + shadow, y - shadow, width, align, wrap);
        font.setColor(color.r, color.g, color.b, color.a * alpha);
        LAYOUT.setText(font, text, font.getColor(), width, align, wrap);
        font.draw(batch, text, x, y, width, align, wrap);
        return LAYOUT.height;
    }

    public static float width(BitmapFont font, String text) {
        LAYOUT.setText(font, Lang.t(text));
        return LAYOUT.width;
    }

    /**
     * The text (translated) shortened with an ellipsis so it fits {@code maxWidth}; unchanged if it
     * already fits.
     */
    public static String fit(BitmapFont font, String text, float maxWidth) {
        String t = Lang.t(text);
        if (width(font, t) <= maxWidth) return t;
        int end = t.length();
        while (end > 1 && width(font, t.substring(0, end).stripTrailing() + "…") > maxWidth) end--;
        return t.substring(0, end).stripTrailing() + "…";
    }

    /** Height of {@code text} wrapped to {@code width}. */
    public static float wrappedHeight(BitmapFont font, String text, float width) {
        LAYOUT.setText(font, Lang.t(text), Color.WHITE, width, Align.left, true);
        return LAYOUT.height;
    }

    /** Smooth 0..1 ease. */
    public static float ease(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }
}
