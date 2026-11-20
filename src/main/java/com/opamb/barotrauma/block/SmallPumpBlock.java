package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.DrainPumpBlockEntity;
import com.opamb.barotrauma.blockentity.SmallPumpBlockEntity;
import com.opamb.barotrauma.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 小型泵：和普通泵共用流量/自动控制逻辑，但渲染改用 GeckoLib 体素模型
 * （原版小型泵的水泵 + 水传感器组合体），所以方块本身是 INVISIBLE。
 *
 * <p>朝向跟玩家放置时的水平朝向相反（正面朝向玩家），模型和碰撞一起转：
 * 模型在 geo 里正面朝北，南北朝向时宽沿 x，东西朝向时宽沿 z。</p>
 */
public class SmallPumpBlock extends DrainPumpBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * 紧贴模型**高度**、但占满整格 footprint 的低平台（6px）。
     *
     * <p>2026-11-19 用户实测：站在引擎旁边这个小泵上，开船时会被挤到船壳外掉海里。
     * 小泵原来只有模型那么宽的一条窄台（0.68×0.28 格），人站上去大半个脚掌悬空，
     * 又正好卡在船壳/引擎的夹角里，VS2 的船体碰撞解算会把人往外挤。舱口那块
     * 1px 板之所以没这个问题，是因为它 footprint 是满格的。</p>
     *
     * <p>改成满格 footprint、高度仍按模型（5.93px）：从任何方向都能直接踩上去
     *（自动上台阶 0.6 &gt; 0.37），人不会再站在一条悬空窄台上。</p>
     */
    private static final VoxelShape SHAPE = Shapes.box(
            0.0D, 0.0D, 0.0D,
            1.0D, 5.93D / 16.0D, 1.0D);

    public SmallPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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

    private static VoxelShape shape(BlockState state) {
        return SHAPE;
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
        return shape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return shape(state);
    }
}
