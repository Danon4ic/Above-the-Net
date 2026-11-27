package com.volleyball.game.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.config.GameConfig;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Plays positioned samples and streams the arena ambience and music. */
public final class GdxSoundSink implements SoundSink, Disposable {
    private static final float SILENCE = 0.001f;
    private final GameConfig config;
    private final EnumMap<SoundId, Sound[]> effects = new EnumMap<>(SoundId.class);
    private final EnumMap<SoundId, Music> background = new EnumMap<>(SoundId.class);
    private final EnumMap<SoundId, Float> loopTargets = new EnumMap<>(SoundId.class);
    private final EnumMap<SoundId, SampleVariants> sampleOrder = new EnumMap<>(SoundId.class);
    private final List<CrowdVoice> crowdVoices = new ArrayList<>();
    private float clock;
    private float duckUntil;
    private float crowdGain = 1f;
    private boolean enabled;

    private record CrowdVoice(Sound sound, long handle, float volume, float expires) {
    }

    private GdxSoundSink(GameConfig config) {
        this.config = config;
        enabled = config.soundEnabled;
        Random random = new Random();
        for (SoundId id : SoundId.values()) {
            if (id.isLoop()) {
                try {
                    Music music = Gdx.audio.newMusic(asset(id, 0));
                    music.setLooping(true);
                    music.setVolume(0f);
                    background.put(id, music);
                } catch (RuntimeException e) {
                    Gdx.app.error("Audio", "Could not load " + id, e);
                }
            } else {
                Sound[] variants = new Sound[id.variants];
                for (int i = 0; i < variants.length; i++) {
                    try {
                        variants[i] = Gdx.audio.newSound(asset(id, i));
                    } catch (RuntimeException e) {
                        Gdx.app.error("Audio", "Could not load " + id.resource(i), e);
                    }
                }
                effects.put(id, variants);
                sampleOrder.put(id, new SampleVariants(id.variants, random));
            }
        }
    }

    private static FileHandle asset(SoundId id, int variant) {
        return Gdx.files.classpath(id.resource(variant));
    }

    public static SoundSink create(GameConfig config) {
        return Gdx.audio == null ? SoundSink.SILENT : new GdxSoundSink(config);
    }

    /** Fades ambience and music between screens and applies volume settings. */
    public void update() {
        float dt = Gdx.graphics.getDeltaTime();
        clock += dt;
        if (clock >= duckUntil) crowdGain += (1f - crowdGain) * (1f - (float) Math.exp(-5f * dt));
        if (enabled && !config.soundEnabled) {
            for (Sound[] variants : effects.values()) {
                for (Sound sound : variants) if (sound != null) sound.stop();
            }
            crowdVoices.clear();
        }
        enabled = config.soundEnabled;
        refreshCrowdVoices();
        float blend = 1f - (float) Math.exp(-8f * dt);
        for (Map.Entry<SoundId, Music> entry : background.entrySet()) {
            SoundId id = entry.getKey();
            Music music = entry.getValue();
            float target = loopTargets.getOrDefault(id, 0f) * gain(id);
            float volume = music.getVolume() + (target - music.getVolume()) * blend;
            if (!enabled || (target <= SILENCE && volume <= SILENCE)) {
                if (music.isPlaying()) music.stop();
                music.setVolume(0f);
            } else {
                music.setVolume(volume);
                if (!music.isPlaying()) music.play();
            }
        }
    }

    @Override
    public void play(SoundId id, float volume, float pitch, float pan) {
        playTake(id, -1, volume, pitch, pan);
    }

    @Override
    public void playTake(SoundId id, int take, float volume, float pitch, float pan) {
        Sound[] variants = effects.get(id);
        float level = volume * gain(id);
        if (variants == null || level <= SILENCE) return;
        int index = take >= 0 ? take : sampleOrder.get(id).next();
        Sound sound = variants[index];
        for (int i = 1; sound == null && i < variants.length; i++) {
            sound = variants[(index + i) % variants.length];
        }
        if (sound != null) {
            long handle = sound.play(level, pitch, pan);
            if (handle >= 0 && id.channel == SoundId.Channel.CROWD) {
                crowdVoices.add(new CrowdVoice(sound, handle, volume, clock + 7f));
            }
        }
    }

    @Override
    public void reflect(SoundId id, int take, float volume, float pitch, float pan) {
        playTake(id, take, volume, pitch, pan);
    }

    @Override
    public void duckCrowd(float targetGain, float duration) {
        float nextGain = Math.min(crowdGain, targetGain);
        if (nextGain < crowdGain) {
            crowdGain = nextGain;
            refreshCrowdVoices();
            for (Map.Entry<SoundId, Music> entry : background.entrySet()) {
                if (entry.getKey().channel != SoundId.Channel.CROWD) continue;
                Music music = entry.getValue();
                music.setVolume(Math.min(music.getVolume(), loopTargets.getOrDefault(entry.getKey(), 0f) * gain(entry.getKey())));
            }
        }
        duckUntil = Math.max(duckUntil, clock + duration);
    }

    private void refreshCrowdVoices() {
        float gain = config.soundEnabled ? config.crowdVolume * config.masterVolume * crowdGain : 0f;
        for (int i = crowdVoices.size() - 1; i >= 0; i--) {
            CrowdVoice voice = crowdVoices.get(i);
            if (clock >= voice.expires()) crowdVoices.remove(i);
            else voice.sound().setVolume(voice.handle(), voice.volume() * gain);
        }
    }

    @Override
    public void setLoop(SoundId id, float volume) {
        loopTargets.put(id, volume);
    }

    private float gain(SoundId id) {
        if (!config.soundEnabled) return 0f;
        float channel = switch (id.channel) {
            case EFFECTS -> config.effectsVolume;
            case CROWD -> config.crowdVolume * crowdGain;
            case MUSIC -> config.musicVolume;
        };
        return channel * config.masterVolume;
    }

    @Override
    public void dispose() {
        for (Sound[] variants : effects.values()) {
            for (Sound sound : variants) if (sound != null) sound.dispose();
        }
        for (Music music : background.values()) music.dispose();
        effects.clear();
        background.clear();
        crowdVoices.clear();
    }
}
