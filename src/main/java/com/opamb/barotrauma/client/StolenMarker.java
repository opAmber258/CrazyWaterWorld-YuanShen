package com.opamb.barotrauma.client;

import com.opamb.barotrauma.item.Illegitimate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * 赃物标识：格子上画一个红色小手（照原作 {@code Inventory.cs} 的
 * {@code LimbSlotIcons[LeftHand]} + {@code GUIStyle.Red}）。
 *
 * <p>画在格子左上角，先画一层暗色投影再画红手，深色物品上也看得清。</p>
 */
public final class StolenMarker {

    private static final int RED = 0xFFD03030;
    private static final int SHADOW = 0xC0000000;

    /** 5×6 的小手位图（X = 红手像素）。 */
    private static final String[] HAND = {
            ".X.X.X..",
            "XXXXXXX.",
            "XXXXXXX.",
            "XXXXXXX.",
            ".XXXXX..",
            "..XXX..."};

    private StolenMarker() {
    }

    /** 在这个容器的所有赃物格子上画红手。 */
    public static void renderSlots(GuiGraphics graphics, AbstractContainerMenu menu,
                                   int left, int top) {
        if (menu == null) {
            return;
        }
        for (Slot slot : menu.slots) {
            if (slot.isActive() && slot.hasItem() && Illegitimate.is(slot.getItem())) {
                draw(graphics, left + slot.x, top + slot.y);
            }
        }
    }

    private static void draw(GuiGraphics graphics, int x, int y) {
        for (int row = 0; row < HAND.length; row++) {
            String line = HAND[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != 'X') {
                    continue;
                }
                graphics.fill(x + col + 1, y + row + 1, x + col + 2, y + row + 2, SHADOW);
            }
        }
        for (int row = 0; row < HAND.length; row++) {
            String line = HAND[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != 'X') {
                    continue;
                }
                graphics.fill(x + col, y + row, x + col + 1, y + row + 1, RED);
            }
        }
    }
}
