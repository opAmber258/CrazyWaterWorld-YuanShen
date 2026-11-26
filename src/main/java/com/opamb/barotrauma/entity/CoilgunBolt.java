package com.opamb.barotrauma.entity;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.registry.ModParticles;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.world.OceanEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;

public final class CoilgunBolt extends Projectile {
    // The mod's visual bolt speed was 24 blocks per second. XML sets a three-second lifetime.
    public static final double SPEED = 24.0 / 20;
    private static final int LIFETIME = 60;
    public static final double RANGE = SPEED * LIFETIME;
    public static final float DAMAGE = 15;
    public static final float STRUCTURE_DAMAGE = 20;

    public CoilgunBolt(EntityType<? extends CoilgunBolt> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 start = position();
        Vec3 velocity = getDeltaMovement();
        Vec3 end = start.add(velocity);
        if (level() instanceof ServerLevel server) {
            if (tickCount > LIFETIME || !server.hasChunkAt(BlockPos.containing(end))) {
                discard();
                return;
            }
            HitResult hit = findImpact(start, end);
            if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
                setPos(hit.getLocation());
                onHit(hit);
                server.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 6, 0.09, 0.09, 0.09, 0.10);
                discard();
                return;
            }
        } else {
            trail(start, end);
        }
        setPos(end);
    }

    private HitResult findImpact(Vec3 start, Vec3 end) {
        BlockHitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (block.getType() == HitResult.Type.BLOCK) {
            Vec3 world = VsState.active()
                    ? VsPilot.shipyardPointToWorld(level(), block.getBlockPos(), block.getLocation()) : block.getLocation();
            // Some VS versions return a shipyard block position together with a world hit point.
            if (world.distanceToSqr(start) < block.getLocation().distanceToSqr(start)) {
                block = new BlockHitResult(world, block.getDirection(), block.getBlockPos(), block.isInside());
            }
            end = block.getLocation();
        }
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level(), this, start, end,
                getBoundingBox().expandTowards(end.subtract(start)).inflate(0.4), this::canHitEntity);
        return entity == null ? block : entity;
    }

    @Override
    protected boolean canHitEntity(net.minecraft.world.entity.Entity target) {
        return target instanceof LivingEntity && super.canHitEntity(target)
                && (!(getOwner() instanceof ServerPlayer shooter && target instanceof ServerPlayer victim)
                || shooter.canHarmPlayer(victim));
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        hit.getEntity().hurt(damageSources().thrown(this, getOwner()), DAMAGE);
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (!(level() instanceof ServerLevel server)) return;
        BlockPos pos = hit.getBlockPos();
        if (!ModShipBlocks.isRepairableHull(server.getBlockState(pos))) return;
        ServerPlayer operator = getOwner() instanceof ServerPlayer player ? player : null;
        if (VsState.isPhysical(server, pos)) {
            VsPilot.damageHullAtImpact(server, pos, STRUCTURE_DAMAGE);
            return;
        }
        ShipCoreBlockEntity core = ShipCores.coreOwning(server, pos, 64);
        if (core != null) core.damageBlock(server, pos, STRUCTURE_DAMAGE, operator);
    }

    private void trail(Vec3 start, Vec3 end) {
        boolean wet = OceanEnvironment.isSubmerged(level(), start.lerp(end, 0.5));
        for (int i = 0; i < 5; i++) {
            Vec3 p = start.lerp(end, i / 5.0);
            level().addParticle(ModParticles.TURRET_TRACER.get(), p.x, p.y, p.z, 0, 0, 0);
            if (wet && i % 2 == 0) {
                level().addParticle(ModParticles.TURRET_BUBBLE.get(), p.x, p.y, p.z, 0, 0.04, 0);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
