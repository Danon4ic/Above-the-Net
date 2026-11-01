package com.volleyball.game.multiplayer;

import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.input.KeyBindings;
import com.volleyball.game.input.PlayerAction;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.RuleMode;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MatchSessionTest {
    final GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);

    @Test
    void singlePlayerHasOneHomeSlot() {
        MatchSession s = MatchSession.create(SessionMode.SINGLE, world);
        assertEquals(1, s.slots().size());
        assertEquals(CourtSide.HOME, s.slot(0).side);
        assertEquals(ControllerKind.KEYBOARD_MOUSE, s.slot(0).controller);
        assertEquals(1, s.humans().size());
    }

    @Test
    void versusPutsThePeopleOnDifferentTeams() {
        MatchSession s = MatchSession.create(SessionMode.LOCAL_VERSUS, world);
        assertEquals(CourtSide.HOME, s.slot(0).player().side);
        assertEquals(CourtSide.AWAY, s.slot(1).player().side);
        assertEquals(ControllerKind.KEYBOARD_ARROWS, s.slot(1).controller);
        // Switching stays within the own team.
        for (int i = 0; i < 8; i++) assertEquals(CourtSide.AWAY, s.switchPlayer(1).side);
    }

    @Test
    void coopPeopleShareATeamButNeverAPlayer() {
        MatchSession s = MatchSession.create(SessionMode.LOCAL_COOP, world);
        assertEquals(CourtSide.HOME, s.slot(1).side);
        assertNotSame(s.slot(0).player(), s.slot(1).player());
        Set<VolleyballPlayer> seen = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            VolleyballPlayer p = s.switchPlayer(0);
            assertNotSame(p, s.slot(1).player(), "never the teammate's player");
            seen.add(p);
        }
        assertEquals(5, seen.size(), "cycles through the other five players");
        assertFalse(s.assign(0, s.slot(1).player()));
        assertFalse(s.assign(0, world.away().players().get(0)), "not a player of the other team");
    }

    @Test
    void automaticSlotsAreLeftToTheAI() {
        MatchSession s = MatchSession.create(SessionMode.LOCAL_COOP, world);
        s.setAutomatic(0, true);
        assertEquals(1, s.humans().size());
        assertSame(s.slot(1).player(), s.humans().get(0));
    }

    @Test
    void networkModesUseARemoteSlotAndAreSkippedLocally() {
        MatchSession s = MatchSession.create(SessionMode.ONLINE_VERSUS, world);
        assertEquals(ControllerKind.REMOTE, s.slot(1).controller);
        SessionMode m = SessionMode.SINGLE;
        for (int i = 0; i < 10; i++) {
            m = m.nextLocal();
            assertFalse(m.networked);
        }
    }

    @Test
    void secondPersonHasItsOwnKeysWithoutTheMouse() {
        KeyBindings p1 = KeyBindings.defaults();
        KeyBindings p2 = KeyBindings.player2Defaults();
        for (PlayerAction action : PlayerAction.values()) {
            if (action == PlayerAction.PAUSE) continue;
            assertFalse(KeyBindings.isMouse(p2.key(action)), action + " must not need the mouse");
            assertNotEquals(p1.key(action), p2.key(action), action + " must differ from person 1");
        }
    }

    @Test
    void twoPeopleCanPlayAMatchAgainstEachOtherWithAITeammates() {
        MatchController match = new MatchController(world, new MatchFormat(1, 3, 3, 2, RuleMode.SIMPLIFIED, 15f), 4,
                Difficulty.INTERMEDIATE, Difficulty.INTERMEDIATE);
        MatchSession s = MatchSession.create(SessionMode.LOCAL_VERSUS, world);
        match.start(CourtSide.HOME);
        // The people stand still; the match goes on (their teammates play) and is refereed.
        for (int i = 0; i < 120 * 60 * 5 && match.phase() != MatchController.Phase.FINISHED; i++) {
            for (VolleyballPlayer p : s.humans()) world.intent(p).clear();
            world.step(1f / 120f);
            match.update(1f / 120f, s.humans());
        }
        assertEquals(MatchController.Phase.FINISHED, match.phase());
    }
}
