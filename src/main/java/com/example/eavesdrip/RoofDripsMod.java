package com.example.eavesdrip;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Registers the two custom water particle types. */
public class RoofDripsMod implements ModInitializer {

    /** A falling droplet (gravity, lands, optional splash). */
    public static final SimpleParticleType DROP = FabricParticleTypes.simple();
    /** A stationary fading dot, used to draw streaks running down block faces. */
    public static final SimpleParticleType DOT = FabricParticleTypes.simple();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("roofdrips", "water_drop"), DROP);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("roofdrips", "water_dot"), DOT);
    }
}
