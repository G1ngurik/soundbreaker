package ru.toone.soundbreaker;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.toone.soundbreaker.config.ModConfig;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entrypoint. The mod is client-side only.
 *
 * When the player tries to break a block, the client-side mixin cancels the
 * attack and opens {@link ru.toone.soundbreaker.screen.SoundCheckScreen}.
 * The player has to imitate the block's breaking sound into the microphone;
 * the recorded waveform is compared with the original one and the block is
 * only broken when the similarity is high enough.
 */
public final class SoundBreakerMod implements ModInitializer {

    public static final String MOD_ID = "soundbreaker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Set right before we re-issue the attack ourselves from the success screen.
     * The mixin consumes it and lets that single attack through.
     */
    private static final AtomicBoolean ALLOW_NEXT_BREAK = new AtomicBoolean(false);

    @Override
    public void onInitialize() {
        ModConfig.load();
        LOGGER.info("[Sound Breaker] initialized (threshold={}%, record={}s)",
                ModConfig.threshold(), ModConfig.recordSeconds());
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static boolean consumeBreakPermission() {
        return ALLOW_NEXT_BREAK.getAndSet(false);
    }

    public static void grantBreakPermission() {
        ALLOW_NEXT_BREAK.set(true);
    }
}
