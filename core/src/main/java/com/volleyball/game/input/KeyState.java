package com.volleyball.game.input;

/** Abstraction over the keyboard so that input logic can be tested without a window. */
public interface KeyState {
    boolean isDown(PlayerAction action);

    boolean justPressed(PlayerAction action);
}
