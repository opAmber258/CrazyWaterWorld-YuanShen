package com.opamb.barotrauma.client.equipment;

import com.opamb.barotrauma.equipment.EquipmentSlotType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * 原版背包界面上的装备槽绘制。
 *
 * <p>按**菜单下标**找槽位（而不是 {@code instanceof EquipmentSlot}），
 * 这样创造模式"物品栏"标签页里被原版包成 {@code SlotWrapper} 的槽位也能画。
 * 背景在 {@code renderBg} 之后补画，因此画在物品下面；
 * 空槽里用两字符提示（ID/HD/HM/AR/BG/DS），悬停空槽时显示槽位名。</p>
 */
public final class EquipmentScreenOverlay {

    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int HINT_COLOR = 0xFF5A5A5A;

    private EquipmentScreenOverlay() {
    }

    /** 在背包背景上补画装备槽。 */
    public static void renderSlots(GuiGraphics graphics, AbstractContainerMenu menu, int left, int top) {
        Font font = Minecraft.getInstance().font;
        for (EquipmentSlotType type : EquipmentSlotType.values()) {
            int menuIndex = type.menuIndex();
            if (menuIndex >= menu.slots.size()) {
                continue;
            }
            Slot slot = menu.slots.get(menuIndex);
            if (!slot.isActive()) {
                continue;
            }
            int x = left + slot.x;
            int y = top + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
            graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
            if (!slot.hasItem()) {
                String hint = type.hint();
                int textX = x + (16 - font.width(hint)) / 2;
                graphics.drawString(font, hint, textX, y + 4, HINT_COLOR, false);
            }
        }
        // 47：双手持独立栏位。
        if (menu.slots.size() > com.opamb.barotrauma.equipment.HandsSlot.MENU_INDEX) {
            Slot hands = menu.slots.get(com.opamb.barotrauma.equipment.HandsSlot.MENU_INDEX);
            if (hands instanceof com.opamb.barotrauma.equipment.HandsSlot && hands.isActive()) {
                int x = left + hands.x;
                int y = top + hands.y;
                graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
                graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
                if (!hands.hasItem()) {
                    String hint = "2H";
                    graphics.drawString(font, hint, x + (16 - font.width(hint)) / 2, y + 4,
                            HINT_COLOR, false);
                }
            }
        }
    }

    /** 悬停空装备槽时显示"ID卡槽"这类提示。 */
    public static void renderTooltip(GuiGraphics graphics, AbstractContainerMenu menu, Slot hovered,
                                     int mouseX, int mouseY) {
        if (hovered == null || hovered.hasItem()) {
            return;
        }
        EquipmentSlotType type = EquipmentSlotType.byMenuIndex(menu.slots.indexOf(hovered));
        if (type == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        graphics.renderTooltip(font, Component.translatable(type.slotNameKey()), mouseX, mouseY);
    }
}
