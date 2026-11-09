package com.example.eavesdrip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.example.eavesdrip.RoofDripsConfig.Preset;

import dev.isxander.yacl3.api.Binding;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The in-game settings screen (opened from Mod Menu's Configure button). Built with YACL. */
public final class RoofDripsConfigScreen {

    private interface Applier {
        void read(RoofDripsConfig from);   // push values from a config into the option
        void write(RoofDripsConfig to);    // pull the option's pending value into a config
    }

    private static RoofDripsConfig CFG;
    private static RoofDripsConfig DEF;
    private static final List<Applier> APPLIERS = new ArrayList<>();
    private static Option<Preset> presetOpt;
    private static boolean applying;

    private RoofDripsConfigScreen() { }

    private static String compass(int deg) {
        String[] names = { "N", "NE", "E", "SE", "S", "SW", "W", "NW" };
        return names[((int) Math.round(deg / 45.0)) % 8];
    }

    // ---------------- Preset logic ----------------
    /** Called when any preset-controlled setting changes: select the matching preset, else CUSTOM. */
    private static void onSettingChanged() {
        if (applying || presetOpt == null) return;
        try {
            RoofDripsConfig tmp = new RoofDripsConfig();
            for (Applier a : APPLIERS) a.write(tmp);
            Preset match = tmp.detectPreset();
            if (presetOpt.pendingValue() != match) {
                applying = true;
                try { presetOpt.requestSet(match); } finally { applying = false; }
            }
        } catch (RuntimeException ignored) { }
    }

    /** Called when the user picks a preset: push its values into every preset-controlled option. */
    private static void onPresetPicked(Preset p) {
        if (applying || p == Preset.CUSTOM) return;
        applying = true;
        try {
            RoofDripsConfig ref = new RoofDripsConfig();
            RoofDripsConfig.applyPreset(ref, p);
            for (Applier a : APPLIERS) a.read(ref);
        } finally {
            applying = false;
        }
    }

    // ---------------- Option helpers ----------------
    private static <T> void register(Option<T> o, Function<RoofDripsConfig, T> get,
                                     BiConsumer<RoofDripsConfig, T> set, boolean inPreset) {
        if (!inPreset) return;
        APPLIERS.add(new Applier() {
            @Override public void read(RoofDripsConfig from) { o.requestSet(get.apply(from)); }
            @Override public void write(RoofDripsConfig to) { set.accept(to, o.pendingValue()); }
        });
    }

    private static Option<Boolean> bool(String name, String tip,
            Function<RoofDripsConfig, Boolean> get, BiConsumer<RoofDripsConfig, Boolean> set, boolean inPreset) {
        Option<Boolean> o = Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(TickBoxControllerBuilder::create)
                .addListener((opt, ev) -> { if (inPreset) onSettingChanged(); })
                .build();
        register(o, get, set, inPreset);
        return o;
    }

    private static Option<Float> flt(String name, String tip, float min, float max, float step, boolean pct,
            Function<RoofDripsConfig, Float> get, BiConsumer<RoofDripsConfig, Float> set, boolean inPreset) {
        Option<Float> o = Option.<Float>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(min, max).step(step)
                        .formatValue(v -> Component.literal(pct ? Math.round(v * 100f) + "%" : String.format("%.2f", v))))
                .addListener((opt, ev) -> { if (inPreset) onSettingChanged(); })
                .build();
        register(o, get, set, inPreset);
        return o;
    }

    private static Option<Integer> num(String name, String tip, int min, int max, int step, String unit,
            Function<RoofDripsConfig, Integer> get, BiConsumer<RoofDripsConfig, Integer> set, boolean inPreset) {
        Option<Integer> o = Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step)
                        .formatValue(v -> Component.literal(v + unit)))
                .addListener((opt, ev) -> { if (inPreset) onSettingChanged(); })
                .build();
        register(o, get, set, inPreset);
        return o;
    }

    private static Option<Integer> numFmt(String name, String tip, int min, int max, int step,
            java.util.function.IntFunction<String> fmt,
            Function<RoofDripsConfig, Integer> get, BiConsumer<RoofDripsConfig, Integer> set, boolean inPreset) {
        Option<Integer> o = Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(tip)))
                .binding(Binding.generic(get.apply(DEF), () -> get.apply(CFG), v -> set.accept(CFG, v)))
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step)
                        .formatValue(v -> Component.literal(fmt.apply(v))))
                .addListener((opt, ev) -> { if (inPreset) onSettingChanged(); })
                .build();
        register(o, get, set, inPreset);
        return o;
    }

    // ---------------- Screen ----------------
    public static Screen create(Screen parent) {
        CFG = RoofDripsConfig.get();
        DEF = new RoofDripsConfig();
        APPLIERS.clear();
        presetOpt = null;
        applying = false;

        // ===== General =====
        Option<Boolean> enabled = bool("Enable effect",
                "Master switch. Turn off to disable all drips, streams and sounds.",
                c -> c.enabled, (c, v) -> c.enabled = v, false);

        presetOpt = Option.<Preset>createBuilder()
                .name(Component.literal("Preset"))
                .description(OptionDescription.of(Component.literal(
                        "Light: easy on performance. Default: running water and drips. Extreme: lots of everything. "
                      + "Changing any preset setting below switches this to CUSTOM.")))
                .binding(Binding.generic(DEF.preset, () -> CFG.preset, v -> CFG.preset = v))
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(Preset.class)
                        .formatValue(p -> p == Preset.CUSTOM
                                ? Component.literal("CUSTOM").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                : Component.literal(p.name().charAt(0) + p.name().substring(1).toLowerCase())))
                .addListener((opt, ev) -> onPresetPicked(opt.pendingValue()))
                .build();

        Option<Integer> radius = num("Effect radius",
                "How far from you the effect works (blocks). Bigger looks more 'everywhere' but costs more.",
                8, 64, 2, " blocks", c -> c.radius, (c, v) -> c.radius = v, true);
        Option<Integer> maxParticles = num("Particle cap per tick",
                "Hard limit on new droplets per tick. Lower this on slower computers.",
                10, 600, 10, "", c -> c.maxParticles, (c, v) -> c.maxParticles = v, true);
        Option<Integer> scanSpeed = num("Search speed",
                "How quickly new overhangs are found around you. Higher finds them faster after you move or teleport, but costs a little more.",
                20, 500, 10, "", c -> c.scanSpeed, (c, v) -> c.scanSpeed = v, true);
        Option<Float> opacity = flt("Water opacity",
                "How visible the droplets are. Lower is more subtle.",
                0.2f, 1f, 0.05f, true, c -> c.opacity, (c, v) -> c.opacity = v, true);
        Option<Integer> afterRain = num("Drip-off time after rain",
                "How long edges keep dripping after the rain stops, fading out gradually. 0 = stop immediately.",
                0, 120, 5, " s", c -> c.afterRainSeconds, (c, v) -> c.afterRainSeconds = v, false);

        List<Option<?>> general = new ArrayList<>();
        general.add(enabled); general.add(presetOpt); general.add(radius); general.add(maxParticles);
        general.add(scanSpeed); general.add(opacity); general.add(afterRain);
        general.add(num("Droplet lifetime limit",
                "Safety limit: droplets that haven't landed after this long simply disappear. Matters mainly for very tall overhangs.",
                2, 30, 1, " s", c -> c.maxDropSeconds, (c, v) -> c.maxDropSeconds = v, false));

        // ===== Overhang streams & drips =====
        List<Option<?>> edge = new ArrayList<>();
        edge.add(flt("Overall density",
                "How much water falls from overhang edges. Affects both drips and streams.",
                0.1f, 4f, 0.05f, false, c -> c.density, (c, v) -> c.density = v, true));
        edge.add(numFmt("Overhang thickness",
                "How many stacked blocks tall an overhang can be and still drip. Arches and cave mouths are often "
              + "several blocks thick. 1 = only single-block overhangs. Slide to the far right for Infinite.",
                1, RoofDripsConfig.INFINITE_DEPTH, 1,
                v -> v >= RoofDripsConfig.INFINITE_DEPTH ? "Infinite" : v + (v == 1 ? " block" : " blocks"),
                c -> c.overhangDepth, (c, v) -> c.overhangDepth = v, false));
        edge.add(bool("Drip above hanging objects",
                "Lets a block still drip when something like a chain, lantern, fence or wall piece hangs "
              + "underneath it. The water falls past the object and never appears on the object itself.",
                c -> c.allowAboveDecor, (c, v) -> c.allowAboveDecor = v, false));
        edge.add(flt("Stream amount",
                "The average share of streaming versus dripping. 0% = edges mostly drip. 100% = edges run as "
              + "constant streams. Each edge gets its own blend around this average.",
                0f, 1f, 0.05f, true, c -> c.streamShare, (c, v) -> c.streamShare = v, true));
        edge.add(flt("Edge variation",
                "How different edges look from each other. Low = all edges behave alike. High = some edges "
              + "pour while their neighbours only drip.",
                0f, 1f, 0.05f, true, c -> c.edgeVariation, (c, v) -> c.edgeVariation = v, true));
        edge.add(num("Stream length",
                "How long each stream runs before pausing. Longer = more continuous ribbons of water.",
                1, 10, 1, "", c -> c.streamLength, (c, v) -> c.streamLength = v, true));
        edge.add(flt("Stream length variance",
                "How much stream lengths differ from each other. 0% = all identical.",
                0f, 1f, 0.05f, true, c -> c.streamLengthVar, (c, v) -> c.streamLengthVar = v, true));
        edge.add(flt("Fall speed",
                "How fast droplets fall. Higher feels heavier and quicker.",
                0.3f, 3f, 0.05f, false, c -> c.fallSpeed, (c, v) -> c.fallSpeed = v, true));
        edge.add(flt("Fall speed variance",
                "How much individual droplets differ in speed.",
                0f, 1f, 0.05f, true, c -> c.fallSpeedVar, (c, v) -> c.fallSpeedVar = v, true));
        edge.add(flt("Droplet size",
                "Size of the falling droplets.",
                0.5f, 5f, 0.1f, false, c -> c.dropSize, (c, v) -> c.dropSize = v, true));
        edge.add(bool("Ground splashes",
                "Tiny splashes where droplets land. Looks nice, costs a bit.",
                c -> c.splashes, (c, v) -> c.splashes = v, true));

        // ===== Block-face streaks =====
        List<Option<?>> face = new ArrayList<>();
        face.add(flt("Amount",
                "How many streaks run down the face of overhang blocks. 0% turns them off completely. "
              + "Lower this if you raise the overhang density and it gets too busy.",
                0f, 4f, 0.05f, false, c -> c.faceStreakAmount, (c, v) -> c.faceStreakAmount = v, true));
        face.add(flt("Streak length",
                "How long the visible trail of each streak is.",
                0.1f, 1f, 0.05f, true, c -> c.faceStreakLength, (c, v) -> c.faceStreakLength = v, true));
        face.add(flt("Length variance",
                "How much streak lengths differ from each other.",
                0f, 1f, 0.05f, true, c -> c.faceStreakLengthVar, (c, v) -> c.faceStreakLengthVar = v, true));
        face.add(flt("Streak speed",
                "How fast streaks slide down the block face.",
                0.2f, 4f, 0.05f, false, c -> c.faceStreakSpeed, (c, v) -> c.faceStreakSpeed = v, true));
        face.add(flt("Speed variance",
                "How much streak speeds differ, so they don't all move in lockstep.",
                0f, 1f, 0.05f, true, c -> c.faceStreakSpeedVar, (c, v) -> c.faceStreakSpeedVar = v, true));
        face.add(flt("Squiggly streaks",
                "The share of streaks that wander left and right and speed up or slow down as they run, like rain "
              + "on a window. 0% = every streak runs straight down.",
                0f, 1f, 0.05f, true, c -> c.faceWander, (c, v) -> c.faceWander = v, true));
        face.add(flt("Wander strength",
                "How far squiggly streaks drift sideways. Low = gentle curves, high = more winding.",
                0.2f, 2f, 0.05f, false, c -> c.faceWanderStrength, (c, v) -> c.faceWanderStrength = v, true));

        // ===== Sounds =====
        List<Option<?>> sound = new ArrayList<>();
        sound.add(bool("Enable sounds",
                "Soft drip and trickle sounds near overhangs. Also follows Minecraft's Weather volume slider.",
                c -> c.soundsEnabled, (c, v) -> c.soundsEnabled = v, false));
        sound.add(flt("Drip volume", "Volume of individual drips. Above 100% layers extra copies of the sound to make it louder.",
                0f, 3f, 0.05f, true, c -> c.dripVolume, (c, v) -> c.dripVolume = v, false));
        sound.add(flt("Stream volume", "Volume of the trickle sound for running streams. Above 100% layers extra copies to make it louder.",
                0f, 3f, 0.05f, true, c -> c.streamVolume, (c, v) -> c.streamVolume = v, false));
        sound.add(num("Sound limit",
                "Maximum drip/trickle sounds per second. Keeps it pleasant and light on performance.",
                1, 30, 1, "/s", c -> c.maxSoundsPerSecond, (c, v) -> c.maxSoundsPerSecond = v, false));

        // ===== Trees =====
        List<Option<?>> trees = new ArrayList<>();
        trees.add(bool("Enable tree dripping",
                "Rain drips off the underside and edges of leaves. No streaks on leaves, only falling drops. Off by default.",
                c -> c.treesEnabled, (c, v) -> c.treesEnabled = v, false));
        trees.add(flt("Tree density", "How much water drips from leaves.",
                0.1f, 4f, 0.05f, false, c -> c.treeDensity, (c, v) -> c.treeDensity = v, false));
        trees.add(num("Tree particle cap per tick", "Separate limit for leaf drips so forests can't hog performance.",
                5, 200, 5, "", c -> c.treeMaxParticles, (c, v) -> c.treeMaxParticles = v, false));
        trees.add(num("Tree radius", "How far from you leaf dripping works.",
                8, 48, 2, " blocks", c -> c.treeRadius, (c, v) -> c.treeRadius = v, false));

        // ===== Cave drip =====
        List<Option<?>> cave = new ArrayList<>();
        cave.add(bool("Enable cave drip",
                "Slow, subtle drips from cave ceilings where water or rain is close enough above to leak through.",
                c -> c.caveEnabled, (c, v) -> c.caveEnabled = v, false));
        cave.add(flt("Strength",
                "How often cave ceilings drip. Caves are meant to be far quieter than outdoor rain, so keep this low.",
                0.1f, 4f, 0.05f, false, c -> c.caveDensity, (c, v) -> c.caveDensity = v, false));
        cave.add(flt("Droplet size", "Size of cave droplets.",
                0.5f, 5f, 0.1f, false, c -> c.caveDropSize, (c, v) -> c.caveDropSize = v, false));
        cave.add(flt("Fall speed", "How fast cave droplets fall.",
                0.3f, 3f, 0.05f, false, c -> c.caveFallSpeed, (c, v) -> c.caveFallSpeed = v, false));
        cave.add(num("Cave radius", "How far from you cave drips are searched for and shown.",
                8, 40, 2, " blocks", c -> c.caveRadius, (c, v) -> c.caveRadius = v, false));
        cave.add(num("Particle cap per tick", "Separate limit for cave drips.",
                2, 60, 1, "", c -> c.caveMaxParticles, (c, v) -> c.caveMaxParticles = v, false));
        cave.add(bool("Water above ceilings",
                "Drip from ceiling blocks that have water (a lake, ocean or underground pool) within range above them.",
                c -> c.caveWaterEnabled, (c, v) -> c.caveWaterEnabled = v, false));
        cave.add(num("Water detection range",
                "How many blocks above a ceiling block to look for water.",
                1, 12, 1, " blocks", c -> c.caveWaterRange, (c, v) -> c.caveWaterRange = v, false));
        cave.add(bool("Rain seeping through",
                "While it rains outside, ceilings with the surface close enough above them occasionally drip, "
              + "as if some of the rain seeps through.",
                c -> c.caveRainEnabled, (c, v) -> c.caveRainEnabled = v, false));
        cave.add(num("Rain detection range",
                "How many blocks above a ceiling block the surface can be for rain to seep through.",
                1, 12, 1, " blocks", c -> c.caveRainRange, (c, v) -> c.caveRainRange = v, false));
        cave.add(flt("Rain seep chance",
                "How often seeping rain drips compared to water drips. Low = only the occasional drop.",
                0f, 1f, 0.05f, true, c -> c.caveRainChance, (c, v) -> c.caveRainChance = v, false));
        cave.add(bool("Natural blocks only",
                "Only natural ceilings (stone, dirt, sand, deepslate...) drip. Keeps player-built roofs and rooms dry.",
                c -> c.caveNaturalOnly, (c, v) -> c.caveNaturalOnly = v, false));
        cave.add(flt("Drip sound volume",
                "Volume of cave drip sounds. Above 100% layers extra copies to make it louder. 0% = silent.",
                0f, 3f, 0.05f, true, c -> c.caveSoundVolume, (c, v) -> c.caveSoundVolume = v, false));

        List<Option<?>> dripstone = new ArrayList<>();
        dripstone.add(bool("Drip from dripstone",
                "Water drops fall from the tips of hanging pointed dripstone.",
                c -> c.dripstoneEnabled, (c, v) -> c.dripstoneEnabled = v, false));
        dripstone.add(bool("Only with water above",
                "Only drip when water (or seeping rain) is above the dripstone, like in vanilla. "
              + "Off = every hanging dripstone tip drips.",
                c -> c.dripstoneRequireWater, (c, v) -> c.dripstoneRequireWater = v, false));
        dripstone.add(flt("Dripstone strength", "How often dripstone tips drip.",
                0.1f, 4f, 0.05f, false, c -> c.dripstoneDensity, (c, v) -> c.dripstoneDensity = v, false));
        dripstone.add(flt("Dripstone droplet size", "Size of dripstone droplets.",
                0.5f, 5f, 0.1f, false, c -> c.dripstoneDropSize, (c, v) -> c.dripstoneDropSize = v, false));
        dripstone.add(flt("Dripstone fall speed", "How fast dripstone droplets fall.",
                0.3f, 3f, 0.05f, false, c -> c.dripstoneFallSpeed, (c, v) -> c.dripstoneFallSpeed = v, false));

        // ===== Wind =====
        List<Option<?>> wind = new ArrayList<>();
        wind.add(bool("Enable wind",
                "Blows falling droplets sideways. Minecraft has no wind of its own, so this is a simulation "
              + "that only affects this mod's water. Off by default.",
                c -> c.windEnabled, (c, v) -> c.windEnabled = v, false));
        wind.add(flt("Wind strength", "How hard the wind pushes droplets.",
                0f, 3f, 0.05f, false, c -> c.windStrength, (c, v) -> c.windStrength = v, false));
        wind.add(numFmt("Wind direction",
                "The compass direction the wind blows TOWARD (0 = north, 90 = east, 180 = south, 270 = west).",
                0, 355, 5, v -> v + "\u00B0 " + compass(v),
                c -> c.windDirection, (c, v) -> c.windDirection = v, false));
        wind.add(flt("Direction drift",
                "How much the wind direction slowly wanders around the chosen direction. 0% = fixed direction.",
                0f, 1f, 0.05f, true, c -> c.windDriftAmount, (c, v) -> c.windDriftAmount = v, false));
        wind.add(bool("New direction each storm",
                "Picks a random wind direction every time rain starts, instead of using the direction above.",
                c -> c.windRandomPerStorm, (c, v) -> c.windRandomPerStorm = v, false));
        wind.add(bool("Storms are windier",
                "Thunderstorms blow up to 50% harder than ordinary rain.",
                c -> c.windStormBoost, (c, v) -> c.windStormBoost = v, false));
        wind.add(flt("Turbulence",
                "Random jitter so droplets don't all travel in perfectly parallel lines.",
                0f, 1f, 0.05f, true, c -> c.windTurbulence, (c, v) -> c.windTurbulence = v, false));
        wind.add(flt("Gust frequency",
                "How often gusts happen. 0% = no gusts. 100% = roughly one every few seconds.",
                0f, 1f, 0.05f, true, c -> c.gustFrequency, (c, v) -> c.gustFrequency = v, false));
        wind.add(flt("Gust strength",
                "How much extra push a gust gives, on top of the normal wind.",
                0f, 3f, 0.05f, true, c -> c.gustStrength, (c, v) -> c.gustStrength = v, false));
        wind.add(num("Gust duration",
                "How long a typical gust lasts. Gusts swell and fade smoothly.",
                1, 10, 1, " s", c -> c.gustDuration, (c, v) -> c.gustDuration = v, false));

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Roof Drips"))
                .category(ConfigCategory.createBuilder().name(Component.literal("General")).options(general).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Overhang Streams & Drips")).options(edge).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Block-Face Streaks")).options(face).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Cave Drip")).options(cave)
                        .group(OptionGroup.createBuilder().name(Component.literal("Dripstone")).options(dripstone).build())
                        .build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Wind")).options(wind).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Sounds")).options(sound).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Trees")).options(trees).build())
                .save(() -> {
                    CFG.preset = CFG.detectPreset();
                    CFG.save();
                })
                .build()
                .generateScreen(parent);
    }
}
