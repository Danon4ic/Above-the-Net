package com.volleyball.game.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class SoundSynthTest {
    @ParameterizedTest
    @EnumSource(SoundId.class)
    void everySoundIsAudibleFiniteAndNormalized(SoundId id) {
        float[] s = new SoundSynth(1).generate(id);
        assertTrue(s.length > SoundSynth.SAMPLE_RATE / 20, id + " is too short");
        float peak = 0f;
        double energy = 0;
        for (float v : s) {
            assertTrue(Float.isFinite(v), id + " has a non-finite sample");
            peak = Math.max(peak, Math.abs(v));
            energy += v * v;
        }
        assertEquals(SoundSynth.PEAK, peak, 1e-3f, id + " peak");
        assertTrue(Math.sqrt(energy / s.length) > 0.01, id + " is practically silent");
        boolean loop = id == SoundId.CROWD_AMBIENT || id == SoundId.MENU_MUSIC;
        if (!loop) assertEquals(0f, s[s.length - 1], 1e-6f, id + " must end at zero (no click)");
    }

    @Test
    void harderContactsAreLongerAndLower() {
        SoundSynth synth = new SoundSynth(3);
        float[] spike = synth.generate(SoundId.SPIKE_HARD);
        float[] tip = synth.generate(SoundId.SPIKE_SOFT);
        assertTrue(spike.length > tip.length * 2);
        // Low-frequency content: average of |sample| after a 300 Hz one-pole low-pass, relative to the peak.
        assertTrue(lowShare(spike) > lowShare(tip), "a power spike booms more than a tip");
    }

    private static float lowShare(float[] s) {
        float a = (float) (1 - Math.exp(-2 * Math.PI * 300 / SoundSynth.SAMPLE_RATE));
        float y = 0f;
        double sum = 0;
        for (float v : s) {
            y += a * (v - y);
            sum += Math.abs(y);
        }
        return (float) (sum / s.length);
    }

    @Test
    void crowdLoopHasNoSeam() {
        float[] s = new SoundSynth(5).generate(SoundId.CROWD_AMBIENT);
        // Cross-fade makes the end flow into the start: the jump at the loop point is like any other step.
        float seam = Math.abs(s[0] - s[s.length - 1]);
        float typical = 0f;
        for (int i = 1; i < s.length; i++) typical = Math.max(typical, Math.abs(s[i] - s[i - 1]));
        assertTrue(seam <= typical, "seam " + seam + " vs max step " + typical);
    }

    @Test
    void sameSeedSameSound() {
        assertArrayEquals(new SoundSynth(9).generate(SoundId.CROWD_CHEER), new SoundSynth(9).generate(SoundId.CROWD_CHEER));
    }

    @Test
    void wavHeaderDescribesMono16BitPcm() {
        float[] samples = {0f, 0.5f, -0.5f, 1f};
        byte[] wav = SoundSynth.toWav(samples);
        assertEquals(44 + samples.length * 2, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4));
        assertEquals("WAVE", new String(wav, 8, 4));
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, b.getShort(20), "PCM");
        assertEquals(1, b.getShort(22), "mono");
        assertEquals(SoundSynth.SAMPLE_RATE, b.getInt(24));
        assertEquals(16, b.getShort(34));
        assertEquals(samples.length * 2, b.getInt(40));
        assertEquals(16384, b.getShort(46), 1);
        assertEquals(32767, b.getShort(50));
    }
}
