package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.Matrix4;

/**
 * Accumulates many small vertex-coloured primitives into a few mesh parts of one material.
 * Merging static geometry this way keeps draw calls low (thousands of crowd boxes become a
 * handful of draws). A new part is started before the 16-bit index limit could be reached.
 */
final class GeometryBuilder {
    static final long ATTRIBUTES = Usage.Position | Usage.Normal | Usage.ColorPacked;
    /**
     * Vertices per part. ModelBuilder may pack several parts into one mesh, so this stays far below
     * the 16-bit index limit (a box uses 24 vertices, a small sphere or cylinder ~70-150).
     */
    private static final int MAX_VERTICES_PER_PART = 12_000;

    private final ModelBuilder modelBuilder;
    private final String partPrefix;
    private final Material material;
    private final Matrix4 transform = new Matrix4();
    private MeshPartBuilder part;
    private int partVertices;
    private int partCount;

    GeometryBuilder(ModelBuilder modelBuilder, String partPrefix, Material material) {
        this.modelBuilder = modelBuilder;
        this.partPrefix = partPrefix;
        this.material = material;
    }

    /** Axis-aligned box given by its centre and full sizes. */
    GeometryBuilder box(float cx, float cy, float cz, float width, float height, float depth, Color color) {
        ensureCapacity(24).setColor(color);
        BoxShapeBuilder.build(part, cx, cy, cz, width, height, depth);
        return this;
    }

    /** Box spanning [x0,x1] x [y0,y1] x [z0,z1]. */
    GeometryBuilder boxBetween(float x0, float y0, float z0, float x1, float y1, float z1, Color color) {
        return box((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f,
                Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), color);
    }

    /** Box of the given sizes centred at the origin of {@code boxTransform} (rotated, translated). */
    GeometryBuilder transformedBox(Matrix4 boxTransform, float width, float height, float depth, Color color) {
        ensureCapacity(24).setColor(color);
        part.setVertexTransform(boxTransform);
        BoxShapeBuilder.build(part, 0f, 0f, 0f, width, height, depth);
        part.setVertexTransform(null);
        return this;
    }

    /** Vertical cylinder standing on {@code baseY}. */
    GeometryBuilder verticalCylinder(float x, float baseY, float z, float diameter, float height, Color color) {
        ensureCapacity(4 * 16 + 8).setColor(color);
        part.setVertexTransform(transform.setToTranslation(x, baseY + height / 2f, z));
        CylinderShapeBuilder.build(part, diameter, height, diameter, 16);
        part.setVertexTransform(null);
        return this;
    }

    GeometryBuilder sphere(float x, float y, float z, float diameter, Color color) {
        ensureCapacity(13 * 11).setColor(color);
        part.setVertexTransform(transform.setToTranslation(x, y, z));
        SphereShapeBuilder.build(part, diameter, diameter, diameter, 12, 10);
        part.setVertexTransform(null);
        return this;
    }

    /** Ellipsoid with the given full sizes along x, y and z. */
    GeometryBuilder ellipsoid(float x, float y, float z, float width, float height, float depth, Color color) {
        ensureCapacity(15 * 13).setColor(color);
        part.setVertexTransform(transform.setToTranslation(x, y, z));
        SphereShapeBuilder.build(part, width, height, depth, 14, 12);
        part.setVertexTransform(null);
        return this;
    }

    private static final int FRUSTUM_SEGMENTS = 14;
    private final MeshPartBuilder.VertexInfo vertex = new MeshPartBuilder.VertexInfo();

    /**
     * A vertical tapered segment with elliptical cross-sections (a limb, a trunk section) between
     * {@code yTop} and {@code yBottom}, centred on (cx, cz): full sizes {@code topX × topZ} at the
     * top and {@code bottomX × bottomZ} at the bottom, both ends capped. Smooth normals, so the
     * segment reads as a rounded body part rather than a box.
     */
    GeometryBuilder frustum(float cx, float cz, float yTop, float yBottom, float topX, float topZ,
                            float bottomX, float bottomZ, Color color) {
        int n = FRUSTUM_SEGMENTS;
        ensureCapacity(4 * (n + 1) + 2).setColor(color);
        float height = Math.abs(yTop - yBottom);
        float[] cos = new float[n + 1];
        float[] sin = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2.0 * i / n;
            cos[i] = (float) Math.cos(a);
            sin[i] = (float) Math.sin(a);
        }
        short[] top = new short[n + 1];
        short[] bottom = new short[n + 1];
        for (int i = 0; i <= n; i++) {
            float ta = topX / 2f;
            float tb = topZ / 2f;
            float ba = bottomX / 2f;
            float bb = bottomZ / 2f;
            // Outward normal of the ellipse, tilted by the taper (a narrowing segment faces a little up).
            float nx = cos[i] / Math.max(1e-4f, (ta + ba) / 2f);
            float nz = sin[i] / Math.max(1e-4f, (tb + bb) / 2f);
            float radial = (float) Math.sqrt(nx * nx + nz * nz);
            float slope = height < 1e-5f ? 0f : ((ba + bb) - (ta + tb)) / 2f / height;
            vertex.setCol(null).setUV(null);
            vertex.setNor(nx / radial, slope, nz / radial);
            vertex.normal.nor();
            vertex.setPos(cx + ta * cos[i], yTop, cz + tb * sin[i]);
            top[i] = part.vertex(vertex);
            vertex.setPos(cx + ba * cos[i], yBottom, cz + bb * sin[i]);
            bottom[i] = part.vertex(vertex);
        }
        for (int i = 0; i < n; i++) {
            part.triangle(bottom[i], top[i], top[i + 1]);
            part.triangle(bottom[i], top[i + 1], bottom[i + 1]);
        }
        // Caps (flat; joints usually hide them).
        cap(cx, yTop, cz, topX / 2f, topZ / 2f, cos, sin, true);
        cap(cx, yBottom, cz, bottomX / 2f, bottomZ / 2f, cos, sin, false);
        return this;
    }

    private void cap(float cx, float y, float cz, float a, float b, float[] cos, float[] sin, boolean up) {
        int n = FRUSTUM_SEGMENTS;
        vertex.setNor(0f, up ? 1f : -1f, 0f);
        vertex.setPos(cx, y, cz);
        short centre = part.vertex(vertex);
        short[] ring = new short[n + 1];
        for (int i = 0; i <= n; i++) {
            vertex.setPos(cx + a * cos[i], y, cz + b * sin[i]);
            ring[i] = part.vertex(vertex);
        }
        for (int i = 0; i < n; i++) {
            if (up) part.triangle(centre, ring[i + 1], ring[i]);
            else part.triangle(centre, ring[i], ring[i + 1]);
        }
    }

    private MeshPartBuilder ensureCapacity(int vertices) {
        if (part == null || partVertices + vertices > MAX_VERTICES_PER_PART) {
            part = modelBuilder.part(partPrefix + "-" + partCount++, GL20.GL_TRIANGLES, ATTRIBUTES, material);
            partVertices = 0;
        }
        partVertices += vertices;
        return part;
    }
}
