package com.volleyball.game.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

import java.util.Locale;

/**
 * Developer aid ({@code --capture=DIR --capture-at=1.5,2,2.5}): saves the screen as PNG files at
 * the given times after the start (seconds of real time) and then closes the game, so poses and
 * animations can be checked frame by frame without sitting at the screen. Frames are scaled down
 * to at most {@link #MAX_WIDTH} pixels wide.
 */
public final class FrameCapture {
    static final int MAX_WIDTH = 1280;

    private final FileHandle directory;
    private final float[] times;
    private int next;
    private float clock;

    public FrameCapture(String directory, float[] times) {
        this.directory = Gdx.files.absolute(directory);
        this.times = times;
        this.directory.mkdirs();
    }

    /** Parses "1.5,2,2.5" into the capture times; an empty or broken list captures once after 2 s. */
    public static float[] parseTimes(String list) {
        if (list == null || list.isBlank()) return new float[]{2f};
        String[] parts = list.split(",");
        float[] out = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Float.parseFloat(parts[i].trim());
        } catch (NumberFormatException e) {
            return new float[]{2f};
        }
        java.util.Arrays.sort(out);
        return out;
    }

    /** Call once per frame after everything is drawn. */
    public void afterFrame(float delta) {
        clock += delta;
        if (next >= times.length) return;
        if (clock < times[next]) return;
        save(String.format(Locale.ROOT, "frame_%02d_%05.2fs.png", next, clock));
        next++;
        if (next >= times.length) Gdx.app.exit();
    }

    private void save(String name) {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        Pixmap frame = Pixmap.createFromFrameBuffer(0, 0, w, h);
        Pixmap out = frame;
        if (w > MAX_WIDTH) {
            int sw = MAX_WIDTH;
            int sh = Math.round(h * (MAX_WIDTH / (float) w));
            out = new Pixmap(sw, sh, Pixmap.Format.RGBA8888);
            out.setFilter(Pixmap.Filter.BiLinear);
            out.drawPixmap(frame, 0, 0, w, h, 0, 0, sw, sh);
            frame.dispose();
        }
        // The frame buffer's first row is the bottom of the screen.
        PixmapIO.writePNG(directory.child(name), out, java.util.zip.Deflater.DEFAULT_COMPRESSION, true);
        out.dispose();
        Gdx.app.log("Capture", "saved " + name);
    }
}
