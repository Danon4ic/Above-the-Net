package com.volleyball.game.physics;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropAtNetTest {
    private final TrajectoryPredictor predictor = new TrajectoryPredictor(new Aerodynamics());
    private final TrajectoryPredictor.Prediction forecast = new TrajectoryPredictor.Prediction();

    private void predict(Vector3 from, Vector3 velocity) {
        forecast.watchHeight = 1.0f;
        predictor.predict(from, velocity, new Vector3(), 6f, forecast);
        TrajectoryPredictor.dropAtNet(forecast, 2.43f, from.x);
    }

    @Test
    void aPassIntoTheNetComesDownOnTheOwnSide() {
        // A pass from deep on the home side (x < 0) that reaches the net plane at about 1.5 m.
        predict(new Vector3(-7f, 0.8f, 1f), new Vector3(4.2f, 9.6f, 0f));
        assertTrue(forecast.netCrossing.y < 2.43f, "the pass meets the net below the top: " + forecast.netCrossing.y);
        assertTrue(forecast.descentPoint.x < 0f, "the ball is still the home team's: " + forecast.descentPoint.x);
        assertEquals(1f, forecast.descentPoint.z, 0.2f);
    }

    @Test
    void aBallOverTheNetStaysOverTheNet() {
        predict(new Vector3(-7f, 0.8f, 1f), new Vector3(6f, 9.6f, 0f));
        assertTrue(forecast.netCrossing.y > 2.6f, "clears the net: " + forecast.netCrossing.y);
        assertTrue(forecast.descentPoint.x > 0f, "comes down on the other side: " + forecast.descentPoint.x);
    }
}
