package com.opamb.barotrauma.block;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.item.CoilgunAmmoBoxItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 电磁炮：复用炮塔的瞄准/相机/占用/网络逻辑，只替换武器规格和装填交互。
 *
 * <p>装填照原版 {@code coilgunloader}：手持弹箱右键炮塔装入；空手蹲下右键取出。
 * 装填器方块（可远程供弹）还没做，先支持直接装填。</p>
 */
public class CoilgunBlock extends TurretBlock {

    public CoilgunBlock(Properties properties) {
        super(properties);
    }

    @Override
    public TurretWeaponKind weaponKind() {
        return TurretWeaponKind.COILGUN;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            if (main.getItem() instanceof CoilgunAmmoBoxItem) {
                if (turret.loadAmmo(serverPlayer, main)) {
                    return InteractionResult.CONSUME;
                }
            } else if (off.getItem() instanceof CoilgunAmmoBoxItem) {
                if (turret.loadAmmo(serverPlayer, off)) {
                    return InteractionResult.CONSUME;
                }
            }
            if (player.isShiftKeyDown() && main.isEmpty() && turret.unloadAmmo(serverPlayer)) {
                return InteractionResult.CONSUME;
            }
        }
        return super.use(state, level, pos, player, hand, hit);
    }
}
