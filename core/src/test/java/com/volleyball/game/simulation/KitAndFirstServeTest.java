package com.volleyball.game.simulation;

import com.volleyball.game.entities.Team;
import com.volleyball.game.match.FirstServe;
import com.volleyball.game.training.Drill;
import com.volleyball.game.training.DrillController;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class KitAndFirstServeTest {
    @Test
    void theChosenAwayKitIsWornAndTheOpponentAvoidsAClash() {
        TeamCatalog.Entry wolves = TeamCatalog.byId("wolves");
        TeamCatalog.Entry sharks = TeamCatalog.byId("sharks");
        Team home = TeamFactory.build(wolves, CourtSide.HOME, 0, sharks.shirt(), true);
        assertEquals(wolves.awayShirt(), home.shirtColor, "your team plays in its away kit");
        Team homeKit = TeamFactory.build(wolves, CourtSide.HOME, 0, sharks.shirt(), false);
        assertEquals(wolves.shirt(), homeKit.shirtColor);
        // A club meeting itself: the visitors change to their away kit.
        Team visitors = TeamFactory.build(wolves, CourtSide.AWAY, 6, homeKit.shirtColor, false);
        assertNotEquals(homeKit.shirtColor, visitors.shirtColor);
    }

    private static CourtSide firstServer(FirstServe firstServe) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        DrillController drills = new DrillController(world);
        drills.setFirstServe(firstServe);
        drills.start(Drill.MATCH);
        return drills.match().rules().servingSide();
    }

    @Test
    void theFirstServeFollowsTheChoice() {
        assertEquals(CourtSide.HOME, firstServer(FirstServe.YOU));
        assertEquals(CourtSide.AWAY, firstServer(FirstServe.OPPONENT));
        Set<CourtSide> tossed = new HashSet<>();
        for (int i = 0; i < 24; i++) tossed.add(firstServer(FirstServe.TOSS));
        assertEquals(2, tossed.size(), "the coin toss gives each side the serve sometimes");
    }
}
