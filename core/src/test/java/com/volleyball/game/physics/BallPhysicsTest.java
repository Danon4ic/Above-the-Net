package com.volleyball.game.physics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.Net;
import com.volleyball.game.world.NetCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class BallPhysicsTest {
    static final float DT = 1f / 120f;

    private final Aerodynamics aerodynamics = new Aerodynamics();
    private final CollisionSystem collisions = new CollisionSystem();
    private final BallPhysics physics = new BallPhysics(aerodynamics, collisions);
    private final List<BallContact> contacts = new ArrayList<>();
    private double time;

    BallPhysicsTest() {
        collisions.buildFloorOnly();
    }

    private void run(Ball ball, float seconds) {
        runUntil(ball, seconds, b -> false);
    }

    private void runUntil(Ball ball, float maxSeconds, Predicate<Ball> stop) {
        int steps = Math.round(maxSeconds / DT);
        for (int i = 0; i < steps; i++) {
            physics.step(ball, DT, time, contacts::add);
            time += DT;
            if (stop.test(ball)) return;
        }
    }

    /** Flies until the first floor impact and returns the ball position at that moment. */
    private Vector3 landing(Ball ball) {
        contacts.clear();
        runUntil(ball, 10f, b -> contacts.stream().anyMatch(c -> c.kind() == ColliderKind.FLOOR));
        return contacts.stream().filter(c -> c.kind() == ColliderKind.FLOOR).findFirst().orElseThrow().position();
    }

    private static Ball launched(Vector3 from, Vector3 velocity, float topspin, float sidespin, long seed) {
        Ball ball = new Ball();
        ball.launch(from, velocity, LaunchSolver.spinFor(velocity, topspin, sidespin, new Vector3()), seed);
        return ball;
    }

    @Test
    void inVacuumTheApexMatchesTheAnalyticSolution() {
        aerodynamics.setAirEnabled(false);
        Ball ball = launched(new Vector3(0f, 3f, 0f), new Vector3(3f, 8f, 0f), 0f, 0f, 1);
        float apex = 0f;
        for (int i = 0; i < 120; i++) {
            physics.step(ball, DT, 0, null);
            apex = Math.max(apex, ball.position.y);
        }
        float expected = 3f + 8f * 8f / (2f * PhysicsConstants.GRAVITY);
        assertEquals(expected, apex, expected * 0.005f);
    }

    @Test
    void airDragShortensTheFlight() {
        aerodynamics.setAirEnabled(false);
        float vacuum = landing(launched(new Vector3(-8f, 2f, 0f), new Vector3(14f, 6f, 0f), 0f, 0f, 1)).x;
        aerodynamics.setAirEnabled(true);
        float withAir = landing(launched(new Vector3(-8f, 2f, 0f), new Vector3(14f, 6f, 0f), 0f, 0f, 1)).x;
        assertTrue(withAir < vacuum - 1f, "drag costs over a metre of range: " + vacuum + " vs " + withAir);
    }

    @Test
    void topspinDivesAndBackspinFloats() {
        aerodynamics.setKnuckleEnabled(false);
        Vector3 from = new Vector3(-9f, 2.5f, 0f);
        Vector3 v = new Vector3(15f, 4f, 0f);
        float none = landing(launched(from, v, 0f, 0f, 1)).x;
        float top = landing(launched(from, v, 40f, 0f, 1)).x;
        float back = landing(launched(from, v, -25f, 0f, 1)).x;
        assertTrue(top < none - 1f, "topspin lands shorter: " + top + " vs " + none);
        assertTrue(back > none + 0.5f, "backspin carries further: " + back + " vs " + none);
    }

    @Test
    void sidespinCurvesTheBall() {
        Vector3 from = new Vector3(-9f, 2.5f, 0f);
        Vector3 v = new Vector3(15f, 4f, 0f);
        Vector3 left = landing(launched(from, v, 0f, 25f, 1));
        Vector3 right = landing(launched(from, v, 0f, -25f, 1));
        // Facing +X, left is -Z.
        assertTrue(left.z < -0.5f, "positive sidespin curves left: " + left.z);
        assertTrue(right.z > 0.5f, "negative sidespin curves right: " + right.z);
    }

    @Test
    void floatServeWobblesUnpredictablyButSpinningBallDoesNot() {
        TrajectoryPredictor predictor = new TrajectoryPredictor(aerodynamics);
        TrajectoryPredictor.Prediction forecast = new TrajectoryPredictor.Prediction();
        Vector3 from = new Vector3(-11f, 2.8f, 0f);
        Vector3 v = new Vector3(16.5f, 2.2f, 0f);

        float floatMiss = 0f;
        int seeds = 8;
        for (long seed = 1; seed <= seeds; seed++) {
            Ball ball = launched(from, v, 0f, 0f, seed);
            predictor.predict(ball, 6f, forecast);
            floatMiss += landing(ball).dst(forecast.landing);
        }
        floatMiss /= seeds;

        Ball spinning = launched(from, v, 40f, 0f, 3);
        predictor.predict(spinning, 6f, forecast);
        float spinMiss = landing(spinning).dst(forecast.landing);

        assertTrue(floatMiss > 0.25f, "float serves deviate from any forecast, mean " + floatMiss);
        assertTrue(spinMiss < 0.03f, "a spinning ball flies true, miss " + spinMiss);
    }

    @Test
    void floatWobbleIsDeterministicPerSeed() {
        Vector3 from = new Vector3(-11f, 2.8f, 0f);
        Vector3 v = new Vector3(16.5f, 2.2f, 0f);
        Vector3 a = landing(launched(from, v, 0f, 0f, 42)).cpy();
        Vector3 b = landing(launched(from, v, 0f, 0f, 42)).cpy();
        Vector3 c = landing(launched(from, v, 0f, 0f, 43)).cpy();
        assertEquals(0f, a.dst(b), 1e-6f);
        assertNotEquals(0f, a.dst(c), 1e-3f);
    }

    @Test
    void bounceHeightFollowsFloorRestitution() {
        aerodynamics.setAirEnabled(false);
        Ball ball = new Ball();
        ball.launch(new Vector3(0f, 2f, 0f), Vector3.Zero, Vector3.Zero, 1);
        runUntil(ball, 2f, b -> b.velocity.y > 0f); // first bounce
        float bounceApex = 0f;
        for (int i = 0; i < 120; i++) {
            physics.step(ball, DT, 0, null);
            bounceApex = Math.max(bounceApex, ball.position.y);
        }
        float e = SurfaceMaterial.FLOOR.restitution;
        float expected = Ball.RADIUS + e * e * (2f - Ball.RADIUS);
        assertEquals(expected, bounceApex, 0.03f);
    }

    @Test
    void skiddingBallStartsRollingOnTheFloor() {
        aerodynamics.setAirEnabled(false);
        Ball ball = new Ball();
        ball.launch(new Vector3(0f, Ball.RADIUS, 0f), new Vector3(5f, 0f, 0f), Vector3.Zero, 1);
        run(ball, 0.6f);
        float rollingSpin = ball.velocity.len() / Ball.RADIUS;
        assertEquals(rollingSpin, ball.spin.len(), rollingSpin * 0.1f);
        assertTrue(ball.spin.z < 0f, "rolling towards +X spins about -Z");
    }

    @Test
    void ballComesToRestAndNeverSinksIntoTheFloor() {
        Ball ball = new Ball();
        ball.launch(new Vector3(0f, 3f, 0f), new Vector3(2f, 0f, 1f), new Vector3(0f, 5f, 0f), 1);
        for (int i = 0; i < 120 * 20; i++) {
            physics.step(ball, DT, i * DT, null);
            assertTrue(ball.position.y >= Ball.RADIUS - 1e-3f, "below floor at step " + i);
        }
        assertTrue(ball.speed() < 0.05f, "at rest, speed " + ball.speed());
    }

    @Test
    void fastSpikeCannotTunnelThroughTheNet() {
        collisions.buildArena(new Net(NetCategory.MEN));
        Ball ball = new Ball();
        ball.launch(new Vector3(-1f, 1.9f, 0f), new Vector3(40f, 0f, 0f), Vector3.Zero, 1);
        contacts.clear();
        run(ball, 0.4f);
        assertTrue(ball.position.x < 0f, "the ball stayed on the attacker's side: x=" + ball.position.x);
        assertTrue(contacts.stream().anyMatch(c -> c.kind() == ColliderKind.NET_MESH));
    }

    @Test
    void ballOverTheNetClearsItAndBallIntoTheAntennaIsReported() {
        collisions.buildArena(new Net(NetCategory.MEN));
        Ball over = new Ball();
        over.launch(new Vector3(-3f, 3.2f, 0f), new Vector3(10f, 2f, 0f), Vector3.Zero, 1);
        contacts.clear();
        run(over, 0.6f);
        assertTrue(over.position.x > 1f);
        assertTrue(contacts.stream().noneMatch(c -> c.kind() == ColliderKind.NET_MESH || c.kind() == ColliderKind.NET_BAND));

        Ball antenna = new Ball();
        antenna.launch(new Vector3(-5f, 2.9f, 4.505f), new Vector3(12f, 2f, 0f), Vector3.Zero, 1);
        contacts.clear();
        run(antenna, 0.8f);
        assertTrue(contacts.stream().anyMatch(c -> c.kind() == ColliderKind.ANTENNA));
    }

    @Test
    void ballBouncesOffAPlayersBodyAndReportsThePlayer() {
        VolleyballPlayer player = new VolleyballPlayer(7, "Test", 1, PlayerRole.OUTSIDE_HITTER, CourtSide.HOME,
                1.95f, new PlayerAttributes());
        player.placeAt(-5f, 0f);
        collisions.setPlayers(List.of(player));
        collisions.updateBodies();
        Ball ball = new Ball();
        ball.launch(new Vector3(-5f + 0.02f, 4.5f, 0f), Vector3.Zero, Vector3.Zero, 1);
        contacts.clear();
        run(ball, 1.2f);
        BallContact touch = contacts.stream().filter(c -> c.kind() == ColliderKind.PLAYER_BODY).findFirst().orElseThrow();
        assertEquals(7, touch.playerId());
        assertTrue(touch.position().y > 1.9f, "hit the head first");
    }

    @Test
    void manyRandomLaunchesStayFinite() {
        collisions.buildArena(new Net(NetCategory.WOMEN));
        java.util.Random random = new java.util.Random(5);
        for (int n = 0; n < 50; n++) {
            Ball ball = new Ball();
            Vector3 v = new Vector3(random.nextFloat() * 70 - 35, random.nextFloat() * 40 - 10, random.nextFloat() * 40 - 20);
            Vector3 w = new Vector3(random.nextFloat() * 200 - 100, random.nextFloat() * 200 - 100, random.nextFloat() * 200 - 100);
            ball.launch(new Vector3(random.nextFloat() * 16 - 8, 0.5f + random.nextFloat() * 6, random.nextFloat() * 8 - 4), v, w, n);
            for (int i = 0; i < 600; i++) {
                physics.step(ball, DT, i * DT, null);
                assertTrue(Float.isFinite(ball.position.len()) && Float.isFinite(ball.velocity.len()));
                assertTrue(ball.position.y >= Ball.RADIUS - 0.01f);
                assertTrue(ball.position.y <= PhysicsConstants.CEILING_HEIGHT);
                assertTrue(Math.abs(ball.position.x) <= CollisionSystem.WALL_X && Math.abs(ball.position.z) <= CollisionSystem.WALL_Z);
            }
        }
    }
}
