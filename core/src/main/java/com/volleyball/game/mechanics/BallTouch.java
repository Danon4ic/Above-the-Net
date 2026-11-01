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
}
