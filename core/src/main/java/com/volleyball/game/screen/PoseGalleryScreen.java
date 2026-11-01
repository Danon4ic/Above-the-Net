package com.volleyball.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.volleyball.game.VolleyballGame;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.PhysicsConstants;
import com.volleyball.game.physics.PlayerLocomotion;
import com.volleyball.game.render.ArenaScene;
import com.volleyball.game.render.PlayerRenderer;
import com.volleyball.game.simulation.TeamFactory;
import com.volleyball.game.ui.UiFonts;
import com.volleyball.game.ui.UiKit;
import com.volleyball.game.world.VolleyballCourt;

import java.util.ArrayList;
import java.util.List;

/**
 * Developer screen ({@code --pose-gallery}): every animation side by side, the ball actions as
 * looping sequences (ready, contact, follow-through; take-off, bow and arrow, swing, landing),
 * so poses and transitions can be checked without playing.
 */
public final class PoseGalleryScreen extends ScreenAdapter {
    private static final String[] LABELS = {
            "idle", "run forward", "side shuffle", "backpedal", "ready stance", "celebrate", "block ready",
            "forearm pass", "overhead set", "spike", "serve", "block jump", "dive", "side fall + roll"};
    /** The action loops run at this speed (1 = real time). */
    private static final float LOOP_SPEED = 0.6f;

    private final VolleyballGame game;
    private final VolleyballCourt court;
    private final Team home = TeamFactory.home(0);
    private final Team away = TeamFactory.away(6);
    private final List<VolleyballPlayer> players = new ArrayList<>();
    private final Vector3[] lookAt = new Vector3[LABELS.length];
    private ArenaScene scene;
    private PlayerRenderer renderer;
    private PerspectiveCamera camera;
    private SpriteBatch batch;
    private float time;
    /** Pose shown in close-up, or -1 for the overview. */
    private int focus;
    private final Vector3 tmp = new Vector3();

    public PoseGalleryScreen(VolleyballGame game) {
        this.game = game;
        this.court = new VolleyballCourt(game.config().netCategory());
        players.addAll(home.squad());
        players.addAll(away.squad());
        for (VolleyballPlayer p : players) p.setOnCourt(true);
        for (int i = 0; i < lookAt.length; i++) lookAt[i] = new Vector3();
        focus = game.launchOptions().poseFocus();
    }

    @Override
    public void show() {
        scene = new ArenaScene(court, game.config().venue(), game.config().courtTheme(), game.config().lighting(), game.config().graphicsQuality());
        renderer = new PlayerRenderer(List.of(home, away));
        ballRenderer = new com.volleyball.game.render.BallRenderer();
        camera = new PerspectiveCamera(45f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 200f;
        batch = new SpriteBatch();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void render(float delta) {
        // Esc never closes the game: back to the main menu.
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.showMainMenu();
            return;
        }
        time += delta;
        scene.update(delta);
        int shown = Math.min(LABELS.length, players.size());
        for (int i = 0; i < shown; i++) arrange(players.get(i), i, delta);
        for (int n = 0; n <= 9; n++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0 + n)) focus = n == 0 ? -1 : n + 6;
        }
        if (focus >= 0 && focus < shown) {
            // Close-up from the front-left, a little above the head.
            VolleyballPlayer p = players.get(focus);
            camera.position.set(p.position.x - 4.2f, 1.9f, p.position.z - 2.2f);
            camera.direction.set(4.2f, -0.25f, 2.2f).nor();
        } else {
            camera.position.set(-16f, 4.4f, 0f);
            camera.direction.set(11.5f, -3.5f, 0f).nor();
        }
        camera.up.set(Vector3.Y);
        camera.update();

        renderer.setBlobShadows(!scene.quality().arenaShader);
        scene.setOccluders(players.subList(0, shown), new com.volleyball.game.entities.Ball());
        int count = shown;
        scene.render(camera, (batch, environment, mirror) -> {
            if (mirror) {
                renderer.drawLast(batch, environment);
                return;
            }
            renderer.setAppending(false);
            renderer.setAppending(true);
            for (int i = 0; i < count; i++) {
                renderer.render(batch, environment, List.of(players.get(i)), 1f, null, lookAt[i]);
            }
            VolleyballPlayer server = players.get(10);
            if (count > 10 && server.actionPose() == ActionPose.HOLD_BALL && renderer.heldBallPoint(server, heldAt) != null) {
                ballRenderer.setHeldAt(heldAt);
                ballRenderer.render(batch, environment, heldBall, 1f, null, false);
            }
            renderer.setAppending(false);
        });

        float s = UiFonts.uiScale(Gdx.graphics.getHeight());
        BitmapFont font = game.fonts().get(UiFonts.Weight.SEMIBOLD, 13f * s);
        batch.begin();
        for (int i = 0; i < shown && focus < 0; i++) {
            VolleyballPlayer p = players.get(i);
            camera.project(tmp.set(p.position.x, 2.45f, p.position.z));
            String label = i == 9 || i == 10 ? LABELS[i] + ": " + p.swingStyle().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    : i == 11 ? LABELS[i] + ": " + BLOCKS[(int) (time * LOOP_SPEED / 1.8f) % BLOCKS.length] : LABELS[i];
            UiKit.text(batch, font, label, tmp.x - 80f * s, tmp.y, 160f * s, Align.center, false, UiKit.TEXT, 1f);
        }
        batch.end();
    }

    /** Places player {@code i} in its gallery slot with a scripted (and for actions, looping) state. */
    private void arrange(VolleyballPlayer p, int i, float delta) {
        int row = i / 7;
        int column = i % 7;
        float x = row == 0 ? -6.0f : -9.2f;
        float z = row == 0 ? -6.0f + column * 2.0f : -4.8f + column * 1.6f;
        p.position.set(x, 0f, z);
        p.previousPosition.set(p.position);
        p.velocity.setZero();
        p.setAirborne(false);
        p.setCrouching(false);
        p.setJumpCharge(0f);
        p.setRecoveryTime(0f);
        p.setBlocking(false, 0f);
        if (i < 12) p.setBodyState(BodyState.UPRIGHT);
        float yaw = MathUtils.PI; // facing the camera (at -X)
        lookAt[i].set(x - 1.2f, 1.2f, z);
        // Close-ups run slower, so every phase of a movement can be seen.
        float t = time * (focus >= 0 ? LOOP_SPEED * 0.5f : LOOP_SPEED);
        switch (i) {
            case 1 -> run(p, yaw + 0.5f, 4.5f, 0f, delta);
            case 2 -> {
                p.setCrouching(true);
                run(p, yaw, 0f, 2.2f, delta);
            }
            case 3 -> run(p, yaw, -2.5f, 0f, delta);
            case 4 -> p.setCrouching(true);
            case 5 -> {
                if (p.actionPose() != ActionPose.CELEBRATE) p.setActionPose(ActionPose.CELEBRATE, 1.3f);
            }
            case 6 -> {
                // At the net with the ball on the far side: block ready.
                p.position.set(-0.6f, 0f, 3.5f);
                p.previousPosition.set(p.position);
                lookAt[i].set(4f, 2.5f, 3.5f);
                yaw = 0f;
            }
            case 7 -> contactLoop(p, t, ActionPose.PLATFORM, 0.9f);
            case 8 -> {
                contactLoop(p, t, ActionPose.OVERHEAD, 2.2f);
                lookAt[i].set(x - 0.5f, 2.6f, z);
            }
            case 9 -> spikeLoop(p, t, x, z, i);
            case 10 -> serveLoop(p, t, x, z, i);
            case 11 -> blockLoop(p, t);
            case 12 -> {
                yaw = -MathUtils.HALF_PI;
                p.diveDirection.set(0f, 0f, 1f);
                p.setDiveRoll(false);
                diveLoop(p, t);
            }
            case 13 -> {
                p.diveDirection.set(0f, 0f, -1f);
                p.setDiveRoll(true);
                diveLoop(p, t + 0.6f);
            }
            default -> {
            }
        }
        if (i < 5 || i == 6) p.setActionPose(ActionPose.NONE, 0f);
        else if (i < 12) p.tickActionPose(delta * LOOP_SPEED);
        p.facingYaw = yaw;
        p.previousFacingYaw = yaw;
    }

    /** Ready (held pose), the contact, follow-through and a pause: 1.6 s. */
    private static void contactLoop(VolleyballPlayer p, float t, ActionPose pose, float ballHeight) {
        float cycle = t % 1.6f;
        if (cycle < 0.8f) {
            p.setCrouching(pose == ActionPose.PLATFORM);
            p.holdActionPose(pose);
        } else if (p.isActionPoseHeld()) {
            p.setActionPose(pose, 0.35f);
        }
    }

    /** Approach steps, jump charge, take-off, bow and arrow, swing at the top, landing: 2.2 s. */
    /** The attack styles the spike loop goes through, one per loop. */
    private static final com.volleyball.game.entities.SwingStyle[] SPIKES = {
            com.volleyball.game.entities.SwingStyle.POWER, com.volleyball.game.entities.SwingStyle.CUT,
            com.volleyball.game.entities.SwingStyle.ROLL, com.volleyball.game.entities.SwingStyle.TIP,
            com.volleyball.game.entities.SwingStyle.TOOL};
    private static final com.volleyball.game.entities.SwingStyle[] SERVES = {
            com.volleyball.game.entities.SwingStyle.FLOAT_SERVE, com.volleyball.game.entities.SwingStyle.TOPSPIN_SERVE,
            com.volleyball.game.entities.SwingStyle.UNDERHAND_SERVE};
    /** The serve loop's ball, drawn in the server's hands while it is held. */
    private final com.volleyball.game.entities.Ball heldBall = new com.volleyball.game.entities.Ball();
    private final Vector3 heldAt = new Vector3();
    private com.volleyball.game.render.BallRenderer ballRenderer;

    private void spikeLoop(VolleyballPlayer p, float t, float x, float z, int i) {
        float cycle = t % 2.2f;
        p.setSwingStyle(SPIKES[(int) (t / 2.2f) % SPIKES.length]);
        float takeoff = 0.6f;
        float vy0 = 3.7f;
        float flight = 2f * vy0 / PhysicsConstants.GRAVITY;
        lookAt[i].set(x - 0.6f, 3.3f, z);
        if (cycle < takeoff) {
            // Approach and load: run, then sink and swing the arms back.
            if (cycle < 0.35f) {
                run(p, MathUtils.PI, 3.5f, 0f, Gdx.graphics.getDeltaTime() * LOOP_SPEED);
            } else {
                p.setJumpCharge((cycle - 0.35f) / (takeoff - 0.35f));
            }
            if (p.actionPose() == ActionPose.SWING) p.setActionPose(ActionPose.NONE, 0f);
        } else if (cycle < takeoff + flight) {
            float a = cycle - takeoff;
            p.setAirborne(true);
            p.velocity.y = vy0 - PhysicsConstants.GRAVITY * a;
            p.position.y = vy0 * a - 0.5f * PhysicsConstants.GRAVITY * a * a;
            // Hit just after the top of the jump.
            if (a > flight * 0.55f && p.actionPose() != ActionPose.SWING) p.setActionPose(ActionPose.SWING, 0.35f);
        } else if (cycle < takeoff + flight + 0.3f) {
            p.setRecoveryTime(0.2f);
        }
    }

    /** Hold the ball, toss, hit (standing float serve): 2.4 s. */
    private void serveLoop(VolleyballPlayer p, float t, float x, float z, int i) {
        float cycle = t % 2.4f;
        p.setSwingStyle(SERVES[(int) (t / 2.4f) % SERVES.length]);
        lookAt[i].set(x - 1.5f, 1.6f, z);
        if (cycle < 0.8f) {
            p.holdActionPose(ActionPose.HOLD_BALL);
        } else if (cycle < 1.4f) {
            if (p.actionPose() != ActionPose.TOSS) p.setActionPose(ActionPose.TOSS, 1.2f);
            lookAt[i].set(x - 0.4f, 3.2f, z);
        } else if (p.actionPose() == ActionPose.TOSS) {
            p.setActionPose(ActionPose.SWING, 0.35f);
        }
    }

    /** Ready at the net, block jump with the hands over the net, landing: 1.8 s. */
    /** The block kinds the block loop goes through, one per loop: straight, line, diagonal, soft, swing. */
    private static final String[] BLOCKS = {"straight", "line", "diagonal", "soft", "swing"};

    private static void blockLoop(VolleyballPlayer p, float t) {
        float cycle = t % 1.8f;
        int kind = (int) (t / 1.8f) % BLOCKS.length;
        float vy0 = 3.3f;
        float flight = 2f * vy0 / PhysicsConstants.GRAVITY;
        if (cycle > 0.5f && cycle < 0.5f + flight) {
            float a = cycle - 0.5f;
            p.setAirborne(true);
            p.velocity.y = vy0 - PhysicsConstants.GRAVITY * a;
            p.position.y = vy0 * a - 0.5f * PhysicsConstants.GRAVITY * a * a;
            float reach = kind == 3 ? 0f : 0.25f * Math.max(0f, 1f - Math.abs(p.velocity.y) / 3f);
            float sideline = Math.signum(p.position.z == 0f ? 1f : p.position.z);
            float shift = kind == 1 ? sideline * 0.2f : kind == 2 ? -sideline * 0.2f : 0f;
            p.setBlocking(true, reach, shift);
            // The swing block: the blocker comes in along the net.
            if (kind == 4) p.velocity.z = 2.2f * Math.max(0f, p.velocity.y / vy0);
            p.holdActionPose(ActionPose.BLOCK);
        } else if (cycle >= 0.5f + flight && cycle < 0.5f + flight + 0.25f) {
            p.setRecoveryTime(0.2f);
        }
    }

    /** Plays dive, floor and getting-up phases in a loop with a short pause standing. */
    private static void diveLoop(VolleyballPlayer p, float t) {
        float cycle = t % 1.8f;
        if (cycle < PlayerLocomotion.DIVE_TIME) {
            p.setBodyState(BodyState.DIVING, cycle);
        } else if (cycle < PlayerLocomotion.DIVE_TIME + PlayerLocomotion.FLOOR_TIME) {
            p.setBodyState(BodyState.ON_FLOOR, cycle - PlayerLocomotion.DIVE_TIME);
        } else if (cycle < PlayerLocomotion.DIVE_TIME + PlayerLocomotion.FLOOR_TIME + PlayerLocomotion.GET_UP_TIME) {
            p.setBodyState(BodyState.GETTING_UP, cycle - PlayerLocomotion.DIVE_TIME - PlayerLocomotion.FLOOR_TIME);
        } else {
            p.setBodyState(BodyState.UPRIGHT, 0f);
        }
    }

    /** Fakes running: speed along/across the facing direction and an advancing stride. */
    private static void run(VolleyballPlayer p, float yaw, float forward, float right, float delta) {
        float fx = MathUtils.cos(yaw);
        float fz = -MathUtils.sin(yaw);
        p.velocity.set(fx * forward - fz * right, 0f, fz * forward + fx * right);
        p.addStride(p.groundSpeed() * delta);
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override
    public void dispose() {
        if (scene != null) scene.dispose();
        if (renderer != null) renderer.dispose();
        if (ballRenderer != null) ballRenderer.dispose();
        if (batch != null) batch.dispose();
    }
}
