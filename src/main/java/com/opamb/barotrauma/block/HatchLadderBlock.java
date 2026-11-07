package com.opamb.barotrauma.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 带梯子的水平舱口（变种方块）：舱口和铁梯做进同一个方块里。
 *
 * <p>和普通 {@link HatchBlock} 的区别只有一条：梯子是方块本身的一部分，
 * <b>不受开合状态影响</b>——关着也照样看得见、爬得上去，右键只开关舱口。
 * 只有中段挂梯子（和方块实体、舱口模型一致），左右两段仍是普通舱口两侧的薄板。</p>
 *
 * <p>梯面朝向跟舱口长轴垂直：舱口 {@code FACING=north} 时梯子朝东，
 * 和对接井里上下贯通的铁梯保持同一平面（都是 {@code facing=east}）。</p>
 */
public class HatchLadderBlock extends HatchBlock {

    private static final VoxelShape PLATE_NS = Shapes.box(
            0.0D, 0.0D, 7.0D / 16.0D,
            1.0D, 1.0D, 9.0D / 16.0D);
    private static final VoxelShape PLATE_EW = Shapes.box(
            7.0D / 16.0D, 0.0D, 0.0D,
            9.0D / 16.0D, 1.0D, 1.0D);

    public HatchLadderBlock(Properties properties) {
        super(properties);
    }

    /** 梯面朝向：和舱口正面垂直（舱口朝北 → 梯子朝东）。 */
    public static Direction ladderFacing(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    /** 梯子薄板：只有中段有。 */
    private static VoxelShape plate(BlockState state) {
        if (state.getValue(PART) != Part.MIDDLE) {
            return Shapes.empty();
        }
        return ladderFacing(state).getAxis() == Direction.Axis.X ? PLATE_EW : PLATE_NS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return Shapes.or(super.getShape(state, level, pos, context), plate(state));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.or(super.getCollisionShape(state, level, pos, context), plate(state));
    }

    /** 梯子常驻：舱口关着也能爬（爬到顶会被关着的舱口面板挡住）。 */
    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return state.getValue(PART) == Part.MIDDLE;
    }
}
