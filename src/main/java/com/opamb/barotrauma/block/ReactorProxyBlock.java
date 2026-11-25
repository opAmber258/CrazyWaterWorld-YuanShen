package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.ReactorBlockEntity;
import com.opamb.barotrauma.menu.ReactorMenu;
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
 * 物理船反应堆的交互锚点。
 *
 * <p>反应堆方块被 VS2 收进刚体后，世界里右键不到它；装配时在反应堆主方块
 * 原位放这一个不可见、不碰撞的代理，跟着船整格移动。右键它打开反应堆面板，
 * 菜单/操作包通过 {@link VsPilot#reactorBlockEntity(ServerLevel)} 找到船坞里的真实方块实体。</p>
 */
public class ReactorProxyBlock extends Block {

    public ReactorProxyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.CONSUME;
            }
            ReactorBlockEntity reactor = VsPilot.reactorBlockEntity(serverLevel);
            if (reactor == null) {
                return InteractionResult.CONSUME;
            }
            Component title = Component.translatable(
                    reactor.getBlockState().getBlock().getDescriptionId());
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new ReactorMenu(id, inventory, pos),
                            title),
                    buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }
}
