package com.volleyball.game.simulation;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.physics.PlayerLocomotion;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameWorldTest {
    static final float DT = 1f / 120f;

    @Test
    void defaultTeamsLineUpSixPlayersPerSideInsideTheirCourt() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN));
        assertEquals(6, world.home().players().size());
        assertEquals(6, world.away().players().size());
        for (VolleyballPlayer p : world.players()) {
            if (!world.home().players().contains(p) && !world.away().players().contains(p)) {
                // The libero and the substitutes wait at the bench until a match brings them in.
                assertFalse(p.isOnCourt());
                assertFalse(CourtDimensions.isInsideCourt(p.position.x, p.position.z, 0f));
                continue;
            }
            assertTrue(p.isOnCourt());
            assertTrue(CourtDimensions.isInsideCourt(p.position.x, p.position.z, 0f));
            assertEquals(p.side, CourtDimensions.sideOf(p.position.x));
        }
        assertNotNull(world.home().libero());
        assertEquals(22, world.players().size());
        assertEquals(4, world.home().substitutes().size());
    }

    @Test
    void idlePlayersStayPutAndApart() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN));
        List<Vector3> start = new ArrayList<>();
        for (VolleyballPlayer p : world.players()) start.add(p.position.cpy());
        for (int i = 0; i < 120 * 5; i++) world.step(DT);
        for (int i = 0; i < world.players().size(); i++) {
            assertEquals(0f, world.players().get(i).position.dst(start.get(i)), 1e-5f);
        }
    }

    @Test
    void playersRunningIntoEachOtherDoNotOverlap() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN));
        // Everyone on the home side runs towards the same spot.
        for (int i = 0; i < 120 * 4; i++) {
            for (VolleyballPlayer p : world.home().players()) {
                world.intent(p).direction.set(-4f - p.position.x, 0f - p.position.z).limit(1f);
            }
            world.step(DT);
        }
        List<VolleyballPlayer> home = world.home().players();
        for (int i = 0; i < home.size(); i++) {
            for (int j = i + 1; j < home.size(); j++) {
                assertTrue(home.get(i).position.dst(home.get(j).position) > PlayerLocomotion.PERSONAL_SPACE * 0.8f);
            }
        }
    }

    @Test
    void ballDroppedOnAPlayerIsReportedAsThatPlayersTouch() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN));
        List<BallContact> contacts = new ArrayList<>();
        world.addContactListener(contacts::add);
        VolleyballPlayer target = world.away().players().get(2);
        world.ball().launch(new Vector3(target.position.x, 5f, target.position.z + 0.05f), Vector3.Zero, Vector3.Zero, 1);
        for (int i = 0; i < 240; i++) world.step(DT);
        BallContact first = contacts.get(0);
        assertEquals(ColliderKind.PLAYER_BODY, first.kind());
        assertEquals(target.id, first.playerId());
        assertTrue(contacts.stream().anyMatch(c -> c.kind() == ColliderKind.FLOOR));
    }

    @Test
    void changingTheNetHeightChangesTheCollisionGeometry() {
        VolleyballCourt court = new VolleyballCourt(NetCategory.MEN);
        GameWorld world = GameWorld.withDefaultTeams(court);
        // Flat ball crossing at 2.35 m: into the men's net (top band), but over the women's net.
        Ball ball = world.ball();
        ball.launch(new Vector3(-2f, 2.35f + Ball.RADIUS, 1f), new Vector3(12f, 0f, 0f), Vector3.Zero, 1);
        for (int i = 0; i < 30; i++) world.step(DT);
        assertTrue(ball.position.x < 0.5f, "blocked by the men's net");

        court.net().setCategory(NetCategory.WOMEN);
        world.onNetChanged();
        ball.launch(new Vector3(-2f, 2.35f + Ball.RADIUS, 1f), new Vector3(12f, 0f, 0f), Vector3.Zero, 1);
        for (int i = 0; i < 30; i++) world.step(DT);
        assertEquals(CourtSide.AWAY, CourtDimensions.sideOf(ball.position.x), "clears the women's net");
    }

    @Test
    void playersTurnToWatchTheBallInFrontButFaceTheNetWhenItIsBehind() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        VolleyballPlayer p = world.home().players().get(4);
        p.placeAt(-6f, 0f);
        // Ball in front-left (towards -Z), far away and held still.
        world.ball().hold(new Vector3(-1f, 2f, -4f));
        for (int i = 0; i < 120; i++) world.step(DT);
        float toBall = com.badlogic.gdx.math.MathUtils.atan2(4f, 5f);
        assertEquals(toBall, p.facingYaw, 0.05f, "turned towards the ball");

        world.ball().hold(new Vector3(-12f, 2f, 0f)); // behind the player
        for (int i = 0; i < 120; i++) world.step(DT);
        assertEquals(0f, p.facingYaw, 0.05f, "faces the net when the ball is behind");
    }

    @Test
    void sprintingAwayFromTheBallTurnsThePlayerToRun() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        VolleyballPlayer p = world.home().players().get(1);
        p.placeAt(-2f, 0f);
        world.ball().hold(new Vector3(4f, 2f, 0f));
        for (int i = 0; i < 90; i++) {
            world.intent(p).direction.set(-1f, 0f);
            world.intent(p).sprint = true;
            world.step(DT);
        }
        assertEquals(Math.PI, Math.abs(p.facingYaw), 0.1f, "turned around to run back");
    }
}
