package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.EllipseShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;

/** Flat decals on the floor (shadows, rings) and small helper shapes. All are unit-sized and scaled per instance. */
final class MarkerModels {
    private MarkerModels() {}

    /** Filled disc of radius 1 lying on the floor, semi-transparent. */
    static Model disc(Color color, float opacity) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("disc", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, decal(color, opacity));
        EllipseShapeBuilder.build(part, 1f, 32, 0f, 0f, 0f, 0f, 1f, 0f);
        return builder.end();
    }

    /** Ring with outer radius 1 and the given inner radius, lying on the floor. */
    static Model ring(Color color, float opacity, float innerRadius) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("ring", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal, decal(color, opacity));
        EllipseShapeBuilder.build(part, 2f, 2f, innerRadius * 2f, innerRadius * 2f, 40, 0f, 0f, 0f, 0f, 1f, 0f);
        return builder.end();
    }

    static Model sphere(Color color, float diameter) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("sphere", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new Material(ColorAttribute.createDiffuse(color)));
        SphereShapeBuilder.build(part, diameter, diameter, diameter, 8, 6);
        return builder.end();
    }

    /** A see-through sphere of the given diameter that never hides anything (the ball's trail). */
    static Model glow(Color color, float opacity, float diameter) {
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("glow", GL20.GL_TRIANGLES, Usage.Position | Usage.Normal,
                new Material(ColorAttribute.createDiffuse(color), new BlendingAttribute(opacity),
                        new DepthTestAttribute(GL20.GL_LEQUAL, false)));
        SphereShapeBuilder.build(part, diameter, diameter, diameter, 10, 8);
        return builder.end();
    }

    private static Material decal(Color color, float opacity) {
        return new Material(ColorAttribute.createDiffuse(color), new BlendingAttribute(opacity),
                // Decals are drawn over the floor and never hide anything: no depth writes.
                new DepthTestAttribute(GL20.GL_LEQUAL, false), IntAttribute.createCullFace(GL20.GL_NONE));
    }
}
