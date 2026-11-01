package com.volleyball.game.input;

import com.badlogic.gdx.Input;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapping of actions to keyboard keys or mouse buttons. Keys use libGDX key codes; mouse buttons
 * are encoded as {@code MOUSE_BASE - button}. Saved by name, e.g. {@code "W"}, {@code "Space"},
 * {@code "Mouse Left"}, so the settings file stays readable and editable.
 */
public final class KeyBindings {
    /** Codes at or below this value are mouse buttons. */
    public static final int MOUSE_BASE = -100;
    private static final String[] MOUSE_NAMES = {"Mouse Left", "Mouse Right", "Mouse Middle", "Mouse Back", "Mouse Forward"};

    private final EnumMap<PlayerAction, Integer> keys = new EnumMap<>(PlayerAction.class);

    public static KeyBindings defaults() {
        KeyBindings bindings = new KeyBindings();
        bindings.bind(PlayerAction.MOVE_FORWARD, Input.Keys.W);
        bindings.bind(PlayerAction.MOVE_BACK, Input.Keys.S);
        bindings.bind(PlayerAction.MOVE_LEFT, Input.Keys.A);
        bindings.bind(PlayerAction.MOVE_RIGHT, Input.Keys.D);
        bindings.bind(PlayerAction.JUMP, Input.Keys.SPACE);
        bindings.bind(PlayerAction.SPRINT, Input.Keys.SHIFT_LEFT);
        bindings.bind(PlayerAction.CROUCH, Input.Keys.CONTROL_LEFT);
        bindings.bind(PlayerAction.SWITCH_PLAYER, Input.Keys.TAB);
        bindings.bind(PlayerAction.PRIMARY_ACTION, mouse(Input.Buttons.LEFT));
        bindings.bind(PlayerAction.SECONDARY_ACTION, mouse(Input.Buttons.RIGHT));
        bindings.bind(PlayerAction.VARIANT_PREVIOUS, Input.Keys.Q);
        bindings.bind(PlayerAction.VARIANT_NEXT, Input.Keys.E);
        bindings.bind(PlayerAction.PAUSE, Input.Keys.ESCAPE);
        return bindings;
    }

    /**
     * Second person on the same keyboard (local multiplayer): arrows to move and the keys
     * around them on the right-hand side, so both people fit on one keyboard without a mouse.
     */
    public static KeyBindings player2Defaults() {
        KeyBindings bindings = new KeyBindings();
        bindings.bind(PlayerAction.MOVE_FORWARD, Input.Keys.UP);
        bindings.bind(PlayerAction.MOVE_BACK, Input.Keys.DOWN);
        bindings.bind(PlayerAction.MOVE_LEFT, Input.Keys.LEFT);
        bindings.bind(PlayerAction.MOVE_RIGHT, Input.Keys.RIGHT);
        bindings.bind(PlayerAction.JUMP, Input.Keys.CONTROL_RIGHT);
        bindings.bind(PlayerAction.SPRINT, Input.Keys.SHIFT_RIGHT);
        bindings.bind(PlayerAction.CROUCH, Input.Keys.COMMA);
        bindings.bind(PlayerAction.SWITCH_PLAYER, Input.Keys.APOSTROPHE);
        bindings.bind(PlayerAction.PRIMARY_ACTION, Input.Keys.SLASH);
        bindings.bind(PlayerAction.SECONDARY_ACTION, Input.Keys.PERIOD);
        bindings.bind(PlayerAction.VARIANT_PREVIOUS, Input.Keys.LEFT_BRACKET);
        bindings.bind(PlayerAction.VARIANT_NEXT, Input.Keys.RIGHT_BRACKET);
        bindings.bind(PlayerAction.PAUSE, Input.Keys.ESCAPE);
        return bindings;
    }

    public static int mouse(int button) {
        return MOUSE_BASE - button;
    }

    public static boolean isMouse(int code) {
        return code <= MOUSE_BASE;
    }

    public static int mouseButton(int code) {
        return MOUSE_BASE - code;
    }

    /** Readable name of a key or mouse button code. */
    public static String nameOf(int code) {
        if (isMouse(code)) {
            int button = mouseButton(code);
            return button < MOUSE_NAMES.length ? MOUSE_NAMES[button] : "Mouse " + button;
        }
        return Input.Keys.toString(code);
    }

    /** Code for a name produced by {@link #nameOf}, or -1 if unknown. */
    public static int codeOf(String name) {
        if (name == null) return -1;
        for (int i = 0; i < MOUSE_NAMES.length; i++) if (MOUSE_NAMES[i].equalsIgnoreCase(name)) return mouse(i);
        return Input.Keys.valueOf(name);
    }

    public void bind(PlayerAction action, int keyCode) {
        keys.put(action, keyCode);
    }

    public int key(PlayerAction action) {
        Integer key = keys.get(action);
        return key == null ? Input.Keys.UNKNOWN : key;
    }

    /** Key names by action name, for saving. */
    public Map<String, String> toNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<PlayerAction, Integer> entry : keys.entrySet()) {
            names.put(entry.getKey().name(), nameOf(entry.getValue()));
        }
        return names;
    }

    /** Applies saved names over the defaults, ignoring unknown actions or keys. */
    public static KeyBindings fromNames(Map<String, String> names) {
        return fromNames(names, defaults());
    }

    /** Applies saved names over the given defaults (e.g. {@link #player2Defaults()}). */
    public static KeyBindings fromNames(Map<String, String> names, KeyBindings bindings) {
        if (names == null) return bindings;
        for (Map.Entry<String, String> entry : names.entrySet()) {
            PlayerAction action;
            try {
                action = PlayerAction.valueOf(entry.getKey());
            } catch (IllegalArgumentException unknownAction) {
                continue;
            }
            int key = codeOf(entry.getValue());
            if (key > 0 || isMouse(key)) bindings.bind(action, key);
        }
        return bindings;
    }
}
