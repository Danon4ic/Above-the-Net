package com.volleyball.game.world;

/**
 * Official indoor court geometry (FIVB rules, chapter 1), in metres.
 *
 * <p>World coordinate system used by the whole game:
 * <ul>
 *   <li>X runs along the court length; the centre line (net plane) is at {@code x = 0}.
 *       The home team plays on {@code x < 0}, the away team on {@code x > 0}.</li>
 *   <li>Y is up; the floor is at {@code y = 0}.</li>
 *   <li>Z runs across the court width; sidelines are at {@code z = ±4.5}.</li>
 * </ul>
 *
 * <p>Boundary lines are 5 cm wide and belong to the court: their outer edges lie exactly on
 * {@code x = ±9} and {@code z = ±4.5}. The attack line's rear edge is 3 m from the axis of the centre line.
 */
public final class CourtDimensions {
    public static final float LENGTH = 18f;
    public static final float WIDTH = 9f;
    public static final float HALF_LENGTH = LENGTH / 2f;
    public static final float HALF_WIDTH = WIDTH / 2f;

    public static final float LINE_WIDTH = 0.05f;
    /** Distance from the centre-line axis to the rear edge of the attack line. */
    public static final float ATTACK_LINE_DISTANCE = 3f;

    /** Free zone widths used in FIVB world and official competitions. */
    public static final float FREE_ZONE_SIDE = 5f;
    public static final float FREE_ZONE_END = 6.5f;

    /** Service zone marks: 15 cm long, drawn 20 cm behind the end line as sideline extensions. */
    public static final float SERVICE_MARK_OFFSET = 0.20f;
    public static final float SERVICE_MARK_LENGTH = 0.15f;

    /** Attack line extensions beyond each sideline: five 15 cm dashes, 20 cm apart (1.75 m total). */
    public static final int ATTACK_EXTENSION_DASHES = 5;
    public static final float ATTACK_EXTENSION_DASH = 0.15f;
    public static final float ATTACK_EXTENSION_GAP = 0.20f;

    private CourtDimensions() {}

    /**
     * Whether a floor contact point is inside the court. Lines count as "in".
     *
     * @param contactRadius radius of the ball's contact patch with the floor; any overlap with the
     *                      court (including lines) means the ball is in. Use 0 for a point test.
     */
    public static boolean isInsideCourt(float x, float z, float contactRadius) {
        return Math.abs(x) <= HALF_LENGTH + contactRadius && Math.abs(z) <= HALF_WIDTH + contactRadius;
    }

    /** Side of the net the point belongs to. Points exactly on the net plane are reported as {@link CourtSide#HOME}. */
    public static CourtSide sideOf(float x) {
        return x <= 0f ? CourtSide.HOME : CourtSide.AWAY;
    }

    /** Front zone of {@code side}: between the centre line axis and the attack line, the attack line included. */
    public static boolean isInFrontZone(float x, CourtSide side) {
        float depth = x * side.sign();
        return depth >= -LINE_WIDTH / 2f && depth <= ATTACK_LINE_DISTANCE;
    }

    /** Service zone of {@code side}: behind the end line, between the extensions of the sidelines. */
    public static boolean isInServiceZone(float x, float z, CourtSide side) {
        float depth = x * side.sign();
        return depth > HALF_LENGTH && depth <= HALF_LENGTH + FREE_ZONE_END && Math.abs(z) <= HALF_WIDTH;
    }

    /** Whether the point lies within the court plus its free zone. */
    public static boolean isInPlayingArea(float x, float z) {
        return Math.abs(x) <= HALF_LENGTH + FREE_ZONE_END && Math.abs(z) <= HALF_WIDTH + FREE_ZONE_SIDE;
    }

    /** X coordinate of the attack line's centre on the given side. */
    public static float attackLineCenterX(CourtSide side) {
        return side.sign() * (ATTACK_LINE_DISTANCE - LINE_WIDTH / 2f);
    }
}
