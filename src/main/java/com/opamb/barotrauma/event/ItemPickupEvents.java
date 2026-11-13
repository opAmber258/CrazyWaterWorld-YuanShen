package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 掉落物只能用右键捡（走过去不再自动拾取，见 {@code ItemEntityMixin}）。
 *
 * <p>右键对着地上的物品实体：服务端把这堆东西按正常的拾取规则塞进背包
 * （装备背包 → 热键栏；双手持物品进双手格），剩下的留在原地。客户端只负责
 * 取消原版交互并挥手。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemPickupEvents {

    private ItemPickupEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ItemEntity itemEntity)) {
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            // 交互包已经在客户端发过了；这里只挡掉本地处理，避免重复。
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        // 和原版拾取一致：别人丢的东西不能抢（Owner 不匹配就拒绝）。
        if (itemEntity.getOwner() != null
                && !itemEntity.getOwner().equals(player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ItemStack remaining = stack.copy();
        if (!player.getInventory().add(remaining)) {
            return;
        }
        if (remaining.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(remaining);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
