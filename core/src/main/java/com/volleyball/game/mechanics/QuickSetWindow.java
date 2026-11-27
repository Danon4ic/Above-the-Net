package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.PhysicsConstants;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.world.CourtDimensions;

/** Reads whether a low, short set can meet a hitter's hands during the current jump. */
public final class QuickSetWindow {
    private static final float GRAVITY = PhysicsConstants.GRAVITY;
    private final Vector3 hand = new Vector3();
    private final TrajectoryPredictor.Prediction pass = new TrajectoryPredictor.Prediction();

    /** Estimates when a reachable pass arrives at both hands and writes the expected release point. */
    public float passLead(VolleyballPlayer setter, Ball ball, TrajectoryPredictor predictor, Vector3 release) {
        ContactZone.idealPoint(setter, ContactType.OVERHEAD_PASS, null, hand);
        if (ball.velocity.y <= 0f && ball.position.y <= hand.y + 0.17f && ball.position.dst2(hand) <= 0.35f * 0.35f) {
            release.set(ball.position);
            return 0f;
        }
        pass.watchHeight = hand.y;
        predictor.predict(ball, 0.28f, pass);
        if (!pass.reachesWatchHeight || pass.timeToDescent > 0.28f || pass.descentPoint.dst2(hand) > 0.60f * 0.60f) return Float.NaN;
        release.set(pass.descentPoint);
        return pass.timeToDescent;
    }

    /** Writes the hitting point and returns the flight time, or NaN if the window is closed. */
    public float read(VolleyballPlayer hitter, Vector3 release, float netTop, float lead, Vector3 target) {
        float side = hitter.side.sign();
        if (!hitter.isOnCourt() || !hitter.isUpright() || !hitter.isAirborne() || lead < 0f || lead > 0.24f
                || release.x * side < 0.45f || release.x * side > 3.2f
                || release.y < netTop - 0.57f) return Float.NaN;
        ContactZone.idealPoint(hitter, ContactType.ATTACK, null, hand);
        for (int step = 0; step <= 8; step++) {
            float flight = 0.15f + step * 0.02f;
            float t = lead + flight;
            float rise = (hitter.velocity.y - 0.5f * GRAVITY * t) * t;
            float x = hand.x + hitter.velocity.x * t;
            float z = hand.z + hitter.velocity.z * t;
            float y = hand.y + rise - 0.14f;
            float distance = (float) Math.hypot(x - release.x, z - release.z);
            if (hitter.velocity.y - GRAVITY * t < -2.3f || hitter.position.y + rise < 0.12f
                    || y < netTop + 0.20f || y - release.y > 1.30f || y - release.y < -0.2f) continue;
            if (x * side < 0.56f || Math.abs(z) > CourtDimensions.HALF_WIDTH - 0.40f
                    || distance < 0.45f || distance > Math.min(2.85f, flight * 12f)) continue;
            target.set(x, y, z);
            return flight;
        }
        return Float.NaN;
    }
}
