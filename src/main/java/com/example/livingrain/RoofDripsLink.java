package com.example.livingrain;

import java.lang.reflect.Field;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional link to the Immersive Drips mod. Uses reflection, so this mod needs nothing from Immersive Drips at build time
 * and works the same whether or not Immersive Drips is installed.
 */
public final class RoofDripsLink {

    private static Boolean present;
    private static boolean inited;
    private static boolean overrideSupported, rainScalingSupported;
    private static Field fActive, fX, fZ;
    private static final float[] OUT = new float[2];

    private RoofDripsLink() { }

    public static boolean present() {
        if (present == null) present = FabricLoader.getInstance().isModLoaded("roofdrips");
        return present;
    }

    /** True if the installed Immersive Drips is a version that accepts a wind override (1.1.0 or newer). */
    public static boolean supportsOverride() {
        init();
        return overrideSupported;
    }

    /** True if the installed Immersive Drips can take a rain level from this mod (1.1.0 or newer). */
    public static boolean supportsRainScaling() {
        init();
        return rainScalingSupported;
    }

    /** Immersive Drips' current wind as {x, z} in blocks per tick, or null if it has none right now. */
    public static float[] readWind() {
        init();
        if (fX == null || fZ == null || fActive == null) return null;
        try {
            if (!fActive.getBoolean(null)) return null;
            OUT[0] = fX.getFloat(null);
            OUT[1] = fZ.getFloat(null);
            return OUT;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void init() {
        if (inited) return;
        inited = true;
        if (!present()) return;
        try {
            Class<?> wind = Class.forName("com.example.eavesdrip.Wind");
            fActive = wind.getField("active");
            fX = wind.getField("x");
            fZ = wind.getField("z");
            try {
                wind.getField("rainOverridden");
                rainScalingSupported = true;
            } catch (NoSuchFieldException e) {
                rainScalingSupported = false;
            }
        } catch (Throwable t) {
            fActive = null; fX = null; fZ = null;
        }
        try {
            Class.forName("com.example.eavesdrip.WindOverrides");
            overrideSupported = true;
        } catch (Throwable t) {
            overrideSupported = false;
        }
    }
}
