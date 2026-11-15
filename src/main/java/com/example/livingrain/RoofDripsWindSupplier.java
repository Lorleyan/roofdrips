package com.example.livingrain;

import java.util.function.Supplier;

/**
 * Registered as the "roofdrips:wind" entrypoint. Roof Drips (1.0.9+) asks this once per tick.
 * Returns {1, windX, windZ, turbulence} to take over Roof Drips' wind, or null to let Roof Drips use its own.
 * Only ever instantiated when Roof Drips is installed.
 */
public class RoofDripsWindSupplier implements Supplier<float[]> {
    @Override
    public float[] get() {
        LivingRainConfig cfg = LivingRainConfig.get();
        if (!cfg.enabled || !cfg.overrideRoofDrips || !cfg.windEnabled) return null;
        return new float[] { 1f, StormWind.x, StormWind.z, StormWind.turbulence };
    }
}
