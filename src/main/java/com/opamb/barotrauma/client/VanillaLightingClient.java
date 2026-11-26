package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * F7：深海光照模式的快捷切换键（模组光照 ↔ 原版光照）。
 *
 * <p>设置本体与面板在 {@link DeepBrightnessDebug} / {@link BrightnessDebuggerScreen}；
 * 这里只负责键位轮询和动作栏反馈。原版模式的效果由光图 mixin、雾效与水体渲染
 * 共同读取同一份设置。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class VanillaLightingClient {

    public static final KeyMapping TOGGLE = new KeyMapping("key.barotrauma.vanilla_lighting",
            GLFW.GLFW_KEY_F7, "key.categories.barotrauma");

    private VanillaLightingClient() {
    }

    /** true = 当前允许的深海世界里使用原版光图与原版亮度滑杆，手电后处理关闭。 */
    public static boolean isVanilla() {
        return DeepBrightnessDebug.effectiveVanilla();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            TOGGLE.consumeClick();
            return;
        }
        if (!TOGGLE.consumeClick()) {
            return;
        }
        if (!DeepBrightnessDebug.canUse()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(
                        "item.barotrauma.brightness_debugger.scope"), true);
            }
            return;
        }
        DeepBrightnessDebug.toggleMode();
        BarotraumaMod.LOGGER.info("[light] 原版光照切换：vanilla={}", isVanilla());
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(
                    isVanilla() ? "lighting.barotrauma.vanilla_on" : "lighting.barotrauma.vanilla_off"), true);
        }
    }
}
