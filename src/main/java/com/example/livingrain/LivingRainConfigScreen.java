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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        return Option.<Float>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step)
                        .formatValue(v -> Component.literal(pct ? Math.round(v * 100f) + "%" : String.format("%.2f", v))))
                .build();
    }

    private static Option<Integer> numFmt(String name, String tip, int min, int max, int step,
                                          Function<Integer, String> fmt,
                                          Function<LivingRainConfig, Integer> get, BiConsumer<LivingRainConfig, Integer> set) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step)
                        .formatValue(v -> Component.literal(fmt.apply(v))))
                .build();
    }

    private static Option<Integer> num(String name, String tip, int min, int max, int step, String unit,
                                       Function<LivingRainConfig, Integer> get, BiConsumer<LivingRainConfig, Integer> set) {
        return numFmt(name, tip, min, max, step, v -> v + unit, get, set);
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
        general.add(bool("Enable Living Rain",
                "Master switch. Off = everything goes back to vanilla Minecraft rain.",
                c -> c.enabled, (c, v) -> c.enabled = v));
        general.add(bool("Control sky & shader rain level",
                "Lets this mod set the rain level that the sky, vanilla clouds and shader packs read. "
              + "With this on, a drizzle gives a lighter sky and lighter shader clouds than a downpour (in shader packs that "
              + "scale their clouds with rain). Roof Drips follows it too. Only affects your own screen, never the server.",
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
        look.add(num("Radius",
                "How far around you rain is created (blocks). Bigger looks more like a real storm but costs more performance.",
                8, 30, 1, " blocks", c -> c.radius, (c, v) -> c.radius = v));
        look.add(num("Rain height",
                "How far above your head rain is created. Rain fades out near this height.",
                6, 30, 1, " blocks", c -> c.spawnHeight, (c, v) -> c.spawnHeight = v));
        look.add(flt("Density",
                "How many drops there are overall. The storm intensity scales this up and down automatically, so a drizzle "
              + "always has far fewer drops than a torrential downpour. Raise it for a heavier look.",
                0.1f, 6f, 0.1f, false, c -> c.density, (c, v) -> c.density = v));
        look.add(numFmt("Particle limit",
                "The most rain particles alive at once. This is the main performance control: lower it if the game stutters "
              + "in storms. If you raise Density a lot, raise this too, or heavy rain will thin out. "
              + "Minecraft itself starts discarding particles at around 16,000.",
                200, 14000, 100, v -> String.valueOf(v), c -> c.maxParticles, (c, v) -> c.maxParticles = v));
        look.add(flt("Motion blur",
                "Stretches each drop into a soft streak, longer the faster it moves (gusts stretch drops further), so the rain looks "
              + "smooth instead of blocky. 0% = short crisp streaks.",
                0f, 2f, 0.05f, true, c -> c.motionBlur, (c, v) -> c.motionBlur = v));
        look.add(flt("Opacity", "How visible the rain is.",
                0.1f, 1f, 0.05f, true, c -> c.opacity, (c, v) -> c.opacity = v));
        look.add(flt("Drop size", "Size of the rain streaks and snowflakes.",
                0.3f, 3f, 0.05f, false, c -> c.dropSize, (c, v) -> c.dropSize = v));
        look.add(flt("Fall speed", "How fast drops fall.",
                0.3f, 2f, 0.05f, false, c -> c.fallSpeed, (c, v) -> c.fallSpeed = v));
        look.add(flt("Fall speed variance", "How much individual drops differ in speed.",
                0f, 0.6f, 0.05f, true, c -> c.fallSpeedVar, (c, v) -> c.fallSpeedVar = v));
        look.add(bool("Ground splashes", "Tiny splashes where drops land near you.",
                c -> c.splashes, (c, v) -> c.splashes = v));
        look.add(flt("Rain volume",
                "Volume of the rain sound. Also follows Minecraft's Weather volume slider. Gets louder with heavier rain.",
                0f, 1.5f, 0.05f, true, c -> c.rainVolume, (c, v) -> c.rainVolume = v));
        look.add(bool("Flip streak tilt",
                "Only if rain streaks look tilted the wrong way compared with the direction they travel, turn this on.",
                c -> c.flipSlant, (c, v) -> c.flipSlant = v));

        // ===== Wind =====
        List<Option<?>> wind = new ArrayList<>();
        wind.add(bool("Enable wind",
                "Blows rain and snow sideways. Minecraft has no wind of its own, so this is a simulation.",
                c -> c.windEnabled, (c, v) -> c.windEnabled = v));
        wind.add(flt("Wind strength", "Overall wind strength. Multiplies the personalities below.",
                0f, 3f, 0.05f, false, c -> c.windStrength, (c, v) -> c.windStrength = v));
        wind.add(flt("Rain slant",
                "How strongly the wind slants rain. Roof Drips' water is pushed by the wind itself; rain falls much faster, "
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

        // ===== Roof Drips =====
        List<Option<?>> link = new ArrayList<>();
        boolean rd = RoofDripsLink.present();
        if (rd) {
            link.add(LabelOption.createBuilder().line(Component.literal(
                    "Roof Drips is installed. With the box below ticked, THIS mod's wind drives Roof Drips' droplets too, "
                  + "so rain and drips lean the same way. Roof Drips' own wind settings are then ignored.")).build());
            if (!RoofDripsLink.supportsOverride()) {
                link.add(LabelOption.createBuilder().line(Component.literal(
                        "Your Roof Drips is too old to accept this (it needs version 1.0.9 or newer). Until you update it, "
                      + "it keeps using its own wind.")).build());
            }
            link.add(bool("Override Roof Drips settings",
                    "On: this mod's wind controls both. Off: Roof Drips keeps its own wind, and the rain here follows "
                  + "Roof Drips' wind instead.",
                    c -> c.overrideRoofDrips, (c, v) -> c.overrideRoofDrips = v));
        } else {
            link.add(LabelOption.createBuilder().line(Component.literal(
                    "Roof Drips is not installed. If you install it, this page lets this mod's wind control Roof Drips' droplets too.")).build());
        }

        // ===== Debug =====
        List<Option<?>> debugStatus = new ArrayList<>();
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
        debug.add(bool("Log storm changes",
                "Writes a short line to the game log (logs/latest.log) whenever a storm starts, changes intensity or ends. "
              + "Handy for checking what the mod is doing.",
                c -> c.logStorms, (c, v) -> c.logStorms = v));

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Living Rain"))
                .category(ConfigCategory.createBuilder().name(Component.literal("Storms")).options(general)
                        .group(OptionGroup.createBuilder().name(Component.literal("Messages")).options(messages).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("Quick presets")).options(presets).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("Rain storms: chance of each intensity")).options(rainChances).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("Thunderstorms: chance of each intensity")).options(thunderChances).build())
                        .group(OptionGroup.createBuilder().name(Component.literal("How storms change")).options(change).build())
                        .build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Rain Look")).options(look).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Wind")).options(wind)
                        .group(OptionGroup.createBuilder().name(Component.literal("Wind personality chances (rolled each storm)")).options(windChances).build())
                        .build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Roof Drips")).options(link).build())
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
