package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.client.equipment.HandsSlotOverlay;
import com.opamb.barotrauma.client.gui.PanelStyle;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.item.CoilgunAmmoBoxItem;
import com.opamb.barotrauma.menu.CoilgunLoaderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class CoilgunLoaderScreen extends ScaledContainerScreen<CoilgunLoaderMenu> {
    public CoilgunLoaderScreen(CoilgunLoaderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 153;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        PanelStyle.frame(g, 0, 0, imageWidth, imageHeight, PanelStyle.AMBER);
        g.drawCenteredString(font, title, 88, 12, PanelStyle.TEXT);
        PanelStyle.well(g, 52, 26, 72, 35);
        PanelStyle.slot(g, 79, 34);
        ItemStack stack = menu.getSlot(0).getItem();
        if (!stack.isEmpty()) {
            g.drawString(font, Integer.toString(CoilgunAmmoBoxItem.shotsLeft(stack)),
                    101, 39, PanelStyle.AMBER, false);
        }
        int x = CoilgunLoaderMenu.INVENTORY_X;
        int y = CoilgunLoaderMenu.INVENTORY_Y;
        for (int i = 0; i < 9; i++) PanelStyle.slot(g, x - 1 + i * 18, y + 57);
        if (minecraft != null && minecraft.player != null) {
            BagSlots.rebuildClientAt(menu, minecraft.player, 1, x, y, 160, 54);
            BagOverlay.renderIn(g, 0, 0, minecraft.player, x, y, 160, 54);
            HandsSlotOverlay.renderIn(g, menu, 0, 0);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Labels are placed alongside the loader slot in renderBg.
    }
}
