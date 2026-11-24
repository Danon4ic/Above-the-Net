package com.volleyball.game.render.anim;

import com.badlogic.gdx.math.MathUtils;
import com.volleyball.game.physics.PhysicsConstants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

/** Layered pose offsets sampled from the editable Blender actions in art/animation. */
public enum JumpMotion {
    PLANT, JUMP_SET, BACK_ATTACK, LANDING;

    public static final int LEAN = 0, TWIST = 1, BANK = 2, COMPRESSION = 3;
    public static final int LEFT_ARM = 4, RIGHT_ARM = 5;
    public static final int LEFT_ANKLE = 6, RIGHT_ANKLE = 7, ANKLE_LIFT = 8;
    public static final int CHANNELS = 9;
    private static final int SAMPLE_COUNT = 65;
    private static final String RESOURCE = "/animation/jump_motion.csv";
    private static final String HEADER =
            "clip,lean,twist,bank,compression,left_arm,right_arm,left_ankle,right_ankle,ankle_lift";
    private final float[][] samples = new float[SAMPLE_COUNT][CHANNELS];

    static {
        int[] counts = new int[values().length];
        try (var input = Objects.requireNonNull(JumpMotion.class.getResourceAsStream(RESOURCE), RESOURCE);
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (!HEADER.equals(reader.readLine())) throw new IOException("Unexpected jump motion channels");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                JumpMotion clip = valueOf(fields[0].toUpperCase(Locale.ROOT));
                int frame = counts[clip.ordinal()]++;
                if (fields.length != CHANNELS + 1 || frame >= SAMPLE_COUNT) {
                    throw new IOException("Invalid sample for " + clip);
                }
                for (int channel = 0; channel < CHANNELS; channel++) {
                    float value = Float.parseFloat(fields[channel + 1]);
                    if (!Float.isFinite(value)) throw new IOException("Non-finite sample for " + clip);
                    clip.samples[frame][channel] = value;
                }
            }
            for (JumpMotion clip : values()) {
                if (counts[clip.ordinal()] != SAMPLE_COUNT) throw new IOException("Incomplete " + clip);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }

    public void sample(float progress, float[] pose) {
        float index = MathUtils.clamp(progress, 0f, 1f) * (SAMPLE_COUNT - 1);
        int lower = (int) index;
        int upper = Math.min(lower + 1, SAMPLE_COUNT - 1);
        for (int channel = 0; channel < CHANNELS; channel++) {
            pose[channel] = MathUtils.lerp(samples[lower][channel], samples[upper][channel], index - lower);
        }
    }

    /** Uses the physical jump's current height and vertical velocity, including during a replay. */
    public static float flightProgress(float height, float verticalSpeed) {
        float takeoffSpeed = (float) Math.sqrt(verticalSpeed * verticalSpeed
                + 2f * PhysicsConstants.GRAVITY * Math.max(0f, height));
        return takeoffSpeed < 0.01f ? 0.5f : 0.5f * (1f - verticalSpeed / takeoffSpeed);
    }
}
