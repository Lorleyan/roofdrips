package com.example.livingrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.livingrain.WeatherHooks;

/** Second half of hiding vanilla rain: stops it from collecting the rain columns to draw. Separate so one can fail without the other. */
@Mixin(targets = "net.minecraft.client.renderer.WeatherEffectRenderer")
public abstract class WeatherExtractMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void livingrain$hideExtract(CallbackInfo ci) {
        if (WeatherHooks.shouldCancel(1)) ci.cancel();
    }
}
