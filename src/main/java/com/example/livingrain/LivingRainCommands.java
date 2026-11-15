package com.example.livingrain;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Client-side chat commands for quick testing. They only change this mod's own settings on your own game.
 *
 *   /livingrain start | stop
 *   /livingrain preset gentle | balanced | dramatic | chaotic
 *   /livingrain weather real | clear | drizzle | light | heavy | torrential | thunder
 *   /livingrain vanilla on | off
 *   /livingrain status
 */
public final class LivingRainCommands {

    private LivingRainCommands() { }

    private static void say(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal(text));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("livingrain");

        // ---- start / stop ----
        root.then(ClientCommands.literal("start").executes(ctx -> {
            LivingRainConfig c = LivingRainConfig.get();
            c.enabled = true;
            c.particleRain = true;
            c.save();
            say(ctx.getSource(), "Living Rain is ON: particle rain and snow are active.");
            return 1;
        }));
        root.then(ClientCommands.literal("stop").executes(ctx -> {
            LivingRainConfig c = LivingRainConfig.get();
            c.enabled = false;
            c.save();
            say(ctx.getSource(), "Living Rain is OFF: vanilla weather is back. Use /livingrain start to turn it on again.");
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
                    say(ctx.getSource(), "Vanilla rain is shown together with Living Rain.");
                    return 1;
                }))
                .then(ClientCommands.literal("off").executes(ctx -> {
                    LivingRainConfig c = LivingRainConfig.get();
                    c.showVanillaRain = false;
                    c.save();
                    say(ctx.getSource(), "Vanilla rain is hidden (default).");
                    return 1;
                })));

        // ---- status ----
        root.then(ClientCommands.literal("status").executes(ctx -> {
            FabricClientCommandSource src = ctx.getSource();
            LivingRainConfig c = LivingRainConfig.get();
            say(src, "Living Rain: " + (c.enabled ? "ON" : "OFF") + "  |  particle rain: " + (c.particleRain ? "on" : "off")
                    + "  |  vanilla rain: " + (WeatherHooks.shouldHide() ? "hidden" : "shown"));
            say(src, StormDirector.statusLine());
            say(src, String.format("Server weather: rain %.2f, thunder %.2f", StormDirector.rawRain(), StormDirector.rawThunder()));
            say(src, "Particles alive: " + RainParticle.live() + " (limit " + c.maxParticles
                    + (RainSpawner.autoCap != Integer.MAX_VALUE ? ", Minecraft is capping at ~" + RainSpawner.autoCap : "")
                    + "), created last tick: " + RainSpawner.lastSpawned + ", discarded by Minecraft: " + RainSpawner.discardedTotal);
            say(src, "Sky/shader rain-level hook: " + (StormDirector.hookWorks() ? "working" : "NOT working"));
            say(src, "Hiding vanilla rain: draw hooks " + WeatherHooks.drawHits() + " hits, splash/sound hooks "
                    + WeatherHooks.tickHits() + " hits, fallback used " + WeatherHooks.fallbackHits + " times");
            return 1;
        }));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> testWeather(String name, int weather, int tier) {
        return ClientCommands.literal(name).executes(ctx -> {
            LivingRainConfig c = LivingRainConfig.get();
            c.testMode = true;
            c.testWeather = weather;
            if (tier >= 0) c.testTier = tier;
            say(ctx.getSource(), "Test weather: " + name + " (your screen only). Use /livingrain weather real to stop.");
            return 1;
        });
    }
}
