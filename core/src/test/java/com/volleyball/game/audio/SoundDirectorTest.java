package com.volleyball.game.audio;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.mechanics.AttackType;
import com.volleyball.game.mechanics.BallTouch;
import com.volleyball.game.mechanics.ContactPurpose;
import com.volleyball.game.mechanics.ContactType;
import com.volleyball.game.physics.BallContact;
import com.volleyball.game.physics.ColliderKind;
import com.volleyball.game.rules.Fault;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.RallyOutcome;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Which sounds the game events produce, how loud and where. */
class SoundDirectorTest {
    record Played(SoundId id, float volume, float pitch, float pan) {
    }

    static final class RecordingSink implements SoundSink {
        final List<Played> played = new ArrayList<>();
        final java.util.Map<SoundId, Float> loops = new java.util.EnumMap<>(SoundId.class);

        @Override
        public void play(SoundId id, float volume, float pitch, float pan) {
            played.add(new Played(id, volume, pitch, pan));
        }

        @Override
        public void setLoop(SoundId id, float volume) {
            loops.put(id, volume);
        }

        Played last(SoundId id) {
            for (int i = played.size() - 1; i >= 0; i--) if (played.get(i).id() == id) return played.get(i);
            return null;
        }
    }

    GameWorld world;
    RecordingSink sink;
    SoundDirector director;

    @BeforeEach
    void setUp() {
        world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 1);
        sink = new RecordingSink();
        director = new SoundDirector(world, sink, 2);
        // Camera on the side line looking across the court (-z): +x is to the right.
        director.setListener(new Vector3(0f, 5f, 15f), new Vector3(0f, -0.3f, -1f));
    }

    private BallTouch touch(ContactType type, String variant, float speed, float x, double time) {
        VolleyballPlayer p = world.home().players().get(1);
        return new BallTouch(p.id, CourtSide.HOME, type, ContactPurpose.ATTACK, 0.8f, null, Float.NaN,
                new Vector3(x, 3f, 0f), new Vector3(x, 0f, 0f), true, 3, new Vector3(speed, -2f, 0f), variant, time);
    }

    @Test
    void hardSpikeIsLouderThanATipAndMayBringAGrunt() {
        director.onBallTouch(touch(ContactType.ATTACK, AttackType.POWER.label, 28f, -1f, 0));
        director.update(0.2f);
        director.onBallTouch(touch(ContactType.ATTACK, AttackType.TIP.label, 6f, -1f, 1));
        Played spike = sink.last(SoundId.SPIKE_HARD);
        Played tip = sink.last(SoundId.SPIKE_SOFT);
        assertNotNull(spike);
        assertNotNull(tip);
        assertTrue(spike.volume() > tip.volume());
    }

    @Test
    void ballContactsMapToTheirSurfacesAndRollingIsSilent() {
        director.onBallContact(new BallContact(ColliderKind.FLOOR, -1, new Vector3(3f, 0.1f, 0f), 9f, 0));
        director.update(0.1f);
        director.onBallContact(new BallContact(ColliderKind.NET_MESH, -1, new Vector3(0f, 2f, 0f), 6f, 0.1));
        director.update(0.1f);
        director.onBallContact(new BallContact(ColliderKind.ANTENNA, -1, new Vector3(0f, 2.6f, 4.5f), 6f, 0.2));
        director.update(0.1f);
        int before = sink.played.size();
        director.onBallContact(new BallContact(ColliderKind.FLOOR, -1, new Vector3(3f, 0.1f, 0f), 0.3f, 0.3));
        assertEquals(before, sink.played.size(), "a rolling ball makes no bounce sound");
        assertNotNull(sink.last(SoundId.BALL_FLOOR));
        assertNotNull(sink.last(SoundId.BALL_NET));
        assertNotNull(sink.last(SoundId.BALL_METAL));
    }

    @Test
    void soundsArePannedTowardsTheirSide() {
        director.onBallContact(new BallContact(ColliderKind.FLOOR, -1, new Vector3(8f, 0.1f, 0f), 9f, 0));
        float right = sink.last(SoundId.BALL_FLOOR).pan();
        director.update(0.2f);
        director.onBallContact(new BallContact(ColliderKind.FLOOR, -1, new Vector3(-8f, 0.1f, 0f), 9f, 0.2));
        float left = sink.last(SoundId.BALL_FLOOR).pan();
        assertTrue(right > 0.2f && left < -0.2f, right + " / " + left);
    }

    @Test
    void sameSoundIsNotRetriggeredWithinTheGuard() {
        director.onBallContact(new BallContact(ColliderKind.FLOOR, -1, new Vector3(3f, 0.1f, 0f), 9f, 0));
        director.onBallContact(new BallContact(ColliderKind.FLOOR, -1, new Vector3(3f, 0.1f, 0f), 9f, 0));
        assertEquals(1, sink.played.stream().filter(p -> p.id() == SoundId.BALL_FLOOR).count());
    }

    @Test
    void homePointBringsWhistleChimeAndCheerAwayKillAGroan() {
        director.onPoint(new MatchRules.PointSummary(new RallyOutcome(CourtSide.HOME, Fault.BALL_IN, -1, 0), false, false, false,
                CourtSide.HOME), null);
        assertNotNull(sink.last(SoundId.WHISTLE_SHORT));
        assertNull(sink.last(SoundId.CROWD_CHEER), "the crowd reacts a moment after the whistle");
        director.update(0.5f);
        assertNotNull(sink.last(SoundId.CROWD_CHEER));
        assertNotNull(sink.last(SoundId.POINT_CHIME));

        director.onPoint(new MatchRules.PointSummary(new RallyOutcome(CourtSide.AWAY, Fault.BALL_IN, -1, 1), true, false, false,
                CourtSide.AWAY), null);
        director.update(0.5f);
        assertNotNull(sink.last(SoundId.CROWD_GROAN));
    }

    @Test
    void setEndsWithALongWhistleAndTheHorn() {
        director.onPoint(new MatchRules.PointSummary(new RallyOutcome(CourtSide.AWAY, Fault.BALL_OUT, -1, 0), false, true, false,
                CourtSide.HOME), null);
        assertNotNull(sink.last(SoundId.WHISTLE_LONG));
        director.update(1.2f);
        assertNotNull(sink.last(SoundId.SET_HORN));
    }

    @Test
    void crowdLoopIsLouderInAMatchAndMenuMusicOnlyInMenus() {
        director.update(0.1f);
        float practice = sink.loops.get(SoundId.CROWD_AMBIENT);
        assertEquals(0f, sink.loops.get(SoundId.MENU_MUSIC));
        director.setMatchActive(true);
        director.update(0.1f);
        assertTrue(sink.loops.get(SoundId.CROWD_AMBIENT) > practice);
        director.setMenuOpen(true);
        director.update(0.1f);
        assertTrue(sink.loops.get(SoundId.MENU_MUSIC) > 0f);
    }

    @Test
    void landingAndDiveFallAreHeard() {
        VolleyballPlayer p = world.home().players().get(2);
        p.setAirborne(true);
        director.update(0.01f);
        p.setAirborne(false);
        director.update(0.01f);
        assertNotNull(sink.last(SoundId.LANDING));
        p.setBodyState(com.volleyball.game.entities.BodyState.DIVING);
        director.update(0.01f);
        p.setBodyState(com.volleyball.game.entities.BodyState.ON_FLOOR);
        director.update(0.01f);
        assertNotNull(sink.last(SoundId.BODY_FALL));
    }
}
