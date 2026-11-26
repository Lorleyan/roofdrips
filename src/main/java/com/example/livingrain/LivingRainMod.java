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
    public static final SimpleParticleType RAIN_SOLID = FabricParticleTypes.simple();
    public static final SimpleParticleType RAIN_GLINT_A = FabricParticleTypes.simple();
    /** Four sheet pictures with different streak patterns, so no two patches look alike. */
    public static final SimpleParticleType[] RAIN_SHEET = {
            FabricParticleTypes.simple(), FabricParticleTypes.simple(), FabricParticleTypes.simple(), FabricParticleTypes.simple() };
    public static final SimpleParticleType RAIN_GLINT_B = FabricParticleTypes.simple();
    public static final SimpleParticleType SNOW = FabricParticleTypes.simple();

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("livingrain", "rain_streak"), RAIN);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("livingrain", "rain_streak_solid"), RAIN_SOLID);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("livingrain", "rain_streak_glint_a"), RAIN_GLINT_A);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("livingrain", "rain_streak_glint_b"), RAIN_GLINT_B);
        for (int i = 0; i < RAIN_SHEET.length; i++) {
            Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                    Identifier.fromNamespaceAndPath("livingrain", "rain_sheet_" + (char) ('a' + i)), RAIN_SHEET[i]);
        }
        Registry.register(BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("livingrain", "snow_flake"), SNOW);
    }
}
