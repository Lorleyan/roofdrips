package com.example.livingrain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Decides what each storm looks like. The server only tells the client "it is raining" (and how hard, 0..1,
 * ramping over about 5 seconds). This class turns that into a living storm: it picks an intensity tier
 * (drizzle / light / heavy / torrential), eases between tiers, builds storms up gently, tapers them off,
 * and sometimes throws in a squall or a lull. The result is exposed as a smooth "virtual rain level" that the
 * rain particles use and that (through a mixin) the sky, clouds and shader packs read instead of the vanilla value.
 * Everything here is client-side and purely visual.
 *
 * Test mode (Debug tab) replaces the server's weather with a chosen one, on this client only.
 */
public final class StormDirector {

    public static final String[] TIER_NAMES = { "Drizzle", "Light rain", "Heavy rain", "Torrential" };
    private static final float[] TIER_LEVEL = { 0.25f, 0.50f, 0.75f, 1.00f };
    private static final Logger LOG = LoggerFactory.getLogger("livingrain");

    // ---- read by the mixins ----
    private static boolean controlling;
    private static boolean testActive;
    public static boolean bypass;
    private static float probe = Float.NaN;

    // ---- state ----
    private static Minecraft mcRef;
    private static ClientLevel lastLevel;
    private static RandomSource rnd;
    private static boolean stormOn, thunderOn, snapNext, hookChecked, hookOk, tapering;
    private static float prevRain, curRain, rawRain, rawThunder, prevThunder, curThunder;
    private static int stormAge, tier, tierTimer, eventTicks, worldTicks, lastWind = -1;
    private static float tierBase, eventOffset, wanderPhase;
    private static long clock;

    /** Per-storm character, read by the rain spawner. */
    public static float densityMul = 1f, speedMul = 1f, sizeMul = 1f;

    private StormDirector() { }

    // ================= Public read-outs =================

    /** What the mixin should return for getRainLevel(partialTick), or null to leave vanilla's answer alone. */
    public static Float hookValue(float partialTick) {
        if (!Float.isNaN(probe)) return probe;
        if (bypass) return null;
        // Safety net: if a weather mixin is not working, make vanilla's own weather code see "no rain" instead.
        if (worldTicks > 100 && WeatherHooks.shouldHide()) {
            int need = WeatherHooks.drawHits() == 0 ? 1 : 0;
            if (WeatherHooks.tickHits() == 0) need |= 2;
            if (need != 0) {
                int caller = vanillaWeatherCaller();
                if ((caller == 1 && (need & 1) != 0) || (caller == 2 && (need & 2) != 0)) {
                    WeatherHooks.fallbackHits++;
                    return 0f;
                }
            }
        }
        if (!controlling) return null;
        return Mth.lerp(partialTick, prevRain, curRain);
    }

    /** 1 = asked by vanilla's rain drawing, 2 = by its splash/sound code, 0 = by anything else. */
    private static int vanillaWeatherCaller() {
        return StackWalker.getInstance().walk(frames -> {
            java.util.Iterator<StackWalker.StackFrame> it = frames.limit(10).iterator();
            while (it.hasNext()) {
                StackWalker.StackFrame f = it.next();
                String m = f.getMethodName();
                if (m.equals("tickWeatherEffects") || m.equals("tickRainParticles")) return 2;
                if (f.getClassName().endsWith("WeatherEffectRenderer")) return 1;
            }
            return 0;
        });
    }

    /** Same for getThunderLevel. Only used in test mode; otherwise vanilla's thunder (scaled by our rain level) applies. */
    public static Float hookThunder(float partialTick) {
        if (!Float.isNaN(probe) || bypass || !testActive) return null;
        return Mth.lerp(partialTick, prevThunder, curThunder) * Mth.lerp(partialTick, prevRain, curRain);
    }

    /** Current smooth storm intensity 0..1 (what the rain particles use). */
    public static float intensity() { return curRain; }
    public static boolean stormOn() { return stormOn; }
    public static boolean thunderOn() { return thunderOn; }
    public static boolean hookWorks() { return hookOk; }
    public static String tierName() { return TIER_NAMES[tier]; }
    public static float rawRain() { return rawRain; }
    public static float rawThunder() { return rawThunder; }
    public static int worldTicks() { return worldTicks; }
    public static boolean testActive() { return testActive; }

    /** One line describing the current weather, for the Debug tab. */
    public static String statusLine() {
        if (lastLevel == null) return "Not in a world.";
        String w = stormOn ? TIER_NAMES[tier] + (thunderOn ? " + thunder" : "") : (curRain > 0.001f ? "clearing up" : "clear");
        return String.format("Now: %s (level %.2f), wind: %s%s", w, curRain, StormWind.NAMES[StormWind.personality],
                testActive ? "   [TEST MODE ON]" : "");
    }

    // ================= Tick =================

    static void reset() {
        prevRain = 0f; curRain = 0f; prevThunder = 0f; curThunder = 0f;
        stormOn = false; thunderOn = false; controlling = false; testActive = false; tapering = false;
        eventTicks = 0; eventOffset = 0f; stormAge = 0; worldTicks = 0; lastWind = -1;
        lastLevel = null;
        RainParticle.clearAll();     // Minecraft discards particles on world change without telling them
        StormWind.forced = -1;
        StormWind.reset();
        WeatherMessages.resetCooldown();
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LivingRainConfig cfg = LivingRainConfig.get();
        if (level == null || mc.player == null || !cfg.enabled) { reset(); return; }
        if (level != lastLevel) {
            reset();
            lastLevel = level;
            snapNext = true;
        }
        if (mc.isPaused()) return;
        mcRef = mc;
        rnd = level.getRandom();
        worldTicks++;
        clock++;
        checkHook(level);
        if (worldTicks == 400 && WeatherHooks.shouldHide() && (WeatherHooks.drawHits() == 0 || WeatherHooks.tickHits() == 0)) {
            LOG.warn("[livingrain] Some vanilla weather hooks never fired (draw hits: {}, splash/sound hits: {}). "
                    + "Using the fallback method to hide vanilla rain. Run /livingrain status and send me the result.",
                    WeatherHooks.drawHits(), WeatherHooks.tickHits());
        }

        boolean test = cfg.testMode;
        testActive = test;
        StormWind.forced = (test && cfg.testWind > 0) ? cfg.testWind - 1 : -1;

        float r;
        if (test) {
            // The server's weather is ignored; use the one chosen in the Debug tab.
            r = cfg.testWeather > 0 ? 1f : 0f;
            rawRain = r;
            rawThunder = cfg.testWeather == 2 ? 1f : 0f;
        } else {
            // The REAL values from the server. The mixins step aside while we read them.
            bypass = true;
            float ts;
            try {
                r = level.getRainLevel(1.0f);
                ts = level.getThunderLevel(1.0f);
            } finally {
                bypass = false;
            }
            rawRain = r;
            rawThunder = r > 0.05f ? Math.min(1f, ts / r) : 0f;
        }

        boolean snap = snapNext;       // only the very first tick in a world counts as "joined while raining"
        snapNext = false;

        boolean raining = r > 0.06f;
        if (raining && !stormOn) startStorm(cfg, snap);
        if (!raining && stormOn) endStorm(cfg);
        stormOn = raining;

        if (stormOn) {
            stormAge++;
            boolean th = thunderOn ? rawThunder > 0.3f : rawThunder > 0.6f;
            if (th != thunderOn) {
                thunderOn = th;
                // While vanilla is fading the storm out, thunder ending is just part of that: no new roll, no message.
                if (th || rawRain > 0.9f) {
                    pickFresh(cfg, th ? "thunder started" : "thunder ended");
                    say(WeatherMessages.pick(th ? WeatherMessages.THUNDER_START : WeatherMessages.THUNDER_END, rnd), true);
                }
            } else if (test && tier != cfg.testTier) {
                int old = tier;
                setTier(cfg, cfg.testTier, "test setting");
                announceTierChange(old, tier);
            } else if (--tierTimer <= 0) {
                int old = tier;
                advanceTier(cfg);
                announceTierChange(old, tier);
            }
            updateEvents(cfg);
        }

        StormWind.tick(rnd, cfg, rawThunder);
        if (stormOn && StormWind.personality != lastWind) {
            if (lastWind >= 0 && cfg.windEnabled && cfg.windMessages) announceWindChange(lastWind, StormWind.personality);
            lastWind = StormWind.personality;
        }

        float target = 0f;
        if (stormOn) {
            float wander = test ? 0f : (float) Math.sin(stormAge * 0.004 + wanderPhase) * 0.04f * cfg.tierRandomness;
            target = clamp(tierBase + eventOffset + wander, 0.22f, 1f);
        }

        float buildUp = test ? 3f : cfg.buildUpSeconds;
        float blend = test ? 3f : cfg.transitionSeconds;
        float taper = test ? 3f : cfg.taperSeconds;

        prevRain = curRain;
        if (stormOn) {
            if (snap) {
                curRain = target;            // joined the world while it was already raining
                prevRain = target;
            } else {
                int startTicks = Math.max(1, Math.round(buildUp)) * 40;
                float secs = stormAge < startTicks ? buildUp : blend;
                float k = secs <= 0f ? 1f : Math.min(1f, 1f / (20f * secs));
                curRain += (target - curRain) * k;
                if (Math.abs(target - curRain) < 0.002f) curRain = target;
            }
        } else if (curRain > 0f) {
            float step = taper <= 0f ? 1f : 1f / (20f * taper);
            curRain = Math.max(0f, curRain - step);
            if (curRain <= 0f && tapering) {
                tapering = false;
                say(WeatherMessages.pick(WeatherMessages.STORM_ENDED, rnd), true);
            }
        }

        prevThunder = curThunder;
        if (test) curThunder += (rawThunder - curThunder) * (1f / 60f);
        else curThunder = 0f;

        controlling = test || (cfg.controlRainLevel && (stormOn || curRain > 0.001f));
    }

    // ================= Storm life =================

    private static void startStorm(LivingRainConfig cfg, boolean snap) {
        stormAge = 0;
        tapering = false;
        wanderPhase = rnd.nextFloat() * 6.2831f;
        float s = cfg.testMode ? 0f : cfg.surprise;
        densityMul = 1f + (rnd.nextFloat() * 2f - 1f) * 0.35f * s;
        speedMul = 1f + (rnd.nextFloat() * 2f - 1f) * 0.20f * s;
        sizeMul = 1f + (rnd.nextFloat() * 2f - 1f) * 0.25f * s;
        eventTicks = 0;
        eventOffset = 0f;
        thunderOn = rawThunder > 0.6f;
        pickFresh(cfg, "storm started");
        StormWind.newStorm(rnd, cfg, tier, rnd.nextFloat() < s * 0.6f);
        lastWind = StormWind.personality;
        if (!snap) {
            say(thunderOn ? WeatherMessages.pick(WeatherMessages.THUNDER_START, rnd)
                          : WeatherMessages.pick(WeatherMessages.START[tier], rnd), true);
        }
        if (cfg.logStorms) {
            LOG.info("[livingrain] wind: {}, character: density x{}, speed x{}", StormWind.NAMES[StormWind.personality],
                    String.format("%.2f", densityMul), String.format("%.2f", speedMul));
        }
    }

    private static void endStorm(LivingRainConfig cfg) {
        thunderOn = false;
        eventTicks = 0;
        eventOffset = 0f;
        tapering = true;
        say(WeatherMessages.pick(WeatherMessages.STORM_ENDING, rnd), true);
        if (cfg.logStorms) LOG.info("[livingrain] storm ended (tapering off over {} s)", cfg.taperSeconds);
    }

    private static float[] weights(LivingRainConfig cfg) {
        return thunderOn ? cfg.thunderWeights() : cfg.rainWeights();
    }

    /** Weighted random pick. Falls back to "Light rain" if every weight is zero. */
    private static int weighted(float[] w) {
        float sum = 0f;
        for (float v : w) sum += Math.max(0f, v);
        if (sum <= 0.0001f) return 1;
        float r = rnd.nextFloat() * sum;
        for (int j = 0; j < w.length; j++) {
            r -= Math.max(0f, w[j]);
            if (r <= 0f) return j;
        }
        return w.length - 1;
    }

    /** Pick a tier from the plain chances (storm start, thunder starting or ending). */
    private static void pickFresh(LivingRainConfig cfg, String why) {
        int t;
        if (cfg.testMode) {
            t = cfg.testTier;
        } else if (rnd.nextFloat() < cfg.surprise * 0.4f) {
            t = thunderOn ? 2 + rnd.nextInt(2) : rnd.nextInt(4);     // wildcard
        } else {
            t = weighted(weights(cfg));
        }
        setTier(cfg, t, why);
    }

    /** Move to a new tier: mostly to a neighbour, weighted by the chances. */
    private static void advanceTier(LivingRainConfig cfg) {
        float[] w = weights(cfg);
        float[] a = new float[4];
        for (int j = 0; j < 4; j++) {
            int dist = Math.abs(j - tier);
            float f = dist == 0 ? cfg.stickiness : (dist == 1 ? 1f : (float) Math.pow(cfg.jumpiness, dist - 1));
            a[j] = Math.max(0f, w[j]) * f;
        }
        setTier(cfg, weighted(a), "intensity change");
    }

    private static void setTier(LivingRainConfig cfg, int t, String why) {
        tier = t;
        float jitter = cfg.testMode ? 0f : (rnd.nextFloat() * 2f - 1f) * 0.07f * cfg.tierRandomness;
        tierBase = clamp(TIER_LEVEL[t] + jitter, 0.22f, 1f);
        float sec = Math.max(10, cfg.changeSeconds) * (0.5f + rnd.nextFloat());
        tierTimer = cfg.testMode ? Integer.MAX_VALUE / 2 : Math.round(sec * 20f);
        StormWind.rainTierChanged(t);
        if (cfg.logStorms) {
            LOG.info("[livingrain] {}: {} (level {}){}", why, TIER_NAMES[t], String.format("%.2f", tierBase),
                    thunderOn ? " [thunderstorm]" : "");
        }
    }

    /** Rare surprises: a squall (burst of heavier rain), a lull (rain nearly stops), or a sudden wind shift. */
    private static void updateEvents(LivingRainConfig cfg) {
        if (eventTicks > 0) {
            if (--eventTicks == 0) eventOffset = 0f;
            return;
        }
        if (cfg.testMode || cfg.surprise <= 0.001f || stormAge < 20 * 60) return;     // nothing in the first minute
        if (rnd.nextFloat() < cfg.surprise / (20f * 240f)) {
            float r = rnd.nextFloat();
            if (r < 0.40f) {
                eventOffset = 0.30f;
                eventTicks = 20 * (20 + rnd.nextInt(30));
                if (cfg.logStorms) LOG.info("[livingrain] event: squall");
                say(WeatherMessages.pick(WeatherMessages.SQUALL, rnd), false);
            } else if (r < 0.75f) {
                eventOffset = -0.30f;
                eventTicks = 20 * (20 + rnd.nextInt(25));
                if (cfg.logStorms) LOG.info("[livingrain] event: lull");
                say(WeatherMessages.pick(WeatherMessages.LULL, rnd), false);
            } else {
                StormWind.shift(rnd);
                if (cfg.logStorms) LOG.info("[livingrain] event: wind shift");
                if (cfg.windEnabled && cfg.windMessages) say(WeatherMessages.pick(WeatherMessages.WIND_SHIFT, rnd), false);
            }
        }
    }

    // ================= Messages =================

    private static void say(String text, boolean major) {
        if (text == null) return;
        WeatherMessages.show(mcRef, LivingRainConfig.get(), text, major, clock);
    }

    private static void announceTierChange(int from, int to) {
        if (from == to) return;
        say(WeatherMessages.pick(to > from ? WeatherMessages.UP_TO[to] : WeatherMessages.DOWN_TO[to], rnd), false);
    }

    private static void announceWindChange(int from, int to) {
        if (from == to) return;
        say(WeatherMessages.pick(to > from ? WeatherMessages.WIND_UP[to] : WeatherMessages.WIND_DOWN[to], rnd), false);
    }

    // ================= Self-check =================

    /** Proves once that the getRainLevel mixin really is in place, and writes the answer to the log. */
    private static void checkHook(ClientLevel level) {
        if (hookChecked) return;
        hookChecked = true;
        probe = 0.4321f;
        float v;
        try {
            v = level.getRainLevel(1.0f);
        } finally {
            probe = Float.NaN;
        }
        hookOk = Math.abs(v - 0.4321f) < 0.0001f;
        if (hookOk) {
            LOG.info("[livingrain] Rain-level hook is active: sky, clouds and shaders will follow the storm.");
        } else {
            LOG.error("[livingrain] Rain-level hook is NOT active. Sky and shader clouds will not follow the storm. "
                    + "Please send this log file to the mod author.");
        }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
