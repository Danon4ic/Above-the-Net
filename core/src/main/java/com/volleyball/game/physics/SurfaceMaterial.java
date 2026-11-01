package com.volleyball.game.physics;

/**
 * Contact properties of the surfaces the ball can hit.
 *
 * <ul>
 *   <li>{@code restitution} — ratio of normal speed after/before impact.</li>
 *   <li>{@code friction} — Coulomb coefficient limiting the tangential impulse; it converts
 *       sliding into spin and vice versa.</li>
 *   <li>{@code rollingDamping} — per-second decay of speed and spin while the ball rests on the surface.</li>
 * </ul>
 */
public enum SurfaceMaterial {
    /** Sports floor: a dropped volleyball rebounds to ~55 % of its height (e ≈ 0.74). */
    FLOOR(0.74f, 0.45f, 1.2f),
    /** The mesh yields and absorbs most of the impact; the ball drops down along it. */
    NET_MESH(0.12f, 0.5f, 0f),
    /** Tensioned top band with its cable: fairly lively, deflects balls over or back. */
    NET_BAND(0.35f, 0.3f, 0f),
    ANTENNA(0.45f, 0.2f, 0f),
    POST(0.5f, 0.3f, 0f),
    /** Foam padding around the posts and around the referee stand. */
    PADDING(0.3f, 0.5f, 0f),
    /** Passive contact with a player's body (torso, legs, head). */
    BODY(0.45f, 0.4f, 0f),
    /** Firm, tensed hands and forearms of a blocker. */
    BLOCK_HANDS(0.55f, 0.5f, 0f),
    WALL(0.55f, 0.35f, 0f),
    CEILING(0.4f, 0.3f, 0f);

    public final float restitution;
    public final float friction;
    public final float rollingDamping;

    SurfaceMaterial(float restitution, float friction, float rollingDamping) {
        this.restitution = restitution;
        this.friction = friction;
        this.rollingDamping = rollingDamping;
    }
}
