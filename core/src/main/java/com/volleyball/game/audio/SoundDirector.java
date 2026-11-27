package com.volleyball.game.audio;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.BodyState;
import com.volleyball.game.entities.GaitCycle;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchListener;
import com.volleyball.game.match.ReplayRecorder;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.BallTouchListener;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.mechanics.ServeType;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.BallContactListener;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.rules.Fault;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.CourtTheme;
import com.volleyball.game.world.Venue;

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
    private static final float PLANT_WINDOW = 0.15f;
    private static final float PLANT_INTERVAL = 0.28f;
    static final float CROWD_BASE = 0.32f;
    static final float CROWD_PRACTICE = 0.12f;

    private final GameWorld world;
    private final SoundSink sink;
    private final Random random;
    private final Random movementRandom;
    private final Vector3 listener = new Vector3(0f, 6f, 20f);
    private final Vector3 listenerRight = new Vector3(1f, 0f, 0f);
    private final EnumMap<SoundId, Float> lastPlayed = new EnumMap<>(SoundId.class);
    private final EnumMap<SoundId, Integer> lastContactTake = new EnumMap<>(SoundId.class);
    private final Map<Integer, PlayerTrack> tracks = new HashMap<>();
    private final List<Delayed> delayed = new ArrayList<>();
    private final List<Reflection> reflections = new ArrayList<>();

    private float clock;
    private float movementClock;
    private boolean matchActive;
    private boolean rallyLive;
    private float rallyTime;
    private int rallyTouches;
    private float crowdLift;
    private float lastSurprise = -10f;
    private float lastTeamCue = -10f;
    private boolean teamCuePlayed;
    private boolean importantServe;
    private boolean menuOpen;
    private boolean paused;
    private Venue venue = Venue.CHAMPIONSHIP;
    private SoundId footstep = SoundId.FOOTSTEP;
    private SoundId plant = SoundId.PLANT;
    private SoundId ambience = SoundId.CROWD_AMBIENT;
    private Acoustics room = Acoustics.CHAMPIONSHIP;
    private ReplayRecorder replay;

    private enum Acoustics {
        CHAMPIONSHIP(0.082f, 0.15f, 1f), BEACH(0f, 0f, 0.98f), SCHOOL_GYM(0.058f, 0.19f, 1.015f),
        SPORTS_PALACE(0.113f, 0.19f, 0.96f), ROOFTOP(0.073f, 0.065f, 1.025f);

        final float reflectionDelay;
        final float reflectionGain;
        final float contactPitch;

        Acoustics(float delay, float gain, float pitch) {
            reflectionDelay = delay;
            reflectionGain = gain;
            contactPitch = pitch;
        }
    }

    private static final class PlayerTrack {
        boolean airborne;
        BodyState bodyState = BodyState.UPRIGHT;
        float vx;
        float vz;
        float vy;
        float lastPlant = -10f;
        float stepAfter;
        float brakingUntil;
        float brakingSpeed;
        float strideCycles = Float.NaN;
        final EnumMap<SoundId, SampleVariants> steps = new EnumMap<>(SoundId.class);
        /** Each player has a voice pitch of their own. */
        float voicePitch;
    }

    private record Delayed(float at, SoundId id, float volume, float pitch, float pan) {
    }

    private record Reflection(float at, SoundId id, int take, float volume, float pitch, Vector3 position) {
    }

    public SoundDirector(GameWorld world, SoundSink sink, long seed) {
        this.world = world;
        this.sink = sink;
        this.random = new Random(seed);
        this.movementRandom = new Random(seed ^ 0x2e145dL);
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
        if (!active) {
            rallyLive = false;
            importantServe = false;
        }
    }

    /** The title screen plays the menu theme above a quieter crowd. */
    public void setMenuOpen(boolean open) {
        menuOpen = open;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void setCourtSurface(Venue venue, CourtTheme theme) {
        if (this.venue != venue) {
            reflections.clear();
            room = Acoustics.valueOf(venue.name());
        }
        this.venue = venue;
        SoundId nextAmbience = switch (venue) {
            case CHAMPIONSHIP -> SoundId.CROWD_AMBIENT;
            case BEACH -> SoundId.CROWD_BEACH;
            case SCHOOL_GYM -> SoundId.CROWD_GYM;
            case SPORTS_PALACE -> SoundId.CROWD_PALACE;
            case ROOFTOP -> SoundId.CROWD_ROOFTOP;
        };
        if (ambience != nextAmbience) {
            sink.setLoop(ambience, 0f);
            ambience = nextAmbience;
        }
        footstep = switch (venue) {
            case BEACH -> SoundId.FOOTSTEP_SAND;
            case ROOFTOP -> SoundId.FOOTSTEP_CONCRETE;
            case SCHOOL_GYM, SPORTS_PALACE -> SoundId.FOOTSTEP_WOOD;
            case CHAMPIONSHIP -> theme == CourtTheme.WOOD_GYM ? SoundId.FOOTSTEP_WOOD : SoundId.FOOTSTEP;
        };
        plant = switch (footstep) {
            case FOOTSTEP_SAND -> SoundId.PLANT_SAND;
            case FOOTSTEP_CONCRETE -> SoundId.PLANT_ROOF;
            case FOOTSTEP_WOOD -> SoundId.PLANT_WOOD;
            default -> SoundId.PLANT;
        };
    }

    public void stopAmbience() {
        sink.setLoop(ambience, 0f);
    }

    public void setReplayRecorder(ReplayRecorder replay) {
        this.replay = replay;
    }

    public void beginReplay() {
        reflections.clear();
    }

    public void playReplaySound(ReplayRecorder.CourtSound cue, boolean slow) {
        playSpatial(cue.id(), cue.take(), cue.position(), cue.volume(), cue.pitch() * (slow ? 0.88f : 1f), false, cue.reflection());
    }

    /** A single positional call from the teammate assigned to receive a reachable ball. */
    public void signalReceiver(VolleyballPlayer receiver) {
        if (!matchActive || !rallyLive || paused || teamCuePlayed || clock - lastTeamCue < 7f) return;
        teamCuePlayed = true;
        lastTeamCue = clock;
        playAt(SoundId.TEAM_RECEIVE_CUE, receiver.position, 0.27f, 1f);
    }

    // ------------------------------------------------------------------ ball
    @Override
    public void onServeToss(VolleyballPlayer server) {
        playAt(SoundId.SERVE_TOSS, world.ball().position, 0.26f, variedPitch(0.06f));
    }

    private static SoundId serveSound(String variant) {
        if (ServeType.UNDERHAND.label.equals(variant)) return SoundId.SERVE_UNDERHAND;
        if (ServeType.JUMP_FLOAT.label.equals(variant)) return SoundId.SERVE_JUMP_FLOAT;
        if (variant != null && variant.startsWith(ServeType.JUMP_TOPSPIN.label)) return SoundId.SERVE_JUMP_TOPSPIN;
        if (variant != null && variant.startsWith(ServeType.POWER.label)) return SoundId.SERVE_POWER;
        return SoundId.SERVE_FLOAT;
    }

    @Override
    public void onBallTouch(BallTouch touch) {
        Vector3 at = touch.contactPoint();
        float speed = touch.outgoingVelocity().len();
        float impact = touch.impactSpeed();
        float force = clamp01((impact - 5f) / 24f);
        float contact = 0.86f + 0.14f * clamp01(touch.quality());
        VolleyballPlayer player = world.player(touch.playerId());
        if (rallyLive) rallyTouches++;
        switch (touch.type()) {
            case SERVE -> {
                hit(serveSound(touch.variant()), at, contact * (0.72f + 0.28f * clamp01((speed - 12f) / 14f)),
                        variedPitch(0.025f), force);
                if (touch.airborne() && player != null) voice(SoundId.GRUNT, player, 0.32f);
                rallyLive = true;
                rallyTime = 0f;
                rallyTouches = 0;
                crowdLift = 0f;
                importantServe = false;
                teamCuePlayed = false;
            }
            case ATTACK -> {
                boolean soft = speed < 13f || AttackType.TIP.label.equals(touch.variant()) || AttackType.ROLL_SHOT.label.equals(touch.variant())
                        || AttackType.FEINT.label.equals(touch.variant());
                if (AttackType.ROLL_SHOT.label.equals(touch.variant())) {
                    hit(SoundId.SPIKE_ROLL, at, contact * (0.58f + 0.15f * force), variedPitch(0.035f), force);
                } else if (soft) {
                    hit(SoundId.SPIKE_SOFT, at, contact * 0.53f, variedPitch(0.04f), force);
                } else {
                    hit(SoundId.SPIKE_HARD, at, contact * (0.79f + 0.21f * force),
                            (1.02f - 0.09f * force) * variedPitch(0.025f), force);
                    if (player != null && random.nextFloat() < 0.55f) voice(SoundId.GRUNT, player, 0.36f);
                }
            }
            case BLOCK -> {
                hit(SoundId.BLOCK, at, 0.59f + 0.41f * force, variedPitch(0.03f), force);
                if (impact >= 14f && rallyTouches >= 5) crowdSurprise();
            }
            case DIVE, ONE_ARM -> {
                hit(touch.type() == ContactType.DIVE ? SoundId.DIG : SoundId.DIG_ONE_ARM, at,
                        0.54f + 0.32f * force, variedPitch(0.045f), force);
                crowdSurprise();
            }
            case FOREARM_PASS -> hit(impact >= 17f ? SoundId.FOREARM_HARD : SoundId.FOREARM_PASS, at,
                    contact * (0.49f + 0.43f * force), (1.04f - 0.08f * force) * variedPitch(0.035f), force);
            case OVERHEAD_PASS, ONE_HAND_SET -> hit(SoundId.OVERHEAD_SET, at,
                    contact * (0.43f + 0.17f * force), variedPitch(0.035f), force);
        }
    }

    private void hit(SoundId id, Vector3 at, float volume, float pitch, float force) {
        float surfaceBias = switch (venue) {
            case BEACH -> -0.13f;
            case ROOFTOP -> 0.11f;
            default -> 0f;
        };
        int take = Math.round(clamp01(force + surfaceBias + (random.nextFloat() - 0.5f) * 0.63f) * (id.variants - 1));
        if (id.variants > 1 && take == lastContactTake.getOrDefault(id, -1)) {
            take += take == 0 ? 1 : take == id.variants - 1 ? -1 : random.nextBoolean() ? 1 : -1;
        }
        lastContactTake.put(id, take);
        if (!menuOpen && force >= 0.35f) sink.duckCrowd(0.67f, 0.14f);
        if (!playSpatial(id, take, at, volume, pitch * room.contactPitch, true, false)) return;
        if (!menuOpen && force >= 0.42f && room.reflectionGain > 0f) {
            reflections.add(new Reflection(clock + room.reflectionDelay, id, take,
                    volume * room.reflectionGain, pitch * 0.91f, at.cpy()));
        }
    }

    private void crowdSurprise() {
        if (!matchActive || !rallyLive) return;
        crowdLift = Math.min(0.14f, crowdLift + 0.055f);
        if (clock - lastSurprise >= 4f) {
            lastSurprise = clock;
            play(SoundId.CROWD_OOH, 0.28f, variedPitch(0.025f), 0f);
        }
    }

    @Override
    public void onBallContact(BallContact contact) {
        float speed = contact.impactSpeed();
        Vector3 at = contact.position();
        switch (contact.kind()) {
            case FLOOR -> {
                if (speed > 0.8f) {
                    boolean sand = venue == Venue.BEACH;
                    SoundId bounce = sand ? SoundId.BALL_SAND : venue == Venue.ROOFTOP ? SoundId.BALL_ROOF : SoundId.BALL_FLOOR;
                    playAt(bounce, at, clamp01(speed / 10f) * (sand ? 0.72f : 1f),
                            variedPitch(0.035f) * (0.94f + 0.06f * clamp01(speed / 10f)));
                }
            }
            case NET_MESH, NET_BAND -> {
                if (speed > 0.5f) playAt(contact.kind() == ColliderKind.NET_BAND
                                ? SoundId.BALL_TAPE : SoundId.BALL_NET,
                        at, 0.3f + 0.7f * clamp01(speed / 12f), variedPitch(0.06f));
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
        importantServe = score.isSetPoint(CourtSide.HOME) || score.isSetPoint(CourtSide.AWAY);
        sink.duckCrowd(0.35f, 0.40f);
        play(SoundId.WHISTLE_SHORT, 0.75f, variedPitch(0.03f), 0f);
        if (importantServe) return;
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
        importantServe = false;
        sink.duckCrowd(0.35f, point.setWon() ? 0.55f : 0.36f);
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
        if (point.setWon()) {
            later(0.9f, SoundId.SET_HORN, 0.7f, 1f, 0f);
            if (homeWon && !point.matchWon()) later(1.25f, SoundId.CROWD_CHANT, 0.34f, 0.97f, 0f);
        }
        if (point.matchWon()) later(1.4f, SoundId.CROWD_CHEER, homeWon ? 1f : 0.5f, 0.95f, 0f);
    }

    // ------------------------------------------------------------------ per frame

    /** Call once per rendered frame for delayed sounds and the crowd loop. */
    public void update(float dt) {
        clock += dt;
        if (rallyLive) rallyTime += dt;
        crowdLift *= (float) Math.exp(-0.8f * dt);
        for (int i = delayed.size() - 1; i >= 0; i--) {
            Delayed d = delayed.get(i);
            if (clock >= d.at) {
                play(d.id, d.volume, d.pitch, d.pan);
                delayed.remove(i);
            }
        }
        for (int i = reflections.size() - 1; i >= 0; i--) {
            Reflection reflection = reflections.get(i);
            if (clock >= reflection.at()) {
                playSpatial(reflection.id(), reflection.take(), reflection.position(), reflection.volume(), reflection.pitch(), true, true);
                reflections.remove(i);
            }
        }
        float crowd = matchActive ? CROWD_BASE + (rallyLive ? 0.14f * clamp01(rallyTime / 11f) + crowdLift : 0f) : CROWD_PRACTICE;
        if (importantServe) crowd *= 0.30f;
        sink.setLoop(ambience, menuOpen || paused ? crowd * 0.4f : crowd);
        sink.setLoop(SoundId.MENU_MUSIC, menuOpen ? 0.72f : 0f);
    }

    /** Samples the same fixed simulation steps that advance the players' gait and velocity. */
    public void updateMovement(float dt) {
        movementClock += dt;
        for (VolleyballPlayer p : world.players()) trackPlayer(p, dt);
    }

    private void trackPlayer(VolleyballPlayer p, float dt) {
        PlayerTrack track = tracks.get(p.id);
        if (track == null) return;
        boolean airborne = p.isAirborne();
        float cycles = p.strideCycles();
        float speed = (float) Math.hypot(track.vx, track.vz);
        float groundSpeed = p.groundSpeed();
        if (p.bodyState() == BodyState.ON_FLOOR && track.bodyState == BodyState.DIVING) {
            playAt(venue.outdoor ? landingSound() : SoundId.BODY_FALL, p.position, 0.68f, variedPitch(0.05f));
            float slide = clamp01(groundSpeed / 5f);
            SoundId slideSound = switch (venue) {
                case BEACH -> SoundId.DIVE_SLIDE_SAND;
                case ROOFTOP -> SoundId.DIVE_SLIDE_ROOF;
                default -> SoundId.DIVE_SLIDE;
            };
            if (slide > 0.15f) playAt(slideSound, p.position, 0.12f + 0.29f * slide, variedPitch(0.05f));
        } else if (track.airborne && !airborne) {
            float impact = clamp01((-track.vy - 1.5f) / 4f);
            playAt(landingSound(), p.position,
                    0.24f + 0.33f * impact, (1.02f - 0.1f * impact) * variedPitch(0.04f));
            track.stepAfter = movementClock + 0.095f;
        } else if (p.isOnCourt() && p.isUpright()) {
            if (!track.airborne && airborne) {
                if (movementClock >= track.stepAfter) {
                    if (speed >= 2.8f) playPlant(p, track, speed);
                    else playStep(p, track, venue.outdoor ? footstep : SoundId.JUMP_TAKEOFF, 0.13f, 1f);
                }
            } else if (!airborne) {
                trackGroundStep(p, track, dt, speed, groundSpeed, cycles);
            }
        }
        if (airborne || !p.isOnCourt() || !p.isUpright()) track.brakingUntil = 0f;
        track.strideCycles = cycles;
        track.airborne = airborne;
        track.bodyState = p.bodyState();
        track.vx = p.velocity.x;
        track.vz = p.velocity.z;
        track.vy = p.velocity.y;
    }

    private void trackGroundStep(VolleyballPlayer p, PlayerTrack track, float dt, float speed, float groundSpeed, float cycles) {
        // A lateral cut or deceleration loads the supporting foot. Pure acceleration does not.
        if (speed >= 2.8f) {
            float forwardLoss = speed - (p.velocity.x * track.vx + p.velocity.z * track.vz) / speed;
            float sidewaysChange = Math.abs(p.velocity.x * track.vz - p.velocity.z * track.vx) / speed;
            if (forwardLoss > 8f * dt || sidewaysChange > 11f * dt) {
                if (movementClock >= track.brakingUntil) track.brakingSpeed = 0f;
                track.brakingSpeed = Math.max(track.brakingSpeed, speed);
                track.brakingUntil = movementClock + PLANT_WINDOW;
            }
        }
        boolean footfall = !Float.isNaN(track.strideCycles) && GaitCycle.crossedFootfall(track.strideCycles, cycles);
        boolean braking = movementClock < track.brakingUntil;
        boolean stopped = speed > 0.7f && groundSpeed <= 0.7f;
        if (movementClock >= track.stepAfter && braking && (footfall || stopped) && movementClock - track.lastPlant >= PLANT_INTERVAL) {
            playPlant(p, track, track.brakingSpeed);
        } else if (footfall && movementClock >= track.stepAfter && groundSpeed > 0.55f) {
            float hustle = clamp01((groundSpeed - 0.55f) / 5.5f);
            float lateral = Math.abs(p.velocity.z * p.facingX() - p.velocity.x * p.facingZ()) / groundSpeed;
            float level = (0.068f + 0.108f * hustle) * (1f - 0.27f * lateral);
            playStep(p, track, footstep, level, 0.94f + 0.10f * hustle - 0.04f * lateral);
        }
    }

    private void playPlant(VolleyballPlayer p, PlayerTrack track, float speed) {
        track.lastPlant = movementClock;
        track.stepAfter = movementClock + 0.11f;
        track.brakingUntil = 0f;
        playStep(p, track, plant, 0.15f + 0.10f * clamp01((speed - 2.8f) / 4f), 1f);
    }

    private void playStep(VolleyballPlayer p, PlayerTrack track, SoundId id, float volume, float pitch) {
        int take = track.steps.computeIfAbsent(id, key -> new SampleVariants(key.variants, movementRandom)).next();
        float variation = movementRandom.nextFloat() - 0.5f;
        playSpatial(id, take, p.position, volume * (1f + 0.12f * variation), pitch + 0.055f * variation, true, false);
    }

    private SoundId landingSound() {
        return switch (venue) {
            case BEACH -> SoundId.LANDING_SAND;
            case ROOFTOP -> SoundId.LANDING_ROOF;
            default -> SoundId.LANDING;
        };
    }

    // ------------------------------------------------------------------ output

    private void voice(SoundId id, VolleyballPlayer p, float volume) {
        PlayerTrack track = tracks.get(p.id);
        float pitch = (track == null ? 1f : track.voicePitch) * variedPitch(0.04f);
        playAt(id, p.position, volume, pitch);
    }

    /** Plays a sound coming from a point in the arena. */
    private void playAt(SoundId id, Vector3 at, float volume, float pitch) {
        playSpatial(id, -1, at, volume, pitch, true, false);
    }

    private boolean playSpatial(SoundId id, int take, Vector3 at, float volume, float pitch, boolean live, boolean reflection) {
        float dx = at.x - listener.x;
        float dy = at.y - listener.y;
        float dz = at.z - listener.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float attenuation = 1f / (1f + Math.max(0f, distance - 4f) / 12f);
        float pan = distance > 1e-3f ? (dx * listenerRight.x + dz * listenerRight.z) / distance * 0.8f : 0f;
        boolean step = id == footstep || id == plant || id == SoundId.JUMP_TAKEOFF;
        float stepRange = step ? clamp01((20f - distance) / 13f) : 1f;
        float level = clamp01(volume * attenuation * stepRange);
        if (reflection) {
            sink.reflect(id, take, level, pitch, pan);
        } else if (!live) {
            sink.playTake(id, take, level, pitch, pan);
        } else if (!play(id, take, level, pitch, pan)) {
            return false;
        }
        if (live && replay != null) replay.recordSound(id, take, at, volume, pitch, reflection);
        return true;
    }

    private boolean play(SoundId id, float volume, float pitch, float pan) {
        return play(id, -1, volume, pitch, pan);
    }

    private boolean play(SoundId id, int take, float volume, float pitch, float pan) {
        if (id != footstep && id != plant && id != SoundId.JUMP_TAKEOFF) {
            Float last = lastPlayed.get(id);
            if (last != null && clock - last < REPEAT_GUARD) return false;
            lastPlayed.put(id, clock);
        }
        sink.playTake(id, take, clamp01(menuOpen ? volume * 0.20f : volume), Math.max(0.5f, Math.min(2f, pitch)),
                Math.max(-1f, Math.min(1f, pan)));
        return true;
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
