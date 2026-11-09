package com.example.eavesdrip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Client side: finds overhangs / leaves while it rains and spawns water drips, streams and streaks. */
public class EavesDripClient implements ClientModInitializer {

    private static final Direction[] HORIZ = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    private static final int EDGE_TTL = 600;
    private static final int MAX_EDGES = 2500;
    private static final int V_RANGE = 20;
    private static final int MAX_STREAKS = 500;
    private static final double CULL_COS = -0.35;
    private static final double NEAR_NO_CULL = 6.0;
    private static final double SOUND_RANGE = 16.0;

    /** A rim block that water can run off. Roof rims use bits 0-3 (open sides); leaves also use bit 4 (underside). */
    private static final class Edge {
        final boolean leaf;
        final int seed;
        int mask;
        long seen;
        final int[] burst = new int[4];
        final int[] gap = new int[4];
        final int[] snd = new int[4];
        Edge(boolean leaf, int seed) { this.leaf = leaf; this.seed = seed; }
    }

    /** A streak sliding down the face of a roof block. */
    private static final class Streak {
        double x, y, z, bottom, v;
        int trail;
    }

    private final Map<Long, Edge> roofEdges = new HashMap<>();
    private final Map<Long, Edge> leafEdges = new HashMap<>();
    private final List<Streak> streaks = new ArrayList<>();

    private float wetness = 0f;
    private long tickCount = 0;
    private float soundTokens = 0f;

    // per-tick context
    private ClientLevel level;
    private RoofDripsConfig cfg;
    private RandomSource rnd;
    private Vec3 eye, look;
    private int roofBudget, leafBudget;

    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(RoofDripsMod.DROP, WaterParticle.DropProvider::new);
        ParticleProviderRegistry.getInstance().register(RoofDripsMod.DOT, WaterParticle.DotProvider::new);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void clearAll() {
        roofEdges.clear();
        leafEdges.clear();
        streaks.clear();
    }

    private void onTick(Minecraft mc) {
        level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) { clearAll(); return; }
        if (mc.isPaused()) return;

        cfg = RoofDripsConfig.get();
        if (!cfg.enabled) { clearAll(); return; }

        float rain = level.getRainLevel(1.0f);
        float fade = cfg.afterRainSeconds <= 0 ? 1f : 1f / (20f * cfg.afterRainSeconds);
        wetness = rain > wetness ? rain : Math.max(rain, wetness - fade);
        if (wetness <= 0.02f) { clearAll(); return; }

        tickCount++;
        rnd = level.getRandom();
        eye = player.getEyePosition();
        look = player.getLookAngle();
        roofBudget = cfg.maxParticles;
        leafBudget = cfg.treeMaxParticles;
        soundTokens = Math.min(4f, soundTokens + cfg.maxSoundsPerSecond / 20f);

        if (rain > 0.02f) {
            discoverRoofs(player);
            if (cfg.treesEnabled) discoverLeaves(player);
        }
        emitRoofs();
        updateStreaks();
        if (cfg.treesEnabled) emitLeaves(); else leafEdges.clear();
    }

    // ================= Discovery =================

    private static boolean isFree(ClientLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    private boolean rainsHere(BlockPos pos) {
        Biome biome = level.getBiome(pos).value();
        return biome.getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.RAIN;
    }

    private static int seedOf(long key) { return (int) ((key * 0x9E3779B97F4A7C15L) >>> 32); }

    private static float hash(int seed, int salt) {
        int h = seed ^ (salt * 0x9E3779B1);
        h ^= h >>> 16; h *= 0x85EBCA6B; h ^= h >>> 13; h *= 0xC2B2AE35; h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private void discoverRoofs(LocalPlayer player) {
        int px = player.getBlockX(), pz = player.getBlockZ(), py = player.getBlockY();
        int R = cfg.radius;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < cfg.scanSpeed && roofEdges.size() < MAX_EDGES; i++) {
            int x = px + rnd.nextInt(R * 2 + 1) - R;
            int z = pz + rnd.nextInt(R * 2 + 1) - R;
            int dx = x - px, dz = z - pz;
            if (dx * dx + dz * dz > R * R) continue;

            pos.set(x, 0, z);
            if (!level.hasChunkAt(pos)) continue;

            int roofY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (Math.abs(roofY - py) > V_RANGE) continue;

            pos.set(x, roofY, z);
            BlockState roof = level.getBlockState(pos);
            if (!roof.getFluidState().isEmpty()) continue;
            if (!roof.isFaceSturdy(level, pos, Direction.DOWN)) continue;
            if (!isFree(level, pos.below())) continue;          // not an overhang (wall / stacked blocks)
            if (!rainsHere(pos)) continue;

            int mask = 0;
            for (int d = 0; d < 4; d++) {
                BlockPos n = pos.relative(HORIZ[d]);
                if (isFree(level, n) && isFree(level, n.below())) mask |= 1 << d;
            }
            if (mask == 0) continue;

            long key = pos.asLong();
            Edge e = roofEdges.get(key);
            if (e == null) { e = new Edge(false, seedOf(key)); roofEdges.put(key, e); }
            e.mask = mask;
            e.seen = tickCount;
        }
    }

    private void discoverLeaves(LocalPlayer player) {
        int px = player.getBlockX(), pz = player.getBlockZ(), py = player.getBlockY();
        int R = cfg.treeRadius;
        int samples = Math.max(20, cfg.scanSpeed / 2);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < samples && leafEdges.size() < MAX_EDGES; i++) {
            int x = px + rnd.nextInt(R * 2 + 1) - R;
            int z = pz + rnd.nextInt(R * 2 + 1) - R;
            int dx = x - px, dz = z - pz;
            if (dx * dx + dz * dz > R * R) continue;

            pos.set(x, 0, z);
            if (!level.hasChunkAt(pos)) continue;

            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            for (int k = 0; k < 6; k++) {
                int y = top - k;
                if (Math.abs(y - py) > V_RANGE) break;
                pos.set(x, y, z);
                if (!level.getBlockState(pos).is(BlockTags.LEAVES)) break;
                if (k == 0 && !rainsHere(pos)) break;

                int mask = 0;
                if (isFree(level, pos.below())) mask |= 16;
                for (int d = 0; d < 4; d++) {
                    BlockPos n = pos.relative(HORIZ[d]);
                    if (isFree(level, n) && isFree(level, n.below())) mask |= 1 << d;
                }
                if (mask == 0) continue;

                long key = pos.asLong();
                Edge e = leafEdges.get(key);
                if (e == null) { e = new Edge(true, seedOf(key)); leafEdges.put(key, e); }
                e.mask = mask;
                e.seen = tickCount;
            }
        }
    }

    // ================= Emission =================

    private boolean isBehind(double tx, double ty, double tz, double distSqr) {
        if (distSqr <= NEAR_NO_CULL * NEAR_NO_CULL) return false;
        double dot = tx * look.x + ty * look.y + tz * look.z;
        return dot < 0 && (dot * dot) > (CULL_COS * CULL_COS) * distSqr;
    }

    private int roll(float x) {
        int n = (int) x;
        if (rnd.nextFloat() < x - n) n++;
        return n;
    }

    private void emitRoofs() {
        double maxSqr = (double) cfg.radius * cfg.radius;
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();

        Iterator<Map.Entry<Long, Edge>> it = roofEdges.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Edge> entry = it.next();
            Edge e = entry.getValue();
            if (tickCount - e.seen > EDGE_TTL) { it.remove(); continue; }

            BlockPos rim = BlockPos.of(entry.getKey());
            double tx = rim.getX() + 0.5 - eye.x, ty = rim.getY() + 0.5 - eye.y, tz = rim.getZ() + 0.5 - eye.z;
            double distSqr = tx * tx + ty * ty + tz * tz;
            if (distSqr > maxSqr + 64) { it.remove(); continue; }
            if (distSqr > maxSqr) continue;
            if (isBehind(tx, ty, tz, distSqr)) continue;

            tmp.set(rim);
            if (!level.getBlockState(tmp).isFaceSturdy(level, tmp, Direction.DOWN)) { it.remove(); continue; }

            double dist = Math.sqrt(distSqr);
            float falloff = (float) (1.0 - 0.6 * dist / cfg.radius);
            float dens = cfg.density * wetness * falloff;

            for (int d = 0; d < 4; d++) {
                if ((e.mask & (1 << d)) == 0) continue;
                processSide(e, rim, d, dens, dist);
            }
        }
    }

    private void processSide(Edge e, BlockPos rim, int d, float dens, double dist) {
        boolean stream = hash(e.seed, d * 7 + 1) < cfg.streamShare;   // this spot streams, or just drips
        float rateMul = 0.5f + hash(e.seed, d * 7 + 2);                // each spot has its own flow rate

        if (stream) {
            if (e.burst[d] > 0) {
                e.burst[d]--;
                int n = roll(0.85f * dens * rateMul);
                for (int i = 0; i < n && roofBudget > 0; i++) { emitLipDrop(rim, d); roofBudget--; }
                if (e.snd[d]-- <= 0) {
                    e.snd[d] = 30 + rnd.nextInt(20);
                    playSound(rim.getX() + 0.5, rim.getY() + 0.2, rim.getZ() + 0.5, true, dist);
                }
                if (e.burst[d] == 0) {
                    e.gap[d] = Math.max(2, Math.round(18f / Math.max(0.3f, dens) * (0.5f + rnd.nextFloat())));
                }
            } else if (e.gap[d] > 0) {
                e.gap[d]--;
            } else {
                float v = 1f + (rnd.nextFloat() * 2f - 1f) * cfg.streamLengthVar;
                e.burst[d] = Math.max(3, Math.round(cfg.streamLength * 7f * v));
            }
        } else if (roofBudget > 0 && rnd.nextFloat() < 0.035f * dens * rateMul) {
            emitLipDrop(rim, d);
            roofBudget--;
            if (rnd.nextFloat() < 0.35f) {
                playSound(rim.getX() + 0.5, rim.getY() + 0.2, rim.getZ() + 0.5, false, dist);
            }
        }

        if (cfg.faceStreakAmount > 0f && dist < cfg.radius * 0.6 && streaks.size() < MAX_STREAKS
                && rnd.nextFloat() < 0.006f * cfg.faceStreakAmount * wetness * rateMul) {
            spawnStreak(rim, d);
        }
    }

    private void emitLipDrop(BlockPos rim, int d) {
        Direction dir = HORIZ[d];
        double along = (rnd.nextDouble() - 0.5) * 0.8;
        double px = rim.getX() + 0.5 + dir.getStepX() * 0.53 + (dir.getAxis() == Direction.Axis.X ? 0 : along);
        double pz = rim.getZ() + 0.5 + dir.getStepZ() * 0.53 + (dir.getAxis() == Direction.Axis.Z ? 0 : along);
        spawnDrop(px, rim.getY() - 0.02, pz);
    }

    private void spawnDrop(double x, double y, double z) {
        float size = 0.028f * cfg.dropSize * (0.8f + rnd.nextFloat() * 0.5f);
        float speed = cfg.fallSpeed * (1f + (rnd.nextFloat() * 2f - 1f) * cfg.fallSpeedVar);
        level.addParticle(RoofDripsMod.DROP, x, y, z, size, Math.max(0.2f, speed), cfg.opacity);
    }

    // ---- Streaks running down the face of a roof block ----
    private void spawnStreak(BlockPos rim, int d) {
        Direction dir = HORIZ[d];
        double lateral = (rnd.nextDouble() - 0.5) * 0.84;
        Streak s = new Streak();
        s.x = rim.getX() + 0.5 + dir.getStepX() * 0.51 + (dir.getAxis() == Direction.Axis.X ? 0 : lateral);
        s.z = rim.getZ() + 0.5 + dir.getStepZ() * 0.51 + (dir.getAxis() == Direction.Axis.Z ? 0 : lateral);
        s.y = rim.getY() + 0.97;
        s.bottom = rim.getY() + 0.03;
        float sv = 1f + (rnd.nextFloat() * 2f - 1f) * cfg.faceStreakSpeedVar;
        s.v = Math.max(0.008, 0.04 * cfg.faceStreakSpeed * sv);
        float lv = 1f + (rnd.nextFloat() * 2f - 1f) * cfg.faceStreakLengthVar;
        double trailBlocks = Math.max(0.1, 0.9 * cfg.faceStreakLength * lv);
        s.trail = Math.max(2, (int) Math.round(trailBlocks / s.v));
        streaks.add(s);
    }

    private void updateStreaks() {
        Iterator<Streak> it = streaks.iterator();
        while (it.hasNext()) {
            Streak s = it.next();
            s.y -= s.v;
            if (s.y <= s.bottom) {
                if (roofBudget > 0 && rnd.nextFloat() < 0.7f) { spawnDrop(s.x, s.bottom - 0.02, s.z); roofBudget--; }
                it.remove();
                continue;
            }
            if (roofBudget > 0) {
                level.addParticle(RoofDripsMod.DOT, s.x, s.y, s.z,
                        0.022f * cfg.dropSize, s.trail, cfg.opacity * 0.85f);
                roofBudget--;
            }
        }
    }

    // ---- Leaves: drops only, never streaks ----
    private void emitLeaves() {
        double maxSqr = (double) cfg.treeRadius * cfg.treeRadius;
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();

        Iterator<Map.Entry<Long, Edge>> it = leafEdges.entrySet().iterator();
        while (it.hasNext() && leafBudget > 0) {
            Map.Entry<Long, Edge> entry = it.next();
            Edge e = entry.getValue();
            if (tickCount - e.seen > EDGE_TTL) { it.remove(); continue; }

            BlockPos rim = BlockPos.of(entry.getKey());
            double tx = rim.getX() + 0.5 - eye.x, ty = rim.getY() + 0.5 - eye.y, tz = rim.getZ() + 0.5 - eye.z;
            double distSqr = tx * tx + ty * ty + tz * tz;
            if (distSqr > maxSqr + 64) { it.remove(); continue; }
            if (distSqr > maxSqr) continue;
            if (isBehind(tx, ty, tz, distSqr)) continue;

            tmp.set(rim);
            if (!level.getBlockState(tmp).is(BlockTags.LEAVES)) { it.remove(); continue; }

            float falloff = (float) (1.0 - 0.6 * Math.sqrt(distSqr) / cfg.treeRadius);
            float rateMul = 0.5f + hash(e.seed, 3);
            if (rnd.nextFloat() >= 0.03f * cfg.treeDensity * wetness * falloff * rateMul) continue;

            boolean under = (e.mask & 16) != 0;
            int sides = e.mask & 15;
            if (under && (sides == 0 || rnd.nextBoolean())) {
                spawnDrop(rim.getX() + 0.1 + rnd.nextDouble() * 0.8, rim.getY() + 0.02, rim.getZ() + 0.1 + rnd.nextDouble() * 0.8);
            } else {
                int d;
                do { d = rnd.nextInt(4); } while ((sides & (1 << d)) == 0);
                Direction dir = HORIZ[d];
                double along = 0.1 + rnd.nextDouble() * 0.8;
                double px = rim.getX() + (dir.getAxis() == Direction.Axis.X ? 0.5 + dir.getStepX() * 0.53 : along);
                double pz = rim.getZ() + (dir.getAxis() == Direction.Axis.Z ? 0.5 + dir.getStepZ() * 0.53 : along);
                spawnDrop(px, rim.getY() + 0.1 + rnd.nextDouble() * 0.7, pz);
            }
            leafBudget--;
        }
    }

    // ================= Sound =================
    private void playSound(double x, double y, double z, boolean stream, double dist) {
        if (!cfg.soundsEnabled || soundTokens < 1f || dist > SOUND_RANGE) return;
        float vol = stream ? cfg.streamVolume : cfg.dripVolume;
        if (vol <= 0.001f) return;
        soundTokens -= 1f;
        SoundEvent ev = stream ? SoundEvents.WATER_AMBIENT : SoundEvents.POINTED_DRIPSTONE_DRIP_WATER;
        float pitch = stream ? 1.0f + rnd.nextFloat() * 0.4f : 0.85f + rnd.nextFloat() * 0.5f;
        level.playLocalSound(x, y, z, ev, SoundSource.WEATHER, vol * (0.5f + 0.5f * wetness), pitch, false);
    }
}
