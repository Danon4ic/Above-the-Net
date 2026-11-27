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

    /** Plays a specific take, or selects one normally when take is negative. */
    default void playTake(SoundId id, int take, float volume, float pitch, float pan) {
        play(id, volume, pitch, pan);
    }

    /** Starts, adjusts or (at volume 0) stops a looping sound. */
    void setLoop(SoundId id, float volume);

    /** Momentarily makes room for a whistle or a strong contact on the crowd channel. */
    default void duckCrowd(float gain, float duration) {
    }

    /** Plays a quieter return of a court sound, without advancing its bank. */
    default void reflect(SoundId id, int take, float volume, float pitch, float pan) {
    }

    SoundSink SILENT = new SoundSink() {
        @Override
        public void play(SoundId id, float volume, float pitch, float pan) {
        }

        @Override
        public void setLoop(SoundId id, float volume) {
        }
    };
}
