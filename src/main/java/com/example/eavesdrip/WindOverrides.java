package com.example.eavesdrip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Lets another mod (Living Rain) take over the wind and the rain level. That mod declares a "roofdrips:wind" entrypoint in its
 * fabric.mod.json: a Supplier that returns a float array, or null to stay out of it. The array slots are:
 *   [0] wind flag (1 = use the wind in [1]..[3])   [1] windX   [2] windZ   [3] turbulence   (blocks per tick)
 *   [4] rain flag (1 = use the rain level in [5] and [6])   [5] rain level 0..1+ that scales every drip   [6] thunder level
 * Older suppliers return only the first four slots. Roof Drips needs nothing else from that mod,
 * and works exactly as before without it.
 */
public final class WindOverrides {

    private static List<Supplier<float[]>> suppliers;
    private static boolean warned;

    private WindOverrides() { }

    /** The first supplier answer this tick, or null. Each part (wind, rain) is applied only if its own flag is set. */
    public static float[] poll() {
        if (suppliers == null) load();
        for (Supplier<float[]> s : suppliers) {
            try {
                float[] v = s.get();
                if (v != null && v.length >= 4) return v;
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
