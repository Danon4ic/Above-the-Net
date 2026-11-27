package com.volleyball.game.entities;

import java.util.List;

/**
 * A player's distinguishing trait: a strength (or a weakness) that changes both how well they
 * execute and what they choose to do. Every player has one or two (see {@code TeamFactory}).
 *
 * <ul>
 *   <li>ratings: a small boost of the matching ratings, applied when the squad is built;</li>
 *   <li>execution: e.g. a float specialist's serves wander less, a clutch player plays better at
 *       set point and a nervous one worse, a workhorse tires slower;</li>
 *   <li>decisions (computer players): the cannon hits hard, the crafty hitter tips and rolls into
 *       the gaps, the line hitter goes down the line, the hothead aims at the lines, the tempo
 *       setter runs fast plays, the wall never jumps with a decoy, the reader moves early.</li>
 * </ul>
 */
public enum PlayerTrait {
    POWER_SERVER("Power server", "Jump serves hard and often.", true),
    FLOAT_SPECIALIST("Float specialist", "Placed floats that wander less and dip late.", true),
    CANNON("Cannon", "Hits as hard as anyone; rarely tips.", true),
    CRAFTY("Crafty", "Tips, rolls and cuts into the open spots.", true),
    LINE_HITTER("Line hitter", "Loves the line shot.", true),
    CROSS_HITTER("Cross hitter", "Goes across the court.", true),
    WALL("Wall", "Big block; never jumps with a decoy.", true),
    READER("Reader", "Reads the play and moves first.", true),
    DIGGER("Digger", "Digs everything: quick on the floor.", true),
    CLUTCH("Clutch", "Plays better when the set is on the line.", true),
    WORKHORSE("Workhorse", "Tires much slower.", true),
    TEMPO_SETTER("Tempo setter", "Runs fast plays: quicks, shoots, slides.", true),
    HIGH_BALL_SETTER("High-ball setter", "Steady high sets that are easy to hit.", true),
    NERVOUS("Nervous", "Plays worse at set point.", false),
    HOTHEAD("Hothead", "Goes for the lines - aces and kills, and errors.", false),
    BLOCK_OUT("Block-out specialist", "Uses the block's hands to send the ball out.", true),
    HARD_RECEIVER("Heavy-ball receiver", "Absorbs hard serves and spikes with the forearms.", true),
    BACK_SETTER("Back-set specialist", "Places back sets and slides more precisely.", true),
    LATE_BLOCKER("Late hands", "Keeps pressing over the net while coming down.", true),
    QUICK_RECOVERY("Quick recovery", "Regains stamina faster while resting.", true),
    EXPLOSIVE("Explosive", "Accelerates and jumps better but spends more stamina on bursts.", false);

    public final String label;
    public final String description;
    /** True for a strength, false for a mixed blessing or a weakness. */
    public final boolean positive;

    PlayerTrait(String label, String description, boolean positive) {
        this.label = label;
        this.description = description;
        this.positive = positive;
    }

    /** Raises the matching ratings (once, when the squad is built). */
    public void boost(PlayerAttributes a) {
        switch (this) {
            case POWER_SERVER -> a.serving += 10;
            case FLOAT_SPECIALIST -> a.serving += 6;
            case CANNON -> a.attackPower += 10;
            case CRAFTY -> {
                a.attackAccuracy += 8;
                a.tactics += 8;
            }
            case LINE_HITTER, CROSS_HITTER -> a.attackAccuracy += 4;
            case WALL -> a.blocking += 10;
            case READER -> {
                a.reaction += 8;
                a.tactics += 6;
            }
            case DIGGER -> {
                a.defense += 10;
                a.reaction += 4;
            }
            case CLUTCH -> a.composure += 12;
            case WORKHORSE -> a.stamina += 15;
            case TEMPO_SETTER -> a.setting += 6;
            case HIGH_BALL_SETTER -> a.setting += 4;
            case NERVOUS -> a.composure -= 15;
            case HOTHEAD -> {
                a.attackPower += 4;
                a.composure -= 6;
            }
            case BLOCK_OUT -> a.attackAccuracy += 3;
            case HARD_RECEIVER -> a.receiving += 4;
            case BACK_SETTER -> a.setting += 3;
            case LATE_BLOCKER -> a.blocking += 4;
            case QUICK_RECOVERY -> { } // Recovery rate changes directly.
            case EXPLOSIVE -> {
                a.acceleration += 8;
                a.jump += 8;
            }
        }
        a.sanitize();
    }

    /** The traits a player of {@code role} may draw (the first trait comes from here). */
    public static List<PlayerTrait> forRole(PlayerRole role) {
        return switch (role) {
            case SETTER -> List.of(TEMPO_SETTER, HIGH_BALL_SETTER, BACK_SETTER, FLOAT_SPECIALIST, READER, CLUTCH);
            case OUTSIDE_HITTER -> List.of(CANNON, CRAFTY, CROSS_HITTER, LINE_HITTER, POWER_SERVER, DIGGER,
                    CLUTCH, BLOCK_OUT, HARD_RECEIVER);
            case OPPOSITE -> List.of(CANNON, LINE_HITTER, POWER_SERVER, HOTHEAD, CLUTCH, CRAFTY, BLOCK_OUT, EXPLOSIVE);
            case MIDDLE_BLOCKER -> List.of(WALL, READER, FLOAT_SPECIALIST, CRAFTY, WORKHORSE, LATE_BLOCKER, EXPLOSIVE);
            case LIBERO -> List.of(DIGGER, READER, WORKHORSE, CLUTCH, HARD_RECEIVER, QUICK_RECOVERY);
        };
    }

    /** Traits any player may draw as a second one. */
    public static final List<PlayerTrait> GENERAL = List.of(CLUTCH, NERVOUS, HOTHEAD, WORKHORSE, READER, POWER_SERVER,
            FLOAT_SPECIALIST, QUICK_RECOVERY);

    /** Whether the two traits contradict each other (never on the same player). */
    public boolean clashes(PlayerTrait other) {
        return this == other
                || pair(other, CLUTCH, NERVOUS) || pair(other, LINE_HITTER, CROSS_HITTER)
                || pair(other, POWER_SERVER, FLOAT_SPECIALIST) || pair(other, TEMPO_SETTER, HIGH_BALL_SETTER)
                || pair(other, CANNON, CRAFTY) || pair(other, WORKHORSE, EXPLOSIVE);
    }

    private boolean pair(PlayerTrait other, PlayerTrait x, PlayerTrait y) {
        return (this == x && other == y) || (this == y && other == x);
    }
}
