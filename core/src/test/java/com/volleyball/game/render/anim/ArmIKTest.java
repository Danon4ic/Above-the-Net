package com.volleyball.game.render.anim;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arm IK puts the hand on the target, checked with the renderer's own joint rotations. */
class ArmIKTest {
    private static final float UPPER = 0.29f;
    private static final float HAND_X = 0.03f;
    private static final float HAND_Y = -0.31f;

    /** Forward kinematics exactly as the renderer applies it: upper arm Rx(spread)·Rz(raise), forearm Rz(elbow). */
    private static Vector3 hand(ArmIK ik) {
        Matrix4 m = new Matrix4().rotateRad(Vector3.X, ik.spread).rotateRad(Vector3.Z, ik.raise)
                .translate(0f, -UPPER, 0f).rotateRad(Vector3.Z, ik.elbow);
        return new Vector3(HAND_X, HAND_Y, 0f).mul(m);
    }

    @Test
    void theHandReachesTargetsAllAroundTheShoulder() {
        ArmIK ik = new ArmIK(UPPER, HAND_X, HAND_Y);
        Random random = new Random(3);
        for (int i = 0; i < 500; i++) {
            // Reachable targets: forwards, sideways (outwards = -z), above and below.
            Vector3 target = new Vector3(random.nextFloat() * 1.2f - 0.4f, random.nextFloat() * 1.4f - 0.7f,
                    -random.nextFloat() * 0.6f + 0.15f);
            float distance = target.len();
            if (distance < 0.15f || distance > 0.58f) continue;
            assertTrue(ik.solve(target.x, target.y, target.z));
            Vector3 reached = hand(ik);
            assertEquals(0f, reached.dst(target), 0.01f, "target " + target + " reached " + reached);
            assertTrue(ik.elbow >= 0f, "the elbow never bends backwards");
        }
    }

    @Test
    void aBallOverheadIsReachedWithTheArmUpNotSwungRoundTheBack() {
        ArmIK ik = new ArmIK(UPPER, HAND_X, HAND_Y);
        ik.solve(0.1f, 0.55f, -0.05f);
        assertTrue(ik.raise > 2.3f, "arm up: " + ik.raise);
        assertTrue(Math.abs(ik.spread) < 0.6f, "little spread: " + ik.spread);
    }

    @Test
    void outOfReachTheArmPointsStraightAtTheTarget() {
        ArmIK ik = new ArmIK(UPPER, HAND_X, HAND_Y);
        ik.solve(2f, 0f, 0f);
        Vector3 reached = hand(ik);
        assertTrue(reached.x > 0.55f && Math.abs(reached.y) < 0.05f, "stretched forwards: " + reached);
    }
}
