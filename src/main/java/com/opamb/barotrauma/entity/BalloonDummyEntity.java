package com.opamb.barotrauma.entity;

import com.opamb.barotrauma.block.BalloonStationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 训练气球：沿台座 FACING 方向伸出，占台座相邻的一格；1 血。
 *
 * <p>实体原点（碰撞箱底面）在气球中心正下方 {@link #HITBOX_RADIUS} 格，
 * 渲染器再从原点抬到气球中心并旋转模型，让绳子指回台座。</p>
 */
public class BalloonDummyEntity extends TrainingDummyEntity {

    /** 台座中心 → 气球中心 的距离（格）：19 px，气球正好落在相邻格靠外的一半。 */
    public static final double STATION_OFFSET = 1.1875D;
    /** 碰撞箱半高（格）：气球直径 10 px = 0.625 格。 */
    public static final double HITBOX_RADIUS = 0.3125D;

    /** 同步给客户端的朝向：客户端拿不到 stationPos（只存在服务端 NBT），必须单独同步。 */
    private static final EntityDataAccessor<Direction> DATA_FACING =
            SynchedEntityData.defineId(BalloonDummyEntity.class, EntityDataSerializers.DIRECTION);

    public BalloonDummyEntity(EntityType<? extends BalloonDummyEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FACING, Direction.UP);
    }

    @Override
    public void tick() {
        super.tick();
        // 模型朝向完全由 DATA_FACING 决定；实体 yaw 固定，保持实体状态干净
        // （渲染器本身不读 yaw）。
        setYRot(0.0F);
        setYHeadRot(0.0F);
        yBodyRot = 0.0F;
        yBodyRotO = 0.0F;
        yHeadRotO = 0.0F;
        yRotO = 0.0F;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 0.0D);
    }

    @Override
    protected boolean bindToStation() {
        BlockPos pos = stationPos();
        if (pos == null) {
            return false;
        }
        BlockState state = level().getBlockState(pos);
        if (!(state.getBlock() instanceof BalloonStationBlock)) {
            return false;
        }
        Direction facing = state.getValue(BalloonStationBlock.FACING);
        this.entityData.set(DATA_FACING, facing);
        Vec3 center = Vec3.atCenterOf(pos);
        setPos(center.x + facing.getStepX() * STATION_OFFSET,
                center.y + facing.getStepY() * STATION_OFFSET - HITBOX_RADIUS,
                center.z + facing.getStepZ() * STATION_OFFSET);
        setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /** 渲染器用来决定绳子朝向；值已同步到客户端。 */
    public Direction stationFacing() {
        return this.entityData.get(DATA_FACING);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // 气球破裂：一团 POOF + 破裂声；渲染器同时做胀大→缩没的死亡动画。
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF,
                    getX(), getY() + HITBOX_RADIUS, getZ(),
                    12, 0.2D, 0.2D, 0.2D, 0.02D);
            level().playSound(null, blockPosition(), SoundEvents.ITEM_BREAK,
                    SoundSource.NEUTRAL, 0.8F, 1.4F);
        }
    }
}
