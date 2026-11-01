package com.volleyball.game.render;

import com.badlogic.gdx.math.MathUtils;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchListener;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.world.CourtSide;

import java.util.Random;

/**
 * How the crowd feels, which drives the spectators' animation: tension grows during long rallies
 * (more bobbing and clapping), a home point brings everybody up with their arms raised, an away
 * point a smaller reaction (the away fans), and now and then a wave runs around the stands.
 */
public final class CrowdMood implements MatchListener {
    static final float WAVE_SPEED = 0.075f;
    static final float WAVE_DURATION = 14f;

    private final Random random = new Random(5);
    private float excitement = 0.2f;
    private float cheer;
    private float waveFront;
    private float waveTime = -1f;
    private float sinceWave;
    private float nextWave = 40f;
    private boolean rallyLive;
    private float rallyTime;

    @Override
    public void onServeWhistle(VolleyballPlayer server, MatchScore score) {
        rallyLive = true;
        rallyTime = 0f;
    }

    @Override
    public void onPoint(MatchRules.PointSummary point, MatchScore score) {
        rallyLive = false;
        if (point.outcome().isReplay()) return;
        boolean home = point.outcome().winner() == CourtSide.HOME;
        // Longer rallies get a bigger reaction; set and match points bring the house down.
        float size = 0.65f + 0.35f * Math.min(1f, rallyTime / 10f);
        if (point.setWon() || point.matchWon()) size = 1f;
        cheer = Math.max(cheer, home ? size : 0.3f * size);
        excitement = Math.min(1f, excitement + 0.2f);
    }

    /** Advances the mood; call once per frame. */
    public void update(float dt) {
        if (rallyLive) rallyTime += dt;
        float target = rallyLive ? 0.25f + 0.55f * Math.min(1f, rallyTime / 9f) : 0.18f;
        excitement += (target - excitement) * Math.min(1f, dt * 0.8f);
        cheer = Math.max(0f, cheer - dt * 0.35f);
        // The wave: now and then, when the game is calm.
        sinceWave += dt;
        if (waveTime < 0f && sinceWave > nextWave && cheer < 0.1f) {
            waveTime = 0f;
            waveFront = random.nextFloat();
        }
        if (waveTime >= 0f) {
            waveTime += dt;
            waveFront = (waveFront + WAVE_SPEED * dt) % 1f;
            if (waveTime > WAVE_DURATION) {
                waveTime = -1f;
                sinceWave = 0f;
                nextWave = 50f + random.nextFloat() * 60f;
            }
        }
    }

    /** Packs the mood for the shader: excitement, cheer, wave front, wave strength. */
    void write(float[] out) {
        out[0] = excitement;
        out[1] = MathUtils.clamp(cheer, 0f, 1f);
        out[2] = waveFront;
        // The wave fades in and out.
        out[3] = waveTime < 0f ? 0f : Math.min(1f, Math.min(waveTime / 2f, (WAVE_DURATION - waveTime) / 2f));
    }

    public float excitement() {
        return excitement;
    }

    public float cheer() {
        return cheer;
    }

    public boolean waveRunning() {
        return waveTime >= 0f;
    }
}
