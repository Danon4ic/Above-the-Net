package com.volleyball.game.match;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.rules.Fault;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.RallyOutcome;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The teams change ends between sets (not before the decider) and at half of the deciding set; the referee signals. */
class EndsAndRefereeTest {
    private static final float DT = 1f / 120f;

    @Test
    void theTeamsChangeEndsAfterTheFirstSetAndAtEightInTheDecider() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 12);
        MatchController match = new MatchController(world, new MatchFormat(2, 5, 5, 2, RuleMode.OFFICIAL, 8f), 12,
                Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
        List<String> changes = new ArrayList<>();
        match.addListener(new MatchListener() {
            @Override
            public void onChangeOfEnds(boolean swapped) {
                var score = match.rules().score();
                changes.add(score.setNumber() + ":" + score.points(CourtSide.HOME) + "-" + score.points(CourtSide.AWAY) + (swapped ? "S" : "N"));
            }
        });
        match.start(CourtSide.HOME);
        boolean sawSwappedBench = false;
        for (long i = 0; match.phase() != MatchController.Phase.FINISHED && i < 120L * 60 * 30; i++) {
            world.step(DT);
            match.update(DT, List.of());
            if (match.sidesSwapped() && match.phase() == MatchController.Phase.RALLY) {
                assertEquals(-1f, world.benchSign());
                for (VolleyballPlayer p : world.home().squad()) if (!p.isOnCourt()) sawSwappedBench |= p.position.z < 0f;
            }
        }
        assertTrue(match.phase() == MatchController.Phase.FINISHED);
        int sets = match.rules().score().finishedSets().size();
        assertTrue(sawSwappedBench, "the benches went with the teams");
        assertTrue(changes.get(0).startsWith("2:0-0"), "a change after the first set: " + changes);
        // No change before the deciding set (rule 18.2.1), one at its half.
        if (sets == 3) assertEquals(2, changes.size(), "and at half of the deciding set: " + changes);
        match.start(CourtSide.HOME);
        assertFalse(match.sidesSwapped());
        assertEquals(1f, world.benchSign());
    }

    @Test
    void theRefereeShowsTheFaultThenTheTeamThatServes() {
        Referee referee = new Referee();
        VolleyballPlayer r = referee.figure();
        assertTrue(r.position.y > 1f, "on the stand");
        RallyOutcome out = new RallyOutcome(CourtSide.AWAY, Fault.BALL_OUT, 1, 0);
        referee.onPoint(new MatchRules.PointSummary(out, true, false, false, CourtSide.AWAY), null);
        assertEquals(ActionPose.REF_OUT, r.actionPose());
        for (int i = 0; i < 160; i++) referee.update(DT, 1f);
        // Facing +z, the left arm points to the away half (+x).
        assertEquals(ActionPose.REF_POINT_LEFT, r.actionPose());
        referee.onTimeout(CourtSide.HOME, 1);
        assertEquals(ActionPose.REF_TIMEOUT, r.actionPose());
        // After the change of ends the referee's stand is on the other side.
        referee.update(DT, -1f);
        assertTrue(r.position.z > 0f);
    }
}
