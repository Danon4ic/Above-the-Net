package com.volleyball.game.mechanics;

@FunctionalInterface
public interface BallTouchListener {
    void onBallTouch(BallTouch touch);
}
