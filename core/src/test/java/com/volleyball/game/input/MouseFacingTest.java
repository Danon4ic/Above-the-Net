package com.volleyball.game.input;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Facing the mouse: the body turns to the cursor's floor point and the feet shuffle. */
class MouseFacingTest {
    private final InputControllerTest.FakeKeys keys = new InputControllerTest.FakeKeys();
    private final InputController controller = new InputController(keys);
    private final VolleyballPlayer player = new VolleyballPlayer(0, "P", 1, PlayerRole.OPPOSITE, CourtSide.HOME, 1.9f,
            new PlayerAttributes());
    private final MoveIntent intent = new MoveIntent();

    @Test
    void theBodyFacesTheCursorWhileMovingSideways() {
        player.placeAt(-1f, 0f);
        controller.setMouseFacing(true);
        controller.setFacingTarget(-1f, 4f, true); // the cursor to the player's side (+Z)
        keys.down.add(PlayerAction.MOVE_RIGHT);
        controller.fillIntent(intent, player, new Vector3(1f, -0.4f, 0f), 0);
        assertFalse(intent.faceMovement, "the feet shuffle, the body keeps facing the cursor");
        // Yaw convention: direction (cos, -sin); a target at +Z is yaw -pi/2.
        assertEquals((float) -Math.PI / 2f, intent.facingYaw, 1e-4f);
    }

    @Test
    void aSprintTurnsTheBodyIntoTheRunAndTheSettingCanBeOff() {
        player.placeAt(-1f, 0f);
        controller.setMouseFacing(true);
        controller.setFacingTarget(-1f, 4f, true);
        keys.down.add(PlayerAction.MOVE_FORWARD);
        keys.down.add(PlayerAction.SPRINT);
        controller.fillIntent(intent, player, new Vector3(1f, -0.4f, 0f), 0);
        assertTrue(Float.isNaN(intent.facingYaw), "sprinting: the run decides the facing");
        keys.down.remove(PlayerAction.SPRINT);
        controller.setMouseFacing(false);
        controller.fillIntent(intent, player, new Vector3(1f, -0.4f, 0f), 0);
        assertTrue(Float.isNaN(intent.facingYaw));
        assertTrue(intent.faceMovement, "off: the classic face-the-movement control");
    }

    @Test
    void aCursorRightAtTheFeetDoesNotSpinThePlayer() {
        player.placeAt(-1f, 0f);
        controller.setMouseFacing(true);
        controller.setFacingTarget(-1.2f, 0.1f, true);
        controller.fillIntent(intent, player, new Vector3(1f, -0.4f, 0f), 0);
        assertTrue(Float.isNaN(intent.facingYaw));
    }
}
