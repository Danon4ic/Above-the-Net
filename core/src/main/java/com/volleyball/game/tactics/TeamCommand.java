package com.volleyball.game.tactics;

/**
 * Tactical commands a person gives the own team. Each changes what the computer players do (the
 * plays the setter calls, how the hitters attack, where the defenders stand, how the servers
 * serve); commands of one group replace each other.
 */
public enum TeamCommand {
    // Offense
    QUICK_ATTACK(Group.OFFENSE, "Quick attack", "Play the first tempo: quick sets to the middle."),
    HIGH_OUTSIDE(Group.OFFENSE, "High ball to the outside", "Safe high sets to the outside hitter."),
    ZONE_TWO(Group.OFFENSE, "Attack through zone 2", "Sets to the right side and the D from zone 1."),
    PIPE(Group.OFFENSE, "Pipe", "Use the back-row attack from zone 6 (pipe, bic)."),
    DECOY(Group.OFFENSE, "Decoy plays", "Decoys, cross runs, tandems: pull the block with the middle."),
    VS_SINGLE_BLOCK(Group.OFFENSE, "Attack the single block", "Set away from the blockers; hitters go hard at a lone block."),
    CAUTIOUS(Group.OFFENSE, "Careful after a poor pass", "No risk: high balls, controlled shots, keep the ball in play."),
    // Defense
    CLOSE_CROSS(Group.DEFENSE, "Close the diagonal", "Block the cross court; dig the line."),
    CLOSE_LINE(Group.DEFENSE, "Close the line", "Block the line; dig the cross court."),
    PLAY_DEEP(Group.DEFENSE, "Play deeper", "Defenders back, against hard hitting."),
    PLAY_UP(Group.DEFENSE, "Move up to the net", "Defenders up, against off-speed shots."),
    COVER_BLOCK(Group.DEFENSE, "Cover the block", "A defender right behind the block for deflections."),
    EXPECT_TIP(Group.DEFENSE, "Expect the tip", "Guard the short zone behind the block."),
    // Serve
    SERVE_ZONE_1(Group.SERVE, "Serve to zone 1", "Deep right of their court."),
    SERVE_ZONE_5(Group.SERVE, "Serve to zone 5", "Deep left of their court."),
    SERVE_ZONE_6(Group.SERVE, "Serve to zone 6", "Deep middle."),
    SERVE_RECEIVER(Group.SERVE, "Serve at a receiver", "At one receiver (press again for the next one)."),
    POWER_SERVE(Group.SERVE, "Risky power serves", "Hard topspin serves: more aces, more errors."),
    SAFE_SERVE(Group.SERVE, "Safe serves", "Floats with a margin: no free points."),
    SHORT_SERVE(Group.SERVE, "Short serves", "Short floats in front of the receivers.");

    /** Where a command belongs; commands of a group replace each other. */
    public enum Group {
        OFFENSE("Offense"), DEFENSE("Defense"), SERVE("Serve");

        public final String label;

        Group(String label) {
            this.label = label;
        }

        public Group next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public final Group group;
    public final String label;
    public final String description;

    TeamCommand(Group group, String label, String description) {
        this.group = group;
        this.label = label;
        this.description = description;
    }

    /** The commands of a group, in key order (key 1 = the first). */
    public static java.util.List<TeamCommand> of(Group group) {
        java.util.List<TeamCommand> list = new java.util.ArrayList<>();
        for (TeamCommand c : values()) if (c.group == group) list.add(c);
        return list;
    }
}
