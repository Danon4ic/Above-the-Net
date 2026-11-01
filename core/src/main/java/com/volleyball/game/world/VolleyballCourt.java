package com.volleyball.game.world;

/** The playing facility as seen by game logic: court geometry plus the net. Contains no rendering code. */
public final class VolleyballCourt {
    private final Net net;

    public VolleyballCourt(NetCategory category) {
        this.net = new Net(category);
    }

    public Net net() {
        return net;
    }

    public boolean isInsideCourt(float x, float z, float contactRadius) {
        return CourtDimensions.isInsideCourt(x, z, contactRadius);
    }

    public CourtSide sideOf(float x) {
        return CourtDimensions.sideOf(x);
    }
}
