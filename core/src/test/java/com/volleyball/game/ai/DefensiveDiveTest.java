package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefensiveDiveTest {
    @Test
    void liberoRunsBeforeDivingForAWideDeflection() {
        assertSave(-2f, 3.1f, ContactType.DIVE);
    }

    @Test
    void liberoStaysOnFootWhenTheRunCanReachTheBall() {
        assertSave(-8f, 2.4f, ContactType.FOREARM_PASS);
    }

    private static void assertSave(float speedX, float lateral, ContactType expected) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
        VolleyballPlayer libero = world.home().libero();
        for (VolleyballPlayer player : world.players()) player.setOnCourt(player == libero);
        libero.placeAt(-5.4f, 0f);
        world.ball().launch(new Vector3(-2f, 2.1f, lateral), new Vector3(speedX, 2f, 0f), Vector3.Zero, 21);
        world.ballHandling().registerFeed(CourtSide.AWAY, 3);
        List<BallTouch> touches = new ArrayList<>();
        world.ballHandling().addListener(touches::add);

        AutoPlayer ai = new AutoPlayer(world, 21);
        double diveAt = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 120 && touches.isEmpty() && world.ball().position.y > Ball.RADIUS; i++) {
            ai.act(libero, AutoPlayer.Behavior.SCRAMBLE, world.time());
            world.step(1f / 120f);
            if (libero.bodyState() == BodyState.DIVING) diveAt = Math.min(diveAt, world.time());
        }
        assertFalse(touches.isEmpty(), "the ball is saved before it hits the floor");
        assertEquals(expected, touches.getFirst().type());
        if (expected == ContactType.DIVE) assertTrue(diveAt > 0.3 && diveAt < touches.getFirst().time());
        else assertEquals(Double.POSITIVE_INFINITY, diveAt);
    }
}
