package ru.toone.soundbreaker.screen;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import ru.toone.soundbreaker.SoundBreakerClient;
import ru.toone.soundbreaker.SoundBreakerMod;
import ru.toone.soundbreaker.audio.AudioData;
import ru.toone.soundbreaker.audio.AudioPlayer;
import ru.toone.soundbreaker.audio.MicrophoneRecorder;
import ru.toone.soundbreaker.audio.OggDecoder;
import ru.toone.soundbreaker.audio.Similarity;
import ru.toone.soundbreaker.config.ModConfig;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** The "repeat the block's sound" check window. */
public final class SoundCheckScreen extends Screen {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 270;

    private static final int COLOR_PANEL = 0xFF0B0F16;
    private static final int COLOR_BORDER = 0xFF7A8CFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_LABEL = 0xFFB0C4DE;
    private static final int COLOR_GOOD = 0xFF6BE86B;
    private static final int COLOR_BAD = 0xFFFF6B6B;
    private static final int COLOR_WAVE_ORIGINAL = 0xFF66B2FF;
    private static final int COLOR_WAVE_RECORDING = 0xFF9BE86B;

    private final BlockPos pos;
    private final Direction direction;
    private final Block block;
    private final Text blockName;
    private final SoundEvent sound;
    private final AudioData original;
    private final MicrophoneRecorder recorder = new MicrophoneRecorder();

    private AudioData recorded;
    private int similarity = -1;
    private int attempts;
    private int best = -1;
    private boolean success;
    private Text status = Text.empty();
    private long recordStartedAt;
    private long openedAt = System.currentTimeMillis();

    private ButtonWidget recordButton;
    private ButtonWidget playRecordingButton;

    public SoundCheckScreen(BlockPos pos, Direction direction, Block block, Text blockName, SoundEvent sound) {
        super(Text.translatable("soundbreaker.title", blockName));
        this.pos = pos;
        this.direction = direction;
        this.block = block;
        this.blockName = blockName;
        this.sound = sound;
        this.original = loadOriginal(sound);
    }

    private static AudioData loadOriginal(SoundEvent sound) {
        try {
            Identifier id = sound.getId();
            Identifier resourceId = id.withPath(path -> "sounds/" + path + ".ogg");
            InputStream stream = MinecraftClient.getInstance()
                    .getResourceManager()
                    .getResource(resourceId)
                    .map(resource -> {
                        try {
                            return resource.getInputStream();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .orElse(null);

            if (stream == null) {
                SoundBreakerMod.LOGGER.warn("[Sound Breaker] no sound resource for {}", id);
                return AudioData.synthetic(id);
            }

            byte[] bytes;
            try (InputStream in = stream) {
                bytes = in.readAllBytes();
            }
            OggDecoder.DecodedAudio decoded = OggDecoder.decode(new ByteArrayInputStream(bytes));
            return AudioData.fromInterleaved(decoded.samples(), decoded.channels(), decoded.rate());
        } catch (Exception e) {
            SoundBreakerMod.LOGGER.warn("[Sound Breaker] could not decode block sound", e);
            return AudioData.synthetic(sound.getId());
        }
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int row = top + 208;

        addDrawableChild(ButtonWidget.builder(Text.translatable("soundbreaker.play_original"), button ->
                        MinecraftClient.getInstance().getSoundManager()
                                .play(PositionedSoundInstance.master(sound, 1.0f)))
                .dimensions(left + 8, row, 91, 20).build());

        recordButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("soundbreaker.record"), button -> toggleRecording())
                .dimensions(left + 103, row, 94, 20).build());

        playRecordingButton = addDrawableChild(ButtonWidget.builder(
                        Text.translatable("soundbreaker.play_record"), button -> {
                            if (recorded != null && recorded.samples.length > 0) {
                                AudioPlayer.play(recorded.samples, recorded.sampleRate);
                            } else {
                                status = Text.translatable("soundbreaker.wait_record");
                            }
                        })
                .dimensions(left + 201, row, 91, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("soundbreaker.done"), button -> finish())
                .dimensions(left + 8, row + 26, PANEL_WIDTH - 16, 20).build());
    }

    private void toggleRecording() {
        if (recorder.isRecording()) {
            stopRecording();
            return;
        }
        try {
            recorder.start();
            recordStartedAt = System.currentTimeMillis();
            status = Text.empty();
            updateRecordButton();
        } catch (Exception e) {
            status = Text.translatable("soundbreaker.no_mic",
                    e.getMessage() == null ? "?" : e.getMessage());
            recordButton.setMessage(Text.translatable("soundbreaker.record"));
        }
    }

    private void stopRecording() {
        short[] pcm = recorder.stop();
        recorded = AudioData.fromMono(pcm, (int) MicrophoneRecorder.FORMAT.getSampleRate());
        similarity = Similarity.compare(original, recorded);
        best = Math.max(best, similarity);
        attempts++;
        success = similarity >= ModConfig.threshold();
        status = success
                ? Text.translatable("soundbreaker.success")
                : Text.translatable("soundbreaker.too_low", ModConfig.threshold());
        recordButton.setMessage(Text.translatable("soundbreaker.record"));
    }

    private void updateRecordButton() {
        if (!recorder.isRecording()) {
            return;
        }
        double remaining = ModConfig.recordSeconds()
                - (System.currentTimeMillis() - recordStartedAt) / 1000.0;
        recordButton.setMessage(Text.translatable("soundbreaker.recording",
                String.format("%.1f", Math.max(0.0, remaining))));
    }

    @Override
    public void tick() {
        if (recorder.isRecording()) {
            recorder.pump();
            short[] live = recorder.snapshot();
            if (live.length > 0) {
                recorded = AudioData.fromMono(live, (int) MicrophoneRecorder.FORMAT.getSampleRate());
            }
            updateRecordButton();
            if ((System.currentTimeMillis() - recordStartedAt) / 1000.0 >= ModConfig.recordSeconds()) {
                stopRecording();
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            toggleRecording();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            MinecraftClient.getInstance().getSoundManager()
                    .play(PositionedSoundInstance.master(sound, 1.0f));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finish();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void finish() {
        if (recorder.isRecording()) {
            recorder.abort();
        }
        if (success) {
            SoundBreakerClient.unlock(block);
            SoundBreakerClient.suppressAbortFor(pos);
            SoundBreakerMod.grantBreakPermission();

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.interactionManager != null) {
                client.interactionManager.attackBlock(pos, direction);
            }
            if (ModConfig.effects()) {
                client.getSoundManager().play(
                        PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f));
                if (client.world != null) {
                    client.particleManager.addBlockBreakingParticles(pos, direction);
                }
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.translatable("soundbreaker.success_chat", similarity, blockName), false);
                }
            }
        }
        close();
    }

    @Override
    public void close() {
        recorder.abort();
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        // Solid, high-contrast panel - no transparency, no item rendering that
        // could fail for blocks without an item form.
        context.fill(left, top, right, bottom, COLOR_PANEL);
        drawBorder(context, left, top, right, bottom, COLOR_BORDER);

        context.drawCenteredTextWithShadow(textRenderer, blockName, width / 2, top + 12, COLOR_TEXT);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("soundbreaker.subtitle"), width / 2, top + 28, COLOR_LABEL);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("soundbreaker.original"), width / 2, top + 44, COLOR_LABEL);
        drawWaveform(context, original, left + 12, top + 56, PANEL_WIDTH - 24, 40,
                COLOR_WAVE_ORIGINAL, false);

        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("soundbreaker.your_record"), width / 2, top + 106, COLOR_LABEL);
        drawWaveform(context, recorded, left + 12, top + 118, PANEL_WIDTH - 24, 40,
                COLOR_WAVE_RECORDING, recorder.isRecording());

        if (similarity >= 0) {
            int color = success ? COLOR_GOOD : COLOR_BAD;
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("soundbreaker.similarity", similarity),
                    width / 2, top + 166, color);
            drawMeter(context, left + 12, top + 180, PANEL_WIDTH - 24, 8, similarity, color);
            context.drawCenteredTextWithShadow(textRenderer, status, width / 2, top + 194, color);
        } else {
            String hint = recorder.isRecording()
                    ? "soundbreaker.recording_hint"
                    : "soundbreaker.hint";
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable(hint), width / 2, top + 170, COLOR_LABEL);
            if (!status.getString().isEmpty()) {
                context.drawCenteredTextWithShadow(textRenderer, status, width / 2, top + 186, COLOR_BAD);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static void drawBorder(DrawContext context, int left, int top, int right, int bottom, int color) {
        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom - 1, right, bottom, color);
        context.fill(left, top, left + 1, bottom, color);
        context.fill(right - 1, top, right, bottom, color);
    }

    private void drawWaveform(DrawContext context, AudioData data, int x, int y, int w, int h,
                              int color, boolean live) {
        context.fill(x, y, x + w, y + h, 0xFF141822);
        int center = y + h / 2;
        context.fill(x, center, x + w, center + 1, 0xFF505868);

        if (data == null) {
            return;
        }

        float[] wave = data.waveform;
        float barWidth = (float) w / wave.length;
        for (int i = 0; i < wave.length; i++) {
            int half = Math.max(1, (int) (Math.abs(wave[i]) * (h / 2f - 2)));
            int px = x + (int) (i * barWidth);
            int c = live && i > wave.length * 0.8f ? COLOR_GOOD : color;
            context.fill(px, center - half, px + Math.max(1, (int) barWidth), center + half, c);
        }

        // Animated scan line while recording.
        if (live) {
            long phase = (System.currentTimeMillis() - openedAt) % 1400L;
            int scanX = x + (int) (phase / 1400.0 * w);
            context.fill(scanX, y, scanX + 2, y + h, 0x80FFFFFF);
        }
    }

    private static void drawMeter(DrawContext context, int x, int y, int w, int h,
                                  int value, int color) {
        context.fill(x, y, x + w, y + h, 0xFF20242E);
        context.fill(x, y, x + (int) (w * Math.max(0, Math.min(100, value)) / 100f), y + h, color);
        int thresholdX = x + (int) (w * ModConfig.threshold() / 100f);
        context.fill(thresholdX, y - 2, thresholdX + 1, y + h + 2, 0xFFFFFFFF);
    }
}
