package com.example.livingrain;

import java.lang.reflect.Method;

import org.slf4j.LoggerFactory;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Lightning flashes. When lightning strikes, Minecraft sets a short "sky flash" timer on the client; this watches it,
 * so the rain can light up for an instant. The method's name is looked up at run time; if this Minecraft version has
 * no such method the flash simply never happens (and /BRain status says so). /BRain flash fakes one for testing.
 */
public final class Flash {

    /** 0..1: the current flash, fading out over a few ticks. */
    public static float level;
    public static boolean sourceFound;

    private static Method skyFlash;
    private static boolean searched, manual, warned;

    private Flash() { }

    public static void trigger() { manual = true; }

    public static void update(ClientLevel clientLevel) {
        boolean flash = manual;
        manual = false;
        try {
            if (!searched) {
                searched = true;
                try { skyFlash = ClientLevel.class.getMethod("getSkyFlashTime"); } catch (NoSuchMethodException e) { skyFlash = null; }
            }
            if (skyFlash != null) {
                sourceFound = true;
                if (((Number) skyFlash.invoke(clientLevel)).intValue() > 0) flash = true;
            } else if (!warned) {
                warned = true;
                LoggerFactory.getLogger("livingrain").info("[livingrain] No lightning-flash timer found in this Minecraft version: rain will not flash with lightning.");
            }
        } catch (Throwable t) {
            // ignore
        }
        level = Math.max(level * 0.80f, flash ? 1f : 0f);
        if (level < 0.01f) level = 0f;
    }
}
