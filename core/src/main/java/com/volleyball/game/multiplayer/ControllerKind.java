package com.volleyball.game.multiplayer;

/** The input device set of a human slot. */
public enum ControllerKind {
    /** WASD + mouse (aim with the cursor). */
    KEYBOARD_MOUSE("Keyboard + mouse (WASD)"),
    /** Arrow keys and the keys around them; aims with the movement keys. */
    KEYBOARD_ARROWS("Keyboard (arrows)"),
    /** A player on another PC; commands arrive over the network. */
    REMOTE("Network");

    public final String label;

    ControllerKind(String label) {
        this.label = label;
    }
}
