package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.PowerOpenRequestPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 电力面板快捷键：按 P 打开本船最近接线盒。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class PowerClient {

    public static final KeyMapping OPEN_POWER = new KeyMapping("key.barotrauma.power",
            GLFW.GLFW_KEY_P, "key.categories.barotrauma");

    private PowerClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_POWER.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                ModNetwork.sendToServer(new PowerOpenRequestPacket());
            }
        }
    }
}
