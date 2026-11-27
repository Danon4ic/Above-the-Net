package com.volleyball.game.audio;

import com.badlogic.gdx.math.Vector3;
import com.volleyball.game.ai.Difficulty;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.match.MatchController;
import com.volleyball.game.match.MatchListener;
import com.volleyball.game.match.ReplayRecorder;
import com.volleyball.game.rules.MatchFormat;
import com.volleyball.game.rules.MatchRules;
import com.volleyball.game.rules.MatchScore;
import com.volleyball.game.simulation.GameWorld;
import com.volleyball.game.world.CourtSide;
import com.volleyball.game.world.NetCategory;
import com.volleyball.game.world.VolleyballCourt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchAudioTest {
    private static final float DT = 1f / 120f;
    private static final Vector3 GAME_CAMERA = new Vector3(0f, 6f, 20f);
    private static final Vector3 ACROSS_COURT = new Vector3(0f, 0f, -1f);

    private record Contact(SoundId id, int take, float volume, float pan) {
    }

    private static final class Mix implements SoundSink {
        final EnumMap<SoundId, Integer> counts = new EnumMap<>(SoundId.class);
        final List<Contact> takes = new ArrayList<>();
        float crowd;
        int whistleDucks;

        @Override
        public void play(SoundId id, float volume, float pitch, float pan) {
            counts.merge(id, 1, Integer::sum);
        }

        @Override
        public void playTake(SoundId id, int take, float volume, float pitch, float pan) {
            play(id, volume, pitch, pan);
            if (take >= 0) takes.add(new Contact(id, take, volume, pan));
        }

        @Override
        public void setLoop(SoundId id, float volume) {
            if (id == SoundId.CROWD_AMBIENT) crowd = volume;
        }

        @Override
        public void duckCrowd(float gain, float duration) {
            if (gain <= 0.35f) whistleDucks++;
        }

        int plays(SoundId id) {
            return counts.getOrDefault(id, 0);
        }
    }

    @Test
    void fullSetKeepsWhistlesClearAndAReplayKeepsTheRallysTakes() {
        GameWorld world = GameWorld.withDefaultTeams(new VolleyballCourt(NetCategory.MEN), 21);
        MatchController match = new MatchController(world, MatchFormat.official(), 21, Difficulty.PROFESSIONAL, Difficulty.PROFESSIONAL);
        ReplayRecorder replay = new ReplayRecorder(world.players(), world.ball());
        Mix mix = new Mix();
        SoundDirector director = new SoundDirector(world, mix, 19).attach();
        director.setReplayRecorder(replay);
        director.setListener(GAME_CAMERA, ACROSS_COURT);
        match.addListener(director);
        List<ReplayRecorder.CourtSound> rally = new ArrayList<>();
        match.addListener(new MatchListener() {
            @Override
            public void onServeWhistle(VolleyballPlayer server, MatchScore score) {
                replay.start();
            }

            @Override
            public void onPoint(MatchRules.PointSummary point, MatchScore score) {
                if (rally.isEmpty()) {
                    List<ReplayRecorder.CourtSound> contacts = new ArrayList<>();
                    for (float from = -0.001f; from < replay.duration();) {
                        float through = Math.min(replay.duration(), from + 0.013f);
                        replay.playSounds(from, through, cue -> {
                            if (cue.take() >= 0 && !cue.reflection() && cue.id().ordinal() < SoundId.BALL_FLOOR.ordinal()) contacts.add(cue);
                        });
                        from = through;
                    }
                    if (contacts.size() >= 3) rally.addAll(contacts);
                }
                replay.stop();
            }
        });
        match.start(CourtSide.HOME);

        float quietest = Float.MAX_VALUE;
        for (int step = 0; step < 120 * 60 * 15 && match.rules().score().finishedSets().isEmpty(); step++) {
            world.step(DT);
            match.update(DT, List.of());
            director.updateMovement(DT);
            replay.capture(DT);
            director.update(DT);
            quietest = Math.min(quietest, mix.crowd);
        }
        assertFalse(match.rules().score().finishedSets().isEmpty(), "a complete official set");
        var score = match.rules().score().finishedSets().getFirst();
        assertTrue(Math.max(score.home(), score.away()) >= 25);
        director.update(1.5f);
        assertTrue(mix.plays(SoundId.WHISTLE_SHORT) >= score.home() + score.away());
        assertEquals(1, mix.plays(SoundId.WHISTLE_LONG));
        assertEquals(1, mix.plays(SoundId.SET_HORN));
        assertTrue(mix.whistleDucks >= mix.plays(SoundId.WHISTLE_SHORT) + 1);
        assertEquals(SoundDirector.CROWD_BASE * 0.30f, quietest, 0.0001f);
        assertTrue(mix.plays(SoundId.FOOTSTEP) > 100);
        assertTrue(mix.plays(SoundId.FOREARM_PASS) + mix.plays(SoundId.FOREARM_HARD) > 0);
        assertTrue(mix.plays(SoundId.OVERHEAD_SET) > 0);
        assertTrue(mix.plays(SoundId.CROWD_CHEER) > 0);
        assertFalse(rally.isEmpty());
        assertEquals(rally.size(), rally.stream().distinct().count());

        director.beginReplay();
        mix.takes.clear();
        rally.forEach(cue -> director.playReplaySound(cue, false));
        List<Contact> gameCamera = List.copyOf(mix.takes);
        mix.takes.clear();
        director.setListener(rally.getFirst().position().cpy().add(-2f, 1f, 2f), ACROSS_COURT);
        rally.forEach(cue -> director.playReplaySound(cue, false));
        assertEquals(gameCamera.size(), mix.takes.size());
        for (int i = 0; i < gameCamera.size(); i++) {
            assertEquals(gameCamera.get(i).id(), mix.takes.get(i).id());
            assertEquals(gameCamera.get(i).take(), mix.takes.get(i).take());
        }
        assertTrue(mix.takes.getFirst().volume() > gameCamera.getFirst().volume());
        assertNotEquals(gameCamera.getFirst().pan(), mix.takes.getFirst().pan(), 0.01f);
        System.out.printf("Audio QA: set %d:%d, %d replay contacts, first gain %.3f -> %.3f%n",
                score.home(), score.away(), rally.size(), gameCamera.getFirst().volume(), mix.takes.getFirst().volume());
    }
}
