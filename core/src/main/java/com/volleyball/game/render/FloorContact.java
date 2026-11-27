package com.volleyball.game.render;

import com.badlogic.gdx.math.Matrix4;

/** Local-space surfaces of the player model that can touch the floor. */
record FloorContact(float x, float y, float z, float halfX, float halfY, float halfZ, boolean sphere) {
    static FloorContact box(float x, float y, float z, float width, float height, float depth) {
        return new FloorContact(x, y, z, width * 0.5f, height * 0.5f, depth * 0.5f, false);
    }

    static FloorContact sphere(float x, float y, float z, float diameter) {
        return new FloorContact(x, y, z, diameter * 0.5f, 0f, 0f, true);
    }

    float lowestY(Matrix4 transform) {
        float[] m = transform.val;
        float ax = m[Matrix4.M10];
        float ay = m[Matrix4.M11];
        float az = m[Matrix4.M12];
        float centerY = ax * x + ay * y + az * z + m[Matrix4.M13];
        float radius = sphere ? halfX * (float) Math.sqrt(ax * ax + ay * ay + az * az)
                : Math.abs(ax) * halfX + Math.abs(ay) * halfY + Math.abs(az) * halfZ;
        return centerY - radius;
    }
}
