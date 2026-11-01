package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;

/**
 * Where on the body each technique meets the ball, and how far from that ideal spot a ball can
 * still be played. Distances are measured in an ellipsoid (horizontal and vertical radii), and
 * the resulting <em>normalized offset</em> is 0 at the ideal spot and 1 at the edge of reach.
 *
 * <p>The offset drives contact quality: being in the right place at the right time — not the
 * player's ratings — is what makes a clean contact possible.
 */
public final class ContactZone {
    private ContactZone() {}

    /** Ideal ball-centre position for the technique, in world coordinates. */
    public static Vector3 idealPoint(VolleyballPlayer p, ContactType type, ServeType serveType, Vector3 out) {
        if (type == ContactType.DIVE) return diveReachPoint(p, out);
        if (type == ContactType.ONE_ARM) type = ContactType.FOREARM_PASS; // same spot, wider reach
        if (type == ContactType.BLOCK) type = ContactType.ATTACK;
        float h = p.height;
        float forward;
        float right;
        float height;
        switch (type) {
            case FOREARM_PASS -> {
                forward = 0.42f;
                right = 0f;
                height = h * (p.isCrouching() ? 0.32f : 0.40f);
            }
            case OVERHEAD_PASS -> {
                forward = 0.18f;
                right = 0f;
                height = h + 0.10f;
            }
            case ONE_HAND_SET -> {
                // One arm stretched up and out: higher and wider than both hands above the forehead.
                forward = 0.15f;
                right = 0.28f;
                height = p.standingReach() - 0.10f;
            }
            case ATTACK -> {
                forward = 0.35f;
                right = 0.18f;
                height = p.standingReach() - 0.02f;
            }
            case SERVE -> {
                if (serveType != null && !serveType.overhand) {
                    forward = 0.45f;
                    right = 0.10f;
                    height = h * 0.42f;
                } else {
                    forward = 0.30f;
                    right = 0.15f;
                    height = p.standingReach() - 0.05f;
                }
            }
            default -> throw new IllegalStateException();
        }
        // A left-hander meets the ball on the left.
        if (type == ContactType.ATTACK || type == ContactType.SERVE || type == ContactType.ONE_HAND_SET) right *= p.handSign();
        float fx = p.facingX();
        float fz = p.facingZ();
        // Right-hand direction on the floor: facing × up = (-fz, 0, fx).
        return out.set(p.position.x + fx * forward - fz * right,
                p.position.y + height,
                p.position.z + fz * forward + fx * right);
    }

    /**
     * Where the hands are at the end of a dive: the body tips over towards the dive direction
     * (pivoting at the feet, like the renderer and the body colliders) with the arms stretched
     * out beyond the head, ending just above the floor.
     */
    static Vector3 diveReachPoint(VolleyballPlayer p, Vector3 out) {
        float angle = p.bodyTilt() * MathUtils.HALF_PI * 0.95f;
        float reach = p.standingReach() - 0.1f;
        float lift = 0.14f * p.bodyTilt();
        return out.set(p.position.x + p.diveDirection.x * MathUtils.sin(angle) * reach,
                p.position.y + lift + MathUtils.cos(angle) * reach + 0.08f,
                p.position.z + p.diveDirection.z * MathUtils.sin(angle) * reach);
    }

    public static float horizontalRadius(ContactType type) {
        return switch (type) {
            case FOREARM_PASS -> 0.65f;
            case OVERHEAD_PASS -> 0.50f;
            case ATTACK, BLOCK -> 0.55f;
            case SERVE -> 0.45f;
            case DIVE -> 1.0f;
            case ONE_ARM -> 1.05f;
            case ONE_HAND_SET -> 0.70f;
        };
    }

    public static float verticalRadius(ContactType type) {
        return switch (type) {
            case FOREARM_PASS -> 0.45f;
            case OVERHEAD_PASS, ATTACK, BLOCK, SERVE -> 0.40f;
            case DIVE -> 0.60f;
            case ONE_ARM -> 0.65f;
            case ONE_HAND_SET -> 0.50f;
        };
    }

    /**
     * Upper bound on contact quality for emergency techniques: even a perfectly placed dive or
     * one-arm save is much less controlled than a pass from a balanced stance.
     */
    public static float qualityCap(ContactType type) {
        return switch (type) {
            case DIVE -> 0.6f;
            case ONE_HAND_SET -> 0.6f;
            case ONE_ARM -> 0.45f;
            default -> 1f;
        };
    }

    /** Normalized offset of a ball centre from the ideal point (≤ 1 means within reach). */
    public static float normalizedOffset(VolleyballPlayer p, ContactType type, ServeType serveType, Vector3 ball,
                                         Vector3 tmp) {
        idealPoint(p, type, serveType, tmp);
        float dx = (ball.x - tmp.x) / horizontalRadius(type);
        float dz = (ball.z - tmp.z) / horizontalRadius(type);
        float dy = (ball.y - tmp.y) / verticalRadius(type);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Signed sideways offset (m) of the ball from the ideal point, + = the player's right. */
    public static float lateralOffset(VolleyballPlayer p, ContactType type, ServeType serveType, Vector3 ball, Vector3 tmp) {
        idealPoint(p, type, serveType, tmp);
        // Right-hand direction: (-fz, 0, fx).
        return (ball.x - tmp.x) * -p.facingZ() + (ball.z - tmp.z) * p.facingX();
    }

    /**
     * Quality of a contact made at the given normalized offset: 1 at the ideal point, falling
     * off steeply towards the edge of reach.
     */
    public static float spatialQuality(float normalizedOffset) {
        float e = Math.max(0f, normalizedOffset);
        return Math.max(0.05f, 1f - 0.95f * (float) Math.pow(Math.min(e, 1.1f), 1.25f));
    }

    /**
     * Balance factor: moving fast or being in the air at contact costs control. Forearm passes
     * in the air are especially poor.
     */
    public static float balanceFactor(VolleyballPlayer p, ContactType type) {
        float moving = Math.max(0f, Math.min(4f, p.groundSpeed() - 2f));
        float factor = 1f - 0.12f * moving;
        // In the air a pass is hard to control; a setter's jump set is a skill of its own.
        boolean jumpSet = type.fingerSet() && p.role == com.volleyball.game.entities.PlayerRole.SETTER;
        if (p.isAirborne() && (type == ContactType.FOREARM_PASS || type.fingerSet())) factor *= jumpSet ? 0.92f : 0.6f;
        if (p.recoveryTime() > 0f) factor *= 0.8f;
        // Tiredness costs a little control (at most 10 %).
        factor *= p.fatigueFactor(0.1f);
        if (type == ContactType.DIVE) factor = 1f; // the dive is its own balance; its cap applies instead
        return factor;
    }
}
