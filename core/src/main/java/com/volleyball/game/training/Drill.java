package com.volleyball.game.training;

/** Repeatable practice situations, one per ball skill. */
public enum Drill {
    SERVE("Serve", "LMB: toss, LMB again to hit as the ball falls. Mouse: aim. Q/E: serve type. Jump serves: run (W) + Space."),
    RECEIVE("Serve receive", "Move under the serve (WASD), LMB shortly before it arrives. The pass goes to the setter."),
    SET("Setting", "Get under the pass, RMB (overhead) or LMB as it drops. Q/E: set type."),
    ATTACK("Attack", "Approach (W/D), Space to jump as the set drops, LMB to spike, RMB to tip. Mouse: aim. Q/E: shot."),
    BLOCK("Block", "Move along the net (A/D) in front of the hitter, Space to jump just after they jump. Q/E: block line/diagonal."),
    DEFENSE("Defense", "Read the hitter, move, LMB to dig. Ctrl+LMB while moving: dive for balls out of reach."),
    FREE("Free / ball lab", "Keys 1-8 launch test balls. Any action works on any ball."),
    MATCH("Quick match", "Official rules to 3 sets vs the AI (D: difficulty, P: 1 or 2 players). Tab: switch player, F10: watch.");

    public final String label;
    public final String instructions;

    Drill(String label, String instructions) {
        this.label = label;
        this.instructions = instructions;
    }
}
