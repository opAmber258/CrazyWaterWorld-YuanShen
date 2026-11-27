package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.menu.OxygenGeneratorMenu;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 制氧机面板，照原作 {@code OxygenGenerator} 的五格容器布局：
 *
 * <pre>
 *   产氧: 418 单位/秒
 *   电压: 1.00
 *   耐久: 100%
 *   接线盒: 默认总线
 *   [氧气瓶][氧气瓶][    ][焊接油箱][    ]
 * </pre>
 *
 * <p>没有开关：通电就工作。槽里的氧气瓶/焊接油箱会被持续充气（每秒 2 点耐久）。</p>
 */
public class OxygenGeneratorScreen extends ScaledContainerScreen<OxygenGeneratorMenu> {

    private static final int W = 224;
    private static final int H = 220;

    private static final int EDGE = PanelStyle.EDGE;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.AMBER;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int YELLOW = 0xFFD8B44A;

    public OxygenGeneratorScreen(OxygenGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        PanelStyle.frame(graphics, x, y, W, H, PanelStyle.AMBER);

        PanelStyle.divider(graphics, x + 10, y + 22, W - 20);
        PanelStyle.well(graphics, x + 10, y + 29, W - 20, 42);
        PanelStyle.well(graphics, x + 10, y + 76, W - 20, 50);
        PanelStyle.divider(graphics, x + 10, y + 130, W - 20);
        graphics.drawString(font, "气瓶 / 焊接油箱", x + 16, y + 34, DIM, false);

        int flowRaw = Math.max(0, menu.data().get(OxygenGeneratorMenu.DATA_FLOW));
        int voltageRaw = menu.data().get(OxygenGeneratorMenu.DATA_VOLTAGE);
        int conditionRaw = menu.data().get(OxygenGeneratorMenu.DATA_CONDITION);
        double voltage = voltageRaw / 100.0D;

        graphics.drawString(font, Component.translatable(
                        "gui.barotrauma.oxygen_generator.flow",
                        String.format("%.0f", flowRaw / 10.0D)),
                x + 16, y + 82, flowRaw > 0 ? GREEN : DIM, false);
        int voltageColor = voltage >= 0.5D ? GREEN : voltage > 0.1D ? YELLOW : RED;
        graphics.drawString(font, Component.translatable(
                        "gui.barotrauma.oxygen_generator.voltage", String.format("%.2f", voltage)),
                x + 16, y + 96, voltageColor, false);
        if (conditionRaw <= 0) {
            graphics.drawString(font, Component.translatable(
                            "gui.barotrauma.oxygen_generator.broken"),
                    x + 16, y + 110, RED, false);
        } else {
            graphics.drawString(font, Component.translatable(
                            "gui.barotrauma.oxygen_generator.condition",
                            String.valueOf(Math.round(conditionRaw / 10.0D))),
                    x + 16, y + 110, DIM, false);
        }
        PanelStyle.lamp(graphics, x + W - 22, y + 88, flowRaw > 0, GREEN);

        // 五个瓶槽底纹
        for (int i = 0; i < 5; i++) {
            slotBg(graphics, x + OxygenGeneratorMenu.TANK_ROW_X + i * OxygenGeneratorMenu.TANK_PITCH - 1,
                    y + OxygenGeneratorMenu.TANK_ROW_Y - 1);
        }
        // 快捷栏槽位底纹；上面那 27 格是"装备背包区"，由 BagOverlay 整块画。
        for (int col = 0; col < 9; col++) {
            slotBg(graphics, x + OxygenGeneratorMenu.INV_AREA_X + col * 18 - 1,
                    y + OxygenGeneratorMenu.INV_AREA_Y + 58 - 1);
        }
        if (minecraft != null && minecraft.player != null) {
            com.opamb.barotrauma.equipment.BagSlots.rebuildClientAt(menu, minecraft.player,
                    OxygenGeneratorMenu.INV_START, OxygenGeneratorMenu.INV_AREA_X,
                    OxygenGeneratorMenu.INV_AREA_Y, OxygenGeneratorMenu.INV_AREA_W,
                    OxygenGeneratorMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.BagOverlay.renderIn(graphics, x, y,
                    minecraft.player, OxygenGeneratorMenu.INV_AREA_X,
                    OxygenGeneratorMenu.INV_AREA_Y, OxygenGeneratorMenu.INV_AREA_W,
                    OxygenGeneratorMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.HandsSlotOverlay.renderIn(graphics, menu, x, y);
        }
    }

    private void slotBg(GuiGraphics graphics, int x, int y) {
        PanelStyle.slot(graphics, x, y);
    }
}
