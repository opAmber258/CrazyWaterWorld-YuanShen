package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家医疗数据的 Capability 句柄与注册。
 *
 * <p>Forge 1.20.1 没有 Data Attachments（那是 1.20.2+ 的 API），所以走经典
 * Capability：{@link RegisterCapabilitiesEvent} 声明类型（mod 总线），
 * {@link MedicalEvents} 在 AttachCapabilitiesEvent 时把 {@link MedicalProvider}
 * 挂到玩家实体上。</p>
 *
 * <p>生命周期：死亡时原版会克隆新实体，新实体天然是空容器（满足 REQUIREMENTS §5.5
 * 的"死亡清空"验收点）；非死亡克隆（如换维度）由 {@link MedicalEvents} 监听
 * {@code PlayerEvent.Clone} 复制旧容器的状态。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MedicalCapabilities {

    public static final Capability<AfflictionContainer> MEDICAL =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private MedicalCapabilities() {
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(AfflictionContainer.class);
    }

    /** 玩家身上取容器；未挂载（理论上不会发生）返回 null。 */
    public static AfflictionContainer container(Player player) {
        return player.getCapability(MEDICAL).orElse(null);
    }
}
