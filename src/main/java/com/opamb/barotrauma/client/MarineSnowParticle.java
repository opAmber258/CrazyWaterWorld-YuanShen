package com.opamb.barotrauma.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Small suspended particles drifting within the open ocean around the viewer. */
final class MarineSnowParticle extends TextureSheetParticle {
    private static final Optional<ParticleGroup> GROUP = Optional.of(new ParticleGroup(180));

    private MarineSnowParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = vx;
        yd = vy;
        zd = vz;
        friction = 1.0F;
        hasPhysics = false;
        lifetime = 45 + random.nextInt(34);
        quadSize = 0.016F + random.nextFloat() * 0.032F;
        var color = UnderwaterEffects.waterLight(y);
        setColor(0.44F + color.red(), 0.53F + color.green(), 0.58F + color.blue());
        setAlpha(0.0F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (age % 6 == 0 && !UnderwaterEffects.isOpenOcean(level, new Vec3(x, y, z))) {
            remove();
            return;
        }
        setAlpha(0.53F * Math.max(0.0F, Math.min(1.0F, Math.min(age / 8.0F, (lifetime - age) / 18.0F))));
    }

    @Override
    protected int getLightColor(float partialTick) {
        int light = super.getLightColor(partialTick);
        return LightTexture.pack(Math.max(3, LightTexture.block(light)), LightTexture.sky(light));
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return GROUP;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public MarineSnowParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                 double x, double y, double z, double vx, double vy, double vz) {
            return new MarineSnowParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
