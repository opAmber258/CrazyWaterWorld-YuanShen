package com.opamb.barotrauma.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 暗绿马赛克瓷砖墙：潜渊症风格的装饰方块。
 *
 * <p>放置时按固定概率权重从 8 张差分贴图里随机一张写进 blockstate 的
 * {@link #VARIANT}，之后该位置保持不变；挖掉重放会重新掷骰。权重为
 * 标准 30%（3/10），血污 1-4 与脏污 1-3 各 10%（各 1/10）。手持物品形态
 * 固定显示标准贴图（item model 直接引用标准模型）。无合成方式，仅创造栏获取。</p>
 */
public class DarkGreenMosaicTileWallBlock extends Block {

    /** 贴图差分：0 = 标准；1-4 = 血污 1-4；5-7 = 脏污 1-3。 */
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 7);

    public DarkGreenMosaicTileWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(VARIANT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(VARIANT, pickWeightedVariant(context.getLevel().random));
    }

    /** 掷骰：标准 30%，其余七种差分各 10%。 */
    private static int pickWeightedVariant(RandomSource random) {
        int roll = random.nextInt(10);
        return roll < 3 ? 0 : 1 + random.nextInt(7);
    }
}
