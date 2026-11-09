package com.opamb.barotrauma.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * 6700X3D 处理器（彩蛋方块）：12×12 的基板 + 10×10 的顶盖，高 3 格像素。
 *
 * <p>纯装饰；外观照用户指令做得尽量简洁：只有两个长方体，顶盖贴图带丝印。
 * 方块本身不可旋转，放置即固定朝向。右键已经放下的方块会显示本机配置面板，
 * 放置物品本身不触发（2026-11-09 用户要求）。</p>
 */
public class Cpu6700X3DBlock extends Block {

    /** 与模型几何一致：x/z 2…14，y 0…3（1/16 格为 1 像素）。 */
    private static final VoxelShape SHAPE =
            Shapes.box(2.0 / 16.0, 0.0, 2.0 / 16.0, 14.0 / 16.0, 3.0 / 16.0, 14.0 / 16.0);

    public Cpu6700X3DBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.opamb.barotrauma.client.CpuInfoOverlay.show());
        }
        return InteractionResult.SUCCESS;
    }
}
