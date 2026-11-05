package com.opamb.barotrauma.block;

import com.opamb.barotrauma.menu.PumpMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * 物理船排水泵的交互锚点。
 *
 * <p>VS2 把泵方块收进刚体后，世界里右键不到泵；这里在泵原位放一个不可见、
 * 不碰撞的方块，跟着船整格移动，右键它打开泵菜单。菜单服务端会通过
 * {@code VsPilot.pumpBlockEntity()} 找到船坞里的真实泵方块实体。</p>
 */
public class PumpProxyBlock extends Block {

    public PumpProxyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new PumpMenu(id, inventory, pos),
                            Component.translatable("block.barotrauma.drain_pump")),
                    buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }
}
