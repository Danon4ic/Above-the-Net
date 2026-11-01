package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.world.CourtSide;

/**
 * A deliberate contact of a player with the ball, reported to listeners (rules, statistics,
 * training, HUD). Passive body contacts are reported separately by the physics.
 *
 * @param teamTouchNumber which touch of the team this is (1 = first), as counted by ball handling
 * @param rating          pass/set rating, or null for serves and attacks
 * @param missDistance    for passes and sets: how far from the intended spot the ball will arrive
 * @param variant         serve, set or attack variant label (may be empty)
 */
public record BallTouch(int playerId, CourtSide side, ContactType type, ContactPurpose purpose, float quality,
                        PassRating rating, float missDistance, Vector3 contactPoint, Vector3 playerPosition,
                        boolean airborne, int teamTouchNumber, Vector3 outgoingVelocity, String variant, double time) {
    /**
     * A contact by the same player this soon after their previous one is part of the same action
     * (a body bounce right after a dig or a block) and is not counted again. Shared by ball handling
     * and the referee so both count the team's touches the same way.
     */
    public static final double SAME_ACTION_WINDOW = 0.25;
}
