package com.volleyball.game.ai;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.tactics.Combination;
import com.volleyball.game.tactics.Playbook;
import com.volleyball.game.tactics.TeamCommand;
import com.volleyball.game.tactics.TeamTactics;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Stage 8: attacking combinations, defensive schemes and team commands. */
class CombinationsAndTacticsTest {
    private static final float DT = 1f / 120f;
    private static final MatchFormat SHORT = new MatchFormat(1, 15, 15, 2, RuleMode.OFFICIAL, 8f);

    /** Every lane has a hitter (the players' identities do not matter for calling a play). */
    private static EnumMap<SetType.Lane, VolleyballPlayer> allLanes(GameWorld world) {
        EnumMap<SetType.Lane, VolleyballPlayer> lanes = new EnumMap<>(SetType.Lane.class);
        List<VolleyballPlayer> players = world.home().players();
        int i = 0;
        for (SetType.Lane lane : SetType.Lane.values()) lanes.put(lane, players.get(1 + i++));
        return lanes;
    }

    private static Map<String, Integer> callMany(Difficulty difficulty, TeamTactics tactics, PassRating pass, boolean setterFront, int n) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        PlayCaller caller = new PlayCaller(CourtSide.HOME, new Random(9));
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Combination c = caller.call(difficulty, Playbook.standard(), tactics, allLanes(world), pass, setterFront, List.of());
            counts.merge(c.id(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void everyBuiltInPlayHasTheSpecifiedParts() {
        assertTrue(Playbook.builtIn().size() >= 13);
        for (Combination c : Playbook.builtIn()) {
            assertFalse(c.name().isBlank());
            assertFalse(c.options().isEmpty(), c.name() + ": a set and a hitter");
            assertNotNull(c.minPass(), c.name() + ": preconditions");
            assertTrue(c.predictedZone() >= 1 && c.predictedZone() <= 6);
            if (!c.emergency()) assertFalse(c.runs().isEmpty(), c.name() + ": positions and approach paths");
        }
    }

    @Test
    void aPoorPassLeavesOnlyTheSafePlays() {
        Map<String, Integer> calls = callMany(Difficulty.PROFESSIONAL, null, PassRating.EMERGENCY, true, 200);
        for (String id : calls.keySet()) {
            Combination c = Playbook.standard().find(id);
            assertTrue(c.minPass() == PassRating.EMERGENCY || c.emergency(), "after a poor pass: " + id);
        }
    }

    @Test
    void beginnersOnlyKnowTheHighBallAndProfessionalsUseTheWholePlaybook() {
        Map<String, Integer> beginner = callMany(Difficulty.BEGINNER, null, PassRating.PERFECT, true, 200);
        for (String id : beginner.keySet()) {
            assertTrue(id.equals(Playbook.HIGH_OUTSIDE) || id.equals(Playbook.ZONE_TWO), "beginner: " + id);
        }
        Map<String, Integer> pro = callMany(Difficulty.PROFESSIONAL, null, PassRating.PERFECT, true, 400);
        assertTrue(pro.size() >= 9, "professionals vary their plays: " + pro.keySet());
        assertTrue(pro.containsKey(Playbook.DECOY) || pro.containsKey(Playbook.CROSS) || pro.containsKey(Playbook.TANDEM));
    }

    @Test
    void aCommandChangesThePlaysTheSetterCalls() {
        TeamTactics quick = new TeamTactics();
        quick.set(TeamCommand.QUICK_ATTACK);
        Map<String, Integer> withCommand = callMany(Difficulty.INTERMEDIATE, quick, PassRating.PERFECT, false, 300);
        Map<String, Integer> without = callMany(Difficulty.INTERMEDIATE, null, PassRating.PERFECT, false, 300);
        int a = withCommand.getOrDefault(Playbook.FIRST_TEMPO, 0);
        int b = without.getOrDefault(Playbook.FIRST_TEMPO, 0);
        assertTrue(a > b * 2, "quick attacks: " + a + " with the command, " + b + " without");
        TeamTactics careful = new TeamTactics();
        careful.set(TeamCommand.CAUTIOUS);
        for (String id : callMany(Difficulty.PROFESSIONAL, careful, PassRating.GOOD, false, 200).keySet()) {
            assertFalse(id.equals(Playbook.BIC) || id.equals(Playbook.CROSS), "no fancy plays when careful: " + id);
        }
    }

    @Test
    void aLateSetterCancelsThePlayForTheEmergencyHighBall() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        PlayCaller caller = new PlayCaller(CourtSide.HOME, new Random(1));
        TeamTactics tactics = new TeamTactics();
        tactics.set(TeamCommand.QUICK_ATTACK);
        Combination play = caller.call(Difficulty.PROFESSIONAL, Playbook.standard(), tactics, allLanes(world), PassRating.PERFECT, false, List.of());
        assertFalse(play.emergency());
        caller.checkSetter(0.9f);
        assertFalse(caller.cancelled(), "a setter in time keeps the play");
        caller.checkSetter(0.1f);
        assertTrue(caller.cancelled());
        assertTrue(caller.play().emergency());
        assertTrue(caller.status().contains("cancelled"));
    }

    @Test
    void theFinalOptionSkipsAHitterWhoIsDownOrLate() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        PlayCaller caller = new PlayCaller(CourtSide.HOME, new Random(2));
        EnumMap<SetType.Lane, VolleyballPlayer> lanes = allLanes(world);
        TeamTactics tactics = new TeamTactics();
        tactics.set(TeamCommand.QUICK_ATTACK);
        Combination play;
        do {
            play = caller.call(Difficulty.PROFESSIONAL, Playbook.standard(), tactics, lanes, PassRating.PERFECT, false, List.of());
        } while (!play.id().equals(Playbook.FIRST_TEMPO));
        // The middle is far from the quick's spot (late); the outside hitter is at the high ball's spot.
        VolleyballPlayer middle = lanes.get(SetType.Lane.MIDDLE);
        middle.placeAt(-6f, 3f);
        VolleyballPlayer outside = lanes.get(SetType.Lane.OUTSIDE);
        com.badlogic.gdx.math.Vector3 spot = SetType.HIGH_OUTSIDE.target(CourtSide.HOME, new com.badlogic.gdx.math.Vector3());
        outside.placeAt(spot.x, spot.z);
        for (VolleyballPlayer p : lanes.values()) if (p != middle && p != outside) p.placeAt(-8f, -4f + p.id * 0.6f);
        Combination.Option o = caller.decide(world.home().players().get(0), 1f, false, world.away().onCourt());
        assertEquals(SetType.HIGH_OUTSIDE, o.set(), "the quick hitter is late: the next option");
        assertTrue(caller.status().contains("developed"));
    }

    @Test
    void inAMatchTheTeamsRunPlaysWithDecoysAndDevelopments() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
        MatchController match = new MatchController(world, SHORT, 21, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        Set<String> plays = new HashSet<>();
        int[] decoyJumps = new int[1];
        int[] attacks = new int[1];
        world.ballHandling().addListener((BallTouch t) -> {
            if (t.type() == ContactType.ATTACK) {
                attacks[0]++;
                Combination c = match.ai(t.side()).currentPlay();
                if (c != null) plays.add(c.id());
            }
        });
        match.start(CourtSide.HOME);
        boolean[] wasAirborne = new boolean[world.players().size() + 1];
        for (long steps = 0; match.phase() != MatchController.Phase.FINISHED && steps < 120L * 60 * 15; steps++) {
            world.step(DT);
            match.update(DT, List.of());
            // A middle blocker jumping on their own side while the own team sets: a decoy (or the quick).
            for (VolleyballPlayer p : world.players()) {
                if (p.isAirborne() && !wasAirborne[p.id] && match.ai(p.side).state(p) == AiState.JUMPING) decoyJumps[0]++;
                wasAirborne[p.id] = p.isAirborne();
            }
        }
        assertEquals(MatchController.Phase.FINISHED, match.phase());
        assertTrue(attacks[0] > 20);
        assertTrue(plays.size() >= 5, "several plays were run: " + plays);
        assertTrue(decoyJumps[0] > 0, "decoys jumped");
    }

    @Test
    void serveCommandsDecideTheServe() {
        for (TeamCommand command : List.of(TeamCommand.SAFE_SERVE, TeamCommand.POWER_SERVE)) {
            GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 5);
            MatchController match = new MatchController(world, SHORT, 5, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
            // The command as a person would give it: the bench coach does not overrule it.
            match.ai(CourtSide.HOME).setCoaching(false);
            match.ai(CourtSide.HOME).tactics().set(command);
            Set<ServeType> types = new HashSet<>();
            world.ballHandling().addListener((BallTouch t) -> {
                if (t.type() == ContactType.SERVE && t.side() == CourtSide.HOME) types.add(ServeType.valueOf(serveName(t.variant())));
            });
            match.start(CourtSide.HOME);
            for (long steps = 0; match.phase() != MatchController.Phase.FINISHED && steps < 120L * 60 * 6; steps++) {
                world.step(DT);
                match.update(DT, List.of());
            }
            assertFalse(types.isEmpty());
            if (command == TeamCommand.SAFE_SERVE) assertEquals(Set.of(ServeType.FLOAT), types, "safe serves are floats");
            else assertTrue(types.contains(ServeType.POWER), "power serves on command: " + types);
        }
    }

    private static String serveName(String label) {
        for (ServeType t : ServeType.values()) if (t.label.equals(label)) return t.name();
        return ServeType.FLOAT.name();
    }

    @Test
    void defenceCommandsSetTheScheme() {
        TeamTactics t = new TeamTactics();
        assertNull(t.defenseScheme(), "without a command the team decides");
        t.toggle(TeamCommand.CLOSE_LINE);
        assertEquals(com.volleyball.game.tactics.DefenseScheme.CLOSE_LINE, t.defenseScheme());
        assertEquals(com.volleyball.game.mechanics.BlockStyle.LINE, t.defenseScheme().blockStyle);
        t.toggle(TeamCommand.EXPECT_TIP);
        assertTrue(t.defenseScheme().upLine, "commands of a group replace each other");
        t.toggle(TeamCommand.EXPECT_TIP);
        assertNull(t.defenseScheme(), "the same command again switches it off");
    }
}
