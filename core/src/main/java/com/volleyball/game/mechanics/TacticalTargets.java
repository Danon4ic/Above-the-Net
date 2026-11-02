package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.world.CourtSide;

/** Standard target points, expressed in a team's own frame and converted to world coordinates. */
public final class TacticalTargets {
    /** Classic setter target: 1 m off the net, between zones 2 and 3, at the setter's hands. */
    public static final float SETTER_DEPTH = 1.0f;
    public static final float SETTER_LATERAL = 1.0f;
    public static final float SETTER_HEIGHT = 2.45f;
    /** Peak height of a first-touch pass: high enough for the setter to get under it. */
    public static final float PASS_APEX = 4.8f;
    /**
     * Digging a hard attack: the aim is to keep the ball up high in the middle of the own court,
     * not to hit the setter target exactly.
     */
    public static final float DIG_DEPTH = 3.0f;
    public static final float DIG_HEIGHT = 2.6f;
    public static final float DIG_APEX = 5.0f;
    /** Incoming speed above which a first touch is treated as a dig. */
    public static final float DIG_SPEED = 14f;

    public static Vector3 digTarget(CourtSide side, Vector3 out) {
        return teamPoint(side, DIG_DEPTH, 0.5f, DIG_HEIGHT, out);
    }

    /** Peak height of a free ball sent over on the third touch. */
    public static final float FREE_BALL_APEX = 4.6f;

    private TacticalTargets() {}

    /**
     * World point for a team on {@code side}: {@code depth} metres from the net into its own court,
     * {@code lateral} metres to the team's right (a team faces the net), at {@code height}.
     */
    public static Vector3 teamPoint(CourtSide side, float depth, float lateral, float height, Vector3 out) {
        // HOME faces +X so its right is +Z; AWAY faces -X so its right is -Z.
        return out.set(side.sign() * depth, height, -side.sign() * lateral);
    }

    public static Vector3 setterTarget(CourtSide side, Vector3 out) {
        return teamPoint(side, SETTER_DEPTH, SETTER_LATERAL, SETTER_HEIGHT, out);
    }

    /**
     * Where the setter's own first pass goes: high into the middle, about 3 m off the net, so the
     * libero (who sets the second ball then) can come in under it.
     */
    public static Vector3 backupSetterTarget(CourtSide side, Vector3 out) {
        return teamPoint(side, BACKUP_SETTER_DEPTH, 0f, SETTER_HEIGHT, out);
    }

    static final float BACKUP_SETTER_DEPTH = 3.0f;

    /** Default spot for serves and free balls: deep middle of the opponent's court. */
    public static Vector3 defaultAttackTarget(CourtSide attackingSide, Vector3 out) {
        return teamPoint(attackingSide.opposite(), 6.5f, 0f, 0f, out);
    }
}
