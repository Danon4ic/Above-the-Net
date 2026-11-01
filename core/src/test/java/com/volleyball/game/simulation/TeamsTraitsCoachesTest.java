package com.volleyball.game.simulation;

import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.PlayerTrait;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.match.SidelineCoaches;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.screen.MatchSetup;
import com.volleyball.game.training.Drill;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.CourtZones;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The team catalog, random teams, player traits and the coaches on the sideline. */
class TeamsTraitsCoachesTest {
    private static final float DT = 1f / 120f;

    @Test
    void everyClubBuildsAFullSquadWithTraitsThatFitTheRoles() {
        assertTrue(TeamCatalog.all().size() >= 8);
        Set<String> ids = new HashSet<>();
        for (TeamCatalog.Entry e : TeamCatalog.all()) {
            assertTrue(ids.add(e.id()), "unique ids");
            Team team = TeamFactory.build(e, CourtSide.HOME, 0, TeamCatalog.defaultAway());
            assertEquals(6, team.players().size());
            assertNotNull(team.libero());
            assertEquals(4, team.substitutes().size());
            assertEquals(e.coach(), team.coach());
            for (VolleyballPlayer p : team.squad()) {
                assertFalse(p.traits().isEmpty(), p.name + " has a trait");
                assertTrue(p.traits().size() <= 2);
                for (PlayerTrait a : p.traits()) {
                    for (PlayerTrait b : p.traits()) if (a != b) assertFalse(a.clashes(b), p.name + ": " + a + " and " + b);
                }
                if (p.role == PlayerRole.LIBERO) {
                    assertFalse(p.has(PlayerTrait.POWER_SERVER) || p.has(PlayerTrait.FLOAT_SPECIALIST) || p.has(PlayerTrait.HOTHEAD));
                }
            }
        }
    }

    @Test
    void theSameClubAlwaysHasTheSamePlayersAndTraits() {
        TeamCatalog.Entry e = TeamCatalog.byId("titans");
        Team a = TeamFactory.build(e, CourtSide.HOME, 0, TeamCatalog.defaultAway());
        Team b = TeamFactory.build(e, CourtSide.AWAY, 6, TeamCatalog.defaultHome());
        for (int i = 0; i < 6; i++) {
            assertEquals(a.players().get(i).traits(), b.players().get(i).traits());
            assertEquals(a.players().get(i).attributes.attackPower, b.players().get(i).attributes.attackPower);
        }
    }

    @Test
    void strongerClubsHaveBetterRatingsAndTraitsBoostThem() {
        Team strong = TeamFactory.build(TeamCatalog.byId("titans"), CourtSide.HOME, 0, TeamCatalog.defaultAway());
        Team weak = TeamFactory.build(TeamCatalog.byId("bears"), CourtSide.AWAY, 6, TeamCatalog.defaultHome());
        assertTrue(strong.players().get(3).attributes.attackPower > weak.players().get(3).attributes.attackPower);
        VolleyballPlayer p = new VolleyballPlayer(99, "T", 1, PlayerRole.OPPOSITE, CourtSide.HOME, 1.9f,
                new com.volleyball.game.entities.PlayerAttributes());
        int before = p.attributes.attackPower;
        p.addTrait(PlayerTrait.CANNON);
        assertEquals(before + 10, p.attributes.attackPower);
        assertEquals("Cannon", p.traitLabel());
    }

    @Test
    void randomTeamsAreAlwaysTwoDifferentClubsAndKitsDoNotClash() {
        MatchSetup setup = new MatchSetup(Drill.MATCH, false, Difficulty.INTERMEDIATE, null, PlayerRole.SETTER,
                MatchFormat.official(), null, MatchSetup.RANDOM, MatchSetup.RANDOM);
        Random random = new Random(5);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            TeamCatalog.Entry[] teams = setup.teams(random);
            assertNotSame(teams[0], teams[1]);
            seen.add(teams[0].id());
            Team home = TeamFactory.build(teams[0], CourtSide.HOME, 0, teams[1]);
            Team away = TeamFactory.build(teams[1], CourtSide.AWAY, 6, teams[0]);
            assertFalse(TeamCatalog.clash(home.shirtColor, away.shirtColor) && TeamCatalog.clash(teams[1].awayShirt(), home.shirtColor),
                    teams[0].name() + " vs " + teams[1].name());
        }
        assertEquals(TeamCatalog.all().size(), seen.size(), "every club comes up");
        // The same club picked twice meets someone else; no choice means the default pairing.
        TeamCatalog.Entry[] same = new MatchSetup(Drill.MATCH, false, Difficulty.INTERMEDIATE, null, PlayerRole.SETTER,
                MatchFormat.official(), null, "sharks", "sharks").teams(random);
        assertNotSame(same[0], same[1]);
        TeamCatalog.Entry[] defaults = MatchSetup.drill(Drill.SERVE).teams(random);
        assertSame(TeamCatalog.defaultHome(), defaults[0]);
        assertSame(TeamCatalog.defaultAway(), defaults[1]);
    }

    @Test
    void theCoachesPaceTheSidelineAndGoToTheTimeoutHuddle() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 7);
        MatchController match = new MatchController(world, new MatchFormat(1, 15, 15, 2, RuleMode.OFFICIAL, 8f), 7,
                Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
        SidelineCoaches coaches = new SidelineCoaches(world.home(), world.away(), 3);
        List<VolleyballPlayer> both = coaches.coaches();
        assertEquals("Igor Petrov", both.get(0).name);
        assertTrue(both.get(0).position.x < 0f && both.get(1).position.x > 0f, "each in front of their own bench");
        match.start(CourtSide.HOME);
        match.requestTimeout(CourtSide.HOME);
        boolean reacted = false;
        boolean huddled = false;
        float walked = 0f;
        for (int i = 0; i < 120 * 60 && !huddled; i++) {
            world.step(DT);
            match.update(DT, List.of());
            coaches.update(DT, match, world.ball());
            walked += both.get(0).position.dst(both.get(0).previousPosition);
            for (VolleyballPlayer c : both) {
                assertTrue(Math.abs(c.position.z) < CourtZones.BENCH_Z, "never on the bench");
                reacted |= c.actionPose() == ActionPose.CELEBRATE;
            }
            if (match.phase() == MatchController.Phase.TIMEOUT) {
                for (int k = 0; k < 120 * 5; k++) coaches.update(DT, match, world.ball());
                huddled = both.get(0).position.dst(-5.5f, 0f, 4.1f) < 0.3f && both.get(1).position.dst(5.5f, 0f, 4.1f) < 0.3f;
            }
        }
        assertTrue(huddled, "both coaches in the middle of their huddles");
        assertTrue(walked > 1f, "the coach moved along the line");
        assertTrue(reacted || match.rules().score().points(CourtSide.HOME) + match.rules().score().points(CourtSide.AWAY) <= 1,
                "a coach celebrated a point");
    }
}
