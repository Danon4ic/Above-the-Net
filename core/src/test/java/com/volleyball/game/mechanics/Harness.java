package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.physics.PhysicsConstants;
import com.volleyball.game.physics.PlayerLocomotion;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Headless world with scripted "perfect players" to exercise the ball-handling mechanics. */
final class Harness {
    static final float DT = 1f / 120f;

    private static long nextSeed = 1;

    final GameWorld world;
    final BallHandling handling;
    final List<BallTouch> touches = new ArrayList<>();
    final List<BallContact> contacts = new ArrayList<>();
    final LaunchSolver solver;

    /** Each harness gets its own deterministic seed, so repeated attempts differ but runs are reproducible. */
    Harness() {
        this(nextSeed++);
    }

    /** Harness with an explicit seed, to compare variants of the same situation. */
    Harness(long seed) {
        world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed);
        handling = world.ballHandling();
        solver = new LaunchSolver(world.predictor());
        handling.addListener(touches::add);
        world.addContactListener(contacts::add);
        // Keep everyone out of the way except the players a test uses.
        for (VolleyballPlayer p : world.players()) p.placeAt(p.side.sign() * 8.5f, -9f + p.id * 0.7f);
    }

    VolleyballPlayer home(int index) {
        return world.home().players().get(index);
    }

    void step() {
        world.step(DT);
    }

    boolean runUntil(BooleanSupplier condition, float maxSeconds) {
        for (int i = 0; i < Math.round(maxSeconds / DT); i++) {
            step();
            if (condition.getAsBoolean()) return true;
        }
        return false;
    }

    /** First floor contact after now (runs the simulation), or null. */
    Vector3 landing(float maxSeconds) {
        int start = contacts.size();
        runUntil(() -> contacts.subList(start, contacts.size()).stream().anyMatch(c -> c.kind() == ColliderKind.FLOOR), maxSeconds);
        return contacts.subList(start, contacts.size()).stream().filter(c -> c.kind() == ColliderKind.FLOOR)
                .map(BallContact::position).findFirst().orElse(null);
    }

    /** Ball machine: lobs the ball from {@code from} so it descends through {@code through} after peaking at {@code apex}. */
    void feedArc(Vector3 from, Vector3 through, float apex, CourtSide touchedBy, int touches) {
        LaunchSolver.Solution s = new LaunchSolver.Solution();
        solver.solveArc(from, through, apex, 0f, s);
        world.ball().launch(from, s.velocity, s.spin, 7);
        handling.registerFeed(touchedBy, touches);
    }

    /**
     * Places {@code p} (standing, facing the net) so that the ideal point of {@code type} lies on
     * the ball's forecast descent, then arms the action shortly before the ball arrives.
     *
     * @param lateralError extra sideways misplacement in metres (0 = perfect footwork)
     */
    BallTouch playWhenArriving(VolleyballPlayer p, ContactType type, ActionKind kind, float lateralError) {
        Vector3 ideal = ContactZone.idealPoint(p, type, null, new Vector3());
        float relX = ideal.x - p.position.x;
        float relZ = ideal.z - p.position.z;
        float idealHeight = ideal.y - p.position.y;
        TrajectoryPredictor.Prediction f = new TrajectoryPredictor.Prediction();
        f.watchHeight = idealHeight;
        world.predictor().predict(world.ball(), 6f, f);
        if (!f.reachesWatchHeight) return null;
        p.position.set(f.descentPoint.x - relX, 0f, f.descentPoint.z - relZ + lateralError);
        p.previousPosition.set(p.position);
        // A scripted reference player: keeps the facing used for the placement.
        world.intent(p).facingYaw = p.facingYaw;
        double armAt = world.time() + f.timeToDescent - 0.25;
        int before = touches.size();
        runUntil(() -> world.time() >= armAt, 6f);
        handling.arm(p, kind, world.time());
        runUntil(() -> touches.size() > before, 1f);
        return touches.size() > before ? touches.get(touches.size() - 1) : null;
    }

    /**
     * Times a standing jump so the hand peaks where the ball arrives, then attacks.
     */
    BallTouch spikeWhenArriving(VolleyballPlayer p, ActionKind kind) {
        float jump = PlayerLocomotion.jumpHeight(p.attributes);
        float handHeight = p.standingReach() - 0.02f + jump;
        Vector3 ideal = ContactZone.idealPoint(p, ContactType.ATTACK, null, new Vector3());
        TrajectoryPredictor.Prediction f = new TrajectoryPredictor.Prediction();
        f.watchHeight = handHeight;
        world.predictor().predict(world.ball(), 6f, f);
        if (!f.reachesWatchHeight) return null;
        p.position.set(f.descentPoint.x - (ideal.x - p.position.x), 0f, f.descentPoint.z - (ideal.z - p.position.z));
        p.previousPosition.set(p.position);
        world.intent(p).facingYaw = p.facingYaw;
        float riseTime = (float) Math.sqrt(2f * jump / PhysicsConstants.GRAVITY);
        double jumpAt = world.time() + f.timeToDescent - riseTime;
        runUntil(() -> world.time() >= jumpAt, 6f);
        world.intent(p).jump = true;
        step();
        world.intent(p).jump = false;
        double armAt = world.time() + riseTime - 0.15;
        int before = touches.size();
        runUntil(() -> world.time() >= armAt, 2f);
        handling.arm(p, kind, world.time());
        runUntil(() -> touches.size() > before, 1f);
        return touches.size() > before ? touches.get(touches.size() - 1) : null;
    }

    /** Machine serve from the away service zone landing near (x, z) on the home side. */
    void machineServe(float targetX, float targetZ, float speed, float topspin) {
        Vector3 from = new Vector3(11f, 2.9f, 0.5f);
        LaunchSolver.Solution s = new LaunchSolver.Solution();
        solver.solveOverNet(from, targetX, targetZ, speed * 0.8f, speed, topspin, 0f, 2.43f + Ball.RADIUS + 0.25f, s);
        world.ball().launch(from, s.velocity, s.spin, 11);
        handling.registerFeed(CourtSide.AWAY, 1);
    }
}
