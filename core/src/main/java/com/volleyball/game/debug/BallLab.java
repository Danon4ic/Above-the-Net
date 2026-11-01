package com.volleyball.game.debug;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.BallContactListener;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;

/**
 * Physics test bench: launches the ball with reproducible presets (like a ball machine) and
 * measures where it really lands compared with the forecast made at launch. Used to verify the
 * ball model in the running game before the serve/attack mechanics exist, and later reused by
 * the training mode.
 */
public final class BallLab implements BallContactListener {
    /** Contact patch radius of a ball hitting the floor; overlap with a line means "in". */
    static final float CONTACT_PATCH_RADIUS = 0.025f;

    public enum Preset {
        UNDERHAND("Underhand serve: slow, slight topspin"),
        FLOAT("Float serve: 17 m/s, no spin - watch it wobble"),
        TOPSPIN_JUMP("Jump serve: 23 m/s, heavy topspin - dives"),
        SIDESPIN("Sidespin serve: curves to the left"),
        INTO_NET("Hard hit into the net mesh"),
        ANTENNA("Ball into the antenna"),
        DROP_ON_PLAYER("Drop the ball on the controlled player"),
        FAST_SPIKE("30 m/s spike from the net (tunnelling check)");

        public final String description;

        Preset(String description) {
            this.description = description;
        }
    }

    private final GameWorld world;
    private final LaunchSolver solver;
    private final LaunchSolver.Solution solution = new LaunchSolver.Solution();
    private final TrajectoryPredictor.Prediction forecast = new TrajectoryPredictor.Prediction();
    private final RandomXS128 random = new RandomXS128();
    private final Vector3 start = new Vector3();
    private final Vector3 velocity = new Vector3();
    private final Vector3 spin = new Vector3();

    private Preset lastPreset;
    private boolean awaitingLanding;
    /** Set when the ball touched anything before landing: the launch forecast no longer applies. */
    private String deflectedBy;
    private boolean forecastLands;
    private final Vector3 forecastLanding = new Vector3();
    private String report = "Press 1-8 to launch a test ball";

    public BallLab(GameWorld world) {
        this.world = world;
        this.solver = new LaunchSolver(world.predictor());
        park();
    }

    /** Puts the ball on the ball machine next to the home service zone. */
    public void park() {
        world.ball().hold(new Vector3(-CourtDimensions.HALF_LENGTH - 1.5f, 1.0f, CourtDimensions.HALF_WIDTH + 1f));
        awaitingLanding = false;
        report = "Ball parked. Press 1-8 to launch a test ball";
    }

    public void fire(Preset preset, VolleyballPlayer controlled) {
        lastPreset = preset;
        switch (preset) {
            case UNDERHAND -> overNet(start.set(-10.5f, 0.9f, 2f), 5.5f, -1.5f, 11f, 15f, 4f, 0f);
            case FLOAT -> overNet(start.set(-11f, 2.85f, 0f), 6.5f, 1.5f, 15f, 19f, 0f, 0f);
            case TOPSPIN_JUMP -> overNet(start.set(-9.4f, 3.25f, -1f), 6f, 2f, 21f, 27f, 45f, 0f);
            case SIDESPIN -> overNet(start.set(-10.5f, 2.8f, 2f), 6f, -2f, 15f, 19f, 8f, 25f);
            case INTO_NET -> direct(start.set(-2.5f, 2.0f, 0.5f), velocity.set(16f, 0.8f, 0f), 15f);
            case ANTENNA -> direct(start.set(-5f, 2.9f, 4.505f), velocity.set(12f, 2f, 0f), 0f);
            case DROP_ON_PLAYER -> {
                Vector3 p = controlled.position;
                start.set(p.x + 0.04f, p.y + controlled.height + 2.5f, p.z + 0.03f);
                direct(start, velocity.setZero(), 0f);
            }
            case FAST_SPIKE -> solved(start.set(-1f, 3.3f, -1f), 6f, 2.5f, 30f, 30f, 0f);
        }
    }

    private void solved(Vector3 from, float targetX, float targetZ, float speed, float topspin, float sidespin) {
        solver.solve(from, targetX, targetZ, speed, topspin, sidespin, solution);
        launch(from, solution.velocity, solution.spin);
    }

    /** Serve-like launch: the slowest speed in range that clears the net by at least 25 cm. */
    private void overNet(Vector3 from, float targetX, float targetZ, float minSpeed, float maxSpeed,
                         float topspin, float sidespin) {
        float clearance = world.court().net().topHeight() + Ball.RADIUS + 0.25f;
        solver.solveOverNet(from, targetX, targetZ, minSpeed, maxSpeed, topspin, sidespin, clearance, solution);
        launch(from, solution.velocity, solution.spin);
    }

    private void direct(Vector3 from, Vector3 v, float topspin) {
        LaunchSolver.spinFor(v, topspin, 0f, spin);
        launch(from, v, spin);
    }

    private void launch(Vector3 from, Vector3 v, Vector3 w) {
        world.predictor().predict(from, v, w, 8f, forecast);
        forecastLands = forecast.lands;
        forecastLanding.set(forecast.landing);
        world.ball().launch(from, v, w, random.nextLong());
        awaitingLanding = true;
        deflectedBy = null;
        report = lastPreset.description;
    }

    @Override
    public void onBallContact(BallContact contact) {
        if (!awaitingLanding) return;
        if (contact.kind() == ColliderKind.FLOOR) {
            awaitingLanding = false;
            Vector3 at = contact.position();
            boolean in = CourtDimensions.isInsideCourt(at.x, at.z, CONTACT_PATCH_RADIUS);
            String deviation;
            if (deflectedBy != null) deviation = " | after touching " + deflectedBy;
            else if (forecastLands) deviation = String.format(java.util.Locale.ROOT, " | forecast miss %.0f cm", floorDistance(at, forecastLanding) * 100f);
            else deviation = "";
            report = String.format(java.util.Locale.ROOT, "%s landed %s at (%.2f, %.2f)%s",
                    lastPreset.name(), in ? "IN" : "OUT", at.x, at.z, deviation);
        } else {
            // Any touch (net, antenna, body...) changes the flight; the landing report follows.
            deflectedBy = contact.kind() == ColliderKind.PLAYER_BODY ? "a player" : contact.kind().name();
            report = lastPreset.name() + " touched " + deflectedBy + String.format(java.util.Locale.ROOT, " at %.1f m/s", contact.impactSpeed());
        }
    }

    public String report() {
        return report;
    }

    /** Distance on the floor plane (X/Z) between two points. */
    static float floorDistance(Vector3 a, Vector3 b) {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    public Ball ball() {
        return world.ball();
    }
}
