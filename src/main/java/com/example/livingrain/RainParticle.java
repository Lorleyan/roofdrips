package com.example.livingrain;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * One falling rain streak or snow flake. It never asks the world for collisions (far too slow for thousands of
 * particles). Instead it checks the height of the highest solid block in its column each tick: reaching it means
 * "landed on something", and being below it means "ended up under a roof", so the drop quietly disappears.
 */
public class RainParticle extends SingleQuadParticle {

    /**
     * Every rain/snow particle we have made and not seen die. We do NOT trust a simple counter: Minecraft keeps
     * particles in a size-limited queue and silently throws the oldest away when it is full, without telling them.
     * Instead each particle stamps the current tick when it is ticked; one that stops being ticked is treated as gone.
     */
    private static final java.util.ArrayList<RainParticle> ALL = new java.util.ArrayList<>();

    /** Number of rain/snow particles that really are alive and being ticked. */
    public static int live() { return ALL.size(); }

    public static void clearAll() { ALL.clear(); }

    /**
     * Forget particles that died or that Minecraft discarded.
     * @return how many were discarded by Minecraft (stopped being ticked without dying)
     */
    public static int prune(long now) {
        int keep = 0;
        int discarded = 0;
        for (int i = 0; i < ALL.size(); i++) {
            RainParticle p = ALL.get(i);
            if (p.gone) continue;
            if (now - p.lastSeen > 3L) {
                discarded++;
                p.gone = true;
                p.remove();
                continue;
            }
            ALL.set(keep++, p);
        }
        if (keep < ALL.size()) ALL.subList(keep, ALL.size()).clear();
        return discarded;
    }

    // Set by the spawner right before addParticle, read once in the constructor.
    public static float pendingSize = 0.3f;
    public static float pendingAlpha = 0.5f;
    public static float pendingWindMul = 1f;

    private final boolean snow;
    private final float windMul;
    private final float phase;
    private final float baseSize;
    private final float baseAlpha;
    private final double refSpeed;
    private boolean gone;
    private long lastSeen;

    protected RainParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, boolean snow,
                           double vx, double vy, double vz) {
        super(level, x, y, z, sprites.first());
        this.snow = snow;
        this.windMul = pendingWindMul;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.baseSize = pendingSize;
        this.baseAlpha = pendingAlpha;
        this.refSpeed = Math.max(0.01, Math.sqrt(vx * vx + vy * vy + vz * vz));
        this.quadSize = pendingSize;
        this.gravity = 0f;
        this.friction = 1f;
        this.hasPhysics = false;
        this.lifetime = snow ? 1600 : 500;
        this.phase = this.random.nextFloat() * 100f;
        if (snow) this.setColor(1.0f, 1.0f, 1.0f);
        else this.setColor(0.80f, 0.88f, 1.0f);
        this.setAlpha(0f);          // fades in over the first few ticks
        this.lastSeen = RainSpawner.clock;
        ALL.add(this);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void remove() {
        this.gone = true;
        super.remove();
    }

    @Override
    public void tick() {
        this.lastSeen = RainSpawner.clock;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        float wx = RainSpawner.windX * windMul;
        float wz = RainSpawner.windZ * windMul;
        if (snow) {
            // Snow is light: it follows the wind more loosely and flutters.
            double sway = Math.sin((this.age + phase) * 0.11) * 0.004;
            double sway2 = Math.cos((this.age + phase) * 0.087) * 0.004;
            this.xd += (wx * 1.4 - this.xd) * 0.05 + sway;
            this.zd += (wz * 1.4 - this.zd) * 0.05 + sway2;
        } else {
            this.xd += (wx - this.xd) * 0.10;
            this.zd += (wz - this.zd) * 0.10;
            float turb = RainSpawner.windTurb;
            if (turb > 0f) {
                float mag = (float) Math.sqrt(wx * wx + wz * wz);
                this.xd += (this.random.nextFloat() - 0.5f) * 0.5f * turb * mag;
                this.zd += (this.random.nextFloat() - 0.5f) * 0.5f * turb * mag;
            }
        }

        double speed = Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        if (!snow) {
            // Where does this drop travel on the SCREEN? Project its velocity onto the camera's right and up
            // directions (which depend on both where you look sideways AND up/down).
            double sx = this.xd * RainSpawner.rightX + this.zd * RainSpawner.rightZ;
            double sy = this.xd * RainSpawner.upX + this.yd * RainSpawner.upY + this.zd * RainSpawner.upZ;
            float tilt = (float) Math.atan2(sx, -sy);
            if (RainSpawner.flipSlant) tilt = -tilt;
            // Keep the angle continuous (no sudden 360-degree spins between ticks).
            float prev = this.roll;
            while (tilt - prev > Math.PI) tilt -= (float) (2 * Math.PI);
            while (tilt - prev < -Math.PI) tilt += (float) (2 * Math.PI);
            this.oRoll = this.roll;
            this.roll = tilt;

            // Seen from straight above or below, a falling drop is almost a point; seen from the side it is a full
            // streak. Faster drops (gusts) are stretched longer: that is the motion blur.
            double proj = Math.sqrt(sx * sx + sy * sy) / Math.max(0.0001, speed);
            double fast = Math.max(0.7, Math.min(2.0, speed / this.refSpeed));
            this.quadSize = (float) (this.baseSize * Math.max(0.3, Math.min(1.0, proj)) * fast);
        }

        this.setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);

        // Far from the player, or far below: not worth keeping.
        double dx = this.x - RainSpawner.camX;
        double dz = this.z - RainSpawner.camZ;
        if (dx * dx + dz * dz > RainSpawner.killDistSq || this.y < RainSpawner.camY - 45.0) {
            this.remove();
            return;
        }

        // Soft edges: fade in after spawning, fade out toward the edge of the rain area, and fade when very close
        // to the camera (a streak right in front of your eye would look huge).
        double dy = this.y - RainSpawner.camY;
        double near = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float a = this.baseAlpha * Math.min(1f, this.age / 6f);
        if (near < 3.5) a *= (float) (near / 3.5);
        double dh = Math.sqrt(dx * dx + dz * dz);
        double fadeStart = RainSpawner.radiusNow * 0.70;
        double fadeEnd = RainSpawner.radiusNow * 1.15;
        if (dh > fadeStart) a *= (float) Math.max(0.0, (fadeEnd - dh) / (fadeEnd - fadeStart));
        this.setAlpha(a);

        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(this.x), Mth.floor(this.z));
        if (this.y <= top) {
            // Landed on a surface (just above the top block) or flew under a roof (well below it).
            if (!snow && RainSpawner.splashes && top - this.y < 1.2
                    && dx * dx + dz * dz < 144.0 && this.random.nextFloat() < 0.35f) {
                this.level.addParticle(ParticleTypes.RAIN, this.x, top + 0.05, this.z, 0, 0, 0);
            }
            this.remove();
        }
    }

    public static class RainProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public RainProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, false, vx, vy, vz);
        }
    }

    public static class SnowProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public SnowProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, true, vx, vy, vz);
        }
    }
}
