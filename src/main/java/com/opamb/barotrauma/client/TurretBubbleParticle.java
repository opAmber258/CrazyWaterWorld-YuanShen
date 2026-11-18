package com.opamb.barotrauma.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 水下弹道上的气泡。
 *
 * <p>深海里没有真正的流体，气泡只做两件事：轻微上浮、横向阻尼，看起来像被水
 * 拖住；尺寸比 {@link TurretTracerParticle} 大一点、颜色偏青白，保证在黑暗海水
 * 里也能看清一条尾迹。</p>
 *
 * <p>开火时的气泡团和命中时的冲击环也用这个粒子：调用方给初速度，粒子自己
 * 上浮并衰减，不需要额外的每帧动画状态。</p>
 */
public final class TurretBubbleParticle extends TextureSheetParticle {

    /** 气泡直径（格）：比光点大，但仍远小于破口气泡。 */
    private static final float MIN_SIZE = 0.05F;
    private static final float SIZE_RANGE = 0.05F;

    private static final int MIN_LIFETIME = 9;
    private static final int LIFETIME_RANGE = 7;

    /** 上浮加速度（格/tick²）。 */
    private static final double BUOYANCY = 0.006D;

    private TurretBubbleParticle(ClientLevel level, double x, double y, double z,
                                 double vx, double vy, double vz) {
        super(level, x, y, z);
        this.quadSize = MIN_SIZE + this.random.nextFloat() * SIZE_RANGE;
        this.lifetime = MIN_LIFETIME + this.random.nextInt(LIFETIME_RANGE);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.setColor(0.72F, 0.88F, 1.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.yd += BUOYANCY;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.93D;
        this.yd *= 0.96D;
        this.zd *= 0.93D;
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    /** 深海没有光源，气泡也要保持可见。 */
    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
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
        public TurretBubbleParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                   double x, double y, double z,
                                                   double vx, double vy, double vz) {
            TurretBubbleParticle particle = new TurretBubbleParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
