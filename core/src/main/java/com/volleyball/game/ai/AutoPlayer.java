package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ContactZone;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.physics.PhysicsConstants;
import com.volleyball.game.physics.PlayerLocomotion;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;

import java.util.Random;

/**
 * Computer-controlled player behaviours (used by the demo mode, the drills' opponents and the
 * basic team play): plays the ball using only what a human could do — movement
 * intents, jump and action requests at chosen moments — and only observable information (the
 * trajectory forecast, never the hidden flight seed). It does not teleport or force contacts,
 * so it can fail like a human would. This is the seed of the player AI.
 */
public final class AutoPlayer {
    /** Arm the action this long before the ball reaches the contact point. */
    static final float ARM_LEAD = 0.22f;
    /** Time a defender needs to react to an attack before moving. */
    static final float DEFENSE_REACTION = 0.18f;
    /** Height at which an attacker on the other side is expected to hit the set. */
    static final float EXPECTED_HIT_HEIGHT = 3.2f;
    /** Closest distance to the net a player runs to for a pass or dig, and for an attack approach. */
    static final float PLAY_NET_DISTANCE = 0.5f;
    static final float ATTACK_NET_DISTANCE = 0.9f;
    /** A back-row hitter takes off at least this far from the net (behind the attack line). */
    static final float BACK_ROW_TAKEOFF = CourtDimensions.ATTACK_LINE_DISTANCE + 0.15f;
    private boolean backRow;

    /**
     * How well the player reads and reacts (set from the AI difficulty and the player's ratings).
     *
     * @param reaction        delay before moving for a new ball (after someone touched it)
     * @param defenseReaction delay before reacting to an opponent's attack
     * @param readError       typical error (m) in judging where the ball comes down, at the start
     * @param residualError   fraction of that error left when the ball arrives
     */
    public record Skill(float reaction, float defenseReaction, float readError, float residualError) {
        public static final Skill DEFAULT = new Skill(0f, DEFENSE_REACTION, 0f, 0f);
    }

    private final GameWorld world;
    private final TrajectoryPredictor.Prediction forecast = new TrajectoryPredictor.Prediction();
    private final Vector3 ideal = new Vector3();
    private final Vector2 readOffset = new Vector2();
    private final Random random;
    private Skill skill = Skill.DEFAULT;
    private double holdingSince = -1;
    private int seenToucher = Integer.MIN_VALUE;
    private double toucherSeenAt;
    /** Sideways offset (m, along the net) of this player's block from the expected hitting spot. */
    private float blockOffset;
    /** Extra delay before the block jump (negative = earlier), e.g. when fooled by a decoy. */
    private float blockJumpDelay;
    /** Base defense: extra depth (m, + = deeper) and sideways shift (m, +z) of the reading position. */
    private float defenseDepth;
    private float defenseShift;
    /** Defensive spot given by the team's formation (NaN: read the hitter on one's own). */
    /** This approach's take-off timing error (s, + = late) and the possession it was drawn for. */
    private float jumpTiming;
    private int jumpTimingFor = Integer.MIN_VALUE;
    private float defenseSpotX = Float.NaN;
    private float defenseSpotZ = Float.NaN;

    public AutoPlayer(GameWorld world) {
        this(world, 0L);
    }

    public AutoPlayer(GameWorld world, long seed) {
        this.world = world;
        this.random = new Random(seed);
    }

    /** The player is in the back row: attacks take off behind the attack line. */
    public void setBackRow(boolean backRow) {
        this.backRow = backRow;
    }

    public void setSkill(Skill skill) {
        this.skill = skill;
    }

    public Skill skill() {
        return skill;
    }

    /** Group block: stand this far along the net (+z) from the expected hitting spot. */
    public void setBlockOffset(float offset) {
        blockOffset = offset;
    }

    public void setBlockJumpDelay(float delay) {
        blockJumpDelay = delay;
    }

    /** Adjusts the base defense position (scouting, tactics). */
    public void setDefenseAdjustment(float depth, float shift) {
        defenseDepth = depth;
        defenseShift = shift;
    }

    /** The spot of this player in the team's defensive formation (NaN, NaN to read the hitter alone). */
    public void setDefenseSpot(float x, float z) {
        defenseSpotX = x;
        defenseSpotZ = z;
    }

    /** Sets the take-off timing error of the coming attack (drawn once per possession by the team AI). */
    public void setJumpTiming(float seconds, int possession) {
        jumpTiming = seconds;
        jumpTimingFor = possession;
    }

    public int jumpTimingFor() {
        return jumpTimingFor;
    }

    /** Seconds since this player noticed the last touch (the new ball). */
    public double sinceNewBall(double now) {
        return now - toucherSeenAt;
    }

    /** What the player should try to do with the ball. */
    public enum Behavior { PASS, SET, ATTACK, BLOCK, DEFEND, /** A ball off the block: chase it at once, dive if needed. */ SCRAMBLE }

    /** Just walks/runs to a spot (no ball action). */
    public void moveTo(VolleyballPlayer p, float x, float z) {
        MoveIntent intent = world.intent(p);
        intent.clear();
        if (p.isUpright() && !p.isAirborne()) moveTowards(p, intent, x, z);
    }

    /**
     * Fills the player's intent (and arms actions) for the coming step. A player holding or
     * having tossed the serve always serves, whatever the behavior.
     */
    public void act(VolleyballPlayer p, Behavior behavior, double now) {
        MoveIntent intent = world.intent(p);
        intent.clear();
        BallHandling handling = world.ballHandling();
        if (handling.server() == p && handling.servePhase() != BallHandling.ServePhase.NONE) {
            serve(p, handling, now);
            return;
        }
        holdingSince = -1;
        noticeTouch(handling, now);
        // Never play the ball twice in a row (that would be a double contact).
        if (!world.ball().isActive() || handling.isArmed(p) || handling.lastToucherId() == p.id) return;
        switch (behavior) {
            case ATTACK -> attack(p, intent, handling, now);
            case BLOCK -> block(p, intent);
            case DEFEND -> defend(p, intent, handling, now);
            case SCRAMBLE -> scramble(p, intent, handling, now);
            case SET -> play(p, intent, handling, ContactType.OVERHEAD_PASS, ActionKind.SECONDARY, now);
            case PASS -> play(p, intent, handling, ContactType.FOREARM_PASS, ActionKind.PRIMARY, now);
        }
    }

    /** A new ball (someone touched it): start the reaction delay and misjudge it a little. */
    private void noticeTouch(BallHandling handling, double now) {
        if (handling.lastToucherId() == seenToucher) return;
        seenToucher = handling.lastToucherId();
        toucherSeenAt = now;
        float error = skill.readError();
        readOffset.set((float) random.nextGaussian() * error * 0.7f, (float) random.nextGaussian() * error * 0.7f);
    }

    /** The misjudgement shrinks as the ball comes closer (the player sees it better). */
    private float readX(float timeToDescent) {
        return readOffset.x * readWeight(timeToDescent);
    }

    private float readZ(float timeToDescent) {
        return readOffset.y * readWeight(timeToDescent);
    }

    private float readWeight(float timeToDescent) {
        float residual = skill.residualError();
        return residual + (1f - residual) * Math.min(1f, Math.max(0f, timeToDescent / 0.9f));
    }

    private void serve(VolleyballPlayer p, BallHandling handling, double now) {
        ServeType type = handling.serveType(p);
        boolean jump = type != null && type.jump;
        MoveIntent intent = world.intent(p);
        if (handling.servePhase() == BallHandling.ServePhase.HOLDING) {
            if (holdingSince < 0) holdingSince = now;
            // A jump server first walks back for the run-up (the take-off must stay behind the end line).
            if (jump && now - holdingSince < 4.0) {
                float startX = p.side.sign() * (CourtDimensions.HALF_LENGTH + JUMP_SERVE_RUNUP);
                if (Math.abs(p.position.x - startX) > 0.25f) {
                    moveTowards(p, intent, startX, p.position.z);
                    intent.faceMovement = false;
                    intent.facingYaw = VolleyballPlayer.facingNetYaw(p.side);
                    return;
                }
            }
            if (now - holdingSince > 1.0) handling.arm(p, ActionKind.PRIMARY, now); // toss
            return;
        }
        if (jump) {
            jumpServe(p, intent, handling, type, now);
            return;
        }
        ContactZone.idealPoint(p, ContactType.SERVE, handling.serveType(p), ideal);
        if (!handling.isArmed(p) && world.ball().velocity.y < 0f && world.ball().position.y < ideal.y + 0.35f) {
            handling.arm(p, ActionKind.PRIMARY, now);
        }
    }

    /**
     * The jump serve after the toss: run in under the ball, take off behind the end line so the
     * hand peaks where the ball comes down, hit it at the top.
     */
    private void jumpServe(VolleyballPlayer p, MoveIntent intent, BallHandling handling, ServeType type, double now) {
        ContactZone.idealPoint(p, ContactType.SERVE, type, ideal);
        float jumpHeight = PlayerLocomotion.jumpHeight(p.attributes);
        if (p.isAirborne()) {
            forecast.watchHeight = ideal.y;
            world.predictor().predict(world.ball(), 3f, forecast);
            forecast.watchHeight = Float.NaN;
            boolean close = world.ball().position.dst(ideal) < 0.6f;
            if (!handling.isArmed(p) && (close || (forecast.reachesWatchHeight && forecast.timeToDescent < 0.12f))) {
                handling.arm(p, ActionKind.PRIMARY, now);
            }
            return;
        }
        if (!p.isUpright()) return;
        float relX = ideal.x - p.position.x;
        float relZ = ideal.z - p.position.z;
        forecast.watchHeight = ideal.y - p.position.y + jumpHeight;
        world.predictor().predict(world.ball(), 3f, forecast);
        forecast.watchHeight = Float.NaN;
        if (!forecast.reachesWatchHeight) return;
        float riseTime = (float) Math.sqrt(2f * jumpHeight / PhysicsConstants.GRAVITY);
        float carry = PlayerLocomotion.TAKEOFF_CARRY * riseTime;
        float takeoffX = forecast.descentPoint.x - relX - p.velocity.x * carry;
        // Never take off on or inside the end line.
        float limit = CourtDimensions.HALF_LENGTH + 0.3f;
        if (takeoffX * p.side.sign() < limit) takeoffX = p.side.sign() * limit;
        moveTowards(p, intent, takeoffX, forecast.descentPoint.z - relZ);
        intent.sprint = true;
        intent.faceMovement = false;
        intent.facingYaw = VolleyballPlayer.facingNetYaw(p.side);
        boolean behindLine = p.position.x * p.side.sign() >= CourtDimensions.HALF_LENGTH + 0.15f;
        if (behindLine && forecast.timeToDescent <= riseTime + 0.02f) intent.jump = true;
    }

    /** How far behind the end line a jump server starts the run-up (m). */
    static final float JUMP_SERVE_RUNUP = 2.6f;

    /** Moves so the ideal contact point meets the ball's descent, then requests the contact. */
    private void play(VolleyballPlayer p, MoveIntent intent, BallHandling handling, ContactType type, ActionKind kind,
                      double now) {
        ContactZone.idealPoint(p, type, null, ideal);
        forecast.watchHeight = ideal.y - p.position.y;
        world.predictor().predict(world.ball(), 6f, forecast);
        TrajectoryPredictor.dropAtNet(forecast, world.court().net().topHeight(), world.ball().position.x);
        forecast.watchHeight = Float.NaN;
        if (!forecast.reachesWatchHeight || CourtDimensions.sideOf(forecast.descentPoint.x) != p.side) return;
        if (sinceNewBall(now) < skill.reaction()) {
            intent.crouch = true;
            return;
        }
        float t = forecast.timeToDescent;
        moveTowards(p, intent, awayFromNet(p, forecast.descentPoint.x - (ideal.x - p.position.x) + readX(t), PLAY_NET_DISTANCE),
                forecast.descentPoint.z - (ideal.z - p.position.z) + readZ(t));
        if (t < ARM_LEAD || arrivingSoon(p)) handling.arm(p, kind, now);
    }

    /**
     * The ball is about to reach the player's hands or body before it comes down to the contact
     * height (a flat, fast serve or spike at chest height): arm now, or it hits the body.
     */
    private boolean arrivingSoon(VolleyballPlayer p) {
        Vector3 ball = world.ball().position;
        Vector3 v = world.ball().velocity;
        float dx = ball.x - p.position.x;
        float dz = ball.z - p.position.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance > 3f || ball.y > p.position.y + p.height + 0.3f) return false;
        float closing = distance < 1e-4f ? 0f : -(v.x * dx + v.z * dz) / distance;
        // Time until the ball is within about half a metre of the body axis (hands' reach in front).
        return closing > 0.5f && (distance - 0.55f) / closing < ARM_LEAD;
    }

    private void attack(VolleyballPlayer p, MoveIntent intent, BallHandling handling, double now) {
        float jump = PlayerLocomotion.jumpHeight(p.attributes);
        ContactZone.idealPoint(p, ContactType.ATTACK, null, ideal);
        float relX = ideal.x - p.position.x;
        float relZ = ideal.z - p.position.z;
        float handAtPeak = ideal.y - p.position.y + (p.isAirborne() ? p.position.y : jump);
        forecast.watchHeight = p.isAirborne() ? ideal.y : handAtPeak;
        world.predictor().predict(world.ball(), 6f, forecast);
        forecast.watchHeight = Float.NaN;
        if (!forecast.reachesWatchHeight) return;
        if (p.isAirborne()) {
            // A setter attacking the second ball (the dump) uses the secondary action: the primary one is the jump set.
            boolean dump = p.role == com.volleyball.game.entities.PlayerRole.SETTER && handling.teamTouches(p.side) == 1;
            if (forecast.timeToDescent < 0.12f) handling.arm(p, dump ? ActionKind.SECONDARY : ActionKind.PRIMARY, now);
            return;
        }
        float t = forecast.timeToDescent;
        float riseTime = (float) Math.sqrt(2f * jump / PhysicsConstants.GRAVITY);
        // The jump keeps part of the run-up: take off that much short of the hitting spot.
        float carry = PlayerLocomotion.TAKEOFF_CARRY * riseTime;
        moveTowards(p, intent, awayFromNet(p, forecast.descentPoint.x - relX - p.velocity.x * carry + readX(t) * 0.5f,
                        backRow ? BACK_ROW_TAKEOFF : ATTACK_NET_DISTANCE),
                forecast.descentPoint.z - relZ - p.velocity.z * carry + readZ(t) * 0.5f);
        brakeAtNet(p, intent, 1.0f);
        // From the back row: never take off in front of the attack line (stop the run there).
        if (backRow && Math.abs(p.position.x) < BACK_ROW_TAKEOFF && intent.direction.x * -p.side.sign() > 0f) intent.direction.x = 0f;
        boolean legal = !backRow || Math.abs(p.position.x) >= CourtDimensions.ATTACK_LINE_DISTANCE + 0.05f;
        if (legal && forecast.timeToDescent <= riseTime + 0.02f - jumpTiming) intent.jump = true;
    }

    /**
     * Block: while the opponent has the ball, shuffle along the net in front of the spot where
     * their set will be hit (judged from the set's trajectory), and jump so the hands peak just
     * after the hitter's contact.
     */
    private void block(VolleyballPlayer p, MoveIntent intent) {
        if (CourtDimensions.sideOf(world.ball().position.x) == p.side || p.isAirborne()) return;
        forecast.watchHeight = EXPECTED_HIT_HEIGHT;
        world.predictor().predict(world.ball(), 6f, forecast);
        forecast.watchHeight = Float.NaN;
        if (!forecast.reachesWatchHeight || CourtDimensions.sideOf(forecast.descentPoint.x) == p.side) return;
        float spot = Math.max(-CourtDimensions.HALF_WIDTH + 0.45f, Math.min(CourtDimensions.HALF_WIDTH - 0.45f,
                forecast.descentPoint.z + blockOffset));
        moveTowards(p, intent, p.side.sign() * 0.4f, spot);
        brakeAtNet(p, intent, 0.7f);
        float riseTime = (float) Math.sqrt(2f * PlayerLocomotion.jumpHeight(p.attributes) / PhysicsConstants.GRAVITY);
        // Jump straight up: never while still drifting towards the net (that ends in the net).
        float towardsNet = p.velocity.x * -p.side.sign();
        if (forecast.timeToDescent <= riseTime - 0.08f + blockJumpDelay && towardsNet < 0.5f) intent.jump = true;
    }

    /**
     * Defense. Before the hit: read the hitter — stand in the cross-court lane from the expected
     * contact point (judged from the set's flight) in a low stance. After the hit: react after a
     * human-like delay, step to the ball (a one-arm save covers the last metre) and dive only for
     * balls that cannot be reached on foot.
     */
    private void defend(VolleyballPlayer p, MoveIntent intent, BallHandling handling, double now) {
        if (handling.lastTouchSide() == p.side) {
            play(p, intent, handling, ContactType.FOREARM_PASS, ActionKind.PRIMARY, now);
            return;
        }
        if (!p.isUpright()) return;
        if (CourtDimensions.sideOf(world.ball().position.x) != p.side) {
            readHitter(p, intent);
            return;
        }
        if (now - toucherSeenAt < skill.defenseReaction()) {
            intent.crouch = true;
            return;
        }
        chase(p, intent, handling, now, true);
    }

    /**
     * A ball coming off a block (theirs or ours): no hitter to read and no time to lose. The
     * covering players expect it, so the reaction is the short one; they chase it at once, step,
     * reach with one arm or dive.
     */
    private void scramble(VolleyballPlayer p, MoveIntent intent, BallHandling handling, double now) {
        if (!p.isUpright()) return;
        if (now - toucherSeenAt < skill.reaction()) {
            intent.crouch = true;
            return;
        }
        chase(p, intent, handling, now, false);
    }

    /** Runs to the ball (diving for one out of reach on foot) and arms the contact in time. */
    private void chase(VolleyballPlayer p, MoveIntent intent, BallHandling handling, double now, boolean letOutBallsGo) {
        ContactZone.idealPoint(p, ContactType.FOREARM_PASS, null, ideal);
        forecast.watchHeight = ideal.y - p.position.y;
        world.predictor().predict(world.ball(), 6f, forecast);
        TrajectoryPredictor.dropAtNet(forecast, world.court().net().topHeight(), world.ball().position.x);
        forecast.watchHeight = Float.NaN;
        if (!forecast.reachesWatchHeight || CourtDimensions.sideOf(forecast.descentPoint.x) != p.side) return;
        // Let a ball that will clearly land out go.
        if (letOutBallsGo && forecast.lands && !CourtDimensions.isInsideCourt(forecast.landing.x, forecast.landing.z, 0.2f)) return;
        float targetX = forecast.descentPoint.x - (ideal.x - p.position.x) + readX(forecast.timeToDescent);
        float targetZ = forecast.descentPoint.z - (ideal.z - p.position.z) + readZ(forecast.timeToDescent);
        float dx = targetX - p.position.x;
        float dz = targetZ - p.position.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float reachable = PlayerLocomotion.maxRunSpeed(p.attributes) * Math.max(0f, forecast.timeToDescent - 0.1f) * 0.7f;
        if (!p.isAirborne() && distance > reachable + 0.9f && distance > 1.3f && distance < 3.2f) {
            intent.direction.set(dx / distance, dz / distance);
            intent.dive = true;
            handling.arm(p, ActionKind.PRIMARY, now);
            return;
        }
        moveTowards(p, intent, awayFromNet(p, targetX, PLAY_NET_DISTANCE), targetZ);
        intent.crouch = distance < 0.8f;
        if (forecast.timeToDescent < ARM_LEAD || arrivingSoon(p)) handling.arm(p, ActionKind.PRIMARY, now);
    }

    /**
     * Base defense against a hitter: on the line from the expected contact point to the far
     * corner of the own court (the cross-court lane), about 6.3 m from the net, in a low stance.
     */
    private void readHitter(VolleyballPlayer p, MoveIntent intent) {
        forecast.watchHeight = EXPECTED_HIT_HEIGHT;
        world.predictor().predict(world.ball(), 6f, forecast);
        forecast.watchHeight = Float.NaN;
        intent.crouch = true;
        if (!Float.isNaN(defenseSpotX)) {
            moveTowards(p, intent, defenseSpotX, defenseSpotZ);
            return;
        }
        if (!forecast.reachesWatchHeight || CourtDimensions.sideOf(forecast.descentPoint.x) == p.side) return;
        float hitX = forecast.descentPoint.x;
        float hitZ = forecast.descentPoint.z;
        float cornerX = p.side.sign() * CourtDimensions.HALF_LENGTH;
        float cornerZ = -Math.signum(hitZ == 0f ? 1f : hitZ) * CourtDimensions.HALF_WIDTH;
        float baseX = p.side.sign() * Math.max(4f, Math.min(8.3f, 6.3f + defenseDepth));
        float t = (baseX - hitX) / (cornerX - hitX);
        float baseZ = hitZ + (cornerZ - hitZ) * t;
        // Defenders stay on their side of the court: blend with the own base position.
        baseZ = (baseZ + p.position.z) / 2f + defenseShift;
        moveTowards(p, intent, baseX, baseZ);
    }

    /** Keeps a movement target at least {@code min} metres from the net on the player's own side. */
    private static float awayFromNet(VolleyballPlayer p, float x, float min) {
        float sign = p.side.sign();
        return x * sign < min ? sign * min : x;
    }

    /**
     * Near the net a player stops running towards it: the run-up speed is kept in a jump, and
     * jumping while running at the net carries the body into it (a net fault).
     */
    private static void brakeAtNet(VolleyballPlayer p, MoveIntent intent, float zone) {
        float towardsNet = -p.side.sign();
        if (Math.abs(p.position.x) < zone && intent.direction.x * towardsNet > 0f) intent.direction.x = 0f;
    }

    private static void moveTowards(VolleyballPlayer p, MoveIntent intent, float x, float z) {
        float dx = x - p.position.x;
        float dz = z - p.position.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.04f) return;
        // Full speed when far, easing in over the last half metre to arrive balanced.
        float scale = Math.min(1f, distance / 0.5f) / distance;
        intent.direction.set(dx * scale, dz * scale);
        intent.sprint = distance > 2.5f;
    }
}
