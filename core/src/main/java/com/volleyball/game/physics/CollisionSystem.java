package com.volleyball.game.physics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.Net;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Collision geometry of the arena and the players' bodies, plus the impulse-based response.
 *
 * <p>Response for a sphere against a fixed or moving surface with normal n:
 * <ol>
 *   <li>Relative velocity of the ball's contact point: {@code vc = v + ω × (−r·n) − v_surface}.</li>
 *   <li>Normal impulse {@code Jn = (1 + e)·m·(−vc·n)} reverses the approach speed scaled by restitution.</li>
 *   <li>Tangential (friction) impulse opposes slip, capped by Coulomb's law {@code Jt ≤ μ·Jn}. The
 *       impulse needed to stop slip on a sphere is {@code m·k/(1+k)·|v_slip|} with {@code I = k·m·r²}.
 *       It changes both velocity and spin — this is how a ball picks up or loses spin on the floor.</li>
 * </ol>
 * Thin objects (net, antennas) cannot be tunnelled through because {@link BallPhysics} sub-steps
 * so that the ball never moves more than a fraction of its radius per sub-step.
 */
public final class CollisionSystem {
    /** Below this approach speed a floor contact is treated as resting (no bounce). */
    static final float RESTING_SPEED = 0.25f;
    /** Minimum approach speed for a reported impact (rolling and sliding are not re-reported). */
    static final float EVENT_MIN_SPEED = 0.3f;
    static final float SENSITIVE_EVENT_MIN_SPEED = 0.02f;
    /** The same collider is not re-reported within this time (one impact spans several sub-steps). */
    static final double EVENT_COOLDOWN = 0.12;

    /** Arena boundary used for the ball: fronts of the stands around the hall floor. */
    public static final float WALL_X = 19f;
    public static final float WALL_Z = 12.5f;

    private final List<Collider> arena = new ArrayList<>();
    private final List<BodyColliders> bodies = new ArrayList<>();
    private final List<Collider> all = new ArrayList<>();

    private final Collider.Contact contact = new Collider.Contact();
    private final Vector3 contactOffset = new Vector3();
    private final Vector3 contactVelocity = new Vector3();
    private final Vector3 slip = new Vector3();
    private final Vector3 tangent = new Vector3();
    private final Vector3 angularImpulse = new Vector3();
    private final double[] lastEventTimes = new double[256];
    /** Per player id: body colliders are ignored until this time (the ball is leaving their hands). */
    private final double[] ignoreBodyUntil = new double[256];

    /** (Re)creates the static arena colliders, e.g. after the net height changes. */
    public void buildArena(Net net) {
        arena.clear();
        arena.add(new Collider.HalfSpace(ColliderKind.FLOOR, SurfaceMaterial.FLOOR, Vector3.Zero, Vector3.Y));
        arena.add(new Collider.HalfSpace(ColliderKind.CEILING, SurfaceMaterial.CEILING,
                new Vector3(0f, PhysicsConstants.CEILING_HEIGHT, 0f), new Vector3(0f, -1f, 0f)));
        for (int s = -1; s <= 1; s += 2) {
            arena.add(new Collider.HalfSpace(ColliderKind.WALL, SurfaceMaterial.WALL,
                    new Vector3(s * WALL_X, 0f, 0f), new Vector3(-s, 0f, 0f)));
            arena.add(new Collider.HalfSpace(ColliderKind.WALL, SurfaceMaterial.WALL,
                    new Vector3(0f, 0f, s * WALL_Z), new Vector3(0f, 0f, -s)));
        }

        float top = net.topHeight();
        float bandCentre = top - Net.TOP_BAND_HEIGHT / 2f;
        float meshTop = top - Net.TOP_BAND_HEIGHT;
        float halfLength = Net.LENGTH / 2f;
        float meshHalfHeight = (meshTop - net.bottomHeight()) / 2f;
        arena.add(new Collider.Box(ColliderKind.NET_MESH, SurfaceMaterial.NET_MESH,
                new Vector3(0f, net.bottomHeight() + meshHalfHeight, 0f), new Vector3(0.005f, meshHalfHeight, halfLength)));
        arena.add(new Collider.Capsule(ColliderKind.NET_BAND, SurfaceMaterial.NET_BAND, -1,
                new Vector3(0f, bandCentre, -halfLength), new Vector3(0f, bandCentre, halfLength), Net.TOP_BAND_HEIGHT / 2f));
        for (int s = -1; s <= 1; s += 2) {
            float antennaZ = s * net.antennaAxisZ();
            arena.add(new Collider.Capsule(ColliderKind.ANTENNA, SurfaceMaterial.ANTENNA, -1,
                    new Vector3(0f, net.antennaBottom(), antennaZ), new Vector3(0f, net.antennaTop(), antennaZ),
                    Net.ANTENNA_DIAMETER / 2f));
            float postZ = s * net.postAxisZ();
            arena.add(new Collider.Capsule(ColliderKind.POST, SurfaceMaterial.POST, -1,
                    new Vector3(0f, 0f, postZ), new Vector3(0f, Net.POST_HEIGHT, postZ), Net.POST_DIAMETER / 2f));
            arena.add(new Collider.Capsule(ColliderKind.POST, SurfaceMaterial.PADDING, -1,
                    new Vector3(0f, 0f, postZ), new Vector3(0f, 1.9f, postZ), 0.14f));
        }
        rebuildList();
    }

    /** Only an infinite floor: for isolated physics experiments (tests, ball-flight tools). */
    public void buildFloorOnly() {
        arena.clear();
        arena.add(new Collider.HalfSpace(ColliderKind.FLOOR, SurfaceMaterial.FLOOR, Vector3.Zero, Vector3.Y));
        rebuildList();
    }

    /** Registers the players whose bodies the ball can hit. */
    public void setPlayers(Collection<VolleyballPlayer> players) {
        bodies.clear();
        for (VolleyballPlayer player : players) bodies.add(new BodyColliders(player));
        rebuildList();
    }

    /** Moves the body colliders to the players' current poses. Call once per step before {@link #resolve}. */
    public void updateBodies() {
        for (BodyColliders body : bodies) body.update();
    }

    /** Ignores a player's body colliders until {@code time}, e.g. right after the player played the ball. */
    public void ignoreBodyUntil(int playerId, double time) {
        if (playerId >= 0 && playerId < ignoreBodyUntil.length) ignoreBodyUntil[playerId] = time;
    }

    public List<Collider> colliders() {
        return all;
    }

    /**
     * Detects and resolves all overlaps of the ball. Returns the number of contacts handled.
     *
     * @param dt duration of the sub-step (used for rolling damping)
     */
    public int resolve(Ball ball, Vector3 previousCenter, float dt, double time, BallContactListener listener) {
        int contacts = 0;
        for (int i = 0; i < all.size(); i++) {
            Collider collider = all.get(i);
            if (!collider.enabled) continue;
            if (collider.ownerId >= 0 && collider.ownerId < ignoreBodyUntil.length && time < ignoreBodyUntil[collider.ownerId]) {
                continue;
            }
            if (!collider.contact(ball.position, Ball.RADIUS, previousCenter, contact)) continue;
            respond(ball, collider, i, dt, time, listener);
            contacts++;
        }
        return contacts;
    }

    private void respond(Ball ball, Collider collider, int index, float dt, double time, BallContactListener listener) {
        Vector3 normal = contact.normal;
        ball.position.mulAdd(normal, contact.penetration);

        contactOffset.set(normal).scl(-Ball.RADIUS);
        contactVelocity.set(ball.spin).crs(contactOffset).add(ball.velocity).sub(collider.velocity);
        float normalSpeed = contactVelocity.dot(normal);
        if (normalSpeed >= 0f) return; // already separating

        float impactSpeed = -normalSpeed;
        boolean resting = collider.kind == ColliderKind.FLOOR && impactSpeed < RESTING_SPEED;
        float restitution = resting ? 0f : collider.material.restitution;
        float normalImpulse = (1f + restitution) * impactSpeed * Ball.MASS;
        ball.velocity.mulAdd(normal, normalImpulse / Ball.MASS);

        slip.set(contactVelocity).mulAdd(normal, -normalSpeed);
        float slipSpeed = slip.len();
        if (slipSpeed > 1e-5f) {
            float tangentialMass = Ball.MASS * Ball.INERTIA_FACTOR / (1f + Ball.INERTIA_FACTOR);
            float tangentialImpulse = Math.min(tangentialMass * slipSpeed, collider.material.friction * normalImpulse);
            tangent.set(slip).scl(-1f / slipSpeed);
            ball.velocity.mulAdd(tangent, tangentialImpulse / Ball.MASS);
            angularImpulse.set(contactOffset).crs(tangent).scl(tangentialImpulse / Ball.INERTIA);
            ball.spin.add(angularImpulse);
        }

        if (resting && collider.material.rollingDamping > 0f) {
            float keep = (float) Math.exp(-collider.material.rollingDamping * dt);
            ball.velocity.x *= keep;
            ball.velocity.z *= keep;
            ball.spin.scl(keep);
        }

        float threshold = collider.kind == ColliderKind.PLAYER_BODY || collider.kind == ColliderKind.BLOCK
                || collider.kind == ColliderKind.ANTENNA
                ? SENSITIVE_EVENT_MIN_SPEED : EVENT_MIN_SPEED;
        if (listener != null && impactSpeed >= threshold && index < lastEventTimes.length
                && time - lastEventTimes[index] > EVENT_COOLDOWN) {
            lastEventTimes[index] = time;
            listener.onBallContact(new BallContact(collider.kind, collider.ownerId, ball.position.cpy(), impactSpeed, time));
        }
    }

    private void rebuildList() {
        all.clear();
        all.addAll(arena);
        for (BodyColliders body : bodies) {
            all.add(body.legs);
            all.add(body.torso);
            all.add(body.head);
            all.add(body.leftHand);
            all.add(body.rightHand);
        }
        Arrays.fill(lastEventTimes, Double.NEGATIVE_INFINITY);
    }

    /** True if the point is inside the court rectangle (lines included) — convenience for floor contacts. */
    public static boolean landedIn(Vector3 ballCentre) {
        return CourtDimensions.isInsideCourt(ballCentre.x, ballCentre.z, 0f);
    }

    /**
     * Collision shapes of a player: legs, torso and head along the body axis (which tips over in
     * a dive), plus the two hands/forearms that become active only in a block jump.
     * Other arm contacts are deliberate and handled by the ball-handling mechanics.
     */
    private static final class BodyColliders {
        static final float HAND_RADIUS = 0.065f;
        /** Centre-to-centre distance of the blocker's hands; the gap between them is narrower than the ball. */
        static final float HAND_SPREAD = 0.30f;

        private final VolleyballPlayer player;
        final Collider.Capsule legs;
        final Collider.Capsule torso;
        final Collider.Capsule head;
        final Collider.Capsule leftHand;
        final Collider.Capsule rightHand;
        private final Vector3 a = new Vector3();
        private final Vector3 b = new Vector3();
        private final Vector3 axis = new Vector3();
        private final Vector3 pivot = new Vector3();

        BodyColliders(VolleyballPlayer player) {
            this.player = player;
            legs = new Collider.Capsule(ColliderKind.PLAYER_BODY, SurfaceMaterial.BODY, player.id, Vector3.Zero, Vector3.Zero, 0.12f);
            torso = new Collider.Capsule(ColliderKind.PLAYER_BODY, SurfaceMaterial.BODY, player.id, Vector3.Zero, Vector3.Zero, 0.17f);
            head = new Collider.Capsule(ColliderKind.PLAYER_BODY, SurfaceMaterial.BODY, player.id, Vector3.Zero, Vector3.Zero, 0.11f);
            leftHand = new Collider.Capsule(ColliderKind.BLOCK, SurfaceMaterial.BLOCK_HANDS, player.id, Vector3.Zero, Vector3.Zero, HAND_RADIUS);
            rightHand = new Collider.Capsule(ColliderKind.BLOCK, SurfaceMaterial.BLOCK_HANDS, player.id, Vector3.Zero, Vector3.Zero, HAND_RADIUS);
            update();
        }

        void update() {
            Vector3 p = player.position;
            float h = player.height * (player.isCrouching() ? 0.8f : 1f);
            // Body axis: straight up, or tipped towards the dive direction (pivoting at the feet).
            float tilt = player.bodyTilt();
            float angle = tilt * MathUtils.HALF_PI * 0.95f;
            axis.set(MathUtils.sin(angle) * player.diveDirection.x, MathUtils.cos(angle), MathUtils.sin(angle) * player.diveDirection.z);
            pivot.set(p.x, p.y + 0.14f * tilt, p.z);
            legs.set(along(a, 0.12f), along(b, h * 0.47f), 0.12f);
            torso.set(along(a, h * 0.55f), along(b, h * 0.78f), 0.17f);
            head.set(along(a, h * 0.92f), along(b, h * 0.92f), 0.11f);
            legs.velocity.set(player.velocity);
            torso.velocity.set(player.velocity);
            head.velocity.set(player.velocity);
            updateHands(p);
            if (!player.isOnCourt()) {
                // On the bench: the ball cannot hit this player.
                legs.enabled = false;
                torso.enabled = false;
                head.enabled = false;
                leftHand.enabled = false;
                rightHand.enabled = false;
            } else {
                legs.enabled = true;
                torso.enabled = true;
                head.enabled = true;
            }
        }

        private Vector3 along(Vector3 out, float distance) {
            return out.set(pivot).mulAdd(axis, distance);
        }

        /**
         * Blocking arms: from the shoulders (own side of the net) up to the fingertips, which
         * reach over the net by the player's current penetration. The whole arm blocks, so a
         * ball cannot slip under the hands; a ball hitting the far side of that slope is
         * deflected down into the attacker's court.
         */
        private void updateHands(Vector3 p) {
            boolean active = player.isBlocking();
            leftHand.enabled = active;
            rightHand.enabled = active;
            if (!active) return;
            float towardsNet = -player.side.sign();
            float tipY = p.y + player.standingReach() + 0.02f;
            float tipX = p.x + towardsNet * (0.30f + player.blockPenetration());
            float shoulderX = p.x + towardsNet * 0.05f;
            float shoulderY = p.y + player.height * 0.78f;
            float centreZ = p.z + player.blockShift();
            // Near the antenna the outside hand turns in ("sealing" the antenna), so a ball wiped
            // off it goes back into the court rather than out.
            float sideline = com.volleyball.game.world.CourtDimensions.HALF_WIDTH;
            float nearSideline = Math.max(0f, Math.min(1f, (Math.abs(centreZ) - (sideline - 1.3f)) / 0.8f));
            for (int s = -1; s <= 1; s += 2) {
                Collider.Capsule hand = s < 0 ? leftHand : rightHand;
                float z = centreZ + s * HAND_SPREAD / 2f;
                boolean outside = Math.signum(z - centreZ) == Math.signum(centreZ);
                float tipZ = outside ? z - Math.signum(centreZ) * 0.14f * nearSideline : z;
                hand.set(a.set(shoulderX, shoulderY, z), b.set(tipX, tipY, tipZ), HAND_RADIUS);
                hand.velocity.set(player.velocity);
            }
        }
    }
}
