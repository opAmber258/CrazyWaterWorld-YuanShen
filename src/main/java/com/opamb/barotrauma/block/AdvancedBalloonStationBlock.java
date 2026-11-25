package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.AdvancedBalloonStationBlockEntity;
import com.opamb.barotrauma.menu.DummyStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 高级气球充气台：六向放置，气球默认 50 血。
 *
 * <p>右键打开设置面板：血量 1..200、是否自发光、重生间隔。</p>
 */
public class AdvancedBalloonStationBlock extends BalloonStationBlock {

    public AdvancedBalloonStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedBalloonStationBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        return DummyStationMenu.open(level, pos, player);
    }
}
