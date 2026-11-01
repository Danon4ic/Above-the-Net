package com.volleyball.game.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Drives the 3D camera. Each mode defines a desired eye position and look-at target; the actual
 * camera follows them with frame-rate-independent exponential smoothing, so switching modes is a
 * smooth glide rather than a cut.
 *
 * <p>The third-person camera orbits the controlled player: it always stays centred on them, the
 * mouse turns it around the player, and {@link #controlForward} gives the matching movement
 * direction, so "forward" on the keyboard is always "away from the camera".
 */
public final class CameraController {
    private static final float MIN_PITCH = 5f * MathUtils.degreesToRadians;
    private static final float MAX_PITCH = 85f * MathUtils.degreesToRadians;
    private static final float MIN_DISTANCE = 3f;
    private static final float MAX_DISTANCE = 45f;
    /** Radians of orbit rotation per pixel of mouse drag at sensitivity 1. */
    private static final float ORBIT_RADIANS_PER_PIXEL = 0.005f;
    /** First-person mouse look: radians per pixel of mouse movement at sensitivity 1 (finer than a drag). */
    private static final float LOOK_RADIANS_PER_PIXEL = 0.0022f;
    static final float FOLLOW_MIN_PITCH = 5f * MathUtils.degreesToRadians;
    static final float FOLLOW_MAX_PITCH = 65f * MathUtils.degreesToRadians;
    static final float FOLLOW_DEFAULT_PITCH = 24f * MathUtils.degreesToRadians;
    static final float FOLLOW_MIN_DISTANCE = 2.5f;
    static final float FOLLOW_MAX_DISTANCE = 12f;
    static final float FOLLOW_DEFAULT_DISTANCE = 5.4f;
    /** Smoothing rates: the follow camera must keep up with a running player; presets glide. */
    private static final float FOLLOW_SMOOTHING = 10f;
    /** The eyes move with the head: almost no lag (just enough to soften the landing of a jump). */
    private static final float FIRST_PERSON_SMOOTHING = 40f;
    private static final float PRESET_SMOOTHING = 4f;

    private final PerspectiveCamera camera;
    private final Vector3 eye = new Vector3();
    private final Vector3 target = new Vector3();
    private final Vector3 desiredEye = new Vector3();
    private final Vector3 desiredTarget = new Vector3();
    private final Vector3 orbitTarget = new Vector3(0f, 1f, 0f);
    private final Vector3 tmp = new Vector3();
    private final Vector3 followTarget = new Vector3(-6f, 0f, 0f);
    /** +1 when the followed player attacks towards +X (home side), -1 otherwise. */
    private float followDirection = 1f;
    /** Heading of the follow camera (yaw: 0 looks along +X, direction = (cos, 0, -sin)). */
    private float followYaw = 0f;
    private float followPitch = FOLLOW_DEFAULT_PITCH;
    /** First person: the eyes' height above the followed point (the player's feet), and the view's pitch. */
    private float eyeHeight = 1.78f;
    private float firstPersonPitch = FIRST_PERSON_DEFAULT_PITCH;
    /** First person: the eyes of the drawn figure (they crouch, jump, dive and fall with it), if known. */
    private final Vector3 eyes = new Vector3();
    private boolean eyesKnown;
    static final float FIRST_PERSON_DEFAULT_PITCH = 4f * MathUtils.degreesToRadians;
    private float followDistance = FOLLOW_DEFAULT_DISTANCE;
    private boolean followInitialised;
    /** The attacker / server camera's subject and the direction (±1 along X) it plays towards. */
    private final Vector3 subject = new Vector3();
    private float subjectDirection = 1f;

    private CameraMode mode = CameraMode.TV_BROADCAST;
    private float orbitYaw = 0f;
    private float orbitPitch = 30f * MathUtils.degreesToRadians;
    private float orbitDistance = 22f;
    private float sensitivity = 1f;

    public CameraController(float fieldOfView, float viewportWidth, float viewportHeight) {
        camera = new PerspectiveCamera(fieldOfView, viewportWidth, viewportHeight);
        camera.near = 0.25f;
        camera.far = 250f;
        setMode(CameraMode.TV_BROADCAST, true);
    }

    public PerspectiveCamera camera() {
        return camera;
    }

    public CameraMode mode() {
        return mode;
    }

    public void setMode(CameraMode newMode, boolean instant) {
        mode = newMode;
        computeDesired();
        if (instant) {
            eye.set(desiredEye);
            target.set(desiredTarget);
            applyToCamera();
        }
    }

    public void cycleMode() {
        setMode(mode.next(), false);
    }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
    }

    public void setFieldOfView(float degrees) {
        camera.fieldOfView = degrees;
    }

    /**
     * Sets the player followed by {@link CameraMode#THIRD_PERSON}. By default the camera sits
     * behind the player looking towards the net, which is how a volleyball player reads the game;
     * when the followed player changes sides the view is reset to that default.
     */
    public void follow(Vector3 playerPosition, float attackDirectionSign) {
        followTarget.set(playerPosition);
        float direction = Math.signum(attackDirectionSign) == 0f ? 1f : Math.signum(attackDirectionSign);
        if (!followInitialised || direction != followDirection) {
            followDirection = direction;
            followInitialised = true;
            resetFollowView();
        }
    }

    /** First person: the followed player's standing height (the eyes are a little below the top of the head). */
    public void setEyeHeight(float playerHeight) {
        eyeHeight = playerHeight * 0.93f;
    }

    /** First person: the eyes of the followed player as drawn (null: unknown, a fixed height above the feet). */
    public void setEyes(Vector3 at) {
        eyesKnown = at != null;
        if (eyesKnown) eyes.set(at);
    }

    /** The player the attacker and server cameras look over (and the X direction of their play). */
    public void setSubject(Vector3 position, float playsTowardsSign) {
        subject.set(position);
        subjectDirection = Math.signum(playsTowardsSign) == 0f ? 1f : Math.signum(playsTowardsSign);
    }

    /** Puts the follow camera back behind the player, facing the net. */
    public void resetFollowView() {
        followYaw = followDirection > 0f ? 0f : MathUtils.PI;
        followPitch = FOLLOW_DEFAULT_PITCH;
        firstPersonPitch = FIRST_PERSON_DEFAULT_PITCH;
        followDistance = FOLLOW_DEFAULT_DISTANCE;
    }

    /**
     * Rotates the view with the mouse: around the player in third person, otherwise as a free
     * orbit (entering it from the current view).
     */
    public void orbit(float deltaXPixels, float deltaYPixels) {
        float yawDelta = deltaXPixels * ORBIT_RADIANS_PER_PIXEL * sensitivity;
        float pitchDelta = deltaYPixels * ORBIT_RADIANS_PER_PIXEL * sensitivity;
        if (mode == CameraMode.THIRD_PERSON) {
            followYaw = wrap(followYaw - yawDelta);
            followPitch = MathUtils.clamp(followPitch + pitchDelta, FOLLOW_MIN_PITCH, FOLLOW_MAX_PITCH);
            return;
        }
        if (mode == CameraMode.FIRST_PERSON) {
            // Dragging up looks up (the opposite of the orbit cameras, which go over the top).
            followYaw = wrap(followYaw - yawDelta);
            firstPersonPitch = MathUtils.clamp(firstPersonPitch - pitchDelta, -1.0f, 1.2f);
            return;
        }
        if (mode != CameraMode.FREE_ORBIT) enterOrbitFromCurrentView();
        orbitYaw -= yawDelta;
        orbitPitch = MathUtils.clamp(orbitPitch + pitchDelta, MIN_PITCH, MAX_PITCH);
    }

    /**
     * First-person mouse look: the view follows the mouse's movement (no button held). Moving the
     * mouse up looks up.
     */
    public void look(float deltaXPixels, float deltaYPixels) {
        if (mode != CameraMode.FIRST_PERSON) return;
        followYaw = wrap(followYaw - deltaXPixels * LOOK_RADIANS_PER_PIXEL * sensitivity);
        firstPersonPitch = MathUtils.clamp(firstPersonPitch - deltaYPixels * LOOK_RADIANS_PER_PIXEL * sensitivity, -1.0f, 1.2f);
    }

    /** Positive amount zooms out. */
    public void zoom(float amount) {
        if (mode == CameraMode.FIRST_PERSON) return;
        if (mode == CameraMode.THIRD_PERSON) {
            followDistance = MathUtils.clamp(followDistance * (1f + 0.1f * amount), FOLLOW_MIN_DISTANCE, FOLLOW_MAX_DISTANCE);
            return;
        }
        if (mode != CameraMode.FREE_ORBIT) enterOrbitFromCurrentView();
        orbitDistance = MathUtils.clamp(orbitDistance * (1f + 0.1f * amount), MIN_DISTANCE, MAX_DISTANCE);
    }

    /**
     * Horizontal "forward" for player movement: where the camera is (or is about to be) looking,
     * projected onto the floor. It uses the camera's target orientation rather than the smoothed
     * one, so movement does not drift while the camera is still catching up.
     */
    public Vector3 controlForward(Vector3 out) {
        if (mode == CameraMode.THIRD_PERSON || mode == CameraMode.FIRST_PERSON) {
            return out.set(MathUtils.cos(followYaw), 0f, -MathUtils.sin(followYaw));
        }
        out.set(desiredTarget).sub(desiredEye);
        out.y = 0f;
        if (out.len2() < 1e-8f) out.set(camera.direction.x, 0f, camera.direction.z);
        if (out.len2() < 1e-8f) out.set(Vector3.X);
        return out.nor();
    }

    public float followYaw() {
        return followYaw;
    }

    public float followPitch() {
        return followPitch;
    }

    public float followDistance() {
        return followDistance;
    }

    public float orbitPitch() {
        return orbitPitch;
    }

    public float orbitDistance() {
        return orbitDistance;
    }

    public Vector3 eye() {
        return eye;
    }

    public Vector3 desiredEye() {
        return desiredEye;
    }

    public void update(float dt) {
        computeDesired();
        float smoothing = mode == CameraMode.FIRST_PERSON ? FIRST_PERSON_SMOOTHING
                : mode == CameraMode.THIRD_PERSON ? FOLLOW_SMOOTHING : PRESET_SMOOTHING;
        float blend = 1f - (float) Math.exp(-smoothing * Math.max(0f, dt));
        eye.lerp(desiredEye, blend);
        target.lerp(desiredTarget, blend);
        applyToCamera();
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    private void enterOrbitFromCurrentView() {
        orbitTarget.set(target);
        tmp.set(eye).sub(target);
        orbitDistance = MathUtils.clamp(tmp.len(), MIN_DISTANCE, MAX_DISTANCE);
        orbitPitch = MathUtils.clamp((float) Math.asin(MathUtils.clamp(tmp.y / tmp.len(), -1f, 1f)),
                MIN_PITCH, MAX_PITCH);
        orbitYaw = MathUtils.atan2(tmp.x, tmp.z);
        mode = CameraMode.FREE_ORBIT;
    }

    private void computeDesired() {
        computeDesiredUnturned();
        if (sidesSwapped && hallFixed()) {
            desiredEye.set(-desiredEye.x, desiredEye.y, -desiredEye.z);
            desiredTarget.set(-desiredTarget.x, desiredTarget.y, -desiredTarget.z);
        }
    }

    private void computeDesiredUnturned() {
        switch (mode) {
            // Main broadcast camera: high behind the home end line, the whole court and both teams in view.
            case TV_BROADCAST -> {
                desiredEye.set(-25f, 10.5f, 0f);
                desiredTarget.set(1.5f, 0f, 0f);
            }
            case THIRD_PERSON -> {
                // Orbit around the player: directly behind along the heading, looking past them.
                // Height follows the player only partly, so jumps do not shake the view.
                float y = followTarget.y * 0.4f;
                float fx = MathUtils.cos(followYaw);
                float fz = -MathUtils.sin(followYaw);
                float horizontal = followDistance * MathUtils.cos(followPitch);
                desiredEye.set(followTarget.x - fx * horizontal, 1.0f + y + followDistance * MathUtils.sin(followPitch),
                        followTarget.z - fz * horizontal);
                desiredTarget.set(followTarget.x + fx * 3.5f, 0.9f + y, followTarget.z + fz * 3.5f);
            }
            case FIRST_PERSON -> {
                // At the eyes, looking along the view's heading; the jump lifts the eyes fully.
                float fx = MathUtils.cos(followYaw);
                float fz = -MathUtils.sin(followYaw);
                float c = MathUtils.cos(firstPersonPitch);
                if (eyesKnown) desiredEye.set(eyes);
                else desiredEye.set(followTarget.x + fx * 0.08f, followTarget.y + eyeHeight, followTarget.z + fz * 0.08f);
                desiredTarget.set(desiredEye).add(fx * c * 5f, MathUtils.sin(firstPersonPitch) * 5f, fz * c * 5f);
            }
            // Elevated side view from the stands opposite the referee, home team on the left. Offset
            // from the net plane so the net is seen at an angle rather than edge-on.
            case SIDELINE -> {
                desiredEye.set(-2.5f, 8.5f, 19f);
                desiredTarget.set(0f, 0.6f, 0f);
            }
            case BEHIND_BASELINE -> {
                desiredEye.set(-21f, 5.5f, 0f);
                desiredTarget.set(2f, 1.2f, 0f);
            }
            // Below the roof trusses. The slight tilt keeps the view direction from becoming
            // parallel to the camera's up vector.
            case OVERHEAD -> {
                desiredEye.set(0f, 14f, 3f);
                desiredTarget.set(0f, 0f, 0.4f);
            }
            // Over the hitter's shoulder, a little to the side and low: the block and the court behind it.
            case ATTACKER -> {
                float d = subjectDirection;
                desiredEye.set(subject.x - d * 4.6f, 2.6f + subject.y * 0.5f, subject.z + 1.6f * Math.signum(subject.z == 0f ? 1f : subject.z));
                desiredTarget.set(d * 2.5f, 2.2f, subject.z * 0.5f);
            }
            // Behind the server, at head height, looking across the net into the receivers' court.
            case SERVER -> {
                float d = subjectDirection;
                desiredEye.set(subject.x - d * 3.4f, 2.3f, subject.z * 0.85f);
                desiredTarget.set(d * 5f, 0.8f, subject.z * 0.3f);
            }
            case FREE_ORBIT -> {
                float horizontal = orbitDistance * MathUtils.cos(orbitPitch);
                desiredEye.set(orbitTarget.x + horizontal * MathUtils.sin(orbitYaw),
                        orbitTarget.y + orbitDistance * MathUtils.sin(orbitPitch),
                        orbitTarget.z + horizontal * MathUtils.cos(orbitYaw));
                desiredTarget.set(orbitTarget);
            }
        }
    }

    private static float wrap(float angle) {
        while (angle > MathUtils.PI) angle -= MathUtils.PI2;
        while (angle < -MathUtils.PI) angle += MathUtils.PI2;
        return angle;
    }

    /**
     * The teams changed ends: the venue is turned around the court (see ArenaScene), and so are
     * the cameras fixed in the hall (TV, sideline, baseline, overhead); the view jumps there.
     */
    public void setSidesSwapped(boolean swapped) {
        if (swapped == sidesSwapped) return;
        sidesSwapped = swapped;
        computeDesired();
        eye.set(desiredEye);
        target.set(desiredTarget);
        applyToCamera();
    }

    private boolean sidesSwapped;

    private boolean hallFixed() {
        return mode == CameraMode.TV_BROADCAST || mode == CameraMode.SIDELINE || mode == CameraMode.BEHIND_BASELINE
                || mode == CameraMode.OVERHEAD;
    }

    private void applyToCamera() {
        // First person: the own hands come close to the eyes (an overhead pass), so the near plane is close too.
        camera.near = mode == CameraMode.FIRST_PERSON ? 0.06f : 0.25f;
        camera.position.set(eye);
        camera.up.set(Vector3.Y);
        camera.direction.set(target).sub(eye).nor();
        camera.update();
    }
}
