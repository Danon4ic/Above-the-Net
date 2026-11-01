package com.volleyball.game.simulation;

import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.Team;
import com.badlogic.gdx.math.MathUtils;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BlockMechanic;
import com.volleyball.game.physics.Aerodynamics;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.BallContactListener;
import com.volleyball.game.physics.BallPhysics;
import com.volleyball.game.physics.CollisionSystem;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.physics.PlayerLocomotion;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.world.VolleyballCourt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything that is simulated: the court, the ball and both teams, advanced in fixed steps.
 * Contains no rendering or input code, so whole rallies can be simulated headless in tests.
 *
 * <p>Order within a step: players move (from their {@link MoveIntent}s), players are kept apart
 * and inside the arena, body colliders follow the players, armed ball actions make contact,
 * then the ball flies and collides.
 */
public final class GameWorld {
    private final VolleyballCourt court;
    private final Ball ball = new Ball();
    private final Team home;
    private final Team away;
    private final List<VolleyballPlayer> players = new ArrayList<>();
    private final MoveIntent[] intents;

    private final Aerodynamics aerodynamics = new Aerodynamics();
    private final CollisionSystem collisions = new CollisionSystem();
    private final BallPhysics ballPhysics = new BallPhysics(aerodynamics, collisions);
    private final PlayerLocomotion locomotion = new PlayerLocomotion();
    private final TrajectoryPredictor predictor = new TrajectoryPredictor(aerodynamics);
    private final List<BallContactListener> contactListeners = new ArrayList<>();
    private final BallContactListener dispatcher = this::dispatch;
    private final BallHandling ballHandling;
    private final BlockMechanic blockMechanic = new BlockMechanic();

    private double time;

    public GameWorld(VolleyballCourt court, Team home, Team away) {
        this(court, home, away, System.nanoTime());
    }

    /** @param seed seed for all random execution errors, for reproducible simulations */
    public GameWorld(VolleyballCourt court, Team home, Team away, long seed) {
        this.court = court;
        this.home = home;
        this.away = away;
        players.addAll(home.squad());
        players.addAll(away.squad());
        int maxId = 0;
        for (VolleyballPlayer player : players) maxId = Math.max(maxId, player.id);
        intents = new MoveIntent[maxId + 1];
        for (int i = 0; i < intents.length; i++) intents[i] = new MoveIntent();
        collisions.buildArena(court.net());
        collisions.setPlayers(players);
        ballHandling = new BallHandling(ball, players, collisions, predictor, court.net(), seed);
        // Ball handling hears about block contacts first, so touch counts are right for other listeners.
        contactListeners.add(ballHandling::onBallContact);
    }

    /** Default match-up of the two built-in teams. */
    public static GameWorld withDefaultTeams(VolleyballCourt court) {
        return new GameWorld(court, TeamFactory.home(0), TeamFactory.away(6));
    }

    public static GameWorld withDefaultTeams(VolleyballCourt court, long seed) {
        return new GameWorld(court, TeamFactory.home(0), TeamFactory.away(6), seed);
    }

    /** A player watches the ball when it is in front or to the side (within ~100° of the net direction). */
    static final float ATTENTION_HALF_ANGLE = 100f * MathUtils.degreesToRadians;
    static final float ATTENTION_HOLD_DISTANCE = 2.5f;

    public void step(float dt) {
        for (VolleyballPlayer player : players) {
            // Players on the bench sit still.
            if (!player.isOnCourt()) continue;
            MoveIntent intent = intents[player.id];
            intent.attentionYaw = attentionYaw(player, intent.faceMovement);
            locomotion.update(player, intent, dt);
        }
        locomotion.resolvePlayerConstraints(players);
        blockMechanic.update(players, ball, ballHandling);
        collisions.updateBodies();
        ballHandling.update(dt, time);
        ballPhysics.step(ball, dt, time, dispatcher);
        ballHandling.afterBallPhysics();
        time += dt;
    }

    /**
     * Yaw towards the ball if the player would naturally watch it, else NaN (face the net).
     * Within the last couple of metres the player holds the current facing: turning at the last
     * moment must not stand in for moving the feet.
     */
    private float attentionYaw(VolleyballPlayer player, boolean humanControlled) {
        float dx = ball.position.x - player.position.x;
        float dz = ball.position.z - player.position.z;
        // A human player squares up to the ball when stopping; only a ball practically overhead
        // freezes the facing (to avoid spinning under it).
        float hold = humanControlled ? 0.5f : ATTENTION_HOLD_DISTANCE;
        if (dx * dx + dz * dz < hold * hold) return player.facingYaw;
        float toBall = MathUtils.atan2(-dz, dx);
        float fromNet = toBall - VolleyballPlayer.facingNetYaw(player.side);
        while (fromNet > MathUtils.PI) fromNet -= MathUtils.PI2;
        while (fromNet < -MathUtils.PI) fromNet += MathUtils.PI2;
        return Math.abs(fromNet) <= ATTENTION_HALF_ANGLE ? toBall : Float.NaN;
    }

    /** Fatigue on (players tire and recover) or off (arcade). */
    /**
     * Which side of the court the benches (and the coaches, and the timeout huddles) are on:
     * +1 the usual +z side, -1 after the teams changed ends. Each team still plays its own half
     * in the simulation; the venue is turned around it (see ArenaScene.setSidesSwapped).
     */
    public float benchSign() {
        return benchSign;
    }

    public void setBenchSign(float sign) {
        benchSign = sign < 0f ? -1f : 1f;
    }

    private float benchSign = 1f;

    public void setFatigue(boolean enabled) {
        locomotion.setFatigue(enabled);
    }

    /** Must be called after the net height changes so collisions match the new net. */
    public void onNetChanged() {
        collisions.buildArena(court.net());
    }

    public void addContactListener(BallContactListener listener) {
        contactListeners.add(listener);
    }

    public void removeContactListener(BallContactListener listener) {
        contactListeners.remove(listener);
    }

    private void dispatch(BallContact contact) {
        for (BallContactListener listener : contactListeners) listener.onBallContact(contact);
    }

    public MoveIntent intent(VolleyballPlayer player) {
        return intents[player.id];
    }

    public BallHandling ballHandling() {
        return ballHandling;
    }

    public VolleyballCourt court() {
        return court;
    }

    public Ball ball() {
        return ball;
    }

    public Team home() {
        return home;
    }

    public Team away() {
        return away;
    }

    public List<VolleyballPlayer> players() {
        return Collections.unmodifiableList(players);
    }

    public VolleyballPlayer player(int id) {
        for (VolleyballPlayer player : players) if (player.id == id) return player;
        return null;
    }

    public Aerodynamics aerodynamics() {
        return aerodynamics;
    }

    /** Forecasts from observable state only; safe to hand to AI. */
    public TrajectoryPredictor predictor() {
        return predictor;
    }

    public CollisionSystem collisions() {
        return collisions;
    }

    public double time() {
        return time;
    }
}
