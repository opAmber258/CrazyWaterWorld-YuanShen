package com.opamb.barotrauma.client;

import com.opamb.barotrauma.blockentity.BatteryBlockEntity;
import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.client.equipment.HandsSlotOverlay;
import com.opamb.barotrauma.client.gui.PanelStyle;
import com.opamb.barotrauma.client.gui.PowerStorageControls;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.menu.BatteryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BatteryScreen extends ScaledContainerScreen<BatteryMenu> {

    private static final int WIDTH = 244;
    private static final int HEIGHT = 246;
    private PowerStorageControls.RechargeSlider recharge;

    public BatteryScreen(BatteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        recharge = addRenderableWidget(new PowerStorageControls.RechargeSlider(12, 41, WIDTH - 24,
                () -> data(BatteryMenu.DATA_MAX_RECHARGE), percent -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, percent);
                    }
                }));
        recharge.sync(data(BatteryMenu.DATA_RECHARGE));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (recharge != null) recharge.sync(data(BatteryMenu.DATA_RECHARGE));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 12, 9, PanelStyle.TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        PanelStyle.frame(g, 0, 0, WIDTH, HEIGHT, PanelStyle.AMBER);
        PanelStyle.divider(g, 12, 23, WIDTH - 24);
        Component setting = recharge.getMessage();
        g.drawString(font, text("recharge"), 12, 29, PanelStyle.TEXT, false);
        g.drawString(font, setting, WIDTH - 12 - font.width(setting), 29, PanelStyle.DIM, false);
        PowerStorageControls.charge(g, font, 12, 63, WIDTH - 24,
                data(BatteryMenu.DATA_CHARGE), data(BatteryMenu.DATA_CAPACITY), 3);
        int flow = data(BatteryMenu.DATA_GRID_FLOW);
        boolean connected = data(BatteryMenu.DATA_GRID_CONNECTED) != 0;
        String status = data(BatteryMenu.DATA_CONDITION) == 0 ? "broken"
                : !connected ? "disconnected" : flow > 0 ? "charging" : flow < 0 ? "discharging" : "standby";
        PanelStyle.lamp(g, 17, 108, true, connected ? 0xFF62A78D : 0xFFD47766);
        g.drawString(font, text(status, Math.abs(flow)), 28, 104, connected ? PanelStyle.TEXT : 0xFFD47766, false);
        g.drawString(font, text("cells"), 12, 122, PanelStyle.DIM, false);
        for (int i = 0; i < BatteryBlockEntity.CELL_SLOTS; i++) {
            PanelStyle.slot(g, 11 + i * 22, 133);
        }

        int bagX = BatteryMenu.INV_AREA_X;
        int bagY = BatteryMenu.INV_AREA_Y;
        PanelStyle.divider(g, 12, bagY - 10, WIDTH - 24);
        for (int col = 0; col < 9; col++) {
            PanelStyle.slot(g, bagX + col * 18 - 1, bagY + 57);
        }
        if (minecraft != null && minecraft.player != null) {
            BagSlots.rebuildClientAt(menu, minecraft.player, BatteryMenu.INV_START,
                    bagX, bagY, BatteryMenu.INV_AREA_W, BatteryMenu.INV_AREA_H);
            BagOverlay.renderIn(g, 0, 0, minecraft.player, bagX, bagY,
                    BatteryMenu.INV_AREA_W, BatteryMenu.INV_AREA_H);
            HandsSlotOverlay.renderIn(g, menu, 0, 0);
        }
    }

    private int data(int id) {
        return menu.data().get(id);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.barotrauma.storage." + key, args);
    }
}
