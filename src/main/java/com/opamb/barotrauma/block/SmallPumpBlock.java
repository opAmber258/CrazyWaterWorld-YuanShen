package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.DrainPumpBlockEntity;
import com.opamb.barotrauma.blockentity.SmallPumpBlockEntity;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 小型泵：和普通泵共用流量/自动控制逻辑，但渲染改用 GeckoLib 体素模型
 * （原版小型泵的水泵 + 水传感器组合体），所以方块本身是 INVISIBLE。
 */
public class SmallPumpBlock extends DrainPumpBlock {

    /**
     * 紧贴模型外形的碰撞：模型实际只占 x 2.55..13.45、y 0..5.93、z 5.78..10.22 像素。
     * 之前用 12×12×12 的方块套，泵周围一圈都是"撞空气"，现在按模型包围盒收紧。
     */
    private static final VoxelShape SHAPE = Shapes.box(
            2.55D / 16.0D, 0.0D, 5.78D / 16.0D,
            13.45D / 16.0D, 5.93D / 16.0D, 10.22D / 16.0D);

    public SmallPumpBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmallPumpBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SMALL_PUMP.get(),
                DrainPumpBlockEntity::serverTick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return SHAPE;
    }
}
