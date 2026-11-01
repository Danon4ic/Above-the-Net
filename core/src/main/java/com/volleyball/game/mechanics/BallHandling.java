package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.SwingStyle;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.physics.CollisionSystem;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.Net;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns players' action requests into ball contacts.
 *
 * <p>How a contact happens: a player <em>arms</em> an action (button press or AI decision). While
 * armed — for a short window — the system watches the ball relative to the ideal contact point
 * of each suitable technique (see {@link ContactZone}). The contact is made when the ball is
 * closest to that point (or when the window is about to close). So pressing too early wastes
 * the window, pressing late or standing in the wrong place gives an off-centre contact, and a
 * ball that never comes within reach is simply missed. Nothing is automatic or perfect.
 *
 * <p>The technique is chosen at contact time from the situation (airborne → attack, high ball →
 * overhead, low ball → forearm). Its purpose follows the team's touch count: first touch passes
 * to the setter, second sets the selected attack, third sends the ball over.
 *
 * <p>It also runs the serve sequence (hold → toss → hit) and counts team touches. Fault calls
 * (four touches, double contact, foot faults...) belong to the rules module, which receives
 * every {@link BallTouch}.
 */
public final class BallHandling {
    public static final float GROUND_WINDOW = 0.40f;
    /** Extra distance from the net of a reception pass's target (always, and for a poor contact). */
    static final float PASS_NET_MARGIN = 0.4f;
    static final float PASS_NET_MARGIN_POOR = 1.8f;
    public static final float AIR_WINDOW = 0.30f;
    /** A request armed with a dive stays alive through the dive and the first moment on the floor. */
    public static final float DIVE_WINDOW = 0.7f;
    /**
     * Players controlled by a person reach this much farther (the ball's "hitbox" is bigger) and
     * their press stays armed {@link #ASSIST_WINDOW} times longer: judging depth on a screen is
     * much harder than on a court. Computer players do not get it.
     */
    public static final float ASSIST_REACH = 1.35f;
    public static final float ASSIST_WINDOW = 1.25f;
    private final java.util.Set<Integer> assisted = new java.util.HashSet<>();
    /** Two hands touching in one block impact are reported once. */
    static final double BLOCK_TOUCH_COOLDOWN = 0.15;
    /** Body collisions of the contacting player are ignored briefly so the ball leaves the hands cleanly. */
    static final float BODY_IGNORE_AFTER_CONTACT = 0.2f;
    /** Torso radius plus the ball's radius and a little margin. */
    static final float BODY_GUARD = 0.32f;
    static final float SERVE_TIMEOUT = 4f;
    /** A tossed serve can only be hit on the way down or near the top of the toss. */
    static final float SERVE_MAX_RISING_SPEED = 1.5f;

    public enum ServePhase { NONE, HOLDING, TOSSED }

    /** Per-player request, aim and preferred variants. */
    private static final class PlayerState {
        boolean armed;
        ActionKind kind;
        double armedAt;
        /** Previous normalized offset per technique, to detect the moment the ball starts moving away. */
        final float[] previousOffsets = new float[ContactType.values().length];
        boolean hasAim;
        final Vector3 aim = new Vector3();
        ServeType serveType = ServeType.FLOAT;
        SetType setType = SetType.HIGH_OUTSIDE;
        AttackType attackType = AttackType.POWER;
        BlockStyle blockStyle = BlockStyle.NORMAL;
        /** The person left the set / shot to the game ("Auto": chosen from the situation at the contact). */
        boolean autoSet = true;
        boolean autoAttack = true;
        double lastBlockTouch = Double.NEGATIVE_INFINITY;

        void resetOffsets() {
            java.util.Arrays.fill(previousOffsets, Float.MAX_VALUE);
        }
    }

    private final Ball ball;
    private final List<VolleyballPlayer> players;
    private final CollisionSystem collisions;
    private final TrajectoryPredictor predictor;
    private final Net net;
    private final RandomXS128 random;
    private final ServeMechanic serveMechanic;
    private final ReceiveMechanic receiveMechanic;
    private final SetMechanic setMechanic;
    private final AttackMechanic attackMechanic;
    private final PlayerState[] states;
    private final List<BallTouchListener> listeners = new ArrayList<>();

    private final ContactContext context = new ContactContext();
    /** Set point or match point: the pressure traits (clutch, nervous) apply. */
    private boolean pressure;
    private final LaunchSolver.Solution solution = new LaunchSolver.Solution();
    private final TrajectoryPredictor.Prediction prediction = new TrajectoryPredictor.Prediction();
    private final Vector3 tmp = new Vector3();

    private ServePhase servePhase = ServePhase.NONE;
    private VolleyballPlayer server;
    private VolleyballPlayer nextServer;
    private ServeType serveType;
    private double tossTime;

    /** Side that touched the ball last and how many consecutive touches it has made. */
    private CourtSide touchSide;
    private int touchCount;
    /** Id of the player who touched the ball last, or -1. */
    private int lastToucherId = -1;
    private double lastTouchTime = Double.NEGATIVE_INFINITY;

    public BallHandling(Ball ball, List<VolleyballPlayer> players, CollisionSystem collisions,
                        TrajectoryPredictor predictor, Net net, long seed) {
        this.ball = ball;
        this.players = players;
        this.collisions = collisions;
        this.predictor = predictor;
        this.net = net;
        this.random = new RandomXS128(seed);
        LaunchSolver solver = new LaunchSolver(predictor);
        serveMechanic = new ServeMechanic(solver);
        receiveMechanic = new ReceiveMechanic(solver);
        setMechanic = new SetMechanic(solver);
        attackMechanic = new AttackMechanic(solver);
        int maxId = 0;
        for (VolleyballPlayer p : players) maxId = Math.max(maxId, p.id);
        states = new PlayerState[maxId + 1];
        for (int i = 0; i < states.length; i++) states[i] = new PlayerState();
    }

    public void removeListener(BallTouchListener listener) {
        listeners.remove(listener);
    }

    public void addListener(BallTouchListener listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------------ requests and selections

    /**
     * Requests a ball action. A serving player holding the ball tosses immediately; otherwise
     * the request stays armed for a short window.
     */
    public void arm(VolleyballPlayer p, ActionKind kind, double now) {
        if (!p.isOnCourt()) return;
        if (servePhase == ServePhase.HOLDING && p == server) {
            toss(now);
            return;
        }
        PlayerState s = states[p.id];
        s.armed = true;
        s.kind = kind;
        s.armedAt = now;
        s.resetOffsets();
    }

    public boolean isArmed(VolleyballPlayer p) {
        return states[p.id].armed;
    }

    public void cancel(VolleyballPlayer p) {
        states[p.id].armed = false;
    }

    /** Floor point the player aims serves, attacks and free balls at. */
    public void setAim(VolleyballPlayer p, float x, float z) {
        PlayerState s = states[p.id];
        s.hasAim = true;
        s.aim.set(x, 0f, z);
    }

    public void clearAim(VolleyballPlayer p) {
        states[p.id].hasAim = false;
    }

    public ServeType serveType(VolleyballPlayer p) {
        return states[p.id].serveType;
    }

    public void setServeType(VolleyballPlayer p, ServeType type) {
        states[p.id].serveType = type;
        if (p == server && servePhase == ServePhase.HOLDING) serveType = type;
    }

    public SetType setType(VolleyballPlayer p) {
        return states[p.id].setType;
    }

    public void setSetType(VolleyballPlayer p, SetType type) {
        states[p.id].setType = type;
    }

    public AttackType attackType(VolleyballPlayer p) {
        return states[p.id].attackType;
    }

    public void setAttackType(VolleyballPlayer p, AttackType type) {
        states[p.id].attackType = type;
    }

    public BlockStyle blockStyle(VolleyballPlayer p) {
        return states[p.id].blockStyle;
    }

    /** The players a person controls this step (they get {@link #ASSIST_REACH}); null entries are ignored. */
    public void setAssisted(VolleyballPlayer... players) {
        assisted.clear();
        for (VolleyballPlayer p : players) if (p != null) assisted.add(p.id);
    }

    private float reachScale(VolleyballPlayer p) {
        return assisted.contains(p.id) ? ASSIST_REACH : 1f;
    }

    /** Set point or match point (set by the match): traits that react to pressure apply. */
    public void setPressure(boolean pressure) {
        this.pressure = pressure;
    }

    public boolean pressure() {
        return pressure;
    }

    public void setBlockStyle(VolleyballPlayer p, BlockStyle style) {
        states[p.id].blockStyle = style;
    }

    /**
     * Which variant Q/E should change for this player right now: serve type while serving,
     * block style at the net while the opponent has the ball, otherwise set type (setters) or
     * attack shot.
     */
    public ContactPurpose variantContext(VolleyballPlayer p) {
        if (p == server && servePhase != ServePhase.NONE) return ContactPurpose.SERVE;
        // The next server can choose the serve before getting the ball (between rallies, in the serve drill).
        if (p == nextServer && servePhase == ServePhase.NONE && !ball.isActive()) return ContactPurpose.SERVE;
        if (Math.abs(p.position.x) < 1.5f && ball.isActive() && CourtDimensions.sideOf(ball.position.x) != p.side) {
            return ContactPurpose.BLOCK;
        }
        return p.role == PlayerRole.SETTER ? ContactPurpose.SET : ContactPurpose.ATTACK;
    }

    /**
     * Steps through the variants of the current context (Q / E). For sets and shots the list
     * starts with "Auto" (index 0), then the types in order.
     */
    public void cycleVariant(VolleyballPlayer p, int direction) {
        ContactPurpose context = variantContext(p);
        int count = variantCount(context);
        int index = selectedVariant(p, context);
        int first = hasAuto(context) ? 0 : 1;
        int span = count + 1 - first;
        selectVariant(p, first + Math.floorMod(index - first + direction, span));
    }

    /** Number of real variants (without "Auto") in a context. */
    public static int variantCount(ContactPurpose context) {
        return switch (context) {
            case SERVE -> ServeType.values().length;
            case BLOCK -> BlockStyle.values().length;
            case SET -> SetType.values().length;
            default -> AttackType.values().length;
        };
    }

    /** Sets and shots can be left to the game ("Auto"); serves and block styles are always chosen. */
    public static boolean hasAuto(ContactPurpose context) {
        return context != ContactPurpose.SERVE && context != ContactPurpose.BLOCK;
    }

    /** The selected variant of the context: 0 = Auto, otherwise the type's ordinal + 1. */
    public int selectedVariant(VolleyballPlayer p, ContactPurpose context) {
        PlayerState s = states[p.id];
        return switch (context) {
            case SERVE -> s.serveType.ordinal() + 1;
            case BLOCK -> s.blockStyle.ordinal() + 1;
            case SET -> s.autoSet ? 0 : s.setType.ordinal() + 1;
            default -> s.autoAttack ? 0 : s.attackType.ordinal() + 1;
        };
    }

    /**
     * Direct choice (number keys) in the current context: 0 = Auto (sets and shots), 1..n = the
     * n-th type. Out-of-range numbers are ignored.
     */
    public void selectVariant(VolleyballPlayer p, int index) {
        ContactPurpose context = variantContext(p);
        if (index < (hasAuto(context) ? 0 : 1) || index > variantCount(context)) return;
        PlayerState s = states[p.id];
        switch (context) {
            case SERVE -> setServeType(p, ServeType.values()[index - 1]);
            case BLOCK -> s.blockStyle = BlockStyle.values()[index - 1];
            case SET -> {
                s.autoSet = index == 0;
                if (index > 0) s.setType = SetType.values()[index - 1];
            }
            default -> {
                s.autoAttack = index == 0;
                if (index > 0) s.attackType = AttackType.values()[index - 1];
            }
        }
    }

    public boolean isAutoSet(VolleyballPlayer p) {
        return states[p.id].autoSet;
    }

    public boolean isAutoAttack(VolleyballPlayer p) {
        return states[p.id].autoAttack;
    }

    /** The player who will serve next (may pick the serve type in advance), or null. */
    public void setNextServer(VolleyballPlayer p) {
        nextServer = p;
    }

    public VolleyballPlayer nextServer() {
        return nextServer;
    }

    // ------------------------------------------------------------------ serve sequence

    /** Gives the ball to the server (held in hand, not yet in play). */
    public void prepareServe(VolleyballPlayer p, ServeType type) {
        server = p;
        serveType = type;
        states[p.id].serveType = type;
        servePhase = ServePhase.HOLDING;
        touchSide = null;
        touchCount = 0;
        lastToucherId = -1;
        for (PlayerState s : states) s.armed = false;
        ball.hold(ServeMechanic.holdPoint(p, type, tmp));
    }

    public boolean toss(double now) {
        if (servePhase != ServePhase.HOLDING) return false;
        serveMechanic.toss(server, serveType, ball, random);
        servePhase = ServePhase.TOSSED;
        tossTime = now;
        // The toss pose lasts until the hit (the swing replaces it), so the arms never drop in between.
        server.setActionPose(ActionPose.TOSS, 1.2f);
        return true;
    }

    public ServePhase servePhase() {
        return servePhase;
    }

    public VolleyballPlayer server() {
        return server;
    }

    /** Ends any serve sequence (e.g. when a drill or rally is reset). */
    public void clearServe() {
        servePhase = ServePhase.NONE;
        server = null;
    }

    // ------------------------------------------------------------------ touch counting

    /** Marks the ball as already touched {@code touches} times by {@code side} (ball machine feeds). */
    public void registerFeed(CourtSide side, int touches) {
        clearServe();
        touchSide = side;
        touchCount = touches;
        lastToucherId = -1;
    }

    /** Touches the given team has made in its current possession. */
    public int teamTouches(CourtSide side) {
        return side == touchSide ? touchCount : 0;
    }

    public CourtSide lastTouchSide() {
        return touchSide;
    }

    /** Player who made the last deliberate contact (-1 after a feed or serve preparation). */
    public int lastToucherId() {
        return lastToucherId;
    }

    // ------------------------------------------------------------------ simulation

    /** Runs before the ball physics step. */
    public void update(float dt, double now) {
        for (VolleyballPlayer p : players) p.tickActionPose(dt);
        if (servePhase == ServePhase.HOLDING) {
            ball.hold(ServeMechanic.holdPoint(server, serveType, tmp));
            server.holdActionPose(ActionPose.HOLD_BALL);
        } else if (servePhase == ServePhase.TOSSED && now - tossTime > SERVE_TIMEOUT) {
            servePhase = ServePhase.NONE;
        }
        updateSwingStyles();
        for (VolleyballPlayer p : players) {
            PlayerState s = states[p.id];
            if (!s.armed) continue;
            float window = (!p.isUpright() ? DIVE_WINDOW : p.isAirborne() ? AIR_WINDOW : GROUND_WINDOW)
                    * (reachScale(p) > 1f ? ASSIST_WINDOW : 1f);
            double age = now - s.armedAt;
            if (age > window || !ball.isActive()) {
                s.armed = false;
                continue;
            }
            // Candidates are in order of preference: the first one within reach is used (a clean
            // forearm pass beats a one-arm save); if none is within reach, the closest one is tracked.
            List<ContactType> candidates = candidates(p, s.kind);
            ContactType best = null;
            float bestOffset = Float.MAX_VALUE;
            for (ContactType type : candidates) {
                float offset = ContactZone.normalizedOffset(p, type, serveTypeFor(p), ball.position, tmp) / reachScale(p);
                if (offset <= 1f) {
                    best = type;
                    bestOffset = offset;
                    break;
                }
                if (offset < bestOffset) {
                    bestOffset = offset;
                    best = type;
                }
            }
            if (best == null) continue;
            if (p.isUpright()) p.holdActionPose(poseFor(best));
            float previous = s.previousOffsets[best.ordinal()];
            if (bestOffset <= 1f) {
                boolean movingAway = bestOffset > previous + 1e-4f && previous <= 1f;
                boolean lastChance = age + dt >= window;
                if (movingAway || bestOffset < 0.12f || lastChance || aboutToHitBody(p, dt)) {
                    contact(p, s, best, bestOffset, now);
                    continue;
                }
            }
            for (ContactType type : candidates) {
                s.previousOffsets[type.ordinal()] = ContactZone.normalizedOffset(p, type, serveTypeFor(p), ball.position, tmp) / reachScale(p);
            }
        }
    }

    /**
     * The ball, already within reach, would hit the player's body during the next step: the arms
     * are in front of the body, so the contact happens now (a fast serve would otherwise reach
     * the chest between two checks and bounce off it).
     */
    private boolean aboutToHitBody(VolleyballPlayer p, float dt) {
        if (!p.isUpright() || ball.position.y > p.position.y + p.height + 0.1f) return false;
        float dx = ball.position.x - p.position.x;
        float dz = ball.position.z - p.position.z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float approach = distance < 1e-4f ? 0f : -(ball.velocity.x * dx + ball.velocity.z * dz) / distance;
        return approach > 0f && distance - approach * dt * 2f < BODY_GUARD;
    }

    /** The techniques of a set, in order of preference: both hands, then one hand at full stretch. */
    private static final List<ContactType> SETS = List.of(ContactType.OVERHEAD_PASS, ContactType.ONE_HAND_SET);

    private List<ContactType> candidates(VolleyballPlayer p, ActionKind kind) {
        if (p == server && servePhase == ServePhase.TOSSED) {
            return ball.velocity.y <= SERVE_MAX_RISING_SPEED ? List.of(ContactType.SERVE) : List.of();
        }
        if (p == server && servePhase == ServePhase.HOLDING) return List.of();
        if (!p.isUpright()) {
            // Diving: the stretched-out arms, until shortly after hitting the floor.
            boolean reaching = p.bodyState() == com.volleyball.game.entities.BodyState.DIVING
                    || (p.bodyState() == com.volleyball.game.entities.BodyState.ON_FLOOR && p.bodyStateTime() < 0.35f);
            return reaching ? List.of(ContactType.DIVE) : List.of();
        }
        if (p.isAirborne()) {
            // A setter in the air on the second touch sets (a jump set) with the primary action;
            // the secondary action is the dump.
            if (p.role == PlayerRole.SETTER && kind == ActionKind.PRIMARY && teamTouches(p.side) == 1) return SETS;
            return List.of(ContactType.ATTACK);
        }
        // On the ground the technique is fixed when the request is made, so the contact never
        // jumps between techniques mid-flight: the secondary action is always overhead; the
        // primary action is overhead when setting (second touch), otherwise a forearm pass.
        // The libero's primary action stays a forearm pass on the second touch (a bump set): an
        // overhead set by the libero in the front zone would forbid the attack above the net.
        boolean liberoBump = p.role == PlayerRole.LIBERO && kind == ActionKind.PRIMARY;
        // On the second touch a ball beyond both hands can still be set with one hand.
        if (teamTouches(p.side) == 1 && !liberoBump) return SETS;
        if (kind == ActionKind.SECONDARY) return List.of(ContactType.OVERHEAD_PASS);
        // A ball arriving above the forearms (a flat serve at shoulder height) is taken overhead.
        if (liberoBump) return List.of(ContactType.FOREARM_PASS, ContactType.ONE_ARM);
        return List.of(ContactType.FOREARM_PASS, ContactType.ONE_ARM, ContactType.OVERHEAD_PASS);
    }

    private ServeType serveTypeFor(VolleyballPlayer p) {
        return p == server ? serveType : null;
    }

    /** The arm action of an attack type at the contact (see {@link SwingStyle}). */
    static SwingStyle swingFor(AttackType type) {
        return switch (type) {
            case POWER, OFF_THE_TOP -> SwingStyle.POWER;
            case CUT -> SwingStyle.CUT;
            case ROLL_SHOT -> SwingStyle.ROLL;
            case TIP, FEINT -> SwingStyle.TIP;
            case TOOL -> SwingStyle.TOOL;
        };
    }

    /** The arm action shown in the wind-up: a feint looks like a power spike until the contact. */
    static SwingStyle windUpFor(AttackType type) {
        return type == AttackType.FEINT ? SwingStyle.POWER : swingFor(type);
    }

    static SwingStyle swingFor(ServeType type) {
        if (!type.overhand) return SwingStyle.UNDERHAND_SERVE;
        return type.topspin > 0f ? SwingStyle.TOPSPIN_SERVE : SwingStyle.FLOAT_SERVE;
    }

    /** Before the contact the wind-up already shows the shot being prepared (the jump, the toss). */
    private void updateSwingStyles() {
        for (VolleyballPlayer p : players) {
            PlayerState s = states[p.id];
            if (p == server && servePhase != ServePhase.NONE) p.setSwingStyle(swingFor(serveType));
            else if (p.isAirborne() && p.actionPose() != ActionPose.SWING) {
                p.setSwingStyle(windUpFor(s.kind == ActionKind.SECONDARY && s.armed ? AttackType.TIP : s.attackType));
            }
        }
    }

    private static ActionPose poseFor(ContactType type) {
        return switch (type) {
            case FOREARM_PASS, ONE_ARM, DIVE -> ActionPose.PLATFORM;
            case OVERHEAD_PASS, ONE_HAND_SET -> ActionPose.OVERHEAD;
            case ATTACK, SERVE -> ActionPose.SWING;
            case BLOCK -> ActionPose.BLOCK;
        };
    }

    private void contact(VolleyballPlayer p, PlayerState s, ContactType type, float offset, double now) {
        ContactContext c = context;
        c.player = p;
        c.type = type;
        c.ballPosition.set(ball.position);
        c.incomingVelocity.set(ball.velocity);
        c.netTop = net.topHeight();
        c.quality = Math.max(0.05f, Math.min(ContactZone.qualityCap(type),
                ContactZone.spatialQuality(offset) * ContactZone.balanceFactor(p, type)));
        // At set point the clutch players rise and the nervous ones tighten up.
        if (pressure && p.has(PlayerTrait.CLUTCH)) c.quality = Math.min(ContactZone.qualityCap(type), c.quality * 1.08f);
        if (pressure && p.has(PlayerTrait.NERVOUS)) c.quality *= 0.85f;
        c.lateralOffset = ContactZone.lateralOffset(p, type, serveTypeFor(p), ball.position, tmp);
        c.serveType = serveTypeFor(p);
        c.setType = s.setType;
        c.attackType = s.kind == ActionKind.SECONDARY ? AttackType.TIP : s.attackType;
        if (type == ContactType.ATTACK) p.setSwingStyle(swingFor(c.attackType));
        else if (type == ContactType.SERVE && c.serveType != null) p.setSwingStyle(swingFor(c.serveType));

        int previousTouches = teamTouches(p.side);
        String variant = "";
        if (type == ContactType.SERVE) {
            c.purpose = ContactPurpose.SERVE;
            aimOrDefault(p, s, c.target);
            serveMechanic.hit(c, random, solution);
            variant = c.serveType.label;
            servePhase = ServePhase.NONE;
        } else if (type == ContactType.ATTACK) {
            c.purpose = ContactPurpose.ATTACK;
            aimOrDefault(p, s, c.target);
            if (c.attackType == AttackType.TOOL) toolTarget(p, c.target);
            else if (c.attackType == AttackType.OFF_THE_TOP) offTheTopTarget(p, c.target);
            attackMechanic.attack(c, random, solution);
            variant = c.attackType.label;
        } else if (previousTouches == 0) {
            c.purpose = ContactPurpose.PASS;
            if (c.incomingVelocity.len() > TacticalTargets.DIG_SPEED || type == ContactType.DIVE || type == ContactType.ONE_ARM) {
                // A dig: up and into the middle of the own court.
                TacticalTargets.digTarget(p.side, c.target);
                c.apex = TacticalTargets.DIG_APEX;
            } else {
                TacticalTargets.setterTarget(p.side, c.target);
                // The worse the contact, the further off the net the pass is aimed, so that an
                // inaccurate pass does not end in the net or go over it.
                float margin = PASS_NET_MARGIN + (1f - c.quality) * PASS_NET_MARGIN_POOR;
                // An overhead reception (a serve at shoulder height) is pushed, not cushioned: aim further off the net.
                if (type == ContactType.OVERHEAD_PASS) margin += 0.8f;
                // A hard serve is passed higher and further off the net (there is no time to control it).
                margin += 0.45f * Math.min(1f, ReceiveMechanic.difficulty(c.incomingVelocity));
                c.target.x += p.side.sign() * margin;
                c.apex = TacticalTargets.PASS_APEX;
            }
            receiveMechanic.pass(c, random, solution);
        } else if (previousTouches == 1) {
            c.purpose = ContactPurpose.SET;
            c.setType.target(p.side, c.target);
            c.apex = c.setType.apex;
            // Set from deep in the court: the ball comes in at an angle, so it is aimed further off the
            // net (and a fast set gets more height), or a small error puts it into the net.
            float fromNet = Math.abs(c.ballPosition.x);
            if (fromNet > 1.5f) {
                c.target.x += p.side.sign() * Math.min(1.2f, 0.35f * (fromNet - 1.5f));
                if (c.setType.fast()) c.apex += Math.min(1.2f, 0.4f * (fromNet - 1.5f));
            }
            variant = c.setType.label;
            if (type.fingerSet()) setMechanic.set(c, random, solution);
            else receiveMechanic.pass(c, random, solution); // bump set
        } else {
            c.purpose = ContactPurpose.SEND_OVER;
            aimOrDefault(p, s, c.target);
            c.target.y = 0.6f;
            c.apex = Math.max(TacticalTargets.FREE_BALL_APEX, c.ballPosition.y + 1.5f);
            receiveMechanic.pass(c, random, solution);
        }

        ball.hit(solution.velocity, solution.spin, random.nextLong());
        collisions.ignoreBodyUntil(p.id, now + BODY_IGNORE_AFTER_CONTACT);
        if (p.isUpright()) p.setActionPose(poseFor(type), 0.35f);
        s.armed = false;

        countTouch(p, now);

        PassRating rating = null;
        float miss = Float.NaN;
        if (c.purpose == ContactPurpose.PASS || c.purpose == ContactPurpose.SET) {
            prediction.watchHeight = c.target.y;
            predictor.predict(ball, 6f, prediction);
            prediction.watchHeight = Float.NaN;
            boolean overNet = prediction.crossesNetPlane
                    && (!prediction.reachesWatchHeight || prediction.timeToNetCrossing < prediction.timeToDescent);
            if (prediction.reachesWatchHeight) {
                float dx = prediction.descentPoint.x - c.target.x;
                float dz = prediction.descentPoint.z - c.target.z;
                miss = (float) Math.sqrt(dx * dx + dz * dz);
            }
            rating = PassRating.classify(miss, overNet);
        }
        BallTouch touch = new BallTouch(p.id, p.side, type, c.purpose, c.quality, rating, miss,
                c.ballPosition.cpy(), p.position.cpy(), p.isAirborne(), touchCount, ball.velocity.cpy(), variant, now);
        for (BallTouchListener listener : listeners) listener.onBallTouch(touch);
    }

    /**
     * Physics contact listener: a ball touching a blocker's hands is a block touch. It does not
     * count as one of the blocking team's three touches; the next touch by that team is its first.
     */
    public void onBallContact(BallContact contact) {
        if (contact.kind() == ColliderKind.PLAYER_BODY) {
            // The ball bouncing off a body is a touch of that player's team too.
            VolleyballPlayer touched = player(contact.playerId());
            if (touched != null) countTouch(touched, contact.time());
            return;
        }
        if (contact.kind() != ColliderKind.BLOCK) return;
        VolleyballPlayer blocker = player(contact.playerId());
        if (blocker == null) return;
        PlayerState s = states[blocker.id];
        if (contact.time() - s.lastBlockTouch < BLOCK_TOUCH_COOLDOWN) return;
        s.lastBlockTouch = contact.time();
        touchSide = blocker.side;
        touchCount = 0;
        lastToucherId = blocker.id;
        lastTouchTime = contact.time();
        BallTouch touch = new BallTouch(blocker.id, blocker.side, ContactType.BLOCK, ContactPurpose.BLOCK,
                Math.min(1f, blocker.blockPenetration() / 0.3f), null, Float.NaN, contact.position().cpy(),
                blocker.position.cpy(), blocker.isAirborne(), 0, ball.velocity.cpy(), s.blockStyle.label, contact.time());
        for (BallTouchListener listener : listeners) listener.onBallTouch(touch);
    }

    /**
     * The tool: if an opponent is up in the block in front of the hitter, drive the ball at the
     * outside hand of that block (the one nearer the sideline) and on, out of the court. With no
     * block the aimed spot stays (and a flat drive there is usually out).
     */
    private void toolTarget(VolleyballPlayer hitter, Vector3 out) {
        VolleyballPlayer outside = null;
        for (VolleyballPlayer o : players) {
            if (o.side == hitter.side || !o.isAirborne() || Math.abs(o.position.x) > BlockMechanic.MAX_NET_DISTANCE + 0.3f) continue;
            if (Math.abs(o.position.z - ball.position.z) > 1.6f) continue;
            if (outside == null || Math.abs(o.position.z) > Math.abs(outside.position.z)) outside = o;
        }
        if (outside == null) return;
        float outward = Math.signum(outside.position.z == 0f ? ball.position.z : outside.position.z);
        // The outside hand is about 0.3 m out from the blocker's centre; aim a little beyond it so
        // the ball clips the hand on its outer edge and keeps going wide and deep.
        float handZ = outside.position.z + outward * 0.32f;
        float hx = ball.position.x;
        float hz = ball.position.z;
        float towardsHand = outside.position.x - hx;
        if (Math.abs(towardsHand) < 0.05f) return;
        // Extend the line from the contact through the hand to beyond the opponent's end line.
        float farX = outside.side.sign() * (CourtDimensions.HALF_LENGTH + 1f);
        float t = (farX - hx) / towardsHand;
        out.set(farX, 0f, hz + (handZ - hz) * t);
    }

    /**
     * Counts a touch by {@code p} with the referee's rules ({@code RallyManager}): every contact
     * counts except the same player again within {@link BallTouch#SAME_ACTION_WINDOW}, and the
     * count only starts over when the other team touches the ball (or after a block).
     */
    private void countTouch(VolleyballPlayer p, double now) {
        boolean sameAction = touchSide == p.side && p.id == lastToucherId && now - lastTouchTime < BallTouch.SAME_ACTION_WINDOW;
        if (!sameAction) {
            if (touchSide == p.side) touchCount++;
            else {
                touchSide = p.side;
                touchCount = 1;
            }
        }
        lastToucherId = p.id;
        lastTouchTime = now;
    }

    /**
     * Off the top of the block: if opponents are up in the block in front of the hitter, drive the
     * ball through the middle of their hands towards a spot well beyond the end line. The line is
     * nearly flat, so it meets the fingertips and the ball flies on, long and out. With no block
     * the aimed spot stays (and the drive is usually out).
     */
    void offTheTopTarget(VolleyballPlayer hitter, Vector3 out) {
        float blockZ = 0f;
        float blockX = 0f;
        int blockers = 0;
        for (VolleyballPlayer o : players) {
            if (o.side == hitter.side || !o.isAirborne() || Math.abs(o.position.x) > BlockMechanic.MAX_NET_DISTANCE + 0.3f) continue;
            if (Math.abs(o.position.z - ball.position.z) > 1.6f) continue;
            blockZ += o.position.z;
            blockX += o.position.x;
            blockers++;
        }
        if (blockers == 0) return;
        blockZ /= blockers;
        blockX /= blockers;
        float hx = ball.position.x;
        float hz = ball.position.z;
        float towardsBlock = blockX - hx;
        if (Math.abs(towardsBlock) < 0.05f) return;
        // Extend the line from the contact through the block to well beyond the opponent's end line.
        float farX = hitter.side.opposite().sign() * (CourtDimensions.HALF_LENGTH + OFF_THE_TOP_BEYOND);
        float t = (farX - hx) / towardsBlock;
        out.set(farX, 0f, hz + (blockZ - hz) * t);
    }

    /** How far beyond the end line (m) a shot off the top of the block is aimed. */
    static final float OFF_THE_TOP_BEYOND = 2.5f;

    private VolleyballPlayer player(int id) {
        for (VolleyballPlayer p : players) if (p.id == id) return p;
        return null;
    }

    private void aimOrDefault(VolleyballPlayer p, PlayerState s, Vector3 out) {
        if (s.hasAim) out.set(s.aim);
        else TacticalTargets.defaultAttackTarget(p.side, out);
    }
}
