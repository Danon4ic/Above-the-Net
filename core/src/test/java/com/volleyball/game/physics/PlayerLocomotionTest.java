package com.volleyball.game.physics;

import com.badlogic.gdx.math.MathUtils;
import com.volleyball.game.entities.PlayerAttributes;
import com.volleyball.game.entities.PlayerRole;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtSide;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerLocomotionTest {
    static final float DT = 1f / 120f;
    private final PlayerLocomotion locomotion = new PlayerLocomotion();

    private static VolleyballPlayer player(int id, CourtSide side, int speed, int jump) {
        PlayerAttributes attributes = new PlayerAttributes();
        attributes.speed = speed;
        attributes.acceleration = speed;
        attributes.jump = jump;
        VolleyballPlayer p = new VolleyballPlayer(id, "P" + id, id, PlayerRole.OUTSIDE_HITTER, side, 1.95f, attributes);
        p.placeAt(side.sign() * 6f, 0f);
        return p;
    }

    private void run(VolleyballPlayer p, MoveIntent intent, float seconds) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            locomotion.update(p, intent, DT);
            intent.jump = false;
        }
    }

    @Test
    void acceleratesGraduallyToTheTopSpeedOfItsRating() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 80, 50);
        MoveIntent intent = new MoveIntent();
        intent.direction.set(0f, 1f);
        // The rating's top speed (a fresh player; tiredness is tested on its own).
        locomotion.setFatigue(false);
        locomotion.update(p, intent, DT);
        assertTrue(p.groundSpeed() < 0.3f, "no instant top speed");
        run(p, intent, 2f);
        assertEquals(PlayerLocomotion.maxRunSpeed(p.attributes), p.groundSpeed(), 1e-3f);
        intent.sprint = true;
        run(p, intent, 1f);
        assertEquals(PlayerLocomotion.maxRunSpeed(p.attributes) * PlayerLocomotion.SPRINT_FACTOR, p.groundSpeed(), 1e-3f);
        locomotion.setFatigue(true);
    }

    @Test
    void aTiredPlayerRunsALittleSlower() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 80, 50);
        p.setStamina(0.2f);
        MoveIntent intent = new MoveIntent();
        intent.direction.set(0f, 1f);
        run(p, intent, 2f);
        float top = PlayerLocomotion.maxRunSpeed(p.attributes);
        assertTrue(p.groundSpeed() < top * 0.95f && p.groundSpeed() > top * 0.85f, "tired: " + p.groundSpeed() + " of " + top);
    }

    @Test
    void fasterRatingRunsFurther() {
        VolleyballPlayer slow = player(1, CourtSide.HOME, 20, 50);
        VolleyballPlayer fast = player(2, CourtSide.HOME, 95, 50);
        MoveIntent intent = new MoveIntent();
        intent.direction.set(0f, -1f);
        run(slow, intent, 1f);
        run(fast, intent, 1f);
        assertTrue(Math.abs(fast.position.z) > Math.abs(slow.position.z) + 0.5f);
    }

    @Test
    void jumpHeightFollowsTheRatingAndTheJumperCannotSteer() {
        VolleyballPlayer low = player(1, CourtSide.HOME, 50, 10);
        VolleyballPlayer high = player(2, CourtSide.HOME, 50, 95);
        for (VolleyballPlayer p : new VolleyballPlayer[]{low, high}) {
            MoveIntent intent = new MoveIntent();
            intent.jump = true;
            float peak = 0f;
            for (int i = 0; i < 240; i++) {
                locomotion.update(p, intent, DT);
                intent.jump = false;
                if (i == 10) intent.direction.set(1f, 0f); // tries to steer mid-air
                if (p.isAirborne()) assertEquals(0f, p.velocity.x, 1e-6f);
                peak = Math.max(peak, p.position.y);
            }
            assertEquals(PlayerLocomotion.jumpHeight(p.attributes), peak, 0.02f);
            assertFalse(p.isAirborne(), "landed");
        }
    }

    @Test
    void landingCostsAMomentOfRecovery() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 50, 50);
        MoveIntent intent = new MoveIntent();
        intent.jump = true;
        run(p, intent, 2f);
        intent.jump = true;
        locomotion.update(p, intent, DT);
        assertTrue(p.isAirborne(), "can jump again after recovering");
    }

    @Test
    void playersDoNotOverlapAndStayOnTheirSide() {
        List<VolleyballPlayer> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            VolleyballPlayer p = player(i, CourtSide.HOME, 50, 50);
            p.placeAt(-3f, 0f);
            players.add(p);
        }
        VolleyballPlayer intruder = player(9, CourtSide.HOME, 50, 50);
        intruder.placeAt(2f, 0f);
        players.add(intruder);
        for (int i = 0; i < 20; i++) locomotion.resolvePlayerConstraints(players);
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                float d = players.get(i).position.dst(players.get(j).position);
                assertTrue(d >= PlayerLocomotion.PERSONAL_SPACE - 0.02f, "players " + i + "," + j + " overlap: " + d);
            }
        }
        assertTrue(intruder.position.x <= -PlayerLocomotion.BODY_RADIUS);
    }

    @Test
    void playersFaceTheNetWhenShuffling() {
        VolleyballPlayer p = player(1, CourtSide.AWAY, 60, 50);
        p.facingYaw = 0f;
        MoveIntent intent = new MoveIntent();
        intent.direction.set(0f, 1f); // sideways
        run(p, intent, 1f);
        assertEquals(MathUtils.PI, Math.abs(p.facingYaw), 0.05f);
    }

    @Test
    void playersCannotRunOrJumpThroughTheNet() {
        for (CourtSide side : CourtSide.values()) {
            VolleyballPlayer p = player(1, side, 95, 95);
            p.placeAt(side.sign() * 2f, 1f);
            MoveIntent intent = new MoveIntent();
            intent.direction.set(-side.sign(), 0f); // straight at the net
            intent.sprint = true;
            for (int i = 0; i < 240; i++) {
                intent.jump = i == 100; // and a running jump into it
                locomotion.update(p, intent, DT);
                locomotion.resolvePlayerConstraints(List.of(p));
                assertTrue(p.position.x * side.sign() >= PlayerLocomotion.BODY_RADIUS - 1e-5f,
                        side + " body crossed the net at step " + i + ": x=" + p.position.x);
            }
        }
    }

    @Test
    void playersWalkAroundThePostsInsteadOfThroughThem() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 90, 50);
        p.placeAt(-0.3f, 3.5f);
        MoveIntent intent = new MoveIntent();
        intent.direction.set(0f, 1f); // along the net towards the post at z = 5.5
        for (int i = 0; i < 240; i++) {
            locomotion.update(p, intent, DT);
            locomotion.resolvePlayerConstraints(List.of(p));
            float dx = p.position.x;
            float dz = p.position.z - 5.5f;
            assertTrue(Math.sqrt(dx * dx + dz * dz) >= 0.14f + PlayerLocomotion.BODY_RADIUS - 1e-4f, "inside the post");
        }
    }

    @Test
    void humanControlledPlayerFacesTheDirectionOfMovement() {
        for (float[] dir : new float[][]{{0f, 1f}, {0f, -1f}, {-1f, 0f}, {0.7f, 0.7f}}) {
            VolleyballPlayer p = player(1, CourtSide.HOME, 60, 50);
            MoveIntent intent = new MoveIntent();
            intent.faceMovement = true;
            intent.direction.set(dir[0], dir[1]);
            run(p, intent, 1f);
            float expected = MathUtils.atan2(-dir[1], dir[0]);
            float diff = Math.abs(((p.facingYaw - expected) % MathUtils.PI2 + MathUtils.PI2 * 1.5f) % MathUtils.PI2 - MathUtils.PI);
            assertTrue(diff < 0.05f, "moving " + dir[0] + "," + dir[1] + ": facing " + p.facingYaw + " vs " + expected);
        }
    }

    @Test
    void lowStanceShufflesFacingTheWatchedDirection() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 60, 50);
        MoveIntent intent = new MoveIntent();
        intent.faceMovement = true;
        intent.crouch = true;
        intent.direction.set(0f, 1f);
        run(p, intent, 1f);
        assertEquals(0f, p.facingYaw, 0.05f, "shuffling sideways still faces the net");
    }

    @Test
    void stoppingTurnsBackToTheBall() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 60, 50);
        MoveIntent intent = new MoveIntent();
        intent.faceMovement = true;
        intent.direction.set(0f, 1f);
        run(p, intent, 1f);
        intent.direction.setZero();
        intent.attentionYaw = 0.3f;
        run(p, intent, 1f);
        assertEquals(0.3f, p.facingYaw, 0.05f);
    }

    @Test
    void forwardDiveTurnsIntoTheDiveAndSidewaysFallRolls() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 60, 50);
        MoveIntent intent = new MoveIntent();
        intent.direction.set(0.9f, 0.4f);
        intent.dive = true;
        locomotion.update(p, intent, DT);
        assertEquals(com.volleyball.game.entities.BodyState.DIVING, p.bodyState());
        assertFalse(p.isDiveRoll(), "a dive close to the facing is chest-first");
        intent.dive = false;
        intent.direction.setZero();
        for (int i = 0; i < 20; i++) locomotion.update(p, intent, DT);
        assertEquals(MathUtils.atan2(-0.4f, 0.9f), p.facingYaw, 0.02f, "the body turns into the dive");

        VolleyballPlayer q = player(2, CourtSide.HOME, 60, 50);
        MoveIntent side = new MoveIntent();
        side.direction.set(0f, 1f); // 90 degrees off the facing
        side.dive = true;
        locomotion.update(q, side, DT);
        assertTrue(q.isDiveRoll(), "a fall far to the side is a roll");
    }

    @Test
    void diveTiltIsEasedNotLinear() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 60, 50);
        p.setBodyState(com.volleyball.game.entities.BodyState.DIVING, 0.022f);
        float early = p.bodyTilt();
        p.setBodyState(com.volleyball.game.entities.BodyState.DIVING, 0.11f);
        assertTrue(early < 0.05f, "slow start: " + early);
        assertEquals(0.5f, p.bodyTilt(), 0.01f);
    }

    @Test
    void chargedJumpGoesHigherThanATap() {
        float[] peaks = new float[2];
        float[] factors = {com.volleyball.game.input.InputController.TAP_JUMP_FACTOR,
                com.volleyball.game.input.InputController.CHARGED_JUMP_FACTOR};
        for (int k = 0; k < 2; k++) {
            VolleyballPlayer p = player(1, CourtSide.HOME, 50, 60);
            MoveIntent intent = new MoveIntent();
            intent.jump = true;
            intent.jumpHeightFactor = factors[k];
            for (int i = 0; i < 200; i++) {
                locomotion.update(p, intent, DT);
                intent.jump = false;
                peaks[k] = Math.max(peaks[k], p.position.y);
            }
        }
        float normal = PlayerLocomotion.jumpHeight(player(9, CourtSide.HOME, 50, 60).attributes);
        assertEquals(normal * factors[0], peaks[0], 0.02f);
        assertEquals(normal * factors[1], peaks[1], 0.02f);
    }

    @Test
    void diveCoversSeveralMetresBeforeTheSlideStops() {
        VolleyballPlayer p = player(1, CourtSide.HOME, 70, 50);
        p.placeAt(-8f, 0f);
        MoveIntent intent = new MoveIntent();
        intent.direction.set(1f, 0f);
        run(p, intent, 0.6f); // run-up
        float start = p.position.x;
        intent.dive = true;
        locomotion.update(p, intent, DT);
        intent.dive = false;
        intent.direction.setZero();
        run(p, intent, 1.2f);
        float slide = p.position.x - start;
        assertTrue(slide > 2.2f, "dive + slide distance " + slide);
    }
}
