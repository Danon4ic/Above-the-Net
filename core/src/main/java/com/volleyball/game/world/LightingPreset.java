package com.volleyball.game.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;

/** Hall lighting setup: ambient light, a key light, a fill light and the colour of the ceiling fixtures. */
public enum LightingPreset {
    ARENA_BRIGHT("Bright arena",
            new Color(0.50f, 0.50f, 0.52f, 1f),
            new Color(0.80f, 0.80f, 0.78f, 1f), new Vector3(-0.25f, -1f, -0.35f),
            new Color(0.30f, 0.30f, 0.34f, 1f), new Vector3(0.4f, -0.6f, 0.5f),
            new Color(1f, 1f, 0.96f, 1f), new Color(0.05f, 0.06f, 0.09f, 1f)),
    WARM_GYM("Warm gym",
            new Color(0.52f, 0.46f, 0.38f, 1f),
            new Color(0.85f, 0.72f, 0.55f, 1f), new Vector3(-0.1f, -1f, -0.2f),
            new Color(0.25f, 0.22f, 0.18f, 1f), new Vector3(0.3f, -0.5f, 0.6f),
            new Color(1f, 0.86f, 0.62f, 1f), new Color(0.16f, 0.13f, 0.10f, 1f)),
    EVENING_SHOW("Evening show",
            new Color(0.22f, 0.22f, 0.30f, 1f),
            new Color(0.95f, 0.95f, 1f, 1f), new Vector3(0f, -1f, -0.05f),
            new Color(0.18f, 0.12f, 0.30f, 1f), new Vector3(-0.5f, -0.4f, 0.6f),
            new Color(0.80f, 0.88f, 1f, 1f), new Color(0.01f, 0.01f, 0.03f, 1f));

    public final String label;
    public final Color ambient;
    public final Color keyColor;
    public final Vector3 keyDirection;
    public final Color fillColor;
    public final Vector3 fillDirection;
    public final Color fixtureColor;
    public final Color background;

    LightingPreset(String label, Color ambient, Color keyColor, Vector3 keyDirection, Color fillColor,
                   Vector3 fillDirection, Color fixtureColor, Color background) {
        this.label = label;
        this.ambient = ambient;
        this.keyColor = keyColor;
        this.keyDirection = keyDirection.nor();
        this.fillColor = fillColor;
        this.fillDirection = fillDirection.nor();
        this.fixtureColor = fixtureColor;
        this.background = background;
    }

    public LightingPreset next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static LightingPreset fromName(String name) {
        for (LightingPreset preset : values()) if (preset.name().equals(name)) return preset;
        return ARENA_BRIGHT;
    }
}
