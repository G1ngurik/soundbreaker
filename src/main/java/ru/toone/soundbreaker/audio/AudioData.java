package ru.toone.soundbreaker.audio;

import net.minecraft.util.Identifier;

import java.util.Random;

/** Mono PCM plus the features used by the waveform UI and the comparison. */
public final class AudioData {

    public static final int WAVE_POINTS = 256;
    public static final int FRAMES = 64;

    public final short[] samples;
    public final int sampleRate;
    public final float duration;

    /** Signed peaks in [-1, 1], one per bar of the waveform display. */
    public final float[] waveform = new float[WAVE_POINTS];

    /** Per-frame RMS, normalized to its own maximum. */
    public final float[] envelope = new float[FRAMES];

    /** Per-frame zero crossing rate, 0..1. */
    public final float[] crossings = new float[FRAMES];

    private AudioData(short[] samples, int sampleRate) {
        this.samples = samples;
        this.sampleRate = sampleRate;
        this.duration = sampleRate > 0 ? (float) samples.length / sampleRate : 0f;
        computeFeatures();
    }

    public static AudioData fromInterleaved(short[] pcm, int channels, int sampleRate) {
        if (channels <= 0 || sampleRate <= 0) {
            return empty();
        }
        int frames = pcm.length / channels;
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            long sum = 0;
            for (int ch = 0; ch < channels; ch++) {
                sum += pcm[i * channels + ch];
            }
            mono[i] = (short) (sum / channels);
        }
        return new AudioData(mono, sampleRate);
    }

    public static AudioData fromMono(short[] pcm, int sampleRate) {
        return new AudioData(pcm, sampleRate);
    }

    public static AudioData empty() {
        return new AudioData(new short[0], 44100);
    }

    /**
     * Deterministic stand-in used when the block's .ogg cannot be decoded
     * (missing resource, unsupported codec, ...). The mod stays playable.
     */
    public static AudioData synthetic(Identifier soundId) {
        long seed = soundId == null ? 0L : soundId.toString().hashCode();
        Random random = new Random(seed);
        int rate = 44100;
        int length = (int) (rate * (0.35 + Math.abs(seed % 1000) / 1400.0));
        short[] pcm = new short[length];
        double amplitude = 0.0;
        for (int i = 0; i < length; i++) {
            double t = (double) i / length;
            double target = 0.55 + 0.45 * Math.sin(t * Math.PI);
            amplitude += (target - amplitude) * 0.002;
            double noise = random.nextDouble() * 2.0 - 1.0;
            pcm[i] = (short) (noise * amplitude * 26000.0);
        }
        return new AudioData(pcm, rate);
    }

    private void computeFeatures() {
        if (samples.length == 0) {
            return;
        }

        float peak = 1f;
        for (short sample : samples) {
            peak = Math.max(peak, Math.abs(sample) / 32768f);
        }

        for (int i = 0; i < WAVE_POINTS; i++) {
            int from = i * samples.length / WAVE_POINTS;
            int to = Math.min(samples.length, (i + 1) * samples.length / WAVE_POINTS);
            float best = 0f;
            for (int j = from; j < to; j++) {
                float v = samples[j] / 32768f / peak;
                if (Math.abs(v) > Math.abs(best)) {
                    best = v;
                }
            }
            waveform[i] = best;
        }

        int frameSize = Math.max(1, samples.length / FRAMES);
        float[] rms = new float[FRAMES];
        for (int f = 0; f < FRAMES; f++) {
            int from = f * frameSize;
            int to = Math.min(samples.length, from + frameSize);
            if (from >= to) {
                continue;
            }
            long sum = 0;
            int crossingCount = 0;
            boolean previous = samples[from] >= 0;
            for (int j = from; j < to; j++) {
                sum += (long) samples[j] * samples[j];
                boolean current = samples[j] >= 0;
                if (current != previous) {
                    crossingCount++;
                }
                previous = current;
            }
            rms[f] = (float) Math.sqrt((double) sum / (to - from));
            crossings[f] = (float) crossingCount / (to - from);
        }

        float maxRms = 0f;
        for (float v : rms) {
            maxRms = Math.max(maxRms, v);
        }
        if (maxRms <= 0f) {
            maxRms = 1f;
        }
        for (int i = 0; i < FRAMES; i++) {
            envelope[i] = rms[i] / maxRms;
        }
        System.arraycopy(crossings, 0, this.crossings, 0, FRAMES);
    }
}
