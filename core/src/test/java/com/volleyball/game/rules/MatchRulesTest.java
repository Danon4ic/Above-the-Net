package com.volleyball.game.rules;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.simulation.TeamFactory;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.Net;
import com.volleyball.game.world.NetCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.volleyball.game.world.CourtSide.AWAY;
import static com.volleyball.game.world.CourtSide.HOME;
import static org.junit.jupiter.api.Assertions.*;

class MatchRulesTest {
    private static MatchRules rules(MatchFormat format) {
        List<VolleyballPlayer> players = new ArrayList<>(TeamFactory.home(0).players());
        players.addAll(TeamFactory.away(6).players());
        RotationManager rotation = new RotationManager(new int[]{0, 1, 2, 3, 4, 5}, new int[]{6, 7, 8, 9, 10, 11});
        MatchRules rules = new MatchRules(format, rotation, players, new Net(NetCategory.MEN));
        rules.startMatch(HOME);
        return rules;
    }

    private static MatchRules.PointSummary point(MatchRules rules, CourtSide winner) {
        return rules.applyOutcome(new RallyOutcome(winner, Fault.BALL_IN, -1, 0));
    }

    @Test
    void sideOutGivesTheServeAndRotatesTheTeamThatWonIt() {
        MatchRules rules = rules(MatchFormat.official());
        assertEquals(0, rules.currentServer());
        MatchRules.PointSummary s = point(rules, HOME); // serving team scores: same server
        assertFalse(s.sideOut());
        assertEquals(0, rules.currentServer());
        assertArrayEquals(new int[]{6, 7, 8, 9, 10, 11}, rules.rotation().lineUp(AWAY));

        s = point(rules, AWAY); // receiving team wins the rally: side-out and rotation
        assertTrue(s.sideOut());
        assertEquals(AWAY, rules.servingSide());
        assertEquals(7, rules.currentServer(), "the zone 2 player rotated into zone 1");
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5}, rules.rotation().lineUp(HOME), "home did not rotate");
        assertEquals(1, rules.score().points(HOME));
        assertEquals(1, rules.score().points(AWAY));
    }

    @Test
    void replayChangesNothing() {
        MatchRules rules = rules(MatchFormat.official());
        rules.applyOutcome(new RallyOutcome(null, Fault.REPLAY, -1, 0));
        assertEquals(0, rules.score().points(HOME) + rules.score().points(AWAY));
        assertEquals(HOME, rules.servingSide());
    }

    @Test
    void newSetResetsTheLineUpsAndAlternatesTheFirstServer() {
        MatchRules rules = rules(new MatchFormat(2, 3, 3, 2, RuleMode.OFFICIAL, 8f));
        point(rules, AWAY); // side-out, away rotates
        point(rules, AWAY);
        MatchRules.PointSummary s = point(rules, AWAY);
        assertTrue(s.setWon());
        assertFalse(s.matchWon());
        assertEquals(AWAY, rules.servingSide(), "home served first in set 1, so away serves first in set 2");
        assertArrayEquals(new int[]{6, 7, 8, 9, 10, 11}, rules.rotation().lineUp(AWAY), "starting line-up again");
    }

    @Test
    void theDecidingSetStartsWithANewCoinToss() {
        java.util.Set<CourtSide> firstServers = java.util.EnumSet.noneOf(CourtSide.class);
        for (long seed = 0; seed < 16; seed++) {
            List<VolleyballPlayer> players = new ArrayList<>(TeamFactory.home(0).players());
            players.addAll(TeamFactory.away(6).players());
            RotationManager rotation = new RotationManager(new int[]{0, 1, 2, 3, 4, 5}, new int[]{6, 7, 8, 9, 10, 11});
            MatchRules rules = new MatchRules(new MatchFormat(2, 3, 3, 2, RuleMode.OFFICIAL, 8f), rotation, players,
                    new Net(NetCategory.MEN), seed);
            rules.startMatch(HOME);
            for (int i = 0; i < 3; i++) point(rules, HOME);
            for (int i = 0; i < 3; i++) point(rules, AWAY);
            assertEquals(3, rules.score().setNumber(), "the deciding set");
            firstServers.add(rules.servingSide());
        }
        assertEquals(2, firstServers.size(), "either team may win the toss: " + firstServers);
    }

    @Test
    void wholeMatchCanBePlayedFromFirstServeToLastSet() {
        MatchRules rules = rules(MatchFormat.official());
        java.util.Random random = new java.util.Random(4);
        int rallies = 0;
        while (!rules.score().isMatchOver()) {
            rules.startRally(rallies);
            point(rules, random.nextBoolean() ? HOME : AWAY);
            rallies++;
            assertTrue(rallies < 1000, "the match must end");
        }
        CourtSide winner = rules.score().matchWinner();
        assertEquals(3, rules.score().sets(winner));
        assertTrue(rules.score().sets(winner.opposite()) < 3);
        for (MatchScore.SetResult set : rules.score().finishedSets()) {
            int target = rules.format().pointsForSet(set.setNumber());
            int high = Math.max(set.home(), set.away());
            assertTrue(high >= target && Math.abs(set.home() - set.away()) >= 2, "set " + set);
        }
        assertThrows(IllegalStateException.class, () -> rules.startRally(0));
    }
}
