package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 仅本机当前单人世界的渲染覆盖。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class DeepBrightnessDebug {
    private static final String MESSAGE = "item.barotrauma.brightness_debugger.";
    private static ClientLevel activeLevel;
    private static int percent;
    private static boolean noSeaFog;

    private DeepBrightnessDebug() {
    }

    public static void use(boolean reset) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!canUse(mc)) {
            mc.player.displayClientMessage(Component.translatable(MESSAGE + "scope"), true);
            return;
        }
        if (activeLevel != mc.level) {
            clear();
        }
        if (Screen.hasControlDown()) {
            noSeaFog = !noSeaFog;
            mc.player.displayClientMessage(Component.translatable(MESSAGE + (noSeaFog ? "fog_off" : "fog_on")), true);
        } else {
            percent = nextPercent(percent, reset);
            mc.player.displayClientMessage(Component.translatable(MESSAGE + "current", percent), true);
        }
        activeLevel = percent > 0 || noSeaFog ? mc.level : null;
    }

    public static float ambientMix() {
        return active() ? 0.60F * percent / 100.0F : 0.0F;
    }

    static boolean seaFogDisabled() {
        return active() && noSeaFog;
    }

    private static boolean active() {
        if (activeLevel == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (activeLevel != mc.level || !canUse(mc)) {
            clear();
            return false;
        }
        return true;
    }

    static int nextPercent(int current, boolean reset) {
        return reset || current == 100 ? 0 : current + 25;
    }

    private static boolean canUse(Minecraft mc) {
        return mc.isSingleplayer() && mc.level != null && mc.level.dimension().equals(ModDimensions.THE_DEEP)
                && mc.player != null && (mc.player.isCreative() || mc.player.hasPermissions(2));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        activeLevel = null;
        percent = 0;
        noSeaFog = false;
    }
}
