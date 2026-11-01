package com.volleyball.game.render;

/**
 * Graphics presets.
 * <ul>
 *   <li>LOW: libGDX's standard per-vertex lighting and blob shadows (fastest).</li>
 *   <li>MEDIUM: the venue's real light sources per pixel, a 1024 shadow map (every object casts
 *       shadows), capsule ambient occlusion, an animated crowd, floor detail, filmic colours.</li>
 *   <li>HIGH: a 2048 shadow map and the reflection of the venue in the floor (half resolution).</li>
 *   <li>ULTRA: a 4096 shadow map and the reflection at full resolution.</li>
 * </ul>
 */
public enum GraphicsQuality {
    LOW("Low", false, 0f, 0),
    MEDIUM("Medium", true, 0f, 1024),
    HIGH("High", true, 0.5f, 2048),
    ULTRA("Ultra", true, 1f, 4096);

    public final String label;
    /** Whether the arena shader is used. */
    public final boolean arenaShader;
    /** Resolution of the floor reflection relative to the screen (0 = no reflection). */
    public final float reflectionScale;
    /** Shadow map size in texels (0 = none). */
    public final int shadowMapSize;

    GraphicsQuality(String label, boolean arenaShader, float reflectionScale, int shadowMapSize) {
        this.label = label;
        this.arenaShader = arenaShader;
        this.reflectionScale = reflectionScale;
        this.shadowMapSize = shadowMapSize;
    }

    public GraphicsQuality next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static GraphicsQuality fromName(String name) {
        for (GraphicsQuality q : values()) if (q.name().equals(name)) return q;
        return HIGH;
    }
}
