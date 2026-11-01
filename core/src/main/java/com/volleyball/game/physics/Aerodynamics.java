package com.volleyball.game.physics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;

/**
 * Forces of air on a flying volleyball, expressed as accelerations.
 *
 * <p>Model (all forces scale with the dynamic pressure ½ρv²A):
 * <ul>
 *   <li><b>Drag</b> {@code -K·Cd·|v|·v}. Cd ≈ 0.50 at low speed. For a ball with little spin the
 *       boundary layer turns turbulent between ~11 and ~18 m/s ("drag crisis") and Cd drops to
 *       ~0.25; spin disturbs the boundary layer and suppresses the crisis.</li>
 *   <li><b>Magnus lift</b> {@code K·Cl·|v|²·(ω̂ × v̂)} with a saturating lift coefficient of the
 *       spin parameter S = rω/v. Topspin makes the ball dive, backspin floats it, sidespin curves it.</li>
 *   <li><b>Knuckle (float) effect</b>: at high speed and almost no spin, asymmetric boundary-layer
 *       separation around the panels creates fluctuating side forces. They are modelled as a
 *       pseudo-random force perpendicular to the velocity, driven by the flight's seed: a slow
 *       wander, a quicker flutter and, late in the flight, a sudden "break" (the ball dips or
 *       darts sideways as it slows through the drag crisis). How lively a float is differs from
 *       serve to serve.
 *       This part is deliberately <em>not</em> available to {@link TrajectoryPredictor}: nobody on
 *       court can know how a float serve will wobble.</li>
 * </ul>
 * where {@code K = ½ρA/m}.
 */
public final class Aerodynamics {
    public static final float AREA = MathUtils.PI * Ball.RADIUS * Ball.RADIUS;
    public static final float K = 0.5f * PhysicsConstants.AIR_DENSITY * AREA / Ball.MASS;

    static final float CD_SUBCRITICAL = 0.50f;
    static final float CD_SUPERCRITICAL = 0.25f;
    static final float CRISIS_START_SPEED = 11f;
    static final float CRISIS_END_SPEED = 18f;
    static final float MAX_LIFT_COEFFICIENT = 0.40f;
    /** Peak side-force coefficient of the knuckle effect. */
    static final float KNUCKLE_FORCE_COEFFICIENT = 0.17f;

    private final Vector3 tmp = new Vector3();
    private final Vector3 side = new Vector3();
    private final Vector3 lift = new Vector3();

    private boolean airEnabled = true;
    private boolean knuckleEnabled = true;

    public void setAirEnabled(boolean enabled) {
        airEnabled = enabled;
    }

    public boolean airEnabled() {
        return airEnabled;
    }

    public void setKnuckleEnabled(boolean enabled) {
        knuckleEnabled = enabled;
    }

    /** Drag coefficient for the given speed (m/s) and spin parameter S = rω/v. */
    public static float dragCoefficient(float speed, float spinParameter) {
        float crisis = smoothstep(CRISIS_START_SPEED, CRISIS_END_SPEED, speed);
        // Spin above S ≈ 0.15 keeps the boundary layer from going supercritical cleanly.
        float suppression = MathUtils.clamp(spinParameter / 0.15f, 0f, 1f);
        float drop = (CD_SUBCRITICAL - CD_SUPERCRITICAL) * crisis * (1f - 0.7f * suppression);
        return CD_SUBCRITICAL - drop;
    }

    /** Magnus lift coefficient: ~linear for small S, saturating at {@link #MAX_LIFT_COEFFICIENT}. */
    public static float liftCoefficient(float spinParameter) {
        if (spinParameter <= 0f) return 0f;
        return MAX_LIFT_COEFFICIENT * spinParameter / (0.1f + spinParameter);
    }

    /**
     * How strongly a ball "floats": 1 for a fast ball with no spin, 0 for a slow or spinning ball.
     * Below ~1 rev/s the seams stay put long enough to make the flight erratic.
     */
    public static float knuckleFactor(float speed, float spinRate) {
        float fastEnough = smoothstep(8f, 14f, speed);
        float lowSpin = 1f - smoothstep(1.5f, 6f, spinRate);
        return fastEnough * lowSpin;
    }

    /**
     * Computes the total acceleration of the ball (gravity + air).
     *
     * @param includeHiddenEffects whether to include the knuckle fluctuations, which depend on
     *                             the hidden flight seed. Predictions must pass {@code false}.
     */
    public Vector3 acceleration(Vector3 velocity, Vector3 spin, long flightSeed, float flightTime,
                                boolean includeHiddenEffects, Vector3 out) {
        out.set(0f, -PhysicsConstants.GRAVITY, 0f);
        if (!airEnabled) return out;
        float speed = velocity.len();
        if (speed < 1e-4f) return out;
        float spinRate = spin.len();
        float spinParameter = Ball.RADIUS * spinRate / speed;

        out.mulAdd(velocity, -K * dragCoefficient(speed, spinParameter) * speed);

        if (spinRate > 1e-4f) {
            // ω̂ × v̂ scaled by K·Cl·v².
            lift.set(spin).crs(velocity).scl(1f / (spinRate * speed));
            out.mulAdd(lift, K * liftCoefficient(spinParameter) * speed * speed);
        }

        if (includeHiddenEffects && knuckleEnabled) {
            float factor = knuckleFactor(speed, spinRate);
            if (factor > 0f) addKnuckleForce(velocity, speed, factor, flightSeed, flightTime, out);
        }
        return out;
    }

    private void addKnuckleForce(Vector3 velocity, float speed, float factor, long seed, float time, Vector3 out) {
        // Orthonormal basis perpendicular to the flight direction: "side" is horizontal,
        // "up" is the remaining direction (mostly vertical).
        side.set(velocity).crs(Vector3.Y);
        if (side.len2() < 1e-8f) side.set(Vector3.X);
        side.nor();
        tmp.set(velocity).crs(side).nor();
        // Two incommensurate low frequencies per axis give a smooth, non-repeating wander; a
        // quicker flutter rides on it.
        float s = wobble(seed, 0, time) + 0.3f * flutter(seed, 2, time);
        float u = (wobble(seed, 1, time) + 0.3f * flutter(seed, 3, time)) * 0.6f;
        // The late break: a short burst of force in a direction of its own (more often down).
        long h = mix(seed * 31 + 7);
        float breakTime = 0.55f + ((h & 0xFF) / 255f) * 0.65f;
        float breakWidth = 0.1f + (((h >>> 8) & 0xFF) / 255f) * 0.08f;
        float angle = (((h >>> 16) & 0xFFFF) / 65536f) * MathUtils.PI2;
        float burst = 2.2f * (float) Math.exp(-0.5f * sq((time - breakTime) / breakWidth));
        s += burst * MathUtils.cos(angle);
        u += burst * (0.8f * MathUtils.sin(angle) - 0.35f);
        // Some floats dance, some barely move.
        float liveliness = 0.6f + (((h >>> 32) & 0xFF) / 255f) * 0.9f;
        float magnitude = K * KNUCKLE_FORCE_COEFFICIENT * speed * speed * factor * liveliness;
        out.mulAdd(side, magnitude * s);
        out.mulAdd(tmp, magnitude * u);
    }

    /** A quicker irregular signal in [-1, 1] (3-5 Hz), from the seed and channel. */
    static float flutter(long seed, int channel, float time) {
        long h = mix(seed * 131 + channel);
        float phase1 = (h & 0xFFFF) / 65536f * MathUtils.PI2;
        float phase2 = ((h >>> 16) & 0xFFFF) / 65536f * MathUtils.PI2;
        float frequency1 = 3f + ((h >>> 32) & 0xFF) / 255f * 1.2f;
        float frequency2 = 4.4f + ((h >>> 40) & 0xFF) / 255f * 1.1f;
        return 0.6f * MathUtils.sin(phase1 + MathUtils.PI2 * frequency1 * time)
                + 0.4f * MathUtils.sin(phase2 + MathUtils.PI2 * frequency2 * time);
    }

    private static float sq(float x) {
        return x * x;
    }

    /** Smooth pseudo-random signal in [-1, 1] determined by the seed and channel. */
    static float wobble(long seed, int channel, float time) {
        long h = mix(seed * 31 + channel);
        float phase1 = (h & 0xFFFF) / 65536f * MathUtils.PI2;
        float phase2 = ((h >>> 16) & 0xFFFF) / 65536f * MathUtils.PI2;
        float frequency1 = 0.6f + ((h >>> 32) & 0xFF) / 255f * 0.6f;
        float frequency2 = 1.3f + ((h >>> 40) & 0xFF) / 255f * 0.9f;
        return 0.65f * MathUtils.sin(phase1 + MathUtils.PI2 * frequency1 * time)
                + 0.35f * MathUtils.sin(phase2 + MathUtils.PI2 * frequency2 * time);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
