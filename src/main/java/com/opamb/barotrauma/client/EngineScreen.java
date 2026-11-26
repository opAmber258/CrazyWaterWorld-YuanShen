package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.EngineMenu;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 引擎只读面板：带方向的目标与当前推力、供电、耗电和运行状态。
 * 控制全在声纳台，这里只显示引擎收到了什么。
 */
public class EngineScreen extends ScaledContainerScreen<EngineMenu> {

    private static final int W = 220;
    private static final int H = 176;

    private static final int EDGE = PanelStyle.EDGE;
    private static final int PANEL = PanelStyle.RECESS;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.AMBER;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int YELLOW = 0xFFD8B44A;
    private static final int TRACK = PanelStyle.RECESS;

    public EngineScreen(EngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private int data(int index) {
        return menu.data().get(index);
    }


    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 7, ACCENT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        PanelStyle.frame(graphics, x, y, W, H, PanelStyle.AMBER);

        int force = data(EngineMenu.DATA_FORCE);
        int target = data(EngineMenu.DATA_TARGET_FORCE);
        double supply = data(EngineMenu.DATA_SUPPLY) / 1000.0D;
        int demand = data(EngineMenu.DATA_DEMAND);
        int count = data(EngineMenu.DATA_COUNT);
        int maxForce = data(EngineMenu.DATA_MAX_FORCE);

        paintIndicator(graphics, x + 10, y + 27, supply >= 0.5D, GREEN,
                Component.translatable("power.indicator.powered"));
        paintIndicator(graphics, x + 110, y + 27, count > 0, ACCENT,
                Component.translatable("gui.barotrauma.engine.helm_input"));
        graphics.drawString(font, "目标 " + signedPercent(target) + "   当前 " + signedPercent(force),
                x + 10, y + 45, TEXT, false);
        int barX = x + 10;
        int barY = y + 58;
        int barW = W - 20;
        graphics.fill(barX, barY, barX + barW, barY + 8, TRACK);
        graphics.fill(barX, barY, barX + barW, barY + 1, EDGE);
        int center = barX + barW / 2;
        int end = center + Math.round(barW * 0.5F * Mth.clamp(force / 100.0F, -1.0F, 1.0F));
        if (end != center) {
            graphics.fill(Math.min(center, end), barY + 1, Math.max(center, end), barY + 8, ACCENT);
        }
        graphics.fill(center, barY, center + 1, barY + 8, EDGE);
        int targetX = center + Math.round((barW / 2.0F - 3) * Mth.clamp(target / 100.0F, -1.0F, 1.0F));
        graphics.fill(targetX - 2, barY - 2, targetX + 3, barY + 10, TEXT);
        graphics.drawString(font, Component.translatable("gui.barotrauma.engine.reverse"),
                barX, y + 71, DIM, false);
        Component ahead = Component.translatable("gui.barotrauma.engine.ahead");
        graphics.drawString(font, ahead, barX + barW - font.width(ahead), y + 71, DIM, false);

        // 供电 / 耗电
        int supplyPercent = (int) Math.round(supply * 100.0D);
        int supplyColor = supply <= 0.02D ? RED : supply < 0.5D ? YELLOW : GREEN;
        graphics.drawString(font, "供电: " + supplyPercent + "%", x + 10, y + 88, supplyColor, false);
        graphics.drawString(font, "耗电: " + demand + " 千瓦", x + 10, y + 102, TEXT, false);
        graphics.drawString(font, "船上引擎: " + count + " 台  ·  单台最大推力 " + maxForce,
                x + 10, y + 116, DIM, false);

        String state;
        int stateColor;
        if (menu.data().get(EngineMenu.DATA_CONDITION) <= 0) {
            state = "引擎已损坏，需要扳手修理";
            stateColor = RED;
        } else if (count <= 0) {
            state = "船上没有引擎，无法推进";
            stateColor = RED;
        } else if (supply <= 0.02D) {
            state = "无电力，推力归零";
            stateColor = RED;
        } else if (target == 0 && force == 0) {
            state = "待命";
            stateColor = DIM;
        } else if (supply < 0.5D) {
            state = "欠压（<50%），推力衰减";
            stateColor = RED;
        } else if (supply < 1.0D) {
            state = "欠压运行，推力按电压衰减";
            stateColor = YELLOW;
        } else {
            state = "运行";
            stateColor = GREEN;
        }
        graphics.drawString(font, state, x + 10, y + 134, stateColor, false);
    }

    private void paintIndicator(GuiGraphics g, int x, int y, boolean on, int color, Component label) {
        g.fill(x, y, x + 10, y + 10, TRACK);
        g.fill(x + 2, y + 2, x + 8, y + 8, on ? color : EDGE);
        g.drawString(font, label, x + 14, y + 1, TEXT, false);
    }

    private static String signedPercent(int force) {
        return (force > 0 ? "+" : "") + force + "%";
    }
}
