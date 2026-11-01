package com.volleyball.game.world;

import com.badlogic.gdx.graphics.Color;

/** Visual style of the floor and the hall. Purely cosmetic: geometry does not depend on it. */
public enum CourtTheme {
    FIVB_ARENA("FIVB arena",
            new Color(0.86f, 0.47f, 0.22f, 1f), new Color(0.13f, 0.34f, 0.63f, 1f), Color.WHITE,
            new Color(0.16f, 0.16f, 0.19f, 1f), new Color(0.55f, 0.09f, 0.12f, 1f), new Color(0.08f, 0.09f, 0.13f, 1f)),
    BLUE_PRO("Blue pro",
            new Color(0.16f, 0.36f, 0.76f, 1f), new Color(0.10f, 0.45f, 0.32f, 1f), Color.WHITE,
            new Color(0.12f, 0.14f, 0.17f, 1f), new Color(0.12f, 0.20f, 0.45f, 1f), new Color(0.06f, 0.08f, 0.12f, 1f)),
    WOOD_GYM("Wooden gym",
            new Color(0.80f, 0.60f, 0.37f, 1f), new Color(0.66f, 0.46f, 0.27f, 1f), new Color(0.98f, 0.98f, 0.95f, 1f),
            new Color(0.55f, 0.39f, 0.24f, 1f), new Color(0.30f, 0.32f, 0.36f, 1f), new Color(0.62f, 0.62f, 0.58f, 1f)),
    NIGHT_GREEN("Night green",
            new Color(0.12f, 0.52f, 0.36f, 1f), new Color(0.10f, 0.20f, 0.28f, 1f), new Color(0.98f, 0.92f, 0.35f, 1f),
            new Color(0.07f, 0.08f, 0.10f, 1f), new Color(0.15f, 0.15f, 0.18f, 1f), new Color(0.04f, 0.04f, 0.06f, 1f));

    public final String label;
    public final Color court;
    public final Color freeZone;
    public final Color lines;
    public final Color hallFloor;
    public final Color stands;
    public final Color walls;

    CourtTheme(String label, Color court, Color freeZone, Color lines, Color hallFloor, Color stands, Color walls) {
        this.label = label;
        this.court = court;
        this.freeZone = freeZone;
        this.lines = lines;
        this.hallFloor = hallFloor;
        this.stands = stands;
        this.walls = walls;
    }

    public CourtTheme next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Resolves a saved name, falling back to the default theme for unknown values. */
    public static CourtTheme fromName(String name) {
        for (CourtTheme theme : values()) if (theme.name().equals(name)) return theme;
        return FIVB_ARENA;
    }
}
