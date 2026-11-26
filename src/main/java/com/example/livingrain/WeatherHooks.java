package com.example.livingrain;

/**
 * Hides vanilla's own rain and snow so only this mod's particles show (unless "Show vanilla rain too" is on).
 *
 * Three independent layers, because Minecraft's weather code is renamed often:
 *  1. mixins on WeatherEffectRenderer (draw + collect), kind 0 and 1
 *  2. a mixin on ClientLevel.tickWeatherEffects (vanilla splashes and rain sounds), kind 2 (26.3) and 3 (older versions)
 *  3. a safety net: if none of the above ever fires, the rain level is reported as 0 to the vanilla weather code only
 *     (found by looking at who is asking), see StormDirector.hookValue
 * The counters are shown by "/BRain status".
 */
public final class WeatherHooks {

    public static final String[] KIND_NAMES = { "render", "extractRenderState", "tickWeatherEffects", "tickRainParticles" };
    private static final int[] HITS = new int[4];
    /** Times the safety net (layer 3) had to step in. */
    public static int fallbackHits;

    private WeatherHooks() { }

    /** Total times any mixin hook has run. */
    public static int totalHits() {
        return HITS[0] + HITS[1] + HITS[2] + HITS[3];
    }

    /** Times the drawing hooks (render / extractRenderState) have run. 0 = vanilla rain is not being hidden by a mixin. */
    public static int drawHits() { return HITS[0] + HITS[1]; }

    /** Times the splash/sound hooks have run. */
    public static int tickHits() { return HITS[2] + HITS[3]; }

    public static int hits(int kind) { return HITS[kind]; }

    /** True if vanilla's own rain drawing / splashes / rain sounds should be skipped right now. */
    public static boolean shouldHide() {
        LivingRainConfig cfg = LivingRainConfig.get();
        return cfg.enabled && cfg.particleRain && !cfg.showVanillaRain;
    }

    /** Called by the mixins. Returns true if the vanilla method should be cancelled. */
    public static boolean shouldCancel(int kind) {
        HITS[kind]++;
        return shouldHide();
    }
}
