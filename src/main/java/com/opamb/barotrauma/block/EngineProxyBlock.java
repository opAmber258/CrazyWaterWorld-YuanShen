package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.EngineBlockEntity;
import com.opamb.barotrauma.menu.EngineMenu;
import com.opamb.barotrauma.ship.vs.VsPilot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/**
 * 物理船引擎的交互锚点：引擎方块被 VS2 收进刚体后靠它打开只读引擎面板。
 */
public class EngineProxyBlock extends Block {

    public EngineProxyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.CONSUME;
            }
            EngineBlockEntity engine = VsPilot.engineBlockEntity(serverLevel);
            if (engine == null) {
                return InteractionResult.CONSUME;
            }
            Component title = Component.translatable(
                    engine.getBlockState().getBlock().getDescriptionId());
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new EngineMenu(id, inventory, pos),
                            title),
                    buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }
}
