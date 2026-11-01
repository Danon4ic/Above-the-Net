package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A defender set for a hard hit picks up a feint late. */
class FeintReactionTest {
    /** How far the defender wants to move this step, a little after the normal reaction time. */
    private static float movementAfterReaction(boolean feint) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 4);
        VolleyballPlayer defender = world.home().players().get(5);
        defender.placeAt(-6f, 0f);
        // A soft ball from the opponent coming down three metres in front of the defender.
        world.ball().launch(new Vector3(-0.5f, 3f, 0f), new Vector3(-2.5f, 1f, 0f), Vector3.Zero, 1);
        world.ballHandling().registerFeed(CourtSide.AWAY, 3);
        AutoPlayer motor = new AutoPlayer(world, 1);
        if (feint) motor.surprise(TeamAI.FEINT_SURPRISE);
        double now = world.time();
        motor.act(defender, AutoPlayer.Behavior.DEFEND, now);
        motor.act(defender, AutoPlayer.Behavior.DEFEND, now + AutoPlayer.DEFENSE_REACTION + 0.05);
        return world.intent(defender).direction.len();
    }

    @Test
    void theDefenderStartsLateAgainstAFeint() {
        assertTrue(movementAfterReaction(false) > 0.1f, "after the normal reaction the defender goes for the ball");
        assertEquals(0f, movementAfterReaction(true), 1e-6f, "against a feint still waiting");
    }
}
