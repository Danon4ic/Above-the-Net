package com.volleyball.game.training;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.ActionKind;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ContactZone;
import com.volleyball.game.mechanics.SetType;
import com.volleyball.game.physics.TrajectoryPredictor;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrillControllerTest {
    static final float DT = 1f / 120f;

    private static void step(GameWorld world, DrillController drills, int steps) {
        for (int i = 0; i < steps; i++) {
            world.step(DT);
            drills.update(DT);
        }
    }

    @Test
    void serveDrillGradesAServeAndStartsTheNextRepetition() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 5);
        DrillController drills = new DrillController(world);
        drills.start(Drill.SERVE);
        VolleyballPlayer server = drills.trainee();
        BallHandling handling = world.ballHandling();
        assertEquals(BallHandling.ServePhase.HOLDING, handling.servePhase());

        handling.setAim(server, 6f, 0f);
        handling.arm(server, ActionKind.PRIMARY, world.time()); // toss
        Vector3 ideal = new Vector3();
        for (int i = 0; i < 400; i++) {
            step(world, drills, 1);
            ContactZone.idealPoint(server, ContactType.SERVE, handling.serveType(server), ideal);
            if (world.ball().velocity.y < 0f && world.ball().position.y < ideal.y + 0.2f) break;
        }
        handling.arm(server, ActionKind.PRIMARY, world.time());
        step(world, drills, 120 * 4);
        assertEquals(1, drills.attempts());
        assertTrue(drills.lastResult().contains("Serve"), drills.lastResult());
        step(world, drills, 120 * 3);
        assertEquals(BallHandling.ServePhase.HOLDING, handling.servePhase(), "next repetition is ready");
    }

    @Test
    void receiveDrillFeedsAServeAndCountsAnAceWhenUntouched() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 6);
        DrillController drills = new DrillController(world);
        drills.start(Drill.RECEIVE);
        assertFalse(world.ball().isActive(), "ball waits on the machine");
        step(world, drills, (int) (120 * (DrillController.READY_TIME + 0.1f)));
        assertTrue(world.ball().isActive(), "machine served");
        // The trainee steps aside and does nothing.
        drills.trainee().placeAt(-8.5f, 4.3f);
        step(world, drills, 120 * 4);
        assertEquals(1, drills.attempts());
        assertEquals(0, drills.successes());
    }

    @Test
    void settingDrillHasARealFirstTempoWithTheMiddle() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
        DrillController drills = new DrillController(world, 21);
        drills.start(Drill.SET);
        VolleyballPlayer setter = drills.trainee();
        BallHandling handling = world.ballHandling();
        handling.selectVariant(setter, SetType.QUICK_MIDDLE.ordinal() + 1);
        for (int i = 0; i < 180 && !world.ball().isActive(); i++) step(world, drills, 1);
        var flight = new TrajectoryPredictor.Prediction();
        flight.watchHeight = setter.height + 0.1f;
        world.predictor().predict(world.ball(), 6f, flight);
        assertTrue(flight.reachesWatchHeight);
        setter.placeAt(flight.descentPoint.x - 0.18f, flight.descentPoint.z);
        setter.attributes.setting = 98;
        var touches = new java.util.ArrayList<com.volleyball.game.mechanics.BallTouch>();
        handling.addListener(touches::add);
        for (int i = 0; i < 300 && handling.quickSet(setter) == null; i++) step(world, drills, 1);
        assertNotNull(handling.quickSet(setter));
        handling.arm(setter, ActionKind.PRIMARY, world.time());
        for (int i = 0; i < 100 && touches.size() < 2; i++) step(world, drills, 1);
        assertEquals(2, touches.size());
        assertEquals(touches.get(1).playerId(), touches.getFirst().quickHitterId());
        assertEquals(ContactType.ATTACK, touches.get(1).type());
    }
}
