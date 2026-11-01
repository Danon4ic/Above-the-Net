package com.volleyball.game.physics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrajectoryPredictorTest {
    private final Aerodynamics aerodynamics = new Aerodynamics();
    private final TrajectoryPredictor predictor = new TrajectoryPredictor(aerodynamics);

    @Test
    void forecastOfASpinningBallMatchesTheSimulation() {
        CollisionSystem collisions = new CollisionSystem();
        collisions.buildFloorOnly();
        BallPhysics physics = new BallPhysics(aerodynamics, collisions);
        Ball ball = new Ball();
        Vector3 v = new Vector3(18f, 5f, 1f);
        ball.launch(new Vector3(-9f, 3f, 0f), v, LaunchSolver.spinFor(v, 35f, 5f, new Vector3()), 99);

        TrajectoryPredictor.Prediction forecast = predictor.predict(ball, 6f, new TrajectoryPredictor.Prediction());
        assertTrue(forecast.lands);

        Vector3[] landed = new Vector3[1];
        double time = 0;
        while (landed[0] == null && time < 6) {
            physics.step(ball, 1f / 120f, time, c -> {
                if (c.kind() == ColliderKind.FLOOR && landed[0] == null) landed[0] = c.position();
            });
            time += 1f / 120f;
        }
        assertNotNull(landed[0]);
        assertEquals(0f, landed[0].dst(forecast.landing), 0.02f);
        assertEquals(time, forecast.timeToLanding, 1f / 60f);
    }

    @Test
    void forecastUsesOnlyObservableState() {
        // Two float serves identical in everything a player can see but with different hidden
        // seeds get the same forecast, although they really fly differently.
        Vector3 from = new Vector3(-11f, 2.8f, 0f);
        Vector3 v = new Vector3(16.5f, 2.2f, 0f);
        Ball a = new Ball();
        Ball b = new Ball();
        a.launch(from, v, Vector3.Zero, 1);
        b.launch(from, v, Vector3.Zero, 2);
        TrajectoryPredictor.Prediction pa = predictor.predict(a, 6f, new TrajectoryPredictor.Prediction());
        TrajectoryPredictor.Prediction pb = predictor.predict(b, 6f, new TrajectoryPredictor.Prediction());
        assertEquals(0f, pa.landing.dst(pb.landing), 1e-6f);
    }

    @Test
    void reportsApexAndNetCrossing() {
        TrajectoryPredictor.Prediction p = predictor.predict(new Vector3(-6f, 1f, 0f), new Vector3(8f, 7f, 0f),
                Vector3.Zero, 6f, new TrajectoryPredictor.Prediction());
        assertTrue(p.crossesNetPlane);
        assertEquals(0f, p.netCrossing.x, 0.02f);
        assertTrue(p.netCrossing.y > 2.43f, "clears a men's net: " + p.netCrossing.y);
        assertTrue(p.apex.y > 3f && p.timeToApex > 0.4f && p.timeToApex < 0.8f);
        assertTrue(p.timeToNetCrossing < p.timeToLanding);
        assertTrue(p.pathSize() > 10);
    }

    @Test
    void launchSolverHitsTheTargetWithSpin() {
        LaunchSolver solver = new LaunchSolver(predictor);
        LaunchSolver.Solution s = new LaunchSolver.Solution();
        TrajectoryPredictor.Prediction p = new TrajectoryPredictor.Prediction();
        Vector3 start = new Vector3(-10f, 3f, -1f);

        solver.solve(start, 5f, 2.5f, 22f, 40f, 0f, s);
        assertTrue(s.reachable);
        predictor.predict(start, s.velocity, s.spin, 6f, p);
        assertEquals(0f, p.landing.dst(5f, Ball.RADIUS, 2.5f), 0.08f);

        solver.solve(start, 6f, -2f, 17f, 5f, 25f, s);
        assertTrue(s.reachable);
        predictor.predict(start, s.velocity, s.spin, 6f, p);
        assertEquals(0f, p.landing.dst(6f, Ball.RADIUS, -2f), 0.08f);
        assertEquals(17f, s.velocity.len(), 0.01f, "speed is preserved");

        solver.solve(start, 30f, 0f, 6f, 0f, 0f, s);
        assertFalse(s.reachable, "6 m/s cannot reach 40 m");
    }

    @Test
    void solverCanTradePaceForSafetyOverTheNet() {
        LaunchSolver solver = new LaunchSolver(predictor);
        LaunchSolver.Solution s = new LaunchSolver.Solution();
        TrajectoryPredictor.Prediction p = new TrajectoryPredictor.Prediction();
        Vector3 start = new Vector3(-9.4f, 3.25f, -1f);
        float clearance = 2.43f + Ball.RADIUS + 0.25f;

        solver.solve(start, 6f, 2f, 28f, 45f, 0f, s);
        predictor.predict(start, s.velocity, s.spin, 6f, p);
        assertTrue(p.netCrossing.y < clearance, "the flattest 28 m/s serve skims the net: " + p.netCrossing.y);

        solver.solveOverNet(start, 6f, 2f, 18f, 28f, 45f, 0f, clearance, s);
        assertTrue(s.clearsNet && s.reachable);
        assertTrue(s.velocity.len() < 28f, "slows down to clear the net");
        predictor.predict(start, s.velocity, s.spin, 6f, p);
        assertTrue(p.netCrossing.y >= clearance - 1e-3f, "crosses at " + p.netCrossing.y);
        assertEquals(0f, p.landing.dst(6f, Ball.RADIUS, 2f), 0.08f);
    }
}
