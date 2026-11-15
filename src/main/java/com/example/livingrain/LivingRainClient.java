package com.example.livingrain;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;

/** Client side: hooks the particle providers and runs the storm director and rain spawner every tick. */
public class LivingRainClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(LivingRainMod.RAIN, RainParticle.RainProvider::new);
        ParticleProviderRegistry.getInstance().register(LivingRainMod.SNOW, RainParticle.SnowProvider::new);
        ClientTickEvents.END_CLIENT_TICK.register(LivingRainClient::onTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> LivingRainCommands.register(dispatcher));
    }

    private static void onTick(Minecraft mc) {
        StormDirector.tick(mc);
        RainSpawner.tick(mc);
    }
}
