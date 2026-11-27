package com.volleyball.game.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.volleyball.game.ui.UiFonts;
import com.volleyball.game.world.Venue;

/** One repeating strip per arena, baked once at scene creation. Only its U offset changes during play. */
final class LedBoardTexture implements Disposable {
    private static final int WIDTH = 2048;
    private static final int HEIGHT = 128;
    static final float ASPECT = (float) WIDTH / HEIGHT;
    private static final float MARK_WIDTH = 66f;
    private static final float TEXT_CENTER = 64f;

    private record Design(String event, String series, Color top, Color bottom, Color ink,
                          Color accent, Color trim, float speed) {
        Design(String event, String series, String top, String bottom, String ink, String accent, String trim,
               float speed) {
            this(event, series, Color.valueOf(top), Color.valueOf(bottom), Color.valueOf(ink),
                    Color.valueOf(accent), Color.valueOf(trim), speed);
        }

        static Design forVenue(Venue venue) {
            return switch (venue) {
                case CHAMPIONSHIP -> new Design("VOLLEYBALL FINALS", "ABOVE THE NET",
                        "192C44", "0B1525", "EAF4FD", "78DCCB", "375674", 0.026f);
                case BEACH -> new Design("BEACH VOLLEY", "COASTAL TOUR",
                        "E9DEBC", "C7B68E", "12383A", "A8402C", "487B76", 0.017f);
                case SCHOOL_GYM -> new Design("SCHOOL CUP", "",
                        "2B4558", "172B3E", "E7DBB5", "DFB766", "557083", 0f);
                case SPORTS_PALACE -> new Design("CITY CUP", "PALACE OF SPORTS",
                        "442728", "23191E", "F6E4C6", "D8A65E", "815B43", 0.018f);
                case ROOFTOP -> new Design("NIGHT SERIES", "SKYLINE VOLLEY",
                        "20213E", "101225", "E2E2FB", "83CBE3", "675A91", 0.023f);
            };
        }
    }

    private final FrameBuffer buffer;
    private final float speed;

    LedBoardTexture(Venue venue, ArenaScene.Matchup matchup) {
        Design design = Design.forVenue(venue);
        speed = design.speed();
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        SpriteBatch batch = new SpriteBatch();
        ShapeRenderer shapes = new ShapeRenderer();
        BitmapFont title = UiFonts.create(UiFonts.Weight.EXTRABOLD, 58);
        BitmapFont secondary = UiFonts.create(UiFonts.Weight.BOLD, 46);
        Matrix4 projection = new Matrix4().setToOrtho2D(0f, 0f, WIDTH, HEIGHT);
        batch.setProjectionMatrix(projection);
        shapes.setProjectionMatrix(projection);
        try {
            buffer.begin();
            try {
                ScreenUtils.clear(0f, 0f, 0f, 1f);
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                background(shapes, design);
                shapes.end();
                if (venue == Venue.SCHOOL_GYM) {
                    drawSchoolBanner(batch, shapes, title, secondary, design, matchup);
                } else {
                    drawRibbon(venue, batch, shapes, title, secondary, design, matchup);
                }
            } finally {
                buffer.end();
            }
            Texture texture = texture();
            texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);
            texture.bind();
            Gdx.gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
            texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        } finally {
            secondary.dispose();
            title.dispose();
            shapes.dispose();
            batch.dispose();
        }
    }

    private static void background(ShapeRenderer shapes, Design design) {
        Color top = design.top();
        Color bottom = design.bottom();
        shapes.rect(0f, 0f, WIDTH, HEIGHT, bottom, bottom, top, top);
        shapes.setColor(design.trim());
        shapes.rect(0f, 8f, WIDTH, 3f);
        shapes.rect(0f, HEIGHT - 11f, WIDTH, 3f);
    }

    private static void drawRibbon(Venue venue, SpriteBatch batch, ShapeRenderer shapes, BitmapFont title,
                                   BitmapFont secondary, Design design, ArenaScene.Matchup matchup) {
        GlyphLayout event = layout(title, design.event(), design.ink());
        GlyphLayout series = layout(secondary, design.series(), design.ink());
        GlyphLayout home = layout(title, matchup.home(), design.ink());
        GlyphLayout away = layout(title, matchup.away(), design.ink());
        GlyphLayout versus = layout(secondary, "×", design.accent());
        float matchupWidth = home.width + versus.width + away.width + 56f;

        float eventWidth = MARK_WIDTH + 24f + event.width;
        float seriesWidth = MARK_WIDTH + 24f + series.width;
        float gap = (WIDTH - eventWidth - matchupWidth - seriesWidth) / 3f;
        float eventStart = gap / 2f;
        float matchupStart = eventStart + eventWidth + gap;
        float seriesStart = matchupStart + matchupWidth + gap;
        float versusStart = matchupStart + home.width + 28f;
        float awayStart = versusStart + versus.width + 28f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        mark(shapes, venue, design, eventStart);
        mark(shapes, venue, design, seriesStart);
        teamStripe(shapes, design, matchup.homeFans(), matchupStart, home.width);
        teamStripe(shapes, design, matchup.awayFans(), awayStart, away.width);
        shapes.end();

        batch.begin();
        draw(batch, title, event, eventStart + MARK_WIDTH + 24f, TEXT_CENTER);
        draw(batch, title, home, matchupStart, TEXT_CENTER + 2f);
        draw(batch, secondary, versus, versusStart, TEXT_CENTER + 2f);
        draw(batch, title, away, awayStart, TEXT_CENTER + 2f);
        draw(batch, secondary, series, seriesStart + MARK_WIDTH + 24f, TEXT_CENTER);
        batch.end();
    }

    /** The school gym has painted end-wall banners on both sides of the court. */
    private static void drawSchoolBanner(SpriteBatch batch, ShapeRenderer shapes, BitmapFont title,
                                         BitmapFont secondary, Design design, ArenaScene.Matchup matchup) {
        GlyphLayout event = layout(title, design.event(), design.ink());
        GlyphLayout teams = layout(secondary, matchup.home() + "  ×  " + matchup.away(), design.ink());
        float center = WIDTH / 2f;
        float left = center - event.width / 2f - 70f;
        float right = center + event.width / 2f + 70f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(design.accent());
        for (int i = 0; i < 3; i++) {
            float inset = 22f * i;
            float y = 50f + 12f * i;
            shapes.rect(90f + inset, y, left - 90f - inset, 4f);
            shapes.rect(right, y, WIDTH - 90f - right - inset, 4f);
        }
        shapes.end();

        batch.begin();
        draw(batch, title, event, center - event.width / 2f, 86f);
        draw(batch, secondary, teams, center - teams.width / 2f, 36f);
        batch.end();
    }

    private static void mark(ShapeRenderer shapes, Venue venue, Design design, float x) {
        shapes.setColor(design.accent());
        switch (venue) {
            case CHAMPIONSHIP -> {
                shapes.triangle(x + 7f, 45f, x + 34f, 87f, x + 34f, 69f);
                shapes.triangle(x + 34f, 87f, x + 61f, 45f, x + 34f, 69f);
                shapes.rect(x + 22f, 45f, 24f, 5f);
            }
            case BEACH -> {
                shapes.arc(x + 33f, 61f, 19f, 0f, 180f, 18);
                shapes.rect(x + 6f, 54f, 54f, 3f);
                shapes.rect(x + 15f, 45f, 36f, 3f);
            }
            case SPORTS_PALACE -> {
                shapes.rect(x + 19f, 51f, 28f, 28f, 14f, 14f, 1f, 1f, 45f);
                shapes.setColor(design.bottom());
                shapes.rect(x + 26f, 58f, 14f, 14f, 7f, 7f, 1f, 1f, 45f);
            }
            case ROOFTOP -> {
                shapes.rect(x + 9f, 45f, 12f, 26f);
                shapes.rect(x + 27f, 45f, 12f, 40f);
                shapes.rect(x + 45f, 45f, 12f, 19f);
            }
            case SCHOOL_GYM -> throw new IllegalArgumentException(venue.name());
        }
    }

    private static void teamStripe(ShapeRenderer shapes, Design design, Color kit, float x, float width) {
        shapes.setColor(kit.cpy().lerp(design.ink(), 0.32f));
        shapes.rect(x, 28f, width, 5f);
    }

    private static GlyphLayout layout(BitmapFont font, String text, Color color) {
        font.setColor(color);
        return new GlyphLayout(font, text);
    }

    private static void draw(SpriteBatch batch, BitmapFont font, GlyphLayout text, float x, float center) {
        font.draw(batch, text, x, center + text.height / 2f);
    }

    Texture texture() {
        return buffer.getColorBufferTexture();
    }

    float speed() {
        return speed;
    }

    @Override
    public void dispose() {
        buffer.dispose();
    }
}
