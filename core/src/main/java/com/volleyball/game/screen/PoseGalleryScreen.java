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
import com.volleyball.game.entities.Ball;
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
            "forearm pass", "jump set", "back-row spike", "serve", "block jump", "forward dive", "side fall + roll",
            "dive (knee drop)", "first-tempo attack", "wing attack", "decoy jump"};
    /** The action loops run at this speed (1 = real time). */
    private static final float LOOP_SPEED = 0.6f;

    private final VolleyballGame game;
    private final VolleyballCourt court;
    private final Team home = TeamFactory.home(0);
    private final Team away = TeamFactory.away(6);
    private final List<VolleyballPlayer> players = new ArrayList<>();
    private final List<VolleyballPlayer> studyPlayers = List.of(home.libero(), home.players().get(2), home.players().get(1));
    private final Vector3[] lookAt = new Vector3[LABELS.length];
    private ArenaScene scene;
    private PlayerRenderer renderer;
    private PerspectiveCamera camera;
    private SpriteBatch batch;
    private float time;
    /** Pose shown in close-up, or -1 for the overview. */
    private int focus;
    private boolean sideView;
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
        if (game.launchOptions().modelStudy()) {
            renderModelStudy();
            return;
        }
        int shown = Math.min(LABELS.length, players.size());
        for (int i = 0; i < shown; i++) arrange(players.get(i), i, delta);
        for (int n = 0; n <= 9; n++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0 + n)) focus = n == 0 ? -1 : n + 6;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) sideView = !sideView;
        if (focus >= 0 && focus < shown) {
            // Close-up from the front-left, a little above the head.
            VolleyballPlayer p = players.get(focus);
            camera.position.set(p.position.x - 4.2f, 1.9f, p.position.z - 2.2f);
            camera.direction.set(4.2f, -0.25f, 2.2f).nor();
            if (focus >= 12 && focus <= 14) {
                float side = p.diveDirection.z;
                camera.position.set(p.position.x - 3.5f, 1.5f, p.position.z - side * 0.4f);
                camera.direction.set(3.5f, -0.65f, side * 0.9f).nor();
            } else if (focus == 10) {
                camera.position.set(p.position.x - 5.3f, 2.5f, p.position.z - 2.8f);
                camera.direction.set(5.3f, -0.48f, 2.8f).nor();
            }
            if (sideView) {
                camera.position.set(p.position.x - 1.25f, focus == 10 ? 2.5f : 1.75f, p.position.z - (focus == 10 ? 6.4f : 4.7f));
                camera.lookAt(p.position.x, focus == 10 ? 2.1f : 1.55f, p.position.z);
            }
        } else {
            camera.position.set(-16f, 4.4f, 0f);
            camera.direction.set(11.5f, -3.5f, 0f).nor();
        }
        camera.up.set(Vector3.Y);
        camera.update();

        boolean closeup = focus >= 0 && focus < shown;
        boolean contactPreview = closeup && switch (focus) {
            case 8, 15 -> previewCombination();
            case 9, 16 -> previewSpike(players.get(focus));
            case 10 -> previewServe(players.get(focus));
            default -> previewReception(players.get(focus));
        };
        renderer.setBall(contactPreview ? receiveBall : null);
        renderer.setBlobShadows(!scene.quality().arenaShader);
        scene.setOccluders(closeup ? List.of(players.get(focus)) : players.subList(0, shown),
                contactPreview ? receiveBall : heldBall);
        int count = shown;
        scene.render(camera, (batch, environment, mirror) -> {
            if (mirror) {
                renderer.drawLast(batch, environment);
                if (contactPreview) ballRenderer.drawLast(batch, environment);
                return;
            }
            renderer.setAppending(false);
            renderer.setAppending(true);
            for (int i = 0; i < count; i++) {
                if (closeup && i != focus) continue;
                renderer.render(batch, environment, List.of(players.get(i)), 1f, null, lookAt[i]);
            }
            VolleyballPlayer server = players.get(10);
            if (contactPreview) {
                ballRenderer.setHeldAt(null);
                ballRenderer.render(batch, environment, receiveBall, 1f, null, false);
            } else if ((!closeup || focus == 10) && count > 10 && server.actionPose() == ActionPose.HOLD_BALL
                    && renderer.heldBallPoint(server, heldAt) != null) {
                ballRenderer.setHeldAt(heldAt);
                ballRenderer.render(batch, environment, heldBall, 1f, null, false);
            }
            renderer.setAppending(false);
        });

        float s = UiFonts.uiScale(Gdx.graphics.getHeight());
        BitmapFont font = game.fonts().get(UiFonts.Weight.SEMIBOLD, 13f * s);
        batch.begin();
        if (closeup && (focus == 8 || focus == 9 || focus == 10 || focus == 15 || focus == 16)) {
            String label = focus == 10 ? players.get(10).swingStyle().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    : focus == 8 ? SET_LABELS[(int) (time * LOOP_SPEED * 0.5f / 1.8f) % SET_LABELS.length] : LABELS[focus];
            if (focus == 9 || focus == 16) label += " · " + players.get(focus).swingStyle().name().toLowerCase(java.util.Locale.ROOT);
            if (focus != 8) label += players.get(focus).isLeftHanded() ? " · left hand" : " · right hand";
            UiKit.text(batch, font, label, 15f * s, Gdx.graphics.getHeight() - 18f * s,
                    380f * s, Align.left, false, UiKit.TEXT, 1f);
        }
        for (int i = 0; i < shown && focus < 0; i++) {
            VolleyballPlayer p = players.get(i);
            camera.project(tmp.set(p.position.x, 2.45f, p.position.z));
            String label = i == 9 || i == 10 ? LABELS[i] + ": " + p.swingStyle().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    : i == 11 ? LABELS[i] + ": " + BLOCKS[(int) (time * LOOP_SPEED / 1.8f) % BLOCKS.length] : LABELS[i];
            UiKit.text(batch, font, label, tmp.x - 80f * s, tmp.y, 160f * s, Align.center, false, UiKit.TEXT, 1f);
        }
        batch.end();
    }

    /** Repeatable lineup and portrait views with the actual Northern Wolves players and lighting. */
    private void renderModelStudy() {
        int view = (int) (time / 1.4f) % 9;
        boolean lineup = view < 3;
        float angle = switch (view) {
            case 0 -> 0.21f;
            case 1 -> MathUtils.PI - 0.17f;
            case 2 -> MathUtils.HALF_PI;
            case 3, 4, 5 -> 0.47f;
            default -> MathUtils.HALF_PI;
        };
        List<VolleyballPlayer> shown = lineup ? studyPlayers : List.of(studyPlayers.get((view - 3) % 3));
        for (int i = 0; i < shown.size(); i++) {
            VolleyballPlayer p = shown.get(i);
            float z = lineup ? (i - 1) * 1.38f : 0f;
            float x = lineup && view == 2 ? -5.5f + (i - 1) * 1.38f : -5.5f;
            p.placeAt(x, z);
            p.previousFacingYaw = p.facingYaw = MathUtils.PI;
            p.setCrouching(lineup);
        }
        float radius = lineup ? 7f : 2.36f;
        camera.position.set(-5.5f - radius * MathUtils.cos(angle), lineup ? 2.56f : 1.66f,
                -radius * MathUtils.sin(angle));
        camera.direction.set(-5.5f, lineup ? 1.07f : 1.43f, 0f).sub(camera.position).nor();
        camera.up.set(Vector3.Y);
        camera.update();
        renderer.setBlobShadows(!scene.quality().arenaShader);
        scene.setOccluders(shown, heldBall);
        scene.render(camera, (batch, environment, mirror) -> {
            if (mirror) renderer.drawLast(batch, environment);
            else renderer.render(batch, environment, shown, 1f, null, null);
        });
    }

    /** Places player {@code i} in its gallery slot with a scripted (and for actions, looping) state. */
    private void arrange(VolleyballPlayer p, int i, float delta) {
        int row = i / 7;
        int column = i % 7;
        float x = row == 0 ? -6.0f : -9.2f;
        if (i == 9) x = -8.5f;
        if (i == 16 || i == 17) x = -1.65f;
        float z = row == 0 ? -6.0f + column * 2.0f : -4.8f + column * 1.6f;
        p.position.set(x, 0f, z);
        p.beginStrideStep();
        p.previousPosition.set(p.position);
        p.velocity.setZero();
        p.setAirborne(false);
        p.setCrouching(false);
        p.setJumpCharge(0f);
        p.setRecoveryTime(0f);
        p.setBlocking(false, 0f);
        if (i < 12 || i >= 15) p.setBodyState(BodyState.UPRIGHT);
        float yaw = MathUtils.PI; // facing the camera (at -X)
        lookAt[i].set(x - 1.2f, 1.2f, z);
        // Close-ups run slower, so every phase of a movement can be seen.
        float t = time * (focus >= 0 ? LOOP_SPEED * 0.5f : LOOP_SPEED);
        switch (i) {
            case 1 -> {
                float cycle = t % 2.7f;
                float speed = cycle < 0.7f ? 1.4f * cycle / 0.7f
                        : cycle < 1.65f ? 1.4f + 4.7f * (cycle - 0.7f) / 0.95f
                        : 6.1f * Math.max(0f, 1f - (cycle - 1.65f) / 0.55f);
                run(p, yaw, speed, 0f, delta);
            }
            case 2 -> {
                p.setCrouching(true);
                run(p, yaw, 0f, 2.6f * MathUtils.sin(t * MathUtils.PI), delta);
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
            case 7 -> {
                if (focus != i) contactLoop(p, t, ActionPose.PLATFORM);
            }
            case 8 -> {
                setLoop(p, t, i);
            }
            case 9, 16, 17 -> spikeLoop(p, t, x, z, i);
            case 10 -> serveLoop(p, t, x, z, i);
            case 11 -> blockLoop(p, t);
            case 12 -> {
                // A defensive specialist: flat on the chest (see FallKind).
                // Diving away from its neighbour on the right (the side fall goes that way).
                yaw = MathUtils.HALF_PI;
                p.attributes.defense = 90;
                p.diveDirection.set(0f, 0f, -1f);
                p.setDiveRoll(false);
                diveLoop(p, t);
            }
            case 13 -> {
                // Sideways to the free end of the row.
                float direction = focus == i && ((int) ((t + 0.6f) / 1.8f) & 1) != 0 ? -1f : 1f;
                p.diveDirection.set(0f, 0f, direction);
                p.setDiveRoll(true);
                diveLoop(p, t + 0.6f);
            }
            case 14 -> {
                // Not a specialist: down through the front knee.
                yaw = -MathUtils.HALF_PI;
                p.attributes.defense = 40;
                p.diveDirection.set(0f, 0f, 1f);
                p.setDiveRoll(false);
                diveLoop(p, t + 1.2f);
            }
            case 15 -> quickAttackLoop(p, t, i);
            default -> {
            }
        }
        if (focus == i && i >= 12 && i <= 14) {
            float cycle = p.bodyStateTime();
            float flight = p.bodyState() == BodyState.DIVING ? cycle : PlayerLocomotion.DIVE_TIME;
            float slide = p.bodyState() == BodyState.ON_FLOOR ? cycle : p.bodyState() == BodyState.DIVING ? 0f : PlayerLocomotion.FLOOR_TIME;
            float distance = 3f * flight + 0.45f * (1f - (float) Math.exp(-7f * slide));
            p.position.mulAdd(p.diveDirection, distance);
            p.previousPosition.set(p.position);
        }
        if (i < 5 || i == 6) p.setActionPose(ActionPose.NONE, 0f);
        else if (i < 12 || i >= 16) p.tickActionPose(delta * LOOP_SPEED);
        p.facingYaw = yaw;
        p.previousFacingYaw = yaw;
    }

    /** Close-up with a timed incoming ball for alternating low, high and one-arm receptions. */
    private boolean previewReception(VolleyballPlayer p) {
        boolean dive = focus >= 12;
        if (!dive && focus != 7) return false;
        float t = time * LOOP_SPEED * 0.5f;
        float cycle = dive ? (t + (focus - 12) * 0.6f) % 1.8f : t % 1.8f;
        int variant = (int) (t / 1.8f) % 3;
        float contact = dive ? 0.27f : 0.80f;
        float elapsed = cycle - contact;
        ActionPose pose = dive ? ActionPose.DIVE_DIG : variant == 2 ? ActionPose.ONE_ARM_DIG : ActionPose.PLATFORM;
        if (elapsed < -0.15f) p.setActionPose(ActionPose.NONE, 0f);
        else if (elapsed < 0f) p.holdActionPose(pose);
        else if (p.isActionPoseHeld()) p.setActionPose(pose, 0.35f);
        if (dive) p.tickActionPose(Gdx.graphics.getDeltaTime() * LOOP_SPEED * 0.5f);
        if (elapsed < (dive ? -0.26f : -0.58f) || elapsed > 0.44f) return false;

        float fx = dive ? p.diveDirection.x : p.facingX();
        float fz = dive ? p.diveDirection.z : p.facingZ();
        float height = dive ? 0.39f : variant == 0 ? 0.60f : 1.03f;
        float side = !dive && variant == 2 ? 0.56f : 0f;
        float distance = dive ? 1.90f : 0.45f;
        float vx = elapsed < 0f ? -3.6f : 1.3f;
        float vy = elapsed < 0f ? -2.9f : 3.8f;
        distance += vx * elapsed;
        lookAt[focus].set(p.position.x + fx * distance - fz * side, height + vy * elapsed,
                p.position.z + fz * distance + fx * side);
        receiveBall.launch(lookAt[focus], previewVelocity.set(fx * vx, vy, fz * vx), Vector3.Zero, 0);
        return true;
    }

    /** Ready (held pose), the contact, follow-through and a pause: 1.6 s. */
    private static void contactLoop(VolleyballPlayer p, float t, ActionPose pose) {
        float cycle = t % 1.6f;
        if (cycle < 0.8f) {
            p.setCrouching(pose == ActionPose.PLATFORM);
            p.holdActionPose(pose);
        } else if (p.isActionPoseHeld()) {
            p.setActionPose(pose, 0.35f);
        }
    }

    private static final ActionPose[] SETS = {ActionPose.SET_FORWARD, ActionPose.SET_FORWARD, ActionPose.SET_BACK,
            ActionPose.SET_FAST, ActionPose.SET_FAST_BACK};
    private static final String[] SET_LABELS = {"jump set", "standing set", "back set", "fast jump set", "fast back set"};
    private final Vector3 previewContact = new Vector3();
    private float previewContactAge;
    private boolean previewBackSet;

    private void setLoop(VolleyballPlayer p, float t, int i) {
        float cycle = t % 1.8f;
        int variation = (int) (t / 1.8f) % SETS.length;
        ActionPose form = SETS[variation];
        boolean jump = variation == 0 || form.fastSet();
        float takeoff = 0.43f;
        float vy0 = form.fastSet() ? 2.92f : 3.1f;
        float flight = 2f * vy0 / PhysicsConstants.GRAVITY;
        float hit = jump ? takeoff + flight * (form.fastSet() ? 0.38f : 0.51f) : 0.74f;
        float elapsed = cycle - takeoff;
        if (jump && elapsed < 0f) p.setJumpCharge(MathUtils.clamp((cycle - 0.22f) / 0.21f, 0f, 1f));
        if (jump && elapsed >= 0f && elapsed < flight) {
            previewJump(p, vy0, elapsed);
        }
        float hitHeight = jump ? jumpHeightAt(vy0, hit - takeoff) : 0f;
        lookAt[i].set(p.position.x - 0.18f, hitHeight + p.height + 0.10f, p.position.z);
        if (cycle >= (jump ? takeoff : 0.31f) && cycle < hit) p.holdActionPose(form);
        else if (cycle >= hit && p.isActionPoseHeld()) {
            p.setActionPose(form, form.fastSet() ? 0.23f : 0.35f);
            p.recordActionContact(lookAt[i]);
        }
        if (focus == i) {
            previewContact.set(lookAt[i]);
            previewContactAge = cycle - hit;
            previewBackSet = form.backSet();
        }
    }

    private void quickAttackLoop(VolleyballPlayer p, float t, int i) {
        float cycle = t % 1.85f;
        float takeoff = 0.41f;
        float vy0 = 3.50f;
        float flight = 2f * vy0 / PhysicsConstants.GRAVITY;
        float elapsed = cycle - takeoff;
        float hit = takeoff + 0.35f;
        p.setLeftHanded(((int) (t / 1.85f) & 1) != 0);
        p.setSwingStyle(com.volleyball.game.entities.SwingStyle.POWER);
        if (elapsed < 0f) p.setJumpCharge(MathUtils.clamp(cycle / takeoff, 0f, 1f));
        else if (elapsed < flight) previewJump(p, vy0, elapsed);
        lookAt[i].set(p.position.x - .35f, jumpHeightAt(vy0, .35f) + p.standingReach() - .02f,
                p.position.z - .18f * p.handSign());
        if (cycle >= takeoff && cycle < hit - .14f) {
            if (p.actionPose() != ActionPose.QUICK_READY) p.setActionPose(ActionPose.QUICK_READY, .25f);
        } else if (cycle >= hit - .14f && cycle < hit) p.holdActionPose(ActionPose.QUICK_SWING);
        else if (cycle >= hit && p.isActionPoseHeld()) {
            p.setActionPose(ActionPose.QUICK_SWING, .22f);
            p.recordActionContact(lookAt[i]);
        }
        if (focus == i) {
            previewContact.set(lookAt[i]);
            previewContactAge = cycle - hit;
            previewBackSet = false;
        }
    }

    private static void previewJump(VolleyballPlayer player, float speed, float elapsed) {
        player.setAirborne(true);
        player.velocity.y = speed - PhysicsConstants.GRAVITY * elapsed;
        player.position.y = jumpHeightAt(speed, elapsed);
    }

    private static float jumpHeightAt(float speed, float elapsed) {
        return speed * elapsed - 0.5f * PhysicsConstants.GRAVITY * elapsed * elapsed;
    }

    private boolean previewCombination() {
        if (previewContactAge < -0.34f || previewContactAge > 0.20f) return false;
        Vector3 at = lookAt[focus];
        if (focus == 15) {
            previewVelocity.set(previewContactAge < 0f ? 0.8f : -18f, previewContactAge < 0f ? 2.6f : -4f,
                    previewContactAge < 0f ? -7f : 0f);
        } else if (previewContactAge < 0f) {
            previewVelocity.set(-1.6f, -4.4f, 0f);
        } else {
            previewVelocity.set(-0.4f, 3.2f, previewBackSet ? 5.4f : -4.2f);
        }
        at.set(previewContact).mulAdd(previewVelocity, previewContactAge);
        receiveBall.launch(at, previewVelocity, Vector3.Zero, 0);
        return true;
    }

    /** Approach steps, jump charge, take-off, bow and arrow, swing at the top, landing: 2.2 s. */
    /** The attack styles the spike loop goes through, one per loop. */
    private static final com.volleyball.game.entities.SwingStyle[] SPIKES = {
            com.volleyball.game.entities.SwingStyle.POWER, com.volleyball.game.entities.SwingStyle.CUT,
            com.volleyball.game.entities.SwingStyle.ROLL, com.volleyball.game.entities.SwingStyle.TIP,
            com.volleyball.game.entities.SwingStyle.TOOL};
    private static final com.volleyball.game.entities.SwingStyle[] SERVES = {
            com.volleyball.game.entities.SwingStyle.JUMP_FLOAT_SERVE, com.volleyball.game.entities.SwingStyle.JUMP_TOPSPIN_SERVE,
            com.volleyball.game.entities.SwingStyle.FLOAT_SERVE, com.volleyball.game.entities.SwingStyle.TOPSPIN_SERVE,
            com.volleyball.game.entities.SwingStyle.UNDERHAND_SERVE};
    private static final float SERVE_PERIOD = 2.75f;
    private static final float TOSS_AT = 0.43f;
    private static final float SERVE_TAKEOFF = 1.13f;
    private float previewServeTime;
    private float previewServeHit;
    private final Vector3 previewServeFrom = new Vector3();
    private final Vector3 previewServeTo = new Vector3();
    /** The serve loop's ball, drawn in the server's hands while it is held. */
    private final com.volleyball.game.entities.Ball heldBall = new com.volleyball.game.entities.Ball();
    private final Ball receiveBall = new Ball();
    private final Vector3 previewVelocity = new Vector3();
    private final Vector3 heldAt = new Vector3();
    private com.volleyball.game.render.BallRenderer ballRenderer;

    private void spikeLoop(VolleyballPlayer p, float t, float x, float z, int i) {
        float cycle = t % 2.2f;
        int lap = (int) (t / 2.2f);
        int style = lap;
        if (focus == i) {
            p.setLeftHanded((lap & 1) != 0);
            style = lap / 2;
        }
        p.setSwingStyle(SPIKES[style % SPIKES.length]);
        float takeoff = 0.6f;
        float vy0 = 3.7f;
        float flight = 2f * vy0 / PhysicsConstants.GRAVITY;
        float hit = takeoff + flight * 0.55f;
        float side = p.isLeftHanded() ? 0.24f : -0.24f;
        lookAt[i].set(x - 0.32f, p.standingReach() + jumpHeightAt(vy0, hit - takeoff) - 0.08f, z + side);
        if (focus == i) {
            previewContact.set(lookAt[i]);
            previewContactAge = cycle - hit;
        }
        if (cycle < takeoff) {
            // Approach and load: run, then sink and swing the arms back.
            if (cycle < 0.35f) {
                run(p, MathUtils.PI, 3.5f, 0f, Gdx.graphics.getDeltaTime() * LOOP_SPEED);
            } else {
                p.setJumpCharge((cycle - 0.35f) / (takeoff - 0.35f));
            }
            if (p.actionPose() != ActionPose.NONE) p.setActionPose(ActionPose.NONE, 0f);
        } else if (cycle < takeoff + flight) {
            float a = cycle - takeoff;
            p.recordTakeoff();
            p.setAirborne(true);
            p.velocity.x = -2.2f;
            p.velocity.y = vy0 - PhysicsConstants.GRAVITY * a;
            p.position.y = vy0 * a - 0.5f * PhysicsConstants.GRAVITY * a * a;
            if (i == 17) {
                p.holdActionPose(ActionPose.QUICK_READY);
            } else if (cycle < hit) {
                p.holdActionPose(ActionPose.SWING);
            } else if (p.isActionPoseHeld()) {
                p.setActionPose(ActionPose.SWING, 0.35f);
                p.recordActionContact(lookAt[i]);
            }
        } else if (cycle < takeoff + flight + 0.3f) {
            if (i == 17) p.setActionPose(ActionPose.NONE, 0f);
            p.setRecoveryTime(0.2f);
        }
    }

    private boolean previewSpike(VolleyballPlayer player) {
        if (previewContactAge < -0.33f || previewContactAge > 0.20f) return false;
        previewVelocity.set(previewContactAge < 0f ? 0.4f : -17f, previewContactAge < 0f ? -1.2f : -3.8f,
                previewContactAge < 0f ? (player.isLeftHanded() ? -5f : 5f) : 0f);
        lookAt[focus].set(previewContact).mulAdd(previewVelocity, previewContactAge);
        receiveBall.launch(lookAt[focus], previewVelocity, Vector3.Zero, 0);
        return true;
    }

    /** Standing and jumping serves use the same timed contact with different physical movement. */
    private void serveLoop(VolleyballPlayer p, float t, float x, float z, int i) {
        float cycle = t % SERVE_PERIOD;
        int lap = (int) (t / SERVE_PERIOD);
        p.setSwingStyle(SERVES[lap % SERVES.length]);
        p.setLeftHanded((lap / SERVES.length & 1) != 0);
        boolean jump = p.swingStyle().jumpServe();
        boolean power = p.swingStyle() == com.volleyball.game.entities.SwingStyle.JUMP_TOPSPIN_SERVE;
        float up = power ? 4.05f : 3.15f;
        float flight = 2f * up / PhysicsConstants.GRAVITY;
        float advance = power ? 1.66f : 0.81f;
        float carry = power ? 1.75f : 0.72f;
        float hit = jump ? SERVE_TAKEOFF + flight * (power ? 0.53f : 0.48f) : 1.39f;
        var type = switch (p.swingStyle()) {
            case JUMP_FLOAT_SERVE -> com.volleyball.game.mechanics.ServeType.JUMP_FLOAT;
            case JUMP_TOPSPIN_SERVE -> com.volleyball.game.mechanics.ServeType.JUMP_TOPSPIN;
            case TOPSPIN_SERVE -> com.volleyball.game.mechanics.ServeType.POWER;
            case UNDERHAND_SERVE -> com.volleyball.game.mechanics.ServeType.UNDERHAND;
            default -> com.volleyball.game.mechanics.ServeType.FLOAT;
        };
        lookAt[i].set(x - 1.5f, 1.6f, z);
        if (cycle < TOSS_AT) {
            p.holdActionPose(ActionPose.HOLD_BALL);
        } else if (cycle < hit) {
            if (p.actionPose() != ActionPose.TOSS) p.setActionPose(ActionPose.TOSS, 1.4f);
        } else if (cycle < hit + 0.35f && p.actionPose() != ActionPose.SWING) {
            p.setActionPose(ActionPose.SWING, 0.35f);
        }
        if (jump && cycle >= TOSS_AT) {
            float approach = MathUtils.clamp((cycle - TOSS_AT) / (SERVE_TAKEOFF - TOSS_AT), 0f, 1f);
            float elapsed = MathUtils.clamp(cycle - SERVE_TAKEOFF, 0f, flight);
            p.position.x = x - advance * approach * approach - carry * elapsed;
            if (cycle < SERVE_TAKEOFF) {
                p.velocity.x = -2f * advance * approach / (SERVE_TAKEOFF - TOSS_AT);
                p.addStride(-p.velocity.x * Math.max(0f, Gdx.graphics.getDeltaTime()) * (focus >= 0 ? LOOP_SPEED * .5f : LOOP_SPEED));
                p.setJumpCharge(MathUtils.clamp((approach - 0.73f) / 0.27f, 0f, 1f));
            } else if (cycle < SERVE_TAKEOFF + flight) {
                p.setAirborne(true);
                p.position.y = up * elapsed - 0.5f * PhysicsConstants.GRAVITY * elapsed * elapsed;
                p.velocity.set(-carry, up - PhysicsConstants.GRAVITY * elapsed, 0f);
            }
        }
        if (focus == i) {
            float a = hit - SERVE_TAKEOFF;
            float reach = type.overhand ? p.standingReach() - 0.05f : p.height * 0.42f;
            previewServeTime = cycle;
            previewServeHit = hit;
            previewServeFrom.set(x - (type.overhand ? 0.45f : 0.42f), p.height * (type.overhand ? .70f : .58f), z);
            previewServeTo.set(x - (jump ? advance + carry * a : 0f) - (type.overhand ? 0.30f : 0.45f),
                    reach + (jump ? up * a - 0.5f * PhysicsConstants.GRAVITY * a * a : 0f),
                    z - (type.overhand ? .15f : .10f) * p.handSign());
        }
    }

    private boolean previewServe(VolleyballPlayer server) {
        float elapsed = previewServeTime - TOSS_AT;
        if (elapsed <= 0f || previewServeTime > previewServeHit + 0.21f) return false;
        float duration = previewServeHit - TOSS_AT;
        Vector3 at = lookAt[10];
        if (elapsed < duration) {
            at.set(previewServeFrom).lerp(previewServeTo, elapsed / duration);
            at.y += 0.5f * PhysicsConstants.GRAVITY * elapsed * (duration - elapsed);
            previewVelocity.set(previewServeTo).sub(previewServeFrom).scl(1f / duration);
            previewVelocity.y += 0.5f * PhysicsConstants.GRAVITY * (duration - 2f * elapsed);
        } else {
            previewVelocity.set(server.swingStyle().jumpServe() ? -17f : -14f, -0.6f, 0f);
            at.set(previewServeTo).mulAdd(previewVelocity, elapsed - duration);
        }
        receiveBall.launch(at, previewVelocity, Vector3.Zero, 0);
        return true;
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
