package com.volleyball.game.render.anim;

import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.VolleyballPlayer;

/**
 * How a player goes down for a ball (the simulation decides that and where they dive; this is
 * how it looks). A defensive specialist throws the whole body flat and slides on the chest; other
 * players drop through a knee onto the floor and reach forwards; a dive far off the facing is a
 * sideways fall with a roll.
 */
public enum FallKind {
    /** Chest-first, flat and long: the libero's and the digger's dive. */
    SPRAWL(1f),
    /** Down through the front knee, the trunk forwards, the arms reaching for the floor. */
    KNEE_DROP(0.55f),
    /** Sideways onto the hip, the back and over the shoulder. */
    SIDE_ROLL(1f);

    /** Defence rating from which a player dives like a specialist. */
    public static final int SPECIALIST_DEFENCE = 70;

    /** How far the whole body tips over (1 = flat on the floor). */
    public final float tilt;

    FallKind(float tilt) {
        this.tilt = tilt;
    }

    /** The fall of {@code p}, decided when the dive starts. */
    public static FallKind of(VolleyballPlayer p) {
        if (p.isDiveRoll()) return SIDE_ROLL;
        boolean specialist = p.role == PlayerRole.LIBERO || p.has(PlayerTrait.DIGGER)
                || p.attributes.defense >= SPECIALIST_DEFENCE;
        return specialist ? SPRAWL : KNEE_DROP;
    }
}
