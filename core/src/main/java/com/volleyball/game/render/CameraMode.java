package com.volleyball.game.render;

/**
 * Camera presets. The attacker and server cameras follow a subject given by the match screen
 * (the hitter of the team with the ball, the server); replays use several of these angles.
 */
public enum CameraMode {
    TV_BROADCAST("TV broadcast"),
    THIRD_PERSON("Third person"),
    /** Through the player's eyes: the view turns with the middle mouse button, V looks back at the net. */
    FIRST_PERSON("First person"),
    SIDELINE("Sideline"),
    BEHIND_BASELINE("Behind home baseline"),
    OVERHEAD("Tactical overhead"),
    ATTACKER("Attacker camera"),
    SERVER("Behind the server"),
    FREE_ORBIT("Free orbit");

    public final String label;

    CameraMode(String label) {
        this.label = label;
    }

    public CameraMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
