package com.volleyball.game.ui;

import com.volleyball.game.entities.PlayerRole;

import java.util.List;

/**
 * Short guide for every role, shown when choosing the role and in the game (F1, pause menu).
 * The computer players follow the same guide (see {@code ai.RolePlan} and {@code ai.TeamAI}).
 */
public record RoleGuide(PlayerRole role, String title, String motto, List<Item> items) {
    public record Item(String topic, String text) {
    }

    public static final List<PlayerRole> ORDER = List.of(PlayerRole.OUTSIDE_HITTER, PlayerRole.SETTER, PlayerRole.OPPOSITE,
            PlayerRole.MIDDLE_BLOCKER, PlayerRole.LIBERO);

    public static RoleGuide of(PlayerRole role) {
        return switch (role) {
            case SETTER -> new RoleGuide(role, "Setter", "The playmaker. Every second ball is yours.", List.of(
                    new Item("Reception", "You never receive the serve. When the opponent serves, run to the setting spot: right of centre, a metre off the net."),
                    new Item("Second ball", "Set your hitters with the right mouse button (overhead). Number keys pick the set from the bar at the bottom - it shows which hitter gets each one; 0 = Auto chooses from the pass and the block."),
                    new Item("Attack", "In the front row a good pass lets you dump the second ball over: jump and play it with the right button - a soft lob just behind their block."),
                    new Item("Block", "Front row: block on the right side, against their outside hitter."),
                    new Item("Defense", "Back row: defend right back (zone 1), then hurry back to the net for the next set."),
                    new Item("Systems", "5-1: you set in every rotation. 4-2: you set in the front row. 6-2: you set from the back row and attack on the right in the front row.")));
            case OUTSIDE_HITTER -> new RoleGuide(role, "Outside hitter", "Receive, attack, never stop.", List.of(
                    new Item("Reception", "You are a main receiver: the ball coming down in your lane of the reception line is yours."),
                    new Item("Attack", "Front row: attack from the left (zone 4) on the high outside set. Back row: hit the pipe from behind the 3 m line."),
                    new Item("Block", "Front row: block on the left side and help the middle."),
                    new Item("Defense", "Back row: defend left back (zone 5): cross-court spikes come to you."),
                    new Item("Shots", "Aim with the mouse, pick the shot with the number keys: power, cut to the sharp angle, roll, tip over the block, tool off the block's outside hand. 0 = Auto reads the block for you.")));
            case OPPOSITE -> new RoleGuide(role, "Opposite", "The scorer on the right side.", List.of(
                    new Item("Reception", "You do not receive the serve: stay on the right, ready to attack."),
                    new Item("Attack", "Front row: attack from the right side (zone 2) on the back set. Back row: the D from behind the 3 m line in zone 1. When the pass is poor, the high ball is yours."),
                    new Item("Block", "Front row: block on the right, against their best outside hitter."),
                    new Item("Defense", "Back row: defend right back (zone 1)."),
                    new Item("Systems", "In a 4-2 or 6-2 the opposite's place goes to a second setter; you start on the bench and can come in (B).")));
            case MIDDLE_BLOCKER -> new RoleGuide(role, "Middle blocker", "The wall in the middle.", List.of(
                    new Item("Reception", "You do not receive the serve."),
                    new Item("Attack", "Front row: run the quick or the short set in zone 3, or the slide behind the setter - jump early, as the set leaves the setter - or jump as a decoy."),
                    new Item("Block", "Read the setter and slide along the net to every attack: you are in every block."),
                    new Item("Back row", "The libero takes your place in the back row. You come back to serve and when you rotate to the front."),
                    new Item("Control", "While the libero is in, you control the libero; you get your middle back when it returns.")));
            case LIBERO -> new RoleGuide(role, "Libero", "The defensive heart. Different shirt, no attacks.", List.of(
                    new Item("Reception", "You lead the reception: take every ball in your lane and help your neighbours."),
                    new Item("Defense", "Dig the spikes in the middle back (zone 6); dive with Ctrl + left button for balls out of reach."),
                    new Item("Second ball", "If the setter played the first ball, set with a forearm pass (left button)."),
                    new Item("Rules", "No attacks above the net, no blocks, no serves. You replace a middle blocker in the back row."),
                    new Item("Control", "While you are on the bench you play the middle blocker you replaced.")));
        };
    }
}
