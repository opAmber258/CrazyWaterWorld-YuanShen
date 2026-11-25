package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.AdvancedTrainingDummyStationBlockEntity;
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
 * 高级充气台：外观金/黑石/发光泵，台面上生成默认 50 血、无受击间隔的高级人偶。
 *
 * <p>右键打开设置面板：血量 1..200、是否自发光、重生间隔。</p>
 */
public class AdvancedTrainingDummyStationBlock extends TrainingDummyStationBlock {

    public AdvancedTrainingDummyStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AdvancedTrainingDummyStationBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        return DummyStationMenu.open(level, pos, player);
    }
}
