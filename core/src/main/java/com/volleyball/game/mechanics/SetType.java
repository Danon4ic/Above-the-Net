package com.volleyball.game.mechanics;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.world.CourtSide;

/**
 * Sets. Each one delivers the ball to an attacker's hitting spot, given in the setting team's
 * own frame (depth from the net, lateral position with + = right), with a peak height that
 * defines the tempo: a quick set barely rises, a high outside set climbs to ~6.5 m. The lane
 * says which attacker the set is for.
 */
public enum SetType {
    //              label                        short     lane              depth lateral height apex
    HIGH_OUTSIDE("High set to zone 4",          "High 4",  Lane.OUTSIDE,      1.15f, -3.6f, 3.25f, 6.5f),
    SHOOT("Shoot to zone 4",                    "Shoot 4", Lane.OUTSIDE,      0.8f, -3.3f, 3.15f, 4.4f),
    QUICK_MIDDLE("Quick to zone 3",             "Quick",   Lane.MIDDLE,       0.7f,  0.0f, 3.20f, 3.7f),
    SHORT_MIDDLE("Short set to zone 3",         "Short 3", Lane.MIDDLE,       0.8f, -0.9f, 3.20f, 4.7f),
    SLIDE("Slide behind the setter",            "Slide",   Lane.MIDDLE,       0.6f,  2.4f, 3.15f, 4.0f),
    BACK_SET("Back set to zone 2",              "Back 2",  Lane.RIGHT,        1.1f,   3.6f, 3.25f, 5.6f),
    PIPE("Pipe from zone 6",                    "Pipe",    Lane.BACK_MIDDLE,  3.6f,  0.0f, 3.15f, 5.6f),
    BIC("Bic: quick back-row attack",            "Bic",     Lane.BACK_MIDDLE,  3.3f, -0.7f, 3.10f, 4.3f),
    BACK_ROW_RIGHT("Back-row attack from zone 1", "D 1",   Lane.BACK_RIGHT,   3.6f,  3.0f, 3.15f, 5.8f);

    /** Who the set is for. */
    public enum Lane {
        /** The front-row hitter in zone 4 (outside hitter). */
        OUTSIDE,
        /** The front-row middle blocker. */
        MIDDLE,
        /** The front-row right-side hitter (opposite, or the second setter in a 4-2 / 6-2). */
        RIGHT,
        /** A back-row hitter from behind the attack line in zone 6. */
        BACK_MIDDLE,
        /** A back-row hitter from behind the attack line in zone 1 (usually the opposite). */
        BACK_RIGHT
    }

    public final String label;
    /** A word or two for the set selector. */
    public final String shortLabel;
    public final Lane lane;
    public final float depth;
    public final float lateral;
    public final float height;
    public final float apex;

    SetType(String label, String shortLabel, Lane lane, float depth, float lateral, float height, float apex) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.lane = lane;
        this.depth = depth;
        this.lateral = lateral;
        this.height = height;
        this.apex = apex;
    }

    /** Hitting spot in world coordinates for a team playing on {@code side}. */
    public Vector3 target(CourtSide side, Vector3 out) {
        return TacticalTargets.teamPoint(side, depth, lateral, height, out);
    }

    /** A fast set (quick, shoot, slide): the hitter is in the air as the ball arrives. */
    public boolean fast() {
        return apex < 4.5f;
    }

    public SetType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public SetType previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    /** The set whose label is {@code label} (as reported in a ball touch), or null. */
    public static SetType fromLabel(String label) {
        for (SetType t : values()) if (t.label.equals(label)) return t;
        return null;
    }
}
