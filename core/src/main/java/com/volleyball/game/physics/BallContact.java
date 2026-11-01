package com.volleyball.game.physics;

import com.badlogic.gdx.math.Vector3;

/**
 * An impact of the ball against something, reported by the physics to listeners (rules,
 * statistics, audio, HUD).
 *
 * @param kind         what was hit
 * @param playerId     id of the player for {@link ColliderKind#PLAYER_BODY}, otherwise -1
 * @param position     ball centre at the moment of impact
 * @param impactSpeed  approach speed along the contact normal, m/s
 * @param time         simulation time of the impact, s
 */
public record BallContact(ColliderKind kind, int playerId, Vector3 position, float impactSpeed, double time) {
}
