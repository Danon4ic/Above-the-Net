package com.volleyball.game.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InputControllerTest {
    /** Scriptable keyboard. */
    static final class FakeKeys implements KeyState {
        final Set<PlayerAction> down = EnumSet.noneOf(PlayerAction.class);
        final Set<PlayerAction> pressed = EnumSet.noneOf(PlayerAction.class);

        @Override
        public boolean isDown(PlayerAction action) {
            return down.contains(action);
        }

        @Override
        public boolean justPressed(PlayerAction action) {
            return pressed.contains(action);
        }
    }

    private final FakeKeys keys = new FakeKeys();
    private final InputController controller = new InputController(keys);
    private final VolleyballPlayer player = new VolleyballPlayer(0, "P", 1, PlayerRole.SETTER, CourtSide.HOME, 1.9f,
            new PlayerAttributes());
    private final MoveIntent intent = new MoveIntent();

    @Test
    void movementIsRelativeToTheCamera() {
        keys.down.add(PlayerAction.MOVE_FORWARD);
        controller.fillIntent(intent, player, new Vector3(1f, -0.5f, 0f), 0);
        assertEquals(1f, intent.direction.x, 1e-6f);
        assertEquals(0f, intent.direction.y, 1e-6f);

        // Camera looking towards -Z: forward is -Z and "right" is +X... rotated accordingly.
        keys.down.clear();
        keys.down.add(PlayerAction.MOVE_RIGHT);
        controller.fillIntent(intent, player, new Vector3(0f, -0.3f, -1f), 0);
        assertEquals(1f, intent.direction.x, 1e-6f);
        assertEquals(0f, intent.direction.y, 1e-6f);
    }

    @Test
    void diagonalInputIsNotFaster() {
        keys.down.addAll(Set.of(PlayerAction.MOVE_FORWARD, PlayerAction.MOVE_LEFT, PlayerAction.SPRINT));
        controller.fillIntent(intent, player, new Vector3(1f, 0f, 0f), 0);
        assertEquals(1f, intent.direction.len(), 1e-5f);
        assertTrue(intent.sprint);
    }

    @Test
    void jumpPressedSlightlyEarlyIsBufferedUntilPossible() {
        player.setAirborne(true);
        keys.pressed.add(PlayerAction.JUMP);
        controller.poll(1.0);
        keys.pressed.clear();
        controller.fillIntent(intent, player, Vector3.X, 1.05);
        assertFalse(intent.jump, "cannot jump while airborne");

        player.setAirborne(false);
        controller.fillIntent(intent, player, Vector3.X, 1.10);
        assertTrue(intent.jump, "buffered press executes on landing");
        controller.fillIntent(intent, player, Vector3.X, 1.11);
        assertFalse(intent.jump, "a press is consumed once");
    }

    @Test
    void staleBufferedPressesExpire() {
        keys.pressed.add(PlayerAction.JUMP);
        controller.poll(1.0);
        controller.fillIntent(intent, player, Vector3.X, 1.0 + InputController.BUFFER_WINDOW + 0.05);
        assertFalse(intent.jump);
    }

    @Test
    void keyBindingsRoundTripByNameAndIgnoreGarbage() {
        KeyBindings custom = KeyBindings.defaults();
        custom.bind(PlayerAction.JUMP, Input.Keys.J);
        Map<String, String> names = custom.toNames();
        assertEquals("J", names.get("JUMP"));
        assertEquals(Input.Keys.J, KeyBindings.fromNames(names).key(PlayerAction.JUMP));

        KeyBindings broken = KeyBindings.fromNames(Map.of("JUMP", "NotAKey", "FLY", "F"));
        assertEquals(Input.Keys.SPACE, broken.key(PlayerAction.JUMP), "invalid key falls back to default");
    }

    /** Holds the jump key for {@code seconds} (polled at 60 Hz) and returns the intent at release. */
    private MoveIntent jumpAfterHolding(double seconds) {
        InputController c = new InputController(keys);
        double now = 0;
        keys.pressed.add(PlayerAction.JUMP);
        keys.down.add(PlayerAction.JUMP);
        c.poll(now);
        keys.pressed.clear();
        MoveIntent charging = new MoveIntent();
        while (now < seconds) {
            now += 1.0 / 60.0;
            c.poll(now);
            c.fillIntent(charging, player, Vector3.X, now);
            assertFalse(charging.jump, "no jump while the key is held");
        }
        assertTrue(charging.jumpChargeLevel > 0f, "the player sinks while charging");
        keys.down.remove(PlayerAction.JUMP);
        c.poll(now + 0.01);
        MoveIntent release = new MoveIntent();
        c.fillIntent(release, player, Vector3.X, now + 0.01);
        return release;
    }

    @Test
    void holdingJumpLongerJumpsHigher() {
        MoveIntent tap = jumpAfterHolding(0.02);
        MoveIntent full = jumpAfterHolding(0.6);
        assertTrue(tap.jump && full.jump, "jumps on release");
        assertEquals(InputController.TAP_JUMP_FACTOR, tap.jumpHeightFactor, 0.05f);
        assertEquals(InputController.CHARGED_JUMP_FACTOR, full.jumpHeightFactor, 1e-4f);
        MoveIntent half = jumpAfterHolding(InputController.FULL_CHARGE_TIME / 2);
        assertTrue(half.jumpHeightFactor > tap.jumpHeightFactor && half.jumpHeightFactor < full.jumpHeightFactor);
    }
}
