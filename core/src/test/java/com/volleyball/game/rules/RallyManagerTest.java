package com.volleyball.game.rules;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.Team;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.simulation.TeamFactory;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.Net;
import com.volleyball.game.world.NetCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.volleyball.game.world.CourtSide.AWAY;
import static com.volleyball.game.world.CourtSide.HOME;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Rally decisions from scripted events. Default teams: home ids 0..5 and away ids 6..11 stand in
 * zones 1..6 in that order, at their base positions; home serves.
 */
class RallyManagerTest {
    private final Team home = TeamFactory.home(0);
    private final Team away = TeamFactory.away(6);
    private final List<VolleyballPlayer> players = new ArrayList<>();
    private final RotationManager rotation = new RotationManager(new int[]{0, 1, 2, 3, 4, 5}, new int[]{6, 7, 8, 9, 10, 11});
    private final Net net = new Net(NetCategory.MEN);
    private final Ball ball = new Ball();
    private double time;

    RallyManagerTest() {
        players.addAll(home.players());
        players.addAll(away.players());
        // The server stands in the service zone.
        player(0).placeAt(-10f, 1.5f);
    }

    private VolleyballPlayer player(int id) {
        return players.stream().filter(p -> p.id == id).findFirst().orElseThrow();
    }

    private RallyManager rally(MatchFormat format) {
        RallyManager r = new RallyManager(format, rotation, players, net);
        r.start(HOME, time);
        return r;
    }

    private BallTouch touch(int id, ContactType type, float contactY, float quality) {
        time += 0.6;
        VolleyballPlayer p = player(id);
        return new BallTouch(id, p.side, type, ContactPurpose.PASS, quality, null, Float.NaN,
                new Vector3(p.position.x, contactY, p.position.z), p.position.cpy(), p.isAirborne(), 1,
                new Vector3(), "", time);
    }

    private BallTouch touch(int id, ContactType type) {
        return touch(id, type, 1f, 0.8f);
    }

    private void floor(RallyManager r, float x, float z) {
        time += 0.5;
        r.onBallContact(new BallContact(ColliderKind.FLOOR, -1, new Vector3(x, Ball.RADIUS, z), 8f, time));
    }

    private static void assertFault(RallyManager r, CourtSide winner, Fault fault) {
        assertEquals(RallyManager.State.OVER, r.state(), "the rally should be over");
        assertEquals(fault, r.outcome().reason());
        assertEquals(winner, r.outcome().winner());
    }

    @Test
    void serveLandingInTheOpponentCourtWinsThePoint() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        assertEquals(RallyManager.State.IN_PLAY, r.state());
        floor(r, 5f, 1f);
        assertFault(r, HOME, Fault.BALL_IN);
    }

    @Test
    void ballOutAfterATouchIsThatTeamsFault() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS)); // away receives, shanks it out
        floor(r, 12f, 6f);
        assertFault(r, HOME, Fault.BALL_OUT);
    }

    @Test
    void fourthTeamTouchIsAFault() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallTouch(touch(6, ContactType.OVERHEAD_PASS));
        r.onBallTouch(touch(9, ContactType.FOREARM_PASS));
        assertEquals(3, r.touches(AWAY));
        r.onBallTouch(touch(8, ContactType.FOREARM_PASS));
        assertFault(r, HOME, Fault.FOUR_HITS);
    }

    @Test
    void afterABlockTheTeamHasThreeMoreTouchesAndTheBlockerMayPlayAgain() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallTouch(touch(6, ContactType.OVERHEAD_PASS));
        r.onBallTouch(touch(9, ContactType.ATTACK, 3.2f, 0.9f)); // away zone 4 attacks
        r.onBallTouch(touch(2, ContactType.BLOCK, 3.0f, 0.8f));  // home zone 3 blocks
        assertEquals(0, r.touches(HOME), "the block does not count");
        r.onBallTouch(touch(2, ContactType.FOREARM_PASS));      // the blocker plays it again: allowed
        r.onBallTouch(touch(0, ContactType.OVERHEAD_PASS));
        r.onBallTouch(touch(3, ContactType.ATTACK, 3.1f, 0.9f));
        assertEquals(RallyManager.State.IN_PLAY, r.state());
        assertEquals(3, r.touches(HOME));
        r.onBallTouch(touch(1, ContactType.FOREARM_PASS));
        assertFault(r, AWAY, Fault.FOUR_HITS);
    }

    @Test
    void samePlayerTwiceInARowIsADoubleContact() {
        RallyManager r = rally(MatchFormat.simplified());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallTouch(touch(10, ContactType.OVERHEAD_PASS));
        assertFault(r, HOME, Fault.DOUBLE_CONTACT);
    }

    @Test
    void bodyBounceInTheSameActionIsNotADoubleContact() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallContact(new BallContact(ColliderKind.PLAYER_BODY, 10, new Vector3(5f, 1.2f, 0f), 3f, time + 0.1));
        assertEquals(RallyManager.State.IN_PLAY, r.state());
        assertEquals(1, r.touches(AWAY));
    }

    @Test
    void antennaAndOutOfPlayObjectsEndTheRally() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallContact(new BallContact(ColliderKind.ANTENNA, -1, new Vector3(0f, 2.6f, 4.5f), 9f, time + 0.3));
        assertFault(r, AWAY, Fault.ANTENNA);

        RallyManager c = rally(MatchFormat.official());
        c.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        c.onBallTouch(touch(7, ContactType.FOREARM_PASS));
        c.onBallContact(new BallContact(ColliderKind.CEILING, -1, new Vector3(3f, 12.4f, 0f), 2f, time + 0.3));
        assertFault(c, HOME, Fault.OUT_OF_PLAY_OBJECT);
    }

    @Test
    void wrongServerIsARotationFault() {
        RallyManager r = rally(MatchFormat.simplified());
        r.onBallTouch(touch(1, ContactType.SERVE, 3f, 0.9f)); // zone 2 player serves instead of zone 1
        assertFault(r, AWAY, Fault.SERVE_ORDER);
    }

    @Test
    void footFaultOnlyInOfficialRules() {
        player(0).placeAt(-8.8f, 1.5f); // standing on the court at the serve
        RallyManager official = rally(MatchFormat.official());
        official.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        assertFault(official, AWAY, Fault.FOOT_FAULT);

        RallyManager simple = rally(MatchFormat.simplified());
        simple.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        assertEquals(RallyManager.State.IN_PLAY, simple.state());
    }

    @Test
    void aTeammateTouchingTheServeIsAFault() {
        for (MatchFormat format : new MatchFormat[]{MatchFormat.official(), MatchFormat.simplified()}) {
            RallyManager r = rally(format);
            r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
            assertEquals(RallyManager.State.IN_PLAY, r.state());
            // The serve hits the zone-2 player of the serving team on its way to the net.
            r.onBallTouch(touch(1, ContactType.FOREARM_PASS));
            assertFault(r, AWAY, Fault.SERVE_TEAM_TOUCH);
        }
    }

    @Test
    void theServerTouchingTheServeAgainIsAFault() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(0, ContactType.OVERHEAD_PASS));
        assertFault(r, AWAY, Fault.SERVE_TEAM_TOUCH);
    }

    @Test
    void theReceiversMayPlayTheServe() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(7, ContactType.FOREARM_PASS));
        assertEquals(RallyManager.State.IN_PLAY, r.state());
    }

    @Test
    void steppingOntoTheCourtWithTheBallIsAFault() {
        ball.hold(new Vector3(-10f, 1f, 1.5f));
        RallyManager r = rally(MatchFormat.official());
        r.update(ball, time += 0.1);
        assertEquals(RallyManager.State.WAITING_SERVE, r.state(), "standing in the service zone is fine");
        player(0).placeAt(-8.7f, 1.5f); // walks onto the court holding the ball
        r.update(ball, time += 0.1);
        assertFault(r, AWAY, Fault.SERVER_ON_COURT);
    }

    @Test
    void walkingBackToTheServiceZoneIsNotAFault() {
        // At the whistle the server is still on the court (walking back to serve): no fault until it
        // has been in the service zone.
        player(0).placeAt(-8f, 1.5f);
        ball.hold(new Vector3(-8f, 1f, 1.5f));
        RallyManager r = rally(MatchFormat.official());
        r.update(ball, time += 0.1);
        assertEquals(RallyManager.State.WAITING_SERVE, r.state());
        RallyManager simple = rally(MatchFormat.simplified());
        player(0).placeAt(-10f, 1.5f);
        simple.update(ball, time += 0.1);
        player(0).placeAt(-8.7f, 1.5f);
        simple.update(ball, time += 0.1);
        assertEquals(RallyManager.State.WAITING_SERVE, simple.state(), "simplified rules do not call it");
    }

    @Test
    void jumpServeTakeOffSpotCounts() {
        VolleyballPlayer server = player(0);
        server.placeAt(-8.95f, 1.5f);
        server.recordTakeoff();           // took off on the end line...
        server.position.set(-8.3f, 0.7f, 1.5f); // ...and hits the ball in the air over the court: still a foot fault
        server.setAirborne(true);
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3.4f, 0.9f));
        assertFault(r, AWAY, Fault.FOOT_FAULT);
    }

    @Test
    void serveMustBeMadeWithinTheTimeLimit() {
        RallyManager r = rally(MatchFormat.official());
        ball.hold(new Vector3(-10f, 1f, 1.5f));
        time += 8.5;
        r.update(ball, time);
        assertFault(r, AWAY, Fault.SERVE_TIMEOUT);
    }

    @Test
    void tossedButNotHitServeIsAFault() {
        RallyManager r = rally(MatchFormat.official());
        floor(r, -10f, 1.5f);
        assertFault(r, AWAY, Fault.SERVE_NOT_HIT);
    }

    @Test
    void playerTouchingTheNetIsCalledInOfficialRulesOnly() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        player(8).markNetContact();
        r.update(ball, time);
        assertFault(r, HOME, Fault.PLAYER_NET_TOUCH);
        assertEquals(8, r.outcome().playerId());

        RallyManager s = rally(MatchFormat.simplified());
        s.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        player(8).markNetContact();
        s.update(ball, time);
        assertEquals(RallyManager.State.IN_PLAY, s.state());
    }

    @Test
    void backRowAttackFromTheFrontZoneIsAFaultButFromBehindTheLineIsLegal() {
        VolleyballPlayer backRow = player(10); // away zone 5
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        backRow.placeAt(2.0f, -3f);           // after the serve: takes off in the front zone
        backRow.recordTakeoff();
        backRow.setAirborne(true);
        r.onBallTouch(touch(7, ContactType.FOREARM_PASS));
        r.onBallTouch(touch(6, ContactType.OVERHEAD_PASS));
        r.onBallTouch(touch(10, ContactType.ATTACK, 3.2f, 0.9f));
        assertFault(r, HOME, Fault.BACK_ROW_ATTACK);

        backRow.placeAt(6.5f, 3f);            // own zone 5 position for the serve (away: left = +Z)
        RallyManager legal = rally(MatchFormat.official());
        legal.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        backRow.placeAt(3.6f, -3f);           // took off behind the attack line: a legal back-row attack
        backRow.recordTakeoff();
        backRow.setAirborne(true);
        legal.onBallTouch(touch(7, ContactType.FOREARM_PASS));
        legal.onBallTouch(touch(6, ContactType.OVERHEAD_PASS));
        legal.onBallTouch(touch(10, ContactType.ATTACK, 3.2f, 0.9f));
        assertEquals(RallyManager.State.IN_PLAY, legal.state());
    }

    @Test
    void backRowPlayerMayNotBlock() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallTouch(touch(6, ContactType.OVERHEAD_PASS));
        r.onBallTouch(touch(9, ContactType.ATTACK, 3.2f, 0.9f));
        r.onBallTouch(touch(5, ContactType.BLOCK, 3.0f, 0.8f)); // home zone 6
        assertFault(r, AWAY, Fault.BACK_ROW_BLOCK);
    }

    @Test
    void liberoRestrictions() {
        VolleyballPlayer libero = new VolleyballPlayer(20, "Libero", 1, PlayerRole.LIBERO, HOME, 1.82f, new PlayerAttributes());
        libero.placeAt(-6f, -3f);
        players.add(libero);
        RotationManager withLibero = new RotationManager(new int[]{0, 1, 2, 3, 20, 5}, new int[]{6, 7, 8, 9, 10, 11});
        player(6).placeAt(10f, -1.5f); // away server in its service zone

        RallyManager attack = new RallyManager(MatchFormat.official(), withLibero, players, net);
        attack.start(AWAY, time);
        attack.onBallTouch(touch(6, ContactType.SERVE, 3f, 0.9f));
        libero.placeAt(-4f, -3f);
        libero.setAirborne(true);
        attack.onBallTouch(touch(20, ContactType.ATTACK, 3.0f, 0.8f));
        assertFault(attack, AWAY, Fault.LIBERO_ATTACK);

        RallyManager block = new RallyManager(MatchFormat.official(), withLibero, players, net);
        block.start(AWAY, time);
        block.onBallTouch(touch(6, ContactType.SERVE, 3f, 0.9f));
        block.onBallTouch(touch(20, ContactType.BLOCK, 3.0f, 0.8f));
        assertFault(block, AWAY, Fault.LIBERO_BLOCK);

        RallyManager set = new RallyManager(MatchFormat.official(), withLibero, players, net);
        set.start(AWAY, time);
        set.onBallTouch(touch(6, ContactType.SERVE, 3f, 0.9f));
        set.onBallTouch(touch(0, ContactType.FOREARM_PASS));
        libero.setAirborne(false);
        libero.placeAt(-1.5f, 0f);  // overhead set from the front zone
        set.onBallTouch(touch(20, ContactType.OVERHEAD_PASS, 2.4f, 0.9f));
        set.onBallTouch(touch(3, ContactType.ATTACK, 3.2f, 0.9f));
        assertFault(set, AWAY, Fault.LIBERO_SET_ATTACK);
    }

    @Test
    void overlapAtTheServeIsAPositionalFault() {
        // Away zone 6 player steps in front of the zone 3 player before the serve.
        player(11).placeAt(1.5f, 0f);
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        assertFault(r, HOME, Fault.POSITIONAL);
        assertEquals(11, r.outcome().playerId());
    }

    @Test
    void ballCrossingOutsideTheAntennasOrUnderTheNetIsAFault() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        ball.launch(new Vector3(-0.2f, 3f, 4.9f), new Vector3(10f, 0f, 0f), Vector3.Zero, 1);
        r.update(ball, time);
        ball.position.set(0.2f, 3f, 4.9f);
        r.update(ball, time + 0.04);
        assertFault(r, AWAY, Fault.OUTSIDE_CROSSING_SPACE);

        RallyManager u = rally(MatchFormat.official());
        u.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        ball.launch(new Vector3(-0.2f, 0.7f, 0f), new Vector3(10f, 0f, 0f), Vector3.Zero, 1);
        u.update(ball, time);
        ball.position.set(0.2f, 0.7f, 0f);
        u.update(ball, time + 0.04);
        assertFault(u, AWAY, Fault.UNDER_NET);
    }

    @Test
    void attackOnServeIsNotCalledAfterABlockOfTheReceivingTeamsAttack() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));   // away receives the serve
        r.onBallTouch(touch(6, ContactType.OVERHEAD_PASS));
        r.onBallTouch(touch(8, ContactType.ATTACK, 3.2f, 0.9f));
        r.onBallTouch(touch(2, ContactType.BLOCK, 3.0f, 0.8f)); // home blocks: possession home
        VolleyballPlayer hitter = player(1);                    // home zone 2 plays the rebound above the net
        hitter.placeAt(-1.5f, 2f);
        hitter.setAirborne(true);
        r.onBallTouch(touch(1, ContactType.ATTACK, 3.2f, 0.9f));
        assertEquals(RallyManager.State.IN_PLAY, r.state(), "home never received a serve in this possession");
        hitter.setAirborne(false);
    }

    @Test
    void aBallCrossingOutsideTheAntennasAfterABlockIsTheBlockingTeamsFault() {
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallTouch(touch(6, ContactType.OVERHEAD_PASS));
        r.onBallTouch(touch(8, ContactType.ATTACK, 3.2f, 0.9f));
        r.onBallTouch(touch(2, ContactType.BLOCK, 3.0f, 0.8f));
        // Off the home block's hands, from the away side back over to home outside the antenna.
        ball.launch(new Vector3(0.2f, 3f, 4.9f), new Vector3(-10f, 0f, 0f), Vector3.Zero, 1);
        r.update(ball, time);
        ball.position.set(-0.2f, 3f, 4.9f);
        r.update(ball, time + 0.04);
        assertFault(r, AWAY, Fault.OUTSIDE_CROSSING_SPACE);
    }

    @Test
    void touchingTheNetBeforeTheServeIsNotAFault() {
        RallyManager r = rally(MatchFormat.official());
        player(8).markNetContact();
        r.update(ball, time);
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.update(ball, time);
        assertEquals(RallyManager.State.IN_PLAY, r.state());
    }

    /** A second-touch overhead set by player {@code id} with the given contact quality. */
    private BallTouch secondTouchSet(int id, float quality) {
        time += 0.6;
        VolleyballPlayer p = player(id);
        return new BallTouch(id, p.side, ContactType.OVERHEAD_PASS, ContactPurpose.SET, quality, null, Float.NaN,
                new Vector3(p.position.x, 2.4f, p.position.z), p.position.cpy(), p.isAirborne(), 2, new Vector3(), "Bic", time);
    }

    @Test
    void theSetterIsCalledForAHeldBallOnlyWhenItIsReallyThrown() {
        assertEquals(PlayerRole.SETTER, player(6).role);
        // A sloppy fast set (a bic) by the setter: play on.
        RallyManager r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallTouch(secondTouchSet(6, 0.12f));
        assertEquals(RallyManager.State.IN_PLAY, r.state(), "a sloppy set by the setter is not a fault");
        // The same contact by another player is a held ball...
        r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallTouch(secondTouchSet(9, 0.12f));
        assertFault(r, HOME, Fault.HELD_BALL);
        // ...and a really thrown ball by the setter too.
        r = rally(MatchFormat.official());
        r.onBallTouch(touch(0, ContactType.SERVE, 3f, 0.9f));
        r.onBallTouch(touch(10, ContactType.FOREARM_PASS));
        r.onBallTouch(secondTouchSet(6, 0.05f));
        assertFault(r, HOME, Fault.HELD_BALL);
    }
}
