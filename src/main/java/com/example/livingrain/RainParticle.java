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
        int sheets = 0;
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
            if (p.sheet) sheets++;
        }
        sheetsAlive = sheets;
        if (keep < ALL.size()) ALL.subList(keep, ALL.size()).clear();
        return discarded;
    }

    // Set by the spawner right before addParticle, read once in the constructor.
    public static float pendingSize = 0.3f;
    public static float pendingAlpha = 0.5f;
    public static float pendingWindMul = 1f;
    /** Debug colour for the next particle (r, g, b), or null for the normal colour. */
    public static float[] pendingTint;

    private static int sheetsAlive;
    /** Number of rain sheets alive (for /BRain status). */
    public static int sheets() { return sheetsAlive; }

    /**
     * Minecraft's "opaque" particle layer, if this version has one (looked up by name so a missing one cannot break the build).
     * Opaque particles are drawn BEFORE translucent glass, so they stay visible through windows when a shader pack is on.
     */
    private static final SingleQuadParticle.Layer OPAQUE_LAYER = findOpaqueLayer();

    private static SingleQuadParticle.Layer findOpaqueLayer() {
        try {
            return (SingleQuadParticle.Layer) SingleQuadParticle.Layer.class.getField("OPAQUE").get(null);
        } catch (Throwable t) {
            return SingleQuadParticle.Layer.TRANSLUCENT;
        }
    }

    private final boolean snow;
    private final boolean solid;
    private final boolean glint;
    private final boolean sheet;
    private final float windMul;
    private final float phase;
    private final float baseSize;
    private final float baseAlpha;
    private final double refSpeed;
    private boolean gone;
    private long lastSeen;

    protected RainParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, boolean snow, boolean solid,
                           boolean glint, boolean sheet, double vx, double vy, double vz) {
        super(level, x, y, z, sprites.first());
        this.snow = snow;
        this.solid = solid;
        this.glint = glint;
        this.sheet = sheet;
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
        this.lifetime = snow ? 1600 : (sheet ? 300 : 500);
        this.phase = this.random.nextFloat() * 100f;
        if (snow) this.setColor(1.0f, 1.0f, 1.0f);
        else if (pendingTint != null) this.setColor(pendingTint[0], pendingTint[1], pendingTint[2]);
        else {
            // A little variation from drop to drop (so the rain is not one flat colour), scaled by the brightness setting.
            float v = RainSpawner.brightness * (0.88f + this.random.nextFloat() * 0.24f);
            this.setColor(Math.min(1f, 0.80f * v), Math.min(1f, 0.88f * v), Math.min(1f, 1.0f * v));
        }
        this.setAlpha(0f);          // fades in over the first few ticks
        this.lastSeen = RainSpawner.clock;
        ALL.add(this);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return solid ? OPAQUE_LAYER : SingleQuadParticle.Layer.TRANSLUCENT;
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
            double fast = sheet ? Math.max(0.9, Math.min(1.3, speed / this.refSpeed))
                                : Math.max(0.7, Math.min(2.0, speed / this.refSpeed));
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
        if (sheet) {
            // A big picture of many streaks: invisible close to the camera, where its flat shape would show.
            a *= (float) Math.max(0.0, Math.min(1.0, (near - 3.5) / 5.0));
        } else if (near < 3.5) {
            a *= (float) (near / 3.5);
        }
        double dh = Math.sqrt(dx * dx + dz * dz);
        double fadeStart = RainSpawner.radiusNow * 0.70;
        double fadeEnd = RainSpawner.radiusNow * 1.15;
        if (dh > fadeStart) a *= (float) Math.max(0.0, (fadeEnd - dh) / (fadeEnd - fadeStart));
        if (!snow) {
            // Backlight: rain glows when it is between you and the sun or moon (light scattering toward the camera).
            if (RainSpawner.backlightAmount > 0f && SunMoon.strength > 0f && near > 1.0) {
                double cosT = (dx * SunMoon.lx + dy * SunMoon.ly + dz * SunMoon.lz) / near;
                if (cosT > 0.0) {
                    double c2 = cosT * cosT;
                    a *= 1f + 2.2f * (float) (c2 * c2) * SunMoon.strength * RainSpawner.backlightAmount;
                }
            }
            // Sparkle: glinting drops flicker a little from tick to tick.
            if (glint) a *= 0.8f + 0.4f * this.random.nextFloat();
            // Lightning: the rain lights up for an instant.
            if (RainSpawner.flashBoost > 0f) a *= 1f + RainSpawner.flashBoost;
            if (a > 0.95f) a = 0.95f;
        }

        int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(this.x), Mth.floor(this.z));
        if (sheet) {
            // The picture is wide, so look at the columns across it too. It fades out before it reaches a roof or the ground
            // (so it never visibly cuts through them) and ends there.
            double o = this.quadSize * 0.6;
            int topMax = Math.max(Math.max(top, heightAt(this.x + o, this.z)), Math.max(heightAt(this.x - o, this.z),
                    Math.max(heightAt(this.x, this.z + o), heightAt(this.x, this.z - o))));
            a *= (float) Math.max(0.0, Math.min(1.0, (this.y - topMax - 0.5) / 2.0));
            this.setAlpha(a);
            if (this.y <= topMax + 0.5) this.remove();
            return;
        }
        this.setAlpha(a);
        if (this.y <= top) {
            // Landed if it crossed the surface during this tick (works however fast it falls); if it was already
            // below the top block it flew in under a roof, and just disappears.
            if (!snow && RainSpawner.splashes && this.yo > top
                    && dx * dx + dz * dz < 144.0 && this.random.nextFloat() < RainSpawner.splashChance) {
                this.level.addParticle(ParticleTypes.RAIN, this.x, top + 0.05, this.z, 0, 0, 0);
            }
            this.remove();
        }
    }

    private int heightAt(double x, double z) {
        return this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    public static class RainProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public RainProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, false, false, false, false, vx, vy, vz);
        }
    }

    /** A big picture holding dozens of thin streaks: one particle that looks like a whole patch of rain. */
    public static class SheetProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public SheetProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, false, false, false, true, vx, vy, vz);
        }
    }

    /** A drop with a bright "catch of the light" along part of its streak, and a little sparkle from tick to tick. */
    public static class GlintProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public GlintProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, false, false, true, false, vx, vy, vz);
        }
    }

    /** Rain that stays visible behind glass with shader packs (hard-edged, because opaque particles cannot blend). */
    public static class SolidRainProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public SolidRainProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, false, true, false, false, vx, vy, vz);
        }
    }

    public static class SnowProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public SnowProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new RainParticle(level, x, y, z, sprites, true, false, false, false, vx, vy, vz);
        }
    }
}
