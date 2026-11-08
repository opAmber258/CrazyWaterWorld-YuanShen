package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.EngineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 引擎只读面板：目标推力、供电、耗电、引擎数量和运行状态。
 * 控制全在声纳台，这里只显示引擎收到了什么。
 */
public class EngineScreen extends AbstractContainerScreen<EngineMenu> {

    private static final int W = 220;
    private static final int H = 120;

    private static final int BG = 0xF0141B16;
    private static final int EDGE = 0xFF3B463C;
    private static final int PANEL = 0xFF1A241D;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF7C8478;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int YELLOW = 0xFFD8B44A;
    private static final int TRACK = 0xFF0C120E;

    public EngineScreen(EngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private int data(int index) {
        return menu.data().get(index);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 7, ACCENT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + W, y + H, BG);
        graphics.fill(x, y, x + W, y + 1, EDGE);
        graphics.fill(x, y + H - 1, x + W, y + H, EDGE);
        graphics.fill(x, y, x + 1, y + H, EDGE);
        graphics.fill(x + W - 1, y, x + W, y + H, EDGE);

        int force = data(EngineMenu.DATA_FORCE);
        double supply = data(EngineMenu.DATA_SUPPLY) / 1000.0D;
        int demand = data(EngineMenu.DATA_DEMAND);
        int count = data(EngineMenu.DATA_COUNT);
        int maxForce = data(EngineMenu.DATA_MAX_FORCE);

        // 目标推力条
        graphics.drawString(font, "目标推力: " + force + "%", x + 10, y + 24, TEXT, false);
        int barX = x + 10;
        int barY = y + 36;
        int barW = W - 20;
        graphics.fill(barX, barY, barX + barW, barY + 8, TRACK);
        graphics.fill(barX, barY, barX + barW, barY + 1, EDGE);
        int filled = Math.round(barW * Mth.clamp(force / 100.0F, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(barX, barY + 1, barX + filled, barY + 8, ACCENT);
        }
        for (int tick = 25; tick < 100; tick += 25) {
            int tx = barX + barW * tick / 100;
            graphics.fill(tx, barY, tx + 1, barY + 8, EDGE);
        }

        // 供电 / 耗电
        int supplyPercent = (int) Math.round(supply * 100.0D);
        int supplyColor = supply <= 0.02D ? RED : supply < 0.5D ? YELLOW : GREEN;
        graphics.drawString(font, "供电: " + supplyPercent + "%", x + 10, y + 52, supplyColor, false);
        graphics.drawString(font, "耗电: " + demand + " 千瓦", x + 10, y + 66, TEXT, false);
        graphics.drawString(font, "船上引擎: " + count + " 台  ·  单台最大推力 " + maxForce,
                x + 10, y + 80, DIM, false);

        String state;
        int stateColor;
        if (count <= 0) {
            state = "船上没有引擎，无法推进";
            stateColor = RED;
        } else if (force <= 0) {
            state = "停车";
            stateColor = DIM;
        } else if (supply <= 0.02D) {
            state = "无电力，推力归零";
            stateColor = RED;
        } else if (supply < 0.5D) {
            state = "欠压（<50%），推力归零";
            stateColor = RED;
        } else if (supply < 1.0D) {
            state = "欠压运行，推力按电压衰减";
            stateColor = YELLOW;
        } else {
            state = "运行";
            stateColor = GREEN;
        }
        graphics.drawString(font, state, x + 10, y + 98, stateColor, false);
    }
}
