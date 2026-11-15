package com.example.livingrain;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** All settings. Saved to config/livingrain.json. Plain Java: no config-library classes in here. */
public class LivingRainConfig {

    private static final int CONFIG_VERSION = 2;

    /** null in files written by older versions of the mod. */
    public Integer configVersion;

    // ---- General ----
    public boolean enabled = true;
    /** Replace vanilla rain/snow with particles. */
    public boolean particleRain = true;
    /** Also keep vanilla's own rain visible. Off by default: the particle rain replaces it. */
    public boolean showVanillaRain = false;
    /** Let this mod set the rain level that the sky, vanilla clouds and shader packs read. */
    public boolean controlRainLevel = true;
    public boolean snow = true;

    // ---- Messages ----
    public boolean weatherMessages = true;
    /** 0 = action bar (above the hotbar), 1 = chat. */
    public int messageStyle = 0;
    public boolean windMessages = true;

    // ---- Rain storms: relative chance of each intensity ----
    public int rainDrizzle = 30;
    public int rainLight = 35;
    public int rainHeavy = 25;
    public int rainTorrential = 10;

    // ---- Thunderstorms: relative chance of each intensity ----
    public int thunderDrizzle = 0;
    public int thunderLight = 5;
    public int thunderHeavy = 40;
    public int thunderTorrential = 55;

    // ---- How storms change ----
    public int changeSeconds = 120;
    public float jumpiness = 0.35f;
    public float stickiness = 0.40f;
    public float tierRandomness = 0.50f;
    public float surprise = 0.25f;
    public int buildUpSeconds = 25;
    public int transitionSeconds = 20;
    public int taperSeconds = 40;

    // ---- Wind (simulated; vanilla Minecraft has no wind) ----
    public boolean windEnabled = true;
    public int windCalm = 25;
    public int windBreezy = 40;
    public int windWindy = 25;
    public int windGale = 10;
    public float windFollowsRain = 0.40f;
    public float windStrength = 1.0f;
    public boolean windRandomPerStorm = true;
    public int windDirection = 45;
    public float windDriftAmount = 0.25f;
    public float windTurbulence = 0.5f;
    public float gustFrequency = 0.5f;
    public float gustStrength = 1.5f;
    public int gustDuration = 3;
    public float rainWindInfluence = 1.5f;

    // ---- Roof Drips link ----
    public boolean overrideRoofDrips = true;

    // ---- Rain look ----
    public int radius = 22;
    public int spawnHeight = 14;
    public float density = 2.2f;
    public int maxParticles = 9000;
    /** 0 = crisp short streaks, 1 = long soft streaks. */
    public float motionBlur = 0.8f;
    public float opacity = 0.8f;
    public float dropSize = 1.0f;
    public float fallSpeed = 1.0f;
    public float fallSpeedVar = 0.20f;
    public boolean splashes = true;
    public boolean flipSlant = false;
    public float rainVolume = 1.0f;

    // ---- Debug ----
    public boolean logStorms = true;

    // ---- Weather test mode: deliberately NOT saved (transient), so it is always off after a restart ----
    public transient boolean testMode = false;
    /** 0 = clear, 1 = rain, 2 = thunderstorm. */
    public transient int testWeather = 1;
    /** 0..3 = drizzle, light, heavy, torrential. */
    public transient int testTier = 2;
    /** 0 = automatic, 1..4 = calm, breezy, windy, gale. */
    public transient int testWind = 0;

    // ---------------- Helpers ----------------
    public float[] rainWeights() {
        return new float[] { rainDrizzle, rainLight, rainHeavy, rainTorrential };
    }
    public float[] thunderWeights() {
        return new float[] { thunderDrizzle, thunderLight, thunderHeavy, thunderTorrential };
    }
    public float[] windWeights() {
        return new float[] { windCalm, windBreezy, windWindy, windGale };
    }

    // ---------------- Storm presets (only touch the storm + wind behaviour) ----------------
    public static final String[] PRESETS = { "Gentle", "Balanced", "Dramatic", "Chaotic" };

    public static void applyPreset(LivingRainConfig c, String name) {
        switch (name) {
            case "Gentle" -> {
                set4(c, 55, 35, 8, 2, 0, 20, 50, 30);
                c.changeSeconds = 180; c.jumpiness = 0.15f; c.stickiness = 0.6f;
                c.tierRandomness = 0.3f; c.surprise = 0.10f;
                c.windCalm = 45; c.windBreezy = 40; c.windWindy = 12; c.windGale = 3;
                c.windFollowsRain = 0.3f; c.gustFrequency = 0.3f; c.gustStrength = 1.0f;
            }
            case "Dramatic" -> {
                set4(c, 10, 25, 35, 30, 0, 0, 30, 70);
                c.changeSeconds = 90; c.jumpiness = 0.45f; c.stickiness = 0.3f;
                c.tierRandomness = 0.6f; c.surprise = 0.40f;
                c.windCalm = 10; c.windBreezy = 30; c.windWindy = 35; c.windGale = 25;
                c.windFollowsRain = 0.6f; c.gustFrequency = 0.7f; c.gustStrength = 2.0f;
            }
            case "Chaotic" -> {
                set4(c, 25, 25, 25, 25, 5, 15, 35, 45);
                c.changeSeconds = 45; c.jumpiness = 0.9f; c.stickiness = 0.1f;
                c.tierRandomness = 1.0f; c.surprise = 0.90f;
                c.windCalm = 20; c.windBreezy = 25; c.windWindy = 25; c.windGale = 30;
                c.windFollowsRain = 0.1f; c.gustFrequency = 0.9f; c.gustStrength = 2.5f;
            }
            default -> { // Balanced
                set4(c, 30, 35, 25, 10, 0, 5, 40, 55);
                c.changeSeconds = 120; c.jumpiness = 0.35f; c.stickiness = 0.4f;
                c.tierRandomness = 0.5f; c.surprise = 0.25f;
                c.windCalm = 25; c.windBreezy = 40; c.windWindy = 25; c.windGale = 10;
                c.windFollowsRain = 0.4f; c.gustFrequency = 0.5f; c.gustStrength = 1.5f;
            }
        }
    }

    private static void set4(LivingRainConfig c, int r0, int r1, int r2, int r3, int t0, int t1, int t2, int t3) {
        c.rainDrizzle = r0; c.rainLight = r1; c.rainHeavy = r2; c.rainTorrential = r3;
        c.thunderDrizzle = t0; c.thunderLight = t1; c.thunderHeavy = t2; c.thunderTorrential = t3;
    }

    // ---------------- Validation ----------------
    private static float cl(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int cl(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void sanitize() {
        rainDrizzle = cl(rainDrizzle, 0, 100); rainLight = cl(rainLight, 0, 100);
        rainHeavy = cl(rainHeavy, 0, 100); rainTorrential = cl(rainTorrential, 0, 100);
        thunderDrizzle = cl(thunderDrizzle, 0, 100); thunderLight = cl(thunderLight, 0, 100);
        thunderHeavy = cl(thunderHeavy, 0, 100); thunderTorrential = cl(thunderTorrential, 0, 100);
        changeSeconds = cl(changeSeconds, 20, 600);
        jumpiness = cl(jumpiness, 0f, 1f); stickiness = cl(stickiness, 0f, 1f);
        tierRandomness = cl(tierRandomness, 0f, 1f); surprise = cl(surprise, 0f, 1f);
        buildUpSeconds = cl(buildUpSeconds, 0, 120); transitionSeconds = cl(transitionSeconds, 0, 120);
        taperSeconds = cl(taperSeconds, 0, 300);
        windCalm = cl(windCalm, 0, 100); windBreezy = cl(windBreezy, 0, 100);
        windWindy = cl(windWindy, 0, 100); windGale = cl(windGale, 0, 100);
        windFollowsRain = cl(windFollowsRain, 0f, 1f); windStrength = cl(windStrength, 0f, 3f);
        windDirection = cl(windDirection, 0, 359); windDriftAmount = cl(windDriftAmount, 0f, 1f);
        windTurbulence = cl(windTurbulence, 0f, 1f);
        gustFrequency = cl(gustFrequency, 0f, 1f); gustStrength = cl(gustStrength, 0f, 3f);
        gustDuration = cl(gustDuration, 1, 10);
        rainWindInfluence = cl(rainWindInfluence, 0f, 5f);
        radius = cl(radius, 8, 30); spawnHeight = cl(spawnHeight, 6, 30);
        density = cl(density, 0.1f, 6f); maxParticles = cl(maxParticles, 200, 14000);
        motionBlur = cl(motionBlur, 0f, 2f); messageStyle = cl(messageStyle, 0, 1);
        opacity = cl(opacity, 0.1f, 1f); dropSize = cl(dropSize, 0.3f, 3f);
        fallSpeed = cl(fallSpeed, 0.3f, 2f); fallSpeedVar = cl(fallSpeedVar, 0f, 0.6f);
        rainVolume = cl(rainVolume, 0f, 1.5f);
    }

    // ---------------- Load / save ----------------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("livingrain.json");
    private static LivingRainConfig instance;

    public static LivingRainConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static LivingRainConfig load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    LivingRainConfig c = GSON.fromJson(r, LivingRainConfig.class);
                    if (c != null) {
                        if (c.configVersion == null || c.configVersion < CONFIG_VERSION) c = new LivingRainConfig();
                        c.configVersion = CONFIG_VERSION;
                        c.sanitize();
                        c.save();
                        return c;
                    }
                }
            }
        } catch (Exception ignored) { }
        LivingRainConfig c = new LivingRainConfig();
        c.configVersion = CONFIG_VERSION;
        c.save();
        return c;
    }

    public void save() {
        try {
            configVersion = CONFIG_VERSION;
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("[livingrain] Could not save config: " + e);
        }
    }
}
