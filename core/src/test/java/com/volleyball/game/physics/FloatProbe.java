package com.volleyball.game.physics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Developer probe (PROBE=1): how far float serves land from the forecast made at the hit, and how
 * much of the deviation comes late (in the last 0.4 s, when the receiver has already moved).
 */
class FloatProbe {
    @Test
    void probe() throws Exception {
        Assumptions.assumeTrue(System.getenv("PROBE") != null);
        Aerodynamics aerodynamics = new Aerodynamics();
        CollisionSystem collisions = new CollisionSystem();
        collisions.buildFloorOnly();
        BallPhysics physics = new BallPhysics(aerodynamics, collisions);
        TrajectoryPredictor predictor = new TrajectoryPredictor(aerodynamics);
        TrajectoryPredictor.Prediction atHit = new TrajectoryPredictor.Prediction();
        TrajectoryPredictor.Prediction late = new TrajectoryPredictor.Prediction();
        StringBuilder out = new StringBuilder();
        float sum = 0f, max = 0f, lateSum = 0f;
        int seeds = 60;
        double time = 0;
        float[] misses = new float[seeds];
        for (int s = 0; s < seeds; s++) {
            Ball ball = new Ball();
            ball.launch(new Vector3(-11f, 2.8f, 0f), new Vector3(15.5f, 4.2f, 0f), LaunchSolver.spinFor(new Vector3(15.5f, 4.2f, 0f), 0f, 0f, new Vector3()), s + 1);
            predictor.predict(ball, 6f, atHit);
            boolean lateTaken = false;
            Vector3 landing = null;
            for (int i = 0; i < 1200 && landing == null; i++) {
                Vector3[] floor = {null};
                physics.step(ball, 1f / 120f, time, c -> {
                    if (c.kind() == ColliderKind.FLOOR && floor[0] == null) floor[0] = c.position().cpy();
                });
                time += 1f / 120f;
                if (!lateTaken && atHit.lands && ball.timeSinceHit() >= atHit.timeToLanding - 0.4f) {
                    predictor.predict(ball, 6f, late);
                    lateTaken = true;
                }
                if (floor[0] != null) landing = floor[0];
            }
            if (landing == null) {
                out.append(String.format("seed %d: no floor contact, ball at %s v %s forecast %s%n", s + 1, ball.position, ball.velocity, atHit.landing));
                continue;
            }
            if (s < 5) out.append(String.format("seed %d: landed %s forecast %s (t %.2f)%n", s + 1, landing, atHit.landing, atHit.timeToLanding));
            float miss = landing.dst(atHit.landing);
            float lateMiss = late.lands ? landing.dst(late.landing) : 0f;
            misses[s] = miss;
            sum += miss;
            lateSum += lateMiss;
            max = Math.max(max, miss);
        }
        java.util.Arrays.sort(misses);
        out.append(String.format("float serve 15.5 m/s deep: mean miss %.2f m, median %.2f, p90 %.2f, max %.2f; miss vs forecast 0.4 s before landing %.2f m%n",
                sum / seeds, misses[seeds / 2], misses[seeds * 9 / 10], max, lateSum / seeds));
        java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv().getOrDefault("PROBE_OUT", "float_probe.txt")), out.toString());
    }
}
