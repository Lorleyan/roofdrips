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

    /** Counts spawner ticks. Particles stamp it so we can tell which ones Minecraft has stopped ticking. */
    public static long clock;
    /** Particles created on the last tick, and the lowest limit Minecraft has been seen to enforce (for /livingrain status). */
    public static int lastSpawned;
    public static int autoCap = Integer.MAX_VALUE;
    public static int discardedTotal;
    private static boolean limitLogged;
    private static int stallTicks;

    private static float acc;
    private static final BlockPos.MutableBlockPos P = new BlockPos.MutableBlockPos();

    private RainSpawner() { }

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
        splashes = cfg.splashes;
        double kill = cfg.radius + 20.0;
        killDistSq = kill * kill;

        float[] w = WindSource.get(cfg);
        windX = w[0] * cfg.rainWindInfluence;
        windZ = w[1] * cfg.rainWindInfluence;
        windTurb = w[2];
        if (!Float.isFinite(windX) || !Float.isFinite(windZ)) { windX = 0f; windZ = 0f; }
        if (!Float.isFinite(windTurb)) windTurb = 0f;
        // Even in the strongest gust, rain never goes fully sideways.
        float fall = (0.30f + 0.38f * inten) * cfg.fallSpeed;
        float maxH = 1.1f * fall;
        float mag = (float) Math.sqrt(windX * windX + windZ * windZ);
        if (mag > maxH && mag > 0f) {
            windX *= maxH / mag;
            windZ *= maxH / mag;
        }

        if (!Float.isFinite(inten) || !cfg.particleRain || inten < 0.02f) { acc = 0f; return; }

        RandomSource rnd = level.getRandom();
        int seaLevel = level.getSeaLevel();

        // ---- how many candidate drops this tick ----
        // flux = density x area x fall speed, so a drop's lifetime does not change how dense the rain looks.
        float dens = 1.6f * (float) Math.pow(inten, 1.6) * StormDirector.densityMul * cfg.density;
        float r = cfg.radius;
        float vyRain = (0.30f + 0.38f * inten) * cfg.fallSpeed * StormDirector.speedMul;
        float vySnow = 0.07f * cfg.fallSpeed;
        float perTick = 0.12f * dens * (float) (Math.PI * r * r) * vyRain;
        if (!Float.isFinite(acc)) acc = 0f;
        acc += Float.isFinite(perTick) ? perTick : 0f;
        int count = (int) acc;
        acc -= count;
        count = Math.min(count, 1500);
        int cap = Math.min(cfg.maxParticles, autoCap);
        count = Math.min(count, Math.max(0, cap - RainParticle.live()));

        // Motion blur: longer streaks, spread over more length, so each one is a little fainter.
        float stretch = 0.55f + 0.9f * cfg.motionBlur;
        float blurAlpha = (float) Math.min(1.35, 1.0 / Math.sqrt(stretch));
        float size = (0.14f + 0.36f * inten) * cfg.dropSize * StormDirector.sizeMul * stretch;
        float alpha = (0.22f + 0.33f * inten) * cfg.opacity * blurAlpha;
        double height = cfg.spawnHeight;

        for (int n = 0; n < count; n++) {
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
            } else {
                RainParticle.pendingSize = size * (0.85f + rnd.nextFloat() * 0.3f);
                RainParticle.pendingAlpha = alpha * (0.8f + rnd.nextFloat() * 0.4f);
                RainParticle.pendingWindMul = 0.9f + rnd.nextFloat() * 0.2f;
                level.addParticle(LivingRainMod.RAIN, sx, ySpawn, sz, windX, -vy, windZ);
                lastSpawned++;
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
