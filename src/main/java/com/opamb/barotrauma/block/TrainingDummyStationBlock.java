package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.TrainingDummyStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 训练人偶充气台：放下后在台面上生成一只 1 血、不会移动的训练人偶。
 *
 * <p>人偶与方块绑定：方块被拆时人偶消失；人偶被打死后由方块实体在 2 秒后重新召唤。
 * 台面模型是临时占位（铁块底座 + 红色充气泵），人偶模型也是临时低模。</p>
 */
public class TrainingDummyStationBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public TrainingDummyStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrainingDummyStationBlockEntity(pos, state);
    }

    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        // 方块本体用普通 JSON 模型；人偶是独立实体。
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<TrainingDummyStationBlockEntity>)
                TrainingDummyStationBlockEntity::serverTick;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof TrainingDummyStationBlockEntity station) {
            station.discardDummy();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
