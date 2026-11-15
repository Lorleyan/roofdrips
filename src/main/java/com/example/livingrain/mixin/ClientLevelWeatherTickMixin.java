package com.example.livingrain.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.livingrain.WeatherHooks;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Minecraft 26.2+: vanilla's rain splashes and rain sounds are made in ClientLevel.tickWeatherEffects.
 * This mod makes its own, so vanilla's are skipped. Does nothing if the method does not exist.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelWeatherTickMixin {

    @Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true, require = 0)
    private void livingrain$noVanillaWeatherTick(CallbackInfo ci) {
        if (WeatherHooks.shouldCancel(2)) ci.cancel();
    }
}
