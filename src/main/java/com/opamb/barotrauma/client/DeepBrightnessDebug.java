package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.Minecraft;
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
        percent = nextPercent(activeLevel == mc.level ? percent : 0, reset);
        activeLevel = percent == 0 ? null : mc.level;
        mc.player.displayClientMessage(Component.translatable(MESSAGE + "current", percent), true);
    }

    public static float ambientMix() {
        if (activeLevel == null) {
            return 0.0F;
        }
        Minecraft mc = Minecraft.getInstance();
        if (activeLevel != mc.level || !canUse(mc)) {
            clear();
            return 0.0F;
        }
        return 0.60F * percent / 100.0F;
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
    }
}
