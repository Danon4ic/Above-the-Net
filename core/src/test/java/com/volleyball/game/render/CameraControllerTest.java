package com.volleyball.game.render;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The camera controller needs no OpenGL context, only libGDX's native math, so it runs headless. */
class CameraControllerTest {
    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void switchingModesGlidesSmoothlyAndConverges() {
        CameraController controller = new CameraController(50f, 1280, 720);
        float startX = controller.eye().x;
        controller.setMode(CameraMode.BEHIND_BASELINE, false);
        controller.update(1f / 60f);
        float afterOneFrame = controller.eye().dst(controller.desiredEye());
        assertTrue(afterOneFrame > 1f, "no instant cut after one frame");
        assertNotEquals(startX, controller.eye().x);
        for (int i = 0; i < 600; i++) controller.update(1f / 60f);
        assertTrue(controller.eye().dst(controller.desiredEye()) < 0.01f);
    }

    @Test
    void smoothingIsFrameRateIndependent() {
        CameraController slow = new CameraController(50f, 1280, 720);
        CameraController fast = new CameraController(50f, 1280, 720);
        slow.setMode(CameraMode.OVERHEAD, false);
        fast.setMode(CameraMode.OVERHEAD, false);
        for (int i = 0; i < 30; i++) slow.update(1f / 30f);
        for (int i = 0; i < 144; i++) fast.update(1f / 144f);
        assertEquals(slow.eye().x, fast.eye().x, 0.05f);
        assertEquals(slow.eye().y, fast.eye().y, 0.05f);
        assertEquals(slow.eye().z, fast.eye().z, 0.05f);
    }

    @Test
    void orbitStartsFromCurrentViewAndClampsPitch() {
        CameraController controller = new CameraController(50f, 1280, 720);
        float distanceBefore = controller.eye().dst(1.5f, 0f, 0f);
        controller.orbit(0f, 0f);
        assertEquals(CameraMode.FREE_ORBIT, controller.mode());
        assertEquals(distanceBefore, controller.orbitDistance(), 0.01f);

        controller.orbit(0f, 100_000f);
        assertEquals(85f * MathUtils.degreesToRadians, controller.orbitPitch(), 1e-4f);
        controller.orbit(0f, -100_000f);
        assertEquals(5f * MathUtils.degreesToRadians, controller.orbitPitch(), 1e-4f);
        for (int i = 0; i < 100; i++) controller.zoom(10f);
        assertEquals(45f, controller.orbitDistance(), 1e-4f);
    }

    @Test
    void presetCamerasShowTheWholeCourt() {
        CameraController controller = new CameraController(50f, 1280, 720);
        for (CameraMode mode : new CameraMode[]{CameraMode.TV_BROADCAST, CameraMode.SIDELINE,
                CameraMode.BEHIND_BASELINE, CameraMode.OVERHEAD}) {
            controller.setMode(mode, true);
            assertTrue(controller.camera().direction.y < 0f, mode + " looks down");
            for (float x : new float[]{-9f, 9f}) {
                for (float z : new float[]{-4.5f, 4.5f}) {
                    assertTrue(controller.camera().frustum.pointInFrustum(x, 0f, z), mode + " sees corner " + x + "," + z);
                }
            }
            assertTrue(controller.camera().frustum.pointInFrustum(0f, 2.43f, 0f), mode + " sees the net");
        }
        controller.setMode(CameraMode.TV_BROADCAST, true);
        assertTrue(controller.camera().direction.x > 0f, "broadcast camera is behind the home end line");
    }

    private static void settle(CameraController c) {
        for (int i = 0; i < 600; i++) c.update(1f / 60f);
    }

    @Test
    void thirdPersonCameraSitsDirectlyBehindThePlayerFacingTheNet() {
        CameraController c = new CameraController(50f, 1280, 720);
        com.badlogic.gdx.math.Vector3 player = new com.badlogic.gdx.math.Vector3(-5f, 0f, 3f);
        c.follow(player, 1f);
        c.setMode(CameraMode.THIRD_PERSON, true);
        settle(c);
        assertEquals(player.z, c.eye().z, 1e-3f, "not offset sideways");
        assertTrue(c.eye().x < player.x, "behind the home player");
        com.badlogic.gdx.math.Vector3 forward = c.controlForward(new com.badlogic.gdx.math.Vector3());
        assertEquals(1f, forward.x, 1e-4f, "forward = towards the net");
    }

    @Test
    void rotatingTheThirdPersonCameraKeepsItOnThePlayerAndTurnsTheControls() {
        CameraController c = new CameraController(50f, 1280, 720);
        com.badlogic.gdx.math.Vector3 player = new com.badlogic.gdx.math.Vector3(-4f, 0f, -1f);
        c.follow(player, 1f);
        c.setMode(CameraMode.THIRD_PERSON, true);
        // A quarter turn: 90 degrees of yaw.
        c.orbit(-MathUtils.HALF_PI / 0.005f, 0f);
        assertEquals(CameraMode.THIRD_PERSON, c.mode(), "stays a follow camera");
        settle(c);
        float horizontal = (float) Math.hypot(c.eye().x - player.x, c.eye().z - player.z);
        assertEquals(c.followDistance() * MathUtils.cos(c.followPitch()), horizontal, 0.01f);

        com.badlogic.gdx.math.Vector3 forward = c.controlForward(new com.badlogic.gdx.math.Vector3());
        com.badlogic.gdx.math.Vector3 look = new com.badlogic.gdx.math.Vector3(c.camera().direction.x, 0f, c.camera().direction.z).nor();
        assertEquals(1f, forward.dot(look), 1e-3f, "movement follows where the camera looks");
        assertEquals(0f, forward.x, 1e-3f, "turned a quarter");

        // The camera follows the player when they move.
        player.set(-2f, 0f, 2f);
        c.follow(player, 1f);
        settle(c);
        horizontal = (float) Math.hypot(c.eye().x - player.x, c.eye().z - player.z);
        assertEquals(c.followDistance() * MathUtils.cos(c.followPitch()), horizontal, 0.01f);

        c.zoom(5f);
        assertTrue(c.followDistance() > CameraController.FOLLOW_DEFAULT_DISTANCE);
        c.resetFollowView();
        assertEquals(0f, c.followYaw(), 1e-6f);
    }

    @Test
    void controlsAreTakenFromTheTargetViewNotTheLaggingCamera() {
        CameraController c = new CameraController(50f, 1280, 720);
        c.setMode(CameraMode.SIDELINE, false); // still gliding from the TV view
        c.update(1f / 60f);
        com.badlogic.gdx.math.Vector3 forward = c.controlForward(new com.badlogic.gdx.math.Vector3());
        assertTrue(forward.z < -0.95f, "sideline camera looks across the court (-Z) immediately: " + forward);
    }

    @Test
    void switchingSidesResetsTheFollowView() {
        CameraController c = new CameraController(50f, 1280, 720);
        c.follow(new com.badlogic.gdx.math.Vector3(-5f, 0f, 0f), 1f);
        c.setMode(CameraMode.THIRD_PERSON, true);
        c.orbit(200f, 0f);
        c.follow(new com.badlogic.gdx.math.Vector3(5f, 0f, 0f), -1f);
        assertEquals(MathUtils.PI, Math.abs(c.followYaw()), 1e-5f, "away player looks towards -X");
    }
}
