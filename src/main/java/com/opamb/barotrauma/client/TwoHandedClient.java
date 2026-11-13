package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.item.TwoHanded;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 抱着双手持物品时锁住热键栏高亮：数字键/滚轮切不了物品。
 *
 * <p>服务端已经在 {@code ServerGamePacketListenerMixin} 里丢弃换栏位包；这里再把
 * 客户端本地的 {@code Inventory.selected} 拉回锁定值，避免高亮乱跳。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TwoHandedClient {

    private static int lockedSlot = -1;

    private TwoHandedClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !TwoHanded.isCarrying(minecraft.player)) {
            lockedSlot = -1;
            return;
        }
        int selected = minecraft.player.getInventory().selected;
        if (lockedSlot < 0) {
            lockedSlot = selected;
        } else if (selected != lockedSlot) {
            minecraft.player.getInventory().selected = lockedSlot;
        }
    }
}
