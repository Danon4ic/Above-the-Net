package com.volleyball.game.match;

import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** A whole match in the real world simulation, refereed by the rules, played by the computer. */
class MatchControllerTest {
    @Test
    void computerMatchIsPlayedFromTheFirstServeToTheLastSet() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 11);
        MatchFormat format = new MatchFormat(2, 7, 7, 2, RuleMode.OFFICIAL, 8f);
        MatchController match = new MatchController(world, format, 5);
        AtomicInteger attacks = new AtomicInteger();
        AtomicInteger serves = new AtomicInteger();
        world.ballHandling().addListener(t -> {
            if (t.type() == ContactType.ATTACK) attacks.incrementAndGet();
            if (t.type() == ContactType.SERVE) serves.incrementAndGet();
        });
        match.start(CourtSide.HOME);
        long steps = 0;
        while (match.phase() != MatchController.Phase.FINISHED) {
            world.step(1f / 120f);
            match.update(1f / 120f, List.of());
            assertTrue(++steps < 120L * 60 * 20, "the match must finish");
        }
        MatchScore score = match.rules().score();
        assertTrue(score.isMatchOver());
        CourtSide winner = score.matchWinner();
        assertEquals(2, score.sets(winner));
        int points = 0;
        for (MatchScore.SetResult set : score.finishedSets()) {
            assertTrue(Math.max(set.home(), set.away()) >= 7 && Math.abs(set.home() - set.away()) >= 2, set.toString());
            points += set.home() + set.away();
        }
        assertTrue(match.rallies() >= points, "every point came from a rally");
        assertTrue(serves.get() >= points * 0.8, "points are played after real serves");
        assertTrue(attacks.get() > 0, "rallies include real attacks");
    }
}
