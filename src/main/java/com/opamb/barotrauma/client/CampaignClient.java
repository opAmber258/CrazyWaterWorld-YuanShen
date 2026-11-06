package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.campaign.*;
import com.opamb.barotrauma.network.CampaignStatePacket;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, value = Dist.CLIENT)
public final class CampaignClient {
    public static final KeyMapping OPEN_MAP = new KeyMapping("key.barotrauma.campaign_map",
            GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.barotrauma");
    private static CampaignStatePacket state;
    private CampaignClient() {}
    public static CampaignStatePacket state() { return state; }
    public static void accept(CampaignStatePacket packet) {
        state = packet;
        BarotraumaMod.LOGGER.info("[campaign] 客户端状态 node={} phase={}", packet.current(), packet.phase());
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof CampaignMapScreen screen) {
            if (packet.phase() != RoundPhase.AT_NODE) mc.setScreen(null);
            else screen.onCampaignUpdate();
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { state = null; }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        // 兜底：只要已经进入航程，任何残留的海图界面都必须关掉，否则它会吃掉所有方块交互。
        if (mc.screen instanceof CampaignMapScreen && state != null && state.phase() != RoundPhase.AT_NODE) {
            mc.setScreen(null);
        }
        while (OPEN_MAP.consumeClick()) {
            if (mc.screen instanceof CampaignMapScreen) mc.setScreen(null);
            else if (mc.screen == null && available()) mc.setScreen(new CampaignMapScreen());
        }
    }
    public static boolean available() {
        var mc = Minecraft.getInstance();
        return state != null && state.phase() == RoundPhase.AT_NODE && mc.player != null
                && mc.level != null && mc.level.dimension().equals(ModDimensions.THE_DEEP);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (!available() || mc.options.hideGui || mc.screen != null) return;
        var node = CampaignMap.node(state.current());
        if (node == null) return;
        Component hint = Component.translatable("campaign.barotrauma.prompt", OPEN_MAP.getTranslatedKeyMessage(),
                Component.translatable("campaign.barotrauma.node." + node.id()));
        var g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int y = 7;
        var lines = mc.font.split(hint, Math.max(100, w - 20));
        g.fill(6, 3, w - 6, 10 + lines.size() * 10, 0xB80A1820);
        for (var line : lines) { g.drawCenteredString(mc.font, line, w / 2, y, 0xFFD1EBDF); y += 10; }
    }
}
