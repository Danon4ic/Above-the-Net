package com.volleyball.game.input;

import com.badlogic.gdx.Gdx;

/** {@link KeyState} backed by the libGDX keyboard and the current key bindings. */
public final class GdxKeyState implements KeyState {
    private final KeyBindings bindings;

    public GdxKeyState(KeyBindings bindings) {
        this.bindings = bindings;
    }

    @Override
    public boolean isDown(PlayerAction action) {
        int code = bindings.key(action);
        if (KeyBindings.isMouse(code)) return Gdx.input.isButtonPressed(KeyBindings.mouseButton(code));
        return Gdx.input.isKeyPressed(code);
    }

    @Override
    public boolean justPressed(PlayerAction action) {
        int code = bindings.key(action);
        if (KeyBindings.isMouse(code)) return Gdx.input.isButtonJustPressed(KeyBindings.mouseButton(code));
        return Gdx.input.isKeyJustPressed(code);
    }
}
