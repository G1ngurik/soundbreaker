package ru.toone.soundbreaker.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/** Captures mono 44.1 kHz / 16-bit audio from the default microphone. */
public final class MicrophoneRecorder {

    public static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 1, true, false);

    private TargetDataLine line;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public void start() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("No compatible microphone found");
        }
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, 4410);
        line.start();
        buffer.reset();
    }

    /** Reads whatever is currently available; safe to call from the render thread. */
    public void pump() {
        if (line == null) {
            return;
        }
        int available = line.available();
        if (available <= 0) {
            return;
        }
        byte[] chunk = new byte[Math.min(available, 8820)];
        int read = line.read(chunk, 0, chunk.length);
        if (read > 0) {
            buffer.write(chunk, 0, read);
        }
    }

    public boolean isRecording() {
        return line != null;
    }

    /** Current PCM without stopping the line; used for the live waveform. */
    public short[] snapshot() {
        byte[] bytes = buffer.toByteArray();
        short[] pcm = new short[bytes.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            int lo = bytes[2 * i] & 0xFF;
            int hi = bytes[2 * i + 1] & 0xFF;
            pcm[i] = (short) ((hi << 8) | lo);
        }
        return pcm;
    }

    public short[] stop() {
        if (line == null) {
            return new short[0];
        }
        line.stop();
        line.close();
        line = null;

        byte[] bytes = buffer.toByteArray();
        short[] pcm = new short[bytes.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            int lo = bytes[2 * i] & 0xFF;
            int hi = bytes[2 * i + 1] & 0xFF;
            pcm[i] = (short) ((hi << 8) | lo);
        }
        return pcm;
    }

    public void abort() {
        if (line != null) {
            line.close();
            line = null;
        }
        buffer.reset();
    }
}
