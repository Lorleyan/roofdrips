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

        // ===== Overhang streams & drips =====
        List<Option<?>> edge = new ArrayList<>();
        edge.add(flt("Overall density",
                "How much water falls from overhang edges. Affects both drips and streams.",
                0.1f, 4f, 0.05f, false, c -> c.density, (c, v) -> c.density = v, true));
        edge.add(flt("Streams vs. drips",
                "0% = every edge only drips. 100% = every edge runs as a stream. In between gives a natural mix, "
              + "some spots stream while others just drip.",
                0f, 1f, 0.05f, true, c -> c.streamShare, (c, v) -> c.streamShare = v, true));
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
                0.5f, 2.5f, 0.05f, false, c -> c.dropSize, (c, v) -> c.dropSize = v, true));
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

        // ===== Sounds =====
        List<Option<?>> sound = new ArrayList<>();
        sound.add(bool("Enable sounds",
                "Soft drip and trickle sounds near overhangs. Also follows Minecraft's Weather volume slider.",
                c -> c.soundsEnabled, (c, v) -> c.soundsEnabled = v, false));
        sound.add(flt("Drip volume", "Volume of individual drips.",
                0f, 1f, 0.05f, true, c -> c.dripVolume, (c, v) -> c.dripVolume = v, false));
        sound.add(flt("Stream volume", "Volume of the trickle sound for running streams.",
                0f, 1f, 0.05f, true, c -> c.streamVolume, (c, v) -> c.streamVolume = v, false));
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

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Roof Drips"))
                .category(ConfigCategory.createBuilder().name(Component.literal("General")).options(general).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Overhang Streams & Drips")).options(edge).build())
                .category(ConfigCategory.createBuilder().name(Component.literal("Block-Face Streaks")).options(face).build())
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
