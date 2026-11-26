package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.item.BrightnessDebuggerItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 亮度调节器的 Shift/Ctrl + 右键快切海雾。
 *
 * <p>普通右键由 {@link BrightnessDebuggerItem} 打开 {@link BrightnessDebuggerScreen}；
 * 按住 Shift 或 Ctrl 时这里先在方块交互之前截住点击，只切换海雾，避免瞄准设备时
 * 被机器面板吃掉。状态本身仍然存在 {@link DeepBrightnessDebug}，与面板共用。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class BrightnessFogShortcut {

    private static final String MESSAGE = "item.barotrauma.brightness_debugger.";
    private static boolean held;

    private BrightnessFogShortcut() {
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !modifierDown()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || !(mc.player.getItemInHand(event.getHand()).getItem() instanceof BrightnessDebuggerItem)) {
            return;
        }
        // 先于方块/设备交互处理，否则瞄准中的机器会吃掉这次点击。
        event.setSwingHand(false);
        event.setCanceled(true);
        if (held) {
            return;
        }
        held = true;
        if (!DeepBrightnessDebug.canUse()) {
            mc.player.displayClientMessage(Component.translatable(MESSAGE + "scope"), true);
            return;
        }
        boolean fogOff = !DeepBrightnessDebug.seaFogDisabled();
        DeepBrightnessDebug.setSeaFogDisabled(fogOff);
        mc.player.displayClientMessage(
                Component.translatable(MESSAGE + (fogOff ? "fog_off" : "fog_on")), true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().options.keyUse.isDown()) {
            held = false;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        held = false;
    }

    private static boolean modifierDown() {
        return Screen.hasShiftDown() || Screen.hasControlDown()
                || Minecraft.getInstance().options.keyShift.isDown();
    }
}
