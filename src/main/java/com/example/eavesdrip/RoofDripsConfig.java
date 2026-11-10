package com.example.eavesdrip;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** All settings. Saved to config/roofdrips.json. Plain Java: no config-library classes in here. */
public class RoofDripsConfig {

    public enum Preset { LIGHT, DEFAULT, EXTREME, CUSTOM }
    public enum StreakStyle { BLOCKY, SMOOTH }

    public static final int INFINITE_DEPTH = 20;   // slider value that means "no limit"
    private static final int CONFIG_VERSION = 5;

    /** null in files written by older versions of the mod. */
    public Integer configVersion;

    // ---- General ----
    public boolean enabled = true;
    public Preset preset = Preset.DEFAULT;
    /** Presets saved by the player. */
    public Map<String, PresetValues> userPresets = new LinkedHashMap<>();

    // ---- Preset-controlled settings (defaults = "Default" preset). 0 particle cap = Auto ----
    public int radius = 28;
    public int maxParticles = 0;
    public int scanSpeed = 150;
    public float density = 1.2f;
    public float streamShare = 0.45f;
    public float edgeVariation = 0.6f;
    public int streamLength = 5;
    public float streamLengthVar = 0.5f;
    public float fallSpeed = 1.25f;
    public float fallSpeedVar = 0.4f;
    public float dropSize = 3.0f;
    public boolean splashes = true;
    public float faceStreakAmount = 0.8f;
    public float faceStreakLength = 0.8f;
    public float faceStreakLengthVar = 0.4f;
    public float faceStreakSpeed = 1.0f;
    public float faceStreakSpeedVar = 0.5f;
    public float faceWander = 0.35f;
    public float faceWanderStrength = 1.0f;
    public float opacity = 0.9f;

    // ---- Not part of presets ----
    public StreakStyle streakStyle = StreakStyle.BLOCKY;
    public int overhangDepth = 3;
    public boolean allowAboveDecor = true;
    public int maxDropSeconds = 12;
    public int afterRainSeconds = 20;
    public boolean soundsEnabled = true;
    public float dripVolume = 0.5f;
    public float streamVolume = 0.5f;
    public int maxSoundsPerSecond = 6;
    public boolean treesEnabled = false;
    public float treeDensity = 1.0f;
    public int treeMaxParticles = 0;
    public int treeRadius = 20;

    // ---- Wind (simulated; vanilla Minecraft has no wind) ----
    public boolean windEnabled = false;
    public float windStrength = 1.0f;
    public int windDirection = 45;
    public float windDriftAmount = 0.25f;
    public boolean windRandomPerStorm = false;
    public boolean windStormBoost = true;
    public float windTurbulence = 0.3f;
    public float gustFrequency = 0.4f;
    public float gustStrength = 1.5f;
    public int gustDuration = 3;

    // ---- Cave drip ----
    public boolean caveEnabled = true;
    public float caveDensity = 1.5f;
    public float caveDropSize = 0.9f;
    public float caveFallSpeed = 1.0f;
    public int caveRadius = 20;
    public int caveMaxParticles = 0;
    public boolean caveWaterEnabled = true;
    public int caveWaterRange = 4;
    public boolean caveRainEnabled = true;
    public int caveRainRange = 3;
    public float caveRainChance = 0.5f;
    public boolean caveNaturalOnly = true;
    public float caveSoundVolume = 0.4f;

    // ---- Dripstone ----
    public boolean dripstoneEnabled = false;
    public boolean dripstoneRequireWater = false;
    public float dripstoneDensity = 1.0f;
    public float dripstoneDropSize = 1.0f;
    public float dripstoneFallSpeed = 1.0f;
    public boolean dripstoneStreams = false;
    public float dripstoneStreamFreq = 0.15f;

    // ---------------- Automatic particle caps ----------------
    public int effectiveRoofCap() {
        return maxParticles > 0 ? maxParticles : cl(Math.round(90f * density), 30, 800);
    }
    public int effectiveCaveCap() {
        return caveMaxParticles > 0 ? caveMaxParticles : cl(Math.round(12f * Math.max(caveDensity, dripstoneEnabled ? dripstoneDensity : 0f)), 4, 300);
    }
    public int effectiveTreeCap() {
        return treeMaxParticles > 0 ? treeMaxParticles : cl(Math.round(30f * treeDensity), 5, 300);
    }

    // ---------------- Presets ----------------
    public static void applyPreset(RoofDripsConfig c, Preset p) {
        switch (p) {
            case LIGHT -> {
                c.radius = 20; c.maxParticles = 0; c.scanSpeed = 80;
                c.density = 0.6f; c.streamShare = 0.25f; c.edgeVariation = 0.4f;
                c.streamLength = 3; c.streamLengthVar = 0.4f;
                c.fallSpeed = 1.0f; c.fallSpeedVar = 0.3f; c.dropSize = 3.0f; c.splashes = false;
                c.faceStreakAmount = 0.4f; c.faceStreakLength = 0.6f; c.faceStreakLengthVar = 0.3f;
                c.faceStreakSpeed = 1.0f; c.faceStreakSpeedVar = 0.4f; c.opacity = 0.8f;
                c.faceWander = 0.15f; c.faceWanderStrength = 0.8f;
            }
            case EXTREME -> {
                c.radius = 40; c.maxParticles = 0; c.scanSpeed = 260;
                c.density = 2.4f; c.streamShare = 0.65f; c.edgeVariation = 0.75f;
                c.streamLength = 8; c.streamLengthVar = 0.6f;
                c.fallSpeed = 1.5f; c.fallSpeedVar = 0.5f; c.dropSize = 3.9f; c.splashes = true;
                c.faceStreakAmount = 1.6f; c.faceStreakLength = 1.0f; c.faceStreakLengthVar = 0.5f;
                c.faceStreakSpeed = 1.2f; c.faceStreakSpeedVar = 0.6f; c.opacity = 1.0f;
                c.faceWander = 0.55f; c.faceWanderStrength = 1.3f;
            }
            default -> { // DEFAULT (and CUSTOM falls back to these values)
                c.radius = 28; c.maxParticles = 0; c.scanSpeed = 150;
                c.density = 1.2f; c.streamShare = 0.45f; c.edgeVariation = 0.6f;
                c.streamLength = 5; c.streamLengthVar = 0.5f;
                c.fallSpeed = 1.25f; c.fallSpeedVar = 0.4f; c.dropSize = 3.0f; c.splashes = true;
                c.faceStreakAmount = 0.8f; c.faceStreakLength = 0.8f; c.faceStreakLengthVar = 0.4f;
                c.faceStreakSpeed = 1.0f; c.faceStreakSpeedVar = 0.5f; c.opacity = 0.9f;
                c.faceWander = 0.35f; c.faceWanderStrength = 1.0f;
            }
        }
        if (p != Preset.CUSTOM) c.preset = p;
    }

    /** True if every preset-controlled value equals that built-in preset's value. */
    public boolean matches(Preset p) {
        RoofDripsConfig r = new RoofDripsConfig();
        applyPreset(r, p);
        return PresetValues.from(r).matches(this);
    }

    public Preset detectPreset() {
        for (Preset p : new Preset[] { Preset.LIGHT, Preset.DEFAULT, Preset.EXTREME }) {
            if (matches(p)) return p;
        }
        return Preset.CUSTOM;
    }

    /** The name shown in the Preset selector for the current values. */
    public String presetLabel() {
        switch (detectPreset()) {
            case LIGHT: return "Light";
            case DEFAULT: return "Default";
            case EXTREME: return "Extreme";
            default: break;
        }
        for (Map.Entry<String, PresetValues> e : userPresets.entrySet()) {
            if (e.getValue() != null && e.getValue().matches(this)) return e.getKey();
        }
        return "CUSTOM";
    }

    public static Preset builtinByLabel(String label) {
        if ("Light".equals(label)) return Preset.LIGHT;
        if ("Default".equals(label)) return Preset.DEFAULT;
        if ("Extreme".equals(label)) return Preset.EXTREME;
        return null;
    }

    private static float cl(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int cl(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void sanitize() {
        radius = cl(radius, 8, 64); maxParticles = cl(maxParticles, 0, 800); scanSpeed = cl(scanSpeed, 20, 500);
        density = cl(density, 0.1f, 8f); streamShare = cl(streamShare, 0f, 1f);
        edgeVariation = cl(edgeVariation, 0f, 1f);
        streamLength = cl(streamLength, 1, 10); streamLengthVar = cl(streamLengthVar, 0f, 1f);
        fallSpeed = cl(fallSpeed, 0.3f, 3f); fallSpeedVar = cl(fallSpeedVar, 0f, 1f);
        dropSize = cl(dropSize, 1f, 6f);
        faceStreakAmount = cl(faceStreakAmount, 0f, 4f); faceStreakLength = cl(faceStreakLength, 0.1f, 1f);
        faceStreakLengthVar = cl(faceStreakLengthVar, 0f, 1f); faceStreakSpeed = cl(faceStreakSpeed, 0.2f, 4f);
        faceStreakSpeedVar = cl(faceStreakSpeedVar, 0f, 1f); opacity = cl(opacity, 0.2f, 1f);
        faceWander = cl(faceWander, 0f, 1f); faceWanderStrength = cl(faceWanderStrength, 0.2f, 2f);
        overhangDepth = cl(overhangDepth, 1, INFINITE_DEPTH);
        maxDropSeconds = cl(maxDropSeconds, 2, 30);
        afterRainSeconds = cl(afterRainSeconds, 0, 120);
        dripVolume = cl(dripVolume, 0f, 3f); streamVolume = cl(streamVolume, 0f, 3f);
        maxSoundsPerSecond = cl(maxSoundsPerSecond, 1, 30);
        treeDensity = cl(treeDensity, 0.1f, 4f); treeMaxParticles = cl(treeMaxParticles, 0, 300);
        treeRadius = cl(treeRadius, 8, 48);
        windStrength = cl(windStrength, 0f, 3f); windDirection = cl(windDirection, 0, 359);
        windDriftAmount = cl(windDriftAmount, 0f, 1f); windTurbulence = cl(windTurbulence, 0f, 1f);
        gustFrequency = cl(gustFrequency, 0f, 1f); gustStrength = cl(gustStrength, 0f, 3f);
        gustDuration = cl(gustDuration, 1, 10);
        caveDensity = cl(caveDensity, 0.1f, 8f); caveDropSize = cl(caveDropSize, 0.5f, 5f);
        caveFallSpeed = cl(caveFallSpeed, 0.3f, 3f); caveRadius = cl(caveRadius, 8, 60);
        caveMaxParticles = cl(caveMaxParticles, 0, 300); caveWaterRange = cl(caveWaterRange, 1, 12);
        caveRainRange = cl(caveRainRange, 1, 12); caveRainChance = cl(caveRainChance, 0f, 1f);
        caveSoundVolume = cl(caveSoundVolume, 0f, 3f);
        dripstoneDensity = cl(dripstoneDensity, 0.1f, 8f); dripstoneDropSize = cl(dripstoneDropSize, 0.5f, 5f);
        dripstoneFallSpeed = cl(dripstoneFallSpeed, 0.3f, 3f); dripstoneStreamFreq = cl(dripstoneStreamFreq, 0f, 1f);
        if (streakStyle == null) streakStyle = StreakStyle.BLOCKY;
        if (userPresets == null) userPresets = new LinkedHashMap<>();
        userPresets.values().removeIf(v -> v == null);
        if (preset == null) preset = Preset.DEFAULT;
        preset = detectPreset();
    }

    // ---------------- Load / save ----------------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("roofdrips.json");
    private static RoofDripsConfig instance;

    public static RoofDripsConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static RoofDripsConfig load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    RoofDripsConfig c = GSON.fromJson(r, RoofDripsConfig.class);
                    if (c != null) {
                        if (c.configVersion == null || c.configVersion < CONFIG_VERSION) {
                            // Refresh built-in presets to their new values, and move old fixed caps / defaults forward.
                            if (c.preset != null && c.preset != Preset.CUSTOM) applyPreset(c, c.preset);
                            if (c.treeMaxParticles == 30) c.treeMaxParticles = 0;
                            if (c.caveMaxParticles == 12) c.caveMaxParticles = 0;
                            if (Math.abs(c.caveDensity - 1.0f) < 0.001f) c.caveDensity = 1.5f;
                            if (Math.abs(c.caveRainChance - 0.25f) < 0.001f) c.caveRainChance = 0.5f;
                        }
                        c.configVersion = CONFIG_VERSION;
                        c.sanitize();
                        c.save();
                        return c;
                    }
                }
            }
        } catch (Exception ignored) { }
        RoofDripsConfig c = new RoofDripsConfig();
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
            System.err.println("[roofdrips] Could not save config: " + e);
        }
    }
}
