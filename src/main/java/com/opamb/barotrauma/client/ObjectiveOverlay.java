package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 目标提示 HUD：在深度表下方常驻"现在该干什么"。
 *
 * <p>一趟最多 3 条任务，所以每条任务一行（超过 3 条只画前 3 条）。
 * 任务都在靠泊目的站时统一结算，所以文案里不再出现"回终端交付"。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ObjectiveOverlay {

    /** 任务系统设置的当前目标（null = 用默认引导文案）。 */
    public static volatile String override;

    private static final int ACCENT = 0xFFE0762F;
    private static final int SAILING = 0xFF7BE38B;
    private static final int FAILED = 0xFFE06666;

    private ObjectiveOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui) {
            return;
        }
        if (!minecraft.level.dimension().equals(ModDimensions.THE_DEEP)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        if (override != null) {
            drawLine(graphics, font, "▸ " + override, 20, ACCENT);
            return;
        }
        List<Mission> active = ClientMissionCache.active();
        if (!active.isEmpty()) {
            int y = 20;
            for (int i = 0; i < Math.min(3, active.size()); i++) {
                Mission mission = active.get(i);
                drawLine(graphics, font, "▸ " + describe(mission), y, color(mission));
                y += 11;
            }
            return;
        }
        String fallback = VsState.active()
                ? "出航中：WASD/舵台操舵 · 返航后在声纳台点「靠泊」"
                : "目标：找码头上的站务员接任务，再在小地图上选目的站出航";
        drawLine(graphics, font, "▸ " + fallback, 20, VsState.active() ? SAILING : ACCENT);
    }

    private static int color(Mission mission) {
        if (mission.state() == Mission.State.FAILED) {
            return FAILED;
        }
        return mission.state() == Mission.State.READY || VsState.active() ? SAILING : ACCENT;
    }

    private static void drawLine(GuiGraphics graphics, Font font, String text, int y, int color) {
        Component line = Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY);
        graphics.fill(6, y, 9 + font.width(line), y + 11, 0x66000000);
        graphics.drawString(font, line, 8, y + 2, color, true);
    }

    private static String describe(Mission mission) {
        if (mission.state() == Mission.State.FAILED) {
            return "任务 #" + mission.id + " 已超时失败（抵达目的地后可重新接取）";
        }
        boolean cargo = mission.type == com.opamb.barotrauma.mission.MissionType.DELIVERY
                || mission.type == com.opamb.barotrauma.mission.MissionType.URGENT_DELIVERY;
        String where = cargo ? "目的站"
                : mission.target.getX() + ", " + mission.target.getY() + ", " + mission.target.getZ();
        int dist = (int) Math.sqrt(minecraft().player.blockPosition().distSqr(mission.target));
        String deadline = "";
        if (mission.deadlineAtGameTime() > 0 && minecraft().level != null) {
            long left = mission.deadlineAtGameTime() - minecraft().level.getGameTime();
            deadline = left > 0
                    ? String.format(" · 剩余 %d:%02d", left / 1200L, (left / 20L) % 60L)
                    : " · 已超时";
        }
        if (mission.state() == Mission.State.READY) {
            return "任务 #" + mission.id + " 目标达成：靠泊目的站后自动结算" + deadline;
        }
        if (cargo) {
            int crates = mission.cargoTotal() > 0 ? mission.cargoTotal() : 1;
            String destination = mission.destinationNode() >= 0
                    ? Component.translatable("campaign.barotrauma.node."
                            + mission.destinationNode()).getString()
                    : "目的站";
            return "任务 #" + mission.id + "：" + crates + " 箱货物送到 " + destination
                    + "（靠泊后自动结算）" + deadline;
        }
        return "任务 #" + mission.id + "：前往 " + where + "（" + dist + "m）" + deadline;
    }

    private static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
