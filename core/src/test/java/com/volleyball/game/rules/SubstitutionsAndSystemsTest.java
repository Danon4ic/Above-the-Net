package com.volleyball.game.rules;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.ai.OffenseSystem;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.match.MatchListener;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.multiplayer.MatchSession;
import com.volleyball.game.multiplayer.SessionMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regular substitutions (rule 15) and the 5-1 / 4-2 / 6-2 systems. */
class SubstitutionsAndSystemsTest {
    static final float DT = 1f / 120f;
    static final MatchFormat SHORT = new MatchFormat(1, 15, 15, 2, RuleMode.OFFICIAL, 8f);

    private static RotationManager rotation() {
        return new RotationManager(new int[]{0, 1, 2, 3, 4, 5}, new int[]{6, 7, 8, 9, 10, 11});
    }

    @Test
    void starterLeavesOnceAndReturnsOnlyForTheirSubstitute() {
        RotationManager r = rotation();
        SubstitutionManager subs = new SubstitutionManager();
        subs.newSet(r);
        CourtSide home = CourtSide.HOME;
        assertEquals(SubstitutionManager.Result.OK, subs.substitute(home, 3, 13, r, 12));
        assertEquals(4, r.slotZone(home, 13), "the substitute takes the starter's place in the rotation");
        assertEquals(-1, r.slotZone(home, 3));
        assertEquals(SubstitutionManager.Result.WRONG_PARTNER, subs.check(home, 13, 14, r, 12), "only the starter can replace them");
        assertEquals(SubstitutionManager.Result.OK, subs.substitute(home, 13, 3, r, 12), "the starter comes back to the same place");
        assertEquals(4, r.slotZone(home, 3));
        assertEquals(SubstitutionManager.Result.STARTER_ALREADY_OUT, subs.check(home, 3, 14, r, 12), "a starter leaves only once");
        assertEquals(SubstitutionManager.Result.SUBSTITUTE_USED, subs.check(home, 1, 13, r, 12), "a substitute enters only once");
        assertEquals(SubstitutionManager.Result.LIBERO, subs.check(home, 1, 12, r, 12));
        assertEquals(SubstitutionManager.Result.ALREADY_ON_COURT, subs.check(home, 1, 2, r, 12));
        assertEquals(2, subs.used(home));
    }

    @Test
    void sixSubstitutionsPerSetThenANewSetStartsAgain() {
        RotationManager r = rotation();
        SubstitutionManager subs = new SubstitutionManager();
        subs.newSet(r);
        for (int i = 0; i < 3; i++) {
            assertEquals(SubstitutionManager.Result.OK, subs.substitute(CourtSide.HOME, i, 13 + i, r, 12));
            assertEquals(SubstitutionManager.Result.OK, subs.substitute(CourtSide.HOME, 13 + i, i, r, 12));
        }
        assertEquals(SubstitutionManager.Result.LIMIT, subs.check(CourtSide.HOME, 4, 16, r, 12));
        assertEquals(0, subs.used(CourtSide.AWAY), "counted per team");
        r.reset();
        subs.newSet(r);
        assertEquals(SubstitutionManager.Result.OK, subs.check(CourtSide.HOME, 4, 16, r, 12));
    }

    @Test
    void theLiberoFollowsASubstitutedMiddle() {
        RotationManager r = rotation();
        SubstitutionManager subs = new SubstitutionManager();
        subs.newSet(r);
        r.replaceWithLibero(CourtSide.HOME, 12, 5);
        subs.substitute(CourtSide.HOME, 5, 15, r, 12);
        assertEquals(15, r.replacedByLibero(CourtSide.HOME));
        assertEquals(12, r.playerInZone(CourtSide.HOME, 6));
    }

    @ParameterizedTest
    @EnumSource(OffenseSystem.class)
    void everySystemPlaysASetAndTheRightSetterSets(OffenseSystem system) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        MatchController match = new MatchController(world, SHORT, 3, Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE, system);
        int[] sets = new int[1];
        int[] wrongSetter = new int[1];
        int[] setterAttacks = new int[1];
        world.ballHandling().addListener(t -> {
            VolleyballPlayer p = world.player(t.playerId());
            if (t.purpose() == ContactPurpose.SET && p.role == PlayerRole.SETTER) {
                sets[0]++;
                boolean front = match.rules().rotation().isFrontRow(p.side, p.id);
                if (system == OffenseSystem.FOUR_TWO && !front || system == OffenseSystem.SIX_TWO && front) wrongSetter[0]++;
            }
            if (t.type() == ContactType.ATTACK && p.role == PlayerRole.SETTER) setterAttacks[0]++;
        });
        match.start(CourtSide.HOME);
        for (long steps = 0; match.phase() != MatchController.Phase.FINISHED; steps++) {
            world.step(DT);
            match.update(DT, List.of());
            if (match.phase() == MatchController.Phase.RALLY && system.twoSetters()) {
                for (Team team : List.of(world.home(), world.away())) {
                    long setters = team.onCourt().stream().filter(p -> p.role == PlayerRole.SETTER).count();
                    assertEquals(2, setters, "two setters on court in a " + system.label);
                }
            }
            assertTrue(steps < 120L * 60 * 15);
        }
        assertTrue(sets[0] > 5, "setters set: " + sets[0]);
        assertTrue(wrongSetter[0] <= sets[0] * 0.15f, wrongSetter[0] + " of " + sets[0] + " sets by the wrong setter");
        if (system == OffenseSystem.SIX_TWO) assertTrue(setterAttacks[0] > 0, "in a 6-2 the front-row setter attacks");
    }

    @Test
    void theCoachSubstitutesAfterErrorsWithinTheRules() {
        int made = 0;
        for (long seed = 1; seed <= 12 && made == 0; seed++) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), seed * 5);
            MatchController match = new MatchController(world, new MatchFormat(2, 15, 15, 2, RuleMode.OFFICIAL, 8f), seed,
                    Difficulty.BEGINNER, Difficulty.BEGINNER);
            VolleyballPlayer human = world.home().players().get(1);
            List<String> subs = new ArrayList<>();
            match.addListener(new MatchListener() {
                @Override
                public void onSubstitution(CourtSide side, VolleyballPlayer out, VolleyballPlayer in, int used) {
                    assertNotSame(human, out, "the person's player is never taken out by the coach");
                    assertEquals(out.role, in.role, "same role");
                    assertTrue(used <= SubstitutionManager.MAX_PER_SET);
                    subs.add(side + " " + out.number + "->" + in.number);
                }
            });
            match.start(CourtSide.HOME);
            for (long steps = 0; match.phase() != MatchController.Phase.FINISHED && steps < 120L * 60 * 30; steps++) {
                world.step(DT);
                match.update(DT, List.of(human));
                if (match.phase() == MatchController.Phase.RALLY) {
                    assertEquals(6, world.home().onCourt().size());
                    assertEquals(6, world.away().onCourt().size());
                }
            }
            made += subs.size();
        }
        assertTrue(made > 0, "beginners make errors, the coach reacts");
    }

    @Test
    void aPersonSubstitutedOutPlaysTheSubstitute() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 2);
        MatchController match = new MatchController(world, SHORT, 2, Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
        MatchSession session = MatchSession.create(SessionMode.SINGLE, world, PlayerRole.OUTSIDE_HITTER);
        VolleyballPlayer starter = session.slot(0).player();
        VolleyballPlayer substitute = world.home().substitutes().stream().filter(p -> p.role == PlayerRole.OUTSIDE_HITTER)
                .findFirst().orElseThrow();
        match.start(CourtSide.HOME);
        assertEquals(SubstitutionManager.Result.OK, match.requestSubstitution(CourtSide.HOME, starter, substitute));
        assertEquals(1, match.pendingSubstitutions(CourtSide.HOME));
        world.step(DT);
        match.update(DT, session.humans()); // the next serve: the substitution is made
        session.followReplacements(match.rules().rotation(), match.rules().substitutions());
        assertFalse(starter.isOnCourt());
        assertTrue(substitute.isOnCourt());
        assertSame(substitute, session.slot(0).player());
        assertEquals(1, match.rules().substitutions().used(CourtSide.HOME));
    }
}
