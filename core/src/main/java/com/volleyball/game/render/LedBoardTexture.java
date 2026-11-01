package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
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
import com.volleyball.game.world.Venue;

/**
 * The content of the LED ribbon boards: a strip of sponsor and event messages in bright colour
 * blocks, drawn once into a texture that repeats along the boards and scrolls (the scene shifts
 * its U offset). The bitmap font's pixels look like LEDs.
 */
final class LedBoardTexture implements Disposable {
    private static final int WIDTH = 1024;
    private static final int HEIGHT = 64;

    private final FrameBuffer buffer;

    LedBoardTexture(Venue venue, String matchup) {
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        buffer.getColorBufferTexture().setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        buffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        String[] messages = switch (venue) {
            case BEACH -> new String[]{"BEACH VOLLEYBALL TOUR", "SUN & SAND", matchup, "COCONUT WATER", "OCEAN FM"};
            case SCHOOL_GYM -> new String[]{"SCHOOL CUP FINAL", matchup, "GO TEAM GO!", "PE DEPARTMENT", "NO GUM ON THE FLOOR"};
            case SPORTS_PALACE -> new String[]{"PALACE OF SPORTS", matchup, "CITY CUP", "SINCE 1974", "FAIR PLAY"};
            case ROOFTOP -> new String[]{"NIGHT SERIES", matchup, "CITY LIGHTS LEAGUE", "SKYLINE SPORTS", "ABOVE THE NET"};
            default -> new String[]{"VOLLEYBALL WORLD CHAMPIONSHIP", matchup, "SPIKE ENERGY", "SPORTS TV LIVE", "PLAY FAIR"};
        };
        Color[] blocks = switch (venue) {
            case BEACH -> new Color[]{new Color(0.95f, 0.55f, 0.1f, 1f), new Color(0.1f, 0.6f, 0.85f, 1f), new Color(0.95f, 0.85f, 0.15f, 1f)};
            case SCHOOL_GYM -> new Color[]{new Color(0.15f, 0.3f, 0.62f, 1f), new Color(0.82f, 0.68f, 0.12f, 1f), new Color(0.7f, 0.15f, 0.18f, 1f)};
            case SPORTS_PALACE -> new Color[]{new Color(0.5f, 0.07f, 0.08f, 1f), new Color(0.06f, 0.12f, 0.3f, 1f), new Color(0.55f, 0.42f, 0.1f, 1f)};
            case ROOFTOP -> new Color[]{new Color(0.75f, 0.05f, 0.55f, 1f), new Color(0.05f, 0.55f, 0.75f, 1f), new Color(0.35f, 0.1f, 0.7f, 1f)};
            default -> new Color[]{new Color(0.05f, 0.1f, 0.35f, 1f), new Color(0.75f, 0.08f, 0.12f, 1f), new Color(0.02f, 0.02f, 0.03f, 1f)};
        };
        SpriteBatch batch = new SpriteBatch();
        ShapeRenderer shapes = new ShapeRenderer();
        BitmapFont font = new BitmapFont();
        GlyphLayout layout = new GlyphLayout();
        Matrix4 projection = new Matrix4().setToOrtho2D(0f, 0f, WIDTH, HEIGHT);
        batch.setProjectionMatrix(projection);
        shapes.setProjectionMatrix(projection);
        font.getData().setScale(2.6f);
        buffer.begin();
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        float segment = WIDTH / (float) messages.length;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < messages.length; i++) {
            shapes.setColor(blocks[i % blocks.length]);
            shapes.rect(i * segment, 0f, segment, HEIGHT);
            shapes.setColor(1f, 1f, 1f, 0.9f);
            shapes.rect(i * segment, 0f, 3f, HEIGHT);
        }
        shapes.end();
        batch.begin();
        for (int i = 0; i < messages.length; i++) {
            layout.setText(font, messages[i]);
            float scale = Math.min(1f, (segment - 20f) / layout.width);
            font.getData().setScale(2.6f * scale);
            layout.setText(font, messages[i]);
            font.setColor(Color.WHITE);
            font.draw(batch, messages[i], i * segment + (segment - layout.width) / 2f, HEIGHT / 2f + layout.height / 2f);
            font.getData().setScale(2.6f);
        }
        batch.end();
        buffer.end();
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }

    Texture texture() {
        return buffer.getColorBufferTexture();
    }

    @Override
    public void dispose() {
        buffer.dispose();
    }
}
