package com.example.livingrain;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * The little in-game messages ("The rain eases up."). Shown above the hotbar or in chat.
 * Minecraft's "show a message to the player" method has been renamed several times between versions, so this
 * tries the known names (via reflection) and uses the first that exists. The log says which one worked.
 */
public final class WeatherMessages {

    private static final Logger LOG = LoggerFactory.getLogger("livingrain");

    // ---------------- Message pools ----------------
    static final String[][] START = {
        { "A fine drizzle begins to fall.", "A soft, misty drizzle drifts down from the grey sky.", "Tiny droplets start to mist the air." },
        { "Rain begins to patter softly.", "A gentle rain starts to fall.", "The first steady raindrops begin to fall." },
        { "Heavy rain sweeps in.", "The clouds open up and the rain comes down in earnest.", "A heavy rain sets in, drumming on everything." },
        { "The sky tears open. A torrential downpour begins!", "Rain hammers down in sheets.", "A sudden, roaring deluge crashes down." }
    };
    static final String[] THUNDER_START = {
        "A thunderstorm rolls in.", "Thunder grumbles overhead as the storm gathers.", "The air turns heavy. A storm is brewing." };
    static final String[] THUNDER_END = {
        "The thunder fades into the distance.", "The thunder rolls away, leaving the rain behind.", "The thunder falls silent." };
    static final String[][] UP_TO = {
        { },
        { "The drizzle thickens into steady rain.", "The rain picks up a little.", "The patter grows steadier." },
        { "The rain grows heavy.", "The rain thickens and begins to drum on the ground.", "The shower swells into a heavy rain." },
        { "The storm erupts into a torrential downpour!", "The heavens open and the rain comes down in torrents.", "The rain turns torrential." }
    };
    static final String[][] DOWN_TO = {
        { "The storm settles into a gentle drizzle.", "The rain thins to a soft, misty drizzle.", "The rain dwindles to a fine drizzle." },
        { "The rain eases up.", "The rain settles into a gentle patter.", "The storm softens into a light rain." },
        { "The torrent eases to a heavy rain.", "The downpour loosens its grip.", "The rain lessens, but still falls heavily." },
        { }
    };
    static final String[] SQUALL = {
        "A sudden squall whips up!", "The rain surges in a violent gust.", "The storm rises in a savage burst." };
    static final String[] LULL = {
        "The rain falters for a moment.", "A hush falls as the rain nearly stops.", "The storm draws breath." };
    static final String[] WIND_SHIFT = {
        "The wind shifts, driving the rain in from a new direction.", "The wind swings round and the rain follows.", "A change in the wind sends the rain slanting the other way." };
    static final String[] STORM_ENDING = {
        "The storm is passing. The rain begins to fade.", "The clouds thin and the rain starts to ease away.", "The rain slackens as the storm moves on." };
    static final String[] STORM_ENDED = {
        "The last raindrops fall. The sky begins to clear.", "The rain stops. Water drips from the leaves.", "Silence settles. The rain is gone." };
    static final String[][] WIND_UP = {
        { },
        { "A cool breeze picks up.", "A light breeze stirs the rain." },
        { "The wind rises, driving the rain sideways.", "The wind strengthens, whipping the rain along." },
        { "A howling gale tears through the storm!", "The wind roars, hurling the rain before it." }
    };
    static final String[][] WIND_DOWN = {
        { "The wind dies down to a whisper.", "The air falls nearly still." },
        { "The wind eases to a gentle breeze.", "The wind softens." },
        { "The gale loosens its grip.", "The howling wind settles to a steady blow." },
        { }
    };

    // ---------------- Display ----------------
    private static long lastShown = -100000L;
    private static boolean failedLogged, loggedMethod;
    private static Method overlayOnGui, overlayOnPlayer, overlayOnPlayer1, chatOnPlayer;

    private WeatherMessages() { }

    public static String pick(String[] pool, RandomSource rnd) {
        if (pool.length == 0) return null;
        return pool[rnd.nextInt(pool.length)];
    }

    static void resetCooldown() { lastShown = -100000L; }

    /**
     * @param major start/end of a storm, thunder: always shown. Minor changes are skipped if one was shown in the last 15 seconds.
     */
    public static void show(Minecraft mc, LivingRainConfig cfg, String text, boolean major, long clock) {
        if (text == null || mc == null || mc.player == null || !cfg.weatherMessages) return;
        if (!major && clock - lastShown < 20L * 15L) return;
        lastShown = clock;
        Component c = Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        boolean ok = cfg.messageStyle == 0 ? actionBar(mc, c) : chat(mc, c);
        if (!ok) ok = cfg.messageStyle == 0 ? chat(mc, c) : actionBar(mc, c);
        if (!ok && !failedLogged) {
            failedLogged = true;
            LOG.warn("[livingrain] Could not find a way to show messages to the player in this Minecraft version.");
        }
    }

    private static boolean actionBar(Minecraft mc, Component c) {
        try {
            Object gui = mc.gui;
            if (overlayOnGui == null && gui != null) overlayOnGui = find(gui.getClass(), "setOverlayMessage", Component.class, boolean.class);
            if (overlayOnGui != null && gui != null) { overlayOnGui.invoke(gui, c, Boolean.FALSE); logUsed("Gui.setOverlayMessage"); return true; }

            Object pl = mc.player;
            if (overlayOnPlayer == null) overlayOnPlayer = find(pl.getClass(), "displayClientMessage", Component.class, boolean.class);
            if (overlayOnPlayer != null) { overlayOnPlayer.invoke(pl, c, Boolean.TRUE); logUsed("Player.displayClientMessage"); return true; }

            if (overlayOnPlayer1 == null) overlayOnPlayer1 = find(pl.getClass(), "sendOverlayMessage", Component.class);
            if (overlayOnPlayer1 != null) { overlayOnPlayer1.invoke(pl, c); logUsed("Player.sendOverlayMessage"); return true; }
        } catch (Throwable t) {
            // fall through
        }
        return false;
    }

    private static boolean chat(Minecraft mc, Component c) {
        try {
            Object pl = mc.player;
            if (chatOnPlayer == null) chatOnPlayer = find(pl.getClass(), "sendSystemMessage", Component.class);
            if (chatOnPlayer != null) { chatOnPlayer.invoke(pl, c); logUsed("Player.sendSystemMessage"); return true; }
        } catch (Throwable t) {
            // fall through
        }
        return false;
    }

    private static Method find(Class<?> cls, String name, Class<?>... params) {
        try {
            return cls.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static void logUsed(String what) {
        if (!loggedMethod) {
            loggedMethod = true;
            LOG.info("[livingrain] Weather messages are shown using {}.", what);
        }
    }
}
