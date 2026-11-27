package com.volleyball.game.net;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.physics.MoveIntent;
import com.volleyball.game.physics.PlayerLocomotion;

/** Predicts only the owned player's locomotion, from the latest authority and outstanding input. */
public final class NetworkPrediction {
    private final PlayerLocomotion locomotion = new PlayerLocomotion();
    private final MoveIntent intent = new MoveIntent();
    private final Vector3 correction = new Vector3();
    private final Vector3[] history = new Vector3[64];
    private final int[] recordedTick = new int[64];
    private final boolean[] valid = new boolean[64];
    private int player = -1;
    private int tick;

    public NetworkPrediction(boolean fatigue) {
        locomotion.setFatigue(fatigue);
        for (int i = 0; i < history.length; i++) history[i] = new Vector3();
    }

    public void reset() {
        player = -1;
        correction.setZero();
        java.util.Arrays.fill(valid, false);
    }

    public void present(MatchLink link, VolleyballPlayer p, float dt) {
        NetworkState state = link.latest();
        if (state == null || state.phase() != MatchController.Phase.RALLY || state.controlledPlayer() != p.id
                || link.status() != MatchLink.Status.LIVE) {
            reset();
            return;
        }
        state.presentPlayer(p);
        if (player != p.id) {
            reset();
        } else if (state.tick() != tick) {
            int index = state.tick() & (history.length - 1);
            if (valid[index] && recordedTick[index] == state.tick()) {
                float error = history[index].dst(p.position);
                if (error > 0.12f) {
                    if (error < 1.5f) correction.set(history[index]).sub(p.position);
                    else correction.setZero();
                    link.noteCorrection();
                }
            }
        }
        int simulated = state.tick();
        NetworkInput held = link.heldAt(simulated);
        for (NetworkInput input : link.inputsAfter(simulated)) {
            int until = input.command().tick();
            if (until - state.tick() > 28) break;
            while (simulated != until) {
                simulated++;
                if (simulated == until) {
                    held = input;
                    held.applyTo(intent, NetPacket.newer(simulated, state.inputTick));
                } else if (held != null) held.applyTo(intent, false);
                else intent.clear();
                locomotion.update(p, intent, 1f / 120f);
                int index = simulated & (history.length - 1);
                history[index].set(p.position);
                recordedTick[index] = simulated;
                valid[index] = true;
            }
        }
        correction.scl((float) Math.exp(-18f * dt));
        p.position.add(correction);
        p.previousPosition.set(p.position);
        p.previousFacingYaw = p.facingYaw;
        player = p.id;
        tick = state.tick();
    }
}
