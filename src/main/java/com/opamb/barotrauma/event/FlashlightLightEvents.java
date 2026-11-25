package com.opamb.barotrauma.event;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.equipment.ItemSubSlot;
import com.opamb.barotrauma.item.FlashlightItem;
import com.opamb.barotrauma.item.FlashlightState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/** 电池由服务端消耗，照明只同步灯的位置类型和低电标志。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID)
public final class FlashlightLightEvents {
    private static final int TICKS_PER_CHARGE = 100;
    // 收起再拿出来延续同一支手电的本次消耗周期。丢弃的物品栈不会被长期持有。
    private static final Map<ItemStack, Integer> DRAIN_PROGRESS = new WeakHashMap<>();

    private FlashlightLightEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        FlashlightItem.Emitter emitter = FlashlightItem.findEmitter(player);
        byte light = FlashlightState.OFF;
        if (emitter != null) {
            if (!player.getAbilities().instabuild) {
                drain(player, emitter);
            }
            light = emitter.state();
        }
        ((FlashlightState) player).barotrauma$setFlashlight(light);
    }

    private static void drain(ServerPlayer player, FlashlightItem.Emitter emitter) {
        ItemStack stack = emitter.stack();
        int elapsed = DRAIN_PROGRESS.getOrDefault(stack, 0) + 1;
        if (elapsed < TICKS_PER_CHARGE) {
            DRAIN_PROGRESS.put(stack, elapsed);
            return;
        }
        DRAIN_PROGRESS.remove(stack);
        ItemStack battery = emitter.battery();
        battery.setDamageValue(battery.getDamageValue() + 1);
        ItemSubSlot.set(stack, battery);
        player.getInventory().setChanged();
        // 头部子格也在 inventoryMenu 里，打开其它容器时仍要同步电量。
        player.inventoryMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DRAIN_PROGRESS.clear();
    }
}
