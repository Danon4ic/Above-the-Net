package com.volleyball.game.render.anim;

import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The animation state is read from what the simulation says the player does. */
class PlayerAnimStateTest {
    private static VolleyballPlayer player() {
        return new VolleyballPlayer(1, "Test", 7, PlayerRole.OUTSIDE_HITTER, CourtSide.HOME, 1.9f, new PlayerAttributes());
    }

    @Test
    void groundMovementByDirectionAndSpeed() {
        VolleyballPlayer p = player();
        assertEquals(PlayerAnimState.IDLE, PlayerAnimState.of(p, 0f, 0f, 10f, false));
        assertEquals(PlayerAnimState.WALK, PlayerAnimState.of(p, 1.2f, 0f, 10f, false));
        assertEquals(PlayerAnimState.RUN, PlayerAnimState.of(p, 3.5f, 0.3f, 10f, false));
        assertEquals(PlayerAnimState.SPRINT, PlayerAnimState.of(p, 6f, 0f, 10f, false));
        assertEquals(PlayerAnimState.APPROACH, PlayerAnimState.of(p, 4f, 0f, 10f, true));
        assertEquals(PlayerAnimState.BACKPEDAL, PlayerAnimState.of(p, -2f, 0.2f, 10f, false));
        assertEquals(PlayerAnimState.SHUFFLE, PlayerAnimState.of(p, 0.3f, 2f, 10f, false));
    }

    @Test
    void jumpPhasesFollowTheVerticalSpeed() {
        VolleyballPlayer p = player();
        p.setAirborne(true);
        p.velocity.y = 3f;
        assertEquals(PlayerAnimState.JUMP, PlayerAnimState.of(p, 0f, 0f, 10f, false));
        p.velocity.y = 0.2f;
        assertEquals(PlayerAnimState.JUMP_PEAK, PlayerAnimState.of(p, 0f, 0f, 10f, false));
        p.setAirborne(false);
        p.velocity.y = 0f;
        assertEquals(PlayerAnimState.LANDING, PlayerAnimState.of(p, 0f, 0f, 0.1f, false));
        assertEquals(PlayerAnimState.IDLE, PlayerAnimState.of(p, 0f, 0f, 1f, false));
    }

    @Test
    void ballActionsComeFromTheActionPose() {
        VolleyballPlayer p = player();
        p.setActionPose(ActionPose.PLATFORM, 0.4f);
        assertEquals(PlayerAnimState.RECEIVE, PlayerAnimState.of(p, 0f, 0f, 10f, false));
        p.setActionPose(ActionPose.OVERHEAD, 0.4f);
        assertEquals(PlayerAnimState.SET, PlayerAnimState.of(p, 0f, 0f, 10f, false));
        p.setActionPose(ActionPose.DISAPPOINTED, 0.4f);
        assertEquals(PlayerAnimState.DISAPPOINTED, PlayerAnimState.of(p, 0f, 0f, 10f, false));
    }
}
