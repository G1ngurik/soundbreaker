package ru.toone.soundbreaker.audio;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * Decodes an Ogg Vorbis stream to 16-bit PCM using JOrbis.
 * Adapted from the JOrbis "OggDecoder" example (public domain example code).
 */
public final class OggDecoder {

    private OggDecoder() {
    }

    public record DecodedAudio(int channels, int rate, short[] samples) {
    }

    public static DecodedAudio decode(InputStream input) throws IOException {
        ByteArrayOutputStream dataOut = new ByteArrayOutputStream();

        SyncState oy = new SyncState();
        StreamState os = new StreamState();
        Page og = new Page();
        Packet op = new Packet();
        Info vi = new Info();
        Comment vc = new Comment();
        DspState vd = new DspState();
        Block vb = new Block(vd);

        byte[] buffer;
        int bytes;
        boolean bigEndian = ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN);
        int channels = 0;
        int rate = 0;

        oy.init();

        while (true) {
            int eos = 0;

            int index = oy.buffer(4096);
            buffer = oy.data;
            bytes = input.read(buffer, index, 4096);
            oy.wrote(bytes);

            if (oy.pageout(og) != 1) {
                if (bytes < 4096) {
                    break;
                }
                throw new IOException("Not an Ogg bitstream");
            }

            os.init(og.serialno());
            vi.init();
            vc.init();

            if (os.pagein(og) < 0 || os.packetout(op) != 1
                    || vi.synthesis_headerin(vc, op) < 0) {
                throw new IOException("Not Vorbis audio");
            }

            int i = 0;
            while (i < 2) {
                while (i < 2) {
                    int result = oy.pageout(og);
                    if (result == 0) {
                        break;
                    }
                    if (result == 1) {
                        os.pagein(og);
                        while (i < 2) {
                            result = os.packetout(op);
                            if (result == 0) {
                                break;
                            }
                            if (result < 0) {
                                throw new IOException("Corrupt Vorbis header");
                            }
                            vi.synthesis_headerin(vc, op);
                            i++;
                        }
                    }
                }
                index = oy.buffer(4096);
                buffer = oy.data;
                bytes = input.read(buffer, index, 4096);
                if (bytes == 0 && i < 2) {
                    throw new IOException("Unexpected end of Vorbis headers");
                }
                oy.wrote(bytes);
            }

            channels = vi.channels;
            rate = vi.rate;
            int convSize = Math.max(4096 / channels, 1024);
            byte[] convBuffer = new byte[convSize * channels * 2];

            vd.synthesis_init(vi);
            vb.init(vd);

            float[][][] pcm = new float[1][][];
            int[] pcmIndex = new int[channels];

            while (eos == 0) {
                while (eos == 0) {
                    int result = oy.pageout(og);
                    if (result == 0) {
                        break;
                    }
                    if (result == 1) {
                        os.pagein(og);
                        while (true) {
                            result = os.packetout(op);
                            if (result == 0) {
                                break;
                            }
                            if (result > 0) {
                                if (vb.synthesis(op) == 0) {
                                    vd.synthesis_blockin(vb);
                                }

                                int samples;
                                while ((samples = vd.synthesis_pcmout(pcm, pcmIndex)) > 0) {
                                    float[][] plane = pcm[0];
                                    int count = Math.min(samples, convSize);

                                    for (int ch = 0; ch < channels; ch++) {
                                        int ptr = ch * 2;
                                        int mono = pcmIndex[ch];
                                        for (int j = 0; j < count; j++) {
                                            int value = (int) (plane[ch][mono + j] * 32767.0);
                                            if (value > 32767) {
                                                value = 32767;
                                            } else if (value < -32768) {
                                                value = -32768;
                                            }
                                            if (bigEndian) {
                                                convBuffer[ptr] = (byte) (value >>> 8);
                                                convBuffer[ptr + 1] = (byte) value;
                                            } else {
                                                convBuffer[ptr] = (byte) value;
                                                convBuffer[ptr + 1] = (byte) (value >>> 8);
                                            }
                                            ptr += 2 * channels;
                                        }
                                    }

                                    dataOut.write(convBuffer, 0, 2 * channels * count);
                                    vd.synthesis_read(count);
                                }
                            }
                        }
                        if (og.eos() != 0) {
                            eos = 1;
                        }
                    }
                }

                if (eos == 0) {
                    index = oy.buffer(4096);
                    buffer = oy.data;
                    bytes = input.read(buffer, index, 4096);
                    oy.wrote(bytes);
                    if (bytes == 0) {
                        eos = 1;
                    }
                }
            }

            os.clear();
            vb.clear();
            vd.clear();
            vi.clear();
        }

        oy.clear();

        byte[] pcmBytes = dataOut.toByteArray();
        short[] samples = new short[pcmBytes.length / 2];
        // The example above writes the samples in host byte order.
        for (int i = 0; i < samples.length; i++) {
            int first = pcmBytes[2 * i] & 0xFF;
            int second = pcmBytes[2 * i + 1] & 0xFF;
            samples[i] = (short) (bigEndian ? ((first << 8) | second) : ((second << 8) | first));
        }

        return new DecodedAudio(channels, rate, samples);
    }
}
