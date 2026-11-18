package ru.toone.soundbreaker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import ru.toone.soundbreaker.config.ModConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side bookkeeping:
 *  - suppresses the ABORT_DESTROY_BLOCK packet right after a successful check,
 *    so survival mode actually finishes breaking the block;
 *  - remembers block types that were already verified successfully, so the
 *    check window does not pop up again for every single block.
 */
public final class SoundBreakerClient implements ClientModInitializer {

    private static final Map<Block, Long> UNLOCKED = new HashMap<>();

    private static BlockPos suppressAbortPos;
    private static long suppressAbortSince;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(SoundBreakerClient::tick);
    }

    public static void suppressAbortFor(BlockPos pos) {
        suppressAbortPos = pos;
        suppressAbortSince = System.currentTimeMillis();
    }

    public static boolean shouldSuppressAbort() {
        return suppressAbortPos != null
                && System.currentTimeMillis() - suppressAbortSince < 5000L;
    }

    public static boolean isUnlocked(Block block) {
        if (!ModConfig.rememberSuccess() || ModConfig.passSeconds() <= 0.0) {
            return false;
        }
        Long until = UNLOCKED.get(block);
        return until != null && System.currentTimeMillis() < until;
    }

    public static void unlock(Block block) {
        if (ModConfig.rememberSuccess() && ModConfig.passSeconds() > 0.0) {
            UNLOCKED.put(block, System.currentTimeMillis() + (long) (ModConfig.passSeconds() * 1000L));
        }
    }

    private static void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();

        if (suppressAbortPos != null) {
            boolean gone = client.world == null
                    || client.world.getBlockState(suppressAbortPos).isAir()
                    || now - suppressAbortSince > 5000L;
            if (gone) {
                suppressAbortPos = null;
            }
        }

        Iterator<Map.Entry<Block, Long>> iterator = UNLOCKED.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
            }
        }
    }
}
