package com.volleyball.game.match;

import com.badlogic.gdx.math.MathUtils;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.CourtZones;

import java.util.List;
import java.util.Random;

/**
 * The two head coaches on the sideline, in front of their benches. They are figures only (not in
 * the world: no collisions, no part in the rules):
 * <ul>
 *   <li>between rallies and in drills they pace their coaching zone;</li>
 *   <li>in a rally they step along the line with the ball and watch it;</li>
 *   <li>after a point the winner's coach celebrates and the other puts their hands to their head;</li>
 *   <li>in a timeout both walk into the middle of their team's huddle.</li>
 * </ul>
 */
public final class SidelineCoaches {
    /** Ids of the coach figures (clear of every player id). */
    public static final int FIRST_ID = 200;
    private static final float WALK_SPEED = 1.1f;
    private static final float HURRY_SPEED = 2.3f;
    private static final float TURN_RATE = 5f;
    /** The coaching zone: a strip between the court's free zone and the bench. */
    static final float ZONE_Z = CourtZones.BENCH_Z - 2.3f;
    static final float ZONE_NEAR = 1.8f;
    static final float ZONE_FAR = 8.5f;

    private final VolleyballPlayer[] coaches = new VolleyballPlayer[2];
    private final float[] targetX = new float[2];
    private final float[] targetZ = new float[2];
    private final float[] idleTimer = new float[2];
    private final float[] reactTimer = new float[2];
    private final Random random;
    private final int[] lastPoints = {-1, -1};
    private float benchSign = 1f;

    public SidelineCoaches(Team home, Team away, long seed) {
        random = new Random(seed);
        Team[] teams = {home, away};
        for (int i = 0; i < 2; i++) {
            CourtSide side = teams[i].side;
            VolleyballPlayer coach = new VolleyballPlayer(FIRST_ID + i, teams[i].coach(), 0, PlayerRole.SETTER, side,
                    1.80f + random.nextFloat() * 0.08f, new PlayerAttributes());
            float x = side.sign() * (ZONE_NEAR + 2.5f + random.nextFloat() * 2f);
            coach.placeAt(x, ZONE_Z);
            coach.facingYaw = yawTo(0f, -1f);
            coach.previousFacingYaw = coach.facingYaw;
            coaches[i] = coach;
            targetX[i] = x;
            targetZ[i] = ZONE_Z;
            idleTimer[i] = 1f + random.nextFloat() * 3f;
        }
    }

    /** The coaches, home first (to draw them with the players). */
    public List<VolleyballPlayer> coaches() {
        return List.of(coaches);
    }

    /**
     * @param match the match (null in a drill: the coaches just pace)
     */
    public void update(float dt, MatchController match, Ball ball) {
        update(dt, match, ball, 1f);
    }

    /** @param benchSign the side the benches are on (see {@code GameWorld.benchSign}) */
    public void update(float dt, MatchController match, Ball ball, float benchSign) {
        if (benchSign != this.benchSign) {
            // The teams changed ends: the coaches are at their new bench when play resumes.
            this.benchSign = benchSign;
            for (int i = 0; i < 2; i++) {
                coaches[i].placeAt(coaches[i].position.x, benchSign * ZONE_Z);
                targetZ[i] = benchSign * ZONE_Z;
            }
        }
        if (match != null) watchScore(match.rules().score());
        MatchController.Phase phase = match == null ? null : match.phase();
        for (int i = 0; i < 2; i++) {
            VolleyballPlayer c = coaches[i];
            float sign = c.side.sign();
            c.previousPosition.set(c.position);
            c.previousFacingYaw = c.facingYaw;
            c.tickActionPose(dt);
            reactTimer[i] = Math.max(0f, reactTimer[i] - dt);
            float speed = WALK_SPEED;
            float faceYaw;
            boolean faceMotion = false;
            if (phase == MatchController.Phase.TIMEOUT) {
                // In the middle of the huddle, talking to the players.
                targetX[i] = sign * 5.5f;
                targetZ[i] = benchSign * (CourtDimensions.HALF_WIDTH - 0.4f);
                speed = HURRY_SPEED;
                faceYaw = yawTo(-sign, 0f);
                faceMotion = true;
            } else if (reactTimer[i] > 0f) {
                targetX[i] = c.position.x;
                targetZ[i] = c.position.z;
                faceYaw = yawTo(0f, -benchSign);
            } else if (phase == MatchController.Phase.RALLY || (match == null && ball.isActive())) {
                // Along the line with the ball (on the own half), watching it.
                float follow = Math.abs(ball.position.x) < 0.5f ? sign * 4f : ball.position.x;
                if (follow * sign < 0f) follow = sign * ZONE_NEAR;
                float wanted = sign * MathUtils.clamp(Math.abs(follow) * 0.8f, ZONE_NEAR, ZONE_FAR);
                // A few real steps now and then, not a constant creep after every move of the ball.
                if (Math.abs(wanted - targetX[i]) > 1.2f || Math.abs(targetZ[i] - benchSign * ZONE_Z) > 0.1f) targetX[i] = wanted;
                targetZ[i] = benchSign * ZONE_Z;
                faceYaw = yawTo(ball.position.x - c.position.x, ball.position.z - c.position.z);
            } else {
                // Pacing the coaching zone.
                targetZ[i] = benchSign * ZONE_Z;
                idleTimer[i] -= dt;
                if (idleTimer[i] <= 0f || Math.abs(targetZ[i] - c.position.z) > 3f) {
                    targetX[i] = sign * (ZONE_NEAR + 1f + random.nextFloat() * (ZONE_FAR - ZONE_NEAR - 2f));
                    idleTimer[i] = 2.5f + random.nextFloat() * 4f;
                }
                faceYaw = yawTo(-sign * 0.3f, -benchSign);
                faceMotion = true;
            }
            move(c, targetX[i], targetZ[i], speed, dt);
            if (faceMotion && c.groundSpeed() > 0.3f) faceYaw = yawTo(c.velocity.x, c.velocity.z);
            c.facingYaw = turn(c.facingYaw, faceYaw, TURN_RATE * dt);
        }
    }

    /** A point: the winner's coach celebrates, the other one is not pleased. */
    private void watchScore(MatchScore score) {
        int home = score.points(CourtSide.HOME);
        int away = score.points(CourtSide.AWAY);
        boolean homeWon = lastPoints[0] >= 0 && home == lastPoints[0] + 1 && away == lastPoints[1];
        boolean awayWon = lastPoints[1] >= 0 && away == lastPoints[1] + 1 && home == lastPoints[0];
        lastPoints[0] = home;
        lastPoints[1] = away;
        if (!homeWon && !awayWon) return;
        for (int i = 0; i < 2; i++) {
            boolean won = (i == 0) == homeWon;
            // Not after every point: a coach reacts to about half of them.
            if (random.nextFloat() > (won ? 0.7f : 0.45f)) continue;
            coaches[i].setActionPose(won ? ActionPose.CELEBRATE : ActionPose.OVERHEAD, won ? 1.4f : 1.0f);
            reactTimer[i] = won ? 1.4f : 1.0f;
        }
    }

    private static void move(VolleyballPlayer c, float tx, float tz, float maxSpeed, float dt) {
        float dx = tx - c.position.x;
        float dz = tz - c.position.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.05f) {
            c.velocity.setZero();
            return;
        }
        // Walk at an even pace and slow down only over the last half metre (no endless creeping).
        float speed = Math.min(maxSpeed, Math.max(0.6f, dist * 2.5f));
        float step = Math.min(dist, speed * dt);
        c.velocity.set(dx / dist * speed, 0f, dz / dist * speed);
        c.position.add(dx / dist * step, 0f, dz / dist * step);
        c.addStride(step);
    }

    /** Yaw that looks along (dx, dz): the facing direction is (cos yaw, -sin yaw). */
    static float yawTo(float dx, float dz) {
        return MathUtils.atan2(-dz, dx);
    }

    private static float turn(float from, float to, float maxStep) {
        float delta = to - from;
        while (delta > MathUtils.PI) delta -= MathUtils.PI2;
        while (delta < -MathUtils.PI) delta += MathUtils.PI2;
        return from + MathUtils.clamp(delta, -maxStep, maxStep);
    }
}
