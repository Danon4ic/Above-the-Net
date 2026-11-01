package com.volleyball.game.tactics;

import java.util.EnumMap;

/**
 * The commands a team is playing with: at most one per group (offense, defense, serve), plus the
 * receiver a "serve at a receiver" command targets. Read by the team AI at every decision.
 */
public final class TeamTactics {
    private final EnumMap<TeamCommand.Group, TeamCommand> active = new EnumMap<>(TeamCommand.Group.class);
    /** Opponent player id for {@link TeamCommand#SERVE_RECEIVER} (-1: the weakest receiver). */
    private int serveTarget = -1;

    /** Activates a command (replacing the group's previous one). The same command again switches it off. */
    public void toggle(TeamCommand command) {
        if (active.get(command.group) == command && command != TeamCommand.SERVE_RECEIVER) active.remove(command.group);
        else active.put(command.group, command);
    }

    public void set(TeamCommand command) {
        active.put(command.group, command);
    }

    public void clear(TeamCommand.Group group) {
        active.remove(group);
    }

    public void clearAll() {
        active.clear();
        serveTarget = -1;
    }

    /** The group's active command, or null. */
    public TeamCommand active(TeamCommand.Group group) {
        return active.get(group);
    }

    public boolean is(TeamCommand command) {
        return active.get(command.group) == command;
    }

    public int serveTarget() {
        return serveTarget;
    }

    public void setServeTarget(int playerId) {
        serveTarget = playerId;
    }

    /** The defensive scheme the defense command asks for, or null (the team decides). */
    public DefenseScheme defenseScheme() {
        TeamCommand c = active.get(TeamCommand.Group.DEFENSE);
        if (c == null) return null;
        return switch (c) {
            case CLOSE_CROSS -> DefenseScheme.CLOSE_CROSS;
            case CLOSE_LINE -> DefenseScheme.CLOSE_LINE;
            case PLAY_DEEP -> DefenseScheme.DEEP;
            case PLAY_UP -> DefenseScheme.UP;
            case COVER_BLOCK -> DefenseScheme.COVER_BLOCK;
            case EXPECT_TIP -> DefenseScheme.ROTATIONAL;
            default -> null;
        };
    }
}
