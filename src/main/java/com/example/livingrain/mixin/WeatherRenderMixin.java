package com.example.livingrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.livingrain.WeatherHooks;

/**
 * Hides vanilla's rain and snow drawing (the particle rain replaces it).
 * Lives in its own optional mixin config: if Minecraft's names differ, the game still starts and the
 * fallback in StormDirector hides vanilla rain instead. "/livingrain status" shows which layer is working.
 */
@Mixin(targets = "net.minecraft.client.renderer.WeatherEffectRenderer")
public abstract class WeatherRenderMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void livingrain$hideRender(CallbackInfo ci) {
        if (WeatherHooks.shouldCancel(0)) ci.cancel();
    }
}
