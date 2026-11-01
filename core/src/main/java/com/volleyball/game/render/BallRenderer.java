package com.volleyball.game.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.physics.TrajectoryPredictor;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the ball (with coloured panels so its spin is visible), its shadow and the forecast
 * landing indicator. The indicator shows the {@link TrajectoryPredictor} forecast, i.e. what an
 * attentive player could estimate — not the guaranteed future (float serves will drift off it).
 */
public final class BallRenderer implements Disposable {
    private static final int MAX_PATH_DOTS = 120;

    private final Model ballModel = buildPanelBall();
    private final Model shadowModel = MarkerModels.disc(Color.BLACK, 0.45f);
    private final Model landingModel = MarkerModels.ring(new Color(1f, 0.95f, 0.2f, 1f), 0.75f, 0.7f);
    private final Model dotModel = MarkerModels.sphere(new Color(1f, 1f, 1f, 1f), 0.05f);
    private final Model trailModel = MarkerModels.glow(new Color(1f, 0.97f, 0.85f, 1f), 1f, Ball.RADIUS * 2f);
    /** Where the ball was drawn in the last frames (newest first), for the trail. */
    private final Vector3[] history = new Vector3[TRAIL_FRAMES];
    private int historySize;
    private final List<ModelInstance> trail = new ArrayList<>();
    private boolean trailEnabled;
    private static final int TRAIL_FRAMES = 8;
    private static final int TRAIL_STEPS = 3;
    /** Below this speed (m/s) there is no trail. */
    private static final float TRAIL_SPEED = 14f;
    private final Model aimModel = MarkerModels.ring(new Color(0.2f, 0.9f, 1f, 1f), 0.85f, 0.8f);
    private final Model receptionDiscModel = MarkerModels.disc(new Color(1f, 1f, 1f, 1f), 0.16f);
    private final Model receptionRingModel = MarkerModels.ring(new Color(1f, 1f, 1f, 1f), 0.9f, 0.55f);
    private final ModelInstance receptionDisc = new ModelInstance(receptionDiscModel);
    private final ModelInstance receptionRing = new ModelInstance(receptionRingModel);
    private final ModelInstance aim = new ModelInstance(aimModel);
    private final ModelInstance ball = new ModelInstance(ballModel);
    private final ModelInstance shadow = new ModelInstance(shadowModel);
    private final ModelInstance landing = new ModelInstance(landingModel);
    private final List<ModelInstance> dots = new ArrayList<>();
    private final Vector3 position = new Vector3();
    private final Quaternion orientation = new Quaternion();

    public BallRenderer() {
        for (int i = 0; i < MAX_PATH_DOTS; i++) dots.add(new ModelInstance(dotModel));
        for (int i = 0; i < TRAIL_FRAMES; i++) history[i] = new Vector3();
        for (int i = 0; i < TRAIL_FRAMES * TRAIL_STEPS; i++) trail.add(new ModelInstance(trailModel));
    }

    /** A faint streak behind fast balls (spikes, hard serves); the higher graphics settings only. */
    public void setTrail(boolean enabled) {
        trailEnabled = enabled;
    }

    /** The streak: fading, shrinking copies of the ball along its last positions. */
    private void renderTrail(ModelBatch batch, Environment environment, Ball state) {
        // Remember this frame's position (newest first).
        for (int i = Math.min(historySize, TRAIL_FRAMES - 1); i > 0; i--) history[i].set(history[i - 1]);
        history[0].set(position);
        historySize = Math.min(TRAIL_FRAMES, historySize + 1);
        if (!trailEnabled || !state.isActive() || state.speed() < TRAIL_SPEED) return;
        float strength = MathUtils.clamp((state.speed() - TRAIL_SPEED) / 10f, 0.3f, 1f);
        int n = 0;
        for (int i = 0; i + 1 < historySize; i++) {
            for (int k = 0; k < TRAIL_STEPS; k++) {
                float f = (i + (k + 1) / (float) TRAIL_STEPS) / (TRAIL_FRAMES - 1);
                ModelInstance m = trail.get(n++);
                float scale = 1f - 0.75f * f;
                m.transform.setToTranslation(history[i].x + (history[i + 1].x - history[i].x) * (k + 1) / TRAIL_STEPS,
                        history[i].y + (history[i + 1].y - history[i].y) * (k + 1) / TRAIL_STEPS,
                        history[i].z + (history[i + 1].z - history[i].z) * (k + 1) / TRAIL_STEPS).scale(scale, scale, scale);
                ((com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute) m.materials.get(0)
                        .get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)).opacity = 0.32f * strength * (1f - f);
                batch.render(m, environment);
            }
        }
    }

    /** Blob shadow under the ball (the arena shader casts a real soft shadow instead). */
    private boolean blobShadow = true;

    public void setBlobShadow(boolean enabled) {
        blobShadow = enabled;
    }

    /** The ball as drawn in the last frame (the mirrored reflection pass). */
    public void drawLast(ModelBatch batch, Environment environment) {
        batch.render(ball, environment);
    }

    /** Draw the ball at this point this frame (a ball held in the hands), or null for its own position. */
    private Vector3 heldAt;
    private final Vector3 releaseOffset = new Vector3();
    private float releaseTime = RELEASE_BLEND;
    private static final float RELEASE_BLEND = 0.15f;

    public void setHeldAt(Vector3 at) {
        heldAt = at;
    }

    public void render(ModelBatch batch, Environment environment, Ball state, float alpha,
                       TrajectoryPredictor.Prediction forecast, boolean showPath) {
        position.set(state.previousPosition).lerp(state.position, alpha);
        if (heldAt != null && !state.isActive()) {
            releaseOffset.set(heldAt).sub(position);
            releaseTime = 0f;
            position.set(heldAt);
        } else if (state.isActive() && releaseTime < RELEASE_BLEND) {
            // Just tossed: the drawn ball glides from the hands onto its flight instead of jumping.
            releaseTime += com.badlogic.gdx.Gdx.graphics.getDeltaTime();
            position.mulAdd(releaseOffset, Math.max(0f, 1f - releaseTime / RELEASE_BLEND));
        }
        orientation.set(state.previousOrientation).slerp(state.orientation, alpha);
        ball.transform.set(position, orientation);
        batch.render(ball, environment);
        renderTrail(batch, environment, state);

        float height = Math.max(0f, position.y - Ball.RADIUS);
        float shadowRadius = Ball.RADIUS * (1f + 0.15f * height);
        shadow.transform.setToTranslation(position.x, 0.013f, position.z).scale(shadowRadius, 1f, shadowRadius);
        if (blobShadow) batch.render(shadow);

        if (forecast == null || !state.isActive()) return;
        if (forecast.lands && forecast.timeToLanding > 0.08f) {
            // The ring tightens as the landing approaches.
            float radius = 0.25f + 0.35f * MathUtils.clamp(forecast.timeToLanding, 0f, 1.5f);
            landing.transform.setToTranslation(forecast.landing.x, 0.015f, forecast.landing.z).scale(radius, 1f, radius);
            batch.render(landing);
        }
        if (showPath) {
            int count = Math.min(forecast.pathSize(), MAX_PATH_DOTS);
            for (int i = 0; i < count; i++) {
                ModelInstance dot = dots.get(i);
                dot.transform.setToTranslation(forecast.path.get(i));
                batch.render(dot, environment);
            }
        }
    }

    /**
     * Where a ball coming to the person's side drops to passing height (projected on the floor):
     * a soft white disc with a ring that closes in as the ball arrives.
     */
    public void renderReception(ModelBatch batch, float x, float z, float timeLeft) {
        float closing = MathUtils.clamp(timeLeft / 1.2f, 0f, 1f);
        float radius = 0.35f + 0.45f * closing;
        receptionRing.transform.setToTranslation(x, 0.016f, z).scale(radius, 1f, radius);
        receptionDisc.transform.setToTranslation(x, 0.015f, z).scale(0.45f, 1f, 0.45f);
        batch.render(receptionDisc);
        batch.render(receptionRing);
    }

    /** Cyan ring where the controlled player is aiming (serves, attacks, free balls). */
    public void renderAim(ModelBatch batch, float x, float z) {
        aim.transform.setToTranslation(x, 0.016f, z).scale(0.35f, 1f, 0.35f);
        batch.render(aim);
    }

    /**
     * UV sphere whose faces are coloured by the dominant axis of their centre, giving six
     * patches in three colours — enough to see the ball rotate.
     */
    private static Model buildPanelBall() {
        Color[] colors = {
                new Color(0.98f, 0.82f, 0.10f, 1f),
                new Color(0.10f, 0.30f, 0.80f, 1f),
                new Color(0.97f, 0.97f, 0.95f, 1f)};
        ModelBuilder builder = new ModelBuilder();
        builder.begin();
        MeshPartBuilder part = builder.part("ball", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.ColorPacked, new Material(ColorAttribute.createDiffuse(Color.WHITE),
                        ShadingAttribute.glossy()));
        int slices = 28;
        int stacks = 18;
        float r = Ball.RADIUS;
        Vector3[] corners = {new Vector3(), new Vector3(), new Vector3(), new Vector3()};
        Vector3 centre = new Vector3();
        MeshPartBuilder.VertexInfo vertex = new MeshPartBuilder.VertexInfo();
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                spherePoint(i, j, stacks, slices, corners[0]);
                spherePoint(i + 1, j, stacks, slices, corners[1]);
                spherePoint(i + 1, j + 1, stacks, slices, corners[2]);
                spherePoint(i, j + 1, stacks, slices, corners[3]);
                centre.set(corners[0]).add(corners[1]).add(corners[2]).add(corners[3]);
                float ax = Math.abs(centre.x), ay = Math.abs(centre.y), az = Math.abs(centre.z);
                Color color = ax >= ay && ax >= az ? colors[0] : ay >= az ? colors[1] : colors[2];
                short[] index = new short[4];
                for (int k = 0; k < 4; k++) {
                    vertex.set(null, null, color, null);
                    vertex.setPos(corners[k].x * r, corners[k].y * r, corners[k].z * r);
                    vertex.setNor(corners[k].x, corners[k].y, corners[k].z);
                    index[k] = part.vertex(vertex);
                }
                part.triangle(index[0], index[1], index[2]);
                part.triangle(index[0], index[2], index[3]);
            }
        }
        return builder.end();
    }

    /** Unit-sphere point for stack i (0 = north pole) and slice j, wound counter-clockwise from outside. */
    private static void spherePoint(int i, int j, int stacks, int slices, Vector3 out) {
        float theta = MathUtils.PI * i / stacks;
        float phi = -MathUtils.PI2 * j / slices;
        out.set(MathUtils.sin(theta) * MathUtils.cos(phi), MathUtils.cos(theta), MathUtils.sin(theta) * MathUtils.sin(phi));
    }

    @Override
    public void dispose() {
        ballModel.dispose();
        shadowModel.dispose();
        landingModel.dispose();
        dotModel.dispose();
        aimModel.dispose();
        trailModel.dispose();
        receptionDiscModel.dispose();
        receptionRingModel.dispose();
    }
}
