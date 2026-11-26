package com.example.livingrain;

import net.minecraft.util.RandomSource;

/**
 * The simulated wind. Vanilla Minecraft has no wind, so this is made up: a base direction that slowly drifts,
 * a per-storm "personality" (Calm / Breezy / Windy / Gale) and smooth gusts. Blocks per tick.
 * Same model and units as Immersive Drips, so both mods can share one wind.
 */
public final class StormWind {

    public static final String[] NAMES = { "Calm", "Breezy", "Windy", "Gale" };
    private static final float[] STRENGTH = { 0.25f, 0.8f, 1.6f, 2.6f };

    public static volatile float x;
    public static volatile float z;
    public static volatile float turbulence;
    public static int personality = 1;
    /** Test mode: -1 = roll normally, 0..3 = always this personality. */
    public static int forced = -1;

    private static double driftPhase, stormDeg, gustAngle, shiftOffset, shiftTarget;
    private static float strCur = 0.8f, gustAmp;
    private static int gustTimer = 200, gustLen, gustAge, shiftTicks, personalityTimer = 1, rainTierHint = 1;

    private StormWind() { }

    static void reset() {
        x = 0f; z = 0f;
        gustLen = 0; shiftTicks = 0; shiftOffset = 0; shiftTarget = 0;
    }

    /** A new storm: new base direction and a freshly rolled personality. */
    static void newStorm(RandomSource rnd, LivingRainConfig cfg, int rainTier, boolean wildcard) {
        stormDeg = rnd.nextDouble() * 360.0;
        rainTierHint = rainTier;
        if (forced >= 0) personality = forced; else pickPersonality(rnd, cfg, wildcard);
        personalityTimer = 20 * (150 + rnd.nextInt(200));
        shiftTicks = 0;
        shiftTarget = 0;
    }

    static void rainTierChanged(int tier) { rainTierHint = tier; }

    /** A sudden change of wind direction and strength (one of the "surprise" events). */
    static void shift(RandomSource rnd) {
        double turn = 40.0 + rnd.nextDouble() * 80.0;
        shiftTarget = rnd.nextBoolean() ? turn : -turn;
        shiftTicks = 20 * (30 + rnd.nextInt(30));
    }

    private static void pickPersonality(RandomSource rnd, LivingRainConfig cfg, boolean wildcard) {
        float[] w = cfg.windWeights();
        float[] a = new float[4];
        float sum = 0f;
        for (int j = 0; j < 4; j++) {
            float closeness = 1f - Math.abs(j - rainTierHint) / 3f;
            a[j] = wildcard ? 1f : w[j] * (1f + cfg.windFollowsRain * 3f * closeness * closeness);
            sum += a[j];
        }
        if (sum <= 0.0001f) { personality = 1; return; }
        float r = rnd.nextFloat() * sum;
        for (int j = 0; j < 4; j++) {
            r -= a[j];
            if (r <= 0f) { personality = j; return; }
        }
        personality = 3;
    }

    private static int nextGustInterval(RandomSource rnd, LivingRainConfig cfg) {
        float freq = cfg.gustFrequency;
        if (freq <= 0.001f) return 20 * 600;
        float sec = 90f + (6f - 90f) * freq;   // 0% -> every ~90 s, 100% -> every ~6 s (on average)
        return Math.max(20, Math.round(20f * sec * (0.6f + 0.8f * rnd.nextFloat())));
    }

    /** Called once per client tick. thunder is 0..1. */
    static void tick(RandomSource rnd, LivingRainConfig cfg, float thunder) {
        if (!cfg.windEnabled) { x = 0f; z = 0f; turbulence = 0f; return; }

        if (forced >= 0) {
            personality = forced;
        } else if (--personalityTimer <= 0) {
            pickPersonality(rnd, cfg, false);
            personalityTimer = 20 * (150 + rnd.nextInt(200));
        }

        float target = STRENGTH[personality] * cfg.windStrength * (1f + 0.5f * thunder);
        if (shiftTicks > 0) {
            target *= 1.4f;
            if (--shiftTicks == 0) shiftTarget = 0;
        }
        strCur += (target - strCur) * 0.004f;
        shiftOffset += (shiftTarget - shiftOffset) * 0.003;

        double deg = cfg.windRandomPerStorm ? stormDeg : cfg.windDirection;
        driftPhase += 0.004;
        deg += (Math.sin(driftPhase) * 60.0 + Math.sin(driftPhase * 2.7 + 1.3) * 25.0) * cfg.windDriftAmount;
        deg += shiftOffset;

        float gust = 0f;
        double gustOff = 0;
        if (gustLen > 0) {
            float t = gustAge / (float) gustLen;
            float swell = (float) Math.sin(Math.PI * t);
            gust = gustAmp * swell;
            gustOff = gustAngle * swell;
            if (++gustAge >= gustLen) { gustLen = 0; gustTimer = nextGustInterval(rnd, cfg); }
        } else if (cfg.gustFrequency > 0.001f && cfg.gustStrength > 0.001f && --gustTimer <= 0) {
            gustLen = Math.max(10, Math.round(cfg.gustDuration * 20f * (0.6f + 0.8f * rnd.nextFloat())));
            gustAge = 0;
            gustAmp = cfg.gustStrength * (0.6f + 0.8f * rnd.nextFloat());
            gustAngle = Math.toRadians((rnd.nextDouble() * 2 - 1) * 30.0);
        }

        double a = Math.toRadians(deg) + gustOff;
        float speed = 0.08f * strCur * (1f + gust);
        x = (float) (Math.sin(a) * speed);     // 0 deg = north = -z, 90 deg = east = +x
        z = (float) (-Math.cos(a) * speed);
        turbulence = cfg.windTurbulence;
    }
}
