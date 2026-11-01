package com.volleyball.game.screen;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.simulation.TeamCatalog;
import com.volleyball.game.training.Drill;

import java.util.Random;

/**
 * What the game screen starts with, chosen in the main menu (or on the command line).
 *
 * @param drill    the drill, or {@link Drill#MATCH} for a quick match
 * @param autoplay the computer plays the person's player (demo / spectator)
 * @param role     the role person 1 plays in the match
 * @param system   the teams' system (5-1, 4-2, 6-2)
 * @param homeTeam the person's team: a {@link TeamCatalog} id, {@link #RANDOM}, or null for the default
 * @param awayTeam the opponent, as {@code homeTeam}
 * @param firstServe who serves first
 * @param awayKit  the person's team plays in its away kit
 */
public record MatchSetup(Drill drill, boolean autoplay, Difficulty difficulty, SessionMode players, PlayerRole role,
                         MatchFormat format, OffenseSystem system, String homeTeam, String awayTeam,
                         com.volleyball.game.match.FirstServe firstServe, boolean awayKit) {
    /** A team drawn at random when the match starts. */
    public static final String RANDOM = "random";

    public MatchSetup(Drill drill, boolean autoplay, Difficulty difficulty, SessionMode players, PlayerRole role,
                      MatchFormat format, OffenseSystem system) {
        this(drill, autoplay, difficulty, players, role, format, system, null, null);
    }

    /** The person's team serves first, in its home kit. */
    public MatchSetup(Drill drill, boolean autoplay, Difficulty difficulty, SessionMode players, PlayerRole role,
                      MatchFormat format, OffenseSystem system, String homeTeam, String awayTeam) {
        this(drill, autoplay, difficulty, players, role, format, system, homeTeam, awayTeam,
                com.volleyball.game.match.FirstServe.YOU, false);
    }

    public static MatchSetup drill(Drill drill) {
        return new MatchSetup(drill, false, Difficulty.INTERMEDIATE, SessionMode.SINGLE, PlayerRole.OUTSIDE_HITTER,
                MatchFormat.official(), OffenseSystem.FIVE_ONE);
    }

    /**
     * The two clubs, home first: random choices are drawn now, and the two are always different
     * (a club picked twice meets a random opponent).
     */
    public TeamCatalog.Entry[] teams(Random random) {
        boolean homeRandom = RANDOM.equalsIgnoreCase(homeTeam);
        boolean awayRandom = RANDOM.equalsIgnoreCase(awayTeam);
        TeamCatalog.Entry home = homeRandom ? null : pick(homeTeam, TeamCatalog.defaultHome());
        TeamCatalog.Entry away = awayRandom ? null : pick(awayTeam, TeamCatalog.defaultAway());
        if (home == null) home = TeamCatalog.random(random, away);
        if (away == null || away == home) away = TeamCatalog.random(random, home);
        return new TeamCatalog.Entry[]{home, away};
    }

    private static TeamCatalog.Entry pick(String id, TeamCatalog.Entry fallback) {
        TeamCatalog.Entry e = TeamCatalog.byId(id);
        return e == null ? fallback : e;
    }
}
