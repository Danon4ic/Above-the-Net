package com.volleyball.game.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.config.GameConfig;

import java.util.EnumMap;
import java.util.Map;

/** Plays positioned samples and streams the two background loops. */
public final class GdxSoundSink implements SoundSink, Disposable {
    private static final float SILENCE = 0.001f;
    private final GameConfig config;
    private final EnumMap<SoundId, Sound[]> effects = new EnumMap<>(SoundId.class);
    private final EnumMap<SoundId, Music> background = new EnumMap<>(SoundId.class);
    private final EnumMap<SoundId, Float> loopTargets = new EnumMap<>(SoundId.class);
    private final int[] nextVariant = new int[SoundId.values().length];
    private boolean enabled;

    private GdxSoundSink(GameConfig config) {
        this.config = config;
        enabled = config.soundEnabled;
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
        if (enabled && !config.soundEnabled) {
            for (Sound[] variants : effects.values()) {
                for (Sound sound : variants) if (sound != null) sound.stop();
            }
        }
        enabled = config.soundEnabled;
        float blend = 1f - (float) Math.exp(-8f * Gdx.graphics.getDeltaTime());
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
        Sound[] variants = effects.get(id);
        float level = volume * gain(id);
        if (variants == null || level <= SILENCE) return;
        int index = nextVariant[id.ordinal()];
        nextVariant[id.ordinal()] = (index + 1) % variants.length;
        Sound sound = variants[index];
        if (sound != null) sound.play(level, pitch, pan);
    }

    @Override
    public void setLoop(SoundId id, float volume) {
        loopTargets.put(id, volume);
    }

    private float gain(SoundId id) {
        if (!config.soundEnabled) return 0f;
        float channel = switch (id.channel) {
            case EFFECTS -> config.effectsVolume;
            case CROWD -> config.crowdVolume;
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
    }
}
