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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.IntConsumer;

/** Client side: finds overhangs / leaves while it rains and spawns water drips, streams and streaks. */
public class EavesDripClient implements ClientModInitializer {

    private static final Direction[] HORIZ = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    private static final int EDGE_TTL = 600;
    private static final int MAX_EDGES = 2500;
    private static final int V_RANGE = 20;
    private static final int MAX_STREAKS = 500;
    private static final int MAX_STREAK_BLOCKS = 10;     // streaks never run further than this above the lip
    private static final int LOWER_MAX_THICK = 8;        // lower overhangs thicker than this are treated as solid terrain
    private static final int INFINITE_SCAN = 512;        // how far down "Infinite" thickness will look
    private static final double CULL_COS = -0.35;
    private static final double NEAR_NO_CULL = 6.0;
    private static final double SOUND_RANGE = 16.0;
    private static final int CAVE_V = 14;          // vertical search range around the player for cave drips
    private static final int CAVE_SAMPLES = 120;   // random positions tested per tick
    private static final int CAVE_TTL = 400;
    private static final int MAX_CAVE = 1500;

    /** A rim block that water can run off. For roofs this is the LOWEST block of the stack above a gap. */
    private static final class Edge {
        final boolean leaf;
        final int seed;
        int mask;        // bits 0-3: open sides. Leaves also use bit 4 for the underside.
        int thick = 1;   // roofs: number of stacked blocks above the gap
        float bottom;    // roofs: LOWEST underside height inside the cell (0 = full block / bottom slab, 0.5 = top slab)
        float[] prof;    // roofs: underside height at 16 points across each of the 4 faces (see Underside); NaN = nothing there
        float exposure = 1f;   // roofs: share of the top that rain can reach (1 for the top roof, less for sheltered lower ones)
        boolean lower;   // roofs: a lower overhang, under another roof, that only slanted rain reaches
        long seen;
        long checked;    // last tick this edge was re-verified against the real world
        final int[] burst = new int[4];
        final int[] gap = new int[4];
        final int[] snd = new int[4];
        Edge(boolean leaf, int seed, long now) { this.leaf = leaf; this.seed = seed; this.checked = now; }

        /** Underside height at lateral position u (0..1) across face d, falling back to the lowest point. */
        float undersideAt(int d, double u) {
            if (prof == null) return bottom;
            float v = prof[d * Underside.SAMPLES + Underside.index(u)];
            return Float.isNaN(v) ? bottom : v;
        }
    }

    /** A streak sliding down the face of a roof stack. It can wander sideways and change speed. */
    private static final class Streak {
        long key;
        double bx, bz, tx, tz;          // face base position and the sideways direction along the face
        double lat, latV, latTarget;    // sideways position / velocity
        double y, bottom;
        double v, v0, vTarget;          // downward speed (blocks per tick)
        int trail, steerTimer, speedTimer;
        boolean wander;
    }

    private record Roof(int lowY, int thick, int mask, float bottom, float[] prof, float exposure, boolean lower) { }

    /** A cave ceiling block (or dripstone tip) that can drip. src: 0 none, 1 water above, 2 rain seeping. */
    private static final class CaveSpot {
        final int seed;
        final boolean dripstone;
        int src;
        int burst;      // dripstone water stream: ticks left
        long seen, checked;
        CaveSpot(int seed, boolean dripstone, long now) { this.seed = seed; this.dripstone = dripstone; this.checked = now; }
    }

    private record CaveInfo(boolean dripstone, int src) { }

    private final Map<Long, Edge> roofEdges = new HashMap<>();
    private final Map<Long, Edge> leafEdges = new HashMap<>();
    private final List<Streak> streaks = new ArrayList<>();
    private final Map<Long, CaveSpot> caveSpots = new HashMap<>();
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos scratch2 = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos scratch3 = new BlockPos.MutableBlockPos();

    /** How far the rain moves sideways per block it falls (smoothed). Decides which lower overhangs the rain can reach. */
    private float slantX, slantZ;
    /** Top block of the stack found by the last call to analyze(), for scanning below it. */
    private int scanTopY = Integer.MIN_VALUE;
    private int scanX, scanZ, scanRef;
    private final Map<BlockState, float[]> profileCache = new HashMap<>();

    private float wetness = 0f;
    private long tickCount = 0;
    private float soundTokens = 0f;

    // per-tick context
    private ClientLevel level;
    private RoofDripsConfig cfg;
    private RandomSource rnd;
    private Vec3 eye, look;
    private int roofBudget, leafBudget;

    // wind simulation state
    private double driftPhase = 0, stormDeg = 0, gustAngle = 0;
    private boolean wasWet = false;
    private int gustTimer = 200, gustLen = 0, gustAge = 0;
    private float gustAmp = 0f;

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
        caveSpots.clear();
    }

    private void onTick(Minecraft mc) {
        level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) { clearAll(); return; }
        if (mc.isPaused()) return;

        cfg = RoofDripsConfig.get();
        if (!cfg.enabled) { clearAll(); return; }

        // Another mod (Living Rain) may supply the wind and/or the rain level that scales every drip. See WindOverrides.
        float[] ov = WindOverrides.poll();
        float rain;
        float thunderLevel;
        if (ov != null && ov.length >= 7 && ov[4] > 0.5f) {
            rain = ov[5];
            thunderLevel = ov[6];
            Wind.rainOverridden = true;
        } else {
            rain = level.getRainLevel(1.0f);
            thunderLevel = level.getThunderLevel(1.0f);
            Wind.rainOverridden = false;
        }
        Wind.rainLevel = rain;
        // Rain slant, for lower overhangs: Living Rain supplies the real one; on its own we derive it from the wind.
        float tsx, tsz;
        if (ov != null && ov.length >= 9 && ov[4] > 0.5f) { tsx = ov[7]; tsz = ov[8]; }
        else { tsx = Wind.x * 4f * cfg.lowerRainSlant; tsz = Wind.z * 4f * cfg.lowerRainSlant; }
        slantX += (tsx - slantX) * 0.04f;
        slantZ += (tsz - slantZ) * 0.04f;
        float fade = cfg.afterRainSeconds <= 0 ? 1f : 1f / (20f * cfg.afterRainSeconds);
        wetness = rain > wetness ? rain : Math.max(rain, wetness - fade);
        boolean wet = wetness > 0.02f;

        tickCount++;
        rnd = level.getRandom();
        eye = player.getEyePosition();
        look = player.getLookAngle();
        roofBudget = cfg.effectiveRoofCap();
        leafBudget = cfg.effectiveTreeCap();
        soundTokens = Math.min(4f, soundTokens + cfg.maxSoundsPerSecond / 20f);

        updateWind(wet, thunderLevel, ov);

        // Outdoor effects only while it is (or just was) raining.
        if (wet) {
            if (rain > 0.02f) {
                discoverRoofs(player);
                if (cfg.treesEnabled) discoverLeaves(player);
            }
            emitRoofs();
            updateStreaks();
            if (cfg.treesEnabled) emitLeaves(); else leafEdges.clear();
        } else {
            roofEdges.clear();
            leafEdges.clear();
            streaks.clear();
        }

        // Cave drips work in any weather (water above), and also while raining (seeping rain).
        if (cfg.caveEnabled || cfg.dripstoneEnabled) {
            discoverCave(player);
            emitCave();
        } else {
            caveSpots.clear();
        }
    }

    // ================= Wind =================
    private int nextGustInterval() {
        float freq = cfg.gustFrequency;
        if (freq <= 0.001f) return 20 * 600;
        float sec = 90f + (6f - 90f) * freq;   // 0% -> every ~90 s, 100% -> every ~6 s (on average)
        return Math.max(20, Math.round(20f * sec * (0.6f + 0.8f * rnd.nextFloat())));
    }

    /** A made-up wind: a base direction that slowly drifts, plus smooth gusts. Vanilla Minecraft has no wind. */
    private void updateWind(boolean wet, float thunder, float[] ov) {
        // Another mod (Living Rain) may take over the wind (flag in slot 0).
        if (ov != null && ov[0] > 0.5f) {
            Wind.overridden = true;
            Wind.turbulence = ov[3];
            Wind.x = ov[1];
            Wind.z = ov[2];
            Wind.active = true;
            return;
        }
        Wind.overridden = false;
        Wind.turbulence = cfg.windTurbulence;

        if (!cfg.windEnabled) { Wind.active = false; Wind.x = 0f; Wind.z = 0f; return; }

        if (wet && !wasWet) stormDeg = rnd.nextDouble() * 360.0;
        wasWet = wet;

        double deg = cfg.windRandomPerStorm ? stormDeg : cfg.windDirection;
        driftPhase += 0.004;
        deg += (Math.sin(driftPhase) * 60.0 + Math.sin(driftPhase * 2.7 + 1.3) * 25.0) * cfg.windDriftAmount;

        float str = cfg.windStrength;
        if (cfg.windStormBoost) str *= 1f + 0.5f * thunder;

        float gust = 0f;
        double gustOff = 0;
        if (gustLen > 0) {
            float t = gustAge / (float) gustLen;
            float swell = (float) Math.sin(Math.PI * t);
            gust = gustAmp * swell;
            gustOff = gustAngle * swell;
            if (++gustAge >= gustLen) { gustLen = 0; gustTimer = nextGustInterval(); }
        } else if (cfg.gustFrequency > 0.001f && cfg.gustStrength > 0.001f && --gustTimer <= 0) {
            gustLen = Math.max(10, Math.round(cfg.gustDuration * 20f * (0.6f + 0.8f * rnd.nextFloat())));
            gustAge = 0;
            gustAmp = cfg.gustStrength * (0.6f + 0.8f * rnd.nextFloat());
            gustAngle = Math.toRadians((rnd.nextDouble() * 2 - 1) * 30.0);
        }

        double a = Math.toRadians(deg) + gustOff;
        float speed = 0.08f * str * (1f + gust);
        Wind.x = (float) (Math.sin(a) * speed);     // 0 deg = north = -z, 90 deg = east = +x
        Wind.z = (float) (-Math.cos(a) * speed);
        Wind.active = true;
    }

    // ================= World analysis =================

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

    /**
     * "Decor": a block that has a collision shape but is not a real roof/wall piece. Chains, lanterns, fences,
     * wall posts, panes, open trapdoors, doors, signs and so on. A real roof piece has a complete flat top or
     * bottom face (full blocks, stairs, both kinds of slab). Leaves are treated as solid, never as decor.
     */
    private boolean isDecorBlock(BlockState s, BlockPos p) {
        if (s.getCollisionShape(level, p).isEmpty()) return false;
        if (!s.getFluidState().isEmpty()) return false;
        if (s.is(BlockTags.LEAVES)) return false;
        return !(s.isFaceSturdy(level, p, Direction.DOWN) || s.isFaceSturdy(level, p, Direction.UP));
    }

    /** Can water and falling drops pass through this cell? (air, plants, and - if enabled - decor.) */
    private boolean thr(int x, int y, int z, boolean decorOk) {
        BlockPos.MutableBlockPos q = scratch2;
        q.set(x, y, z);
        BlockState s = level.getBlockState(q);
        if (!s.getFluidState().isEmpty()) return false;
        if (s.getCollisionShape(level, q).isEmpty()) return true;
        return decorOk && isDecorBlock(s, q);
    }

    /**
     * Looks at one column and decides whether it is a drip rim right now.
     *
     * 1. Take the rain-exposed top block (skipping a few decor blocks such as fence posts on top).
     * 2. Hand that stack to analyzeStack(), which does the rest (see there).
     * The height of the top block is remembered in scanTopY so lower stacks in the same column can be looked at too.
     */
    private Roof analyze(int x, int z, int refY) {
        boolean decorOk = cfg.allowAboveDecor;
        BlockPos.MutableBlockPos p = scratch;
        scanTopY = Integer.MIN_VALUE;
        p.set(x, 0, z);
        if (!level.hasChunkAt(p)) return null;

        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        int y = top;
        int skipped = 0;
        BlockState s;
        while (true) {
            p.set(x, y, z);
            s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES)) return null;
            if (decorOk && isDecorBlock(s, p)) {
                if (++skipped > 4) return null;
                y--;
                continue;
            }
            break;
        }
        if (s.getCollisionShape(level, p).isEmpty()) return null;
        scanTopY = y;
        return analyzeStack(x, z, y, false, refY);
    }

    /**
     * Analyses the stack of solid blocks whose top block is at topY.
     *
     * lower = false: the top stack of the column; rain falls straight onto it.
     * lower = true:  a stack further down, under another roof (a balcony, an awning, a tower tier). It only counts if
     *                slanted rain can reach its top face, and it drips less the more sheltered it is.
     *
     * 1. Cheap test: water on the top must be able to run off at least one side. That side needs open air beside the
     *    top surface and an open edge (for the top stack also open sky above the neighbouring column, so interior steps,
     *    diagonal roof lines and thin crusts over caves are rejected right here).
     * 2. Walk down through the stack. A side stays alive only while the whole face is open air. The walk ends at the
     *    first gap (open air, or decor if enabled). The block just above the gap is the rim.
     * 3. The rim needs a full flat top or bottom (so top slabs and stairs work) and the side below it must be open.
     * 4. Work out where the rim's underside really is along each face (upside-down stairs are thin on one side).
     */
    private Roof analyzeStack(int x, int z, int topY, boolean lower, int refY) {
        boolean decorOk = cfg.allowAboveDecor;
        int maxT = cfg.overhangDepth >= RoofDripsConfig.INFINITE_DEPTH ? INFINITE_SCAN : cfg.overhangDepth;
        if (lower) maxT = Math.min(maxT, LOWER_MAX_THICK);
        BlockPos.MutableBlockPos p = scratch;
        p.set(x, topY, z);
        BlockState s = level.getBlockState(p);
        if (lower) {
            if (!s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES) || s.getCollisionShape(level, p).isEmpty()) return null;
            if (!thr(x, topY + 1, z, decorOk)) return null;      // covered right above: no rain gets in
        }

        int alive = 0;
        for (int d = 0; d < 4; d++) {
            int nx = x + HORIZ[d].getStepX(), nz = z + HORIZ[d].getStepZ();
            if (thr(nx, topY + 1, nz, decorOk) && thr(nx, topY, nz, decorOk)
                    && (lower || level.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz) <= topY + 2)) {
                alive |= 1 << d;
            }
        }
        if (alive == 0) return null;

        int t = 1;
        int yy = topY - 1;
        while (true) {
            if (yy < topY - INFINITE_SCAN) return null;
            p.set(x, yy, z);
            s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty()) return null;
            if (s.getCollisionShape(level, p).isEmpty() || (decorOk && isDecorBlock(s, p))) break;   // the gap

            for (int d = 0; d < 4; d++) {
                if ((alive & (1 << d)) == 0) continue;
                if (!thr(x + HORIZ[d].getStepX(), yy, z + HORIZ[d].getStepZ(), decorOk)) alive &= ~(1 << d);
            }
            if (alive == 0) return null;
            t++;
            if (t > maxT) return null;
            yy--;
        }

        int lowY = yy + 1;
        if (Math.abs(lowY - refY) > V_RANGE) return null;
        p.set(x, lowY, z);
        BlockState low = level.getBlockState(p);
        if (!(low.isFaceSturdy(level, p, Direction.DOWN) || low.isFaceSturdy(level, p, Direction.UP))) return null;
        if (!rainsHere(p)) return null;

        for (int d = 0; d < 4; d++) {
            if ((alive & (1 << d)) == 0) continue;
            if (!thr(x + HORIZ[d].getStepX(), lowY - 1, z + HORIZ[d].getStepZ(), decorOk)) alive &= ~(1 << d);
        }
        if (alive == 0) return null;

        // Where is the underside along each open face? A side that has no solid part reaching it cannot drip.
        float[] prof = profileOf(low, p);
        float bottom = 1f;
        for (int d = 0; d < 4; d++) {
            if ((alive & (1 << d)) == 0) continue;
            boolean any = false;
            for (int i = 0; i < Underside.SAMPLES; i++) {
                float v = prof[d * Underside.SAMPLES + i];
                if (!Float.isNaN(v)) { any = true; if (v < bottom) bottom = v; }
            }
            if (!any) alive &= ~(1 << d);
        }
        if (alive == 0) return null;

        float exposure = 1f;
        if (lower) {
            exposure = RainExposure.exposure(exposureGrid, x, topY, z, slantX, slantZ, 16.0);
            if (exposure <= 0.01f) return null;
        }
        return new Roof(lowY, t, alive, bottom, prof, exposure, lower);
    }

    /** Underside height across all four faces of a block state, from its collision shape. Cached per state. */
    private float[] profileOf(BlockState state, BlockPos pos) {
        float[] cached = profileCache.get(state);
        if (cached != null) return cached;
        List<double[]> boxes = new ArrayList<>();
        for (AABB b : state.getCollisionShape(level, pos).toAabbs()) {
            boxes.add(new double[] { b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ });
        }
        float[] out = new float[4 * Underside.SAMPLES];
        for (int d = 0; d < 4; d++) {
            float[] face = Underside.sideProfile(boxes, HORIZ[d].getStepX(), HORIZ[d].getStepZ());
            System.arraycopy(face, 0, out, d * Underside.SAMPLES, Underside.SAMPLES);
        }
        if (profileCache.size() > 1024) profileCache.clear();
        profileCache.put(state, out);
        return out;
    }

    /** What the rain-exposure test needs to know about the world. */
    private final RainExposure.Grid exposureGrid = new RainExposure.Grid() {
        @Override public boolean blocked(int x, int y, int z) { return !thr(x, y, z, cfg.allowAboveDecor); }
        @Override public int skyY(int x, int z) {
            scratch3.set(x, 0, z);
            return level.hasChunkAt(scratch3) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : Integer.MAX_VALUE;
        }
    };

    private final StackScan.Cells scanCells = new StackScan.Cells() {
        @Override public boolean passable(int x, int y, int z) { return thr(x, y, z, cfg.allowAboveDecor); }
        @Override public boolean fluid(int x, int y, int z) {
            scratch3.set(x, y, z);
            return !level.getFluidState(scratch3).isEmpty();
        }
    };

    /** Finds the stacks below the first one in this column (balconies, awnings, tower tiers) and analyses each. */
    private final IntConsumer lowerSink = topY -> {
        Roof r = analyzeStack(scanX, scanZ, topY, true, scanRef);
        if (r != null) addEdge(scanX, scanZ, r);
    };

    private void scanLower(int x, int z, int refY, int firstTop) {
        scanX = x;
        scanZ = z;
        scanRef = refY;
        StackScan.lowerTops(scanCells, x, z, firstTop, refY - V_RANGE - 2, cfg.lowerStacks, LOWER_MAX_THICK, lowerSink);
    }

    private boolean hasSlant() {
        return Math.abs(slantX) >= 0.03f || Math.abs(slantZ) >= 0.03f;
    }

    private static void applyRoof(Edge e, Roof r) {
        e.mask = r.mask();
        e.thick = r.thick();
        e.bottom = r.bottom();
        e.prof = r.prof();
        e.exposure = r.exposure();
        e.lower = r.lower();
    }

    private void addEdge(int x, int z, Roof r) {
        long key = BlockPos.asLong(x, r.lowY(), z);
        Edge e = roofEdges.get(key);
        if (e == null) { e = new Edge(false, seedOf(key), tickCount); roofEdges.put(key, e); }
        applyRoof(e, r);
        e.seen = tickCount;
    }

    // ================= Discovery =================

    private void discoverRoofs(LocalPlayer player) {
        int px = player.getBlockX(), pz = player.getBlockZ(), py = player.getBlockY();
        int R = cfg.radius;

        for (int i = 0; i < cfg.scanSpeed && roofEdges.size() < MAX_EDGES; i++) {
            int x = px + rnd.nextInt(R * 2 + 1) - R;
            int z = pz + rnd.nextInt(R * 2 + 1) - R;
            int dx = x - px, dz = z - pz;
            if (dx * dx + dz * dz > R * R) continue;

            Roof r = analyze(x, z, py);
            int firstTop = scanTopY;
            if (r != null) addEdge(x, z, r);

            // Further stacks lower down in the same column (under the roof we just looked at). Only worth looking at
            // when there is wind: with straight-down rain, anything under a roof stays dry.
            if (cfg.lowerOverhangs && firstTop != Integer.MIN_VALUE && hasSlant() && roofEdges.size() < MAX_EDGES) {
                scanLower(x, z, py, firstTop);
            }
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
                if (e == null) { e = new Edge(true, seedOf(key), tickCount); leafEdges.put(key, e); }
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

            // Re-check against the real world every few ticks, so filling a gap stops the effect at once.
            // Lower overhangs are re-checked less often: they cost more to check (the rain path is traced).
            long sinceCheck = tickCount - e.checked;
            if (sinceCheck >= (e.lower ? 24 : 8)
                    || (!e.lower && sinceCheck >= 3 && Math.floorMod((long) e.seed + tickCount, 3L) == 0)) {
                Roof r = e.lower
                        ? analyzeStack(rim.getX(), rim.getZ(), rim.getY() + e.thick - 1, true, rim.getY())
                        : analyze(rim.getX(), rim.getZ(), rim.getY());
                if (r == null || r.lowY() != rim.getY()) { it.remove(); continue; }
                applyRoof(e, r);
                e.checked = tickCount;
                e.seen = tickCount;
            }

            double dist = Math.sqrt(distSqr);
            float falloff = (float) (1.0 - 0.6 * dist / cfg.radius);
            float dens = cfg.density * wetness * falloff * e.exposure;

            for (int d = 0; d < 4; d++) {
                if ((e.mask & (1 << d)) == 0) continue;
                processSide(e, rim, d, dens, dist);
            }
        }
    }

    /**
     * Every side of every edge has its own blend of streaming and dripping.
     *  s = how "streamy" this side is (average from settings, spread by Edge variation).
     * High s: long, thick, near-continuous streams.  Mid s: milder streams with drips between.  Low s: mostly drips.
     */
    private void processSide(Edge e, BlockPos rim, int d, float dens, double dist) {
        float var = cfg.edgeVariation;
        float s = Math.max(0f, Math.min(1f, cfg.streamShare + (hash(e.seed, d * 7 + 1) - 0.5f) * 2f * var));
        float rateMul = Math.max(0.2f, 1f + (hash(e.seed, d * 7 + 2) - 0.5f) * var * 1.6f);
        float dripChance = 0.035f * dens * rateMul * (0.4f + 0.6f * (1f - s));
        double soundY = rim.getY() + e.bottom + 0.2;

        if (e.burst[d] > 0) {
            e.burst[d]--;
            int n = roll(0.85f * dens * rateMul * (0.45f + 0.55f * s));
            for (int i = 0; i < n && roofBudget > 0; i++) { emitLipDrop(rim, d, e); roofBudget--; }
            if (e.snd[d]-- <= 0) {
                e.snd[d] = 30 + rnd.nextInt(20);
                playSound(rim.getX() + 0.5, soundY, rim.getZ() + 0.5, true, dist);
            }
            if (e.burst[d] == 0) {
                if (s > 0.97f) {
                    e.gap[d] = 0;
                } else {
                    float avgLen = cfg.streamLength * 7f * (0.4f + 0.6f * s);
                    e.gap[d] = Math.min(400, Math.max(2, Math.round(avgLen * (1f - s) / Math.max(s, 0.05f) * (0.5f + rnd.nextFloat()))));
                }
            }
        } else {
            if (e.gap[d] > 0) {
                e.gap[d]--;
            } else if (s > 0.05f) {
                float v = 1f + (rnd.nextFloat() * 2f - 1f) * cfg.streamLengthVar;
                e.burst[d] = Math.max(3, Math.round(cfg.streamLength * 7f * v * (0.4f + 0.6f * s)));
            }
            if (roofBudget > 0 && rnd.nextFloat() < dripChance) {
                emitLipDrop(rim, d, e);
                roofBudget--;
                if (rnd.nextFloat() < 0.35f) {
                    playSound(rim.getX() + 0.5, soundY, rim.getZ() + 0.5, false, dist);
                }
            }
        }

        if (cfg.faceStreakAmount > 0f && dist < cfg.radius * 0.6 && streaks.size() < MAX_STREAKS
                && rnd.nextFloat() < 0.006f * cfg.faceStreakAmount * wetness * rateMul * e.exposure
                && faceStreakAllowed(rim, e.thick, d)) {
            spawnStreak(rim, d, e.thick);
        }
    }

    private void emitLipDrop(BlockPos rim, int d, Edge e) {
        Direction dir = HORIZ[d];
        double along = (rnd.nextDouble() - 0.5) * 0.8;
        double px = rim.getX() + 0.5 + dir.getStepX() * 0.53 + (dir.getAxis() == Direction.Axis.X ? 0 : along);
        double pz = rim.getZ() + 0.5 + dir.getStepZ() * 0.53 + (dir.getAxis() == Direction.Axis.Z ? 0 : along);
        // Start at the underside as it really is at this spot on the face: an upside-down stair is thin on one side.
        double under = e.undersideAt(d, 0.5 + along);
        spawnDrop(px, rim.getY() + under - 0.02, pz);
    }

    private void spawnDrop(double x, double y, double z) {
        float size = 0.028f * cfg.dropSize * (0.8f + rnd.nextFloat() * 0.5f);
        float speed = cfg.fallSpeed * (1f + (rnd.nextFloat() * 2f - 1f) * cfg.fallSpeedVar);
        WaterParticle.pendingWind = (cfg.windEnabled || Wind.overridden) ? 0.7f + rnd.nextFloat() * 0.6f : 0f;
        level.addParticle(RoofDripsMod.DROP, x, y, z, size, Math.max(0.2f, speed), cfg.opacity);
    }

    /** Cave drops are sheltered from the wind. */
    private void spawnCaveDrop(double x, double y, double z, float sizeMul, float speedMul) {
        float size = 0.028f * sizeMul * (0.8f + rnd.nextFloat() * 0.5f);
        float speed = speedMul * (1f + (rnd.nextFloat() * 2f - 1f) * 0.25f);
        WaterParticle.pendingWind = 0f;
        level.addParticle(RoofDripsMod.DROP, x, y, z, size, Math.max(0.2f, speed), cfg.opacity);
    }

    // ---- Streaks running down the face of a roof stack ----

    /**
     * Streaks only run down a face that is a complete flat wall of full-height blocks with open air beside it,
     * for (at most) the bottom MAX_STREAK_BLOCKS blocks of the stack. Slabs and stairs fail this test, so they
     * only drip. Hanging decor next to the face also blocks streaks, so streaks never run into it.
     */
    private boolean faceStreakAllowed(BlockPos rim, int thick, int d) {
        Direction dir = HORIZ[d];
        int h = Math.min(thick, MAX_STREAK_BLOCKS);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = 0; k < h; k++) {
            p.set(rim.getX(), rim.getY() + k, rim.getZ());
            BlockState s = level.getBlockState(p);
            if (!s.isFaceSturdy(level, p, dir)) return false;
            if (!isFree(level, p.relative(dir))) return false;
        }
        return true;
    }

    private void spawnStreak(BlockPos rim, int d, int thick) {
        Direction dir = HORIZ[d];
        int h = Math.min(thick, MAX_STREAK_BLOCKS);
        boolean xAxis = dir.getAxis() == Direction.Axis.X;

        Streak s = new Streak();
        s.key = rim.asLong();
        s.bx = rim.getX() + 0.5 + dir.getStepX() * 0.51;
        s.bz = rim.getZ() + 0.5 + dir.getStepZ() * 0.51;
        s.tx = xAxis ? 0 : 1;
        s.tz = xAxis ? 1 : 0;
        s.lat = (rnd.nextDouble() - 0.5) * 0.84;
        s.bottom = rim.getY() + 0.03;
        // Single-block faces start at the top. Taller faces get streaks starting at random heights.
        s.y = h <= 1 ? rim.getY() + 1 - 0.03 : rim.getY() + 1 + (h - 1 - 0.03) * rnd.nextDouble();

        float sv = 1f + (rnd.nextFloat() * 2f - 1f) * cfg.faceStreakSpeedVar;
        s.v0 = Math.max(0.008, 0.04 * cfg.faceStreakSpeed * sv);
        s.v = s.v0;
        s.vTarget = s.v0;
        float lv = 1f + (rnd.nextFloat() * 2f - 1f) * cfg.faceStreakLengthVar;
        double trailBlocks = Math.max(0.1, 0.9 * cfg.faceStreakLength * lv);
        s.trail = Math.max(2, (int) Math.round(trailBlocks / s.v0));
        s.wander = rnd.nextFloat() < cfg.faceWander;
        s.steerTimer = 1;
        s.speedTimer = 5 + rnd.nextInt(10);
        streaks.add(s);
    }

    private void updateStreaks() {
        Iterator<Streak> it = streaks.iterator();
        while (it.hasNext()) {
            Streak s = it.next();
            if (!roofEdges.containsKey(s.key)) { it.remove(); continue; }   // roof changed or removed

            if (s.wander) {
                // Like rain on a window: mostly downward, but drifting left and right and changing speed in sections.
                if (--s.steerTimer <= 0) {
                    s.steerTimer = 5 + rnd.nextInt(14);
                    s.latTarget = (rnd.nextDouble() * 2 - 1) * 0.014 * cfg.faceWanderStrength;
                }
                s.latV += (s.latTarget - s.latV) * 0.3;
                s.lat += s.latV;
                if (s.lat > 0.44) { s.lat = 0.44; s.latTarget = -Math.abs(s.latTarget); s.latV *= -0.5; }
                else if (s.lat < -0.44) { s.lat = -0.44; s.latTarget = Math.abs(s.latTarget); s.latV *= -0.5; }

                if (--s.speedTimer <= 0) {
                    s.speedTimer = 8 + rnd.nextInt(20);
                    double swing = Math.max(0.3, cfg.faceStreakSpeedVar) * 0.8;
                    s.vTarget = Math.max(0.006, s.v0 * (1 + (rnd.nextDouble() * 2 - 1) * swing));
                }
                s.v += (s.vTarget - s.v) * 0.2;
            }

            s.y -= s.v;
            double x = s.bx + s.tx * s.lat, z = s.bz + s.tz * s.lat;
            if (s.y <= s.bottom) {
                if (roofBudget > 0 && rnd.nextFloat() < 0.7f) { spawnDrop(x, s.bottom - 0.02, z); roofBudget--; }
                it.remove();
                continue;
            }
            if (roofBudget > 0) {
                level.addParticle(RoofDripsMod.DOT, x, s.y, z, 0.022f * cfg.dropSize, s.trail, cfg.opacity * 0.85f);
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

    // ================= Cave drip =================

    private boolean isNaturalCeiling(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.STONE_ORE_REPLACEABLES)
                || s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY);
    }

    /**
     * Is this position a cave ceiling block, or a hanging dripstone tip, with a leak source above it?
     * Source 1 = water within range above. Source 2 = the rainy surface within range above.
     */
    private CaveInfo analyzeCave(int x, int y, int z) {
        BlockPos.MutableBlockPos p = scratch;
        p.set(x, y, z);
        if (!level.hasChunkAt(p)) return null;
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return null;

        boolean dripstone = false;
        int hostY;
        if (s.is(Blocks.POINTED_DRIPSTONE)) {
            if (!cfg.dripstoneEnabled) return null;
            if (s.getValue(PointedDripstoneBlock.TIP_DIRECTION) != Direction.DOWN) return null;
            if (!s.getValue(PointedDripstoneBlock.THICKNESS).toString().equalsIgnoreCase("tip")) return null;
            dripstone = true;
            int hy = y + 1;
            while (hy < y + 10) {
                p.set(x, hy, z);
                if (level.getBlockState(p).is(Blocks.POINTED_DRIPSTONE)) hy++; else break;
            }
            hostY = hy;
        } else {
            if (!cfg.caveEnabled) return null;
            if (!s.getFluidState().isEmpty()) return null;
            if (!s.isFaceSturdy(level, p, Direction.DOWN)) return null;
            if (cfg.caveNaturalOnly && !isNaturalCeiling(s)) return null;
            p.set(x, y - 1, z);
            if (!isFree(level, p)) return null;
            hostY = y;
        }

        // A block that is itself open to the sky is an outdoor overhang, which the rain logic handles.
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (top <= hostY) return null;

        int src = 0;
        if (cfg.caveWaterEnabled) {
            for (int k = 1; k <= cfg.caveWaterRange; k++) {
                p.set(x, hostY + k, z);
                if (level.getBlockState(p).getFluidState().is(FluidTags.WATER)) { src = 1; break; }
            }
        }
        if (src == 0 && cfg.caveRainEnabled && top - hostY <= cfg.caveRainRange) {
            p.set(x, top, z);
            if (rainsHere(p)) src = 2;
        }

        if (!dripstone && src == 0) return null;
        if (dripstone && cfg.dripstoneRequireWater && src == 0) return null;
        return new CaveInfo(dripstone, src);
    }

    private void discoverCave(LocalPlayer player) {
        int px = player.getBlockX(), pz = player.getBlockZ(), py = player.getBlockY();
        int R = cfg.caveRadius;
        // A bigger radius covers far more ground, so search harder and keep more spots in memory.
        double scale = Math.pow(R / 20.0, 1.5);
        int samples = (int) Math.min(900, Math.max(40, Math.round(CAVE_SAMPLES * scale)));
        int maxSpots = (int) Math.min(6000, Math.round(MAX_CAVE * Math.max(1.0, scale)));
        for (int i = 0; i < samples && caveSpots.size() < maxSpots; i++) {
            int x = px + rnd.nextInt(R * 2 + 1) - R;
            int z = pz + rnd.nextInt(R * 2 + 1) - R;
            int dx = x - px, dz = z - pz;
            if (dx * dx + dz * dz > R * R) continue;
            int y = py + rnd.nextInt(CAVE_V * 2 + 1) - CAVE_V;

            CaveInfo info = analyzeCave(x, y, z);
            if (info == null) continue;

            long key = BlockPos.asLong(x, y, z);
            CaveSpot sp = caveSpots.get(key);
            if (sp == null) { sp = new CaveSpot(seedOf(key), info.dripstone(), tickCount); caveSpots.put(key, sp); }
            sp.src = info.src();
            sp.seen = tickCount;
        }
    }

    /**
     * Pointed dripstone is drawn with a small random sideways offset (up to 1/8 of a block) that depends on its
     * position. This reproduces that offset so the water starts exactly at the visible tip.
     */
    private static double[] dripstoneXZ(BlockPos pos) {
        long seed = Mth.getSeed(pos.getX(), 0, pos.getZ());
        double dx = Mth.clamp(((double) ((float) (seed & 15L) / 15.0F) - 0.5) * 0.5, -0.125, 0.125);
        double dz = Mth.clamp(((double) ((float) (seed >> 8 & 15L) / 15.0F) - 0.5) * 0.5, -0.125, 0.125);
        return new double[] { pos.getX() + 0.5 + dx, pos.getZ() + 0.5 + dz };
    }

    private void emitCave() {
        double maxSqr = (double) cfg.caveRadius * cfg.caveRadius;
        boolean raining = wetness > 0.02f;
        int bud = cfg.effectiveCaveCap();
        // Seeping-rain intensity curve: 25% = occasional, 50% = definite visible dripping, 100% = near constant.
        float seep = (float) (0.03 * Math.pow(cfg.caveRainChance, 2.3));

        Iterator<Map.Entry<Long, CaveSpot>> it = caveSpots.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, CaveSpot> entry = it.next();
            CaveSpot sp = entry.getValue();
            if (tickCount - sp.seen > CAVE_TTL) { it.remove(); continue; }

            BlockPos pos = BlockPos.of(entry.getKey());
            double tx = pos.getX() + 0.5 - eye.x, ty = pos.getY() + 0.5 - eye.y, tz = pos.getZ() + 0.5 - eye.z;
            double distSqr = tx * tx + ty * ty + tz * tz;
            if (distSqr > maxSqr + 64) { it.remove(); continue; }
            if (distSqr > maxSqr) continue;
            if (isBehind(tx, ty, tz, distSqr)) continue;

            // Re-check now and then, so a changed ceiling stops dripping quickly.
            long since = tickCount - sp.checked;
            if (since >= 20 && (since >= 40 || Math.floorMod((long) sp.seed + tickCount, 5L) == 0)) {
                CaveInfo info = analyzeCave(pos.getX(), pos.getY(), pos.getZ());
                if (info == null || info.dripstone() != sp.dripstone) { it.remove(); continue; }
                sp.src = info.src();
                sp.checked = tickCount;
                sp.seen = tickCount;
            }
            if (bud <= 0) continue;

            float rateMul = 0.4f + 1.2f * hash(sp.seed, 5);

            // Occasional water streams from dripstone tips.
            if (sp.dripstone && cfg.dripstoneStreams) {
                if (sp.burst > 0) {
                    sp.burst--;
                    double[] tip = dripstoneXZ(pos);
                    spawnCaveDrop(tip[0] + (rnd.nextDouble() - 0.5) * 0.03, pos.getY() + 0.30,
                            tip[1] + (rnd.nextDouble() - 0.5) * 0.03,
                            cfg.dripstoneDropSize, cfg.dripstoneFallSpeed);
                    bud--;
                    continue;
                }
                boolean streamOk = !cfg.dripstoneRequireWater || sp.src == 1 || (sp.src == 2 && raining);
                if (streamOk && rnd.nextFloat() < 0.02f * cfg.dripstoneStreamFreq * rateMul) {
                    sp.burst = 20 + rnd.nextInt(40);
                    double[] tip = dripstoneXZ(pos);
                    playCaveSound(tip[0], pos.getY() + 0.30, tip[1], Math.sqrt(distSqr));
                }
            }

            float chance;
            float sizeMul, speedMul;
            if (!sp.dripstone) {
                if (sp.src == 2) {
                    if (!raining) continue;
                    chance = seep * cfg.caveDensity * rateMul * wetness;
                } else {
                    chance = 0.0025f * cfg.caveDensity * rateMul;
                }
                sizeMul = cfg.caveDropSize;
                speedMul = cfg.caveFallSpeed;
            } else {
                chance = 0.006f * cfg.dripstoneDensity * rateMul;
                if (cfg.dripstoneRequireWater && sp.src == 2) {
                    if (!raining) continue;
                    chance *= cfg.caveRainChance * wetness;
                }
                sizeMul = cfg.dripstoneDropSize;
                speedMul = cfg.dripstoneFallSpeed;
            }
            if (rnd.nextFloat() >= chance) continue;

            double px, py, pz;
            if (sp.dripstone) {
                double[] tip = dripstoneXZ(pos);
                px = tip[0]; py = pos.getY() + 0.30; pz = tip[1];
            } else {
                px = pos.getX() + 0.15 + rnd.nextDouble() * 0.7;
                py = pos.getY() - 0.02;
                pz = pos.getZ() + 0.15 + rnd.nextDouble() * 0.7;
            }
            spawnCaveDrop(px, py, pz, sizeMul, speedMul);
            bud--;
            if (rnd.nextFloat() < 0.5f) playCaveSound(px, py, pz, Math.sqrt(distSqr));
        }
    }

    private void playCaveSound(double x, double y, double z, double dist) {
        if (!cfg.soundsEnabled || soundTokens < 1f || dist > SOUND_RANGE) return;
        float vol = Math.min(1f, cfg.caveSoundVolume);
        if (vol <= 0.001f) return;
        soundTokens -= 1f;
        float pitch = 0.7f + rnd.nextFloat() * 0.35f;
        level.playLocalSound(x, y, z, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.AMBIENT, vol, pitch, false);
    }

    // ================= Sound =================
    /** 100% is Minecraft's loudest possible volume for a sound. Nothing is ever boosted beyond it. */
    private void playSound(double x, double y, double z, boolean stream, double dist) {
        if (!cfg.soundsEnabled || soundTokens < 1f || dist > SOUND_RANGE) return;
        float vol = Math.min(1f, stream ? cfg.streamVolume : cfg.dripVolume);
        if (vol <= 0.001f) return;
        soundTokens -= 1f;

        SoundEvent ev = stream ? SoundEvents.WATER_AMBIENT : SoundEvents.POINTED_DRIPSTONE_DRIP_WATER;
        float pitch = stream ? 1.0f + rnd.nextFloat() * 0.4f : 0.85f + rnd.nextFloat() * 0.5f;
        level.playLocalSound(x, y, z, ev, SoundSource.WEATHER, vol, pitch, false);
    }
}
