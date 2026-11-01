package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.world.LightingPreset;
import com.volleyball.game.world.Venue;

/**
 * The real light sources of a venue for the arena shader, in physical terms: spotlights with a
 * position, an aim, a beam cone and inverse-square falloff (championship floodlight banks and
 * stand wash lights), or the sun as a directional light (beach), plus sky and ground ambient
 * light (a hemisphere: what the sky/roof and the floor bounce back). One light direction casts
 * the shadow map: the average of the overhead rig, or the sun.
 *
 * <p>Packed for the shader: per light {@code pos} (xyz, w = 0 directional / 1 spot),
 * {@code dir} (xyz aim, w = cosine of the outer cone), {@code color} (rgb intensity,
 * w = reference distance at which the intensity applies).
 */
public final class LightRig {
    public static final int MAX_LIGHTS = 8;
    /** Direction towards the beach sun (afternoon, over the sea side). */
    public static final Vector3 SUN_DIRECTION = new Vector3(-0.45f, 0.62f, 0.64f).nor();

    final float[] pos = new float[MAX_LIGHTS * 4];
    final float[] dir = new float[MAX_LIGHTS * 4];
    final float[] color = new float[MAX_LIGHTS * 4];
    int count;
    final Color sky = new Color();
    final Color ground = new Color();
    /** Direction towards the light that casts the shadow map. */
    final Vector3 shadowDirection = new Vector3();
    /** Half size of the area the shadow map covers. */
    float shadowExtent;
    /**
     * Distance of the shadow view from the court centre along the light direction: things beyond
     * it (the roof, trusses and video cube above the floodlights) cast no shadow from these lights.
     */
    float shadowDistance;
    final Color background = new Color();
    float exposure = 1f;

    public static LightRig of(Venue venue, LightingPreset preset) {
        LightRig rig = new LightRig();
        switch (venue) {
            case BEACH -> rig.beach(preset);
            case SCHOOL_GYM -> rig.gym(preset);
            case SPORTS_PALACE -> rig.palace(preset);
            case ROOFTOP -> rig.rooftop(preset);
            default -> rig.championship(preset);
        }
        return rig;
    }

    /**
     * School gym: rows of fluorescent tubes (wide beams straight down) and daylight from the high
     * windows as a bright sky term; the evening preset is the gym after dark (the tubes only).
     */
    private void gym(LightingPreset preset) {
        Color tint;
        float tubes;
        switch (preset) {
            case WARM_GYM -> {
                tint = new Color(1f, 0.9f, 0.74f, 1f);
                tubes = 0.3f;
                sky.set(0.36f, 0.32f, 0.26f, 1f);
                ground.set(0.24f, 0.18f, 0.12f, 1f);
            }
            case EVENING_SHOW -> {
                tint = new Color(0.9f, 0.96f, 1f, 1f);
                tubes = 0.36f;
                sky.set(0.12f, 0.13f, 0.17f, 1f);
                ground.set(0.12f, 0.1f, 0.08f, 1f);
            }
            default -> {
                tint = new Color(0.94f, 0.98f, 1f, 1f);
                tubes = 0.28f;
                sky.set(0.4f, 0.42f, 0.46f, 1f);
                ground.set(0.22f, 0.18f, 0.13f, 1f);
            }
        }
        float y = HallVenues.GYM_CEILING - 1.1f;
        for (int sz = -1; sz <= 1; sz += 2) {
            for (int k = 0; k < 4; k++) {
                float x = -15f + k * 10f;
                spot(new Vector3(x, y, sz * 5f), new Vector3(x, 0f, sz * 7f), tint, tubes, 0.25f, 15f);
            }
        }
        shadowDirection.set(0.08f, 1f, 0.12f).nor();
        shadowExtent = 34f;
        shadowDistance = (y - 0.9f) / shadowDirection.y;
        background.set(0.55f, 0.57f, 0.6f, 1f);
        exposure = 1.05f;
    }

    /** Sports palace: the ring of round lamps under the ceiling, warm incandescent light. */
    private void palace(LightingPreset preset) {
        Color tint;
        float court;
        switch (preset) {
            case WARM_GYM -> {
                tint = new Color(1f, 0.8f, 0.56f, 1f);
                court = 0.34f;
                sky.set(0.22f, 0.17f, 0.12f, 1f);
                ground.set(0.17f, 0.12f, 0.08f, 1f);
            }
            case EVENING_SHOW -> {
                tint = new Color(0.95f, 0.92f, 0.9f, 1f);
                court = 0.4f;
                sky.set(0.07f, 0.06f, 0.08f, 1f);
                ground.set(0.06f, 0.05f, 0.05f, 1f);
            }
            default -> {
                tint = new Color(1f, 0.9f, 0.76f, 1f);
                court = 0.34f;
                sky.set(0.24f, 0.21f, 0.18f, 1f);
                ground.set(0.15f, 0.12f, 0.1f, 1f);
            }
        }
        // Eight of the ring's lamps carry the light: each aimed across the court.
        for (int i = 0; i < HallVenues.PALACE_LAMPS; i += 3) {
            Vector3 p = HallVenues.palaceLamp(i);
            spot(p, new Vector3(-p.x * 0.35f, 0f, -p.z * 0.35f), tint, court, 0.55f, 20f);
        }
        shadowDirection.set(0.1f, 1f, 0.14f).nor();
        shadowExtent = 44f;
        shadowDistance = (HallVenues.PALACE_LAMP_Y - 0.9f) / shadowDirection.y;
        background.set(0.03f, 0.025f, 0.02f, 1f);
        exposure = 1.04f;
    }

    /** Rooftop at night: two floodlights on each corner mast; the night sky is dark. */
    private void rooftop(LightingPreset preset) {
        Color tint;
        float court;
        switch (preset) {
            case WARM_GYM -> {
                // Sodium-like warm floodlights.
                tint = new Color(1f, 0.8f, 0.55f, 1f);
                court = 0.62f;
                sky.set(0.1f, 0.08f, 0.1f, 1f);
            }
            case EVENING_SHOW -> {
                tint = new Color(0.85f, 0.9f, 1f, 1f);
                court = 0.7f;
                sky.set(0.05f, 0.05f, 0.1f, 1f);
            }
            default -> {
                tint = new Color(0.95f, 0.97f, 1f, 1f);
                court = 0.62f;
                sky.set(0.09f, 0.1f, 0.16f, 1f);
            }
        }
        ground.set(0.07f, 0.07f, 0.08f, 1f);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                Vector3 p = new Vector3(sx * HallVenues.MAST_X, HallVenues.MAST_HEIGHT, sz * HallVenues.MAST_Z);
                spot(p, new Vector3(-sx * 2f, 0f, -sz * 1f), tint, court, 0.62f, 26f);
                spot(p, new Vector3(sx * 4f, 1.5f, sz * 12f), tint, court * 0.35f, 0.5f, 18f);
            }
        }
        shadowDirection.set(0.15f, 1f, 0.1f).nor();
        shadowExtent = 46f;
        shadowDistance = (HallVenues.MAST_HEIGHT - 0.6f) / shadowDirection.y;
        background.set(0.03f, 0.03f, 0.08f, 1f);
        exposure = 1.15f;
    }

    private void championship(LightingPreset preset) {
        // Colour temperature and show style per preset.
        Color tint;
        float court;
        float stands;
        switch (preset) {
            case WARM_GYM -> {
                tint = new Color(1f, 0.86f, 0.68f, 1f);
                court = 0.46f;
                stands = 0.16f;
                sky.set(0.22f, 0.19f, 0.15f, 1f);
                ground.set(0.16f, 0.12f, 0.09f, 1f);
                background.set(0.05f, 0.04f, 0.03f, 1f);
            }
            case EVENING_SHOW -> {
                tint = new Color(0.92f, 0.95f, 1f, 1f);
                court = 0.52f;
                stands = 0.07f;
                sky.set(0.08f, 0.09f, 0.16f, 1f);
                ground.set(0.05f, 0.05f, 0.08f, 1f);
                background.set(0.005f, 0.005f, 0.015f, 1f);
            }
            default -> {
                tint = new Color(1f, 0.98f, 0.94f, 1f);
                court = 0.48f;
                stands = 0.14f;
                sky.set(0.2f, 0.21f, 0.24f, 1f);
                ground.set(0.13f, 0.12f, 0.12f, 1f);
                background.set(0.02f, 0.02f, 0.03f, 1f);
            }
        }
        // Four floodlight banks on the truss, each aimed across the court (crossed beams give
        // shadow-softening light from several sides).
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                Vector3 p = new Vector3(sx * 6f, ArenaModelFactory.TRUSS_HEIGHT - 0.5f, sz * 9f);
                Vector3 target = new Vector3(-sx * 3f, 0f, -sz * 1.5f);
                spot(p, target, tint, court, 0.56f, 19f);
            }
        }
        // Stand wash lights: wide beams from the centre onto each stand.
        Vector3 centre = new Vector3(0f, 21f, 0f);
        spot(centre, new Vector3(0f, 6f, 20f), tint, stands, 0.45f, 22f);
        spot(centre, new Vector3(0f, 6f, -20f), tint, stands, 0.45f, 22f);
        spot(centre, new Vector3(26f, 6f, 0f), tint, stands, 0.5f, 26f);
        spot(centre, new Vector3(-26f, 6f, 0f), tint, stands, 0.5f, 26f);
        shadowDirection.set(0.12f, 1f, 0.18f).nor();
        shadowExtent = 42f;
        shadowDistance = (ArenaModelFactory.TRUSS_HEIGHT - 0.9f) / shadowDirection.y;
        exposure = 1.1f;
    }

    private void beach(LightingPreset preset) {
        Color sun;
        switch (preset) {
            case WARM_GYM -> {
                // Golden afternoon.
                sun = new Color(1.25f, 0.98f, 0.7f, 1f);
                sky.set(0.42f, 0.45f, 0.55f, 1f);
                ground.set(0.5f, 0.42f, 0.3f, 1f);
            }
            case EVENING_SHOW -> {
                // Sunset.
                sun = new Color(1.1f, 0.62f, 0.42f, 1f);
                sky.set(0.32f, 0.3f, 0.45f, 1f);
                ground.set(0.38f, 0.28f, 0.22f, 1f);
            }
            default -> {
                // Midday.
                sun = new Color(1.25f, 1.17f, 1.05f, 1f);
                sky.set(0.42f, 0.55f, 0.76f, 1f);
                ground.set(0.55f, 0.48f, 0.36f, 1f);
            }
        }
        int i = count * 4;
        pos[i] = SUN_DIRECTION.x;
        pos[i + 1] = SUN_DIRECTION.y;
        pos[i + 2] = SUN_DIRECTION.z;
        pos[i + 3] = 0f;
        color[i] = sun.r;
        color[i + 1] = sun.g;
        color[i + 2] = sun.b;
        color[i + 3] = 1f;
        count++;
        shadowDirection.set(SUN_DIRECTION);
        shadowExtent = 48f;
        shadowDistance = 70f;
        background.set(0.62f, 0.8f, 0.96f, 1f);
        exposure = 1.18f;
    }

    /**
     * A spotlight at {@code p} aimed at {@code target}: {@code intensity} at the reference distance
     * {@code range}, falling off with the square of the distance; {@code cosOuter} is the beam edge.
     */
    private void spot(Vector3 p, Vector3 target, Color tint, float intensity, float cosOuter, float range) {
        if (count >= MAX_LIGHTS) return;
        Vector3 aim = new Vector3(target).sub(p).nor();
        int i = count * 4;
        pos[i] = p.x;
        pos[i + 1] = p.y;
        pos[i + 2] = p.z;
        pos[i + 3] = 1f;
        dir[i] = aim.x;
        dir[i + 1] = aim.y;
        dir[i + 2] = aim.z;
        dir[i + 3] = cosOuter;
        color[i] = tint.r * intensity;
        color[i + 1] = tint.g * intensity;
        color[i + 2] = tint.b * intensity;
        color[i + 3] = range;
        count++;
    }

    public int count() {
        return count;
    }
}
