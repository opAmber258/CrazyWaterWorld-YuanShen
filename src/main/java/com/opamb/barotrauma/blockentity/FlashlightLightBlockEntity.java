package com.opamb.barotrauma.blockentity;

import com.opamb.barotrauma.block.FlashlightLightBlock;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 清理旧手电光源留下的存档方块。 */
public class FlashlightLightBlockEntity extends BlockEntity {

    public FlashlightLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLASHLIGHT_LIGHT.get(), pos, state);
    }

    /** 恢复成放置前的空气/水，然后让方块实体随方块一起消失。 */
    public void selfRemove() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FlashlightLightBlock)) {
            return;
        }
        boolean water = state.getValue(LightBlock.WATERLOGGED);
        level.setBlock(worldPosition,
                water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
    }
}
