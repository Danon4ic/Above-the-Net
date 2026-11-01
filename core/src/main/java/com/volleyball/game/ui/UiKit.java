package com.volleyball.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.volleyball.game.i18n.Lang;

/**
 * The look of the interface: a deep navy glass, warm gold accents and a cool cyan for
 * secondary highlights, with Montserrat type. Panels have rounded corners, a soft layered
 * shadow, a vertical gradient with a light rim on top and an optional gold accent with a glow.
 * Shared drawing helpers (panels, key caps, bars, dividers) keep every overlay consistent.
 *
 * <p>Roles of the colours: ACCENT (lines, fills, glows) and JADE (active text, values) are the
 * gold accent, MINT the cyan secondary highlight, DEEP a raised navy surface, GOLD the gold
 * decoration, RED errors.
 */
public final class UiKit {
    public static final Color NAVY = new Color(0.035f, 0.05f, 0.10f, 1f);
    public static final Color GLASS = new Color(0.045f, 0.06f, 0.12f, 0.9f);
    public static final Color GLASS_LIGHT = new Color(0.13f, 0.16f, 0.27f, 0.92f);
    /** A raised surface (hovered buttons). */
    public static final Color DEEP = new Color(0.17f, 0.21f, 0.33f, 1f);
    /** The accent for lines, fills and glows. */
    public static final Color ACCENT = new Color(0.96f, 0.76f, 0.30f, 1f);
    /** The accent for active text and values. */
    public static final Color JADE = new Color(0.96f, 0.76f, 0.30f, 1f);
    /** Secondary highlights (mottos, notes). */
    public static final Color MINT = new Color(0.30f, 0.80f, 0.98f, 1f);
    public static final Color GOLD = new Color(0.96f, 0.76f, 0.30f, 1f);
    public static final Color GREEN = new Color(0.35f, 0.88f, 0.55f, 1f);
    public static final Color RED = new Color(0.98f, 0.36f, 0.36f, 1f);
    public static final Color TEXT = new Color(0.96f, 0.97f, 1f, 1f);
    public static final Color TEXT_DIM = new Color(0.63f, 0.68f, 0.78f, 1f);
    public static final Color TEXT_FAINT = new Color(0.45f, 0.5f, 0.6f, 1f);

    private static final GlyphLayout LAYOUT = new GlyphLayout();
    private static final Color TMP = new Color();
    private static final Color TMP_BOTTOM = new Color();
    private static final Color A = new Color();
    private static final Color B = new Color();
    private static final Color C = new Color();
    // Used only inside roundedRect (callers pass A..C, TMP).
    private static final Color R_LOW = new Color();
    private static final Color R_HIGH = new Color();
    private static final Color R_CORNER = new Color();

    private UiKit() {
    }

    /**
     * A glass panel: soft shadow, rounded corners, vertical gradient, light rim, and (if
     * {@code accent} > 0) a gold accent line along the top with a glow below it.
     */
    public static void panel(ShapeRenderer s, float x, float y, float w, float h, float alpha, float accent) {
        float r = Math.min(12f, Math.min(w, h) * 0.12f);
        shadow(s, x, y, w, h, r, alpha);
        Color top = TMP.set(GLASS_LIGHT).mul(1f, 1f, 1f, alpha);
        Color bottomColor = TMP_BOTTOM.set(GLASS).mul(1f, 1f, 1f, alpha);
        roundedRect(s, x, y, w, h, r, bottomColor, top);
        // A light rim along the top edge and a faint one at the bottom.
        s.setColor(1f, 1f, 1f, 0.10f * alpha);
        s.rect(x + r, y + h - 1f, w - 2f * r, 1f);
        s.setColor(0f, 0f, 0f, 0.25f * alpha);
        s.rect(x + r, y, w - 2f * r, 1f);
        if (accent > 0f) {
            s.setColor(ACCENT.r, ACCENT.g, ACCENT.b, alpha);
            s.rect(x + r, y + h - accent, w - 2f * r, accent);
            Color glow = A.set(ACCENT.r, ACCENT.g, ACCENT.b, 0.16f * alpha);
            Color none = B.set(ACCENT.r, ACCENT.g, ACCENT.b, 0f);
            float gh = Math.min(26f, h * 0.25f);
            s.rect(x + r, y + h - accent - gh, w - 2f * r, gh, none, none, glow, glow);
        }
    }

    /** A soft shadow under a panel: a few widening, fading layers, slightly below it. */
    public static void shadow(ShapeRenderer s, float x, float y, float w, float h, float r, float alpha) {
        for (int i = 4; i >= 1; i--) {
            float grow = i * 3f;
            Color c = A.set(0f, 0f, 0.02f, 0.07f * alpha * alpha * alpha);
            roundedRect(s, x - grow, y - grow - 3f, w + 2f * grow, h + 2f * grow, r + grow, c, c);
        }
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
        int segments = Math.max(6, (int) (r / 1.5f));
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
     * A key cap (the background of a key label such as "F1" or "Alt+3"): a small raised rounded
     * box, gold when highlighted.
     */
    public static void keycap(ShapeRenderer s, float x, float y, float w, float h, boolean highlighted, float alpha) {
        float r = Math.min(5f, h * 0.25f);
        Color base = highlighted ? A.set(GOLD.r * 0.85f, GOLD.g * 0.85f, GOLD.b * 0.85f, alpha)
                : A.set(0.17f, 0.21f, 0.33f, alpha);
        Color face = highlighted ? B.set(GOLD.r, GOLD.g, GOLD.b, alpha) : B.set(0.24f, 0.29f, 0.43f, alpha);
        // The lower lip makes it look pressed out of the panel.
        roundedRect(s, x, y - 2f, w, h, r, base, base);
        roundedRect(s, x, y, w, h, r, base, face);
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
                highlighted ? NAVY : TEXT, alpha);
        return w;
    }

    /** A rounded progress bar: a dark track and a fill of {@code fraction} in {@code color}. */
    public static void bar(ShapeRenderer s, float x, float y, float w, float h, float fraction, Color color, float alpha) {
        float r = h / 2f;
        Color track = A.set(0f, 0f, 0f, 0.45f * alpha);
        roundedRect(s, x, y, w, h, r, track, track);
        float f = Math.max(0f, Math.min(1f, fraction));
        if (f <= 0f) return;
        Color low = B.set(color.r * 0.7f, color.g * 0.7f, color.b * 0.7f, alpha);
        Color high = C.set(color.r, color.g, color.b, alpha);
        roundedRect(s, x, y, Math.max(h, w * f), h, r, low, high);
    }

    /** A horizontal divider that fades out at both ends. */
    public static void divider(ShapeRenderer s, float x, float y, float w, float alpha) {
        Color mid = A.set(1f, 1f, 1f, 0.14f * alpha);
        Color none = B.set(1f, 1f, 1f, 0f);
        s.rect(x, y, w / 2f, 1f, none, mid, mid, none);
        s.rect(x + w / 2f, y, w / 2f, 1f, mid, none, none, mid);
    }

    /** A small section heading: gold small caps. Text pass. */
    public static float sectionTitle(SpriteBatch batch, BitmapFont font, String title, float x, float y, float alpha) {
        text(batch, font, title.toUpperCase(java.util.Locale.ROOT), x, y, JADE, alpha);
        return width(font, title.toUpperCase(java.util.Locale.ROOT));
    }

    /** Darkens the screen behind a modal overlay, with a slight vignette towards the edges. */
    public static void dim(ShapeRenderer s, float width, float height, float alpha) {
        Color centre = A.set(0.01f, 0.015f, 0.04f, alpha * 0.85f);
        Color edge = B.set(0.0f, 0.0f, 0.02f, Math.min(1f, alpha * 1.15f));
        s.rect(0, 0, width, height / 2f, edge, edge, centre, centre);
        s.rect(0, height / 2f, width, height / 2f, centre, centre, edge, edge);
    }

    /** Text with a soft drop shadow; returns the drawn height. */
    public static float text(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color, float alpha) {
        return text(batch, font, text, x, y, 0f, Align.left, false, color, alpha);
    }

    public static float text(SpriteBatch batch, BitmapFont font, String text, float x, float y, float width, int align,
                             boolean wrap, Color color, float alpha) {
        text = Lang.t(text);
        float shadow = Math.max(1f, font.getLineHeight() / 22f);
        font.setColor(0f, 0f, 0f, 0.45f * alpha * color.a);
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
