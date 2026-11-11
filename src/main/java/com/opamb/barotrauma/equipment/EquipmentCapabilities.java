package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家装备数据的 Capability 句柄与注册。
 *
 * <p>Forge 1.20.1 没有 Data Attachments，沿用医疗模块的经典 Capability 写法：
 * {@link RegisterCapabilitiesEvent} 声明类型（mod 总线），{@link EquipmentEvents}
 * 在 {@code AttachCapabilitiesEvent} 时把 {@link EquipmentProvider} 挂到玩家身上。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EquipmentCapabilities {

    public static final Capability<EquipmentInventory> EQUIPMENT =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private EquipmentCapabilities() {
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(EquipmentInventory.class);
    }
}
