package com.example.livingrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.example.livingrain.StormDirector;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

/**
 * The heart of the sky/cloud/shader link: on the CLIENT only, the game's rain level is replaced by the
 * smooth storm level from StormDirector. The sky colour, vanilla clouds, shader packs and Roof Drips all read this value.
 * The server's own weather is never touched.
 */
@Mixin(Level.class)
public abstract class LevelRainMixin {

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true, require = 0)
    private void livingrain$rainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        Float v = StormDirector.hookValue(partialTick);
        if (v != null) cir.setReturnValue(v);
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true, require = 0)
    private void livingrain$thunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        Float v = StormDirector.hookThunder(partialTick);
        if (v != null) cir.setReturnValue(v);
    }
}
