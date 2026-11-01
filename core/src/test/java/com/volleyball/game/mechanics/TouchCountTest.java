package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ball handling counts a team's touches with the referee's rules, so the players and the referee agree. */
class TouchCountTest {
    private final GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
    private final BallHandling handling = world.ballHandling();

    private void contact(ColliderKind kind, VolleyballPlayer p, double time) {
        handling.onBallContact(new BallContact(kind, p.id, new Vector3(p.position.x, 2f, p.position.z), 5f, time));
    }

    @Test
    void aBodyBounceRightAfterTheSamePlayersTouchIsTheSameAction() {
        VolleyballPlayer p = world.home().onCourt().get(0);
        handling.registerFeed(CourtSide.AWAY, 1);
        contact(ColliderKind.PLAYER_BODY, p, 1.0);
        contact(ColliderKind.PLAYER_BODY, p, 1.0 + BallTouch.SAME_ACTION_WINDOW * 0.9);
        assertEquals(1, handling.teamTouches(CourtSide.HOME));
        contact(ColliderKind.PLAYER_BODY, p, 2.0);
        assertEquals(2, handling.teamTouches(CourtSide.HOME), "a later contact is a new touch");
    }

    @Test
    void theBallDroppingOntoTheBlockerRightAfterTheBlockIsPartOfTheBlock() {
        VolleyballPlayer blocker = world.home().onCourt().get(2);
        handling.registerFeed(CourtSide.AWAY, 3);
        contact(ColliderKind.BLOCK, blocker, 1.0);
        assertEquals(0, handling.teamTouches(CourtSide.HOME), "a block is not a team touch");
        contact(ColliderKind.PLAYER_BODY, blocker, 1.1);
        assertEquals(0, handling.teamTouches(CourtSide.HOME));
    }

    @Test
    void theCountOnlyStartsOverWhenTheOtherTeamTouchesTheBall() {
        VolleyballPlayer a = world.home().onCourt().get(0);
        VolleyballPlayer b = world.home().onCourt().get(1);
        handling.registerFeed(CourtSide.AWAY, 1);
        contact(ColliderKind.PLAYER_BODY, a, 1.0);
        world.ball().launch(new Vector3(0.3f, 2.6f, 0f), new Vector3(-2f, 0f, 0f), Vector3.Zero, 1);
        world.step(1f / 120f); // the ball comes back into the home half: still the same possession
        contact(ColliderKind.PLAYER_BODY, b, 2.0);
        assertEquals(2, handling.teamTouches(CourtSide.HOME));
    }
}
