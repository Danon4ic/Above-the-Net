package com.volleyball.game.render.anim;

import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Defensive specialists sprawl, others drop through a knee; a dive off the facing is a roll. */
class FallKindTest {
    private static VolleyballPlayer player(PlayerRole role, int defence) {
        PlayerAttributes a = new PlayerAttributes();
        a.defense = defence;
        return new VolleyballPlayer(1, "Test", 7, role, CourtSide.HOME, 1.9f, a);
    }

    @Test
    void theFallFitsThePlayer() {
        assertEquals(FallKind.SPRAWL, FallKind.of(player(PlayerRole.LIBERO, 40)));
        assertEquals(FallKind.SPRAWL, FallKind.of(player(PlayerRole.OUTSIDE_HITTER, FallKind.SPECIALIST_DEFENCE)));
        assertEquals(FallKind.KNEE_DROP, FallKind.of(player(PlayerRole.MIDDLE_BLOCKER, 45)));
        VolleyballPlayer rolling = player(PlayerRole.LIBERO, 90);
        rolling.setDiveRoll(true);
        assertEquals(FallKind.SIDE_ROLL, FallKind.of(rolling));
    }
}
