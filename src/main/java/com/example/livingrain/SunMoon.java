package com.example.livingrain;

import java.lang.reflect.Method;

import org.slf4j.LoggerFactory;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Which way the sun or moon is, so rain can glow when you look toward the light (the way rain looks backlit in films and games).
 * Minecraft's name for "what time of day is it" has changed between versions, so this looks for the known ones by name
 * and simply does nothing (no backlight glow) if none exists.
 */
public final class SunMoon {

    /** True if the time of day could be read. */
    public static boolean available;
    /** Unit vector toward the strongest light (sun by day, moon by night): x east, y up, z south. */
    public static float lx, ly = 1f, lz;
    /** 0..1: how strong that light is (0 when it is below the horizon). The moon counts as weaker. */
    public static float strength;

    private static Method timeOfDay, dayTime;
    private static boolean searched, warned;

    private SunMoon() { }

    public static void update(ClientLevel level) {
        float angle = celestialAngle(level);
        if (Float.isNaN(angle)) {
            available = false;
            strength = 0f;
            return;
        }
        available = true;
        double phi = angle * Math.PI * 2.0;
        // From Minecraft's sky renderer: the sun is at (-sin, cos, 0) of the celestial angle (0 = noon), the moon opposite.
        float sunX = (float) -Math.sin(phi), sunY = (float) Math.cos(phi);
        if (sunY > -0.05f) {
            lx = sunX; ly = sunY; lz = 0f;
            strength = smooth(-0.08f, 0.12f, sunY);        // full strength a little above the horizon: golden-hour rain glows
        } else {
            lx = -sunX; ly = -sunY; lz = 0f;
            strength = 0.6f * smooth(-0.08f, 0.12f, -sunY);
        }
    }

    private static float smooth(float a, float b, float v) {
        float t = Math.max(0f, Math.min(1f, (v - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    /** Celestial angle: 0 = noon, 0.25 = sunset, 0.5 = midnight, 0.75 = sunrise. NaN if unknown. */
    private static float celestialAngle(ClientLevel level) {
        try {
            if (!searched) {
                searched = true;
                // Looked up on the public ClientLevel class (not the object's exact class), so the call is always allowed.
                try { timeOfDay = ClientLevel.class.getMethod("getTimeOfDay", float.class); } catch (NoSuchMethodException e) { timeOfDay = null; }
                try { dayTime = ClientLevel.class.getMethod("getDayTime"); } catch (NoSuchMethodException e) { dayTime = null; }
            }
            if (timeOfDay != null) return ((Number) timeOfDay.invoke(level, 1.0f)).floatValue();
            if (dayTime != null) {
                // Same formula Minecraft uses to turn the tick of the day into the sun's position.
                long t = ((Number) dayTime.invoke(level)).longValue();
                double d = (t / 24000.0 - 0.25);
                d -= Math.floor(d);
                double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
                return (float) ((d * 2.0 + e) / 3.0);
            }
        } catch (Throwable t) {
            // fall through
        }
        if (!warned) {
            warned = true;
            LoggerFactory.getLogger("livingrain").info("[livingrain] Could not read the time of day, so the backlight glow on rain is off.");
        }
        return Float.NaN;
    }
}
