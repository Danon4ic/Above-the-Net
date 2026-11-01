package com.volleyball.game.audio;

import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * Procedural sound effects. The game ships without recorded samples: every sound is
 * synthesized once at start-up from noise, oscillators, filters and envelopes, so the audio
 * needs no asset licensing and the generator can be unit-tested headless.
 *
 * <ul>
 *   <li>Ball contacts: a low "thump" (the ball's air volume) plus a band-passed noise "slap"
 *       (skin on leather); harder contacts are lower, louder and longer.</li>
 *   <li>Voices (grunts, calls, crowd): a glottal pulse train through three formant filters.</li>
 *   <li>Metal and chimes: inharmonic / harmonic partials with exponential decay.</li>
 * </ul>
 * Output is mono 16-bit PCM at {@link #SAMPLE_RATE}, normalized to the same peak level; the
 * relative loudness of each event is set when it is played.
 */
public final class SoundSynth {
    public static final int SAMPLE_RATE = 22050;
    static final float PEAK = 0.89f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    // Vowel formants (F1, F2, F3), Hz.
    private static final float[] VOWEL_A = {750f, 1150f, 2500f};
    private static final float[] VOWEL_AE = {660f, 1700f, 2400f};
    private static final float[] VOWEL_E = {500f, 1850f, 2600f};
    private static final float[] VOWEL_I = {320f, 2250f, 2900f};
    private static final float[] VOWEL_O = {450f, 800f, 2600f};
    private static final float[] VOWEL_U = {340f, 700f, 2400f};
    private static final float[][] VOWELS = {VOWEL_A, VOWEL_AE, VOWEL_E, VOWEL_I, VOWEL_O, VOWEL_U};

    private final Random random;

    public SoundSynth(long seed) {
        this.random = new Random(seed);
    }

    /** Samples in -1..1 for the sound. */
    public float[] generate(SoundId id) {
        float[] s = switch (id) {
            case SERVE_FLOAT -> ballHit(0.25f, 170f, 0.6f, 0.04f, 2000f, 1.2f, 1.0f, 0.018f);
            case SERVE_POWER -> crack(ballHit(0.35f, 120f, 0.9f, 0.06f, 1600f, 0.9f, 1.2f, 0.025f), 0.5f);
            case SERVE_TOSS -> ballHit(0.12f, 220f, 0.3f, 0.02f, 1200f, 1f, 0.25f, 0.01f);
            case FOREARM_PASS -> ballHit(0.3f, 150f, 1.0f, 0.05f, 900f, 1f, 0.5f, 0.02f);
            case OVERHEAD_SET -> doubleHit(ballHit(0.15f, 260f, 0.4f, 0.025f, 1500f, 0.8f, 0.35f, 0.012f), 0.008f, 0.6f);
            case SPIKE_HARD -> crack(ballHit(0.45f, 100f, 1.0f, 0.08f, 1800f, 0.7f, 1.6f, 0.03f), 0.9f);
            case SPIKE_SOFT -> ballHit(0.15f, 240f, 0.35f, 0.02f, 1700f, 1f, 0.4f, 0.01f);
            case BLOCK -> doubleHit(ballHit(0.4f, 130f, 0.8f, 0.05f, 1400f, 0.8f, 1.2f, 0.025f), 0.012f, 0.8f);
            case DIG -> ballHit(0.35f, 110f, 1.0f, 0.07f, 700f, 1f, 0.5f, 0.03f);
            case BALL_FLOOR -> floorBounce();
            case BALL_NET -> netRustle();
            case BALL_METAL -> metal();
            case BALL_WALL -> ballHit(0.4f, 70f, 1.0f, 0.09f, 600f, 1f, 0.5f, 0.03f);
            case BALL_BODY -> ballHit(0.2f, 120f, 0.8f, 0.04f, 600f, 1f, 0.3f, 0.015f);
            case SHOE_SQUEAK -> squeak();
            case LANDING -> thud(0.25f, 75f, 0.07f, 400f, 0.5f, 0.04f, 0f);
            case BODY_FALL -> thud(0.7f, 60f, 0.12f, 700f, 0.8f, 0.08f, 0.15f);
            case GRUNT -> grunt();
            case CALL -> call();
            case WHISTLE_SHORT -> whistle(0.45f);
            case WHISTLE_LONG -> whistle(1.2f);
            case POINT_CHIME -> chime();
            case SET_HORN -> horn(1.1f, new float[]{233f, 294f, 349f}, 1500f);
            case TIMEOUT_BUZZER -> horn(0.7f, new float[]{180f, 270f}, 1200f);
            case CROWD_AMBIENT -> crowdAmbient(6f);
            case CROWD_CHEER -> crowdCheer();
            case CROWD_GROAN -> crowdVoices(2.2f, 30, VOWEL_O, 200f, 320f, -0.3f, 0.25f, 0f);
            case CROWD_OOH -> crowdVoices(1.6f, 26, VOWEL_U, 180f, 300f, 0.12f, 0.2f, 0f);
            case CROWD_CLAPS -> unisonClaps(5, 0.42f);
            case CROWD_CHANT -> crowdChant();
            case MENU_MUSIC -> menuMusic();
        };
        return normalize(s, id != SoundId.CROWD_AMBIENT && id != SoundId.MENU_MUSIC);
    }

    // ------------------------------------------------------------------ ball and bodies

    /**
     * A ball contact: a decaying sine whose pitch drops right after the impact, plus a burst of
     * band-passed noise and a 2 ms click.
     */
    private float[] ballHit(float duration, float thumpFreq, float thumpLevel, float thumpTau,
                            float slapFreq, float slapQ, float slapLevel, float slapTau) {
        float[] out = buffer(duration);
        Biquad slap = Biquad.bandpass(slapFreq, slapQ);
        float phase = 0f;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            float f = thumpFreq * (1f + 0.5f * (float) Math.exp(-t / 0.01f));
            phase += TWO_PI * f / SAMPLE_RATE;
            float thump = (float) Math.sin(phase) * thumpLevel * (float) Math.exp(-t / thumpTau);
            float noise = slap.process(noise()) * slapLevel * 2.5f * (float) Math.exp(-t / slapTau);
            float click = t < 0.002f ? noise() * (1f - t / 0.002f) * 0.6f : 0f;
            out[i] = thump + noise + click;
        }
        return out;
    }

    /** Adds the sharp crack of a hard hand-on-ball hit (high-passed noise, very short). */
    private float[] crack(float[] s, float level) {
        Biquad high = Biquad.highpass(3000f, 0.7f);
        for (int i = 0; i < s.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            s[i] += high.process(noise()) * level * (float) Math.exp(-t / 0.006f);
        }
        return s;
    }

    /** Mixes a delayed, quieter copy in (two hands / fingers touching a few ms apart). */
    private static float[] doubleHit(float[] s, float delay, float gain) {
        int offset = Math.round(delay * SAMPLE_RATE);
        float[] out = s.clone();
        for (int i = offset; i < out.length; i++) out[i] += s[i - offset] * gain;
        return out;
    }

    /** The ball on a wooden/sports floor: a deep boom with a drop in pitch and a floor resonance. */
    private float[] floorBounce() {
        float[] out = ballHit(0.6f, 85f, 1.0f, 0.12f, 1100f, 1f, 0.7f, 0.02f);
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            out[i] += (float) Math.sin(TWO_PI * 230f * t) * 0.25f * (float) Math.exp(-t / 0.15f);
        }
        return out;
    }

    /** The ball in the net: a rattling rustle of the mesh, a soft thump and the cable humming. */
    private float[] netRustle() {
        float[] out = buffer(0.7f);
        Biquad mesh = Biquad.bandpass(3000f, 0.6f);
        float rattle = 37f + random.nextFloat() * 10f;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            float env = Math.min(1f, t / 0.005f) * (float) Math.exp(-t / 0.18f);
            float am = (float) Math.pow(Math.abs(Math.sin(TWO_PI * rattle * t)), 2.0);
            float rustle = mesh.process(noise()) * 2.2f * env * (0.35f + 0.65f * am);
            float thump = (float) Math.sin(TWO_PI * 140f * t) * 0.4f * (float) Math.exp(-t / 0.05f);
            float cable = (float) Math.sin(TWO_PI * 95f * t) * 0.2f * (float) Math.exp(-t / 0.25f);
            out[i] = rustle + thump + cable;
        }
        return out;
    }

    /** The ball hitting an antenna or a post: inharmonic metallic partials. */
    private float[] metal() {
        float[] out = ballHit(0.8f, 150f, 0.5f, 0.03f, 1500f, 1f, 0.3f, 0.01f);
        float[] partials = {870f, 1420f, 2300f, 3350f, 4520f};
        float[] amps = {1f, 0.7f, 0.5f, 0.35f, 0.2f};
        float[] taus = {0.35f, 0.25f, 0.18f, 0.12f, 0.08f};
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            float ring = 0f;
            for (int k = 0; k < partials.length; k++) {
                ring += (float) Math.sin(TWO_PI * partials[k] * t) * amps[k] * (float) Math.exp(-t / taus[k]);
            }
            out[i] += ring * 0.45f;
        }
        return out;
    }

    /** A body landing or falling: low thump, low-passed noise and (for slides) floor friction. */
    private float[] thud(float duration, float freq, float tau, float noiseCutoff, float noiseLevel, float noiseTau,
                         float slide) {
        float[] out = buffer(duration);
        Biquad low = Biquad.lowpass(noiseCutoff, 0.7f);
        Biquad friction = Biquad.bandpass(2500f, 0.8f);
        float phase = 0f;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            phase += TWO_PI * freq * (1f + 0.6f * (float) Math.exp(-t / 0.015f)) / SAMPLE_RATE;
            float body = (float) Math.sin(phase) * (float) Math.exp(-t / tau);
            float dull = low.process(noise()) * noiseLevel * 2f * (float) Math.exp(-t / noiseTau);
            float rub = slide > 0f ? friction.process(noise()) * slide * Math.min(1f, t / 0.05f) * (float) Math.exp(-t / 0.25f) : 0f;
            out[i] = body + dull + rub;
        }
        return out;
    }

    /** Rubber sole squeaking on the court: a rising tone with harmonics and fast vibrato. */
    private float[] squeak() {
        float duration = 0.14f;
        float[] out = buffer(duration);
        float phase = 0f;
        float base = 1800f + random.nextFloat() * 300f;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            float f = base + 700f * t / duration + 80f * (float) Math.sin(TWO_PI * 45f * t);
            phase += TWO_PI * f / SAMPLE_RATE;
            float wave = (float) (Math.sin(phase) + 0.5 * Math.sin(2 * phase) + 0.25 * Math.sin(3 * phase));
            float env = Math.min(1f, t / 0.01f) * Math.min(1f, (duration - t) / 0.03f);
            out[i] = (wave + noise() * 0.1f) * env;
        }
        return out;
    }

    // ------------------------------------------------------------------ voices

    /** Effort grunt of a hitter: a short, falling "ha". */
    private float[] grunt() {
        float[] out = buffer(0.32f);
        voice(out, 0, 0.3f, 140f, 105f, VOWEL_A, VOWEL_A, 1f, 0.015f, 0.12f, 0.35f);
        return out;
    }

    /** A player's call ("hey!" / "mine!"): a short shout gliding from "e" to "i". */
    private float[] call() {
        float[] out = buffer(0.38f);
        voice(out, 0, 0.35f, 200f, 225f, VOWEL_E, VOWEL_I, 1f, 0.04f, 0.1f, 0.5f);
        return out;
    }

    /**
     * One voice into {@code out}: a band-limited pulse train (pitch gliding f0a → f0b, with a
     * little vibrato) through three formant filters gliding between two vowels, with an
     * aspirated onset.
     */
    private void voice(float[] out, int start, float duration, float f0a, float f0b, float[] vowelA, float[] vowelB,
                       float amp, float attack, float release, float breath) {
        int n = Math.min(out.length - start, Math.round(duration * SAMPLE_RATE));
        if (n <= 0) return;
        Biquad[] formants = {new Biquad(), new Biquad(), new Biquad()};
        float[] gains = {1f, 0.6f, 0.3f};
        float vibratoRate = 4.5f + random.nextFloat() * 2f;
        float vibratoPhase = random.nextFloat() * TWO_PI;
        float phase = random.nextFloat();
        float lowpassed = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) SAMPLE_RATE;
            float k = i / (float) n;
            if ((i & 63) == 0) {
                for (int f = 0; f < 3; f++) {
                    float freq = vowelA[f] + (vowelB[f] - vowelA[f]) * k;
                    formants[f].setBandpass(freq, freq / (60f + 30f * f));
                }
            }
            float f0 = (f0a + (f0b - f0a) * k) * (1f + 0.02f * (float) Math.sin(vibratoPhase + TWO_PI * vibratoRate * t));
            phase += f0 / SAMPLE_RATE;
            if (phase >= 1f) phase -= 1f;
            // Sawtooth smoothed by a one-pole low-pass: a soft glottal pulse.
            lowpassed += 0.35f * ((2f * phase - 1f) - lowpassed);
            float source = lowpassed + noise() * breath * Math.max(0.15f, 1f - t / 0.05f);
            float sample = 0f;
            for (int f = 0; f < 3; f++) sample += formants[f].process(source) * gains[f];
            float env = Math.min(1f, t / attack) * Math.min(1f, (n - i) / (release * SAMPLE_RATE));
            out[start + i] += sample * env * amp * 3f;
        }
    }

    // ------------------------------------------------------------------ referee and arena

    /** Pea whistle: a ~3 kHz tone warbled by the pea (fast frequency and amplitude trill). */
    private float[] whistle(float duration) {
        float[] out = buffer(duration);
        Biquad breathFilter = Biquad.bandpass(3100f, 4f);
        float phase = 0f;
        float trill = 26f + random.nextFloat() * 4f;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            float warble = (float) Math.sin(TWO_PI * trill * t);
            phase += TWO_PI * (3100f + 150f * warble) / SAMPLE_RATE;
            float tone = (float) Math.sin(phase) * (0.6f + 0.4f * warble) + 0.15f * (float) Math.sin(2f * phase);
            float env = Math.min(1f, t / 0.015f) * Math.min(1f, (duration - t) / 0.04f);
            out[i] = (tone + breathFilter.process(noise()) * 0.6f) * env;
        }
        return out;
    }

    /** Arena "point" chime: two bright bell notes. */
    private float[] chime() {
        float[] out = buffer(0.9f);
        bell(out, 0f, 1318.5f);
        bell(out, 0.12f, 1975.5f);
        return out;
    }

    private static void bell(float[] out, float start, float freq) {
        int offset = Math.round(start * SAMPLE_RATE);
        for (int i = offset; i < out.length; i++) {
            float t = (i - offset) / (float) SAMPLE_RATE;
            float env = Math.min(1f, t / 0.003f) * (float) Math.exp(-t / 0.35f);
            out[i] += (float) (Math.sin(TWO_PI * freq * t) + 0.3 * Math.sin(TWO_PI * 2f * freq * t)
                    + 0.1 * Math.sin(TWO_PI * 3f * freq * t)) * env;
        }
    }

    /** Arena horn / buzzer: a chord of sawtooth waves through a low-pass filter. */
    private static float[] horn(float duration, float[] notes, float cutoff) {
        float[] out = buffer(duration);
        Biquad low = Biquad.lowpass(cutoff, 0.8f);
        float[] phases = new float[notes.length];
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            float sample = 0f;
            for (int k = 0; k < notes.length; k++) {
                phases[k] += notes[k] * (1f + 0.003f * (float) Math.sin(TWO_PI * 5f * t)) / SAMPLE_RATE;
                if (phases[k] >= 1f) phases[k] -= 1f;
                sample += 2f * phases[k] - 1f;
            }
            float env = Math.min(1f, t / 0.02f) * Math.min(1f, (duration - t) / 0.08f);
            out[i] = low.process(sample / notes.length) * env;
        }
        return out;
    }

    // ------------------------------------------------------------------ crowd

    /**
     * Seamless crowd murmur: a band-limited noise bed with slow swells plus babbling voices.
     * It is generated half a second longer and the tail is cross-faded into the start, so it
     * loops without a click.
     */
    private float[] crowdAmbient(float seconds) {
        float fade = 0.5f;
        float[] raw = buffer(seconds + fade);
        Biquad low = Biquad.lowpass(1200f, 0.7f);
        Biquad high = Biquad.highpass(200f, 0.7f);
        float swellA = random.nextFloat() * TWO_PI;
        float swellB = random.nextFloat() * TWO_PI;
        for (int i = 0; i < raw.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            float swell = 0.75f + 0.15f * (float) Math.sin(swellA + TWO_PI * 0.23f * t) + 0.1f * (float) Math.sin(swellB + TWO_PI * 0.61f * t);
            raw[i] = high.process(low.process(noise())) * 1.6f * swell;
        }
        for (int v = 0; v < 16; v++) {
            float t = random.nextFloat() * 0.4f;
            while (t < seconds + fade - 0.2f) {
                float length = 0.12f + random.nextFloat() * 0.25f;
                float f0 = 100f + random.nextFloat() * 150f;
                float[] from = VOWELS[random.nextInt(VOWELS.length)];
                float[] to = VOWELS[random.nextInt(VOWELS.length)];
                voice(raw, Math.round(t * SAMPLE_RATE), length, f0, f0 * (0.9f + random.nextFloat() * 0.2f), from, to,
                        0.12f + random.nextFloat() * 0.1f, 0.03f, 0.05f, 0.3f);
                t += length + 0.05f + random.nextFloat() * 0.35f;
            }
        }
        int n = Math.round(seconds * SAMPLE_RATE);
        int fadeSamples = raw.length - n;
        float[] out = new float[n];
        System.arraycopy(raw, 0, out, 0, n);
        for (int i = 0; i < fadeSamples; i++) {
            float k = i / (float) fadeSamples;
            out[i] = out[i] * k + raw[n + i] * (1f - k);
        }
        return out;
    }

    /** Big cheer after a point: many rising "yeah"s over a noise roar, with applause. */
    private float[] crowdCheer() {
        float duration = 3f;
        float[] out = crowdVoices(duration, 30, VOWEL_AE, 220f, 400f, 0.15f, 0.3f, 0.3f);
        Biquad clapFilter = Biquad.bandpass(1500f, 1f);
        float t = 0.4f;
        while (t < duration - 0.05f) {
            int start = Math.round(t * SAMPLE_RATE);
            float level = (0.2f + random.nextFloat() * 0.3f) * Math.min(1f, (duration - t) / 1.2f);
            for (int i = 0; i < 400 && start + i < out.length; i++) {
                out[start + i] += clapFilter.process(noise()) * level * 3f * (float) Math.exp(-i / (0.008f * SAMPLE_RATE));
            }
            t += 1f / 40f * (0.5f + random.nextFloat());
        }
        return out;
    }

    /**
     * A crowd reaction: {@code count} voices on the same vowel, pitches between {@code f0Min}
     * and {@code f0Max} that glide by {@code glide} (relative), over a noise bed.
     */
    private float[] crowdVoices(float duration, int count, float[] vowel, float f0Min, float f0Max, float glide,
                                float swell, float roar) {
        float[] out = buffer(duration);
        for (int v = 0; v < count; v++) {
            float start = random.nextFloat() * swell;
            float length = duration * (0.55f + random.nextFloat() * 0.4f) - start;
            float f0 = f0Min + random.nextFloat() * (f0Max - f0Min);
            float[] own = {vowel[0] * (0.92f + random.nextFloat() * 0.16f), vowel[1] * (0.92f + random.nextFloat() * 0.16f), vowel[2]};
            voice(out, Math.round(start * SAMPLE_RATE), length, f0, f0 * (1f + glide), own, own,
                    0.15f + random.nextFloat() * 0.1f, 0.12f, length * 0.6f, 0.4f);
        }
        Biquad bed = Biquad.bandpass(1000f, 0.5f);
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            float env = Math.min(1f, t / Math.max(0.05f, swell)) * Math.min(1f, (duration - t) / (duration * 0.5f));
            out[i] += bed.process(noise()) * (0.5f + roar * 2f) * env;
        }
        return out;
    }

    /**
     * The fans at set point: "let's - go!" in chorus, then stamp - stamp - clap, twice
     * (one bar of 1.8 s each).
     */
    private float[] crowdChant() {
        float bar = 1.8f;
        float[] out = buffer(bar * 2f + 0.4f);
        Biquad clapFilter = Biquad.bandpass(1400f, 1.1f);
        for (int b = 0; b < 2; b++) {
            float t0 = b * bar;
            // Two sung syllables: a short "le(t's)" and a longer, rising "go".
            float[][] syllables = {{0f, 0.24f}, {0.34f, 0.42f}};
            for (int s = 0; s < 2; s++) {
                float[] vowel = s == 0 ? VOWEL_E : VOWEL_O;
                for (int v = 0; v < 36; v++) {
                    float start = t0 + syllables[s][0] + random.nextFloat() * 0.04f;
                    float length = syllables[s][1] * (0.85f + random.nextFloat() * 0.25f);
                    float f0 = 170f + random.nextFloat() * 160f;
                    float[] own = {vowel[0] * (0.92f + random.nextFloat() * 0.16f), vowel[1] * (0.92f + random.nextFloat() * 0.16f), vowel[2]};
                    voice(out, Math.round(start * SAMPLE_RATE), length, f0, f0 * (s == 0 ? 1f : 1.06f), own, own,
                            0.12f + random.nextFloat() * 0.08f, 0.03f, length * 0.7f, 0.2f);
                }
            }
            // Stamp, stamp (the stands' floor), clap.
            for (int k = 0; k < 3; k++) {
                float t = t0 + 0.9f + k * 0.28f;
                int start = Math.round(t * SAMPLE_RATE);
                if (k < 2) {
                    float[] stamp = thud(0.25f, 55f, 0.08f, 300f, 0.9f, 0.05f, 0f);
                    for (int i = 0; i < stamp.length && start + i < out.length; i++) out[start + i] += stamp[i] * 0.9f;
                } else {
                    for (int c = 0; c < 40; c++) {
                        int cs = start + Math.round((random.nextFloat() - 0.5f) * 0.04f * SAMPLE_RATE);
                        float level = 0.2f + random.nextFloat() * 0.3f;
                        for (int i = 0; i < 300 && cs + i < out.length; i++) {
                            if (cs + i >= 0) out[cs + i] += clapFilter.process(noise()) * level * (float) Math.exp(-i / (0.007f * SAMPLE_RATE));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** The crowd clapping in time ("clap - clap - clap") while a serve is awaited. */
    private float[] unisonClaps(int beats, float interval) {
        float[] out = buffer(beats * interval + 0.2f);
        Biquad clapFilter = Biquad.bandpass(1400f, 1.1f);
        for (int b = 0; b < beats; b++) {
            for (int c = 0; c < 40; c++) {
                float t = b * interval + (random.nextFloat() - 0.5f) * 0.05f + 0.025f;
                int start = Math.round(t * SAMPLE_RATE);
                float level = 0.2f + random.nextFloat() * 0.3f;
                for (int i = 0; i < 300 && start + i < out.length; i++) {
                    out[start + i] += clapFilter.process(noise()) * level * (float) Math.exp(-i / (0.007f * SAMPLE_RATE));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ music

    /**
     * A short, loopable menu theme: four chords (C - Am - F - G) at 120 bpm with a soft pad, a
     * plucked arpeggio, bass and a light kick.
     */
    private float[] menuMusic() {
        float beat = 0.5f;
        float[][] chords = {{261.6f, 329.6f, 392.0f}, {220.0f, 261.6f, 329.6f}, {174.6f, 220.0f, 261.6f}, {196.0f, 246.9f, 293.7f}};
        float bar = beat * 4f;
        float[] out = buffer(bar * chords.length);
        Biquad padFilter = Biquad.lowpass(900f, 0.7f);
        float[] padPhases = new float[3];
        float bassPhase = 0f;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) SAMPLE_RATE;
            int chord = Math.min(chords.length - 1, (int) (t / bar));
            float inBar = t - chord * bar;
            float[] notes = chords[chord];
            float pad = 0f;
            for (int k = 0; k < 3; k++) {
                padPhases[k] += notes[k] / SAMPLE_RATE;
                if (padPhases[k] >= 1f) padPhases[k] -= 1f;
                pad += 2f * padPhases[k] - 1f;
            }
            float padEnv = Math.min(1f, inBar / 0.3f) * Math.min(1f, (bar - inBar) / 0.2f);
            float sample = padFilter.process(pad / 3f) * 0.35f * padEnv;
            // Arpeggio: eighth notes up and down the chord, an octave higher.
            int step = (int) (inBar / (beat / 2f));
            float inStep = inBar - step * (beat / 2f);
            float arpFreq = notes[new int[]{0, 1, 2, 1}[step % 4]] * 2f;
            sample += (float) Math.sin(TWO_PI * arpFreq * inStep) * 0.25f * (float) Math.exp(-inStep / 0.12f);
            bassPhase += notes[0] / 2f / SAMPLE_RATE;
            if (bassPhase >= 1f) bassPhase -= 1f;
            sample += (float) Math.sin(TWO_PI * bassPhase) * 0.3f * Math.min(1f, inBar / 0.02f);
            float inBeat = t % beat;
            sample += (float) Math.sin(TWO_PI * (50f + 80f * (float) Math.exp(-inBeat / 0.02f)) * inBeat) * 0.35f
                    * (float) Math.exp(-inBeat / 0.09f);
            out[i] = sample;
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private float noise() {
        return random.nextFloat() * 2f - 1f;
    }

    private static float[] buffer(float seconds) {
        return new float[Math.max(1, Math.round(seconds * SAMPLE_RATE))];
    }

    /**
     * Scales to the common peak level; one-shot sounds also fade out over the last 5 ms (no
     * click at the end), loops do not (their end must flow into their start).
     */
    static float[] normalize(float[] s, boolean fadeOut) {
        float peak = 0f;
        for (float v : s) peak = Math.max(peak, Math.abs(v));
        float gain = peak > 1e-6f ? PEAK / peak : 0f;
        int fade = Math.max(1, Math.min(s.length - 1, Math.round(0.005f * SAMPLE_RATE)));
        for (int i = 0; i < s.length; i++) {
            float tail = fadeOut ? Math.min(1f, (s.length - 1 - i) / (float) fade) : 1f;
            s[i] *= gain * tail;
        }
        return s;
    }

    /** Encodes samples as a mono 16-bit PCM WAV file. */
    public static byte[] toWav(float[] samples) {
        int dataSize = samples.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
        writeAscii(out, "RIFF");
        writeInt(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeInt(out, 16);
        writeShort(out, 1); // PCM
        writeShort(out, 1); // mono
        writeInt(out, SAMPLE_RATE);
        writeInt(out, SAMPLE_RATE * 2);
        writeShort(out, 2);
        writeShort(out, 16);
        writeAscii(out, "data");
        writeInt(out, dataSize);
        for (float v : samples) writeShort(out, Math.round(Math.max(-1f, Math.min(1f, v)) * 32767f));
        return out.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        for (int i = 0; i < text.length(); i++) out.write(text.charAt(i));
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
        out.write((v >> 16) & 0xff);
        out.write((v >> 24) & 0xff);
    }

    private static void writeShort(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
    }

    /** Second-order filter (RBJ cookbook), direct form I. */
    static final class Biquad {
        private float b0, b1, b2, a1, a2;
        private float x1, x2, y1, y2;

        static Biquad bandpass(float freq, float q) {
            Biquad f = new Biquad();
            f.setBandpass(freq, q);
            return f;
        }

        static Biquad lowpass(float freq, float q) {
            Biquad f = new Biquad();
            double w = 2 * Math.PI * freq / SAMPLE_RATE;
            double alpha = Math.sin(w) / (2 * q);
            double cos = Math.cos(w);
            f.set((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
            return f;
        }

        static Biquad highpass(float freq, float q) {
            Biquad f = new Biquad();
            double w = 2 * Math.PI * freq / SAMPLE_RATE;
            double alpha = Math.sin(w) / (2 * q);
            double cos = Math.cos(w);
            f.set((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
            return f;
        }

        /** Band-pass with 0 dB peak gain. */
        void setBandpass(float freq, float q) {
            double w = 2 * Math.PI * Math.min(freq, SAMPLE_RATE * 0.45f) / SAMPLE_RATE;
            double alpha = Math.sin(w) / (2 * Math.max(0.1f, q));
            set(alpha, 0, -alpha, 1 + alpha, -2 * Math.cos(w), 1 - alpha);
        }

        private void set(double nb0, double nb1, double nb2, double na0, double na1, double na2) {
            b0 = (float) (nb0 / na0);
            b1 = (float) (nb1 / na0);
            b2 = (float) (nb2 / na0);
            a1 = (float) (na1 / na0);
            a2 = (float) (na2 / na0);
        }

        float process(float x) {
            float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
