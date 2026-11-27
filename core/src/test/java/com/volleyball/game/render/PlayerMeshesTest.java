package com.volleyball.game.render;

import com.volleyball.game.simulation.TeamCatalog;
import com.volleyball.game.simulation.TeamFactory;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMeshesTest {
    @Test
    void appearanceTravelsWithTheRosterInsteadOfTheSideOrKit() {
        Set<String> combinations = new HashSet<>();
        for (TeamCatalog.Entry club : TeamCatalog.all()) {
            var home = TeamFactory.build(club, CourtSide.HOME, 0, club.awayShirt(), false);
            var away = TeamFactory.build(club, CourtSide.AWAY, 6, club.shirt(), true);
            for (int i = 0; i < home.squad().size(); i++) {
                var first = PlayerMeshes.of(home.shortName, home.squad().get(i));
                var second = PlayerMeshes.of(away.shortName, away.squad().get(i));
                assertEquals(first, second);
                combinations.add(first.face() + "/" + first.build() + "/" + first.hair() + "/" + first.equipment());
                int loaded = expectedGeometry(first);
                assertTrue(loaded > 700 && loaded < 2100, () -> "unexpected authored triangle count: " + loaded);
            }
        }
        assertTrue(combinations.size() > 20);
    }

    private static int expectedGeometry(PlayerMeshes.Style style) {
        String body = "body_" + style.build();
        String kit = "kit_" + style.equipment();
        return PlayerMeshes.triangles("face_" + style.face(), "head")
                + PlayerMeshes.triangles("hair_" + style.hair(), "head")
                + PlayerMeshes.triangles(body, "pelvis")
                + PlayerMeshes.triangles(body, "torso")
                + 2 * (PlayerMeshes.triangles(body, "upper") + PlayerMeshes.triangles(body, "thigh")
                    + PlayerMeshes.triangles(kit, "shin") + PlayerMeshes.triangles(kit, "foot"))
                + PlayerMeshes.triangles(kit, "forearm");
    }
}
