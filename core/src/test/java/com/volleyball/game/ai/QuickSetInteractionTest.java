package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.ActionPose;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ContactZone;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuickSetInteractionTest {
    private static final float DT = 1f / 120f;

    @Test
    void setterKeepsTheContactWindowForWhenTheBallReachesTheHands() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
        VolleyballPlayer setter = world.home().players().stream().filter(p -> p.role == PlayerRole.SETTER).findFirst().orElseThrow();
        for (VolleyballPlayer p : world.players()) p.setOnCourt(p == setter);
        setter.placeAt(-1.32f, 0.98f);

        world.ball().launch(new Vector3(-2.14f, 4.27f, 0.86f), new Vector3(3.14f, -3.75f, 0.48f), Vector3.Zero, 21);
        world.ballHandling().registerFeed(CourtSide.HOME, 1);
        List<BallTouch> contacts = new ArrayList<>();
        world.ballHandling().addListener(contacts::add);
        world.intent(setter).jump = true;
        world.intent(setter).jumpHeightFactor = 0.65f;
        world.step(DT);
        assertTrue(setter.isAirborne());

        AutoPlayer motor = new AutoPlayer(world, 21);
        motor.act(setter, AutoPlayer.Behavior.JUMP_SET, world.time());
        assertFalse(world.ballHandling().isArmed(setter), "the far ball should not start the contact window at take-off");
        for (int i = 0; i < 60 && contacts.isEmpty(); i++) {
            world.step(DT);
            motor.act(setter, AutoPlayer.Behavior.JUMP_SET, world.time());
        }
        assertFalse(contacts.isEmpty());
        BallTouch set = contacts.getFirst();
        assertEquals(ContactType.OVERHEAD_PASS, set.type());
        assertEquals(ContactPurpose.SET, set.purpose());
        assertTrue(set.airborne());
        assertTrue(set.quality() > 0.45f, "the setter plays within the two-hand window");
    }

    private static final class Rally {
        final GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
        final VolleyballPlayer setter = role(PlayerRole.SETTER);
        final VolleyballPlayer middle = role(PlayerRole.MIDDLE_BLOCKER);
        final TeamAI team = new TeamAI(world, world.home(), Difficulty.PROFESSIONAL, 21);
        final MatchScore score = new MatchScore(MatchFormat.official());
        final List<BallTouch> touches = new ArrayList<>();
        final RotationManager rotation;

        Rally(boolean middleFront) {
            int[] home = world.home().players().stream().limit(6).mapToInt(p -> p.id).toArray();
            int old = java.util.Arrays.stream(home).boxed().toList().indexOf(middle.id);
            int spot = middleFront ? 2 : 5;
            home[old] = home[spot];
            home[spot] = middle.id;
            rotation = new RotationManager(home, world.away().players().stream().limit(6).mapToInt(p -> p.id).toArray());
            setter.placeAt(-1.5f, 1.0f);
            middle.placeAt(-1.17f, -0.2f);
            middle.setAirborne(true);
            middle.position.y = 0.27f;
            middle.velocity.y = 2.1f;
            setter.attributes.setting = 94;
            Vector3 release = ContactZone.idealPoint(setter, ContactType.OVERHEAD_PASS, null, new Vector3());
            world.ball().launch(release, new Vector3(0f, -2f, 0f), new Vector3(), 3);
            world.ballHandling().registerFeed(CourtSide.HOME, 1);
            world.ballHandling().addListener(team);
            world.ballHandling().addListener(touches::add);
        }

        private VolleyballPlayer role(PlayerRole role) {
            return world.home().players().stream().filter(p -> p.role == role).findFirst().orElseThrow();
        }

        void read(List<VolleyballPlayer> humans) {
            team.update(world.time(), rotation, score, humans);
        }

        BallTouch release() {
            world.ballHandling().arm(setter, ActionKind.PRIMARY, world.time());
            world.step(DT);
            assertFalse(touches.isEmpty(), "two hands contact the pass");
            return touches.getFirst();
        }
    }

    @Test
    void botSetterAdaptsToTheHumanAlreadyInTheAir() {
        Rally rally = new Rally(true);
        rally.read(List.of(rally.middle));
        var option = rally.world.ballHandling().quickSet(rally.setter);
        assertNotNull(option);
        assertSame(rally.middle, option.hitter());
        assertTrue(rally.team.quickWindowFor(rally.middle));
        BallTouch set = rally.release();
        assertEquals(ContactPurpose.SET, set.purpose());
        assertEquals(rally.middle.id, set.quickHitterId());
        assertEquals(ActionPose.SET_FAST, rally.setter.actionPose());
        assertEquals(ActionPose.QUICK_READY, rally.middle.actionPose());
        Vector3 anchor = new Vector3();
        assertTrue(rally.setter.actionContact(anchor));
        assertEquals(set.contactPoint(), anchor);
        assertSame(rally.middle, rally.team.quickHitter());
        rally.read(List.of(rally.middle));
        assertEquals(rally.middle.id, rally.team.responsibleId());
        rally.world.ballHandling().arm(rally.middle, ActionKind.PRIMARY, rally.world.time());
        for (int i = 0; i < 45 && rally.touches.size() < 2; i++) rally.world.step(DT);
        assertEquals(ContactType.ATTACK, rally.touches.get(1).type());
        assertEquals(rally.middle.id, rally.touches.get(1).playerId());
        assertEquals(ActionPose.QUICK_SWING, rally.middle.actionPose());
        assertTrue(rally.touches.get(1).time() - set.time() < 0.34, "the attack is made during the original jump");
        assertNull(rally.team.quickHitter());
    }

    @Test
    void humanCanCommitToTheFastLaneOrKeepTheHighBall() {
        Rally quick = new Rally(true);
        quick.world.ballHandling().selectVariant(quick.setter, SetType.QUICK_MIDDLE.ordinal() + 1);
        quick.read(List.of(quick.setter, quick.middle));
        assertNotNull(quick.world.ballHandling().quickSet(quick.setter));
        assertEquals(quick.middle.id, quick.release().quickHitterId());

        Rally high = new Rally(true);
        high.world.ballHandling().selectVariant(high.setter, SetType.HIGH_OUTSIDE.ordinal() + 1);
        high.read(List.of(high.setter, high.middle));
        assertNull(high.world.ballHandling().quickSet(high.setter));
        BallTouch ordinary = high.release();
        assertEquals(-1, ordinary.quickHitterId());
        assertEquals(SetType.HIGH_OUTSIDE.label, ordinary.variant());
    }

    @Test
    void expiredJumpAndIllegalBackRowTakeoffCannotCommit() {
        Rally late = new Rally(true);
        late.read(List.of(late.middle));
        assertNotNull(late.world.ballHandling().quickSet(late.setter));
        late.middle.velocity.y = -3.8f;
        assertEquals(-1, late.release().quickHitterId(), "a stale read is checked again at contact");

        Rally backRow = new Rally(false);
        backRow.read(List.of(backRow.middle));
        assertNull(backRow.world.ballHandling().quickSet(backRow.setter));
        assertEquals(-1, backRow.release().quickHitterId());
    }

    @Test
    void anEstablishedBlockWarnsThePersonAndTurnsTheBotToItsNormalSet() {
        Rally rally = new Rally(true);
        var blocker = rally.world.player(rally.rotation.playerInZone(CourtSide.AWAY, 3));
        blocker.placeAt(0.60f, 0f);
        blocker.setAirborne(true);
        blocker.position.y = 0.5f;
        rally.read(List.of(rally.middle));
        assertNull(rally.world.ballHandling().quickSet(rally.setter));

        rally.world.ballHandling().selectVariant(rally.setter, SetType.QUICK_MIDDLE.ordinal() + 1);
        rally.read(List.of(rally.setter, rally.middle));
        assertTrue(rally.world.ballHandling().quickSet(rally.setter).contested());
    }
}
