package com.volleyball.game.ai;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.physics.LaunchSolver;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttackApproachTest {
    private static final float DT = 1f / 120f;

    @Test
    void outsideHitterMakesRoomAndRunsIntoHighSet() {
        runUpAndHit(SetType.HIGH_OUTSIDE, CourtSide.HOME, 0.25f, false);
    }

    @Test
    void pipeHitterRunsForwardAndJumpsBehindAttackLine() {
        runUpAndHit(SetType.PIPE, CourtSide.AWAY, 1.5f, true);
    }

    private void runUpAndHit(SetType set, CourtSide side, float startBehindSet, boolean backRow) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
        VolleyballPlayer hitter = (side == CourtSide.HOME ? world.home() : world.away()).players().get(1);
        for (VolleyballPlayer player : world.players()) player.setOnCourt(player == hitter);
        hitter.attributes.jump = 84;
        Vector3 target = set.target(side, new Vector3());
        float startDepth = set.depth + startBehindSet;
        hitter.placeAt(startDepth * side.sign(), target.z);

        Vector3 release = new Vector3(1.55f * side.sign(), 2.55f, 0.45f * -side.sign());
        LaunchSolver.Solution flight = new LaunchSolver.Solution();
        assertTrue(new LaunchSolver(world.predictor()).solveArc(release, target, set.apex, 0f, flight));
        world.ball().launch(release, flight.velocity, flight.spin, 21);
        world.ballHandling().registerFeed(side, 2);
        List<BallTouch> touches = new ArrayList<>();
        world.ballHandling().addListener(touches::add);

        AutoPlayer motor = new AutoPlayer(world, 21);
        motor.setBackRow(backRow);
        float deepestPosition = startDepth;
        float forwardSpeedAtTakeoff = 0f;
        for (int i = 0; i < 285 && touches.isEmpty(); i++) {
            boolean wasAirborne = hitter.isAirborne();
            motor.act(hitter, AutoPlayer.Behavior.ATTACK, world.time());
            world.step(DT);
            if (!hitter.isAirborne()) {
                deepestPosition = Math.max(deepestPosition, hitter.position.x * side.sign());
            } else if (!wasAirborne) {
                forwardSpeedAtTakeoff = -hitter.velocity.x * side.sign();
            }
        }

        float takeoffDepth = hitter.takeoffPosition.x * side.sign();
        assertTrue(deepestPosition - takeoffDepth > 0.6f, "the hitter runs forward before planting");
        assertTrue(forwardSpeedAtTakeoff > 0.8f, "the jump carries the hitter towards the net");
        if (backRow) assertTrue(takeoffDepth > CourtDimensions.ATTACK_LINE_DISTANCE);
        assertFalse(touches.isEmpty(), "the hitter reaches the set");
        assertEquals(ContactType.ATTACK, touches.getFirst().type());
        assertTrue(touches.getFirst().quality() > 0.70f);
    }
}
