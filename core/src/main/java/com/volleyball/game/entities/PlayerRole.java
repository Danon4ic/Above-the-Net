package com.volleyball.game.entities;

/** Specialised position of a player in the team. */
public enum PlayerRole {
    SETTER("S", "Setter"),
    OPPOSITE("OP", "Opposite"),
    MIDDLE_BLOCKER("MB", "Middle blocker"),
    OUTSIDE_HITTER("OH", "Outside hitter"),
    LIBERO("L", "Libero");

    public final String shortName;
    public final String label;

    PlayerRole(String shortName, String label) {
        this.shortName = shortName;
        this.label = label;
    }
}
