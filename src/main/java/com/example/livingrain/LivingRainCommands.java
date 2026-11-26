package com.example.livingrain;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Client-side chat commands for quick testing. They only change this mod's own settings on your own game.
 *
 *   /BRain start | stop
 *   /BRain preset gentle | balanced | dramatic | chaotic
 *   /BRain weather real | clear | drizzle | light | heavy | torrential | thunder
 *   /BRain vanilla on | off
 *   /BRain drips on | off
 *   /BRain curtains show on | off     (debug tint: red = thick rain, blue = thin rain)
 *   /BRain flash                      (test the lightning flash)
 *   /BRain status
 *
 * Minecraft treats command names as case-sensitive, so the same commands are also available as /brain.
 * (/BDrip is reserved for Brewtality's Immersive Drips, which has no commands yet.)
 */
public final class LivingRainCommands {

    private LivingRainCommands() { }

    private static void say(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal(text));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(build("BRain"));
        dispatcher.register(build("brain"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String rootName) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(rootName);

        // ---- start / stop ----
        root.then(ClientCommands.literal("start").executes(ctx -> {
            LivingRainConfig c = LivingRainConfig.get();
            c.enabled = true;
            c.particleRain = true;
            c.save();
            say(ctx.getSource(), "Immersive Rain is ON: particle rain and snow are active.");
            return 1;
        }));
        root.then(ClientCommands.literal("stop").executes(ctx -> {
            LivingRainConfig c = LivingRainConfig.get();
            c.enabled = false;
            c.save();
            say(ctx.getSource(), "Immersive Rain is OFF: vanilla weather is back. Use /BRain start to turn it on again.");
            return 1;
        }));

        // ---- presets ----
        LiteralArgumentBuilder<FabricClientCommandSource> preset = ClientCommands.literal("preset");
        for (String name : LivingRainConfig.PRESETS) {
            preset.then(ClientCommands.literal(name.toLowerCase()).executes(ctx -> {
                LivingRainConfig c = LivingRainConfig.get();
                LivingRainConfig.applyPreset(c, name);
                c.save();
                say(ctx.getSource(), "Preset applied: " + name + " (storm and wind settings).");
                return 1;
            }));
        }
        root.then(preset);

        // ---- test weather ----
        LiteralArgumentBuilder<FabricClientCommandSource> weather = ClientCommands.literal("weather");
        weather.then(ClientCommands.literal("real").executes(ctx -> {
            LivingRainConfig.get().testMode = false;
            say(ctx.getSource(), "Test weather off: following the world's real weather.");
            return 1;
        }));
        weather.then(testWeather("clear", 0, -1));
        weather.then(testWeather("drizzle", 1, 0));
        weather.then(testWeather("light", 1, 1));
        weather.then(testWeather("heavy", 1, 2));
        weather.then(testWeather("torrential", 1, 3));
        weather.then(testWeather("thunder", 2, 3));
        root.then(weather);

        // ---- vanilla rain ----
        root.then(ClientCommands.literal("vanilla")
                .then(ClientCommands.literal("on").executes(ctx -> {
                    LivingRainConfig c = LivingRainConfig.get();
                    c.showVanillaRain = true;
                    c.save();
                    say(ctx.getSource(), "Vanilla rain is shown together with Immersive Rain.");
                    return 1;
                }))
                .then(ClientCommands.literal("off").executes(ctx -> {
                    LivingRainConfig c = LivingRainConfig.get();
                    c.showVanillaRain = false;
                    c.save();
                    say(ctx.getSource(), "Vanilla rain is hidden (default).");
                    return 1;
                })));

        // ---- roof drips scaling ----
        root.then(ClientCommands.literal("drips")
                .then(ClientCommands.literal("on").executes(ctx -> {
                    LivingRainConfig c = LivingRainConfig.get();
                    c.dripScaling = true;
                    c.save();
                    say(ctx.getSource(), "Immersive Drips drip amounts now follow the rain's severity.");
                    return 1;
                }))
                .then(ClientCommands.literal("off").executes(ctx -> {
                    LivingRainConfig c = LivingRainConfig.get();
                    c.dripScaling = false;
                    c.save();
                    say(ctx.getSource(), "Immersive Drips ignores rain severity (behaves as it does without Immersive Rain).");
                    return 1;
                })));

        // ---- curtain debug view and lightning-flash test ----
        root.then(ClientCommands.literal("curtains").then(ClientCommands.literal("show")
                .then(ClientCommands.literal("on").executes(ctx -> {
                    LivingRainConfig.get().curtainDebug = true;
                    say(ctx.getSource(), "Curtain view ON: red = thick rain, blue = thin rain. Rain must be falling to see it "
                            + "(try /BRain weather heavy). It switches off when you restart the game.");
                    return 1;
                }))
                .then(ClientCommands.literal("off").executes(ctx -> {
                    LivingRainConfig.get().curtainDebug = false;
                    say(ctx.getSource(), "Curtain view OFF.");
                    return 1;
                }))));
        root.then(ClientCommands.literal("flash").executes(ctx -> {
            Flash.trigger();
            say(ctx.getSource(), "Lightning flash test: the rain should brighten for an instant.");
            return 1;
        }));

        // ---- status ----
        root.then(ClientCommands.literal("status").executes(ctx -> {
            FabricClientCommandSource src = ctx.getSource();
            LivingRainConfig c = LivingRainConfig.get();
            say(src, "Immersive Rain: " + (c.enabled ? "ON" : "OFF") + "  |  particle rain: " + (c.particleRain ? "on" : "off")
                    + "  |  vanilla rain: " + (WeatherHooks.shouldHide() ? "hidden" : "shown"));
            say(src, StormDirector.statusLine());
            say(src, String.format("Server weather: rain %.2f, thunder %.2f", StormDirector.rawRain(), StormDirector.rawThunder()));
            say(src, String.format("Rain falls at %.2f blocks/tick (%.0f blocks/second)", RainSpawner.lastFall, RainSpawner.lastFall * 20f));
            say(src, String.format("Rain slant sent to Immersive Drips (sideways blocks per block of fall): x %.2f, z %.2f", RainSpawner.slantX, RainSpawner.slantZ));
            say(src, String.format("Heavy-rain effects: strength %.2f | curtain contrast +/-%.0f%% | glinting drops %.0f%% | sun/moon direction: %s",
                    RainSpawner.heavyFactor, RainSpawner.lastCurtainAmp * 100f, RainSpawner.lastGlintShare * 100f,
                    SunMoon.available ? "available" : "NOT found (no backlight glow)"));
            say(src, String.format("Rain sheets alive: %d  (sheet strength %.2f)  |  lightning-flash timer: %s  |  curtain view: %s",
                    RainParticle.sheets(), RainSpawner.lastSheetShare, Flash.sourceFound ? "found" : "NOT found (only /BRain flash works)",
                    c.curtainDebug ? "ON" : "off"));
            say(src, String.format("Drop size multiplier now: x%.2f   |   splash target: %.0f per tick   |   see-through rain: %s",
                    RainSpawner.lastSizeMult, RainSpawner.lastSplashTarget, c.seeThroughWindows ? "on" : "off"));
            say(src, "Particles alive: " + RainParticle.live() + " (limit " + c.maxParticles
                    + (RainSpawner.autoCap != Integer.MAX_VALUE ? ", Minecraft is capping at ~" + RainSpawner.autoCap : "")
                    + "), created last tick: " + RainSpawner.lastSpawned + ", discarded by Minecraft: " + RainSpawner.discardedTotal);
            say(src, "Immersive Drips: " + (!RoofDripsLink.present() ? "not installed"
                    : (RoofDripsLink.supportsRainScaling() ? "1.1.0+" : "older version (no drip scaling)")
                    + ", wind override " + (c.overrideRoofDrips ? "on" : "off")
                    + ", drip scaling " + (c.dripScaling ? "on" : "off")
                    + String.format(", rain level sent to it: %.2f", StormDirector.dripLevel(c))));
            say(src, "Sky/shader rain-level hook: " + (StormDirector.hookWorks() ? "working" : "NOT working"));
            say(src, "Hiding vanilla rain: draw hooks " + WeatherHooks.drawHits() + " hits, splash/sound hooks "
                    + WeatherHooks.tickHits() + " hits, fallback used " + WeatherHooks.fallbackHits + " times");
            return 1;
        }));

        return root;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> testWeather(String name, int weather, int tier) {
        return ClientCommands.literal(name).executes(ctx -> {
            LivingRainConfig c = LivingRainConfig.get();
            c.testMode = true;
            c.testWeather = weather;
            if (tier >= 0) c.testTier = tier;
            say(ctx.getSource(), "Test weather: " + name + " (your screen only). Use /BRain weather real to stop.");
            return 1;
        });
    }
}
