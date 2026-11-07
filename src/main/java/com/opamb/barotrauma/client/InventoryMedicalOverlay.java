package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.client.MedicalBodyRenderer.DisplayLine;
import com.opamb.barotrauma.client.MedicalBodyRenderer.Zone;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 背包界面的伤病状态面板——仿原版潜渊症角色健康界面的部位显示。
 *
 * <p>活力条 + 身体图（按严重度着色）；**部位伤情只在鼠标悬停到身体部位时显示**（用户指令），
 * 全身病症（失血/缺氧）常驻显示。数据来自 {@link ClientMedicalCache}。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class InventoryMedicalOverlay {

    private static final int PANEL_WIDTH = 120;
    private static final int BODY_HEIGHT = 90;

    private static final int BG = 0xE012121A;
    private static final int BORDER = 0xFF4A4A5A;

    private InventoryMedicalOverlay() {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)
                || event.getScreen() instanceof MedicalInventoryScreen) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var self = minecraft.player == null ? null : ClientMedicalCache.snapshot(minecraft.player.getUUID());

        int x = screen.width - PANEL_WIDTH - 6;
        int y = 6;
        int panelHeight = Math.min(screen.height - 12, 132);
        g.fill(x - 1, y - 1, x + PANEL_WIDTH + 1, y + panelHeight + 1, BORDER);
        g.fill(x, y, x + PANEL_WIDTH, y + panelHeight, BG);

        List<String> rows = self == null ? List.of() : self.rows();
        float vitality = self == null ? 100.0f : self.vitality();
        boolean downed = self != null && self.downed();

        // 标题
        g.drawString(screen.getMinecraft().font, Component.literal("伤病状态"), x + 5, y + 4, 0xFFFFC74A);

        int cy = y + 16;
        // 倒地横幅
        if (downed) {
            g.fill(x + 4, cy, x + PANEL_WIDTH - 4, cy + 11, 0x80FF5A5A);
            g.drawString(screen.getMinecraft().font,
                    Component.literal("⚠ 重伤倒地——等待救援"), x + 7, cy + 2, 0xFFFFE0E0);
        }
        cy += downed ? 14 : 0;

        // 活力
        int vitalityColor = downed ? 0xFFFF5A5A
                : vitality > 60 ? 0xFF55E05A : vitality > 25 ? 0xFFFFD75A : 0xFFFF5A5A;
        g.drawString(screen.getMinecraft().font,
                Component.literal(String.format("活力 %.0f/100%s", vitality, downed ? "（倒地）" : "")),
                x + 5, cy, vitalityColor);
        drawBar(g, x + 5, cy + 10, PANEL_WIDTH - 10, Math.max(0.0, vitality / 100.0), vitalityColor);
        cy += 24;

        // 身体图（悬停部位才显示该部位伤情文字）
        int bodyCx = x + PANEL_WIDTH / 2;
        int bodyBy = cy + 4;
        List<Zone> zones = MedicalBodyRenderer.zones(bodyCx, bodyBy);
        MedicalBodyRenderer.draw(g, zones, rows, event.getMouseX(), event.getMouseY());
        List<DisplayLine> hover = MedicalBodyRenderer.hoverLines(
                zones, rows, event.getMouseX(), event.getMouseY());
        cy = bodyBy + BODY_HEIGHT;
        for (DisplayLine line : hover) {
            g.drawString(screen.getMinecraft().font, Component.literal(line.text()), x + 5, cy, line.color());
            cy += 10;
        }

        // 全身病症（失血/缺氧/眩晕——系统性状态常驻显示）
        for (DisplayLine line : MedicalBodyRenderer.globalLines(rows)) {
            g.drawString(screen.getMinecraft().font, Component.literal("· " + line.text()), x + 5, cy, line.color());
            cy += 10;
        }
        if (hover.isEmpty() && MedicalBodyRenderer.globalLines(rows).isEmpty()) {
            boolean treating = rows.stream().anyMatch(r -> r.startsWith("t|"));
            if (!treating) {
                g.drawString(screen.getMinecraft().font,
                        Component.literal("没有伤病"), x + 5, cy, 0xFF55E05A);
            }
        }
        if (rows.stream().anyMatch(r -> r.startsWith("t|"))) {
            g.drawString(screen.getMinecraft().font,
                    Component.literal("◦ 治疗中…"), x + 5, Math.min(cy + 2, y + panelHeight - 10), 0xFF7FE87F);
        }
    }

    private static void drawBar(GuiGraphics g, int x, int y, int width, double ratio, int color) {
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        g.fill(x, y, x + width, y + 3, 0xFF26262E);
        if (ratio > 0) {
            g.fill(x, y, x + (int) (width * ratio), y + 3, color);
        }
    }
}
