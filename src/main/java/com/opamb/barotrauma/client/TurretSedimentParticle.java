package com.opamb.barotrauma.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 地波扬起的海底泥沙：比气泡大一点、颜色偏土黄，上浮很慢，横向速度靠掉，
 * 落点周围因此会留下一圈慢慢散开的浑浊带。
 *
 * <p>贴图复用 {@code ship_bubble}，靠 {@link #setColor} 调成泥沙色。</p>
 */
public final class TurretSedimentParticle extends TextureSheetParticle {

    private static final float MIN_SIZE = 0.07F;
    private static final float SIZE_RANGE = 0.07F;

    private static final int MIN_LIFETIME = 12;
    private static final int LIFETIME_RANGE = 9;

    /** 泥沙只轻微上浮，主要靠初速度扩散。 */
    private static final double BUOYANCY = 0.002D;

    private TurretSedimentParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz) {
        super(level, x, y, z);
        this.quadSize = MIN_SIZE + this.random.nextFloat() * SIZE_RANGE;
        this.lifetime = MIN_LIFETIME + this.random.nextInt(LIFETIME_RANGE);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.setColor(0.55F, 0.50F, 0.40F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.yd += BUOYANCY;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.90D;
        this.yd *= 0.94D;
        this.zd *= 0.90D;
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public TurretSedimentParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                     double x, double y, double z,
                                                     double vx, double vy, double vz) {
            TurretSedimentParticle particle =
                    new TurretSedimentParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
