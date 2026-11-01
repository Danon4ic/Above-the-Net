package com.volleyball.game.match;

import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.VolleyballPlayer;

import java.util.List;

/**
 * Records the rally (every player's look and the ball, 60 times a second) from the serve until
 * a replay is asked for, and plays it back: {@link #apply} puts the players and the ball where
 * they were at a moment of the rally (interpolated, so slow motion is smooth). The live state is
 * saved before a replay and restored afterwards ({@link #saveLive}, {@link #restoreLive}).
 */
public final class ReplayRecorder {
    static final float FRAME = 1f / 60f;
    /** At most this many seconds are kept (the end of a longer rally). */
    static final float MAX_SECONDS = 30f;

    private static final class Frame {
        float time;
        final VolleyballPlayer.VisualState[] players;
        final Ball.VisualState ball = new Ball.VisualState();

        Frame(int playerCount) {
            players = new VolleyballPlayer.VisualState[playerCount];
            for (int i = 0; i < playerCount; i++) players[i] = new VolleyballPlayer.VisualState();
        }
    }

    private final List<VolleyballPlayer> players;
    private final Ball ball;
    /** A ring of frames (reused, no garbage while recording). */
    private final Frame[] ring;
    private int first;
    private int count;
    private float clock;
    private float sinceFrame;
    private boolean recording;

    private final Frame live;
    private final VolleyballPlayer.VisualState[] blended;
    private final Ball.VisualState blendedBall = new Ball.VisualState();

    public ReplayRecorder(List<VolleyballPlayer> players, Ball ball) {
        this.players = players;
        this.ball = ball;
        ring = new Frame[(int) (MAX_SECONDS / FRAME) + 1];
        live = new Frame(players.size());
        blended = new VolleyballPlayer.VisualState[players.size()];
        for (int i = 0; i < blended.length; i++) blended[i] = new VolleyballPlayer.VisualState();
    }

    /** A new rally: forget the old one and record. */
    public void start() {
        first = 0;
        count = 0;
        clock = 0f;
        sinceFrame = FRAME;
        recording = true;
    }

    public void stop() {
        recording = false;
    }

    public boolean isRecording() {
        return recording;
    }

    /** Records a frame if it is time; call after each simulation step. */
    public void capture(float dt) {
        if (!recording) return;
        clock += dt;
        sinceFrame += dt;
        if (sinceFrame < FRAME) return;
        sinceFrame = 0f;
        int index;
        if (count < ring.length) {
            index = (first + count) % ring.length;
            count++;
        } else {
            index = first;
            first = (first + 1) % ring.length;
        }
        if (ring[index] == null) ring[index] = new Frame(players.size());
        Frame f = ring[index];
        f.time = clock;
        for (int i = 0; i < players.size(); i++) players.get(i).saveVisual(f.players[i]);
        ball.saveVisual(f.ball);
    }

    /** True if there is a rally to show (at least a second of it). */
    public boolean hasReplay() {
        return count > 60;
    }

    /** Length of the recording in seconds. */
    public float duration() {
        return count < 2 ? 0f : frame(count - 1).time - frame(0).time;
    }

    private Frame frame(int i) {
        return ring[(first + i) % ring.length];
    }

    /** Shows the moment {@code t} seconds into the recording. */
    public void apply(float t) {
        if (count < 2) return;
        float start = frame(0).time;
        float target = start + Math.max(0f, Math.min(duration(), t));
        // Binary search for the frames around the moment.
        int lo = 0;
        int hi = count - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (frame(mid).time <= target) lo = mid;
            else hi = mid;
        }
        Frame a = frame(lo);
        Frame b = frame(hi);
        float span = b.time - a.time;
        float k = span <= 0f ? 0f : (target - a.time) / span;
        for (int i = 0; i < players.size(); i++) {
            blended[i].interpolate(a.players[i], b.players[i], k);
            players.get(i).restoreVisual(blended[i]);
        }
        blendedBall.interpolate(a.ball, b.ball, k);
        ball.restoreVisual(blendedBall);
    }

    /** Saves the current (live) state before a replay. */
    public void saveLive() {
        for (int i = 0; i < players.size(); i++) players.get(i).saveVisual(live.players[i]);
        ball.saveVisual(live.ball);
    }

    /** Puts the live state back after a replay. */
    public void restoreLive() {
        for (int i = 0; i < players.size(); i++) players.get(i).restoreVisual(live.players[i]);
        ball.restoreVisual(live.ball);
    }
}
