package com.volleyball.game.rules;

import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static com.volleyball.game.world.CourtSide.AWAY;
import static com.volleyball.game.world.CourtSide.HOME;

class MatchScoreAndRotationTest {

    private static void points(MatchScore score, CourtSide side, int n) {
        for (int i = 0; i < n; i++) score.pointTo(side);
    }

    @Test
    void setEndsOnlyAt25WithATwoPointLead() {
        MatchScore score = new MatchScore(MatchFormat.official());
        points(score, HOME, 24);
        points(score, AWAY, 24);
        assertEquals(1, score.setNumber());
        assertFalse(score.pointTo(HOME).setWon(), "25:24 is not enough");
        assertFalse(score.pointTo(AWAY).setWon(), "25:25");
        assertFalse(score.pointTo(AWAY).setWon(), "25:26");
        MatchScore.PointResult r = score.pointTo(AWAY);
        assertTrue(r.setWon(), "25:27 ends the set");
        assertEquals(1, score.sets(AWAY));
        assertEquals(0, score.points(HOME));
        assertEquals(2, score.setNumber());
        assertEquals(27, score.finishedSets().get(0).away());
    }

    @Test
    void decidingSetIsPlayedTo15AndTheMatchEndsAtThreeSets() {
        MatchScore score = new MatchScore(MatchFormat.official());
        for (int set = 0; set < 2; set++) points(score, HOME, 25);
        for (int set = 0; set < 2; set++) points(score, AWAY, 25);
        assertEquals(5, score.setNumber());
        points(score, HOME, 14);
        assertTrue(score.isSetPoint(HOME));
        MatchScore.PointResult r = score.pointTo(HOME);
        assertTrue(r.matchWon());
        assertEquals(HOME, score.matchWinner());
        assertThrows(IllegalStateException.class, () -> score.pointTo(AWAY), "no points after the match");
    }

    @Test
    void customFormatIsRespected() {
        MatchScore score = new MatchScore(new MatchFormat(1, 5, 5, 2, RuleMode.SIMPLIFIED, 10f));
        points(score, AWAY, 4);
        assertFalse(score.isMatchOver());
        assertTrue(score.pointTo(AWAY).matchWon());
    }

    @Test
    void rotationIsClockwise() {
        RotationManager rotation = new RotationManager(new int[]{1, 2, 3, 4, 5, 6}, new int[]{11, 12, 13, 14, 15, 16});
        assertEquals(1, rotation.server(HOME));
        rotation.rotate(HOME);
        // zone 2 -> 1, 3 -> 2, ..., 1 -> 6
        assertArrayEquals(new int[]{2, 3, 4, 5, 6, 1}, rotation.lineUp(HOME));
        assertEquals(2, rotation.server(HOME));
        assertEquals(11, rotation.server(AWAY), "only the rotating team moves");
        assertTrue(rotation.isFrontRow(HOME, 3) && !rotation.isFrontRow(HOME, 1));
        for (int i = 0; i < 5; i++) rotation.rotate(HOME);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, rotation.lineUp(HOME), "six rotations = full circle");
        assertThrows(IllegalArgumentException.class, () -> new RotationManager(new int[]{1, 1, 2, 3, 4, 5}, new int[]{11, 12, 13, 14, 15, 16}));
    }
}
