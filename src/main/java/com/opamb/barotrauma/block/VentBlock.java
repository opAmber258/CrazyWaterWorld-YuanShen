package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.VentBlockEntity;
import net.minecraft.core.BlockPos;
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
