package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContactFeedbackTest {
    @Test
    void anInputIsEarlyOnlyIfTheBallEntersReachAfterItExpires() {
        Harness game = new Harness(17);
        VolleyballPlayer player = game.home(0);
        player.placeAt(-6f, 0f);
        Vector3 ideal = ContactZone.idealPoint(player, ContactType.FOREARM_PASS, null, new Vector3());
        game.world.ball().launch(ideal.add(0f, 2.55f, 0f), new Vector3(0f, -0.4f, 0f), Vector3.Zero, 10);
        game.handling.registerFeed(CourtSide.AWAY, 3);
        game.handling.armManual(player, ActionKind.PRIMARY, game.world.time());

        assertTrue(game.runUntil(() -> game.handling.contactFeedback(player, game.world.time()) != null, 1f));
        assertEquals(BallHandling.ContactFeedback.EARLY, game.handling.contactFeedback(player, game.world.time()));
        assertTrue(game.touches.isEmpty());
    }

    @Test
    void aBallInAnotherLaneIsOutOfReach() {
        Harness game = new Harness(17);
        VolleyballPlayer player = game.home(0);
        player.placeAt(-6f, 0f);
        game.world.ball().launch(new Vector3(-5.6f, 2.2f, 3f), new Vector3(0f, -1f, 0f), Vector3.Zero, 10);
        game.handling.registerFeed(CourtSide.AWAY, 3);
        game.handling.armManual(player, ActionKind.PRIMARY, game.world.time());

        assertTrue(game.runUntil(() -> game.handling.contactFeedback(player, game.world.time()) != null, 1f));
        assertEquals(BallHandling.ContactFeedback.OUT_OF_REACH, game.handling.contactFeedback(player, game.world.time()));
        assertTrue(game.touches.isEmpty());
    }

    @Test
    void aWeakContactAtTheEdgeReportsThePosition() {
        Harness game = new Harness(17);
        VolleyballPlayer player = game.home(0);
        player.placeAt(-6f, 0f);
        Vector3 ideal = ContactZone.idealPoint(player, ContactType.FOREARM_PASS, null, new Vector3());
        game.world.ball().launch(ideal.add(0f, 0.24f, 0.55f), new Vector3(0f, -2f, 0f), Vector3.Zero, 10);
        game.handling.registerFeed(CourtSide.AWAY, 3);
        game.handling.armManual(player, ActionKind.PRIMARY, game.world.time());

        assertTrue(game.runUntil(() -> !game.touches.isEmpty(), 0.4f));
        assertEquals(BallHandling.ContactFeedback.OFF_CENTER, game.handling.contactFeedback(player, game.world.time()));
    }
}
