package com.volleyball.game.render;

import com.badlogic.gdx.graphics.g3d.Attribute;

/**
 * Surface parameters for the arena shader: how much the floor reflects, how glossy a surface is,
 * the rim light on bodies and the procedural floor pattern.
 */
public final class ShadingAttribute extends Attribute {
    public static final String ALIAS = "arenaShading";
    public static final long TYPE = register(ALIAS);

    public static final int PATTERN_NONE = 0;
    public static final int PATTERN_SPORTS_FLOOR = 1;
    public static final int PATTERN_WOOD = 2;
    public static final int PATTERN_SAND = 3;

    /** 0..1: strength of the floor reflection. */
    public float reflectivity;
    /** Blinn-Phong exponent. */
    public float shininess;
    /** 0..1: specular highlight strength. */
    public float specular;
    /** 0..1: rim light strength. */
    public float rim;
    /** One of the PATTERN_ constants. */
    public int pattern;
    /** Blur of the reflection, in pixels (rougher surfaces blur more). */
    public float roughness;

    public ShadingAttribute(float reflectivity, float shininess, float specular, float rim, int pattern, float roughness) {
        super(TYPE);
        this.reflectivity = reflectivity;
        this.shininess = shininess;
        this.specular = specular;
        this.rim = rim;
        this.pattern = pattern;
        this.roughness = roughness;
    }

    public static ShadingAttribute floor(float reflectivity, int pattern) {
        return new ShadingAttribute(reflectivity, 60f, 0.35f, 0f, pattern, 3.5f);
    }

    /** Sand: no reflection, almost no highlight. */
    public static ShadingAttribute sand() {
        return new ShadingAttribute(0f, 8f, 0.03f, 0f, PATTERN_SAND, 0f);
    }

    public static ShadingAttribute body() {
        return new ShadingAttribute(0f, 18f, 0.12f, 0.35f, PATTERN_NONE, 0f);
    }

    public static ShadingAttribute matte() {
        return new ShadingAttribute(0f, 12f, 0.05f, 0f, PATTERN_NONE, 0f);
    }

    public static ShadingAttribute glossy() {
        return new ShadingAttribute(0f, 80f, 0.6f, 0.15f, PATTERN_NONE, 0f);
    }

    @Override
    public Attribute copy() {
        return new ShadingAttribute(reflectivity, shininess, specular, rim, pattern, roughness);
    }

    @Override
    public int hashCode() {
        int h = (int) type;
        h = 31 * h + Float.floatToIntBits(reflectivity);
        h = 31 * h + Float.floatToIntBits(shininess);
        h = 31 * h + Float.floatToIntBits(specular);
        h = 31 * h + Float.floatToIntBits(rim);
        h = 31 * h + pattern;
        return h;
    }

    @Override
    public int compareTo(Attribute o) {
        if (type != o.type) return Long.compare(type, o.type);
        ShadingAttribute other = (ShadingAttribute) o;
        int c = Float.compare(reflectivity, other.reflectivity);
        if (c != 0) return c;
        c = Float.compare(shininess, other.shininess);
        if (c != 0) return c;
        c = Float.compare(specular, other.specular);
        if (c != 0) return c;
        c = Float.compare(rim, other.rim);
        return c != 0 ? c : Integer.compare(pattern, other.pattern);
    }
}
