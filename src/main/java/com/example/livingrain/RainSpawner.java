package com.example.livingrain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Creates the rain and snow particles each tick, in a cylinder around the player, and plays the rain sounds.
 * Particles are only created where the sky is open above them, so rain never falls indoors.
 */
public final class RainSpawner {

    // Shared with the particles (updated once per tick, before any particle ticks).
    public static double camX, camY, camZ;
    /** The camera's right and up directions in world space (they depend on yaw AND pitch). */
    public static float rightX = -1f, rightZ = 0f, upX = 0f, upY = 1f, upZ = 0f;
    public static float radiusNow = 22f;
    public static float windX, windZ, windTurb;
    public static double killDistSq = 1600.0;
    public static boolean flipSlant, splashes;
    /** Chance that a drop landing near the player makes a splash (lowered automatically in dense rain). */
    public static float splashChance = 0.35f;
    /** Rain colour brightness (set each tick from the settings; new particles read it). */
    public static float brightness = 0.85f;
    /** 0..1: how much the heavy-rain effects (curtains, glints, backlight) apply right now. 0 in drizzle and light rain. */
    public static float heavyFactor;
    /** Backlight setting times heavyFactor; read by the particles. */
    public static float backlightAmount;
    /** Share of drops that are glint variants right now (for /BRain status). */
    public static float lastGlintShare, lastCurtainAmp, lastSheetShare;
    /** How much brighter the rain is right now because of a lightning flash (0 = not at all). */
    public static float flashBoost;
    private static float accSheet;

    /** Counts spawner ticks. Particles stamp it so we can tell which ones Minecraft has stopped ticking. */
    public static long clock;
    /** Particles created on the last tick, and the lowest limit Minecraft has been seen to enforce (for /BRain status). */
    public static int lastSpawned;
    public static int autoCap = Integer.MAX_VALUE;
    public static int discardedTotal;
    /** Current rain fall speed in blocks per tick (for /BRain status). */
    public static float lastFall;
    /** How far the visible rain moves sideways per block it falls (x and z). Immersive Drips uses this to see which lower overhangs rain reaches. */
    public static float slantX, slantZ;
    /** Current drop-size multiplier and splash target per tick (for /BRain status). */
    public static float lastSizeMult, lastSplashTarget;
    private static boolean limitLogged;
    private static int stallTicks;

    private static float acc;
    private static final BlockPos.MutableBlockPos P = new BlockPos.MutableBlockPos();

    private RainSpawner() { }

    /**
     * Rain drop size multiplier for the current weather: big drops in light rain, smaller ones as rain gets heavier.
     * Drizzle uses "Drop size: drizzle", torrential rain uses "Drop size: thunderstorm", and light and heavy rain are blended
     * in between. The blend drops a little faster than a straight line, so even light rain is already smaller than a drizzle.
     * During any thunderstorm the size eases to the thunderstorm size (a few seconds after thunder starts).
     */
    public static float sizeMultiplier(float inten, LivingRainConfig cfg) {
        float t = Math.max(0f, Math.min(1f, (inten - 0.25f) / 0.75f));
        float rainSize = cfg.dropSizeDrizzle + (cfg.dropSizeThunder - cfg.dropSizeDrizzle) * (float) Math.pow(t, 0.75);
        float tb = StormDirector.thunderBlend();
        return rainSize + (cfg.dropSizeThunder - rainSize) * tb;
    }

    /**
     * How fast rain falls, in blocks per tick, BEFORE the user's "Fall speed" multiplier. The heavier the rain, the
     * faster it falls: torrential rain falls about 3x as fast as a drizzle.
     */
    public static float fallCurve(float inten) {
        return 0.30f + 0.38f * inten + 0.55f * (float) Math.pow(inten, 2.5);
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        LivingRainConfig cfg = LivingRainConfig.get();
        if (level == null || player == null || !cfg.enabled || mc.isPaused()) return;

        clock++;
        lastSpawned = 0;
        // Minecraft silently throws particles away when its own queue is full. Notice that, and stay below it.
        int discarded = RainParticle.prune(clock);
        if (discarded > 2) {
            discardedTotal += discarded;
            autoCap = Math.max(500, RainParticle.live());
            if (!limitLogged) {
                limitLogged = true;
                org.slf4j.LoggerFactory.getLogger("livingrain").warn(
                        "[livingrain] Minecraft is discarding particles at about {}. Limiting rain to that automatically.", autoCap);
            }
        } else if (autoCap != Integer.MAX_VALUE) {
            autoCap += 2;                                  // slowly test whether more fits again
            if (autoCap >= cfg.maxParticles) autoCap = Integer.MAX_VALUE;
        }

        float inten = StormDirector.intensity();
        Vec3 eye = player.getEyePosition();
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        // Minecraft: yaw 0 = looking south, pitch > 0 = looking down. right = (-cos y, 0, -sin y),
        // up = right x look = (-sin y * sin p, cos p, cos y * sin p).
        double yaw = Math.toRadians(player.getYRot());
        double pitch = Math.toRadians(player.getXRot());
        float sy = (float) Math.sin(yaw), cy = (float) Math.cos(yaw);
        float sp = (float) Math.sin(pitch), cp = (float) Math.cos(pitch);
        rightX = -cy;
        rightZ = -sy;
        upX = -sy * sp;
        upY = cp;
        upZ = cy * sp;
        radiusNow = cfg.radius;
        flipSlant = cfg.flipSlant;
        brightness = cfg.rainBrightness;
        // Heavy-rain effects fade in between light rain (0.55) and heavy rain (0.75); drizzle and light rain stay plain particles.
        float ht = Math.max(0f, Math.min(1f, (inten - 0.55f) / 0.20f));
        heavyFactor = ht * ht * (3f - 2f * ht);
        backlightAmount = cfg.backlight * heavyFactor;
        if (cfg.backlight > 0f && heavyFactor > 0f) SunMoon.update(level);
        Flash.update(level);
        flashBoost = cfg.lightningFlash ? cfg.flashStrength * 3f * Flash.level : 0f;
        splashes = cfg.splashes;
        double kill = cfg.radius + 20.0;
        killDistSq = kill * kill;

        float fall = fallCurve(inten) * cfg.fallSpeed;
        lastFall = fall * StormDirector.speedMul;
        // The wind push is scaled with the fall speed, so the slant ANGLE looks the same however fast the rain falls.
        float windScale = fall / (0.30f + 0.38f * Math.max(0.02f, inten));
        float[] w = WindSource.get(cfg);
        windX = w[0] * cfg.rainWindInfluence * windScale;
        windZ = w[1] * cfg.rainWindInfluence * windScale;
        windTurb = w[2];
        if (!Float.isFinite(windX) || !Float.isFinite(windZ)) { windX = 0f; windZ = 0f; }
        if (!Float.isFinite(windTurb)) windTurb = 0f;
        // Even in the strongest gust, rain never goes fully sideways.
        float maxH = 1.1f * fall;
        float mag = (float) Math.sqrt(windX * windX + windZ * windZ);
        if (mag > maxH && mag > 0f) {
            windX *= maxH / mag;
            windZ *= maxH / mag;
        }
        float fallNow = Math.max(0.05f, fall * StormDirector.speedMul);
        slantX = windX / fallNow;
        slantZ = windZ / fallNow;

        if (!Float.isFinite(inten) || !cfg.particleRain || inten < 0.02f) { acc = 0f; return; }

        RandomSource rnd = level.getRandom();
        int seaLevel = level.getSeaLevel();

        // ---- how many candidate drops this tick ----
        // flux = density x area x fall speed, so a drop's lifetime does not change how dense the rain looks.
        float dens = 1.6f * (float) Math.pow(inten, 1.6) * StormDirector.densityMul * cfg.density;
        float r = cfg.radius;
        float vyRain = fall * StormDirector.speedMul;
        float vySnow = 0.07f * (0.6f + 0.2f * cfg.fallSpeed);       // snow is slow and does not scale like rain
        float perTick = 0.12f * dens * (float) (Math.PI * r * r) * vyRain;
        if (!Float.isFinite(acc)) acc = 0f;
        // Rain curtains: patches of thicker and thinner rain drifting with the wind. Candidates are raised by (1 + amp) and then
        // thinned by the local density, so the average amount of rain stays what the Density setting says.
        float amp = cfg.curtains ? Math.min(0.9f, 0.7f * cfg.curtainStrength * heavyFactor) : 0f;
        // Debug view: show the curtains at full strength whatever the rain, so you can see what they do.
        if (cfg.curtainDebug) amp = Math.min(0.9f, 0.7f * Math.max(0.5f, cfg.curtainStrength));
        lastCurtainAmp = amp;
        Curtains.advance(windX, windZ);

        // Splashes near the player per tick: few in a drizzle, many in a downpour, the most in a thunderstorm.
        // (Only drops landing within 12 blocks splash, so the chance is the target divided by how many land there.)
        float nearLandings = perTick * (144f / (r * r));
        lastSplashTarget = (10f + 140f * inten * inten) * (1f + 0.5f * StormDirector.thunderBlend()) * cfg.splashAmount;
        splashChance = Math.min(0.7f, lastSplashTarget / Math.max(1f, nearLandings));
        // With rain sheets on, fewer individual drops are needed (the sheets supply much of the look): saves a lot of CPU.
        float sheetShare = (cfg.sheets && !cfg.seeThroughWindows && cfg.sheetAmount > 0f) ? heavyFactor : 0f;
        lastSheetShare = sheetShare;
        float dropFactor = 1f - 0.45f * Math.min(1f, cfg.sheetAmount) * sheetShare;
        acc += Float.isFinite(perTick) ? perTick * (1f + amp) * dropFactor : 0f;
        int count = (int) acc;
        acc -= count;
        count = Math.min(count, 30000);
        // The particle limit and the per-tick limit apply to particles actually CREATED, not to candidates: candidates are
        // thinned afterwards (curtains, roofs), and limiting them first would quietly lose rain.
        int cap = Math.min(cfg.maxParticles, autoCap);
        int room = Math.max(0, cap - RainParticle.live());

        // Motion blur: longer streaks, spread over more length, so each one is a little fainter.
        float stretch = 0.55f + 0.9f * cfg.motionBlur;
        float blurAlpha = (float) Math.min(1.35, 1.0 / Math.sqrt(stretch));
        // Faster drops are smeared over a longer streak (that is what motion blur is), up to twice as long.
        float lenSpeed = Math.max(0.85f, Math.min(2.0f, (float) Math.sqrt(vyRain / 1.2f)));
        lastSizeMult = sizeMultiplier(inten, cfg);
        net.minecraft.core.particles.SimpleParticleType rainType = cfg.seeThroughWindows ? LivingRainMod.RAIN_SOLID : LivingRainMod.RAIN;
        float size = (0.14f + 0.36f * inten) * lastSizeMult * StormDirector.sizeMul * stretch * lenSpeed;
        // Each drop is fainter the more drops there are (so a downpour is not a white-out), but a drizzle must stay visible:
        // it has the fewest drops, so each one has to show.
        float baseA = inten <= 0.25f ? 0.80f
                : inten >= 0.75f ? 0.4675f + (0.55f - 0.4675f) * (inten - 0.75f) / 0.25f
                : 0.80f - (0.80f - 0.4675f) * (inten - 0.25f) / 0.50f;
        float alpha = baseA * cfg.opacity * blurAlpha;
        if (cfg.seeThroughWindows) {
            // The opaque layer cannot blend: alpha only decides how much of the streak's edge is cut away. Keep it high
            // enough that even a drizzle leaves a visible thin line.
            alpha = (0.16f + 0.34f * cfg.opacity) * (0.85f + 0.35f * inten);
        }
        lastGlintShare = (cfg.glints && !cfg.seeThroughWindows) ? cfg.glintAmount * heavyFactor : 0f;
        double height = cfg.spawnHeight;

        int sheetsMade = 0;
        // ---- rain sheets: big pictures of many thin streaks (the "tons of thin rain" look) ----
        if (sheetShare > 0.01f && r > 6f) {
            // How many sheets: none in light rain, about half as many in heavy rain as in torrential, a few more in thunder.
            float sheetInten = Math.max(0f, Math.min(1f, (inten - 0.45f) / 0.55f));
            float dSheet = 0.10f * cfg.sheetAmount * sheetShare * sheetInten
                    * (1f + 0.25f * StormDirector.thunderBlend()) * StormDirector.densityMul;
            float ampS = Math.min(0.95f, amp * 1.35f);               // sheets show the curtains more strongly
            if (!Float.isFinite(accSheet)) accSheet = 0f;
            accSheet += dSheet * (float) (Math.PI * r * r) * vyRain * (1f + ampS);
            int sc = (int) accSheet;
            accSheet -= sc;
            int sheetBudget = Math.min(600, room);
            float sheetAlpha = Math.min(0.8f, 0.36f * cfg.opacity / 0.45f);

            for (int n = 0; n < sc && sheetsMade < sheetBudget; n++) {
                double ang = rnd.nextDouble() * Math.PI * 2.0;
                double dist = 5.0 + (r - 5.0) * Math.sqrt(rnd.nextDouble());     // never right next to the player
                double tx = camX + Math.cos(ang) * dist;
                double tz = camZ + Math.sin(ang) * dist;
                double half = (1.7 + 1.0 * rnd.nextDouble()) * cfg.sheetSize;
                double h = 1.0 + rnd.nextDouble() * height;
                double ySpawn = camY + h;
                float vy = vyRain * (1f + (rnd.nextFloat() * 2f - 1f) * cfg.fallSpeedVar);
                double t = h / Math.max(0.05f, vy);
                double sx = tx - windX * t;
                double sz = tz - windZ * t;

                // The picture is wide: every column across it must be open sky, with room to fade out above the roofs.
                double o = half * 0.6;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx), Mth.floor(sz));
                int topMax = Math.max(Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx + o), Mth.floor(sz))),
                        Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx - o), Mth.floor(sz)),
                        Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx), Mth.floor(sz + o)),
                                level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx), Mth.floor(sz - o)))));
                if (ySpawn < topMax + 3.0) continue;

                float curtain = ampS > 0f ? Curtains.multiplier(tx, tz, ampS) : 1f;
                if (ampS > 0f && rnd.nextFloat() > curtain / (1f + ampS)) continue;

                P.set(Mth.floor(sx), top, Mth.floor(sz));
                if (level.getBiome(P).value().getPrecipitationAt(P, seaLevel) != Biome.Precipitation.RAIN) continue;

                RainParticle.pendingSize = (float) half;
                RainParticle.pendingAlpha = sheetAlpha * (0.8f + rnd.nextFloat() * 0.4f);
                RainParticle.pendingWindMul = 0.9f + rnd.nextFloat() * 0.2f;
                RainParticle.pendingTint = cfg.curtainDebug ? debugTint(curtain) : null;
                level.addParticle(LivingRainMod.RAIN_SHEET[rnd.nextInt(LivingRainMod.RAIN_SHEET.length)], sx, ySpawn, sz, windX, -vy, windZ);
                RainParticle.pendingTint = null;
                lastSpawned++;
                sheetsMade++;
            }
        } else {
            accSheet = 0f;
        }
        room -= sheetsMade;


        int dropBudget = Math.min(12000, room);
        int made = 0;

        for (int n = 0; n < count && made < dropBudget; n++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double dist = r * Math.sqrt(rnd.nextDouble());
            double tx = camX + Math.cos(ang) * dist;
            double tz = camZ + Math.sin(ang) * dist;
            double h = 1.0 + rnd.nextDouble() * height;
            double ySpawn = camY + h;

            float vy = vyRain * (1f + (rnd.nextFloat() * 2f - 1f) * cfg.fallSpeedVar);
            // Start upwind, so the slanted drops cross the player's eye level where we aimed.
            double t = h / Math.max(0.05f, vy);
            double sx = tx - windX * t;
            double sz = tz - windZ * t;
            int bx = Mth.floor(sx);
            int bz = Mth.floor(sz);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            if (ySpawn < top + 0.5) continue;                    // that column is roofed over

            // thin out by the curtain pattern at the spot where the drop will pass the player
            float curtain = amp > 0f ? Curtains.multiplier(tx, tz, amp) : 1f;
            if (amp > 0f && rnd.nextFloat() > curtain / (1f + amp)) continue;

            P.set(bx, top, bz);
            Biome.Precipitation precip = level.getBiome(P).value().getPrecipitationAt(P, seaLevel);
            if (precip == Biome.Precipitation.NONE) continue;

            if (precip == Biome.Precipitation.SNOW) {
                if (!cfg.snow || rnd.nextFloat() > vySnow / vyRain) continue;
                RainParticle.pendingSize = 0.035f * (0.7f + rnd.nextFloat() * 0.8f) * cfg.dropSize;
                RainParticle.pendingAlpha = 0.85f * cfg.opacity;
                RainParticle.pendingWindMul = 0.8f + rnd.nextFloat() * 0.4f;
                double vs = vySnow * (0.7f + rnd.nextFloat() * 0.6f);
                level.addParticle(LivingRainMod.SNOW, sx, ySpawn, sz, windX, -vs, windZ);
                lastSpawned++;
                made++;
            } else {
                RainParticle.pendingSize = size * (0.85f + rnd.nextFloat() * 0.3f);
                RainParticle.pendingAlpha = alpha * (0.8f + rnd.nextFloat() * 0.4f);
                RainParticle.pendingWindMul = 0.9f + rnd.nextFloat() * 0.2f;
                RainParticle.pendingTint = cfg.curtainDebug ? debugTint(curtain) : null;
                net.minecraft.core.particles.SimpleParticleType kind = rainType;
                if (lastGlintShare > 0f && rnd.nextFloat() < lastGlintShare) {
                    kind = rnd.nextBoolean() ? LivingRainMod.RAIN_GLINT_A : LivingRainMod.RAIN_GLINT_B;
                }
                level.addParticle(kind, sx, ySpawn, sz, windX, -vy, windZ);
                RainParticle.pendingTint = null;
                lastSpawned++;
                made++;
            }
        }

        // If a storm is running but not a single particle exists for 5 seconds, say exactly why in the log.
        if (lastSpawned == 0 && RainParticle.live() == 0 && inten > 0.3f) {
            stallTicks++;
            if (stallTicks == 100 || stallTicks % 1200 == 0) {
                org.slf4j.LoggerFactory.getLogger("livingrain").warn(
                        "[livingrain] Storm is active but no rain particles exist. intensity={} perTick={} candidates={} cap={} autoCap={} "
                      + "wind=({}, {}) radius={} paused={} Please send this line to the mod author.",
                        inten, perTick, count, cap, autoCap, windX, windZ, cfg.radius, mc.isPaused());
            }
        } else {
            stallTicks = 0;
        }

        playSounds(level, cfg, rnd, inten, seaLevel);
    }

    /** Curtain debug colours: red = thick rain, blue = thin rain, white = about average. */
    private static float[] debugTint(float multiplier) {
        if (multiplier > 1.25f) return new float[] { 1.0f, 0.25f, 0.25f };
        if (multiplier < 0.75f) return new float[] { 0.3f, 0.5f, 1.0f };
        return new float[] { 1.0f, 1.0f, 1.0f };
    }

    /** Vanilla's rain sounds come from the code we switch off, so this does the same job: sounds from nearby open ground. */
    private static void playSounds(ClientLevel level, LivingRainConfig cfg, RandomSource rnd, float inten, int seaLevel) {
        if (cfg.rainVolume <= 0f || inten < 0.04f) return;
        if (rnd.nextFloat() > 0.10f + 0.40f * inten) return;

        int sx = Mth.floor(camX) + rnd.nextInt(21) - 10;
        int sz = Mth.floor(camZ) + rnd.nextInt(21) - 10;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
        P.set(sx, top, sz);
        Biome.Precipitation precip = level.getBiome(P).value().getPrecipitationAt(P, seaLevel);
        if (precip != Biome.Precipitation.RAIN) return;          // none in dry biomes, silence for snow

        boolean above = top > camY + 1.0
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(camX), Mth.floor(camZ)) > Mth.floor(camY);
        float scale = cfg.rainVolume * (0.35f + 0.65f * inten);
        SoundEvent ev = above ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN;
        float vol = (above ? 0.1f : 0.2f) * scale;
        float pitch = above ? 0.5f : 1.0f;
        level.playLocalSound(sx + 0.5, top, sz + 0.5, ev, SoundSource.WEATHER, vol, pitch, false);
    }
}
