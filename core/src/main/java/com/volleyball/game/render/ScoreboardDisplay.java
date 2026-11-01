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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/**
 * The arena's video scoreboard. Its image is drawn into an off-screen frame buffer that the 3D
 * screens use as a texture; it is redrawn only when the displayed data changes.
 *
 * <p>In this stage it shows static pre-match values; the match systems feed real data into
 * {@link #setScore} once scoring and sets are implemented.
 */
public final class ScoreboardDisplay implements Disposable {
    private static final int WIDTH = 768;
    private static final int HEIGHT = 384;

    private final FrameBuffer frameBuffer;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();

    private String homeName = "HOME";
    private String awayName = "AWAY";
    private int homePoints;
    private int awayPoints;
    private int homeSets;
    private int awaySets;
    private int setNumber = 1;
    private String caption = "WARM-UP";
    private boolean dirty = true;

    public ScoreboardDisplay() {
        frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
        frameBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(false);
        // The 3D screen maps the top of the image to v = 0 while a frame buffer stores its bottom
        // row there, so draw with a vertically flipped projection to keep the text upright.
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0f, HEIGHT, WIDTH, -HEIGHT));
    }

    public Texture texture() {
        return frameBuffer.getColorBufferTexture();
    }

    public void setTeams(String home, String away) {
        if (!home.equals(homeName) || !away.equals(awayName)) {
            homeName = home;
            awayName = away;
            dirty = true;
        }
    }

    public void setScore(int homePoints, int awayPoints, int homeSets, int awaySets, int setNumber) {
        if (homePoints != this.homePoints || awayPoints != this.awayPoints || homeSets != this.homeSets
                || awaySets != this.awaySets || setNumber != this.setNumber) {
            this.homePoints = homePoints;
            this.awayPoints = awayPoints;
            this.homeSets = homeSets;
            this.awaySets = awaySets;
            this.setNumber = setNumber;
            dirty = true;
        }
    }

    public void setCaption(String caption) {
        if (!caption.equals(this.caption)) {
            this.caption = caption;
            dirty = true;
        }
    }

    /** Redraws the frame buffer if needed. Must be called outside any other batch/frame-buffer pass. */
    public void refresh() {
        if (!dirty) return;
        dirty = false;
        frameBuffer.begin();
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        drawCentered(caption + "   SET " + setNumber, HEIGHT - 30f, 2.6f, new Color(0.6f, 0.8f, 1f, 1f));
        drawCentered(homeName, HEIGHT - 120f, 3.2f, Color.WHITE, WIDTH * 0.25f);
        drawCentered(awayName, HEIGHT - 120f, 3.2f, Color.WHITE, WIDTH * 0.75f);
        drawCentered(String.valueOf(homePoints), HEIGHT - 175f, 8f, new Color(1f, 0.85f, 0.2f, 1f), WIDTH * 0.25f);
        drawCentered(String.valueOf(awayPoints), HEIGHT - 175f, 8f, new Color(1f, 0.85f, 0.2f, 1f), WIDTH * 0.75f);
        drawCentered(":", HEIGHT - 175f, 8f, Color.WHITE, WIDTH * 0.5f);
        drawCentered("SETS " + homeSets + " - " + awaySets, 50f, 2.6f, new Color(0.7f, 0.95f, 0.7f, 1f));
        batch.end();
        frameBuffer.end();
        // The screens are seen from 20+ m away: without mipmaps the minified text shimmers.
        Texture texture = texture();
        texture.bind();
        Gdx.gl.glGenerateMipmap(GL20.GL_TEXTURE_2D);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
    }

    private void drawCentered(String text, float topY, float scale, Color color) {
        drawCentered(text, topY, scale, color, WIDTH / 2f);
    }

    private void drawCentered(String text, float topY, float scale, Color color, float centreX) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, layout, centreX - layout.width / 2f, topY);
    }

    @Override
    public void dispose() {
        frameBuffer.dispose();
        batch.dispose();
        font.dispose();
    }
}
