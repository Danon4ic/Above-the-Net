package com.volleyball.game.mechanics;

/**
 * Attack shots. The aim (mouse) gives the direction; the shot gives the pace, the spin and the
 * flight. Each has its use: power through or past the block, the cut shot to the sharp angle,
 * the roll shot and the tip into empty zones, the tool off the blocker's hands, the feint that
 * looks like a full swing, and the wipe off the top of a tall block.
 */
public enum AttackType {
    /** Full arm swing with heavy topspin. */
    POWER("Power spike", "Power", "Full swing: fast and steep. Through or past the block."),
    /** Wrist turned at the contact: a sharp angle across the court, with sidespin, a little slower. */
    CUT("Cut shot", "Cut", "Wrist turned: a sharp angle cross-court with sidespin, past the block's inside hand."),
    /** Slower, looping shot with topspin to a deep or short gap. */
    ROLL_SHOT("Roll shot", "Roll", "Off-speed loop with topspin over the block into a gap (deep corner or the middle)."),
    /** Soft fingertip push over or behind the block into an empty spot (the setter's second-ball dump too). */
    TIP("Tip", "Tip", "Soft lob over the block, just behind it. Setters use it to dump the second ball."),
    /** Hit off the outside hand of the block, out of the court ("block-out"). */
    TOOL("Tool off the block", "Tool", "Hard and wide at the block's outside hand: off the hands and out. Without a block it is out!"),
    /** The full approach and arm swing of a spike, then a soft push: the defence was set for a hard hit. */
    FEINT("Feint", "Feint", "Full swing that turns into a soft push behind the block. Defenders set for a hard hit react late."),
    /** Hard and flat at the fingertips of the block: off the top and long, out of the court. */
    OFF_THE_TOP("Off the top of the block", "Top", "Hard at the block's fingertips: off the top and long. Without a block it is out!");

    public final String label;
    /** One word for the shot selector. */
    public final String shortLabel;
    public final String description;

    AttackType(String label, String shortLabel, String description) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.description = description;
    }

    public AttackType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public AttackType previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    /** The shot whose label is {@code label} (as reported in a ball touch), or null. */
    public static AttackType fromLabel(String label) {
        for (AttackType t : values()) if (t.label.equals(label)) return t;
        return null;
    }
}
