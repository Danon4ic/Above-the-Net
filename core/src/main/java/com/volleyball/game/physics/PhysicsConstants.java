package com.volleyball.game.physics;

/** Physical constants in SI units. */
public final class PhysicsConstants {
    public static final float GRAVITY = 9.81f;
    /** Air density in an indoor hall at ~20 °C, kg/m³. */
    public static final float AIR_DENSITY = 1.2f;
    /** Free height above the playing area required by FIVB for world competitions. */
    public static final float CEILING_HEIGHT = 12.5f;

    private PhysicsConstants() {}
}
