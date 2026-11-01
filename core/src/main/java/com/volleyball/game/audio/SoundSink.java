package com.volleyball.game.audio;

/**
 * Where the game's sounds go. The libGDX implementation plays them; tests record them; the
 * silent sink is used when no audio device is available.
 */
public interface SoundSink {
    /**
     * Plays a sound once.
     *
     * @param volume 0..1 before the channel and master volumes are applied
     * @param pitch  playback rate, 0.5..2 (1 = original)
     * @param pan    -1 (left) .. 1 (right)
     */
    void play(SoundId id, float volume, float pitch, float pan);

    /** Starts, adjusts or (at volume 0) stops a looping sound. */
    void setLoop(SoundId id, float volume);

    SoundSink SILENT = new SoundSink() {
        @Override
        public void play(SoundId id, float volume, float pitch, float pan) {
        }

        @Override
        public void setLoop(SoundId id, float volume) {
        }
    };
}
