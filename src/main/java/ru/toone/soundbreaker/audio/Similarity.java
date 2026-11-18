package ru.toone.soundbreaker.audio;

/** Compares the shape of two sounds and produces a 0..100 "similarity". */
public final class Similarity {

    private Similarity() {
    }

    public static int compare(AudioData original, AudioData recorded) {
        if (original == null || recorded == null
                || original.samples.length == 0 || recorded.samples.length == 0) {
            return 0;
        }

        // Amplitude envelope (the main cue: how the sound swells and fades).
        double envelope = 1.0 - bestEnvelopeDistance(original.envelope, recorded.envelope, 6);

        // Noisiness / pitch-ness over time.
        double crossings = 1.0 - meanAbsoluteDifference(original.crossings, recorded.crossings);

        // Duration match, with a generous tolerance.
        double longest = Math.max(0.4, Math.max(original.duration, recorded.duration));
        double duration = 1.0 - Math.min(1.0, Math.abs(original.duration - recorded.duration) / longest);

        double score = 0.60 * envelope + 0.30 * crossings + 0.10 * duration;
        return (int) Math.round(100.0 * Math.max(0.0, Math.min(1.0, score)));
    }

    private static double bestEnvelopeDistance(float[] a, float[] b, int maxShift) {
        double best = Double.MAX_VALUE;
        for (int shift = -maxShift; shift <= maxShift; shift++) {
            double distance = shiftedDistance(a, b, shift);
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    private static double shiftedDistance(float[] a, float[] b, int shift) {
        double diff = 0.0;
        double total = 0.0;
        int n = a.length;
        for (int i = 0; i < n; i++) {
            int j = ((i + shift) % n + n) % n;
            diff += Math.abs(a[i] - b[j]);
            total += a[i] + b[j];
        }
        return total <= 1e-6 ? 0.0 : diff / total;
    }

    private static double meanAbsoluteDifference(float[] a, float[] b) {
        double sum = 0.0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            sum += Math.abs(a[i] - b[i]);
        }
        return n == 0 ? 1.0 : sum / n;
    }
}
