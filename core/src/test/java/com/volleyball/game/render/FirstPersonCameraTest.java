package com.volleyball.game.render;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** First person: the eyes at the head, looking along the view's heading (towards the net by default). */
class FirstPersonCameraTest {
    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void theEyesAreAtTheHeadAndLookAtTheNet() {
        CameraController c = new CameraController(70f, 1280, 720);
        c.setMode(CameraMode.FIRST_PERSON, true);
        c.setEyeHeight(1.95f);
        for (int i = 0; i < 120; i++) {
            c.follow(new Vector3(-3f + i * 0.01f, i % 30 < 15 ? 0.4f : 0f, 1f), 1f);
            c.update(1f / 60f);
        }
        Vector3 eye = c.camera().position;
        Vector3 dir = c.camera().direction;
        System.out.println("eye " + eye + " dir " + dir);
        assertEquals(1.81f, eye.y, 0.5f);
        assertTrue(dir.x > 0.9f, "looking at the net: " + dir);
        assertTrue(Math.abs(dir.y) < 0.2f, "level: " + dir);
    }
}
