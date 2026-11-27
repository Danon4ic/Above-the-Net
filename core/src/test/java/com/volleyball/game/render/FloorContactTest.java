package com.volleyball.game.render;

import com.badlogic.gdx.math.Matrix4;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorContactTest {
    @Test
    void rolledShoeCanClipAtItsEdgeWhileTheCenterOfTheSoleIsAboveFloor() {
        FloorContact sole = FloorContact.box(0.05f, -0.03f, 0f, 0.275f, 0.012f, 0.12f);
        Matrix4 pose = new Matrix4();
        float diagonal = (float) Math.sqrt(0.5);
        pose.val[Matrix4.M11] = diagonal;
        pose.val[Matrix4.M12] = -diagonal;
        pose.val[Matrix4.M13] = 0.04f;
        assertTrue(0.04f - 0.036f * diagonal > 0f, "the old sole-center probe clears the ground");
        assertEquals(0.04f - (0.036f + 0.06f) * diagonal, sole.lowestY(pose), 0.0001f);
        assertTrue(sole.lowestY(pose) < 0f);
    }

    @Test
    void scaledHeadEnvelopeDoesNotGrowWhenTheModelRolls() {
        FloorContact head = FloorContact.sphere(0f, 0f, 0f, 0.23f);
        Matrix4 pose = new Matrix4();
        pose.val[Matrix4.M11] = 0f;
        pose.val[Matrix4.M12] = -1.1f;
        pose.val[Matrix4.M13] = 0.4f;
        assertEquals(0.4f - 0.115f * 1.1f, head.lowestY(pose), 0.0001f);
    }
}
