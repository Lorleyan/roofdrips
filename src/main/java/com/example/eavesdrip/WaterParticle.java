package com.example.eavesdrip;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * One class, two behaviours.
 *  falling = true : water droplet. Parameters: size, fall-speed multiplier, opacity.
 *  falling = false: fading dot.    Parameters: size, lifetime in ticks, opacity.
 */
public class WaterParticle extends SingleQuadParticle {

    private final boolean falling;
    private final float baseAlpha;
    private final boolean splash;

    protected WaterParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites,
                            boolean falling, float size, float p1, float alpha) {
        super(level, x, y, z, sprites.first());
        this.falling = falling;
        this.baseAlpha = alpha;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.quadSize = size;
        this.setColor(0.78f, 0.88f, 1.0f);
        this.setAlpha(alpha);

        if (falling) {
            this.gravity = 0.5f * p1;     // 0.04 * 0.5 = 0.02 blocks/tick^2 at speed 1.0
            this.friction = 0.96f;
            this.hasPhysics = true;
            this.lifetime = Math.max(40, RoofDripsConfig.get().maxDropSeconds * 20);
            this.yd = -0.03 * p1;
            this.splash = RoofDripsConfig.get().splashes;
        } else {
            this.gravity = 0f;
            this.friction = 1f;
            this.hasPhysics = false;
            this.lifetime = Math.max(2, (int) p1);
            this.splash = false;
        }
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) return;

        if (falling) {
            if (this.onGround) {
                this.remove();
                if (splash && this.random.nextFloat() < 0.5f) {
                    this.level.addParticle(ParticleTypes.RAIN, this.x, this.y + 0.02, this.z, 0, 0, 0);
                }
            }
        } else {
            float t = (float) this.age / (float) this.lifetime;
            this.setAlpha(baseAlpha * (1f - t) * (1f - t));
        }
    }

    public static class DropProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public DropProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new WaterParticle(level, x, y, z, sprites, true, (float) vx, (float) vy, (float) vz);
        }
    }

    public static class DotProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public DotProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz, RandomSource random) {
            return new WaterParticle(level, x, y, z, sprites, false, (float) vx, (float) vy, (float) vz);
        }
    }
}
