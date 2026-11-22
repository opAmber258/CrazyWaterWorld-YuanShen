package com.opamb.barotrauma.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Short-lived falling droplets leaving an opening or splashing off a receiving surface.
 */
public final class ShipSprayParticle extends TextureSheetParticle {

    private ShipSprayParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz) {
        super(level, x, y, z);
        this.setSize(0.03F, 0.03F);
        this.quadSize = 0.046F + this.random.nextFloat() * 0.064F;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 8 + this.random.nextInt(8);
        var color = UnderwaterEffects.waterLight(y);
        setColor(0.60F + color.red(), 0.73F + color.green(), 0.76F + color.blue());
        setAlpha(0.83F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.yd -= 0.05D;
        this.move(this.xd, this.yd, this.zd);
        if (onGround || this.lifetime-- <= 0) {
            this.remove();
        } else if (this.lifetime < 5) {
            setAlpha(0.16F * this.lifetime);
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
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            ShipSprayParticle particle = new ShipSprayParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
