package com.volleyball.game.render.anim;

import com.badlogic.gdx.math.MathUtils;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.physics.PlayerLocomotion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Continuous defense poses sampled from Blender at 100 Hz. */
public final class DiveMotion {
    public static final int HIP_HEIGHT = 0, PITCH = 1, ADVANCE = 2, LEAN = 3, BANK = 4, HEAD = 5;
    public static final int SUPPORT_ARM = 6, REACH_ARM = 9, SUPPORT_LEG = 12, TRAIL_LEG = 15;
    public static final int CHANNELS = 18;
    private static final int HZ = 100;
    private static final int FRAMES = 143;
    private static final String HEADER = "hip_height,pitch,advance,lean,bank,head,"
            + "support_raise,support_spread,support_elbow,reach_raise,reach_spread,reach_elbow,"
            + "support_ankle,support_depth,support_pitch,trail_ankle,trail_depth,trail_pitch";
    private static final float[][] SPRAWL = load("dive_motion");
    private static final float[][] KNEE_DROP = load("knee_drop");
    private static final float[][] SIDE_ROLL = load("side_roll");

    private DiveMotion() {
    }

    private static float[][] load(String name) {
        String resource = "/animation/" + name + ".csv";
        float[][] result = new float[FRAMES][CHANNELS];
        try (var input = Objects.requireNonNull(DiveMotion.class.getResourceAsStream(resource), resource);
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (!HEADER.equals(reader.readLine())) throw new IOException("Unexpected dive motion channels");
            for (float[] pose : result) {
                String line = reader.readLine();
                if (line == null) throw new IOException("Incomplete dive motion");
                String[] fields = line.split(",", -1);
                if (fields.length != CHANNELS) throw new IOException("Invalid dive motion frame");
                for (int i = 0; i < pose.length; i++) {
                    pose[i] = Float.parseFloat(fields[i]);
                    if (!Float.isFinite(pose[i])) throw new IOException("Non-finite dive motion sample");
                }
            }
            if (reader.readLine() != null) throw new IOException("Extra dive motion frames");
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
        return result;
    }

    public static void sample(FallKind kind, BodyState phase, float elapsed, float[] pose) {
        float[][] frames = switch (kind) {
            case SPRAWL -> SPRAWL;
            case KNEE_DROP -> KNEE_DROP;
            case SIDE_ROLL -> SIDE_ROLL;
        };
        float seconds = switch (phase) {
            case DIVING -> MathUtils.clamp(elapsed, 0f, PlayerLocomotion.DIVE_TIME);
            case ON_FLOOR -> PlayerLocomotion.DIVE_TIME + MathUtils.clamp(elapsed, 0f, PlayerLocomotion.FLOOR_TIME);
            case GETTING_UP -> PlayerLocomotion.DIVE_TIME + PlayerLocomotion.FLOOR_TIME
                    + MathUtils.clamp(elapsed, 0f, PlayerLocomotion.GET_UP_TIME);
            case UPRIGHT -> PlayerLocomotion.DIVE_TIME + PlayerLocomotion.FLOOR_TIME + PlayerLocomotion.GET_UP_TIME;
        };
        float frame = MathUtils.clamp(seconds * HZ, 0f, FRAMES - 1);
        int lower = (int) frame;
        int upper = Math.min(lower + 1, FRAMES - 1);
        for (int i = 0; i < CHANNELS; i++) {
            pose[i] = MathUtils.lerp(frames[lower][i], frames[upper][i], frame - lower);
        }
    }
}
