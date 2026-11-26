package com.opamb.barotrauma.client;

import com.opamb.barotrauma.blockentity.SupercapacitorBlockEntity;
import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.client.equipment.HandsSlotOverlay;
import com.opamb.barotrauma.client.gui.PanelStyle;
import com.opamb.barotrauma.client.gui.PowerStorageControls;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.menu.SupercapacitorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SupercapacitorScreen extends ScaledContainerScreen<SupercapacitorMenu> {

    private static final int WIDTH = 244;
    private static final int HEIGHT = 220;
    private PowerStorageControls.RechargeSlider recharge;

    public SupercapacitorScreen(SupercapacitorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        recharge = addRenderableWidget(new PowerStorageControls.RechargeSlider(12, 41, WIDTH - 24,
                () -> (int) SupercapacitorBlockEntity.MAX_RECHARGE_KW, percent -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, percent);
                    }
                }));
        recharge.sync(data(SupercapacitorMenu.DATA_RECHARGE));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (recharge != null) recharge.sync(data(SupercapacitorMenu.DATA_RECHARGE));
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
                data(SupercapacitorMenu.DATA_CHARGE), data(SupercapacitorMenu.DATA_CAPACITY), 5);
        String status = data(SupercapacitorMenu.DATA_CONDITION) == 0 ? "broken"
                : data(SupercapacitorMenu.DATA_BOUND) == 0 ? "disconnected" : "bound";
        int color = status.equals("bound") ? PanelStyle.DIM : 0xFFD47766;
        g.drawString(font, text(status), 12, 104, color, false);

        int bagX = SupercapacitorMenu.INV_AREA_X;
        int bagY = SupercapacitorMenu.INV_AREA_Y;
        PanelStyle.divider(g, 12, bagY - 10, WIDTH - 24);
        for (int col = 0; col < 9; col++) {
            PanelStyle.slot(g, bagX + col * 18 - 1, bagY + 57);
        }
        if (minecraft != null && minecraft.player != null) {
            BagSlots.rebuildClientAt(menu, minecraft.player, SupercapacitorMenu.INV_START,
                    bagX, bagY, SupercapacitorMenu.INV_AREA_W, SupercapacitorMenu.INV_AREA_H);
            BagOverlay.renderIn(g, 0, 0, minecraft.player, bagX, bagY,
                    SupercapacitorMenu.INV_AREA_W, SupercapacitorMenu.INV_AREA_H);
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
