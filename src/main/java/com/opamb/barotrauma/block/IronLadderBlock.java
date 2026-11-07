package com.opamb.barotrauma.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 免支撑铁梯：整格宽、半格厚的钢框梯，立在对接井正中。
 *
 * <p>原版梯子必须贴墙；对接井的舱口在井正中，铁梯只能立在中间，所以这里重写
 * {@link LadderBlock} 的支撑判断和 {@code updateShape}，让它像薄板一样独立存在。</p>
 *
 * <p>模型是用户要的"加音效之前那版"：整格宽、2 像素厚的 cutout 梯子图案
 * （两根立柱 + 5 根横档，透明背景），居中放在方块里（z 7..9），像一块立在
 * 方块正中的玻璃板。碰撞是同一块 2px 居中板：人能摸到、会被挡住，脚下方块
 * 仍是铁梯，可以正常攀爬。</p>
 */
public class IronLadderBlock extends LadderBlock {

    /**
     * 居中薄板：16×16×2 像素，放在方块正中。
     *
     * <p>南北朝向时薄板沿 x（z 7..9），东西朝向时转 90° 沿 z（x 7..9）。
     * 2px 板两侧各留 7px，玩家宽 0.6 格（9.6px），贴着板站时中心仍在
     * 铁梯格内（偏移约 0.14 或 0.86），脚下方块是铁梯，攀爬判定正常。</p>
     */
    private static final VoxelShape PLATE_NS = Shapes.box(
            0.0D, 0.0D, 7.0D / 16.0D,
            1.0D, 1.0D, 9.0D / 16.0D);
    private static final VoxelShape PLATE_EW = Shapes.box(
            7.0D / 16.0D, 0.0D, 0.0D,
            9.0D / 16.0D, 1.0D, 1.0D);

    private static VoxelShape plate(BlockState state) {
        Direction facing = state.getValue(FACING);
        return facing.getAxis() == Direction.Axis.X ? PLATE_EW : PLATE_NS;
    }

    public IronLadderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // 不检查支撑；水下的梯子照常排掉那一格水。
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return plate(state);
    }

    /** 碰撞和外形一致：2px 居中板是实心的，人不能穿过去。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return plate(state);
    }
}
