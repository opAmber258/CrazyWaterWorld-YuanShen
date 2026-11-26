package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.ClientConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.MouseSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class TurretMouseSettings {
    private static final String LABEL = "options.barotrauma.turret_sensitivity";

    private TurretMouseSettings() {}

    @SubscribeEvent
    public static void addSensitivity(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MouseSettingsScreen)) return;
        for (var listener : event.getListenersList()) {
            if (listener instanceof OptionsList options) {
                options.addBig(new OptionInstance<>(LABEL,
                        OptionInstance.cachedConstantTooltip(Component.translatable(LABEL + ".tooltip")),
                        (label, value) -> Component.translatable(LABEL + ".value", label, value),
                        new OptionInstance.IntRange(25, 300), ClientConfig.TURRET_SENSITIVITY.get(),
                        value -> {
                            ClientConfig.TURRET_SENSITIVITY.set(value);
                            ClientConfig.TURRET_SENSITIVITY.save();
                        }));
                break;
            }
        }
    }
}
