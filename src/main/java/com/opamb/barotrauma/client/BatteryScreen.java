package com.opamb.barotrauma.client;

import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.menu.BatteryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 电池面板，照原作 {@code PowerContainer} 的布局：
 *
 * <pre>
 *   电量: 1200 / 2000 kW·min (60%)
 *   [==========电量条==========]
 *   充电速率: 500 kW (100%)
 *   [==========滑条==========]
 *   耐久: 100%
 *   接线盒: 已连接
 * </pre>
 *
 * <p>滑条 0..100% 对应 0..500 kW；拖动时通过
 * {@link BatteryMenu#clickMenuButton} 回服务端。没有物品槽。</p>
 */
public class BatteryScreen extends ScaledContainerScreen<BatteryMenu> {

    private static final int W = 224;
    private static final int H = 292;

    private static final int BG = 0xF0141B16;
    private static final int EDGE = 0xFF3B463C;
    private static final int PANEL = 0xFF1E2A22;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF7C8478;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int YELLOW = 0xFFD8B44A;
    private static final int TRACK = 0xFF0C120E;

    private RechargeSlider slider;

    public BatteryScreen(BatteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        int value = Mth.clamp(menu.data().get(BatteryMenu.DATA_RECHARGE), 0,
                (int) Math.round(BatteryBlockEntity.MAX_RECHARGE_KW));
        double ratio = value / BatteryBlockEntity.MAX_RECHARGE_KW;
        slider = addRenderableWidget(new RechargeSlider(
                leftPos + 12, topPos + 90, W - 24, 20, ratio));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 8, ACCENT, false);
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

        int chargeRaw = Math.max(0, menu.data().get(BatteryMenu.DATA_CHARGE));
        int capacityRaw = Math.max(1, menu.data().get(BatteryMenu.DATA_CAPACITY));
        double fraction = Mth.clamp(chargeRaw / (double) capacityRaw, 0.0D, 1.0D);
        double charge = chargeRaw / 10.0D;
        double capacity = capacityRaw / 10.0D;

        graphics.drawString(font, String.format("电量: %.0f / %.0f kW·min (%.0f%%)",
                charge, capacity, fraction * 100.0D), x + 12, y + 32, TEXT, false);

        int barX = x + 12;
        int barY = y + 46;
        int barW = W - 24;
        int barH = 14;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, EDGE);
        graphics.fill(barX, barY, barX + barW, barY + barH, TRACK);
        int fillColor = fraction <= 0.2D ? RED : fraction <= 0.5D ? YELLOW : GREEN;
        graphics.fill(barX, barY, barX + (int) Math.round(barW * fraction), barY + barH,
                fillColor);
        // 原版电池屏的三段刻度，方便看出 1/3、2/3。
        for (int i = 1; i < 3; i++) {
            int segX = barX + barW * i / 3;
            graphics.fill(segX, barY, segX + 1, barY + barH, 0x66000000);
        }

        boolean bound = menu.data().get(BatteryMenu.DATA_BOUND) != 0;
        graphics.drawString(font, bound ? "接线盒: 已连接" : "接线盒: 默认总线",
                x + 12, y + 160, bound ? GREEN : DIM, false);
        if (menu.data().get(BatteryMenu.DATA_CONDITION) <= 0) {
            graphics.drawString(font, "已损坏，需要螺丝刀修理",
                    x + 12, y + 176, RED, false);
        } else {
            graphics.drawString(font, "耐久: " + Math.round(
                            menu.data().get(BatteryMenu.DATA_CONDITION) / 10.0D) + "%",
                    x + 12, y + 176, DIM, false);
        }

        // 三个电池 cell 充电槽（原作 batterycellrecharger）。
        graphics.drawString(font, "电池槽", x + 12, y + 122, DIM, false);
        for (int i = 0; i < 3; i++) {
            slotBg(graphics, x + 12 + i * 22 - 1, y + 134 - 1);
        }
        // 快捷栏槽位底纹；上面那 27 格是"装备背包区"，由 BagOverlay 整块画。
        for (int col = 0; col < 9; col++) {
            slotBg(graphics, x + BatteryMenu.INV_AREA_X + col * 18 - 1,
                    y + BatteryMenu.INV_AREA_Y + 58 - 1);
        }
        if (minecraft != null && minecraft.player != null) {
            com.opamb.barotrauma.equipment.BagSlots.rebuildClientAt(menu, minecraft.player,
                    BatteryMenu.INV_START, BatteryMenu.INV_AREA_X, BatteryMenu.INV_AREA_Y,
                    BatteryMenu.INV_AREA_W, BatteryMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.BagOverlay.renderIn(graphics, x, y,
                    minecraft.player, BatteryMenu.INV_AREA_X, BatteryMenu.INV_AREA_Y,
                    BatteryMenu.INV_AREA_W, BatteryMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.HandsSlotOverlay.renderIn(graphics, menu, x, y);
        }
    }

    private void slotBg(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
    }

    /** 0..1 的充电速率滑条，松手/拖动时把百分比发给服务端。 */
    private final class RechargeSlider extends AbstractSliderButton {

        private int lastSent;

        RechargeSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            this.lastSent = (int) Math.round(value * 100.0D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round(value * 100.0D);
            int kw = (int) Math.round(BatteryBlockEntity.MAX_RECHARGE_KW * value);
            setMessage(Component.literal("充电速率: " + kw + " kW (" + percent + "%)"));
        }

        @Override
        protected void applyValue() {
            int percent = (int) Math.round(value * 100.0D);
            if (percent == lastSent) {
                return;
            }
            lastSent = percent;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, percent);
            }
        }
    }
}
