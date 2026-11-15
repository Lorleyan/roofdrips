package com.example.livingrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.example.livingrain.StormDirector;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Safety net for LevelRainMixin: if a Minecraft version declares getRainLevel in ClientLevel itself
 * (instead of inheriting it from Level), this one catches it. If the method is not there, this does nothing.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelRainMixin {

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true, require = 0)
    private void livingrain$rainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        Float v = StormDirector.hookValue(partialTick);
        if (v != null) cir.setReturnValue(v);
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true, require = 0)
    private void livingrain$thunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        Float v = StormDirector.hookThunder(partialTick);
        if (v != null) cir.setReturnValue(v);
    }
}
