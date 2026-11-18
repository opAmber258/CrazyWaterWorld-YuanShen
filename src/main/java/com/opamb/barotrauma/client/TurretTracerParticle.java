package com.opamb.barotrauma.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 炮塔开火弹道上的单个亮点。
 *
 * <p>原版没有“又细又不带火焰”的粒子可用：{@code END_ROD} 的光晕在近处偏粗，
 * {@code CRIT} 受重力会往下掉。这里用自定义粒子把 {@code quadSize} 压到
 * {@value #QUAD_SIZE} 格（原版默认 0.1），并关掉重力与随机速度；点的位置完全由
 * {@link TurretClient} 沿弹道逐帧给出，所以整束是笔直的一条细线。</p>
 *
 * <p>{@code getLightColor} 返回满亮度，深海里没有光源也能看清弹道。</p>
 */
public final class TurretTracerParticle extends TextureSheetParticle {

    /** 单点直径（格）。0.03 大约是原版粒子默认尺寸的三分之一，近看也只有几个像素。 */
    private static final float QUAD_SIZE = 0.03F;

    /** 存活 tick 数：24 格/秒下 6 tick 约留下 7 格的拖尾。 */
    private static final int LIFETIME = 6;

    private TurretTracerParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        this.quadSize = QUAD_SIZE;
        this.lifetime = LIFETIME;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.setColor(0.87F, 0.95F, 1.0F);
    }

    @Override
    public void tick() {
        // 位置由发射端逐帧给，粒子自己不动；只做寿命倒计时。
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

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
        public TurretTracerParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                   double x, double y, double z,
                                                   double vx, double vy, double vz) {
            TurretTracerParticle particle = new TurretTracerParticle(level, x, y, z);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}