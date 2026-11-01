package com.volleyball.game.entities;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.world.CourtSide;

/**
 * A volleyball player's physical state. {@link #position} is the point between the feet; Y is the
 * height of the feet above the floor (0 when standing, positive while airborne).
 *
 * <p>Facing is a yaw angle around +Y; yaw 0 looks along +X and the facing direction is
 * {@code (cos yaw, 0, -sin yaw)}, matching libGDX's rotation convention.
 */
public final class VolleyballPlayer {
    public final int id;
    public final String name;
    public final int number;
    public final PlayerRole role;
    public final CourtSide side;
    /** Standing body height in metres. */
    public final float height;
    public final PlayerAttributes attributes;

    public final Vector3 position = new Vector3();
    public final Vector3 velocity = new Vector3();
    public final Vector3 previousPosition = new Vector3();
    public float facingYaw;
    public float previousFacingYaw;

    private boolean airborne;
    private boolean crouching;
    /** Time left before the player can move freely again (after landing, diving, falling...). */
    private float recoveryTime;
    /** Total ground distance covered, used to phase the running animation. */
    private float strideDistance;
    private ActionPose actionPose = ActionPose.NONE;
    /** Seconds the current action pose has been held. */
    private float actionPoseTime;
    private float actionPoseDuration;
    /** True while the pose is a prepared one (held), false while an action is being executed. */
    private boolean actionPoseHeld;
    private BodyState bodyState = BodyState.UPRIGHT;
    private float bodyStateTime;
    /** Horizontal unit direction of the current dive or fall. */
    public final Vector3 diveDirection = new Vector3(1f, 0f, 0f);
    /** True for a sideways fall that ends in a roll; false for a forward, chest-first dive. */
    private boolean diveRoll;
    /** Set when the body ran or jumped into the net; read (and cleared) by the rules. */
    private boolean netContact;
    /** 0..1 while the player sinks down charging a jump (for animation). */
    private float jumpCharge;
    /** Where the feet last left the floor (take-off spot of the last jump); rules use it for jump serves and back-row attacks. */
    public final Vector3 takeoffPosition = new Vector3();
    /** Hands up and over the net in a block jump (set by the block mechanic every step). */
    private boolean blocking;
    /** How far the blocker's hands reach over the net plane, metres (0 = straight up). */
    private float blockPenetration;
    /** Sideways shift of the blocking hands from the body centre (world Z), to close the line or the diagonal. */
    private float blockShift;
    /** False while on the bench (e.g. a middle blocker replaced by the libero): no body, no play. */
    private boolean onCourt = true;
    /** Stamina, 0..1 (1 = fresh); see {@code PlayerLocomotion} for what costs and restores it. */
    private float stamina = 1f;
    /** Hits and serves with the left hand (the swing is mirrored, the contact is on the left). */
    private boolean leftHanded;
    /** The arm action of the coming (or last) attack or serve, for the animation. */
    private SwingStyle swingStyle = SwingStyle.POWER;
    /** Distinguishing traits (one or two); see {@link PlayerTrait}. */
    private final java.util.EnumSet<PlayerTrait> traits = java.util.EnumSet.noneOf(PlayerTrait.class);

    public VolleyballPlayer(int id, String name, int number, PlayerRole role, CourtSide side, float height,
                            PlayerAttributes attributes) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.role = role;
        this.side = side;
        this.height = height;
        this.attributes = attributes;
        this.facingYaw = facingNetYaw(side);
        this.previousFacingYaw = facingYaw;
    }

    public boolean isLeftHanded() {
        return leftHanded;
    }

    public void setLeftHanded(boolean leftHanded) {
        this.leftHanded = leftHanded;
    }

    /** +1 for a right-hander, -1 for a left-hander: the side of the hitting arm. */
    public float handSign() {
        return leftHanded ? -1f : 1f;
    }

    public SwingStyle swingStyle() {
        return swingStyle;
    }

    public void setSwingStyle(SwingStyle style) {
        swingStyle = style;
    }

    public java.util.Set<PlayerTrait> traits() {
        return java.util.Collections.unmodifiableSet(traits);
    }

    public boolean has(PlayerTrait trait) {
        return traits.contains(trait);
    }

    /** Gives the player a trait and its rating boost. */
    public void addTrait(PlayerTrait trait) {
        if (traits.add(trait)) trait.boost(attributes);
    }

    /** The traits as text, e.g. "Left-handed · Cannon · Clutch" (empty: none). */
    public String traitLabel() {
        StringBuilder b = new StringBuilder(leftHanded ? "Left-handed" : "");
        for (PlayerTrait t : traits) b.append(b.length() == 0 ? "" : " · ").append(t.label);
        return b.toString();
    }

    /** Yaw that faces the net from the given side. */
    public static float facingNetYaw(CourtSide side) {
        return side == CourtSide.HOME ? 0f : MathUtils.PI;
    }

    public void placeAt(float x, float z) {
        position.set(x, 0f, z);
        previousPosition.set(position);
        velocity.setZero();
        airborne = false;
        recoveryTime = 0f;
        bodyState = BodyState.UPRIGHT;
        bodyStateTime = 0f;
        blocking = false;
        jumpCharge = 0f;
        takeoffPosition.set(position);
        facingYaw = facingNetYaw(side);
        previousFacingYaw = facingYaw;
    }

    /** Puts a player on a bench chair, facing the court (the benches are on the +Z side). */
    public void sitOnBench(float x, float z) {
        placeAt(x, z);
        // Facing the court from whichever side the bench is on.
        facingYaw = (float) (z >= 0f ? Math.PI / 2.0 : -Math.PI / 2.0);
        previousFacingYaw = facingYaw;
    }

    public boolean isOnCourt() {
        return onCourt;
    }

    public void setOnCourt(boolean onCourt) {
        this.onCourt = onCourt;
    }

    public boolean isAirborne() {
        return airborne;
    }

    public void setAirborne(boolean airborne) {
        this.airborne = airborne;
    }

    public boolean isCrouching() {
        return crouching;
    }

    public void setCrouching(boolean crouching) {
        this.crouching = crouching;
    }

    public float recoveryTime() {
        return recoveryTime;
    }

    public void setRecoveryTime(float seconds) {
        recoveryTime = Math.max(recoveryTime, seconds);
    }

    public void tickRecovery(float dt) {
        recoveryTime = Math.max(0f, recoveryTime - dt);
    }

    public float strideDistance() {
        return strideDistance;
    }

    public void addStride(float distance) {
        strideDistance += distance;
    }

    /** Starts an action pose (e.g. a swing) that plays for the given time. */
    public void setActionPose(ActionPose pose, float duration) {
        actionPose = pose;
        actionPoseTime = 0f;
        actionPoseDuration = duration;
        actionPoseHeld = false;
    }

    /** Keeps a prepared pose (e.g. platform ready for a pass) alive; call every step it applies. */
    public void holdActionPose(ActionPose pose) {
        if (pose != actionPose || !actionPoseHeld) setActionPose(pose, 0.1f);
        else actionPoseDuration = actionPoseTime + 0.1f;
        actionPoseHeld = true;
    }

    public boolean isActionPoseHeld() {
        return actionPoseHeld;
    }

    public void tickActionPose(float dt) {
        if (actionPose == ActionPose.NONE) return;
        actionPoseTime += dt;
        if (actionPoseTime >= actionPoseDuration) {
            actionPose = ActionPose.NONE;
            actionPoseTime = 0f;
        }
    }

    public ActionPose actionPose() {
        return actionPose;
    }

    /** 0..1 progress through the current action pose. */
    public float actionPoseProgress() {
        return actionPoseDuration <= 0f ? 1f : Math.min(1f, actionPoseTime / actionPoseDuration);
    }

    public void markNetContact() {
        netContact = true;
    }

    /** Returns whether the player touched the net since the last call, and clears the flag. */
    public boolean consumeNetContact() {
        boolean touched = netContact;
        netContact = false;
        return touched;
    }

    public float jumpCharge() {
        return jumpCharge;
    }

    public void setJumpCharge(float charge) {
        jumpCharge = Math.max(0f, Math.min(1f, charge));
    }

    public void recordTakeoff() {
        takeoffPosition.set(position.x, 0f, position.z);
    }

    public BodyState bodyState() {
        return bodyState;
    }

    public void setBodyState(BodyState state) {
        if (state != bodyState) bodyStateTime = 0f;
        bodyState = state;
    }

    /** Sets the state as if it had started {@code elapsed} seconds ago (tools, replays). */
    public void setBodyState(BodyState state, float elapsed) {
        bodyState = state;
        bodyStateTime = elapsed;
    }

    public boolean isDiveRoll() {
        return diveRoll;
    }

    public void setDiveRoll(boolean roll) {
        diveRoll = roll;
    }

    public float bodyStateTime() {
        return bodyStateTime;
    }

    public void tickBodyState(float dt) {
        bodyStateTime += dt;
    }

    public boolean isUpright() {
        return bodyState == BodyState.UPRIGHT;
    }

    /**
     * 0 = standing, 1 = lying flat: how far the body is tipped over in a dive or fall. Eased, so
     * the body accelerates into the dive and decelerates when getting up (shared by physics,
     * contact geometry and rendering).
     */
    public float bodyTilt() {
        return switch (bodyState) {
            case UPRIGHT -> 0f;
            case DIVING -> smoothstep(bodyStateTime / 0.22f);
            case ON_FLOOR -> 1f;
            case GETTING_UP -> 1f - smoothstep(bodyStateTime / 0.5f);
        };
    }

    private static float smoothstep(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }

    public boolean isBlocking() {
        return blocking;
    }

    public float blockPenetration() {
        return blockPenetration;
    }

    public float blockShift() {
        return blockShift;
    }

    public void setBlocking(boolean blocking, float penetration) {
        setBlocking(blocking, penetration, 0f);
    }

    public void setBlocking(boolean blocking, float penetration, float shift) {
        this.blocking = blocking;
        this.blockPenetration = blocking ? penetration : 0f;
        this.blockShift = blocking ? shift : 0f;
    }

    public float facingX() {
        return MathUtils.cos(facingYaw);
    }

    public float facingZ() {
        return -MathUtils.sin(facingYaw);
    }

    /** Horizontal speed in m/s. */
    public float groundSpeed() {
        return (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    /** Height of the fingertips with arms raised while standing (~1.33 x body height). */
    public float standingReach() {
        return height * 1.33f;
    }

    public float stamina() {
        return stamina;
    }

    public void setStamina(float value) {
        stamina = Math.max(0f, Math.min(1f, value));
    }

    /** Recovers part of the lost stamina (rest between rallies, a timeout, the set break). */
    public void rest(float fractionOfMissing) {
        setStamina(stamina + (1f - stamina) * fractionOfMissing);
    }

    /** 1 when fresh, down to {@code 1 - maxLoss} when exhausted: how much tiredness weighs on an ability. */
    public float fatigueFactor(float maxLoss) {
        return 1f - maxLoss * (1f - stamina);
    }

    /**
     * Everything that shows how a player looks at a moment (pose, place, body state): saved for
     * replays and restored afterwards. It does not touch identity or ratings.
     */
    public static final class VisualState {
        final Vector3 position = new Vector3();
        final Vector3 previousPosition = new Vector3();
        final Vector3 velocity = new Vector3();
        float facingYaw;
        float previousFacingYaw;
        boolean airborne;
        boolean crouching;
        float recoveryTime;
        float strideDistance;
        ActionPose actionPose = ActionPose.NONE;
        float actionPoseTime;
        float actionPoseDuration;
        boolean actionPoseHeld;
        BodyState bodyState = BodyState.UPRIGHT;
        float bodyStateTime;
        final Vector3 diveDirection = new Vector3();
        boolean diveRoll;
        boolean netContact;
        float jumpCharge;
        final Vector3 takeoffPosition = new Vector3();
        boolean blocking;
        float blockPenetration;
        float blockShift;
        boolean onCourt;
        float stamina;

        /** Blends two states for smooth slow motion: places and angles in between, the rest from {@code a}. */
        public void interpolate(VisualState a, VisualState b, float t) {
            copy(a);
            position.set(a.position).lerp(b.position, t);
            previousPosition.set(position);
            velocity.set(a.velocity).lerp(b.velocity, t);
            float d = b.facingYaw - a.facingYaw;
            while (d > Math.PI) d -= (float) (Math.PI * 2.0);
            while (d < -Math.PI) d += (float) (Math.PI * 2.0);
            facingYaw = a.facingYaw + d * t;
            previousFacingYaw = facingYaw;
            strideDistance = a.strideDistance + (b.strideDistance - a.strideDistance) * t;
            if (a.actionPose == b.actionPose) actionPoseTime = a.actionPoseTime + (b.actionPoseTime - a.actionPoseTime) * t;
            if (a.bodyState == b.bodyState) bodyStateTime = a.bodyStateTime + (b.bodyStateTime - a.bodyStateTime) * t;
            blockPenetration = a.blockPenetration + (b.blockPenetration - a.blockPenetration) * t;
        }

        void copy(VisualState o) {
            position.set(o.position);
            previousPosition.set(o.previousPosition);
            velocity.set(o.velocity);
            facingYaw = o.facingYaw;
            previousFacingYaw = o.previousFacingYaw;
            airborne = o.airborne;
            crouching = o.crouching;
            recoveryTime = o.recoveryTime;
            strideDistance = o.strideDistance;
            actionPose = o.actionPose;
            actionPoseTime = o.actionPoseTime;
            actionPoseDuration = o.actionPoseDuration;
            actionPoseHeld = o.actionPoseHeld;
            bodyState = o.bodyState;
            bodyStateTime = o.bodyStateTime;
            diveDirection.set(o.diveDirection);
            diveRoll = o.diveRoll;
            netContact = o.netContact;
            jumpCharge = o.jumpCharge;
            takeoffPosition.set(o.takeoffPosition);
            blocking = o.blocking;
            blockPenetration = o.blockPenetration;
            blockShift = o.blockShift;
            onCourt = o.onCourt;
            stamina = o.stamina;
        }
    }

    public void saveVisual(VisualState out) {
        out.position.set(position);
        out.previousPosition.set(previousPosition);
        out.velocity.set(velocity);
        out.facingYaw = facingYaw;
        out.previousFacingYaw = previousFacingYaw;
        out.airborne = airborne;
        out.crouching = crouching;
        out.recoveryTime = recoveryTime;
        out.strideDistance = strideDistance;
        out.actionPose = actionPose;
        out.actionPoseTime = actionPoseTime;
        out.actionPoseDuration = actionPoseDuration;
        out.actionPoseHeld = actionPoseHeld;
        out.bodyState = bodyState;
        out.bodyStateTime = bodyStateTime;
        out.diveDirection.set(diveDirection);
        out.diveRoll = diveRoll;
        out.netContact = netContact;
        out.jumpCharge = jumpCharge;
        out.takeoffPosition.set(takeoffPosition);
        out.blocking = blocking;
        out.blockPenetration = blockPenetration;
        out.blockShift = blockShift;
        out.onCourt = onCourt;
        out.stamina = stamina;
    }

    public void restoreVisual(VisualState in) {
        position.set(in.position);
        previousPosition.set(in.previousPosition);
        velocity.set(in.velocity);
        facingYaw = in.facingYaw;
        previousFacingYaw = in.previousFacingYaw;
        airborne = in.airborne;
        crouching = in.crouching;
        recoveryTime = in.recoveryTime;
        strideDistance = in.strideDistance;
        actionPose = in.actionPose;
        actionPoseTime = in.actionPoseTime;
        actionPoseDuration = in.actionPoseDuration;
        actionPoseHeld = in.actionPoseHeld;
        bodyState = in.bodyState;
        bodyStateTime = in.bodyStateTime;
        diveDirection.set(in.diveDirection);
        diveRoll = in.diveRoll;
        netContact = in.netContact;
        jumpCharge = in.jumpCharge;
        takeoffPosition.set(in.takeoffPosition);
        blocking = in.blocking;
        blockPenetration = in.blockPenetration;
        blockShift = in.blockShift;
        onCourt = in.onCourt;
        stamina = in.stamina;
    }
}
