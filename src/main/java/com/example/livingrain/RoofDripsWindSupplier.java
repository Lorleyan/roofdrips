package com.example.livingrain;

import java.util.function.Supplier;

/**
 * Registered as the "roofdrips:wind" entrypoint. Immersive Drips (1.1.0+) asks this once per tick.
 * Slots: [0] wind flag, [1] windX, [2] windZ, [3] turbulence, [4] rain flag, [5] rain level for drips, [6] thunder level,
 *        [7] and [8] how far the visible rain moves sideways per block it falls (x, z).
 * Older Immersive Drips builds (before 1.1.0) understand only the first slots; 1.1.0 uses all of them.
 * Returns null when Immersive Rain is off, so Immersive Drips uses everything of its own.
 * Only ever instantiated when Immersive Drips is installed.
 */
public class RoofDripsWindSupplier implements Supplier<float[]> {
    @Override
    public float[] get() {
        LivingRainConfig cfg = LivingRainConfig.get();
        if (!cfg.enabled) return null;
        boolean wind = cfg.overrideRoofDrips && cfg.windEnabled;
        // The rain level is always supplied: this mod changes the game's rain level for the sky and shaders, so Immersive Drips
        // is told explicitly whether to follow the storm's severity or to use the plain server rain, as it would alone.
        return new float[] { wind ? 1f : 0f, StormWind.x, StormWind.z, StormWind.turbulence,
                1f, StormDirector.dripLevel(cfg), StormDirector.rawThunderLevel(),
                RainSpawner.slantX, RainSpawner.slantZ };
    }
}
