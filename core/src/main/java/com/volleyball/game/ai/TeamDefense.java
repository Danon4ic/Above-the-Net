package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BlockStyle;
import com.volleyball.game.mechanics.TacticalTargets;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.tactics.DefenseScheme;
import com.volleyball.game.tactics.TeamTactics;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A team's answer to the opponent's attack: reading the set, who blocks and how (single, double
 * or triple block, block style from scouting, falling for decoys), and the back-court formation that
 * defends what gets past the block. The team AI calls {@link #read} once per step while the
 * opponent has the ball and then asks, player by player, what each one does.
 */
final class TeamDefense {
    /**
     * A third blocker (professional teams) joins only a slow ball: at least this long (s) from the
     * set to the hit, measured when the set is read...
     */
    static final float TRIPLE_MIN_TIME = 0.75f;
    /** ...and only from this close (m, along the net) to where the ball will be hit. */
    static final float TRIPLE_REACH = 3.5f;
    /** Distance (m) between neighbouring blockers' block positions in a group block. */
    static final float BLOCK_SPACING = 0.7f;

    private final GameWorld world;
    private final CourtSide side;
    private final TeamTactics tactics;
    private final MatchMemory memory;
    /** The team AI's random source (shared, so a seed still replays the same match). */
    private final Random random;
    private final Map<Integer, TeamAI.Brain> brains;
    /** Where the opponent will attack: their set's descent at hitting height. */
    private final TrajectoryPredictor.Prediction setRead = new TrajectoryPredictor.Prediction();
    private final Vector3 target = new Vector3();
    private final List<VolleyballPlayer> back = new ArrayList<>(3);
    /** This step's block (one to three players) and each one's place along the net (+z, m). */
    private final List<VolleyballPlayer> block = new ArrayList<>(3);
    private final float[] blockOffsets = new float[3];
    /** The extra blockers decided for the current set (decided once, so nobody leaves the block late). */
    private final List<VolleyballPlayer> extraBlockers = new ArrayList<>(2);
    private int extraDecidedFor = Integer.MIN_VALUE;

    // This step's reading of the attack (see read).
    private Difficulty difficulty = Difficulty.INTERMEDIATE;
    private boolean opponentHas;
    private VolleyballPlayer middle;
    private VolleyballPlayer wing;
    private boolean blockReady;
    private HabitRead habit = HabitRead.NONE;
    private boolean formationActive;
    /** Lateral position (team frame) of the expected attack, for the formation. */
    private float hitLateral;
    /** The scheme now played (for the AI view and the off-blocker). */
    private DefenseScheme scheme = DefenseScheme.PERIMETER;

    // Decoys: once per set, a weaker blocker may bite on the opponent's quick.
    /** The blocker who bit on the decoy of the current set (jumps on the next step), or -1. */
    private int fooledByDecoy = -1;
    private int decoyCheckedFor = Integer.MIN_VALUE;

    /** What we know about the hitter who will probably attack (professional scouting). */
    private record HabitRead(float crossBias, float tipShare) {
        static final HabitRead NONE = new HabitRead(0f, 0f);
    }

    TeamDefense(GameWorld world, CourtSide side, TeamTactics tactics, MatchMemory memory, Random random,
                Map<Integer, TeamAI.Brain> brains) {
        this.world = world;
        this.side = side;
        this.tactics = tactics;
        this.memory = memory;
        this.random = random;
        this.brains = brains;
    }

    void newRally() {
        fooledByDecoy = -1;
        decoyCheckedFor = Integer.MIN_VALUE;
        extraBlockers.clear();
        extraDecidedFor = Integer.MIN_VALUE;
    }

    /**
     * Reads the coming attack for this step: whether the opponent has the ball, where they will
     * hit, who blocks, what is known about the hitter and the back-court formation. Who blocks and
     * how well depends on the difficulty (single block / double block, reading the set or
     * reacting late, block style from the hitter's habits).
     */
    void read(double now, double lastTouchAt, RotationManager rotation, List<VolleyballPlayer> court,
              Collection<VolleyballPlayer> humans, Difficulty difficulty) {
        this.difficulty = difficulty;
        BallHandling handling = world.ballHandling();
        opponentHas = handling.lastTouchSide() != null && handling.lastTouchSide() != side;
        int opponentTouches = opponentHas ? handling.teamTouches(side.opposite()) : 0;
        boolean attackComing = false;
        if (opponentHas) {
            setRead.watchHeight = 3.1f;
            world.predictor().predict(world.ball(), 6f, setRead);
            setRead.watchHeight = Float.NaN;
            attackComing = setRead.reachesWatchHeight && CourtDimensions.sideOf(setRead.descentPoint.x) != side;
        }
        // Blockers by role: the middle in the middle, the outside hitter on the left, the setter
        // or opposite on the right.
        middle = frontRole(court, rotation, PlayerRole.MIDDLE_BLOCKER);
        if (middle == null) middle = attackerIn(rotation, 3);
        wing = null;
        if (attackComing && difficulty.meaningfulBlock && opponentTouches >= 2) {
            // The wing blocker on the hitter's side joins the middle.
            float hitAt = lateral(setRead.descentPoint.z);
            wing = hitAt > 0 ? rightSideBlocker(court, rotation) : frontRole(court, rotation, PlayerRole.OUTSIDE_HITTER);
            if (wing != null && Math.abs(hitAt) < 1.2f) wing = null; // middle attack: single block
        }
        if (middle != null && humans.contains(middle)) middle = null;
        if (wing != null && humans.contains(wing)) wing = null;
        formBlock(court, rotation, humans, attackComing && opponentTouches >= 2);
        float readDelay = difficulty.readsPlay ? 0.05f : difficulty.meaningfulBlock ? 0.25f : 0.45f;
        blockReady = now - lastTouchAt >= readDelay;
        habit = habitOfHitter();
        planFormation(rotation, court, attackComing && opponentTouches >= 1, humans);
    }

    /** The opponent touched the ball last (it is theirs, or coming over from them). */
    boolean opponentHas() {
        return opponentHas;
    }

    /** {@code p} is in this step's block. */
    boolean blocks(VolleyballPlayer p) {
        return opponentHas && block.contains(p) && p.role != PlayerRole.LIBERO;
    }

    /** How many players block this step (0 to 3). */
    int blockSize() {
        return opponentHas ? block.size() : 0;
    }

    /** The blockers have read the set (before that they only step up to the net). */
    boolean blockReady() {
        return blockReady;
    }

    /**
     * Sets up a blocker's jump: where along the net (see {@link #formBlock}), the block style from
     * scouting, and a decoy bite.
     */
    void prepareBlock(VolleyballPlayer p) {
        int i = block.indexOf(p);
        brains.get(p.id).motor.setBlockOffset(i < 0 ? 0f : blockOffsets[i]);
        applyBlockStyle(p);
        maybeFallForDecoy(p);
    }

    /**
     * The block for this step and where each blocker stands along the net. A single blocker
     * stands in front of the hit. A double block closes towards the antenna: the wing outside,
     * the middle inside it. Professional teams add the remaining front-row players who can still
     * get there on a slow ball - the far wing, or both wings against a high ball to the middle -
     * making a wall of up to three, {@link #BLOCK_SPACING} apart.
     */
    private void formBlock(List<VolleyballPlayer> court, RotationManager rotation, Collection<VolleyballPlayer> humans,
                           boolean attackOnItsWay) {
        if (!attackOnItsWay) {
            extraBlockers.clear();
            extraDecidedFor = Integer.MIN_VALUE;
        } else if (extraDecidedFor != world.ballHandling().lastToucherId()) {
            extraDecidedFor = world.ballHandling().lastToucherId();
            extraBlockers.clear();
            if (difficulty == Difficulty.PROFESSIONAL && middle != null && setRead.timeToDescent >= TRIPLE_MIN_TIME) {
                addIfInReach(frontRole(court, rotation, PlayerRole.OUTSIDE_HITTER), humans);
                addIfInReach(rightSideBlocker(court, rotation), humans);
            }
        }
        block.clear();
        if (middle != null) block.add(middle);
        if (wing != null) block.add(wing);
        for (VolleyballPlayer p : extraBlockers) {
            if (block.size() < 3 && !block.contains(p) && rotation.isFrontRow(side, p.id)) block.add(p);
        }
        float outward = Math.signum(setRead.descentPoint.z == 0f ? 1f : setRead.descentPoint.z);
        if (block.size() <= 1) {
            // Alone in front of the hit; a wing whose middle is played by a person still closes outside.
            blockOffsets[0] = block.size() == 1 && block.get(0) == wing ? outward * 0.3f : 0f;
            return;
        }
        if (wing != null && block.size() == 2) {
            // Coordinated double block: the wing closes towards the antenna, the middle inside it.
            blockOffsets[0] = -outward * 0.4f;
            blockOffsets[1] = outward * 0.3f;
            return;
        }
        if (wing != null) {
            // Triple block on a side attack: from the antenna inwards, the wing, the middle, the far wing.
            for (int i = 0; i < block.size(); i++) {
                int fromOutside = block.get(i) == wing ? 0 : block.get(i) == middle ? 1 : 2;
                blockOffsets[i] = outward * (0.3f - fromOutside * BLOCK_SPACING);
            }
            return;
        }
        // Against the middle: a wall centred on the hit, in the blockers' order along the net.
        for (int i = 0; i < block.size(); i++) {
            int rank = 0;
            for (VolleyballPlayer o : block) if (o.position.z < block.get(i).position.z) rank++;
            blockOffsets[i] = (rank - (block.size() - 1) / 2f) * BLOCK_SPACING;
        }
    }

    /** Adds a front-row player to the coming block if they can get to the hitting spot in time. */
    private void addIfInReach(VolleyballPlayer p, Collection<VolleyballPlayer> humans) {
        if (p == null || p == middle || p == wing || humans.contains(p) || p.role == PlayerRole.LIBERO || !p.isUpright()) return;
        if (Math.abs(p.position.z - setRead.descentPoint.z) <= TRIPLE_REACH) extraBlockers.add(p);
    }

    /**
     * Where a front-row player who does not block stands: off the net for tips and sharp cross
     * shots; in the formation, behind the block (covering it) or on the side away from the hit.
     */
    Vector3 offBlockerSpot(VolleyballPlayer p) {
        float lateral = lateral(p.position.z);
        float depth = 3.2f;
        if (formationActive && scheme.tipCover) {
            // Covering the block: right behind it, for tips and deflections.
            lateral = hitLateral * 0.5f;
            depth = 2.3f;
        } else if (formationActive && Math.abs(hitLateral) > 1.2f) {
            lateral = -Math.signum(hitLateral) * 1.4f;
            depth = 2.9f;
        }
        return TacticalTargets.teamPoint(side, depth, Math.max(-3.5f, Math.min(3.5f, lateral)), 0f, target);
    }

    /** Defenders shift towards the hitter's favourite direction and come up for a tipper. */
    void readHitter(VolleyballPlayer p) {
        float depth = habit.tipShare() > 0.3f ? -1.2f : 0f;
        float shift = 0f;
        if (Math.abs(habit.crossBias()) > 0.3f) {
            // Cross-court shots go towards the far side from the hitter: shift that way (or to the line).
            float towardsCross = -Math.signum(setRead.descentPoint.z == 0f ? 1f : setRead.descentPoint.z);
            shift = towardsCross * habit.crossBias() * 0.8f;
        }
        brains.get(p.id).motor.setDefenseAdjustment(depth, shift);
    }

    /** The defensive scheme being played (for the AI view). */
    DefenseScheme scheme() {
        return scheme;
    }

    /**
     * Defensive formation against the coming attack (professional), "perimeter" style: the
     * back-row defender on the side of the hit guards the line deep, the middle back plays deep
     * in the middle, the other back defender takes the cross court. Against a hitter known to tip
     * (professional scouting) the line defender comes up behind the block instead ("rotational").
     * Other teams read the hitter each on their own.
     */
    private void planFormation(RotationManager rotation, List<VolleyballPlayer> court, boolean attackComing,
                               Collection<VolleyballPlayer> humans) {
        DefenseScheme commanded = tactics.defenseScheme();
        // Professionals play a formation; any team plays one when the coach commands a scheme.
        formationActive = attackComing && (difficulty == Difficulty.PROFESSIONAL || commanded != null);
        scheme = chooseScheme();
        back.clear();
        for (VolleyballPlayer p : court) {
            if (humans.contains(p)) continue;
            if (!formationActive || block.contains(p) || rotation.isFrontRow(side, p.id) && p.role != PlayerRole.LIBERO) {
                brains.get(p.id).motor.setDefenseSpot(Float.NaN, Float.NaN);
                continue;
            }
            back.add(p);
        }
        if (!formationActive || back.isEmpty()) return;
        hitLateral = lateral(setRead.descentPoint.z);
        // After the serve the back row switches to its specialities: the libero to middle back
        // (zone 6, deep in the middle, from where it reaches both sides and digs the most balls),
        // the setter to right back (zone 1, the shortest way to the net), the other to left back.
        back.sort((a, b) -> Float.compare(defenseOrder(a), defenseOrder(b)));
        boolean middleAttack = Math.abs(hitLateral) < 1.2f;
        float s = hitLateral >= 0f ? 1f : -1f;
        boolean rotational = scheme.upLine;
        for (int i = 0; i < back.size(); i++) {
            VolleyballPlayer p = back.get(i);
            // Position in the lateral order: -1 left, 0 middle, +1 right (two defenders: left and right).
            float slot = back.size() == 1 ? 0f : back.size() == 2 ? (i == 0 ? -1f : 1f) : i - 1f;
            float depth;
            float lateral;
            if (middleAttack) {
                depth = slot == 0f ? 8.2f : 6.6f;
                lateral = slot * 3.2f;
            } else if (slot == s) {
                // Line defender: deep on the hit side, or up behind the block against a tipper.
                depth = rotational ? 3.0f : 6.8f;
                lateral = s * (rotational ? 2.6f : 3.7f);
            } else if (slot == 0f) {
                depth = 8.0f;
                lateral = -s * 0.8f;
            } else {
                depth = 6.0f;
                lateral = -s * 3.3f;
            }
            // The scheme: deeper or closer, and towards the cross court or the line.
            // A scheme the coach called is played to the full; one the team picked from its own
            // scouting is played more cautiously (a wrong read costs less).
            float weight = tactics.defenseScheme() == scheme ? 1f : 0.55f;
            if (!(rotational && slot == s && !middleAttack)) depth = Math.max(2.5f, Math.min(8.6f, depth + scheme.depthShift * weight));
            if (!middleAttack) lateral = Math.max(-4.2f, Math.min(4.2f, lateral - s * scheme.crossShift * weight));
            TacticalTargets.teamPoint(side, depth, lateral, 0f, target);
            TeamAI.Brain brain = brains.get(p.id);
            brain.motor.setDefenseSpot(target.x + brain.positionError.x * 0.5f, target.z + brain.positionError.y * 0.5f);
        }
    }

    /** Left-to-right order of the back-row defenders: the others (zone 5), libero (zone 6), setter (zone 1). */
    private float defenseOrder(VolleyballPlayer p) {
        if (p.role == PlayerRole.LIBERO) return 0f;
        if (p.role == PlayerRole.SETTER) return 100f;
        // With the libero in the middle the others take the left side (zone 5).
        return -100f + lateral(p.position.z);
    }

    /**
     * The defensive scheme: the coach's command if there is one; otherwise professionals read the
     * hitter (tips: rotational; a strong cross or line: the block closes it), others play the
     * standard perimeter.
     */
    private DefenseScheme chooseScheme() {
        DefenseScheme commanded = tactics.defenseScheme();
        if (commanded != null) return commanded;
        if (difficulty != Difficulty.PROFESSIONAL) return DefenseScheme.PERIMETER;
        if (habit.tipShare() > 0.3f) return DefenseScheme.ROTATIONAL;
        if (habit.crossBias() > 0.45f) return DefenseScheme.CLOSE_CROSS;
        if (habit.crossBias() < -0.45f) return DefenseScheme.CLOSE_LINE;
        return DefenseScheme.PERIMETER;
    }

    private HabitRead habitOfHitter() {
        if (!difficulty.readsPlay) return HabitRead.NONE;
        VolleyballPlayer hitter = null;
        float best = Float.MAX_VALUE;
        for (VolleyballPlayer o : opponents()) {
            if (o.id == world.ballHandling().lastToucherId()) continue;
            float dz = o.position.z - setRead.descentPoint.z;
            float dx = o.position.x - setRead.descentPoint.x;
            float d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                hitter = o;
            }
        }
        MatchMemory.Habit h = hitter == null ? null : memory.habit(hitter.id);
        if (h == null || h.total() < 2) return HabitRead.NONE;
        return new HabitRead(h.crossBias(), h.tipShare());
    }

    /** Block style from the hitter's habit: close the side the hitter prefers. */
    private void applyBlockStyle(VolleyballPlayer p) {
        BlockStyle style = BlockStyle.NORMAL;
        DefenseScheme commanded = tactics.defenseScheme();
        if (commanded != null) style = commanded.blockStyle;
        else if (habit.crossBias() > 0.3f) style = BlockStyle.CROSS;
        else if (habit.crossBias() < -0.3f) style = BlockStyle.LINE;
        // Closing the line, but never pushing the hands out past the antenna (that only gives a block-out).
        if (style == BlockStyle.LINE && Math.abs(p.position.z) > CourtDimensions.HALF_WIDTH - 1.2f) style = BlockStyle.NORMAL;
        world.ballHandling().setBlockStyle(p, style);
    }

    /**
     * A decoy fools weaker blockers: if an opposing middle jumps for a quick while the set is
     * still on its way elsewhere, a non-professional blocker may jump with them (once per set).
     */
    private void maybeFallForDecoy(VolleyballPlayer p) {
        if (difficulty.readsPlay || p.isAirborne()) return;
        int setToucher = world.ballHandling().lastToucherId();
        if (decoyCheckedFor == setToucher) {
            // Only the blocker whose read was fooled jumps with the decoy.
            if (fooledByDecoy == p.id) {
                world.intent(p).jump = true;
                fooledByDecoy = -1;
            }
            return;
        }
        for (VolleyballPlayer o : opponents()) {
            if (o.isAirborne() && o.role == PlayerRole.MIDDLE_BLOCKER && Math.abs(o.position.x) < 2.5f
                    && Math.abs(o.position.z - p.position.z) < 1.5f && world.ball().velocity.y > 0f) {
                decoyCheckedFor = setToucher;
                // The wall never bites; the reader rarely does.
                float chance = difficulty == Difficulty.BEGINNER ? 0.6f : 0.35f;
                if (p.has(PlayerTrait.READER)) chance *= 0.4f;
                fooledByDecoy = !p.has(PlayerTrait.WALL) && random.nextFloat() < chance ? p.id : -1;
                return;
            }
        }
    }

    /** The setter or opposite in the front row (the right-side blocker). */
    private VolleyballPlayer rightSideBlocker(List<VolleyballPlayer> court, RotationManager rotation) {
        VolleyballPlayer p = frontRole(court, rotation, PlayerRole.OPPOSITE);
        return p != null ? p : frontRole(court, rotation, PlayerRole.SETTER);
    }

    private VolleyballPlayer frontRole(List<VolleyballPlayer> court, RotationManager rotation, PlayerRole role) {
        return TeamAI.frontRole(court, side, rotation, role, null);
    }

    /** The front-row player in a zone who can attack (not the libero), or null. */
    private VolleyballPlayer attackerIn(RotationManager rotation, int zone) {
        VolleyballPlayer p = world.player(rotation.playerInZone(side, zone));
        return p == null || p.role == PlayerRole.LIBERO ? null : p;
    }

    /** Lateral position in the team's frame (+ = right, facing the net). */
    private float lateral(float z) {
        return -z * side.sign();
    }

    private List<VolleyballPlayer> opponents() {
        return side == CourtSide.HOME ? world.away().onCourt() : world.home().onCourt();
    }
}
