package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.mechanics.TacticalTargets;
import com.volleyball.game.physics.PlayerLocomotion;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;

import java.util.EnumMap;
import java.util.List;

/**
 * Reads the situation the way a good player would and recommends a set or a shot: which hitters
 * are available in which lane, how good the pass is, where the opponent's blockers stand, and
 * whether the short zone behind the block is empty. Used for the person's "Auto" choice and to
 * mark the recommended option in the selector; the computer teams make their own decisions
 * (see {@link TeamAI}) but follow the same reasoning.
 */
public final class ShotAdvisor {
    /**
     * An opponent this close to the net (m) and this far along it from the hitter (m) is in the
     * hitter's block: the shot choice of the team AI and the advice read the same block.
     */
    static final float BLOCK_NET_DISTANCE = 1.5f;
    static final float BLOCK_LATERAL = 1.8f;

    /** How good the pass to the setter is, judged from where it comes down. */
    public enum PassQuality { GOOD, OK, POOR }

    private final GameWorld world;
    private final TrajectoryPredictor.Prediction prediction = new TrajectoryPredictor.Prediction();
    private final Vector3 tmp = new Vector3();
    private final EnumMap<SetType.Lane, VolleyballPlayer> hitters = new EnumMap<>(SetType.Lane.class);

    public ShotAdvisor(GameWorld world) {
        this.world = world;
    }

    // ------------------------------------------------------------------ sets

    /**
     * The hitter each set would go to (null: nobody in that lane). With a rotation (a match) the
     * front / back row is the official one; in drills it is judged from where players stand.
     */
    public EnumMap<SetType.Lane, VolleyballPlayer> hitters(VolleyballPlayer setter, RotationManager rotation) {
        hitters.clear();
        List<VolleyballPlayer> team = setter.side == CourtSide.HOME ? world.home().onCourt() : world.away().onCourt();
        for (VolleyballPlayer p : team) {
            if (p == setter || p.role == PlayerRole.LIBERO || !p.isUpright()) continue;
            boolean front = rotation != null ? rotation.isFrontRow(p.side, p.id) : Math.abs(p.position.x) < 4.5f;
            SetType.Lane lane;
            if (front) {
                lane = switch (p.role) {
                    case OUTSIDE_HITTER -> SetType.Lane.OUTSIDE;
                    case MIDDLE_BLOCKER -> SetType.Lane.MIDDLE;
                    default -> SetType.Lane.RIGHT; // opposite, or the second setter hitting on the right
                };
            } else {
                if (p.role == PlayerRole.MIDDLE_BLOCKER) continue;
                lane = p.role == PlayerRole.OUTSIDE_HITTER ? SetType.Lane.BACK_MIDDLE : SetType.Lane.BACK_RIGHT;
            }
            hitters.putIfAbsent(lane, p);
        }
        return hitters;
    }

    /** The pass now coming to the setter: how close to the setting spot it comes down. */
    public PassQuality passQuality(CourtSide side) {
        Ball ball = world.ball();
        if (!ball.isActive()) return PassQuality.OK;
        prediction.watchHeight = TacticalTargets.SETTER_HEIGHT;
        world.predictor().predict(ball, 6f, prediction);
        prediction.watchHeight = Float.NaN;
        if (!prediction.reachesWatchHeight) return PassQuality.POOR;
        TacticalTargets.setterTarget(side, tmp);
        float miss = (float) Math.hypot(prediction.descentPoint.x - tmp.x, prediction.descentPoint.z - tmp.z);
        return miss < 1.2f ? PassQuality.GOOD : miss < 2.5f ? PassQuality.OK : PassQuality.POOR;
    }

    /**
     * The best set now: only to lanes with a hitter; fast sets only off a good pass; away from
     * the opponent's blockers (counted near each hitting spot); the better hitter preferred.
     */
    public SetType recommendSet(VolleyballPlayer setter, RotationManager rotation) {
        EnumMap<SetType.Lane, VolleyballPlayer> available = hitters(setter, rotation);
        PassQuality pass = passQuality(setter.side);
        SetType best = null;
        float bestScore = -Float.MAX_VALUE;
        for (SetType type : SetType.values()) {
            VolleyballPlayer hitter = available.get(type.lane);
            if (hitter == null) continue;
            float score = 0.4f + hitter.attributes.attackPower / 250f;
            if (type.fast() && pass != PassQuality.GOOD) score -= 0.8f;
            if (pass == PassQuality.POOR && type.apex < 5.5f) score -= 0.5f;
            if (pass == PassQuality.POOR && (type == SetType.HIGH_OUTSIDE || type == SetType.BACK_SET)) score += 0.3f;
            // Fewer blockers in front of the hitting spot is better; a quick beats a slow block.
            int blockers = blockersNear(setter.side, type);
            score -= blockers * (type.fast() ? 0.12f : 0.25f);
            if (type.lane == SetType.Lane.BACK_MIDDLE || type.lane == SetType.Lane.BACK_RIGHT) score -= 0.15f;
            if (score > bestScore) {
                bestScore = score;
                best = type;
            }
        }
        return best != null ? best : SetType.HIGH_OUTSIDE;
    }

    /** Opposing front-row players (near the net) within 2 m of a set's hitting spot. */
    private int blockersNear(CourtSide side, SetType type) {
        type.target(side, tmp);
        int n = 0;
        for (VolleyballPlayer o : opponents(side)) {
            if (Math.abs(o.position.x) < 2.5f && Math.abs(o.position.z - tmp.z) < 2f) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ shots

    /**
     * The best shot for a hitter about to attack:
     * <ul>
     *   <li>a set that will be hit low (the hand will be near the net top): tip or roll shot;</li>
     *   <li>no block: power;</li>
     *   <li>a single block: power past it, or the cut to the angle if the block closes the line;</li>
     *   <li>a double block: the tool when the hitter is near the antenna, the tip when the short zone
     *       behind the block is empty, otherwise the cut or the roll shot.</li>
     * </ul>
     * The setter's second ball is a dump (tip).
     */
    public AttackType recommendAttack(VolleyballPlayer hitter, boolean secondBall) {
        if (secondBall) return AttackType.TIP;
        Ball ball = world.ball();
        float netTop = world.court().net().topHeight();
        // The hand's height at the contact: the hitter's reach at the top of the jump, unless the
        // ball is already lower than that (a set that dropped too far, a late approach).
        float peak = hitter.standingReach() + PlayerLocomotion.jumpHeight(hitter.attributes) * 0.95f;
        float contactHeight = ball.velocity.y < 0f && ball.position.y < peak ? ball.position.y : peak;
        if (contactHeight < netTop + 0.35f) return AttackType.TIP;
        if (contactHeight < netTop + 0.6f) return AttackType.ROLL_SHOT;
        int blockers = 0;
        float blockZ = 0f;
        for (VolleyballPlayer o : opponents(hitter.side)) {
            if (Math.abs(o.position.x) < BLOCK_NET_DISTANCE && Math.abs(o.position.z - hitter.position.z) < BLOCK_LATERAL) {
                blockers++;
                blockZ += o.position.z;
            }
        }
        if (blockers == 0) return AttackType.POWER;
        blockZ /= blockers;
        boolean nearAntenna = Math.abs(hitter.position.z) > CourtDimensions.HALF_WIDTH - 1.4f;
        // Does the block stand between the hitter and the line (closer to the sideline than the hitter)?
        boolean lineClosed = Math.abs(blockZ) > Math.abs(hitter.position.z) - 0.2f;
        if (blockers == 1) return lineClosed ? AttackType.CUT : AttackType.POWER;
        if (shortZoneOpen(hitter.side.opposite(), hitter.position.z)) return AttackType.TIP;
        // A closed double block at the antenna: off the outside hand.
        if (nearAntenna && lineClosed) return AttackType.TOOL;
        return lineClosed ? AttackType.CUT : AttackType.ROLL_SHOT;
    }

    /** True if no defender (away from the net) stands within 2 m of the short zone behind the block. */
    public boolean shortZoneOpen(CourtSide defending, float z) {
        float x = defending.sign() * 2.2f;
        for (VolleyballPlayer o : defending == CourtSide.HOME ? world.home().onCourt() : world.away().onCourt()) {
            if (Math.abs(o.position.x) < 1.4f) continue;
            float dx = o.position.x - x;
            float dz = o.position.z - z;
            if (dx * dx + dz * dz < 4f) return false;
        }
        return true;
    }

    private List<VolleyballPlayer> opponents(CourtSide side) {
        return side == CourtSide.HOME ? world.away().onCourt() : world.home().onCourt();
    }
}
