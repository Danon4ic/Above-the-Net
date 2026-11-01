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

    private MeshPartBuilder ensureCapacity(int vertices) {
        if (part == null || partVertices + vertices > MAX_VERTICES_PER_PART) {
            part = modelBuilder.part(partPrefix + "-" + partCount++, GL20.GL_TRIANGLES, ATTRIBUTES, material);
            partVertices = 0;
        }
        partVertices += vertices;
        return part;
    }
}
