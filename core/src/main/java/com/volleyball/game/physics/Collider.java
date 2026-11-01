package com.volleyball.game.physics;

import com.badlogic.gdx.math.Vector3;

/**
 * A solid the ball can collide with. Shapes compute the contact against a sphere; the response
 * (impulses, spin, events) is shared and lives in {@link CollisionSystem}.
 */
public abstract class Collider {
    /** Result of an overlap test. The normal points from the collider towards the ball centre. */
    public static final class Contact {
        public final Vector3 normal = new Vector3();
        public float penetration;
    }

    public final ColliderKind kind;
    public final SurfaceMaterial material;
    /** Player id for body colliders, -1 for static arena objects. */
    public final int ownerId;
    /** Velocity of the collider surface (non-zero for moving players). */
    public final Vector3 velocity = new Vector3();
    /** Disabled colliders are skipped (e.g. a player's hands when not blocking). */
    public boolean enabled = true;

    protected Collider(ColliderKind kind, SurfaceMaterial material, int ownerId) {
        this.kind = kind;
        this.material = material;
        this.ownerId = ownerId;
    }

    /**
     * Tests a sphere for overlap.
     *
     * @param previousCenter where the sphere was a moment ago; used to pick the correct side
     *                       when the centre lies exactly inside a thin object
     */
    public abstract boolean contact(Vector3 center, float radius, Vector3 previousCenter, Contact out);

    /** Infinite plane; everything behind it is solid. */
    public static final class HalfSpace extends Collider {
        private final Vector3 point = new Vector3();
        private final Vector3 normal = new Vector3();

        public HalfSpace(ColliderKind kind, SurfaceMaterial material, Vector3 point, Vector3 normal) {
            super(kind, material, -1);
            this.point.set(point);
            this.normal.set(normal).nor();
        }

        @Override
        public boolean contact(Vector3 center, float radius, Vector3 previousCenter, Contact out) {
            float distance = (center.x - point.x) * normal.x + (center.y - point.y) * normal.y + (center.z - point.z) * normal.z;
            if (distance >= radius) return false;
            out.normal.set(normal);
            out.penetration = radius - distance;
            return true;
        }
    }

    /** Segment with a radius (cylinder with rounded ends). A sphere is a capsule with a == b. */
    public static final class Capsule extends Collider {
        public final Vector3 a = new Vector3();
        public final Vector3 b = new Vector3();
        public float radius;
        private final Vector3 closest = new Vector3();

        public Capsule(ColliderKind kind, SurfaceMaterial material, int ownerId, Vector3 a, Vector3 b, float radius) {
            super(kind, material, ownerId);
            set(a, b, radius);
        }

        public void set(Vector3 a, Vector3 b, float radius) {
            this.a.set(a);
            this.b.set(b);
            this.radius = radius;
        }

        @Override
        public boolean contact(Vector3 center, float sphereRadius, Vector3 previousCenter, Contact out) {
            closestPointOnSegment(a, b, center, closest);
            float reach = radius + sphereRadius;
            float distance2 = closest.dst2(center);
            if (distance2 >= reach * reach) return false;
            float distance = (float) Math.sqrt(distance2);
            if (distance > 1e-6f) {
                out.normal.set(center).sub(closest).scl(1f / distance);
            } else {
                closestPointOnSegment(a, b, previousCenter, closest);
                out.normal.set(previousCenter).sub(closest);
                if (out.normal.len2() < 1e-12f) out.normal.set(Vector3.Y);
                out.normal.nor();
            }
            out.penetration = reach - distance;
            return true;
        }

        static void closestPointOnSegment(Vector3 a, Vector3 b, Vector3 p, Vector3 out) {
            float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
            float length2 = abx * abx + aby * aby + abz * abz;
            float t = length2 < 1e-12f ? 0f
                    : ((p.x - a.x) * abx + (p.y - a.y) * aby + (p.z - a.z) * abz) / length2;
            t = Math.max(0f, Math.min(1f, t));
            out.set(a.x + abx * t, a.y + aby * t, a.z + abz * t);
        }
    }

    /** Axis-aligned box. Used for the thin net mesh. */
    public static final class Box extends Collider {
        private final Vector3 center = new Vector3();
        private final Vector3 halfSize = new Vector3();
        private final Vector3 closest = new Vector3();

        public Box(ColliderKind kind, SurfaceMaterial material, Vector3 center, Vector3 halfSize) {
            super(kind, material, -1);
            this.center.set(center);
            this.halfSize.set(halfSize);
        }

        @Override
        public boolean contact(Vector3 sphere, float radius, Vector3 previousCenter, Contact out) {
            closest.set(
                    clamp(sphere.x, center.x - halfSize.x, center.x + halfSize.x),
                    clamp(sphere.y, center.y - halfSize.y, center.y + halfSize.y),
                    clamp(sphere.z, center.z - halfSize.z, center.z + halfSize.z));
            float distance2 = closest.dst2(sphere);
            if (distance2 >= radius * radius) return false;
            float distance = (float) Math.sqrt(distance2);
            if (distance > 1e-6f) {
                out.normal.set(sphere).sub(closest).scl(1f / distance);
                out.penetration = radius - distance;
                return true;
            }
            // Centre inside the box (only possible for very thin boxes): push out through the face
            // on the side the ball came from, along the box's thinnest axis.
            pushOutOfThinAxis(sphere, radius, previousCenter, out);
            return true;
        }

        private void pushOutOfThinAxis(Vector3 sphere, float radius, Vector3 previousCenter, Contact out) {
            out.normal.setZero();
            if (halfSize.x <= halfSize.y && halfSize.x <= halfSize.z) {
                float side = Math.signum(previousCenter.x - center.x);
                out.normal.x = side == 0f ? 1f : side;
                out.penetration = radius + halfSize.x - Math.abs(sphere.x - center.x);
            } else if (halfSize.z <= halfSize.y) {
                float side = Math.signum(previousCenter.z - center.z);
                out.normal.z = side == 0f ? 1f : side;
                out.penetration = radius + halfSize.z - Math.abs(sphere.z - center.z);
            } else {
                float side = Math.signum(previousCenter.y - center.y);
                out.normal.y = side == 0f ? 1f : side;
                out.penetration = radius + halfSize.y - Math.abs(sphere.y - center.y);
            }
        }

        private static float clamp(float v, float min, float max) {
            return v < min ? min : Math.min(v, max);
        }
    }
}
