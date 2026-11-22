package com.opamb.barotrauma.client;

import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Entrained air in the receiving water. Lifetime and count follow the particle engine. */
public final class ShipBubbleParticle extends TextureSheetParticle {
    private static final Optional<ParticleGroup> GROUP = Optional.of(new ParticleGroup(160));
    private static Vec3 shipCarry = Vec3.ZERO;

    /** The active ship's displacement per client tick. */
    public static void setShipCarry(double vxPerSecond, double vyPerSecond, double vzPerSecond) {
        shipCarry = new Vec3(vxPerSecond / 20.0D, vyPerSecond / 20.0D, vzPerSecond / 20.0D);
    }

    private ShipBubbleParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vy, double vz) {
        super(level, x, y, z);
        setSize(0.065F, 0.065F);
        quadSize = 0.065F + random.nextFloat() * 0.055F;
        var color = UnderwaterEffects.waterLight(y);
        setColor(0.67F + color.red(), 0.75F + color.green(), 0.77F + color.blue());
        setAlpha(0.76F);
        xd = vx + (random.nextDouble() - 0.5D) * 0.027D;
        yd = vy + (random.nextDouble() - 0.5D) * 0.027D;
        zd = vz + (random.nextDouble() - 0.5D) * 0.027D;
        lifetime = 42 + random.nextInt(19);
        hasPhysics = false;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        yd += 0.004D;
        move(xd + shipCarry.x, yd + shipCarry.y, zd + shipCarry.z);
        xd *= 0.92D;
        yd *= 0.92D;
        zd *= 0.92D;
        if (++age >= lifetime || ((age + lifetime) & 3) == 0
                && !OceanEnvironment.isOceanWaterAt(level, new Vec3(x, y, z))) {
            remove();
        } else if (lifetime - age < 10) {
            setAlpha((lifetime - age) * 0.076F);
        }
    }

    @Override
    public Optional<ParticleGroup> getParticleGroup() {
        return GROUP;
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
            ShipBubbleParticle particle = new ShipBubbleParticle(level, x, y, z, vx, vy, vz);
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
