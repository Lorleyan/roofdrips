package com.example.eavesdrip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Lets another mod (Living Rain) take over the wind. That mod declares a "roofdrips:wind" entrypoint in its
 * fabric.mod.json: a Supplier that returns {1, windX, windZ, turbulence} (blocks per tick) to take over,
 * or null to stay out of it. Roof Drips needs nothing else from that mod, and works exactly as before without it.
 */
public final class WindOverrides {

    private static List<Supplier<float[]>> suppliers;
    private static boolean warned;

    private WindOverrides() { }

    /** The first override on offer this tick, or null. */
    public static float[] poll() {
        if (suppliers == null) load();
        for (Supplier<float[]> s : suppliers) {
            try {
                float[] v = s.get();
                if (v != null && v.length >= 4 && v[0] > 0.5f) return v;
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    System.err.println("[roofdrips] A wind override failed and is being ignored: " + t);
                }
            }
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void load() {
        suppliers = new ArrayList<>();
        try {
            List<Supplier> found = FabricLoader.getInstance().getEntrypoints("roofdrips:wind", Supplier.class);
            for (Supplier s : found) suppliers.add((Supplier<float[]>) s);
        } catch (Throwable t) {
            System.err.println("[roofdrips] Could not load wind overrides: " + t);
        }
    }
}
