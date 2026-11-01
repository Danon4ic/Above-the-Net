package com.volleyball.game.audio;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchListener;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallHandling;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.BallTouchListener;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.BallContactListener;
import com.volleyball.game.rules.Fault;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtDimensions;
import com.volleyball.game.world.CourtSide;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Decides what is heard. It listens to ball touches, physical ball contacts and match events,
 * watches the players (landings, falls, sharp stops) and turns all of that into sounds with a
 * volume (how hard), a pitch (a little variety, and each player's own voice) and a stereo
 * position relative to the camera.
 *
 * <p>The crowd supports the home team: it cheers home points, groans at home errors and
 * opponent kills, claps while the home team prepares to serve, and the arena murmur rises
 * with the length of a rally.
 */
public final class SoundDirector implements BallTouchListener, BallContactListener, MatchListener {
    /** The same sound is not started again within this time (ball rolling, rapid contacts). */
    static final float REPEAT_GUARD = 0.05f;
    static final float SQUEAK_DECELERATION = 9f;
    static final float SQUEAK_INTERVAL = 0.35f;
    static final float CROWD_BASE = 0.32f;
    static final float CROWD_PRACTICE = 0.12f;

    private final GameWorld world;
    private final SoundSink sink;
    private final Random random;
    private final Vector3 listener = new Vector3(0f, 6f, 20f);
    private final Vector3 listenerRight = new Vector3(1f, 0f, 0f);
    private final EnumMap<SoundId, Float> lastPlayed = new EnumMap<>(SoundId.class);
    private final Map<Integer, PlayerTrack> tracks = new HashMap<>();
    private final List<Delayed> delayed = new ArrayList<>();

    private float clock;
    private boolean matchActive;
    private boolean rallyLive;
    private float rallyTime;
    private boolean menuOpen;
    private int calledToucher = Integer.MIN_VALUE;

    private static final class PlayerTrack {
        boolean airborne;
        BodyState bodyState = BodyState.UPRIGHT;
        float vx;
        float vz;
        float lastSqueak = -10f;
        boolean armed;
        /** Each player has a voice pitch of their own. */
        float voicePitch;
    }

    private record Delayed(float at, SoundId id, float volume, float pitch, float pan) {
    }

    public SoundDirector(GameWorld world, SoundSink sink, long seed) {
        this.world = world;
        this.sink = sink;
        this.random = new Random(seed);
        for (VolleyballPlayer p : world.players()) {
            PlayerTrack track = new PlayerTrack();
            track.voicePitch = 0.85f + random.nextFloat() * 0.3f;
            tracks.put(p.id, track);
        }
    }

    /** Subscribes to the world's ball touches and contacts. */
    public SoundDirector attach() {
        world.ballHandling().addListener(this);
        world.addContactListener(this);
        return this;
    }

    /** The camera: sounds are panned and attenuated relative to it. */
    public void setListener(Vector3 position, Vector3 direction) {
        listener.set(position);
        // Right on the floor plane = forward × up.
        listenerRight.set(-direction.z, 0f, direction.x);
        if (listenerRight.len2() < 1e-6f) listenerRight.set(1f, 0f, 0f);
        listenerRight.nor();
    }

    /** A match is running (crowd at full size) or a practice drill (quiet arena). */
    public void setMatchActive(boolean active) {
        matchActive = active;
        if (!active) rallyLive = false;
    }

    /** While a menu is open the game is paused: the menu theme plays and the crowd is quieter. */
    public void setMenuOpen(boolean open) {
        menuOpen = open;
    }

    // ------------------------------------------------------------------ ball

    @Override
    public void onBallTouch(BallTouch touch) {
        Vector3 at = touch.contactPoint();
        float speed = touch.outgoingVelocity().len();
        VolleyballPlayer player = world.player(touch.playerId());
        switch (touch.type()) {
            case SERVE -> {
                boolean power = speed > 19f;
                playAt(power ? SoundId.SERVE_POWER : SoundId.SERVE_FLOAT, at, 0.75f + 0.25f * clamp01((speed - 12f) / 14f), variedPitch(0.05f));
                if (touch.airborne() && player != null) voice(SoundId.GRUNT, player, 0.55f);
                rallyLive = true;
                rallyTime = 0f;
            }
            case ATTACK -> {
                boolean soft = speed < 13f || AttackType.TIP.label.equals(touch.variant()) || AttackType.ROLL_SHOT.label.equals(touch.variant());
                if (soft) {
                    playAt(SoundId.SPIKE_SOFT, at, 0.55f, variedPitch(0.06f));
                } else {
                    playAt(SoundId.SPIKE_HARD, at, 0.65f + 0.35f * clamp01((speed - 13f) / 17f), variedPitch(0.05f));
                    if (player != null && random.nextFloat() < 0.75f) voice(SoundId.GRUNT, player, 0.7f);
                }
            }
            case BLOCK -> playAt(SoundId.BLOCK, at, 0.6f + 0.4f * clamp01(speed / 20f), variedPitch(0.05f));
            case DIVE, ONE_ARM -> playAt(SoundId.DIG, at, 0.7f, variedPitch(0.06f));
            case FOREARM_PASS -> playAt(SoundId.FOREARM_PASS, at, 0.45f + 0.4f * clamp01(speed / 14f), variedPitch(0.07f));
            case OVERHEAD_PASS -> playAt(SoundId.OVERHEAD_SET, at, 0.45f, variedPitch(0.07f));
        }
    }

    @Override
    public void onBallContact(BallContact contact) {
        float speed = contact.impactSpeed();
        Vector3 at = contact.position();
        switch (contact.kind()) {
            case FLOOR -> {
                if (speed > 0.8f) playAt(SoundId.BALL_FLOOR, at, clamp01(speed / 12f), variedPitch(0.05f) * (0.9f + 0.1f * clamp01(speed / 10f)));
            }
            case NET_MESH, NET_BAND -> {
                if (speed > 0.5f) playAt(SoundId.BALL_NET, at, 0.3f + 0.7f * clamp01(speed / 12f), variedPitch(0.08f));
            }
            case ANTENNA, POST -> playAt(SoundId.BALL_METAL, at, 0.4f + 0.6f * clamp01(speed / 10f), variedPitch(0.03f));
            case WALL, CEILING -> playAt(SoundId.BALL_WALL, at, clamp01(speed / 12f), variedPitch(0.05f));
            case PLAYER_BODY -> playAt(SoundId.BALL_BODY, at, 0.3f + 0.5f * clamp01(speed / 12f), variedPitch(0.08f));
            case BLOCK -> {
                // reported as a block touch by the ball handling
            }
        }
    }

    // ------------------------------------------------------------------ match

    @Override
    public void onServeWhistle(VolleyballPlayer server, MatchScore score) {
        matchActive = true;
        rallyLive = false;
        play(SoundId.WHISTLE_SHORT, 0.75f, variedPitch(0.03f), 0f);
        // Set point or match point: the stands chant and stamp before the serve.
        if (score.isSetPoint(CourtSide.HOME) || score.isSetPoint(CourtSide.AWAY)) {
            play(SoundId.CROWD_CHANT, 0.7f, variedPitch(0.03f), 0f);
            return;
        }
        // Home fans clap the rhythm while their team serves.
        if (server.side == CourtSide.HOME && random.nextFloat() < 0.6f) later(0.5f, SoundId.CROWD_CLAPS, 0.5f, 1f, 0f);
    }

    /** The timeout buzzer; the crowd claps the break. */
    @Override
    public void onTimeout(CourtSide side, int used) {
        play(SoundId.TIMEOUT_BUZZER, 0.7f, 1f, 0f);
        later(0.6f, SoundId.CROWD_CLAPS, 0.45f, 1f, 0f);
    }

    /** The substitution buzzer from the scorer's table. */
    @Override
    public void onSubstitution(CourtSide side, VolleyballPlayer out, VolleyballPlayer in, int used) {
        play(SoundId.TIMEOUT_BUZZER, 0.55f, 1.1f, 0f);
    }

    @Override
    public void onPoint(MatchRules.PointSummary point, MatchScore score) {
        rallyLive = false;
        if (point.outcome().isReplay()) {
            play(SoundId.WHISTLE_SHORT, 0.7f, 1f, 0f);
            return;
        }
        boolean homeWon = point.outcome().winner() == CourtSide.HOME;
        boolean kill = point.outcome().reason() == Fault.BALL_IN;
        play(point.setWon() ? SoundId.WHISTLE_LONG : SoundId.WHISTLE_SHORT, 0.8f, 1f, 0f);
        // Longer rallies get a louder reaction.
        float excitement = 0.6f + 0.4f * clamp01(rallyTime / 12f);
        if (homeWon) {
            later(0.15f, SoundId.CROWD_CHEER, (kill ? 0.9f : 0.7f) * excitement, variedPitch(0.04f), 0f);
            later(0.1f, SoundId.POINT_CHIME, 0.35f, 1f, 0f);
        } else {
            later(0.15f, kill ? SoundId.CROWD_GROAN : SoundId.CROWD_OOH, 0.7f * excitement, variedPitch(0.04f), 0f);
        }
        if (point.setWon()) later(0.9f, SoundId.SET_HORN, 0.7f, 1f, 0f);
        if (point.matchWon()) later(1.4f, SoundId.CROWD_CHEER, homeWon ? 1f : 0.5f, 0.95f, 0f);
    }

    // ------------------------------------------------------------------ per frame

    /** Call once per rendered frame: player sounds, delayed sounds and the crowd loop. */
    public void update(float dt) {
        clock += dt;
        if (rallyLive) rallyTime += dt;
        if (dt > 0f) for (VolleyballPlayer p : world.players()) trackPlayer(p, dt);
        for (int i = delayed.size() - 1; i >= 0; i--) {
            Delayed d = delayed.get(i);
            if (clock >= d.at) {
                play(d.id, d.volume, d.pitch, d.pan);
                delayed.remove(i);
            }
        }
        float crowd = matchActive ? CROWD_BASE + (rallyLive ? 0.18f * clamp01(rallyTime / 10f) : 0f) : CROWD_PRACTICE;
        sink.setLoop(SoundId.CROWD_AMBIENT, menuOpen ? crowd * 0.4f : crowd);
        sink.setLoop(SoundId.MENU_MUSIC, menuOpen ? 0.6f : 0f);
    }

    private void trackPlayer(VolleyballPlayer p, float dt) {
        PlayerTrack track = tracks.get(p.id);
        if (track == null) return;
        if (track.airborne && !p.isAirborne()) playAt(SoundId.LANDING, p.position, 0.45f, variedPitch(0.1f));
        if (p.bodyState() == BodyState.ON_FLOOR && track.bodyState == BodyState.DIVING) {
            playAt(SoundId.BODY_FALL, p.position, 0.75f, variedPitch(0.08f));
        }
        // Sharp stops and cuts make the shoes squeak.
        float vx = p.velocity.x;
        float vz = p.velocity.z;
        float change = (float) Math.hypot(vx - track.vx, vz - track.vz);
        float speed = (float) Math.hypot(track.vx, track.vz);
        if (!p.isAirborne() && p.isUpright() && speed > 1.5f && change / dt > SQUEAK_DECELERATION
                && clock - track.lastSqueak > SQUEAK_INTERVAL && random.nextFloat() < 0.5f) {
            track.lastSqueak = clock;
            playAt(SoundId.SHOE_SQUEAK, p.position, 0.25f + 0.2f * clamp01(speed / 6f), variedPitch(0.12f));
        }
        // "Mine!": the first player of a team going for the opponent's ball calls it.
        BallHandling handling = world.ballHandling();
        boolean armed = handling.isArmed(p);
        if (armed && !track.armed && handling.lastTouchSide() != p.side && handling.lastTouchSide() != null
                && world.ball().isActive() && CourtDimensions.sideOf(world.ball().position.x) == p.side
                && calledToucher != handling.lastToucherId() && random.nextFloat() < 0.7f) {
            calledToucher = handling.lastToucherId();
            voice(SoundId.CALL, p, 0.55f);
        }
        track.armed = armed;
        track.airborne = p.isAirborne();
        track.bodyState = p.bodyState();
        track.vx = vx;
        track.vz = vz;
    }

    // ------------------------------------------------------------------ output

    private void voice(SoundId id, VolleyballPlayer p, float volume) {
        PlayerTrack track = tracks.get(p.id);
        float pitch = (track == null ? 1f : track.voicePitch) * variedPitch(0.04f);
        playAt(id, p.position, volume, pitch);
    }

    /** Plays a sound coming from a point in the arena. */
    private void playAt(SoundId id, Vector3 at, float volume, float pitch) {
        float dx = at.x - listener.x;
        float dy = at.y - listener.y;
        float dz = at.z - listener.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float attenuation = 1f / (1f + Math.max(0f, distance - 4f) / 12f);
        float pan = distance > 1e-3f ? (dx * listenerRight.x + dz * listenerRight.z) / distance * 0.8f : 0f;
        play(id, volume * attenuation, pitch, pan);
    }

    private void play(SoundId id, float volume, float pitch, float pan) {
        Float last = lastPlayed.get(id);
        if (last != null && clock - last < REPEAT_GUARD) return;
        lastPlayed.put(id, clock);
        sink.play(id, clamp01(volume), Math.max(0.5f, Math.min(2f, pitch)), Math.max(-1f, Math.min(1f, pan)));
    }

    private void later(float delay, SoundId id, float volume, float pitch, float pan) {
        delayed.add(new Delayed(clock + delay, id, volume, pitch, pan));
    }

    private float variedPitch(float spread) {
        return 1f + (random.nextFloat() * 2f - 1f) * spread;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
