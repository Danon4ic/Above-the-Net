package com.volleyball.game.input;

import java.util.EnumMap;

/**
 * Remembers action presses for a short window, so a press slightly before the action becomes
 * possible (e.g. jump while still landing) is executed as soon as it does become possible,
 * instead of being lost.
 */
public final class InputBuffer {
    private final EnumMap<PlayerAction, Double> pressedAt = new EnumMap<>(PlayerAction.class);
    private final double windowSeconds;

    public InputBuffer(double windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public void press(PlayerAction action, double now) {
        pressedAt.put(action, now);
    }

    /** True while a press of {@code action} is within the window and not yet consumed. */
    public boolean isPending(PlayerAction action, double now) {
        Double at = pressedAt.get(action);
        return at != null && now - at <= windowSeconds;
    }

    /** Takes the pending press, if any. Each press is consumed at most once. */
    public boolean consume(PlayerAction action, double now) {
        boolean pending = isPending(action, now);
        pressedAt.remove(action);
        return pending;
    }

    public void clear() {
        pressedAt.clear();
    }
}
