package com.volleyball.game.world;

/**
 * Logical net in the plane {@code x = 0} (FIVB rules 2.1–2.5). Used by rendering now and by
 * ball collisions and net-fault detection in later stages.
 */
public final class Net {
    /** Vertical size of the mesh, top band included. */
    public static final float MESH_HEIGHT = 1.0f;
    /** Total net length (rules allow 9.5–10 m); it is centred on the court. */
    public static final float LENGTH = 10.0f;
    public static final float MESH_CELL = 0.10f;
    public static final float TOP_BAND_HEIGHT = 0.07f;
    public static final float BOTTOM_BAND_HEIGHT = 0.05f;
    /** Side bands are 5 cm wide and sit directly above each sideline. */
    public static final float SIDE_BAND_WIDTH = 0.05f;

    public static final float ANTENNA_LENGTH = 1.80f;
    public static final float ANTENNA_DIAMETER = 0.01f;
    public static final float ANTENNA_ABOVE_NET = 0.80f;

    /** Posts stand 0.5–1.0 m outside the sidelines; we use the 1.0 m required in FIVB competitions. */
    public static final float POST_DISTANCE_FROM_SIDELINE = 1.0f;
    public static final float POST_HEIGHT = 2.55f;
    public static final float POST_DIAMETER = 0.10f;

    private NetCategory category;

    public Net(NetCategory category) {
        this.category = category;
    }

    public NetCategory category() {
        return category;
    }

    public void setCategory(NetCategory category) {
        this.category = category;
    }

    public float topHeight() {
        return category.height();
    }

    public float bottomHeight() {
        return topHeight() - MESH_HEIGHT;
    }

    /** |z| of each antenna's axis: antennas are fastened at the outer edge of the side bands. */
    public float antennaAxisZ() {
        return CourtDimensions.HALF_WIDTH + ANTENNA_DIAMETER / 2f;
    }

    public float antennaBottom() {
        return bottomHeight();
    }

    public float antennaTop() {
        return topHeight() + ANTENNA_ABOVE_NET;
    }

    public float postAxisZ() {
        return CourtDimensions.HALF_WIDTH + POST_DISTANCE_FROM_SIDELINE;
    }

    /**
     * Whether a ball of the given radius is laterally inside the crossing space, i.e. completely
     * between the antennas (and their imaginary extension up to the ceiling).
     */
    public boolean isWithinAntennas(float z, float ballRadius) {
        float innerEdge = antennaAxisZ() - ANTENNA_DIAMETER / 2f;
        return Math.abs(z) + ballRadius <= innerEdge;
    }
}
