package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 抱着双手持物品时两只手都占着：不能用主手物品、不能攻击、不能右键方块/物品。
 *
 * <p>服务端和客户端都会收到这些事件，两边一起取消，避免客户端先播动画。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TwoHandedEvents {

    private TwoHandedEvents() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (TwoHanded.isCarrying(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!TwoHanded.isCarrying(event.getEntity())) {
            return;
        }
        // 抱着箱子时右键空手/空气 → 打开箱子容器界面（和原作点容器同义）。
        if (event.getItemStack().getItem() instanceof com.opamb.barotrauma.item.CrateItem crate
                && crate.capacity() > 0) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!TwoHanded.isCarrying(event.getEntity())) {
            return;
        }
        // 抱着箱子还能往箱子货架上码（货架的 use 自己处理），别的方块一律不让点。
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof CabinetBlock cabinet
                && cabinet.kind() == CabinetBlock.Kind.CRATE_SHELF) {
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (TwoHanded.isCarrying(player)) {
            event.setCanceled(true);
        }
    }
}
