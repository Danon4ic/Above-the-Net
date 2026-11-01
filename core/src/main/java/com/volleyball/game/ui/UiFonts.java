package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;

import com.badlogic.gdx.utils.LongMap;

import java.util.EnumMap;

/**
 * The game's typefaces: Montserrat (SIL Open Font License, bundled in {@code fonts/}) in several
 * weights, rasterized by FreeType at exactly the pixel size needed, so text stays crisp at
 * every resolution. Latin and Cyrillic glyphs are included. Fonts are generated on first use
 * and cached by weight and size; sizes not used for a while (the window was resized) are freed.
 */
public final class UiFonts implements Disposable {
    public enum Weight {
        MEDIUM("Montserrat-Medium.ttf"),
        SEMIBOLD("Montserrat-SemiBold.ttf"),
        BOLD("Montserrat-Bold.ttf"),
        EXTRABOLD("Montserrat-ExtraBold.ttf"),
        BLACK("Montserrat-Black.ttf");

        final String file;

        Weight(String file) {
            this.file = file;
        }
    }

    static final String CHARACTERS = FreeTypeFontGenerator.DEFAULT_CHARS
            + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"
            + "—–«»…•·°±×←→↑↓№’“”‹›";

    private final EnumMap<Weight, FreeTypeFontGenerator> generators = new EnumMap<>(Weight.class);
    private final LongMap<Cached> cache = new LongMap<>();
    /** Above this many cached fonts, the ones unused for {@link #STALE_FRAMES} frames are freed. */
    private static final int CACHE_SOFT_LIMIT = 40;
    private static final long STALE_FRAMES = 600;

    private static final class Cached {
        final BitmapFont font;
        /** Frame the font was last asked for. */
        long lastUsed;

        Cached(BitmapFont font) {
            this.font = font;
        }
    }

    /**
     * A font of the weight at {@code pixels} height (rounded). The returned font is shared: set
     * its colour before drawing, do not dispose it, do not change its scale and do not keep it
     * past the frame (fonts of sizes no longer asked for are freed).
     */
    public BitmapFont get(Weight weight, float pixels) {
        int size = Math.max(8, Math.round(pixels));
        long key = (long) weight.ordinal() << 32 | size;
        Cached cached = cache.get(key);
        if (cached == null) {
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = size;
            p.characters = CHARACTERS;
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            p.hinting = FreeTypeFontGenerator.Hinting.AutoMedium;
            p.kerning = true;
            BitmapFont font = generator(weight).generateFont(p);
            font.setUseIntegerPositions(true);
            if (cache.size >= CACHE_SOFT_LIMIT) evictStale();
            cached = new Cached(font);
            cache.put(key, cached);
        }
        cached.lastUsed = frame();
        return cached.font;
    }

    private static long frame() {
        return Gdx.graphics == null ? 0L : Gdx.graphics.getFrameId();
    }

    /** Frees the fonts no screen has asked for in the last {@link #STALE_FRAMES} frames. */
    private void evictStale() {
        long now = frame();
        LongMap.Entries<Cached> entries = cache.entries();
        while (entries.hasNext) {
            LongMap.Entry<Cached> entry = entries.next();
            if (now - entry.value.lastUsed > STALE_FRAMES) {
                entry.value.font.dispose();
                entries.remove();
            }
        }
    }

    /**
     * A font of its own (not cached, the caller disposes it) with Latin and Cyrillic glyphs, for
     * text drawn outside the interface, such as the arena's video scoreboard.
     */
    public static BitmapFont create(Weight weight, int pixels) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.classpath("fonts/" + weight.file));
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = pixels;
            p.characters = CHARACTERS;
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            p.kerning = true;
            return generator.generateFont(p);
        } finally {
            generator.dispose();
        }
    }

    private static float userScale = 1f;

    /** Screen-height scale for sizes designed at 900 px, times the interface size from the settings. */
    public static float uiScale(int screenHeight) {
        return Math.max(0.75f, screenHeight / 900f) * userScale;
    }

    /** The interface size chosen in the settings (0.8 .. 1.4). */
    public static void setUserScale(float scale) {
        userScale = Math.max(0.8f, Math.min(1.4f, scale));
    }

    private FreeTypeFontGenerator generator(Weight weight) {
        return generators.computeIfAbsent(weight, w -> new FreeTypeFontGenerator(Gdx.files.classpath("fonts/" + w.file)));
    }

    @Override
    public void dispose() {
        for (Cached cached : cache.values()) cached.font.dispose();
        cache.clear();
        for (FreeTypeFontGenerator generator : generators.values()) generator.dispose();
        generators.clear();
    }
}
