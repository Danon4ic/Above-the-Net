package com.volleyball.game.training;

import com.volleyball.game.ai.AutoPlayer;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.simulation.GameWorld;

/** Demo mode for the drills: maps a drill to the matching computer behaviour. */
public final class AutoTrainee {
    private final AutoPlayer player;

    public AutoTrainee(GameWorld world) {
        this.player = new AutoPlayer(world);
    }

    /** Fills the trainee's intent (and arms actions) for the coming step. */
    public void update(VolleyballPlayer p, Drill drill, double now) {
        player.act(p, switch (drill) {
            case ATTACK -> AutoPlayer.Behavior.ATTACK;
            case SET -> AutoPlayer.Behavior.SET;
            case BLOCK -> AutoPlayer.Behavior.BLOCK;
            case DEFENSE -> AutoPlayer.Behavior.DEFEND;
            default -> AutoPlayer.Behavior.PASS;
        }, now);
    }
}
