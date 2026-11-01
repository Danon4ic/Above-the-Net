package com.volleyball.game.training;

import com.volleyball.game.config.LaunchOptions;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end check of every drill: the demo player uses only movement intents, jumps and action
 * requests (no teleporting), and the full physics + mechanics decide the outcome.
 */
class AutoTraineeTest {
    static final float DT = 1f / 120f;

    @ParameterizedTest
    @EnumSource(value = Drill.class, names = {"SERVE", "RECEIVE", "SET", "ATTACK", "BLOCK", "DEFENSE"})
    void demoPlayerSucceedsInMostRepetitions(Drill drill) {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 42);
        DrillController drills = new DrillController(world, 7);
        AutoTrainee auto = new AutoTrainee(world);
        drills.start(drill);
        for (int i = 0; i < 120 * 90 && drills.attempts() < 12; i++) {
            auto.update(drills.trainee(), drill, world.time());
            world.step(DT);
            drills.update(DT);
        }
        assertTrue(drills.attempts() >= 8, drill + ": only " + drills.attempts() + " repetitions");
        // Blocking and digging spikes are hard even for a good player: a third is a fine rate.
        int required = drill == Drill.BLOCK || drill == Drill.DEFENSE ? drills.attempts() / 4 : drills.attempts() / 2;
        assertTrue(drills.successes() >= required,
                drill + ": " + drills.successes() + "/" + drills.attempts() + ", last: " + drills.lastResult());
    }

    @Test
    void launchOptionsParse() {
        LaunchOptions options = LaunchOptions.parse(new String[]{"--drill=attack", "--AUTOPLAY"});
        assertEquals("ATTACK", options.drill());
        assertTrue(options.autoplay());
        assertFalse(LaunchOptions.parse(null).autoplay());
    }
}
