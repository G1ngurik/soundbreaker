package ru.toone.soundbreaker.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.toone.soundbreaker.SoundBreakerClient;
import ru.toone.soundbreaker.SoundBreakerMod;
import ru.toone.soundbreaker.config.ModConfig;
import ru.toone.soundbreaker.screen.SoundCheckScreen;

/** Cancels the normal attack and opens the sound check instead. */
@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void soundBreaker$onAttackBlock(BlockPos pos, Direction direction,
                                            CallbackInfoReturnable<Boolean> info) {
        if (SoundBreakerMod.consumeBreakPermission()) {
            return; // Our own re-issued attack after a successful check.
        }
        if (!ModConfig.enabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null || client.interactionManager == null
                || client.currentScreen != null) {
            return;
        }

        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        Block block = state.getBlock();
        if (SoundBreakerClient.isUnlocked(block)) {
            return; // Already verified recently - break normally, no pop-up.
        }

        BlockSoundGroup soundGroup = state.getSoundGroup();
        SoundEvent breakSound = soundGroup.getBreakSound();

        client.setScreen(new SoundCheckScreen(pos, direction, block, state.getBlock().getName(), breakSound));
        info.setReturnValue(false);
    }

    /**
     * Vanilla sends ABORT_DESTROY_BLOCK on the next tick after the GUI closes
     * because the attack key is no longer held. Skip it for our successful
     * break so the server can finish breaking the block.
     */
    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void soundBreaker$onCancelBlockBreaking(CallbackInfo info) {
        if (SoundBreakerClient.shouldSuppressAbort()) {
            info.cancel();
        }
    }
}
