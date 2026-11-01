package com.volleyball.game.render;

import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The block animation follows the block: straight, line, diagonal, swing, soft, penetrating. */
class BlockKindTest {
    private static VolleyballPlayer blocker(float z) {
        VolleyballPlayer p = new VolleyballPlayer(0, "B", 1, PlayerRole.MIDDLE_BLOCKER, CourtSide.HOME, 2f, new PlayerAttributes());
        p.placeAt(-0.4f, z);
        p.setAirborne(true);
        p.velocity.set(0f, 0.1f, 0f);
        return p;
    }

    @Test
    void theBlockKindIsReadFromTheBlockersState() {
        VolleyballPlayer p = blocker(2f);
        p.setBlocking(true, 0.12f, 0f);
        assertEquals(PlayerRenderer.BlockKind.STRAIGHT, PlayerRenderer.blockKind(p));
        p.setBlocking(true, 0.12f, 0.2f); // hands towards the near sideline (+z)
        assertEquals(PlayerRenderer.BlockKind.LINE, PlayerRenderer.blockKind(p));
        p.setBlocking(true, 0.12f, -0.2f);
        assertEquals(PlayerRenderer.BlockKind.CROSS, PlayerRenderer.blockKind(p));
        p.setBlocking(true, 0.28f, 0f);
        assertEquals(PlayerRenderer.BlockKind.PENETRATING, PlayerRenderer.blockKind(p));
        p.setBlocking(true, 0.01f, 0f);
        assertEquals(PlayerRenderer.BlockKind.SOFT, PlayerRenderer.blockKind(p), "cannot reach over: soft block");
        VolleyballPlayer swing = blocker(0f);
        swing.velocity.set(0f, 2.5f, 1.8f); // taking off from a run along the net
        assertEquals(PlayerRenderer.BlockKind.SWING, PlayerRenderer.blockKind(swing));
    }
}
