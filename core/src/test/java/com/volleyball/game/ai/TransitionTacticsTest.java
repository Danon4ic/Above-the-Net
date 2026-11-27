package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ContactZone;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.rules.RotationManager;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.tactics.TeamCommand;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransitionTacticsTest {
    private static final float DT = 1f / 120f;
    private final GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
    private final List<VolleyballPlayer> home = world.home().players();
    private final List<VolleyballPlayer> away = world.away().players();
    private final RotationManager rotation = new RotationManager(ids(home), ids(away));
    private final TeamAI team = new TeamAI(world, world.home(), Difficulty.PROFESSIONAL, 21);
    private final VolleyballPlayer setter = home.get(0);

    private static int[] ids(List<VolleyballPlayer> players) {
        return players.stream().limit(6).mapToInt(p -> p.id).toArray();
    }

    @Test
    void theSetterCanReadHighBallPipeAndTheRiskOfAShoot() {
        var advisor = new ShotAdvisor(world);
        VolleyballPlayer wing = home.get(1);
        VolleyballPlayer pipe = home.get(4);
        setter.placeAt(-3.5f, 0f);
        wing.placeAt(-2.65f, -3.4f);
        pipe.placeAt(-5.9f, 0.25f);
        pipe.velocity.x = 2.4f;
        world.ball().launch(new Vector3(-3.5f, 2.85f, 0f), new Vector3(0f, -2.5f, 0f), Vector3.Zero, 21);

        assertEquals(ShotAdvisor.PassQuality.POOR, advisor.passQuality(CourtSide.HOME));
        assertEquals(ShotAdvisor.SetReadiness.AVAILABLE, advisor.setReadiness(setter, SetType.HIGH_OUTSIDE, wing));
        assertEquals(ShotAdvisor.SetReadiness.APPROACHING, advisor.setReadiness(setter, SetType.PIPE, pipe));
        assertEquals(ShotAdvisor.SetReadiness.RISKY, advisor.setReadiness(setter, SetType.SHOOT, wing));

        world.ball().launch(new Vector3(-5f, 2.85f, 0f), new Vector3(0f, -2.5f, 0f), Vector3.Zero, 22);
        assertEquals(ShotAdvisor.SetReadiness.UNAVAILABLE, advisor.setReadiness(setter, SetType.SHOOT, wing));
    }

    @Test
    void aPlayableSetStaysWithItsHitterEvenWhenACovererIsCloser() {
        VolleyballPlayer hitter = home.get(1);
        VolleyballPlayer closeCoverer = home.get(3);
        setter.placeAt(-1.5f, 1f);
        setter.attributes.setting = 96;
        hitter.placeAt(-5.55f, -3.6f);
        closeCoverer.placeAt(-1.5f, -3.35f);
        home.get(2).placeAt(-2.2f, -0.2f);
        home.get(4).placeAt(-6f, 0.2f);
        home.get(5).placeAt(-6f, 2.4f);

        var handling = world.ballHandling();
        List<BallTouch> touches = new ArrayList<>();
        handling.addListener(team);
        handling.addListener(touches::add);
        handling.selectVariant(setter, SetType.HIGH_OUTSIDE.ordinal() + 1);
        Vector3 release = ContactZone.idealPoint(setter, ContactType.OVERHEAD_PASS, null, new Vector3());
        world.ball().launch(release, new Vector3(0f, -1.5f, 0f), Vector3.Zero, 21);
        handling.registerFeed(CourtSide.HOME, 1);
        team.update(world.time(), rotation, null, List.of(setter));
        handling.arm(setter, ActionKind.PRIMARY, world.time());
        world.step(DT);
        assertEquals(ContactPurpose.SET, touches.getFirst().purpose());

        for (int i = 0; i < 250 && touches.size() == 1; i++) {
            team.update(world.time(), rotation, null, List.of(setter));
            assertSame(hitter, team.calledBallPlayer());
            world.step(DT);
        }
        assertEquals(2, touches.size());
        assertEquals(ContactType.ATTACK, touches.get(1).type());
        assertEquals(hitter.id, touches.get(1).playerId());
    }

    @Test
    void aWingClosingTheLineLetsMiddleBackShiftTowardsTheCross() {
        VolleyballPlayer middle = home.get(2);
        VolleyballPlayer wing = home.get(1);
        VolleyballPlayer middleBack = home.get(5);
        VolleyballPlayer offBlocker = home.get(3);
        middle.placeAt(-0.55f, -2.3f);
        wing.placeAt(-0.55f, -3.1f);
        middleBack.placeAt(-8f, 2.3f);
        offBlocker.placeAt(-2.6f, -1.4f);
        world.ball().launch(new Vector3(1f, 4.1f, -2.95f), new Vector3(0f, -2.5f, 0f), Vector3.Zero, 21);
        world.ballHandling().registerFeed(CourtSide.AWAY, 2);

        team.tactics().set(TeamCommand.CLOSE_LINE);
        team.update(0.3, rotation, null, List.of());
        float withBlock = world.intent(middleBack).direction.y;
        float readyBlockCover = world.intent(offBlocker).direction.x;
        wing.placeAt(-1.8f, 1f);
        middle.placeAt(-1.8f, 1.7f);
        team.update(0.31, rotation, null, List.of());
        float lateBlock = world.intent(middleBack).direction.y;
        assertTrue(withBlock > lateBlock + 0.05f, "middle back moves towards the open diagonal: " + withBlock + " vs " + lateBlock);
        assertTrue(world.intent(offBlocker).direction.x > readyBlockCover + 0.1f,
                "the off-blocker steps in for the tip when both blockers are late");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void callsTheNearOrDeepCovererForTheRebound(boolean deep) {
        VolleyballPlayer hitter = home.get(1);
        VolleyballPlayer near = home.get(2);
        VolleyballPlayer back = home.get(4);
        hitter.placeAt(-1.35f, -1.25f);
        hitter.position.y = 0.7f;
        hitter.setAirborne(true);
        near.placeAt(-1.9f, -1f);
        back.placeAt(-6.7f, -1f);
        setter.placeAt(-4.6f, 2.8f);
        home.get(3).placeAt(-2.7f, 2.8f);
        home.get(5).placeAt(-7.2f, 2f);
        Vector3 spike = new Vector3(-1.0f, 3.2f, -1f);
        team.onBallTouch(new BallTouch(hitter.id, hitter.side, ContactType.ATTACK, ContactPurpose.ATTACK, 0.9f, null,
                Float.NaN, spike, hitter.position.cpy(), true, 3, new Vector3(26f, -3f, 0f), "", 0));
        world.ball().launch(new Vector3(0.25f, 3.05f, -1f), new Vector3(24f, -3f, 0f), Vector3.Zero, 20);
        world.ballHandling().registerFeed(CourtSide.HOME, 3);
        team.update(0.04, rotation, null, List.of());
        assertEquals(AiState.COVERING, team.state(near));
        assertEquals(AiState.COVERING, team.state(back));

        VolleyballPlayer blocker = away.get(2);
        Vector3 rebound = new Vector3(deep ? -14f : -3f, -2f, 0f);
        team.onBallTouch(new BallTouch(blocker.id, blocker.side, ContactType.BLOCK, ContactPurpose.BLOCK, 0.6f, null,
                Float.NaN, new Vector3(0f, 3.1f, -1f), blocker.position.cpy(), true, 0, rebound, "", 0.06));
        world.ball().launch(new Vector3(-0.3f, 2.8f, -1f), rebound, Vector3.Zero, 21);
        world.ballHandling().registerFeed(CourtSide.AWAY, 0);
        team.update(0.07, rotation, null, List.of());
        assertSame(deep ? back : near, team.calledBallPlayer());
    }

    @Test
    void theLiberoTakesTheSecondBallAfterTheSetterSavesABlockRebound() {
        VolleyballPlayer libero = world.home().libero();
        VolleyballPlayer replaced = home.get(5);
        rotation.replaceWithLibero(CourtSide.HOME, libero.id, replaced.id);
        replaced.setOnCourt(false);
        libero.setOnCourt(true);
        for (int i = 1; i < 5; i++) home.get(i).placeAt(-6.5f, -3.5f + i);
        setter.placeAt(-2.3f, 0f);
        libero.placeAt(-3f, 0f);
        Vector3 save = ContactZone.idealPoint(setter, ContactType.FOREARM_PASS, null, new Vector3());
        VolleyballPlayer blocker = away.get(2);
        team.onBallTouch(new BallTouch(blocker.id, blocker.side, ContactType.BLOCK, ContactPurpose.BLOCK, 0.6f, null,
                Float.NaN, new Vector3(0f, 3.1f, 0f), blocker.position.cpy(), true, 0, new Vector3(-6f, -2f, 0f), "", 0));
        world.ball().launch(save, new Vector3(-5f, -2f, 0f), Vector3.Zero, 21);
        world.ballHandling().registerFeed(CourtSide.AWAY, 0);
        world.ballHandling().addListener(team);
        world.ballHandling().arm(setter, ActionKind.PRIMARY, world.time());
        world.step(DT);
        assertEquals(1, world.ballHandling().teamTouches(CourtSide.HOME));
        assertEquals(setter.id, world.ballHandling().lastToucherId());

        team.update(world.time(), rotation, null, List.of());
        assertSame(libero, team.calledBallPlayer());
        assertNotNull(team.playAssignment(home.get(1)));
    }
}
