package com.example.livingrain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import dev.isxander.yacl3.api.Binding;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The in-game settings screen (opened from Mod Menu's Configure button). Built with YACL. */
public final class LivingRainConfigScreen {

    private static LivingRainConfig CFG;
    private static LivingRainConfig DEF;
    private static Screen PARENT;

    private LivingRainConfigScreen() { }

    // ---------------- Option helpers ----------------
    private static Option<Boolean> bool(String name, String tip,
                                        Function<LivingRainConfig, Boolean> get, BiConsumer<LivingRainConfig, Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Float> flt(String name, String tip, float min, float max, float step, boolean pct,
                                     Function<LivingRainConfig, Float> get, BiConsumer<LivingRainConfig, Float> set) {
        return flt(name, tip, min, max, step, pct, null, get, set);
    }

    /** warnAbove: values above it are shown in red, to flag a heavier performance cost than the default. */
    private static Option<Float> flt(String name, String tip, float min, float max, float step, boolean pct, Float warnAbove,
                                     Function<LivingRainConfig, Float> get, BiConsumer<LivingRainConfig, Float> set) {
        return Option.<Float>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step)
                        .formatValue(v -> {
                            MutableComponent c = Component.literal(pct ? Math.round(v * 100f) + "%" : String.format("%.2f", v));
                            if (warnAbove != null && v > warnAbove) c.withStyle(ChatFormatting.RED);
                            return c;
                        }))
                .build();
    }

    private static Option<Integer> numFmt(String name, String tip, int min, int max, int step,
                                          Function<Integer, String> fmt,
                                          Function<LivingRainConfig, Integer> get, BiConsumer<LivingRainConfig, Integer> set) {
        return numFmt(name, tip, min, max, step, fmt, null, get, set);
    }

    /** warnAbove: values above it are shown in red, to flag a heavier performance cost than the default. */
    private static Option<Integer> numFmt(String name, String tip, int min, int max, int step,
                                          Function<Integer, String> fmt, Integer warnAbove,
                                          Function<LivingRainConfig, Integer> get, BiConsumer<LivingRainConfig, Integer> set) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step)
                        .formatValue(v -> {
                            MutableComponent c = Component.literal(fmt.apply(v));
                            if (warnAbove != null && v > warnAbove) c.withStyle(ChatFormatting.RED);
                            return c;
                        }))
                .build();
    }

    private static Option<Integer> num(String name, String tip, int min, int max, int step, String unit,
                                       Function<LivingRainConfig, Integer> get, BiConsumer<LivingRainConfig, Integer> set) {
        return numFmt(name, tip, min, max, step, v -> v + unit, get, set);
    }

    private static Option<Integer> numWarn(String name, String tip, int min, int max, int step, String unit, int warnAbove,
                                           Function<LivingRainConfig, Integer> get, BiConsumer<LivingRainConfig, Integer> set) {
        return numFmt(name, tip, min, max, step, v -> v + unit, warnAbove, get, set);
    }

    /** A relative chance 0..100. The four chances of a group are compared with each other, so they need not add up to 100. */
    private static Option<Integer> chance(String name, String tip,
                                          Function<LivingRainConfig, Integer> get, BiConsumer<LivingRainConfig, Integer> set) {
        return numFmt(name, tip, 0, 100, 5, v -> v == 0 ? "never" : String.valueOf(v), get, set);
    }

    private static String compass(int deg) {
        String[] names = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
        return names[((int) Math.round(deg / 45.0)) % 8];
    }

    private static ButtonOption presetButton(String name, String tip) {
        return ButtonOption.createBuilder()
                .name(Component.literal("Preset: " + name))
                .description(OptionDescription.of(Component.literal(tip
                        + " Changes the storm and wind settings only (not the look of the rain). "
                        + "Any unsaved changes on this screen are discarded.")))
                .action((scr, btn) -> {
                    LivingRainConfig.applyPreset(CFG, name);
                    CFG.save();
                    Minecraft.getInstance().gui.setScreen(create(PARENT));
                })
                .build();
    }

    private static ButtonOption testButton(String name, String tip, int weather, int tier) {
        return ButtonOption.createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip
                        + " Starts test mode and returns to the game right away. Unsaved changes on this screen are discarded.")))
                .action((scr, btn) -> {
                    CFG.testMode = true;
                    CFG.testWeather = weather;
                    if (tier >= 0) CFG.testTier = tier;
                    Minecraft.getInstance().gui.setScreen((Screen) null);
                })
                .build();
    }

    private static final String CHANCE_NOTE = " These four numbers are compared with each other, so they do not need to add up to 100.";

    // ---------------- Screen ----------------
    public static Screen create(Screen parent) {
        PARENT = parent;
        CFG = LivingRainConfig.get();
        DEF = new LivingRainConfig();

        // ===== Storms =====
        List<Option<?>> general = new ArrayList<>();
        general.add(bool("Enable Immersive Rain",
                "Master switch. Off = everything goes back to vanilla Minecraft rain.",
                c -> c.enabled, (c, v) -> c.enabled = v));
        general.add(bool("Control sky & shader rain level",
                "Lets this mod set the rain level that the sky, vanilla clouds and shader packs read. "
              + "With this on, a drizzle gives a lighter sky and lighter shader clouds than a downpour (in shader packs that "
              + "scale their clouds with rain). Immersive Drips follows it too. Only affects your own screen, never the server.",
                c -> c.controlRainLevel, (c, v) -> c.controlRainLevel = v));

        List<Option<?>> messages = new ArrayList<>();
        messages.add(bool("Weather messages",
                "Shows a short, atmospheric line whenever the weather changes, such as \"The rain eases up.\" or "
              + "\"A thunderstorm rolls in.\"",
                c -> c.weatherMessages, (c, v) -> c.weatherMessages = v));
        messages.add(numFmt("Message style",
                "Where the messages appear: briefly above your hotbar, or in the chat window.",
                0, 1, 1, v -> v == 0 ? "Above hotbar" : "Chat", c -> c.messageStyle, (c, v) -> c.messageStyle = v));
        messages.add(bool("Wind messages",
                "Also announce notable changes in the wind (\"The wind rises, driving the rain sideways.\").",
                c -> c.windMessages, (c, v) -> c.windMessages = v));

        List<Option<?>> presets = new ArrayList<>();
        presets.add(presetButton("Gentle", "Mostly drizzle and light rain, calm winds, few surprises."));
        presets.add(presetButton("Balanced", "The default mix."));
        presets.add(presetButton("Dramatic", "Heavier storms and stronger winds."));
        presets.add(presetButton("Chaotic", "Anything can happen at any time."));

        List<Option<?>> rainChances = new ArrayList<>();
        rainChances.add(chance("Drizzle", "How likely ordinary rain storms are to be a fine, light drizzle." + CHANCE_NOTE,
                c -> c.rainDrizzle, (c, v) -> c.rainDrizzle = v));
        rainChances.add(chance("Light rain", "How likely a steady light rain is." + CHANCE_NOTE,
                c -> c.rainLight, (c, v) -> c.rainLight = v));
        rainChances.add(chance("Heavy rain", "How likely heavy rainfall is." + CHANCE_NOTE,
                c -> c.rainHeavy, (c, v) -> c.rainHeavy = v));
        rainChances.add(chance("Torrential", "How likely a storm-like downpour is." + CHANCE_NOTE,
                c -> c.rainTorrential, (c, v) -> c.rainTorrential = v));

        List<Option<?>> thunderChances = new ArrayList<>();
        thunderChances.add(chance("Drizzle", "Chance of drizzle while it is thundering." + CHANCE_NOTE,
                c -> c.thunderDrizzle, (c, v) -> c.thunderDrizzle = v));
        thunderChances.add(chance("Light rain", "Chance of light rain while it is thundering." + CHANCE_NOTE,
                c -> c.thunderLight, (c, v) -> c.thunderLight = v));
        thunderChances.add(chance("Heavy rain", "Chance of heavy rain while it is thundering." + CHANCE_NOTE,
                c -> c.thunderHeavy, (c, v) -> c.thunderHeavy = v));
        thunderChances.add(chance("Torrential", "Chance of a torrential downpour while it is thundering." + CHANCE_NOTE,
                c -> c.thunderTorrential, (c, v) -> c.thunderTorrential = v));

        List<Option<?>> change = new ArrayList<>();
        change.add(num("Time between changes",
                "On average, how long a storm stays at one intensity before it may change. Each time is randomised by about half either way.",
                20, 600, 10, " s", c -> c.changeSeconds, (c, v) -> c.changeSeconds = v));
        change.add(flt("Jumpiness",
                "How willing the storm is to skip a step, for example from drizzle straight to torrential. 0% = it always moves one step at a time.",
                0f, 1f, 0.05f, true, c -> c.jumpiness, (c, v) -> c.jumpiness = v));
        change.add(flt("Stickiness",
                "How likely a storm is to stay at its current intensity when it is time to change. 100% = changes are rare.",
                0f, 1f, 0.05f, true, c -> c.stickiness, (c, v) -> c.stickiness = v));
        change.add(flt("Randomness within a level",
                "How much the exact strength varies inside each level, and how much it slowly rises and falls on its own.",
                0f, 1f, 0.05f, true, c -> c.tierRandomness, (c, v) -> c.tierRandomness = v));
        change.add(flt("Surprise factor",
                "Occasionally randomises things: each storm's density, speed and size, its starting level and its wind, "
              + "and during a storm it can throw in a squall (a burst of heavier rain), a lull (the rain nearly stops) "
              + "or a sudden wind shift. 0% = no surprises.",
                0f, 1f, 0.05f, true, c -> c.surprise, (c, v) -> c.surprise = v));
        change.add(num("Build-up time",
                "How slowly a storm fades in when rain starts. Vanilla does it in about 5 seconds.",
                0, 120, 5, " s", c -> c.buildUpSeconds, (c, v) -> c.buildUpSeconds = v));
        change.add(num("Change smoothness",
                "How smoothly the rain eases from one intensity to the next. Higher = slower, gentler changes.",
                0, 120, 5, " s", c -> c.transitionSeconds, (c, v) -> c.transitionSeconds = v));
        change.add(num("Taper-off time",
                "How long the rain takes to die away after the server ends the storm. Vanilla stops in about 5 seconds.",
                0, 300, 5, " s", c -> c.taperSeconds, (c, v) -> c.taperSeconds = v));

        // ===== Rain look =====
        List<Option<?>> look = new ArrayList<>();
        look.add(bool("Particle rain and snow",
                "Replaces vanilla rain and snow with particles. Turn off to see vanilla's rain again (the storm levels still apply). "
              + "Tip: keep Minecraft's Particles setting on 'All', otherwise some drops are skipped.",
                c -> c.particleRain, (c, v) -> c.particleRain = v));
        look.add(bool("Show vanilla rain too",
                "Off (default): vanilla Minecraft's own rain and snow are hidden, and only this mod's particles show. "
              + "On: both are shown together. Only matters while 'Particle rain and snow' is on.",
                c -> c.showVanillaRain, (c, v) -> c.showVanillaRain = v));
        look.add(bool("Snow in cold biomes",
                "Particle snow where vanilla would snow. Off = no snow at all while this mod is replacing vanilla weather.",
                c -> c.snow, (c, v) -> c.snow = v));
        look.add(numWarn("Radius",
                "How far around you rain is created (blocks). Bigger looks more like a real storm but costs more performance. "
              + "Shown in red above 26, where the cost climbs quickly.",
                8, 30, 1, " blocks", 26, c -> c.radius, (c, v) -> c.radius = v));
        look.add(numWarn("Rain height",
                "How far above your head rain is created. Rain fades out near this height. "
              + "Shown in red above 20 (more rain in the air = more cost).",
                6, 30, 1, " blocks", 20, c -> c.spawnHeight, (c, v) -> c.spawnHeight = v));
        look.add(flt("Density",
                "How many drops there are overall. The storm intensity scales this up and down automatically, so a drizzle "
              + "always has far fewer drops than a torrential downpour. The default is already heavy. "
              + "RED = above the default, which costs noticeably more performance. If you raise it, raise the Particle limit too.",
                0.1f, 20f, 0.1f, false, 6.0f, c -> c.density, (c, v) -> c.density = v));
        look.add(numFmt("Particle limit",
                "The most rain particles alive at once. This is the main performance control: lower it if the game stutters "
              + "in storms. Heavy rain at the default density needs roughly 10,000 to 16,000. RED = above 25,000, which most "
              + "systems will struggle with. Minecraft may discard particles beyond its own internal limit (around 16,000); the "
              + "mod detects this automatically and /BRain status shows it.",
                200, 60000, 100, v -> String.valueOf(v), 25000, c -> c.maxParticles, (c, v) -> c.maxParticles = v));
        look.add(flt("Motion blur",
                "Stretches each drop into a soft streak, longer the faster it moves (gusts stretch drops further), so the rain looks "
              + "smooth instead of blocky. 0% = short crisp streaks.",
                0f, 2f, 0.05f, true, c -> c.motionBlur, (c, v) -> c.motionBlur = v));
        look.add(flt("Rain brightness",
                "How bright the rain's colour is. Lower values make the rain sit more quietly in the scene, which can help it blend "
              + "with a shader pack's lighting (shader packs often add their own bloom and glare on bright particles). "
              + "Each drop also varies slightly in brightness.",
                0.3f, 1f, 0.05f, true, c -> c.rainBrightness, (c, v) -> c.rainBrightness = v));
        look.add(flt("Opacity", "How visible the rain is.",
                0.1f, 1f, 0.05f, true, c -> c.opacity, (c, v) -> c.opacity = v));
        look.add(flt("Drop size: drizzle",
                "Size of the drops in the lightest rain. Drop size is blended automatically between this and the thunderstorm "
              + "size below, so light rain gets bigger drops and heavy rain gets slightly smaller ones.",
                0.3f, 2f, 0.05f, false, c -> c.dropSizeDrizzle, (c, v) -> c.dropSizeDrizzle = v));
        look.add(flt("Drop size: thunderstorm",
                "Size of the drops during a thunderstorm and in torrential rain (the smallest). Light and heavy rain are "
              + "blended evenly between the drizzle size and this one.",
                0.3f, 2f, 0.05f, false, c -> c.dropSizeThunder, (c, v) -> c.dropSizeThunder = v));
        look.add(flt("Snowflake size", "Size of snowflakes.",
                0.3f, 2f, 0.05f, false, c -> c.dropSize, (c, v) -> c.dropSize = v));
        look.add(flt("Fall speed",
                "Base fall speed. Heavier rain automatically falls faster on top of this: at any setting, torrential rain "
              + "falls about three times as fast as a drizzle. Wind slant keeps the same angle at any speed.",
                0.3f, 6f, 0.1f, false, c -> c.fallSpeed, (c, v) -> c.fallSpeed = v));
        look.add(flt("Fall speed variance", "How much individual drops differ in speed.",
                0f, 0.6f, 0.05f, true, c -> c.fallSpeedVar, (c, v) -> c.fallSpeedVar = v));
        look.add(bool("Ground splashes", "Tiny splashes where drops land near you.",
                c -> c.splashes, (c, v) -> c.splashes = v));
        look.add(flt("Splash amount",
                "How many ground splashes there are. They also scale with the weather automatically: few in a drizzle, many in a "
              + "downpour, the most in a thunderstorm. RED = above 2.0, which adds a lot of extra particles.",
                0f, 4f, 0.1f, false, 2.0f, c -> c.splashAmount, (c, v) -> c.splashAmount = v));
        look.add(bool("See rain through windows (experimental)",
                "Shader packs draw glass in a way that can hide ordinary rain behind windows. Turning this on draws the rain in a "
              + "different way that stays visible through glass, but the streaks become thin, hard-edged lines (no soft blur, "
              + "and Opacity makes them thinner rather than fainter). Only worth trying if you use a shader pack and see this problem.",
                c -> c.seeThroughWindows, (c, v) -> c.seeThroughWindows = v));
        look.add(flt("Rain volume",
                "Volume of the rain sound. Also follows Minecraft's Weather volume slider. Gets louder with heavier rain.",
                0f, 1.5f, 0.05f, true, c -> c.rainVolume, (c, v) -> c.rainVolume = v));
        look.add(bool("Flip streak tilt",
                "Only if rain streaks look tilted the wrong way compared with the direction they travel, turn this on.",
                c -> c.flipSlant, (c, v) -> c.flipSlant = v));

        // ===== Heavy-rain effects =====
        List<Option<?>> heavy = new ArrayList<>();
        heavy.add(LabelOption.createBuilder().line(Component.literal(
                "These effects fade in as rain gets heavier. Drizzle and light rain are plain particles; the full effect is "
              + "reached in heavy rain, torrential rain and thunderstorms.")).build());
        heavy.add(bool("Rain sheets",
                "Heavy rain gets many extra thin streaks drawn as big flat pictures, each holding dozens of drops. This is what gives "
              + "a downpour its dense, thin-streak look without needing millions of particles. Fewer individual drops are made "
              + "while sheets are on. Not used with 'See rain through windows'.",
                c -> c.sheets, (c, v) -> c.sheets = v));
        heavy.add(flt("Sheet amount",
                "How many sheets there are. RED = above 150%: sheets are big, so many of them cost graphics performance.",
                0f, 2f, 0.05f, true, 1.5f, c -> c.sheetAmount, (c, v) -> c.sheetAmount = v));
        heavy.add(flt("Sheet size", "How big each sheet is. Bigger sheets cover more of the view but show their flat shape more.",
                0.6f, 1.6f, 0.05f, true, c -> c.sheetSize, (c, v) -> c.sheetSize = v));
        heavy.add(bool("Lightning flash",
                "The rain lights up for an instant when lightning strikes. Use /BRain flash to test it.",
                c -> c.lightningFlash, (c, v) -> c.lightningFlash = v));
        heavy.add(flt("Flash strength", "How bright the flash is.",
                0f, 2f, 0.05f, true, c -> c.flashStrength, (c, v) -> c.flashStrength = v));
        heavy.add(bool("Rain curtains",
                "Patches of thicker and thinner rain drift along the wind, so you see walls of rain sweeping past instead of an "
              + "even sprinkle. The overall amount of rain stays what Density says.",
                c -> c.curtains, (c, v) -> c.curtains = v));
        heavy.add(flt("Curtain strength", "How different the thick and thin patches are. 0% = even rain.",
                0f, 2f, 0.05f, true, c -> c.curtainStrength, (c, v) -> c.curtainStrength = v));
        heavy.add(bool("Glinting drops",
                "Some drops get a brighter segment, like a streak catching the light, and sparkle slightly as they fall.",
                c -> c.glints, (c, v) -> c.glints = v));
        heavy.add(flt("Glinting share", "How many of the drops glint (at full effect).",
                0f, 1f, 0.05f, true, c -> c.glintAmount, (c, v) -> c.glintAmount = v));
        heavy.add(flt("Backlight glow",
                "Rain glows when it is between you and the sun or the moon, like rain lit from behind. 0% = off. "
              + "If it never seems to work, the log says whether the game's time of day could be read.",
                0f, 2f, 0.05f, true, c -> c.backlight, (c, v) -> c.backlight = v));

        // ===== Wind =====
        List<Option<?>> wind = new ArrayList<>();
        wind.add(bool("Enable wind",
                "Blows rain and snow sideways. Minecraft has no wind of its own, so this is a simulation.",
                c -> c.windEnabled, (c, v) -> c.windEnabled = v));
        wind.add(flt("Wind strength", "Overall wind strength. Multiplies the personalities below.",
                0f, 3f, 0.05f, false, c -> c.windStrength, (c, v) -> c.windStrength = v));
        wind.add(flt("Rain slant",
                "How strongly the wind slants rain. Immersive Drips' water is pushed by the wind itself; rain falls much faster, "
              + "so it needs a boost to lean visibly.",
                0f, 5f, 0.1f, false, c -> c.rainWindInfluence, (c, v) -> c.rainWindInfluence = v));
        List<Option<?>> windChances = new ArrayList<>();
        windChances.add(chance("Calm", "Chance a storm has almost no wind." + CHANCE_NOTE,
                c -> c.windCalm, (c, v) -> c.windCalm = v));
        windChances.add(chance("Breezy", "Chance of a light breeze." + CHANCE_NOTE,
                c -> c.windBreezy, (c, v) -> c.windBreezy = v));
        windChances.add(chance("Windy", "Chance of a strong wind." + CHANCE_NOTE,
                c -> c.windWindy, (c, v) -> c.windWindy = v));
        windChances.add(chance("Gale", "Chance of a howling gale." + CHANCE_NOTE,
                c -> c.windGale, (c, v) -> c.windGale = v));
        wind.add(flt("Wind follows rain intensity",
                "0% = wind is rolled completely independently of the rain (light rain in a gale is as likely as anything). "
              + "100% = heavy rain usually brings strong wind.",
                0f, 1f, 0.05f, true, c -> c.windFollowsRain, (c, v) -> c.windFollowsRain = v));
        wind.add(bool("New direction each storm",
                "Picks a random wind direction whenever rain starts, instead of using the direction below.",
                c -> c.windRandomPerStorm, (c, v) -> c.windRandomPerStorm = v));
        wind.add(numFmt("Wind direction",
                "The compass direction the wind blows TOWARD (0 = north, 90 = east, 180 = south, 270 = west). "
              + "Only used when 'New direction each storm' is off.",
                0, 355, 5, v -> v + "\u00B0 " + compass(v), c -> c.windDirection, (c, v) -> c.windDirection = v));
        wind.add(flt("Direction drift",
                "How much the wind direction slowly wanders. 0% = fixed direction.",
                0f, 1f, 0.05f, true, c -> c.windDriftAmount, (c, v) -> c.windDriftAmount = v));
        wind.add(flt("Turbulence", "Random jitter so drops don't all travel in perfectly parallel lines.",
                0f, 1f, 0.05f, true, c -> c.windTurbulence, (c, v) -> c.windTurbulence = v));
        wind.add(flt("Gust frequency",
                "How often gusts happen. 0% = no gusts. 100% = roughly one every few seconds.",
                0f, 1f, 0.05f, true, c -> c.gustFrequency, (c, v) -> c.gustFrequency = v));
        wind.add(flt("Gust strength", "How much extra push a gust gives, on top of the normal wind.",
                0f, 3f, 0.05f, false, c -> c.gustStrength, (c, v) -> c.gustStrength = v));
        wind.add(num("Gust duration", "How long a typical gust lasts. Gusts swell and fade smoothly.",
                1, 10, 1, " s", c -> c.gustDuration, (c, v) -> c.gustDuration = v));

        // ===== Immersive Drips =====
        List<Option<?>> link = new ArrayList<>();
        boolean rd = RoofDripsLink.present();
        if (rd) {
            link.add(LabelOption.createBuilder().line(Component.literal(
                    "Immersive Drips is installed. With the box below ticked, THIS mod's wind drives Immersive Drips' droplets too, "
                  + "so rain and drips lean the same way. Immersive Drips' own wind settings are then ignored.")).build());
            if (!RoofDripsLink.supportsOverride()) {
                link.add(LabelOption.createBuilder().line(Component.literal(
                        "Your Immersive Drips is too old to accept this (it needs version 1.1.0 or newer). Until you update it, "
                      + "it keeps using its own wind.")).build());
            }
            link.add(bool("Override Immersive Drips settings",
                    "On: this mod's wind controls both. Off: Immersive Drips keeps its own wind, and the rain here follows "
                  + "Immersive Drips' wind instead.",
                    c -> c.overrideRoofDrips, (c, v) -> c.overrideRoofDrips = v));
            if (!RoofDripsLink.supportsRainScaling()) {
                link.add(LabelOption.createBuilder().line(Component.literal(
                        "The drip-amount settings below need Immersive Drips 1.1.0 or newer. Until you update it, "
                      + "Immersive Drips uses its own drip amounts.")).build());
            }
            link.add(bool("Scale Immersive Drips with rain severity",
                    "On: Immersive Drips makes fewer drips in a drizzle and more in a downpour, following this mod's storm. Immersive Drips' own "
                  + "density settings become the amounts for a full downpour. Off: Immersive Drips ignores the storm's severity and "
                  + "behaves exactly as it does without this mod (full drips whenever it rains).",
                    c -> c.dripScaling, (c, v) -> c.dripScaling = v));
            link.add(flt("Drip amount: drizzle",
                    "How many drips there are in the lightest rain, as a share of Immersive Drips' own density setting. Light and heavy "
                  + "rain are blended evenly between this and the torrential amount.",
                    0f, 2f, 0.05f, true, c -> c.dripDrizzle, (c, v) -> c.dripDrizzle = v));
            link.add(flt("Drip amount: torrential rain",
                    "How many drips there are in torrential rain, as a share of Immersive Drips' own density setting. 100% = exactly "
                  + "what you set in Immersive Drips.",
                    0f, 2f, 0.05f, true, c -> c.dripTorrential, (c, v) -> c.dripTorrential = v));
            link.add(flt("Drip amount: thunderstorm",
                    "How many drips there are during a thunderstorm. Eases in a few seconds after thunder starts. Above 100% "
                  + "means more than your Immersive Drips setting.",
                    0f, 2f, 0.05f, true, c -> c.dripThunder, (c, v) -> c.dripThunder = v));
        } else {
            link.add(LabelOption.createBuilder().line(Component.literal(
                    "Immersive Drips is not installed. If you install it, this page lets this mod's wind control Immersive Drips' droplets too.")).build());
        }

        // ===== Debug =====
        List<Option<?>> debugStatus = new ArrayList<>();
        debugStatus.add(LabelOption.createBuilder().line(Component.literal(
                "Access console commands via /BRain  (for example /BRain status, /BRain weather heavy, /BRain preset dramatic, "
              + "/BRain curtains show on, /BRain flash, /BRain start, /BRain stop). Type /BRain in chat and press Tab to see them all.")).build());
        debugStatus.add(LabelOption.createBuilder().line(Component.literal(StormDirector.statusLine())).build());
        debugStatus.add(LabelOption.createBuilder().line(Component.literal(
                "Test mode swaps the real weather for one you choose, on your screen only (the server and other players are "
              + "unaffected, and there is no lightning). It switches itself off whenever you restart the game.")).build());

        List<Option<?>> quick = new ArrayList<>();
        quick.add(testButton("Test: Clear sky", "Clear weather.", 0, -1));
        quick.add(testButton("Test: Drizzle", "A fine drizzle.", 1, 0));
        quick.add(testButton("Test: Light rain", "Steady light rain.", 1, 1));
        quick.add(testButton("Test: Heavy rain", "Heavy rainfall.", 1, 2));
        quick.add(testButton("Test: Torrential rain", "A storm-like downpour.", 1, 3));
        quick.add(testButton("Test: Thunderstorm", "Torrential rain with thunder.", 2, 3));
        quick.add(ButtonOption.createBuilder()
                .name(Component.literal("Stop testing (real weather)"))
                .description(OptionDescription.of(Component.literal("Go back to the weather the world is really having.")))
                .action((scr, btn) -> {
                    CFG.testMode = false;
                    Minecraft.getInstance().gui.setScreen((Screen) null);
                })
                .build());

        List<Option<?>> manual = new ArrayList<>();
        manual.add(bool("Test mode",
                "Use the weather chosen below instead of the real weather. Applies when you close this screen.",
                c -> c.testMode, (c, v) -> c.testMode = v));
        manual.add(numFmt("Test weather", "Clear, rain, or a thunderstorm.", 0, 2, 1,
                v -> new String[] { "Clear", "Rain", "Thunderstorm" }[v], c -> c.testWeather, (c, v) -> c.testWeather = v));
        manual.add(numFmt("Test intensity", "Drizzle, light, heavy or torrential rain.", 0, 3, 1,
                v -> StormDirector.TIER_NAMES[v], c -> c.testTier, (c, v) -> c.testTier = v));
        manual.add(numFmt("Test wind", "Automatic keeps the normal random wind; the others lock it to one strength.", 0, 4, 1,
                v -> new String[] { "Automatic", "Calm", "Breezy", "Windy", "Gale" }[v], c -> c.testWind, (c, v) -> c.testWind = v));

        List<Option<?>> debug = new ArrayList<>();
        debug.add(bool("Show rain curtains (tint)",
                "Debug view: colours the rain by how thick the rain curtain is at that spot. RED = thick rain, BLUE = thin rain, white = "
              + "average. It also shows the curtains at full strength even in light rain, so you can see how they move with the wind. "
              + "Switches itself off when you restart the game. Also: /BRain curtains show on|off.",
                c -> c.curtainDebug, (c, v) -> c.curtainDebug = v));
        debug.add(bool("Log storm changes",
                "Writes a short line to the game log (logs/latest.log) whenever a storm starts, changes intensity or ends. "
              + "Handy for checking what the mod is doing.",
                c -> c.logStorms, (c, v) -> c.logStorms = v));

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Brewtality's Immersive Rain"))
                .category(ConfigCategory.createBuilder().name(Component.literal("Storms")).options(general)
                        .group(OptionGroup.createBuilder().name(Component.literal("Messages")).options(messages).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("Quick presets")).options(presets).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("Rain storms: chance of each intensity")).options(rainChances).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("Thunderstorms: chance of each intensity")).options(thunderChances).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("How storms change")).options(change).build())
                        .build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Rain Look")).options(look)
                        .group(OptionGroup.createBuilder().name(Component.literal("Heavy-rain effects")).options(heavy).build())
                        .build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Wind")).options(wind)
                        .group(OptionGroup.createBuilder().name(Component.literal("Wind personality chances (rolled each storm)")).options(windChances).build())
                        .build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Immersive Drips")).options(link).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Debug")).options(debugStatus)
                        .group(OptionGroup.createBuilder().name(Component.literal("Test a weather type (one click)")).options(quick).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("Test a weather type (choose the details)")).options(manual).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("Logging")).options(debug).build())
                        .build())
                .save(() -> CFG.save())
                .build()
                .generateScreen(parent);
    }
}
