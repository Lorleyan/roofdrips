package com.example.livingrain;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Registers the two custom particle types. */
public class LivingRainMod implements ModInitializer {

    public static final SimpleParticleType RAIN = FabricParticleTypes.simple();
    public static final SimpleParticleType SNOW = FabricParticleTypes.simple();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("livingrain", "rain_streak"), RAIN);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("livingrain", "snow_flake"), SNOW);
    }
}
