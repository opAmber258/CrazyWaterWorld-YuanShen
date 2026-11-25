package com.opamb.barotrauma.entity;

import com.opamb.barotrauma.block.TrainingDummyStationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 训练人偶：固定站在充气台上，1 血，不会移动、不会被击退。
 *
 * <p>由 {@link com.opamb.barotrauma.blockentity.TrainingDummyStationBlockEntity} 生成；
 * 人偶自己保存台座坐标，台座不存在时自毁。死亡后的重生由台座方块实体负责。</p>
 */
public class TrainingDummyEntity extends PathfinderMob {

    /** 高级版菜单里的“自发光”：满亮度渲染，不吃环境光。 */
    private static final EntityDataAccessor<Boolean> DATA_SELF_ILLUMINATED =
            SynchedEntityData.defineId(TrainingDummyEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private BlockPos stationPos;

    public TrainingDummyEntity(EntityType<? extends TrainingDummyEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        setInvulnerable(false);
        setHealth(1.0F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SELF_ILLUMINATED, false);
    }

    public boolean isSelfIlluminated() {
        return this.entityData.get(DATA_SELF_ILLUMINATED);
    }

    public void setSelfIlluminated(boolean value) {
        this.entityData.set(DATA_SELF_ILLUMINATED, value);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 0.0D);
    }

    public void setStationPos(BlockPos pos) {
        stationPos = pos.immutable();
    }

    @Nullable
    public BlockPos stationPos() {
        return stationPos;
    }

    /** 生成时从 25% 放大到 100%，做一个很短的“充气”出场。 */
    public float spawnScale(float partialTick) {
        float progress = Mth.clamp((tickCount + partialTick) / 8.0F, 0.0F, 1.0F);
        return 0.25F + 0.75F * progress;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            return;
        }
        if (!bindToStation()) {
            discard();
        }
    }

    /**
     * 每 tick 把实体钉回台座；台座不存在或类型不匹配返回 false，由 {@link #tick()} 触发自毁。
     *
     * <p>气球子类会覆盖这个方法，改成沿台座 FACING 方向摆放。</p>
     */
    protected boolean bindToStation() {
        if (stationPos == null) {
            return false;
        }
        BlockState stationState = level().getBlockState(stationPos);
        if (!(stationState.getBlock() instanceof TrainingDummyStationBlock)) {
            return false;
        }
        setPos(stationPos.getX() + 0.5D, stationPos.getY() + 1.0D,
                stationPos.getZ() + 0.5D);
        setDeltaMovement(Vec3.ZERO);

        // 人偶朝向跟随台座 FACING：玩家放下时朝向玩家，旋转台座后也同步。
        float yaw = stationState.hasProperty(TrainingDummyStationBlock.FACING)
                ? stationState.getValue(TrainingDummyStationBlock.FACING).toYRot() : 0.0F;
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        yBodyRotO = yaw;
        yHeadRotO = yaw;
        yRotO = yaw;
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // 固定在台座上，不接受外力推移。
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // 不产生击退，方便连续打同一个点。
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (stationPos != null) {
            tag.putLong("StationPos", stationPos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        stationPos = tag.contains("StationPos") ? BlockPos.of(tag.getLong("StationPos")) : null;
    }
}
