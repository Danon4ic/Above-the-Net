package com.volleyball.game.mechanics;

import com.volleyball.game.entities.VolleyballPlayer;

@FunctionalInterface
public interface BallTouchListener {
    void onBallTouch(BallTouch touch);

    default void onServeToss(VolleyballPlayer server) {
    }
}
