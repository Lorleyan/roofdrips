package com.example.eavesdrip;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** All settings. Saved to config/roofdrips.json. Plain Java: no config-library classes in here. */
public class RoofDripsConfig {

    public enum Preset { LIGHT, DEFAULT, EXTREME, CUSTOM }

    private static final int CONFIG_VERSION = 2;

    /** null in files written by older versions of the mod. */
    public Integer configVersion;

    // ---- General ----
    public boolean enabled = true;
    public Preset preset = Preset.DEFAULT;

    // ---- Preset-controlled settings (defaults = "Default" preset) ----
    public int radius = 28;
    public int maxParticles = 110;
    public int scanSpeed = 150;
    public float density = 1.2f;
    public float streamShare = 0.45f;       // average "streaminess" of an edge
    public float edgeVariation = 0.6f;      // how different edges are from each other
    public int streamLength = 5;
    public float streamLengthVar = 0.5f;
    public float fallSpeed = 1.25f;
    public float fallSpeedVar = 0.4f;
    public float dropSize = 1.0f;
    public boolean splashes = true;
    public float faceStreakAmount = 0.8f;
    public float faceStreakLength = 0.8f;
    public float faceStreakLengthVar = 0.4f;
    public float faceStreakSpeed = 1.0f;
    public float faceStreakSpeedVar = 0.5f;
    public float opacity = 0.9f;

    // ---- Not part of presets ----
    public int overhangDepth = 3;
    public int afterRainSeconds = 20;
    public boolean soundsEnabled = true;
    public float dripVolume = 0.5f;
    public float streamVolume = 0.5f;
    public int maxSoundsPerSecond = 6;
    public boolean treesEnabled = false;
    public float treeDensity = 1.0f;
    public int treeMaxParticles = 30;
    public int treeRadius = 20;

    // ---------------- Presets ----------------
    public static void applyPreset(RoofDripsConfig c, Preset p) {
        switch (p) {
            case LIGHT -> {
                c.radius = 20; c.maxParticles = 40; c.scanSpeed = 80;
                c.density = 0.6f; c.streamShare = 0.25f; c.edgeVariation = 0.4f;
                c.streamLength = 3; c.streamLengthVar = 0.4f;
                c.fallSpeed = 1.0f; c.fallSpeedVar = 0.3f; c.dropSize = 1.0f; c.splashes = false;
                c.faceStreakAmount = 0.4f; c.faceStreakLength = 0.6f; c.faceStreakLengthVar = 0.3f;
                c.faceStreakSpeed = 1.0f; c.faceStreakSpeedVar = 0.4f; c.opacity = 0.8f;
            }
            case EXTREME -> {
                c.radius = 40; c.maxParticles = 260; c.scanSpeed = 260;
                c.density = 2.4f; c.streamShare = 0.65f; c.edgeVariation = 0.75f;
                c.streamLength = 8; c.streamLengthVar = 0.6f;
                c.fallSpeed = 1.5f; c.fallSpeedVar = 0.5f; c.dropSize = 1.3f; c.splashes = true;
                c.faceStreakAmount = 1.6f; c.faceStreakLength = 1.0f; c.faceStreakLengthVar = 0.5f;
                c.faceStreakSpeed = 1.2f; c.faceStreakSpeedVar = 0.6f; c.opacity = 1.0f;
            }
            default -> { // DEFAULT (and CUSTOM falls back to these values)
                c.radius = 28; c.maxParticles = 110; c.scanSpeed = 150;
                c.density = 1.2f; c.streamShare = 0.45f; c.edgeVariation = 0.6f;
                c.streamLength = 5; c.streamLengthVar = 0.5f;
                c.fallSpeed = 1.25f; c.fallSpeedVar = 0.4f; c.dropSize = 1.0f; c.splashes = true;
                c.faceStreakAmount = 0.8f; c.faceStreakLength = 0.8f; c.faceStreakLengthVar = 0.4f;
                c.faceStreakSpeed = 1.0f; c.faceStreakSpeedVar = 0.5f; c.opacity = 0.9f;
            }
        }
        if (p != Preset.CUSTOM) c.preset = p;
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < 0.001f; }

    /** True if every preset-controlled value equals that preset's value. */
    public boolean matches(Preset p) {
        RoofDripsConfig r = new RoofDripsConfig();
        applyPreset(r, p);
        return radius == r.radius && maxParticles == r.maxParticles && scanSpeed == r.scanSpeed
                && near(density, r.density) && near(streamShare, r.streamShare)
                && near(edgeVariation, r.edgeVariation)
                && streamLength == r.streamLength && near(streamLengthVar, r.streamLengthVar)
                && near(fallSpeed, r.fallSpeed) && near(fallSpeedVar, r.fallSpeedVar)
                && near(dropSize, r.dropSize) && splashes == r.splashes
                && near(faceStreakAmount, r.faceStreakAmount) && near(faceStreakLength, r.faceStreakLength)
                && near(faceStreakLengthVar, r.faceStreakLengthVar) && near(faceStreakSpeed, r.faceStreakSpeed)
                && near(faceStreakSpeedVar, r.faceStreakSpeedVar) && near(opacity, r.opacity);
    }

    public Preset detectPreset() {
        for (Preset p : new Preset[] { Preset.LIGHT, Preset.DEFAULT, Preset.EXTREME }) {
            if (matches(p)) return p;
        }
        return Preset.CUSTOM;
    }

    private static float cl(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int cl(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void sanitize() {
        radius = cl(radius, 8, 64); maxParticles = cl(maxParticles, 10, 600); scanSpeed = cl(scanSpeed, 20, 500);
        density = cl(density, 0.1f, 4f); streamShare = cl(streamShare, 0f, 1f);
        edgeVariation = cl(edgeVariation, 0f, 1f);
        streamLength = cl(streamLength, 1, 10); streamLengthVar = cl(streamLengthVar, 0f, 1f);
        fallSpeed = cl(fallSpeed, 0.3f, 3f); fallSpeedVar = cl(fallSpeedVar, 0f, 1f);
        dropSize = cl(dropSize, 0.5f, 5f);
        faceStreakAmount = cl(faceStreakAmount, 0f, 4f); faceStreakLength = cl(faceStreakLength, 0.1f, 1f);
        faceStreakLengthVar = cl(faceStreakLengthVar, 0f, 1f); faceStreakSpeed = cl(faceStreakSpeed, 0.2f, 4f);
        faceStreakSpeedVar = cl(faceStreakSpeedVar, 0f, 1f); opacity = cl(opacity, 0.2f, 1f);
        overhangDepth = cl(overhangDepth, 1, 8);
        afterRainSeconds = cl(afterRainSeconds, 0, 120);
        dripVolume = cl(dripVolume, 0f, 3f); streamVolume = cl(streamVolume, 0f, 3f);
        maxSoundsPerSecond = cl(maxSoundsPerSecond, 1, 30);
        treeDensity = cl(treeDensity, 0.1f, 4f); treeMaxParticles = cl(treeMaxParticles, 5, 200);
        treeRadius = cl(treeRadius, 8, 48);
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
                        // Older config (1.0/1.1): if a preset was selected, refresh it to the new preset values.
                        if (c.configVersion == null || c.configVersion < CONFIG_VERSION) {
                            if (c.preset != null && c.preset != Preset.CUSTOM) applyPreset(c, c.preset);
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
