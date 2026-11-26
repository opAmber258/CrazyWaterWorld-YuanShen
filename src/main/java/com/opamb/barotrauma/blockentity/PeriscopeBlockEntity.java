package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** 潜望镜方块实体：静态 GeckoLib 模型；炮塔接管逻辑在 {@code PeriscopeBlock}。 */
public class PeriscopeBlockEntity extends BlockEntity implements GeoBlockEntity {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PeriscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERISCOPE.get(), pos, state);
    }

    private BlockPos turretOffset;

    @Nullable
    public BlockPos turretPos() {
        return turretOffset == null ? null : worldPosition.offset(turretOffset);
    }

    public void bindTo(@Nullable BlockPos turret) {
        turretOffset = turret == null ? null : turret.subtract(worldPosition);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (turretOffset != null) tag.putLong("TurretOffset", turretOffset.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        turretOffset = tag.contains("TurretOffset") ? BlockPos.of(tag.getLong("TurretOffset")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态模型。
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
