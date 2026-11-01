package com.volleyball.game.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.SwingStyle;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.PlayerLocomotion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws players with procedural, joint-based animation derived from their physical state.
 *
 * <ul>
 *   <li>Legs use two-bone inverse kinematics: the renderer decides where each foot should be
 *       (planted, stepping, tucked in the air) and solves hip, knee and ankle angles, so the low
 *       stance bends the knees instead of squashing the legs.</li>
 *   <li>The gait follows the movement direction relative to the body: running forward,
 *       backpedalling, or side-shuffling with the body facing the net.</li>
 *   <li>Ball actions (platform, overhead, swing, toss, block) and dives pose arms and trunk.</li>
 *   <li>The head turns towards the ball within a natural range.</li>
 * </ul>
 * Positions and facing are interpolated between fixed simulation steps.
 */
public final class PlayerRenderer implements Disposable {
    /** Distance covered in one full running cycle (two steps). */
    private static final float STRIDE_LENGTH = 1.7f;
    private static final float CROUCH_DROP = 0.24f;
    private static final float HEAD_TURN_LIMIT = 1.1f;
    /** Duration of the roll after a sideways fall. */
    private static final float ROLL_TIME = 0.45f;

    private static final Color COACH_TROUSERS = new Color(0.10f, 0.11f, 0.14f, 1f);
    private final List<Model> models = new ArrayList<>();
    private final Map<Integer, Figure> figures = new HashMap<>();
    private final Model shadowModel = MarkerModels.disc(Color.BLACK, 0.35f);
    private final Model markerModel = MarkerModels.ring(new Color(1f, 0.85f, 0.1f, 1f), 0.9f, 0.82f);
    private final ModelInstance shadow = new ModelInstance(shadowModel);
    private final ModelInstance marker = new ModelInstance(markerModel);
    /** Second person's player (local two-player matches): a cyan ring. */
    private final Model secondMarkerModel = MarkerModels.ring(new Color(0.2f, 0.9f, 1f, 1f), 0.9f, 0.82f);
    private final ModelInstance secondMarker = new ModelInstance(secondMarkerModel);
    private final Vector3 position = new Vector3();
    private final Vector3 tiltAxis = new Vector3();
    private final Quaternion tmpQuat = new Quaternion();
    /** Camera eye: a player the camera is inside of (or right against) is not drawn. */
    private Vector3 cameraPosition;
    /** Figures drawn in the last frame (redrawn as they are in the floor reflection). */
    private final List<ModelInstance> drawn = new ArrayList<>();
    /** Blob shadows under the players (the arena shader casts real soft shadows instead). */
    private boolean blobShadows = true;

    /** A posed instance and its joints. */
    private static final class Figure {
        final ModelInstance instance;
        final Node pelvis, torso, head;
        final Node[] upperArm = new Node[2];
        final Node[] forearm = new Node[2];
        final Node[] thigh = new Node[2];
        final Node[] shin = new Node[2];
        final Node[] foot = new Node[2];

        /** Smoothed pose (what is drawn): left/right arm, trunk lean and twist, pelvis drop, head pitch. */
        final ArmPose left = new ArmPose();
        final ArmPose right = new ArmPose();
        float lean;
        float twist;
        float drop;
        float headPitch;
        /** Step cycle (radians) and the travelled distance it was last advanced to. */
        float stridePhase;
        float strideAt = Float.NaN;
        boolean initialised;

        /** Moves the drawn pose a fraction {@code k} towards the target. */
        void blend(ArmPose l, ArmPose r, float targetLean, float targetTwist, float targetDrop, float k) {
            if (!initialised) {
                syncTo(l, r, targetLean, targetTwist);
                drop = targetDrop;
                return;
            }
            left.raise += (l.raise - left.raise) * k;
            left.spread += (l.spread - left.spread) * k;
            left.elbow += (l.elbow - left.elbow) * k;
            right.raise += (r.raise - right.raise) * k;
            right.spread += (r.spread - right.spread) * k;
            right.elbow += (r.elbow - right.elbow) * k;
            lean += (targetLean - lean) * k;
            twist += (targetTwist - twist) * k;
            drop += (targetDrop - drop) * k;
        }

        /** Jumps straight to a pose (dives are keyed exactly; the next blend starts from here). */
        void syncTo(ArmPose l, ArmPose r, float newLean, float newTwist) {
            left.set(l.raise, l.spread, l.elbow);
            right.set(r.raise, r.spread, r.elbow);
            lean = newLean;
            twist = newTwist;
            initialised = true;
        }

        Figure(ModelInstance instance) {
            this.instance = instance;
            pelvis = instance.getNode(PlayerModelFactory.PELVIS);
            torso = instance.getNode(PlayerModelFactory.TORSO);
            head = instance.getNode(PlayerModelFactory.HEAD);
            upperArm[0] = instance.getNode(PlayerModelFactory.UPPER_ARM_LEFT);
            upperArm[1] = instance.getNode(PlayerModelFactory.UPPER_ARM_RIGHT);
            forearm[0] = instance.getNode(PlayerModelFactory.FOREARM_LEFT);
            forearm[1] = instance.getNode(PlayerModelFactory.FOREARM_RIGHT);
            thigh[0] = instance.getNode(PlayerModelFactory.THIGH_LEFT);
            thigh[1] = instance.getNode(PlayerModelFactory.THIGH_RIGHT);
            shin[0] = instance.getNode(PlayerModelFactory.SHIN_LEFT);
            shin[1] = instance.getNode(PlayerModelFactory.SHIN_RIGHT);
            foot[0] = instance.getNode(PlayerModelFactory.FOOT_LEFT);
            foot[1] = instance.getNode(PlayerModelFactory.FOOT_RIGHT);
        }
    }

    /** Arm joint angles (radians) for one side. Positive "raise" swings the arm forward and up. */
    private static final class ArmPose {
        float raise;
        /** Positive moves the hand away from the body's centre line. */
        float spread;
        float elbow;

        void set(float raise, float spread, float elbow) {
            this.raise = raise;
            this.spread = spread;
            this.elbow = elbow;
        }
    }

    private final ArmPose leftArm = new ArmPose();
    private final ArmPose rightArm = new ArmPose();

    private final PlayerModelFactory factory = new PlayerModelFactory();

    public PlayerRenderer(List<Team> teams) {
        for (Team team : teams) {
            for (VolleyballPlayer player : team.squad()) {
                Model model = player.role == PlayerRole.LIBERO
                        ? factory.buildLibero(team.liberoShirtColor, team.shirtColor, team.shortsColor, player.number)
                        : factory.build(team.shirtColor, team.shortsColor, player.number);
                models.add(model);
                figures.put(player.id, new Figure(new ModelInstance(model)));
            }
        }
    }

    /** Adds the referee: a white shirt and dark trousers. */
    public void addReferee(VolleyballPlayer referee) {
        Model model = factory.buildCoach(new Color(0.93f, 0.93f, 0.95f, 1f), COACH_TROUSERS);
        models.add(model);
        figures.put(referee.id, new Figure(new ModelInstance(model)));
    }

    /** Adds a team's coach figure: a track jacket in the team colour and dark trousers. */
    public void addCoach(VolleyballPlayer coach, Team team) {
        Model model = factory.buildCoach(team.shirtColor, COACH_TROUSERS);
        models.add(model);
        figures.put(coach.id, new Figure(new ModelInstance(model)));
    }

    /**
     * @param lookAt point the players look at (usually the ball), or null
     */
    public void render(ModelBatch batch, Environment environment, List<VolleyballPlayer> players, float alpha,
                       VolleyballPlayer controlled, Vector3 lookAt) {
        render(batch, environment, players, alpha, controlled, null, lookAt);
    }

    /** @param second the second person's player (cyan marker), or null */
    public void render(ModelBatch batch, Environment environment, List<VolleyballPlayer> players, float alpha,
                       VolleyballPlayer controlled, VolleyballPlayer second, Vector3 lookAt) {
        if (!appending) drawn.clear();
        for (VolleyballPlayer player : players) {
            Figure figure = figures.get(player.id);
            if (figure == null) continue;
            position.set(player.previousPosition).lerp(player.position, alpha);
            boolean ownBody = player == firstPersonPlayer;
            if (!ownBody && cameraPosition != null && blocksCamera(position, player.height)) continue;
            float yaw = lerpAngle(player.previousFacingYaw, player.facingYaw, alpha);
            // Seen from inside (first person): everything but the head, which the eyes are in.
            figure.head.scale.set(ownBody ? 0.0001f : 1f, ownBody ? 0.0001f : 1f, ownBody ? 0.0001f : 1f);
            pose(figure, player, yaw, lookAt);
            float scale = player.height / PlayerModelFactory.MODEL_HEIGHT;
            figure.instance.transform.setToTranslation(position).rotateRad(Vector3.Y, yaw);
            applyTilt(figure, player, yaw);
            figure.instance.transform.scale(scale, scale, scale);
            batch.render(figure.instance, environment);
            drawn.add(figure.instance);
            if (!blobShadows) {
                drawMarkers(batch, player, controlled, second);
                continue;
            }

            // Blob shadow shrinks as the player rises and stretches when lying down.
            float shadowRadius = 0.34f / (1f + position.y);
            float stretch = 1f + 2.2f * player.bodyTilt();
            shadow.transform.setToTranslation(position.x, 0.012f, position.z).rotateRad(Vector3.Y, yaw);
            if (player.bodyTilt() > 0f) {
                float dirYaw = MathUtils.atan2(-player.diveDirection.z, player.diveDirection.x);
                shadow.transform.setToTranslation(position.x + player.diveDirection.x * 0.8f * player.bodyTilt(), 0.012f,
                        position.z + player.diveDirection.z * 0.8f * player.bodyTilt()).rotateRad(Vector3.Y, dirYaw);
            }
            shadow.transform.scale(shadowRadius * stretch, 1f, shadowRadius);
            batch.render(shadow);
            drawMarkers(batch, player, controlled, second);
        }
    }

    /** Several render calls in one frame add up for the reflection (pose gallery). */
    private boolean appending;

    public void setAppending(boolean appending) {
        this.appending = appending;
        if (!appending) drawn.clear();
    }

    private void drawMarkers(ModelBatch batch, VolleyballPlayer player, VolleyballPlayer controlled, VolleyballPlayer second) {
        if (player == controlled) {
            marker.transform.setToTranslation(position.x, 0.014f, position.z).scale(0.5f, 1f, 0.5f);
            batch.render(marker);
        } else if (player == second) {
            secondMarker.transform.setToTranslation(position.x, 0.014f, position.z).scale(0.5f, 1f, 0.5f);
            batch.render(secondMarker);
        }
    }

    public void setBlobShadows(boolean enabled) {
        blobShadows = enabled;
    }

    private final com.badlogic.gdx.math.Matrix4 handMatrix = new com.badlogic.gdx.math.Matrix4();

    /**
     * Where the player's hands are as drawn in the last frame (the palm of each hand, world
     * space): a held ball is drawn between them. False if the player was not drawn.
     */
    public boolean hands(VolleyballPlayer p, Vector3 left, Vector3 right) {
        Figure f = figures.get(p.id);
        if (f == null || !drawn.contains(f.instance)) return false;
        for (int i = 0; i < 2; i++) {
            handMatrix.set(f.instance.transform).mul(f.forearm[i].globalTransform);
            (i == 0 ? left : right).set(0.03f, -0.31f, 0f).mul(handMatrix);
        }
        return true;
    }

    /**
     * Where the player's eyes are as drawn in the last frame (world space): they follow the head
     * through a crouch, a jump, a dive and a fall. False if the player was not drawn.
     */
    public boolean eyes(VolleyballPlayer p, Vector3 out) {
        Figure f = figures.get(p.id);
        if (f == null || !drawn.contains(f.instance)) return false;
        // Through the torso: the own head is drawn shrunk to nothing in first person, its matrix would be too.
        handMatrix.set(f.instance.transform).mul(f.torso.globalTransform).translate(f.head.translation).rotate(f.head.rotation);
        out.set(EYES_FORWARD, EYES_UP, 0f).mul(handMatrix);
        return true;
    }

    /** The eyes in the head's frame: forward of its centre (between the eyes and the nose), at eye height. */
    private static final float EYES_FORWARD = 0.09f;
    private static final float EYES_UP = 0.165f;

    private final Vector3 handL = new Vector3();
    private final Vector3 handR = new Vector3();

    /**
     * Where a held ball is drawn, or null: between the palms (a little in front) for an overhand
     * serve, on the palm of the toss hand for an underhand one.
     */
    public Vector3 heldBallPoint(VolleyballPlayer p, Vector3 out) {
        if (!hands(p, handL, handR)) return null;
        if (p.swingStyle() == SwingStyle.UNDERHAND_SERVE) {
            Vector3 toss = p.isLeftHanded() ? handR : handL;
            return out.set(toss).add(p.facingX() * 0.03f, 0.1f, p.facingZ() * 0.03f);
        }
        return out.set(handL).add(handR).scl(0.5f).add(p.facingX() * 0.06f, 0f, p.facingZ() * 0.06f);
    }

    /** Draws the figures posed in the last {@link #render} again (the mirrored reflection pass). */
    public void drawLast(ModelBatch batch, Environment environment) {
        for (ModelInstance instance : drawn) batch.render(instance, environment);
    }

    /** The camera eye, so players right in front of the lens are hidden instead of filling the screen. */
    public void setCamera(Vector3 eye) {
        cameraPosition = eye;
    }

    private boolean blocksCamera(Vector3 at, float height) {
        float dx = cameraPosition.x - at.x;
        float dz = cameraPosition.z - at.z;
        return dx * dx + dz * dz < cameraClearance * cameraClearance && cameraPosition.y < at.y + height + 0.6f;
    }

    /** The player seen through their own eyes (first person): drawn without the head; null for none. */
    private VolleyballPlayer firstPersonPlayer;

    public void setFirstPersonPlayer(VolleyballPlayer player) {
        firstPersonPlayer = player;
    }

    /** Players closer than this to the camera are not drawn (1.8 m for the follow cameras; first person: only the own body). */
    private float cameraClearance = 1.8f;

    public void setCameraClearance(float radius) {
        cameraClearance = radius;
    }

    /** Dives and falls: tip the whole body over towards the dive direction, pivoting at the feet. */
    private void applyTilt(Figure f, VolleyballPlayer player, float yaw) {
        float tilt = player.bodyTilt();
        if (tilt <= 0f) return;
        // Dive direction in the model's local frame (model faces +X, right is +Z).
        float cos = MathUtils.cos(yaw);
        float sin = MathUtils.sin(yaw);
        float dx = player.diveDirection.x * cos - player.diveDirection.z * sin;
        float dz = player.diveDirection.x * sin + player.diveDirection.z * cos;
        // Rotating "up" towards d is a rotation about Y × d = (dz, 0, -dx).
        tiltAxis.set(dz, 0f, -dx);
        if (tiltAxis.len2() < 1e-6f) tiltAxis.set(0f, 0f, -1f);
        tiltAxis.nor();
        f.instance.transform.translate(0f, 0.14f * tilt + 0.06f * kneelLift(player), 0f)
                .rotateRad(tiltAxis, tilt * MathUtils.HALF_PI * 0.95f);
        if (player.isDiveRoll() && player.bodyState() == BodyState.ON_FLOOR) {
            // Sideways fall: a full roll over the shoulder, about the long axis of the body.
            float roll = smooth(player.bodyStateTime() / ROLL_TIME) * MathUtils.PI2;
            f.instance.transform.rotateRad(Vector3.Y, -Math.signum(dz == 0f ? 1f : dz) * roll);
        }
    }

    /** Extra lift while kneeling up, so bent knees do not sink into the floor. */
    private static float kneelLift(VolleyballPlayer player) {
        if (player.bodyState() != BodyState.GETTING_UP) return 0f;
        float g = player.bodyStateTime() / PlayerLocomotion.GET_UP_TIME;
        return MathUtils.sin(MathUtils.clamp(g, 0f, 1f) * MathUtils.PI);
    }

    private void pose(Figure f, VolleyballPlayer player, float yaw, Vector3 lookAt) {
        float speed = player.groundSpeed();
        float speedFactor = Math.min(1f, speed / 4f);
        // Movement direction in the model's local frame (forward +X, right +Z).
        float cos = MathUtils.cos(yaw);
        float sin = MathUtils.sin(yaw);
        float moveX = 0f;
        float moveZ = 0f;
        if (speed > 0.05f) {
            moveX = (player.velocity.x * cos - player.velocity.z * sin) / speed;
            moveZ = (player.velocity.x * sin + player.velocity.z * cos) / speed;
        }
        // The step cycle advances with the distance covered; a slower gait takes shorter steps,
        // so the planted foot stays put on the floor instead of sliding (walking coaches too).
        float strideLength = strideLength(speed);
        float travelled = player.strideDistance();
        if (Float.isNaN(f.strideAt) || travelled < f.strideAt) f.strideAt = travelled;
        f.stridePhase = (f.stridePhase + (travelled - f.strideAt) / strideLength * MathUtils.PI2) % (MathUtils.PI2 * 64f);
        f.strideAt = travelled;
        float phase = f.stridePhase;
        ActionPose action = player.actionPose();
        BodyState body = player.bodyState();
        if (!player.isOnCourt()) {
            poseSeated(f, player, yaw, lookAt);
            return;
        }
        if (body != BodyState.UPRIGHT) {
            poseDiveAndRecovery(f, player);
            f.syncTo(leftArm, rightArm, 0f, 0f);
            f.instance.calculateTransforms();
            return;
        }
        float progress = player.actionPoseProgress();
        boolean held = player.isActionPoseHeld();
        boolean airborne = player.isAirborne();
        // Ball relative to the player: in front and above the shoulders (attack read), and its height.
        boolean ballHigh = false;
        float ballPitch = 0f;
        float headYaw = 0f;
        if (lookAt != null) {
            float dx = lookAt.x - player.position.x;
            float dz = lookAt.z - player.position.z;
            float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
            float dy = lookAt.y - (player.position.y + player.height * 0.92f);
            ballHigh = dy > -0.6f && horizontal < 3f;
            ballPitch = MathUtils.clamp(MathUtils.atan2(dy, Math.max(0.3f, horizontal)), -0.45f, 0.9f);
            float toBall = MathUtils.atan2(-dz, dx);
            headYaw = MathUtils.clamp(wrap(toBall - yaw), -HEAD_TURN_LIMIT, HEAD_TURN_LIMIT);
        }

        // ---- targets: pelvis drop, trunk lean (forward < 0) and twist (+ = right shoulder forward)
        float drop = player.isCrouching() ? CROUCH_DROP : 0f;
        if (!airborne && speed > 0.05f) {
            drop = Math.max(drop, 0.06f * speedFactor) + 0.03f * Math.abs(MathUtils.sin(phase * 2f)) * speedFactor;
        }
        // Landing: the legs absorb the impact.
        if (!airborne && player.recoveryTime() > 0f) drop = Math.max(drop, 0.2f);
        drop = Math.max(drop, 0.26f * player.jumpCharge());
        float lean = -0.12f * speedFactor - (player.isCrouching() ? 0.26f : 0f) - 0.3f * player.jumpCharge();
        if (speed > 5f) lean -= 0.08f; // sprint
        // Running: the shoulders counter-rotate against the legs.
        float twist = -0.14f * MathUtils.sin(phase) * speedFactor;
        // Stance: feet apart (+ = wider), and a split stance (left foot forward) for serving.
        float stance = player.isCrouching() ? 1f : 0.3f;
        float split = 0f;

        // ---- arms: locomotion first
        if (airborne) {
            airborneArms(player, ballHigh);
            if (!player.isBlocking() && ballHigh && player.velocity.y < 1.6f && action == ActionPose.NONE) {
                // Bow and arrow: the trunk turns away and arches before the hit.
                twist = -0.5f;
                lean = 0.18f;
            }
        } else if (player.jumpCharge() > 0f) {
            // Arms swing back while loading the jump.
            float c = player.jumpCharge();
            leftArm.set(-0.9f * c, 0.15f, 0.2f);
            rightArm.set(-0.9f * c, 0.15f, 0.2f);
        } else if (player.recoveryTime() > 0f) {
            // Just landed: arms forward and out for balance.
            leftArm.set(0.9f, 0.4f, 0.6f);
            rightArm.set(0.9f, 0.4f, 0.6f);
        } else if (atNetReady(player, lookAt, speed)) {
            // Block ready: hands up at the height of the head, palms to the net.
            leftArm.set(1.75f, 0.3f, 1.55f);
            rightArm.set(1.75f, 0.3f, 1.55f);
            stance = 0.8f;
        } else if (player.isCrouching()) {
            leftArm.set(0.6f, 0.2f, 0.9f);
            rightArm.set(0.6f, 0.2f, 0.9f);
        } else {
            float swing = 0.65f * MathUtils.sin(phase) * speedFactor;
            leftArm.set(0.1f + swing, 0.1f, 0.3f + 0.75f * speedFactor);
            rightArm.set(0.1f - swing, 0.1f, 0.3f + 0.75f * speedFactor);
        }

        // ---- ball actions override
        switch (action) {
            case PLATFORM -> {
                // Forearms joined, arms straight; on the contact the legs and shoulders push through the ball.
                float push = held ? 0f : bump(progress, 0.05f, 0.35f, 0.8f);
                leftArm.set(0.8f + 0.35f * push, -0.33f, 0f);
                rightArm.set(0.8f + 0.35f * push, -0.33f, 0f);
                drop = Math.max(drop * (1f - 0.6f * push), (0.22f - 0.12f * push) * (airborne ? 0f : 1f));
                lean = -0.42f + 0.22f * push;
                stance = 1.4f;
            }
            case OVERHEAD -> {
                // Hands form a window above the forehead; on the contact arms and legs extend together.
                float extend = held ? 0f : smooth(progress / 0.45f);
                leftArm.set(MathUtils.lerp(2.35f, 2.95f, extend), MathUtils.lerp(0.42f, 0.18f, extend), MathUtils.lerp(1.45f, 0.1f, extend));
                rightArm.set(MathUtils.lerp(2.35f, 2.95f, extend), MathUtils.lerp(0.42f, 0.18f, extend), MathUtils.lerp(1.45f, 0.1f, extend));
                drop = airborne ? 0f : MathUtils.lerp(0.13f, 0f, extend);
                lean = MathUtils.lerp(-0.02f, 0.06f, extend);
                stance = 0.9f;
                split = 0.12f;
            }
            case SWING -> {
                // Attack or serve: the arm action depends on the shot (see SwingStyle).
                if (held) {
                    windUp(player.swingStyle());
                    twist = player.swingStyle() == SwingStyle.TIP ? -0.2f : -0.55f;
                    lean = player.swingStyle() == SwingStyle.TIP ? 0.05f : 0.2f;
                } else {
                    float[] trunk = swing(player.swingStyle(), progress);
                    twist = trunk[0];
                    lean = trunk[1];
                }
                if (!airborne) split = 0.2f;
            }
            case TOSS -> {
                if (player.swingStyle() == SwingStyle.UNDERHAND_SERVE) {
                    // Underhand: the ball hand lifts it a little; the hitting arm swings back.
                    float t = smooth(progress / 0.35f);
                    leftArm.set(MathUtils.lerp(0.7f, 0.95f, t), -0.6f, 0.05f);
                    rightArm.set(MathUtils.lerp(-0.1f, -0.7f, t), 0.15f, 0.1f);
                    lean = -0.25f * t;
                    split = 0.22f;
                    break;
                }
                // The toss hand lifts the ball straight up; the hitting arm draws back.
                float t = smooth(progress / 0.45f);
                leftArm.set(MathUtils.lerp(1.0f, 3.0f, t), MathUtils.lerp(-0.35f, 0.05f, t), MathUtils.lerp(0.9f, 0f, t));
                rightArm.set(MathUtils.lerp(1.0f, 3.0f, t), MathUtils.lerp(-0.35f, 0.6f, t), MathUtils.lerp(0.9f, 2.0f, t));
                twist = -0.45f * t;
                lean = 0.15f * t;
                split = 0.22f;
            }
            case HOLD_BALL -> {
                if (player.swingStyle() == SwingStyle.UNDERHAND_SERVE) {
                    // Underhand: the ball on the palm of the toss hand in front of the belly, the hitting arm down.
                    leftArm.set(0.7f, -0.6f, 0.05f);
                    rightArm.set(-0.1f, 0.15f, 0.1f);
                    lean = -0.12f;
                } else {
                    // Both hands on the ball in front of the chest (palms 25 cm apart), feet in a split stance.
                    leftArm.set(0.5f, -0.8f, 1.2f);
                    rightArm.set(0.5f, -0.8f, 1.2f);
                }
                split = 0.22f;
            }
            case BLOCK -> {
                float[] trunk = blockArms(player);
                lean = trunk[0];
                twist = trunk[1];
            }
            case REF_SERVE_RIGHT, REF_SERVE_LEFT -> {
                // The arm comes up in front and sweeps out to the side, towards the serving team.
                float sweep = smooth(progress / 0.6f);
                ArmPose arm = action == ActionPose.REF_SERVE_RIGHT ? rightArm : leftArm;
                arm.set(MathUtils.lerp(1.5f, 0.2f, sweep), MathUtils.lerp(0.1f, 1.45f, sweep), 0.05f);
                (arm == rightArm ? leftArm : rightArm).set(0.05f, 0.08f, 0.1f);
            }
            case REF_POINT_RIGHT, REF_POINT_LEFT -> {
                ArmPose arm = action == ActionPose.REF_POINT_RIGHT ? rightArm : leftArm;
                float up = smooth(progress / 0.25f);
                arm.set(0.25f * up, 1.45f * up, 0.05f);
                (arm == rightArm ? leftArm : rightArm).set(0.05f, 0.08f, 0.1f);
            }
            case REF_IN -> {
                float up = smooth(progress / 0.25f);
                rightArm.set(0.75f * up, 0.55f * up, 0.05f);
                leftArm.set(0.05f, 0.08f, 0.1f);
                lean = -0.12f * up;
            }
            case REF_OUT -> {
                // Forearms vertical, palms in.
                float up = smooth(progress / 0.25f);
                leftArm.set(0.35f * up, 0.1f, 1.75f * up);
                rightArm.set(0.35f * up, 0.1f, 1.75f * up);
            }
            case REF_NET -> {
                float up = smooth(progress / 0.25f);
                rightArm.set(1.35f * up, 0.65f * up, 0.25f);
                leftArm.set(0.05f, 0.08f, 0.1f);
                lean = -0.1f * up;
            }
            case REF_FOUR_HITS, REF_DOUBLE -> {
                float up = smooth(progress / 0.25f);
                rightArm.set(2.7f * up, 0.3f, 0.2f);
                leftArm.set(0.05f, 0.08f, 0.1f);
            }
            case REF_HELD -> {
                // The forearm rises slowly, palm up.
                float up = smooth(progress / 0.8f);
                rightArm.set(0.45f, 0.05f, 0.4f + 1.1f * up);
                leftArm.set(0.05f, 0.08f, 0.1f);
            }
            case REF_TIMEOUT -> {
                // T: one hand flat on the other's fingertips, in front of the chest.
                float up = smooth(progress / 0.25f);
                leftArm.set(1.4f * up, -0.25f * up, 1.6f * up);
                rightArm.set(1.5f * up, -0.55f * up, 1.15f * up);
            }
            case HIGH_FIVE -> {
                // The hitting hand goes up and forward to meet the teammate's, then comes down.
                float up = bump(progress, 0f, 0.35f, 1f);
                rightArm.set(MathUtils.lerp(0.4f, 2.5f, up), MathUtils.lerp(0.1f, 0.25f, up), MathUtils.lerp(0.3f, 0.15f, up));
                leftArm.set(0.3f, 0.1f, 0.3f);
                twist = 0.2f * up;
            }
            case CLAP -> {
                // Hands come together in front of the chest twice.
                float clap = 0.5f + 0.5f * MathUtils.cos(progress * MathUtils.PI2 * 2f);
                leftArm.set(1.15f, MathUtils.lerp(-0.45f, -0.1f, clap), 1.2f);
                rightArm.set(1.15f, MathUtils.lerp(-0.45f, -0.1f, clap), 1.2f);
                lean = -0.05f;
            }
            case CELEBRATE -> {
                // Fists up, pumping.
                float pump = 0.5f + 0.5f * MathUtils.sin(progress * MathUtils.PI2 * 3f);
                leftArm.set(2.7f, 0.5f, 0.6f + 0.9f * pump);
                rightArm.set(2.7f, 0.5f, 0.6f + 0.9f * pump);
                lean = 0.1f;
                twist = 0f;
            }
            default -> {
            }
        }
        if (airborne) drop = 0f;

        // ---- smooth towards the targets: quick for strikes, softer for everything else
        // A left-hander's hitting actions are the mirror image (the toss and hold too).
        boolean handed = action == ActionPose.SWING || action == ActionPose.TOSS || action == ActionPose.HOLD_BALL
                || (airborne && action == ActionPose.NONE && !player.isBlocking() && ballHigh);
        if (player.isLeftHanded() && handed) {
            mirrorArms();
            twist = -twist;
            split = -split;
        }
        boolean striking = !held && (action == ActionPose.SWING || action == ActionPose.PLATFORM || action == ActionPose.OVERHEAD);
        float dt = Math.min(0.05f, Gdx.graphics == null ? 1f / 60f : Gdx.graphics.getDeltaTime());
        float k = 1f - (float) Math.exp(-(striking ? 30f : 13f) * dt);
        f.blend(leftArm, rightArm, lean, twist, drop, k);

        // ---- legs, with the smoothed pelvis height
        float pelvisDrop = f.drop;
        f.pelvis.translation.set(0f, PlayerModelFactory.PELVIS_HEIGHT - pelvisDrop, 0f);
        f.pelvis.rotation.setFromAxisRad(0f, 1f, 0f, -0.35f * f.twist);
        for (int i = 0; i < 2; i++) {
            float legPhase = phase + i * MathUtils.PI;
            float hipHeight = PlayerModelFactory.PELVIS_HEIGHT - pelvisDrop - PlayerModelFactory.HIP_DROP - PlayerModelFactory.ANKLE_HEIGHT;
            float footX;
            float footZ;
            float footY;
            float footPitch = 0f;
            if (airborne) {
                float rise = MathUtils.clamp(player.velocity.y / 3.5f, -1f, 1f);
                if (player.isBlocking() || action == ActionPose.BLOCK) {
                    // Block: legs straight down, together.
                    footX = 0f;
                    footY = -(PlayerModelFactory.THIGH_LENGTH + PlayerModelFactory.SHIN_LENGTH) * 0.97f;
                    footPitch = -0.4f;
                } else if (rise > 0.35f) {
                    // Take-off: legs extended, toes pointed.
                    footX = i == 0 ? -0.05f : 0.03f;
                    footY = -(PlayerModelFactory.THIGH_LENGTH + PlayerModelFactory.SHIN_LENGTH) * 0.97f;
                    footPitch = -0.6f;
                } else if (rise > -0.45f) {
                    // At the top: the lower legs fold back (the body is a bow).
                    footX = -0.22f + i * 0.06f;
                    footY = -(PlayerModelFactory.THIGH_LENGTH + PlayerModelFactory.SHIN_LENGTH) * 0.78f;
                    footPitch = -0.7f;
                } else {
                    // Coming down: feet reach for the floor, knees soft.
                    footX = 0.06f - i * 0.1f;
                    footY = -(PlayerModelFactory.THIGH_LENGTH + PlayerModelFactory.SHIN_LENGTH) * 0.9f;
                    footPitch = -0.2f;
                }
                footZ = i == 0 ? -0.05f : 0.05f;
            } else {
                // Step cycle along the movement direction; the swinging foot lifts.
                // A quarter of the cycle each way: the foot on the floor moves back exactly as fast as the body goes.
                float stepAmplitude = strideLength * 0.25f * Math.min(1f, speed / 0.5f);
                float st = MathUtils.sin(legPhase);
                float lift = Math.max(0f, MathUtils.cos(legPhase)) * 0.13f * Math.min(1f, speed / 2.5f);
                footX = moveX * stepAmplitude * st + (i == 0 ? split : -split * 0.6f);
                // Side shuffle: steps are shorter and the stance widens.
                footZ = moveZ * stepAmplitude * 0.6f * st + (i == 0 ? -0.05f : 0.05f) * stance;
                footY = -(hipHeight - lift);
            }
            solveLeg(f, i, footX, footY, footZ, footPitch);
        }

        applyArm(f, 0, f.left);
        applyArm(f, 1, f.right);
        f.torso.rotation.setFromAxisRad(0f, 1f, 0f, f.twist).mul(tmpQuat.setFromAxisRad(0f, 0f, 1f, f.lean));
        // The head keeps looking forward over a leaning trunk, turns towards the ball and looks up at it.
        f.headPitch += (ballPitch * 0.55f - f.headPitch) * k;
        float nod = MathUtils.clamp(-f.lean * 0.6f + f.headPitch, -0.35f, 0.55f);
        f.head.rotation.setFromAxisRad(0f, 1f, 0f, headYaw - f.twist * 0.8f)
                .mul(tmpQuat.setFromAxisRad(0f, 0f, 1f, nod));
        f.instance.calculateTransforms();
    }

    /** Length of a full step cycle (two steps) at this speed: about 1.3 m walking, 1.7 m running. */
    static float strideLength(float speed) {
        return MathUtils.clamp(0.95f + 0.3f * speed, 1.15f, STRIDE_LENGTH);
    }

    /** On the bench: sitting on a chair (seat 0.47 m), forearms on the thighs, watching the ball. */
    private void poseSeated(Figure f, VolleyballPlayer player, float yaw, Vector3 lookAt) {
        float seat = 0.47f;
        float hip = seat + 0.03f;
        f.pelvis.translation.set(0f, hip + PlayerModelFactory.HIP_DROP, 0f);
        f.pelvis.rotation.idt();
        float footY = -(hip - PlayerModelFactory.ANKLE_HEIGHT);
        solveLeg(f, 0, 0.4f, footY, -0.07f, 0f);
        solveLeg(f, 1, 0.4f, footY, 0.07f, 0f);
        leftArm.set(0.75f, -0.05f, 0.75f);
        rightArm.set(0.75f, -0.05f, 0.75f);
        f.syncTo(leftArm, rightArm, -0.12f, 0f);
        f.drop = 0f;
        applyArm(f, 0, leftArm);
        applyArm(f, 1, rightArm);
        f.torso.rotation.setFromAxisRad(0f, 0f, 1f, -0.12f);
        float headYaw = 0f;
        if (lookAt != null) {
            float toBall = MathUtils.atan2(-(lookAt.z - player.position.z), lookAt.x - player.position.x);
            headYaw = MathUtils.clamp(wrap(toBall - yaw), -HEAD_TURN_LIMIT, HEAD_TURN_LIMIT);
        }
        f.head.rotation.setFromAxisRad(0f, 1f, 0f, headYaw);
        f.instance.calculateTransforms();
    }

    /**
     * Arms in the air without a ball action: swinging up at the take-off, then (with the ball up
     * in front) drawn into the bow and arrow - hitting arm cocked behind the head, the other one
     * pointing at the ball - and lowered for balance on the way down.
     */
    private void airborneArms(VolleyballPlayer player, boolean ballHigh) {
        float rise = MathUtils.clamp(player.velocity.y / 3.5f, -1f, 1f);
        if (rise > 0.45f) {
            leftArm.set(2.7f, 0.25f, 0.25f);
            rightArm.set(2.7f, 0.25f, 0.25f);
        } else if (ballHigh && rise > -0.5f) {
            windUp(player.swingStyle());
        } else {
            leftArm.set(1.4f, 0.55f, 0.4f);
            rightArm.set(1.4f, 0.55f, 0.4f);
        }
    }

    /**
     * The cocked arm before the hit (hitting arm = right here; mirrored for left-handers): a full
     * bow and arrow for power, cut and tool shots, a high, less bent arm for the roll shot, the
     * hand up at the ball with soft fingers for a tip, a short, firm cock for the float serve.
     */
    private void windUp(SwingStyle style) {
        switch (style) {
            case TIP -> {
                rightArm.set(2.9f, 0.25f, 0.8f);
                leftArm.set(2.0f, 0.15f, 0.4f);
            }
            case ROLL -> {
                rightArm.set(3.0f, 0.45f, 1.5f);
                leftArm.set(2.3f, 0.1f, 0.15f);
            }
            case FLOAT_SERVE -> {
                rightArm.set(2.6f, 0.75f, 1.75f);
                leftArm.set(2.5f, 0.05f, 0.1f);
            }
            case UNDERHAND_SERVE -> {
                rightArm.set(-0.6f, 0.2f, 0.1f);
                leftArm.set(0.9f, -0.35f, 0.5f);
            }
            case TOOL -> {
                rightArm.set(3.0f, 0.85f, 2.0f);
                leftArm.set(2.35f, 0.1f, 0.1f);
            }
            default -> {
                rightArm.set(3.0f, 0.6f, 2.05f);
                leftArm.set(2.35f, 0.1f, 0.1f);
            }
        }
    }

    /**
     * The swing after the wind-up, by shot, over the pose's progress (0..1); returns the trunk's
     * twist and lean. Power: whip and a long follow-through across the body. Cut: the arm snaps
     * across the body with the trunk turning hard. Roll: a slower brush up and over, the arm
     * finishing high. Tip: a short push up and forward, the arm stays up. Tool: the swing goes
     * out wide, towards the block's edge. Float serve: a punch that stops at the contact.
     * Topspin serve: a full whip with the body thrown into it. Underhand: from behind the hip
     * forward and up.
     */
    private float[] swing(SwingStyle style, float progress) {
        float whip = smooth(progress / 0.3f);
        float follow = smooth((progress - 0.3f) / 0.7f);
        float twist;
        float lean;
        switch (style) {
            case TIP -> {
                float push = smooth(progress / 0.4f);
                rightArm.set(MathUtils.lerp(2.9f, 2.75f, push), MathUtils.lerp(0.25f, 0.05f, push), MathUtils.lerp(0.8f, 0.1f, push));
                leftArm.set(MathUtils.lerp(2.0f, 1.2f, push), 0.15f, 0.5f);
                twist = MathUtils.lerp(-0.2f, 0.1f, push);
                lean = MathUtils.lerp(0.05f, -0.1f, push);
            }
            case ROLL -> {
                float brush = smooth(progress / 0.45f);
                float after = smooth((progress - 0.45f) / 0.55f);
                rightArm.set(MathUtils.lerp(MathUtils.lerp(3.0f, 2.7f, brush), 2.2f, after), MathUtils.lerp(0.45f, 0.1f, brush),
                        MathUtils.lerp(MathUtils.lerp(1.5f, 0.2f, brush), 0.6f, after));
                leftArm.set(MathUtils.lerp(2.3f, 1.0f, brush), 0.2f, MathUtils.lerp(0.15f, 1.0f, brush));
                twist = MathUtils.lerp(-0.45f, 0.2f, brush);
                lean = MathUtils.lerp(0.15f, -0.15f, brush);
            }
            case CUT -> {
                // Wrist turned, the arm snapping across the body to the opposite hip.
                rightArm.set(MathUtils.lerp(MathUtils.lerp(3.0f, 2.3f, whip), 0.6f, follow),
                        MathUtils.lerp(MathUtils.lerp(0.6f, -0.1f, whip), -0.9f, follow),
                        MathUtils.lerp(MathUtils.lerp(2.05f, 0.05f, whip), 0.5f, follow));
                leftArm.set(MathUtils.lerp(MathUtils.lerp(2.35f, 0.7f, whip), 0.4f, follow), 0.25f, MathUtils.lerp(0.1f, 1.4f, whip));
                twist = MathUtils.lerp(MathUtils.lerp(-0.6f, 0.7f, whip), 0.45f, follow);
                lean = MathUtils.lerp(MathUtils.lerp(0.2f, -0.3f, whip), -0.2f, follow);
            }
            case TOOL -> {
                // Hard and wide: the arm swings out towards the sideline.
                rightArm.set(MathUtils.lerp(MathUtils.lerp(3.0f, 2.3f, whip), 0.9f, follow),
                        MathUtils.lerp(MathUtils.lerp(0.85f, 0.75f, whip), 0.55f, follow),
                        MathUtils.lerp(MathUtils.lerp(2.0f, 0.02f, whip), 0.3f, follow));
                leftArm.set(MathUtils.lerp(MathUtils.lerp(2.35f, 0.9f, whip), 0.5f, follow), 0.2f, MathUtils.lerp(0.1f, 1.2f, whip));
                twist = MathUtils.lerp(MathUtils.lerp(-0.55f, 0.25f, whip), 0.1f, follow);
                lean = MathUtils.lerp(MathUtils.lerp(0.2f, -0.3f, whip), -0.2f, follow);
            }
            case FLOAT_SERVE -> {
                // A punch: the arm drives straight through the ball and stops there.
                float punch = smooth(progress / 0.25f);
                float drop = smooth((progress - 0.7f) / 0.3f);
                rightArm.set(MathUtils.lerp(MathUtils.lerp(2.6f, 2.55f, punch), 1.6f, drop), MathUtils.lerp(0.75f, 0.2f, punch),
                        MathUtils.lerp(MathUtils.lerp(1.75f, 0.0f, punch), 0.3f, drop));
                leftArm.set(MathUtils.lerp(2.5f, 1.0f, punch), 0.15f, MathUtils.lerp(0.1f, 0.9f, punch));
                twist = MathUtils.lerp(-0.35f, 0.15f, punch);
                lean = MathUtils.lerp(0.1f, -0.1f, punch);
            }
            case UNDERHAND_SERVE -> {
                float arc = smooth(progress / 0.5f);
                float after = smooth((progress - 0.5f) / 0.5f);
                rightArm.set(MathUtils.lerp(MathUtils.lerp(-0.6f, 0.9f, arc), 1.6f, after), MathUtils.lerp(0.2f, 0.05f, arc), 0.1f);
                leftArm.set(MathUtils.lerp(0.9f, 0.4f, arc), MathUtils.lerp(-0.35f, 0.3f, arc), 0.4f);
                twist = MathUtils.lerp(-0.3f, 0.2f, arc);
                lean = MathUtils.lerp(-0.3f, -0.15f, arc);
            }
            default -> {
                // Power spike and topspin serve: whip, then the arm runs through across the body.
                boolean serve = style == SwingStyle.TOPSPIN_SERVE;
                rightArm.set(MathUtils.lerp(MathUtils.lerp(3.0f, 2.4f, whip), 0.35f, follow),
                        MathUtils.lerp(MathUtils.lerp(0.6f, 0.2f, whip), -0.45f, follow),
                        MathUtils.lerp(MathUtils.lerp(2.05f, 0.02f, whip), 0.35f, follow));
                leftArm.set(MathUtils.lerp(MathUtils.lerp(2.35f, 0.9f, whip), 0.5f, follow), 0.2f, MathUtils.lerp(0.1f, 1.3f, whip));
                twist = MathUtils.lerp(MathUtils.lerp(-0.55f, serve ? 0.6f : 0.45f, whip), 0.25f, follow);
                lean = MathUtils.lerp(MathUtils.lerp(serve ? 0.3f : 0.2f, serve ? -0.5f : -0.35f, whip), -0.25f, follow);
            }
        }
        return new float[]{twist, lean};
    }

    private final ArmPose swapArm = new ArmPose();

    /** A left-hander: the arms trade places and the trunk turns the other way. */
    private void mirrorArms() {
        swapArm.set(leftArm.raise, leftArm.spread, leftArm.elbow);
        leftArm.set(rightArm.raise, rightArm.spread, rightArm.elbow);
        rightArm.set(swapArm.raise, swapArm.spread, swapArm.elbow);
    }

    /** The kinds of block the arms show (see {@link #blockKind}). */
    public enum BlockKind { STRAIGHT, LINE, CROSS, SWING, SOFT, PENETRATING }

    /**
     * Which block the player is making, read from its state: a blocker who travelled along the
     * net swings the arms up (swing block); one who cannot reach over (late, too short) blocks soft,
     * hands tilted back; hands shifted to the sideline close the line, to the middle the diagonal;
     * hands well over the net press down (penetrating); otherwise the straight read block.
     */
    public static BlockKind blockKind(VolleyballPlayer p) {
        float alongNet = Math.abs(p.velocity.z);
        if (p.isAirborne() && p.velocity.y > 0.8f && alongNet > 1.0f) return BlockKind.SWING;
        if (p.isBlocking() && p.blockPenetration() < 0.04f && p.velocity.y < 0.3f) return BlockKind.SOFT;
        // The hands' shift relative to the nearer sideline: + towards it (line), - towards the middle (diagonal).
        float towardsSideline = p.blockShift() * Math.signum(p.position.z == 0f ? 1f : p.position.z);
        if (towardsSideline > 0.05f) return BlockKind.LINE;
        if (towardsSideline < -0.05f) return BlockKind.CROSS;
        if (p.isBlocking() && p.blockPenetration() > 0.2f) return BlockKind.PENETRATING;
        return BlockKind.STRAIGHT;
    }

    /** The arms of the block (by {@link #blockKind}); returns the trunk's lean and twist. */
    private float[] blockArms(VolleyballPlayer player) {
        float over = player.isBlocking() ? Math.min(1f, player.blockPenetration() / 0.3f) : 0f;
        float up = 2.95f - 0.4f * over;
        float lean = -0.1f - 0.12f * over;
        float twist = 0f;
        // The player's right hand points to world +z when facing +x: towards the shift side, + = right.
        float shiftRight = MathUtils.clamp(player.blockShift() * Math.signum(player.facingX()) / 0.2f, -1f, 1f);
        switch (blockKind(player)) {
            case SWING -> {
                // Arms come round from behind the hips as the blocker takes off from the run along the net.
                float rise = MathUtils.clamp(1f - player.velocity.y / 3.6f, 0f, 1f);
                float arc = smooth(rise / 0.8f);
                leftArm.set(MathUtils.lerp(-0.7f, up, arc), MathUtils.lerp(0.35f, 0.14f, arc), MathUtils.lerp(0.5f, 0.05f, arc));
                rightArm.set(MathUtils.lerp(-0.7f, up, arc), MathUtils.lerp(0.35f, 0.14f, arc), MathUtils.lerp(0.5f, 0.05f, arc));
                lean = MathUtils.lerp(-0.3f, lean, arc);
            }
            case SOFT -> {
                // Late or short: palms and arms tilted back to deflect the ball up for the defence.
                leftArm.set(3.2f, 0.22f, 0.2f);
                rightArm.set(3.2f, 0.22f, 0.2f);
                lean = 0.12f;
            }
            case LINE, CROSS -> {
                // Both hands shift to one side; the leading hand (on the shift side) turns in to seal it,
                // and the chest turns towards the court.
                ArmPose lead = shiftRight >= 0f ? rightArm : leftArm;
                ArmPose trail = lead == rightArm ? leftArm : rightArm;
                float k = Math.abs(shiftRight);
                lead.set(up - 0.12f * k, 0.14f + 0.32f * k, 0.25f * k);
                trail.set(up, 0.14f - 0.22f * k, 0.05f);
                twist = -0.28f * shiftRight;
            }
            case PENETRATING -> {
                // Well over the net: arms locked straight, pressing down into their court.
                leftArm.set(up - 0.15f, 0.1f, 0f);
                rightArm.set(up - 0.15f, 0.1f, 0f);
                lean = lean - 0.08f;
            }
            default -> {
                leftArm.set(up, 0.14f, 0.05f);
                rightArm.set(up, 0.14f, 0.05f);
            }
        }
        return new float[]{lean, twist};
    }

    /** Standing at the net facing it while the ball is on the other side: ready to block. */
    private static boolean atNetReady(VolleyballPlayer player, Vector3 lookAt, float speed) {
        if (lookAt == null || Math.abs(player.position.x) > 1.0f || speed > 2.5f) return false;
        boolean ballOpposite = Math.signum(lookAt.x) == -player.side.sign() || lookAt.x * player.side.sign() < 0f;
        boolean facingNet = Math.abs(wrap(player.facingYaw - VolleyballPlayer.facingNetYaw(player.side))) < 0.8f;
        return ballOpposite && facingNet && Math.abs(player.position.z) < 5f;
    }

    /** 0 → 1 → 0 hump between {@code start} and {@code end}, peaking at {@code peak}. */
    private static float bump(float t, float start, float peak, float end) {
        if (t <= start || t >= end) return 0f;
        return t < peak ? smooth((t - start) / (peak - start)) : 1f - smooth((t - peak) / (end - peak));
    }

    /**
     * Dive, slide or roll, and getting up, keyed by the time spent in each phase and eased, so
     * the body moves through it rather than tipping over like a plank:
     * launch (dip, one leg drives, the other trails, arms reach ahead, back arches); on the
     * floor (stretched out and sliding, then hands under the shoulders; a sideways fall tucks
     * up and rolls instead); getting up (push up onto the knees, then stand through a crouch).
     */
    private void poseDiveAndRecovery(Figure f, VolleyballPlayer player) {
        float t = player.bodyStateTime();
        float hip = PlayerModelFactory.PELVIS_HEIGHT - PlayerModelFactory.HIP_DROP - PlayerModelFactory.ANKLE_HEIGHT;
        float pelvisDrop = 0f;
        float lean;
        float head;
        switch (player.bodyState()) {
            case DIVING -> {
                float p = smooth(t / PlayerLocomotion.DIVE_TIME);
                pelvisDrop = 0.22f * (1f - p);
                solveLeg(f, 0, MathUtils.lerp(-0.05f, -0.38f, p), -hip * MathUtils.lerp(0.85f, 0.97f, p), 0f, -0.6f * p);
                solveLeg(f, 1, MathUtils.lerp(0.25f, -0.2f, p), -hip * MathUtils.lerp(0.62f, 0.9f, p), 0f, -0.5f * p);
                float raise = MathUtils.lerp(1.2f, 2.95f, p);
                leftArm.set(raise, MathUtils.lerp(0.2f, -0.08f, p), MathUtils.lerp(0.7f, 0.05f, p));
                rightArm.set(raise, MathUtils.lerp(0.2f, -0.08f, p), MathUtils.lerp(0.7f, 0.05f, p));
                lean = MathUtils.lerp(-0.55f, 0.15f, p);
                head = 0.55f * p;
            }
            case ON_FLOOR -> {
                if (player.isDiveRoll()) {
                    float r = smooth(t / ROLL_TIME);
                    float tuck = MathUtils.sin(r * MathUtils.PI);
                    solveLeg(f, 0, 0.3f * tuck, -hip * MathUtils.lerp(0.95f, 0.5f, tuck), 0f, -0.5f);
                    solveLeg(f, 1, 0.3f * tuck, -hip * MathUtils.lerp(0.95f, 0.5f, tuck), 0f, -0.5f);
                    leftArm.set(MathUtils.lerp(2.9f, 1.1f, tuck), 0.2f, 1.6f * tuck);
                    rightArm.set(MathUtils.lerp(2.9f, 1.1f, tuck), 0.2f, 1.6f * tuck);
                    lean = -0.6f * tuck;
                    head = -0.4f * tuck;
                } else {
                    float q = smooth((t / PlayerLocomotion.FLOOR_TIME - 0.35f) / 0.65f);
                    solveLeg(f, 0, -0.12f, -hip * 0.96f, 0f, -0.9f);
                    solveLeg(f, 1, -0.05f, -hip * 0.9f, 0f, -0.9f);
                    leftArm.set(MathUtils.lerp(2.95f, 0.45f, q), MathUtils.lerp(-0.08f, 0.35f, q), MathUtils.lerp(0.05f, 1.7f, q));
                    rightArm.set(MathUtils.lerp(2.95f, 0.45f, q), MathUtils.lerp(-0.08f, 0.35f, q), MathUtils.lerp(0.05f, 1.7f, q));
                    lean = MathUtils.lerp(0.15f, 0.05f, q);
                    head = 0.55f;
                }
            }
            default -> {
                float g = MathUtils.clamp(t / PlayerLocomotion.GET_UP_TIME, 0f, 1f);
                if (g < 0.5f) {
                    float k = smooth(g / 0.5f);
                    solveLeg(f, 0, MathUtils.lerp(-0.05f, 0.3f, k), -hip * MathUtils.lerp(0.95f, 0.5f, k), 0f, -0.6f);
                    solveLeg(f, 1, MathUtils.lerp(-0.05f, 0.25f, k), -hip * MathUtils.lerp(0.95f, 0.55f, k), 0f, -0.6f);
                    leftArm.set(MathUtils.lerp(0.45f, 0.15f, k), 0.3f, MathUtils.lerp(1.7f, 0.2f, k));
                    rightArm.set(MathUtils.lerp(0.45f, 0.15f, k), 0.3f, MathUtils.lerp(1.7f, 0.2f, k));
                    lean = MathUtils.lerp(0.05f, -0.45f, k);
                    head = MathUtils.lerp(0.5f, 0.2f, k);
                } else {
                    float u = smooth((g - 0.5f) / 0.5f);
                    pelvisDrop = MathUtils.lerp(0.38f, 0.05f, u);
                    float standingHip = hip - pelvisDrop;
                    solveLeg(f, 0, 0f, -standingHip, -0.04f, 0f);
                    solveLeg(f, 1, 0f, -standingHip, 0.04f, 0f);
                    leftArm.set(MathUtils.lerp(0.7f, 0.15f, u), 0.15f, MathUtils.lerp(0.6f, 0.3f, u));
                    rightArm.set(MathUtils.lerp(0.7f, 0.15f, u), 0.15f, MathUtils.lerp(0.6f, 0.3f, u));
                    lean = MathUtils.lerp(-0.45f, -0.05f, u);
                    head = MathUtils.lerp(0.2f, 0f, u);
                }
            }
        }
        f.pelvis.translation.set(0f, PlayerModelFactory.PELVIS_HEIGHT - pelvisDrop, 0f);
        f.pelvis.rotation.idt();
        applyArm(f, 0, leftArm);
        applyArm(f, 1, rightArm);
        f.torso.rotation.setFromAxisRad(0f, 0f, 1f, lean);
        f.head.rotation.setFromAxisRad(0f, 0f, 1f, head);
    }

    private static float smooth(float x) {
        float t = MathUtils.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Two-bone IK for one leg: places the ankle at (x, y, z) relative to the hip joint.
     * Sagittal plane: thigh angle = angle to target + knee offset (law of cosines); the shin bends
     * back so the knee points forward; the foot counter-rotates to stay level (plus {@code footPitch}).
     * A sideways target is reached by rotating the thigh out (abduction).
     */
    private void solveLeg(Figure f, int i, float x, float y, float z, float footPitch) {
        float a = PlayerModelFactory.THIGH_LENGTH;
        float b = PlayerModelFactory.SHIN_LENGTH;
        float down = (float) Math.sqrt(y * y + z * z); // length in the sagittal projection
        // Positive rotation about +X swings the leg towards -Z, hence the minus sign.
        float abduction = -MathUtils.atan2(z, Math.max(1e-4f, -y));
        float distance = MathUtils.clamp((float) Math.sqrt(x * x + down * down), Math.abs(a - b) + 0.01f, a + b - 0.001f);
        float toTarget = MathUtils.atan2(x, down);
        float alpha = (float) Math.acos(MathUtils.clamp((a * a + distance * distance - b * b) / (2f * a * distance), -1f, 1f));
        float beta = (float) Math.acos(MathUtils.clamp((b * b + distance * distance - a * a) / (2f * b * distance), -1f, 1f));
        f.thigh[i].rotation.setFromAxisRad(1f, 0f, 0f, abduction).mul(tmpQuat.setFromAxisRad(0f, 0f, 1f, toTarget + alpha));
        f.shin[i].rotation.setFromAxisRad(0f, 0f, 1f, -(alpha + beta));
        f.foot[i].rotation.setFromAxisRad(0f, 0f, 1f, beta - toTarget + footPitch);
    }

    /** Shoulder raise/spread and elbow flexion for one arm (0 = left, 1 = right). */
    private void applyArm(Figure f, int i, ArmPose arm) {
        // Spreading the left arm outwards (towards -Z) is a positive rotation about +X; the right arm mirrors it.
        float spread = i == 0 ? arm.spread : -arm.spread;
        f.upperArm[i].rotation.setFromAxisRad(1f, 0f, 0f, spread).mul(tmpQuat.setFromAxisRad(0f, 0f, 1f, arm.raise));
        f.forearm[i].rotation.setFromAxisRad(0f, 0f, 1f, arm.elbow);
    }

    private static float wrap(float angle) {
        while (angle > MathUtils.PI) angle -= MathUtils.PI2;
        while (angle < -MathUtils.PI) angle += MathUtils.PI2;
        return angle;
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + wrap(to - from) * t;
    }

    @Override
    public void dispose() {
        for (Model model : models) model.dispose();
        shadowModel.dispose();
        markerModel.dispose();
        secondMarkerModel.dispose();
    }
}
