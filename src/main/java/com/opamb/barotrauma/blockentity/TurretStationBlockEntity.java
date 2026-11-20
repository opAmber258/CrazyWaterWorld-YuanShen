package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 炮塔瞄准站方块实体（开发者工具）。
 *
 * <p>只存一个绑定目标：炮塔的方块坐标。绑定由 {@code TurretBinderItem} 写入，
 * 右键站台时服务端按这个坐标把操作权交给炮塔。目标不存反向引用——一个炮塔
 * 可以同时被多个站台绑定，炮塔仍然只允许一个操作者。</p>
 *
 * <p>绑定同步到客户端（{@link #getUpdateTag()} + {@link #getUpdatePacket()}），
 * 手持绑定器时客户端扫描已加载区块里的站台实体来画连线。</p>
 */
public class TurretStationBlockEntity extends BlockEntity {

    private static final String TAG_TURRET_POS = "TurretPos";

    @Nullable
    private BlockPos turretPos;

    public TurretStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURRET_STATION.get(), pos, state);
    }

    @Nullable
    public BlockPos turretPos() {
        return turretPos;
    }

    public boolean isBound() {
        return turretPos != null;
    }

    public boolean boundTo(BlockPos pos) {
        return pos.equals(turretPos);
    }

    public void bindTo(BlockPos pos) {
        this.turretPos = pos.immutable();
        setChanged();
    }

    public void clearBinding() {
        if (turretPos != null) {
            turretPos = null;
            setChanged();
        }
    }

    /**
     * 绑定变化要立刻同步给客户端：站台实体没有 ticker，不主动发包的话，
     * 绑定器连线要等区块重载才能看到。
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveBinding(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveBinding(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.turretPos = tag.contains(TAG_TURRET_POS)
                ? BlockPos.of(tag.getLong(TAG_TURRET_POS))
                : null;
    }

    private void saveBinding(CompoundTag tag) {
        if (turretPos != null) {
            tag.putLong(TAG_TURRET_POS, turretPos.asLong());
        }
    }
}
