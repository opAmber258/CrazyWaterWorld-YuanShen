package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.DrainPumpBlockEntity;
import com.opamb.barotrauma.menu.PumpMenu;
import com.opamb.barotrauma.ship.vs.VsPilot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

/**
 * 物理船排水泵的交互锚点。
 *
 * <p>VS2 把泵方块收进刚体后，世界里右键不到泵；这里在泵原位放一个不可见、
 * 不碰撞的方块，跟着船整格移动，右键它打开泵菜单。菜单服务端会通过
 * {@code VsPilot.pumpBlockEntity(pos)} 找到船坞里对应的那台泵方块实体。</p>
 *
 * <p>锚点本身不可破坏（strength -1），但瞄准形状必须贴着小泵，不能是整格：
 * 整格的透明描边会让玩家以为泵上多了一个"透明不可破坏方块"（用户实测）。</p>
 */
public class PumpProxyBlock extends Block {

    /** 小泵模型约 10.9×5.9×4.4 像素；描边取 12×6×12，够点右键又不显眼。 */
    private static final VoxelShape SHAPE = Shapes.box(
            2.0D / 16.0D, 0.0D, 2.0D / 16.0D,
            14.0D / 16.0D, 6.0D / 16.0D, 14.0D / 16.0D);

    public PumpProxyBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 船坞偏移还没扫出来/真实泵不可用时先不打开空菜单；解析完成后玩家可再次右键。
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.CONSUME;
            }
            // 每台泵都有独立代理：按被右键的代理位置解析出对应的真实泵，
            // 否则小泵/排水泵会全部打开压载泵菜单。
            DrainPumpBlockEntity resolved = VsPilot.pumpBlockEntity(serverLevel, pos);
            if (resolved == null) {
                return InteractionResult.CONSUME;
            }
            // 标题跟随真实泵类型（压载泵 / 排水泵 / 小型泵），不要一律写"排水泵"。
            Component title = Component.translatable(
                    resolved.getBlockState().getBlock().getDescriptionId());
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new PumpMenu(id, inventory, pos),
                            title),
                    buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }
}
