package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.PassRating;
import com.volleyball.game.mechanics.SetType;
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
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** A person's set brings the right hitter on the right route; the coach's command survives the block read. */
class PersonSetAndCommandsTest {
    private static EnumMap<SetType.Lane, VolleyballPlayer> lanes(GameWorld world) {
        EnumMap<SetType.Lane, VolleyballPlayer> lanes = new EnumMap<>(SetType.Lane.class);
        List<VolleyballPlayer> players = world.home().players();
        int i = 0;
        for (SetType.Lane lane : SetType.Lane.values()) lanes.put(lane, players.get(1 + i++));
        return lanes;
    }

    @Test
    void thePersonsSetSwitchesTheTeamToAPlayWithThatSet() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        PlayCaller caller = new PlayCaller(CourtSide.HOME, new Random(4));
        EnumMap<SetType.Lane, VolleyballPlayer> lanes = lanes(world);
        caller.call(Difficulty.PROFESSIONAL, Playbook.standard(), new TeamTactics(), lanes, PassRating.PERFECT, false, List.of());
        for (SetType set : SetType.values()) {
            caller.followSet(set, Playbook.standard(), lanes);
            assertNotNull(caller.chosen(), set + ": a play with it");
            assertEquals(set, caller.chosen().set(), set.label);
            assertTrue(caller.decided(), "the person decides, not the team");
            assertEquals(lanes.get(caller.chosen().hitter()), caller.chosenHitter(world.home().players().get(0)));
        }
        // The slide has a play of its own now: the middle runs behind the setter.
        caller.followSet(SetType.SLIDE, Playbook.standard(), lanes);
        assertEquals(Playbook.SLIDE, caller.play().id());
        Vector3 route = caller.runTarget(lanes.get(SetType.Lane.MIDDLE), -1f, new Vector3());
        assertNotNull(route);
        assertTrue(route.z * CourtSide.HOME.sign() < -1.5f || route.z * CourtSide.HOME.sign() > 1.5f, "towards zone 2");
    }

    @Test
    void theCommandedQuickIsPlayedEvenThoughTheirMiddleBlockerIsThere() {
        assertEquals(SetType.HIGH_OUTSIDE, decideFirstTempo(null), "reading the block: the free side");
        assertEquals(SetType.QUICK_MIDDLE, decideFirstTempo(TeamCommand.QUICK_ATTACK), "the coach wants the quick");
    }

    private static SetType decideFirstTempo(TeamCommand command) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        PlayCaller caller = new PlayCaller(CourtSide.HOME, new Random(2));
        EnumMap<SetType.Lane, VolleyballPlayer> lanes = lanes(world);
        TeamTactics tactics = new TeamTactics();
        if (command != null) tactics.set(command);
        Combination play;
        do {
            play = caller.call(Difficulty.PROFESSIONAL, Playbook.standard(), tactics, lanes, PassRating.PERFECT, false, List.of());
        } while (!play.id().equals(Playbook.FIRST_TEMPO));
        // Every hitter at its spot; two of their blockers waiting in the middle.
        for (Combination.Option o : play.options()) {
            Vector3 spot = o.set().target(CourtSide.HOME, new Vector3());
            lanes.get(o.hitter()).placeAt(spot.x, spot.z);
        }
        // The setter at the net (a fast set is only set from close to it).
        world.home().players().get(0).placeAt(-0.6f, 0.8f);
        List<VolleyballPlayer> theirs = world.away().onCourt();
        for (int i = 0; i < theirs.size(); i++) theirs.get(i).placeAt(i < 2 ? 0.5f : 6f, i < 2 ? -0.3f + i * 0.6f : -3f + i);
        return caller.decide(world.home().players().get(0), 1f, true, theirs).set();
    }

    @Test
    void withTheDecoyCommandTheTeamRunsDecoyPlays() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        PlayCaller caller = new PlayCaller(CourtSide.HOME, new Random(5));
        TeamTactics tactics = new TeamTactics();
        tactics.set(TeamCommand.DECOY);
        int decoys = 0;
        for (int i = 0; i < 200; i++) {
            Combination c = caller.call(Difficulty.PROFESSIONAL, Playbook.standard(), tactics, lanes(world), PassRating.GOOD, false, List.of());
            if (c.runs().stream().anyMatch(Combination.Run::decoy)) decoys++;
        }
        assertTrue(decoys > 150, "decoy plays: " + decoys + " of 200");
    }

    @Test
    void noFastSetFromDeepInTheCourt() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 3);
        PlayCaller caller = new PlayCaller(CourtSide.HOME, new Random(2));
        EnumMap<SetType.Lane, VolleyballPlayer> lanes = lanes(world);
        TeamTactics tactics = new TeamTactics();
        tactics.set(TeamCommand.QUICK_ATTACK);
        Combination play;
        do {
            play = caller.call(Difficulty.PROFESSIONAL, Playbook.standard(), tactics, lanes, PassRating.PERFECT, false, List.of());
        } while (!play.id().equals(Playbook.FIRST_TEMPO));
        for (Combination.Option o : play.options()) {
            Vector3 spot = o.set().target(CourtSide.HOME, new Vector3());
            lanes.get(o.hitter()).placeAt(spot.x, spot.z);
        }
        VolleyballPlayer setter = world.home().players().get(0);
        setter.placeAt(-3.5f, 0.5f); // a pass 3.5 m off the net
        assertFalse(caller.decide(setter, 1f, false, world.away().onCourt()).set().fast());
    }
}
