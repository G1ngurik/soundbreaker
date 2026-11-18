package ru.toone.soundbreaker.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Plays raw PCM on a daemon thread so the game never blocks. */
public final class AudioPlayer {

    private AudioPlayer() {
    }

    public static void play(short[] pcm, int sampleRate) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            bytes[2 * i] = (byte) pcm[i];
            bytes[2 * i + 1] = (byte) (pcm[i] >> 8);
        }

        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        Thread thread = new Thread(() -> {
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format);
                line.start();
                line.write(bytes, 0, bytes.length);
                line.drain();
            } catch (Exception ignored) {
                // Audio output is optional for the challenge itself.
            }
        }, "SoundBreaker-Playback");
        thread.setDaemon(true);
        thread.start();
    }
}
