package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.item.BrightnessDebuggerItem;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 仅本机当前单人世界的渲染覆盖。 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class DeepBrightnessDebug {
    private static final String MESSAGE = "item.barotrauma.brightness_debugger.";
    private static ClientLevel activeLevel;
    private static Settings settings = new Settings(0, false);
    private static boolean useHeld;

    record Settings(int brightness, boolean noSeaFog) {
        Settings afterUse(boolean toggleFog) {
            return toggleFog ? new Settings(brightness, !noSeaFog)
                    : new Settings((brightness + 25) % 125, noSeaFog);
        }
    }

    private DeepBrightnessDebug() {
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null
                || !(mc.player.getItemInHand(event.getHand()).getItem() instanceof BrightnessDebuggerItem)) return;
        // Handle before block/entity use so the targeted machine cannot consume this click.
        event.setSwingHand(false);
        event.setCanceled(true);
        if (useHeld) return;
        useHeld = mc.options.keyUse.isDown();
        if (!canUse(mc)) {
            mc.player.displayClientMessage(Component.translatable(MESSAGE + "scope"), true);
            return;
        }
        if (activeLevel != mc.level) clear();
        boolean toggleFog = Screen.hasShiftDown() || mc.options.keyShift.isDown() || Screen.hasControlDown();
        settings = settings.afterUse(toggleFog);
        activeLevel = mc.level;
        Component status = toggleFog
                ? Component.translatable(MESSAGE + (settings.noSeaFog() ? "fog_off" : "fog_on"))
                : Component.translatable(MESSAGE + "current", settings.brightness());
        mc.player.displayClientMessage(status, true);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().options.keyUse.isDown()) {
            useHeld = false;
        }
    }

    public static float ambientMix() {
        return active() ? 0.60F * settings.brightness() / 100.0F : 0.0F;
    }

    static boolean seaFogDisabled() {
        return active() && settings.noSeaFog();
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

    private static boolean canUse(Minecraft mc) {
        return mc.isSingleplayer() && mc.level != null && mc.level.dimension().equals(ModDimensions.THE_DEEP)
                && mc.player != null && (mc.player.isCreative() || mc.player.hasPermissions(2));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        useHeld = false;
    }

    private static void clear() {
        activeLevel = null;
        settings = new Settings(0, false);
    }
}
