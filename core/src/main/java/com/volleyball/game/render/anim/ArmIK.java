package com.volleyball.game.render.anim;

/**
 * Two-bone inverse kinematics for an arm of the procedural figure, so a hand can be put exactly
 * where the ball is at the contact. The arm hangs along -Y from the shoulder; its pose is the
 * renderer's: the upper arm turns by {@code raise} about Z (forwards and up) and then by
 * {@code spread} about X (outwards), the forearm bends by {@code elbow} about Z.
 *
 * <p>Coordinates are the trunk's (x forwards, y up), relative to the shoulder, with z pointing
 * <em>outwards</em> from the body negative (the left arm's convention; mirror z for the right arm).
 */
public final class ArmIK {
    public float raise;
    public float spread;
    public float elbow;

    private final float upper;
    private final float lower;
    /** The hand's offset ahead of the forearm's axis, as an angle. */
    private final float handAngle;

    /**
     * @param upperLength shoulder to elbow
     * @param handX       the palm ahead of the forearm's axis (forearm frame)
     * @param handY       the palm below the elbow (forearm frame, negative)
     */
    public ArmIK(float upperLength, float handX, float handY) {
        this.upper = upperLength;
        this.lower = (float) Math.sqrt(handX * handX + handY * handY);
        this.handAngle = (float) Math.atan2(handX, -handY);
    }

    /**
     * Solves for the hand at (x, y, z) relative to the shoulder. A target out of reach is
     * approached as far as the arm goes (straight towards it). Returns false only for a target
     * on the shoulder itself.
     */
    public boolean solve(float x, float y, float z) {
        float distance = (float) Math.sqrt(x * x + y * y + z * z);
        if (distance < 1e-4f) return false;
        float d = Math.max(Math.abs(upper - lower) + 1e-3f, Math.min(upper + lower - 1e-3f, distance));
        // Law of cosines: the bend between the upper arm and the shoulder-to-hand line.
        float cosBend = (d * d - upper * upper - lower * lower) / (2f * upper * lower);
        float bend = (float) Math.acos(Math.max(-1f, Math.min(1f, cosBend)));
        elbow = Math.max(0f, bend - handAngle);
        bend = elbow + handAngle;
        float offset = (float) Math.atan2(lower * Math.sin(bend), upper + lower * Math.cos(bend));
        float ux = x / distance;
        float uy = y / distance;
        float uz = z / distance;
        // The hand's direction (sin r, -cos r cos s, -cos r sin s) for a total raise r and spread s:
        // below the shoulder line (cos r > 0) or above it (cos r < 0); the one with less spread wins.
        float asin = (float) Math.asin(Math.max(-1f, Math.min(1f, ux)));
        float spreadBelow = (float) Math.atan2(-uz, -uy);
        float spreadAbove = (float) Math.atan2(uz, uy);
        if (Math.abs(spreadBelow) <= Math.abs(spreadAbove)) {
            raise = asin - offset;
            spread = spreadBelow;
        } else {
            raise = (float) Math.PI - asin - offset;
            spread = spreadAbove;
        }
        return true;
    }
}
