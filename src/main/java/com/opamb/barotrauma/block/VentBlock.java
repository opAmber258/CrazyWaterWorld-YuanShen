package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.VentBlockEntity;
import com.opamb.barotrauma.ship.ShipCores;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 通风口（临时单格占位方块）。
 *
 * <p>原作是一个 0.73 格见方的墙挂通风口，只负责把氧送进它所在的 hull。
 * 现在先用普通立方体把"制氧机 → 通风口 → 舱室"这条链路跑通，模型以后再换。</p>
 */
public class VentBlock extends Block implements EntityBlock {

    public VentBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 临时方块：普通立方体模型（不是 GeckoLib 的 ENTITYBLOCK_ANIMATED）。
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VentBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        // 物理船上新放的设备要进船体几何和靠港还原快照，否则靠泊后留在船坞。
        ShipCores.refreshNearby(level, pos);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<VentBlockEntity>)
                VentBlockEntity::serverTick;
    }
}
