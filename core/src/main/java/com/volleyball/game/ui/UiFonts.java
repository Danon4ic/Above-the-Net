package com.volleyball.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The game's typefaces: Montserrat (SIL Open Font License, bundled in {@code fonts/}) in several
 * weights, rasterized by FreeType at exactly the pixel size needed, so text stays crisp at
 * every resolution. Latin and Cyrillic glyphs are included. Fonts are generated on first use
 * and cached by weight and size.
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
    private final Map<Long, BitmapFont> cache = new HashMap<>();

    /**
     * A font of the weight at {@code pixels} height (rounded). The returned font is shared: set
     * its colour before drawing, do not dispose it and do not change its scale.
     */
    public BitmapFont get(Weight weight, float pixels) {
        int size = Math.max(8, Math.round(pixels));
        long key = (long) weight.ordinal() << 32 | size;
        BitmapFont font = cache.get(key);
        if (font == null) {
            FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
            p.size = size;
            p.characters = CHARACTERS;
            p.minFilter = Texture.TextureFilter.Linear;
            p.magFilter = Texture.TextureFilter.Linear;
            p.hinting = FreeTypeFontGenerator.Hinting.AutoMedium;
            p.kerning = true;
            font = generator(weight).generateFont(p);
            font.setUseIntegerPositions(true);
            cache.put(key, font);
        }
        return font;
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
        for (BitmapFont font : cache.values()) font.dispose();
        cache.clear();
        for (FreeTypeFontGenerator generator : generators.values()) generator.dispose();
        generators.clear();
    }
}
