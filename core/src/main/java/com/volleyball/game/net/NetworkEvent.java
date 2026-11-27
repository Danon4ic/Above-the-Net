package com.volleyball.game.net;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.rules.Fault;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.RallyOutcome;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;

import java.nio.ByteBuffer;

/** Confirmed host event with a stable id, separate from frame interpolation. */
public record NetworkEvent(int id, int tick, Kind kind, int a, int b, int c, int d, int e, int f, int g,
                           float x, float y, float z, float vx, float vy, float vz, float quality, float impact) {
    public enum Kind { TOUCH, COLLISION, SERVE, POINT, SUBSTITUTION, TIMEOUT, ENDS }
    static final int BYTES = 48;

    NetworkEvent withIdentity(int serial, int hostTick) {
        return new NetworkEvent(serial, hostTick, kind, a, b, c, d, e, f, g, x, y, z, vx, vy, vz, quality, impact);
    }

    void write(ByteBuffer out) {
        out.putInt(id).putInt(tick).put((byte) kind.ordinal()).put((byte) a).put((byte) b).put((byte) c)
                .put((byte) d).put((byte) e).put((byte) f).put((byte) g);
        out.putFloat(x).putFloat(y).putFloat(z).putFloat(vx).putFloat(vy).putFloat(vz).putFloat(quality).putFloat(impact);
    }

    static NetworkEvent read(ByteBuffer in) {
        int id = in.getInt();
        int tick = in.getInt();
        Kind kind = NetworkSetup.readEnum(in, Kind.values());
        return new NetworkEvent(id, tick, kind, in.get(), in.get(), in.get(), in.get(), in.get(), in.get(), in.get(),
                finite(in), finite(in), finite(in), finite(in), finite(in), finite(in), finite(in), finite(in));
    }

    private static float finite(ByteBuffer in) {
        float value = in.getFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("event float");
        return value;
    }

    private static NetworkEvent simple(Kind kind, int a, int b, int c, int d, int e) {
        return new NetworkEvent(0, 0, kind, a, b, c, d, e, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static NetworkEvent serve(int id) { return simple(Kind.SERVE, id, 0, 0, 0, 0); }
    public static NetworkEvent ends(boolean swapped) { return simple(Kind.ENDS, swapped ? 1 : 0, 0, 0, 0, 0); }
    public static NetworkEvent timeout(CourtSide side, int used) { return simple(Kind.TIMEOUT, side.ordinal(), used, 0, 0, 0); }
    public static NetworkEvent substitution(CourtSide side, int out, int in, int used) {
        return simple(Kind.SUBSTITUTION, side.ordinal(), out, in, used, 0);
    }

    public static NetworkEvent point(MatchRules.PointSummary point) {
        var result = point.outcome();
        return simple(Kind.POINT, result.winner() == null ? -1 : result.winner().ordinal(), result.reason().ordinal(),
                result.playerId(), (point.sideOut() ? 1 : 0) | (point.setWon() ? 2 : 0) | (point.matchWon() ? 4 : 0),
                point.nextServingSide().ordinal());
    }

    public static NetworkEvent collision(BallContact hit) {
        var p = hit.position();
        return new NetworkEvent(0, 0, Kind.COLLISION, hit.kind().ordinal(), hit.playerId(), 0, 0, 0, 0, 0,
                p.x, p.y, p.z, 0, 0, 0, 0, hit.impactSpeed());
    }

    public static NetworkEvent touch(BallTouch touch) {
        var p = touch.contactPoint();
        var v = touch.outgoingVelocity();
        int variant = switch (touch.purpose()) {
            case SET -> indexOf(touch.variant(), SetType.values());
            case SERVE -> indexOf(touch.variant(), ServeType.values());
            case ATTACK -> indexOf(touch.variant(), AttackType.values());
            default -> -1;
        };
        int meta = touch.teamTouchNumber() | (touch.airborne() ? 16 : 0)
                | (touch.rating() == null ? 0 : (touch.rating().ordinal() + 1) << 5);
        return new NetworkEvent(0, 0, Kind.TOUCH, touch.playerId(), touch.side().ordinal(), touch.type().ordinal(),
                touch.purpose().ordinal(), meta, variant, touch.quickHitterId(), p.x, p.y, p.z, v.x, v.y, v.z,
                touch.quality(), touch.impactSpeed());
    }

    public BallTouch toTouch(GameWorld world) {
        var p = world.player(a);
        ContactPurpose purpose = ContactPurpose.values()[d];
        String variant = switch (purpose) {
            case SET -> f < 0 ? "" : label(SetType.values()[f]);
            case SERVE -> f < 0 ? "" : label(ServeType.values()[f]);
            case ATTACK -> f < 0 ? "" : label(AttackType.values()[f]);
            default -> "";
        };
        int rating = (e & 0xff) >>> 5;
        return new BallTouch(a, CourtSide.values()[b], ContactType.values()[c], purpose, quality,
                rating == 0 ? null : PassRating.values()[rating - 1], Float.NaN, new Vector3(x, y, z),
                p == null ? new Vector3(x, y, z) : p.position.cpy(), (e & 16) != 0, e & 15,
                new Vector3(vx, vy, vz), variant, tick / 120.0, impact, g);
    }

    public BallContact toCollision() {
        return new BallContact(ColliderKind.values()[a], b, new Vector3(x, y, z), impact, tick / 120.0);
    }

    public void presentMatch(GameWorld world, MatchController match) {
        switch (kind) {
            case SERVE -> match.presentServeWhistle(world.player(a));
            case POINT -> match.presentPoint(new MatchRules.PointSummary(
                    new RallyOutcome(a < 0 ? null : CourtSide.values()[a], Fault.values()[b], c, tick / 120.0),
                    (d & 1) != 0, (d & 2) != 0, (d & 4) != 0, CourtSide.values()[e]));
            case SUBSTITUTION -> match.presentSubstitution(CourtSide.values()[a], world.player(b), world.player(c), d);
            case TIMEOUT -> match.presentTimeout(CourtSide.values()[a], b);
            case ENDS -> match.presentChangeOfEnds(a != 0);
            default -> { }
        }
    }

    private static int indexOf(String sought, Enum<?>[] values) {
        for (int i = 0; i < values.length; i++) if (sought.startsWith(label(values[i]))) return i;
        return -1;
    }

    private static String label(Enum<?> e) {
        return switch (e) {
            case SetType set -> set.label;
            case ServeType serve -> serve.label;
            case AttackType attack -> attack.label;
            default -> throw new IllegalArgumentException("variant enum");
        };
    }
}
