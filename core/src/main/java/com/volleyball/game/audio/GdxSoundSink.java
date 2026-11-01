package com.volleyball.game.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.volleyball.game.config.GameConfig;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Plays the synthesized sounds through libGDX. The sounds are generated on a background thread
 * at start-up (written as WAV files to a temporary folder) and loaded on the render thread as
 * they become ready, so the game starts without waiting; a sound requested before it is ready
 * is simply skipped.
 *
 * <p>Volumes: each sound's volume × its channel (effects, crowd, music) × master, all from the
 * settings; muting stops everything.
 */
public final class GdxSoundSink implements SoundSink, Disposable {
    private final GameConfig config;
    private final EnumMap<SoundId, Sound> sounds = new EnumMap<>(SoundId.class);
    private final EnumMap<SoundId, Long> loops = new EnumMap<>(SoundId.class);
    private final EnumMap<SoundId, Float> loopVolumes = new EnumMap<>(SoundId.class);
    private final ConcurrentLinkedQueue<Map.Entry<SoundId, FileHandle>> ready = new ConcurrentLinkedQueue<>();
    private final FileHandle folder;
    private final Thread generator;
    private volatile boolean disposed;

    private GdxSoundSink(GameConfig config) {
        this.config = config;
        folder = FileHandle.tempDirectory("volleyball-audio");
        generator = new Thread(this::generateAll, "audio-synth");
        generator.setDaemon(true);
        generator.start();
    }

    /** The libGDX sink, or a silent one if there is no audio device. */
    public static SoundSink create(GameConfig config) {
        if (Gdx.audio == null) return SoundSink.SILENT;
        try {
            return new GdxSoundSink(config);
        } catch (RuntimeException e) {
            Gdx.app.error("Audio", "Sound disabled: " + e.getMessage());
            return SoundSink.SILENT;
        }
    }

    private void generateAll() {
        SoundSynth synth = new SoundSynth(20251023L);
        try {
            for (SoundId id : SoundId.values()) {
                if (disposed) return;
                FileHandle file = folder.child(id.name().toLowerCase() + ".wav");
                file.writeBytes(SoundSynth.toWav(synth.generate(id)), false);
                ready.add(Map.entry(id, file));
            }
        } catch (RuntimeException e) {
            Gdx.app.error("Audio", "Sound generation failed: " + e.getMessage());
        }
    }

    /** Loads the sounds generated so far; call on the render thread every frame. */
    public void update() {
        Map.Entry<SoundId, FileHandle> entry;
        while ((entry = ready.poll()) != null) {
            try {
                sounds.put(entry.getKey(), Gdx.audio.newSound(entry.getValue()));
            } catch (RuntimeException e) {
                Gdx.app.error("Audio", "Could not load " + entry.getKey() + ": " + e.getMessage());
            }
        }
        // Keep the loops at the right level when the settings change.
        for (Map.Entry<SoundId, Float> loop : loopVolumes.entrySet()) applyLoop(loop.getKey(), loop.getValue());
    }

    @Override
    public void play(SoundId id, float volume, float pitch, float pan) {
        Sound sound = sounds.get(id);
        float v = volume * gain(id);
        if (sound == null || v <= 0.001f) return;
        sound.play(v, pitch, pan);
    }

    @Override
    public void setLoop(SoundId id, float volume) {
        loopVolumes.put(id, volume);
        applyLoop(id, volume);
    }

    private void applyLoop(SoundId id, float volume) {
        Sound sound = sounds.get(id);
        if (sound == null) return;
        float v = volume * gain(id);
        Long playing = loops.get(id);
        if (v <= 0.001f) {
            if (playing != null) {
                sound.stop(playing);
                loops.remove(id);
            }
            return;
        }
        if (playing == null || playing < 0) loops.put(id, sound.loop(v));
        else sound.setVolume(playing, v);
    }

    /** Channel × master volume, 0 when muted. */
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
        disposed = true;
        try {
            generator.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Sound sound : sounds.values()) sound.dispose();
        sounds.clear();
        folder.deleteDirectory();
    }
}
