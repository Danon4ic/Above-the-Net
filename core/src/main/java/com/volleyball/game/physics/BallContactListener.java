package com.volleyball.game.physics;

@FunctionalInterface
public interface BallContactListener {
    void onBallContact(BallContact contact);
}
