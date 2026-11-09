package com.example.eavesdrip;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only mod: water streams off roof/overhang rims while it rains.
 * Mojang mappings (Minecraft ships unobfuscated from 26.1).
 */
public class EavesDripClient implements ClientModInitializer {

    // ---- Tunables ----
    private static final int RADIUS = 28;               // horizontal radius around player
    private static final int V_RANGE = 20;              // vertical range around player
    private static final int SAMPLES_PER_TICK = 150;    // random columns tested per tick
    private static final int EDGE_TTL = 600;            // ticks before an unconfirmed rim is dropped
    private static final int MAX_EDGES = 2000;
    private static final int MAX_PARTICLES_PER_TICK = 80;
    private static final double BASE_CHANCE = 0.30;     // per open side, per tick, at full rain
    private static final double CULL_COS = -0.35;       // skip rims further behind than this
    private static final double NEAR_NO_CULL = 6.0;     // never cull within this many blocks
    private static final float WETNESS_FADE = 0.0025f;  // per tick (~20s fade after rain)

    private static final Direction[] HORIZ = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static final class Edge {
        int mask;   // bit i set => HORIZ[i] side is an open drip side
        long seen;  // game tick last confirmed
    }

    private final Map<Long, Edge> edges = new HashMap<>();
    private float wetness = 0f;
    private long tickCount = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) {
            edges.clear();
            return;
        }
        tickCount++;

        float rain = level.getRainLevel(1.0f);
        wetness = rain > wetness ? rain : Math.max(rain, wetness - WETNESS_FADE);
        if (wetness <= 0.02f) {
            if (!edges.isEmpty()) edges.clear();
            return;
        }

        if (rain > 0.02f) discover(level, player);
        emit(level, player);
    }

    // ---- Discovery: random column sampling ----
    private void discover(ClientLevel level, LocalPlayer player) {
        RandomSource rnd = level.getRandom();
        int px = player.getBlockX(), pz = player.getBlockZ(), py = player.getBlockY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < SAMPLES_PER_TICK && edges.size() < MAX_EDGES; i++) {
            int x = px + rnd.nextInt(RADIUS * 2 + 1) - RADIUS;
            int z = pz + rnd.nextInt(RADIUS * 2 + 1) - RADIUS;
            int dx = x - px, dz = z - pz;
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;

            pos.set(x, 0, z);
            if (!level.hasChunkAt(pos)) continue;

            // Highest rain-blocking block in this column = the only block rain hits.
            int roofY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            if (Math.abs(roofY - py) > V_RANGE) continue;

            pos.set(x, roofY, z);
            BlockState roof = level.getBlockState(pos);
            if (!roof.getFluidState().isEmpty()) continue;
            if (!roof.isFaceSturdy(level, pos, Direction.DOWN)) continue;

            // Overhang test: nothing solid directly beneath (rejects walls / stacked blocks).
            if (!isFree(level, pos.below())) continue;

            // Only rain-type precipitation (no snow / dry biomes).
            Biome biome = level.getBiome(pos).value();
            if (biome.getPrecipitationAt(pos, level.getSeaLevel()) != Biome.Precipitation.RAIN) continue;

            int mask = 0;
            for (int d = 0; d < 4; d++) {
                BlockPos n = pos.relative(HORIZ[d]);
                // Neighbor open AND open below it => water can drop off freely.
                if (isFree(level, n) && isFree(level, n.below())) mask |= 1 << d;
            }
            if (mask == 0) continue;

            long key = pos.asLong();
            Edge e = edges.get(key);
            if (e == null) {
                e = new Edge();
                edges.put(key, e);
            }
            e.mask = mask;
            e.seen = tickCount;
        }
    }

    private static boolean isFree(ClientLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    // ---- Emission with radius, distance falloff and behind-the-player culling ----
    private void emit(ClientLevel level, LocalPlayer player) {
        RandomSource rnd = level.getRandom();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double maxDistSqr = (double) RADIUS * RADIUS;
        int budget = MAX_PARTICLES_PER_TICK;
        BlockPos.MutableBlockPos tmp = new BlockPos.MutableBlockPos();

        Iterator<Map.Entry<Long, Edge>> it = edges.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Edge> entry = it.next();
            Edge e = entry.getValue();
            if (tickCount - e.seen > EDGE_TTL) { it.remove(); continue; }

            BlockPos rim = BlockPos.of(entry.getKey());
            double cx = rim.getX() + 0.5, cy = rim.getY() + 0.5, cz = rim.getZ() + 0.5;
            double tx = cx - eye.x, ty = cy - eye.y, tz = cz - eye.z;
            double distSqr = tx * tx + ty * ty + tz * tz;

            if (distSqr > (maxDistSqr + 64)) { it.remove(); continue; }
            if (distSqr > maxDistSqr) continue;
            if (budget <= 0) continue;

            // Cull rims well behind the player (but keep nearby ones, so turning doesn't pop).
            if (distSqr > NEAR_NO_CULL * NEAR_NO_CULL) {
                double dot = tx * look.x + ty * look.y + tz * look.z;
                if (dot < 0 && (dot * dot) > (CULL_COS * CULL_COS) * distSqr) continue;
            }

            // Cheap validity re-check so broken roofs stop dripping quickly.
            tmp.set(rim);
            if (!level.getBlockState(tmp).isFaceSturdy(level, tmp, net.minecraft.core.Direction.DOWN)) {
                it.remove();
                continue;
            }

            double dist = Math.sqrt(distSqr);
            double chance = BASE_CHANCE * wetness * (1.0 - 0.7 * (dist / RADIUS));

            for (int d = 0; d < 4 && budget > 0; d++) {
                if ((e.mask & (1 << d)) == 0) continue;
                if (rnd.nextDouble() > chance) continue;

                Direction dir = HORIZ[d];
                double along = (rnd.nextDouble() - 0.5) * 0.9; // spread along the lip
                double px = cx + dir.getStepX() * 0.52 + (dir.getAxis() == Direction.Axis.X ? 0 : along);
                double pz = cz + dir.getStepZ() * 0.52 + (dir.getAxis() == Direction.Axis.Z ? 0 : along);
                double py = rim.getY() - 0.02;

                level.addParticle(ParticleTypes.FALLING_WATER, px, py, pz, 0.0, 0.0, 0.0);
                budget--;
            }
        }
    }
}
