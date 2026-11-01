package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.RandomXS128;

/**
 * Builds the spectators as one big mesh whose vertices carry animation data in the texture
 * coordinates: u = the person's random phase (0..1), v = the body part (0 legs, 1 body and
 * head, 2 left arm, 3 right arm, 4 flag). The arena shader moves the parts on the GPU: breathing
 * and bobbing, standing up and raising the arms when the crowd cheers, the wave, waving flags.
 */
final class CrowdBuilder {
    static final float PART_LEGS = 0f;
    static final float PART_BODY = 1f;
    static final float PART_ARM_LEFT = 2f;
    static final float PART_ARM_RIGHT = 3f;
    static final float PART_FLAG = 4f;
    /** Short indices: a part holds at most 65536 vertices. */
    private static final int MAX_VERTICES_PER_PART = 65_000;
    private static final int VERTICES_PER_BOX = 16;

    private final ModelBuilder builder;
    private final Material material;
    private final RandomXS128 random;
    private final Color[] shirts;
    private final Color[] skins;
    private final Color[] hair;
    private final Color[] trousers;
    private MeshPartBuilder part;
    private int vertices;
    private int parts;
    private int count;

    CrowdBuilder(ModelBuilder builder, Material material, long seed, Color[] shirts, Color[] skins, Color[] hair, Color[] trousers) {
        this.builder = builder;
        this.material = material;
        this.random = new RandomXS128(seed);
        this.shirts = shirts;
        this.skins = skins;
        this.hair = hair;
        this.trousers = trousers;
    }

    int count() {
        return count;
    }

    /**
     * A seated spectator. {@code fx, fz} is the unit direction they face (towards the court);
     * {@code seatY} is the top of the seat.
     */
    void spectator(float x, float seatY, float z, float fx, float fz, float flagChance, Color teamColor) {
        count++;
        float phase = random.nextFloat();
        float scale = 0.92f + random.nextFloat() * 0.16f;
        Color shirt = random.nextFloat() < 0.35f && teamColor != null ? teamColor : shirts[random.nextInt(shirts.length)];
        Color skin = skins[random.nextInt(skins.length)];
        Color hairColor = hair[random.nextInt(hair.length)];
        boolean shortSleeves = random.nextFloat() < 0.6f;
        // Lateral axis (to the person's right).
        float lx = -fz;
        float lz = fx;
        // Legs: thighs on the seat and shins down in front of it.
        box(x + fx * 0.22f, seatY + 0.07f, z + fz * 0.22f, 0.34f, 0.14f, 0.36f, fx, fz, trousers[random.nextInt(trousers.length)], phase, PART_LEGS);
        box(x + fx * 0.4f, seatY - 0.2f, z + fz * 0.4f, 0.3f, 0.42f, 0.13f, fx, fz, trousers[random.nextInt(trousers.length)], phase, PART_LEGS);
        // Torso, head, hair or cap.
        float torsoH = 0.5f * scale;
        box(x, seatY + 0.05f + torsoH / 2f, z, 0.4f * scale, torsoH, 0.24f, fx, fz, shirt, phase, PART_BODY);
        float headY = seatY + 0.05f + torsoH + 0.13f;
        box(x + fx * 0.02f, headY, z + fz * 0.02f, 0.19f, 0.22f, 0.2f, fx, fz, skin, phase, PART_BODY);
        if (random.nextFloat() < 0.2f) {
            Color cap = shirts[random.nextInt(shirts.length)];
            box(x, headY + 0.12f, z, 0.21f, 0.06f, 0.22f, fx, fz, cap, phase, PART_BODY);
            box(x + fx * 0.14f, headY + 0.1f, z + fz * 0.14f, 0.19f, 0.02f, 0.1f, fx, fz, cap, phase, PART_BODY);
        } else {
            box(x - fx * 0.02f, headY + 0.1f, z - fz * 0.02f, 0.21f, 0.07f, 0.22f, fx, fz, hairColor, phase, PART_BODY);
        }
        // Arms hang at the sides (the shader raises them).
        Color arm = shortSleeves ? skin : shirt;
        float armY = seatY + 0.05f + torsoH - 0.2f;
        float side = 0.25f * scale;
        box(x - lx * side + fx * 0.05f, armY, z - lz * side + fz * 0.05f, 0.1f, 0.42f, 0.1f, fx, fz, arm, phase, PART_ARM_LEFT);
        box(x + lx * side + fx * 0.05f, armY, z + lz * side + fz * 0.05f, 0.1f, 0.42f, 0.1f, fx, fz, arm, phase, PART_ARM_RIGHT);
        if (teamColor != null && random.nextFloat() < flagChance) {
            // A flag on a short pole, held up.
            box(x + lx * side, armY + 0.75f, z + lz * side, 0.02f, 0.7f, 0.02f, fx, fz, Color.LIGHT_GRAY, phase, PART_FLAG);
            box(x + lx * (side + 0.25f), armY + 0.95f, z + lz * (side + 0.25f), 0.48f, 0.32f, 0.02f, fx, fz, teamColor, phase, PART_FLAG);
        }
    }

    /**
     * Box oriented to face (fx, fz): width along the lateral axis, depth along the facing. Only
     * the top, the front and the two sides are built; the back (towards the stand) and the
     * bottom are never seen.
     */
    private void box(float cx, float cy, float cz, float width, float height, float depth, float fx, float fz,
                     Color color, float phase, float code) {
        boolean alongX = Math.abs(fz) > Math.abs(fx);
        float hx = (alongX ? width : depth) / 2f;
        float hy = height / 2f;
        float hz = (alongX ? depth : width) / 2f;
        if (part == null || vertices + VERTICES_PER_BOX > MAX_VERTICES_PER_PART) {
            part = builder.part("crowd-" + parts++, GL20.GL_TRIANGLES, Usage.Position | Usage.Normal | Usage.ColorPacked
                    | Usage.TextureCoordinates, material);
            vertices = 0;
        }
        vertices += VERTICES_PER_BOX;
        part.setColor(color);
        part.setUVRange(phase, code, phase, code);
        face(cx, cy, cz, hx, hy, hz, 0, 1, 0);
        if (alongX) {
            face(cx, cy, cz, hx, hy, hz, 0, 0, fz > 0 ? 1 : -1);
            face(cx, cy, cz, hx, hy, hz, 1, 0, 0);
            face(cx, cy, cz, hx, hy, hz, -1, 0, 0);
        } else {
            face(cx, cy, cz, hx, hy, hz, fx > 0 ? 1 : -1, 0, 0);
            face(cx, cy, cz, hx, hy, hz, 0, 0, 1);
            face(cx, cy, cz, hx, hy, hz, 0, 0, -1);
        }
    }

    /** One face of an axis-aligned box, with outward normal (nx, ny, nz) along an axis, counter-clockwise from outside. */
    private void face(float cx, float cy, float cz, float hx, float hy, float hz, int nx, int ny, int nz) {
        // Tangents u, v with u x v = n.
        float ux, uy, uz, vx, vy, vz;
        if (nx != 0) {
            ux = 0; uy = nx > 0 ? hy : 0; uz = nx > 0 ? 0 : hz;
            vx = 0; vy = nx > 0 ? 0 : hy; vz = nx > 0 ? hz : 0;
        } else if (ny != 0) {
            ux = 0; uy = 0; uz = hz;
            vx = hx; vy = 0; vz = 0;
        } else {
            ux = nz > 0 ? hx : 0; uy = nz > 0 ? 0 : hy; uz = 0;
            vx = nz > 0 ? 0 : hx; vy = nz > 0 ? hy : 0; vz = 0;
        }
        float px = cx + nx * hx;
        float py = cy + ny * hy;
        float pz = cz + nz * hz;
        part.rect(px - ux - vx, py - uy - vy, pz - uz - vz,
                px + ux - vx, py + uy - vy, pz + uz - vz,
                px + ux + vx, py + uy + vy, pz + uz + vz,
                px - ux + vx, py - uy + vy, pz - uz + vz,
                nx, ny, nz);
    }
}
