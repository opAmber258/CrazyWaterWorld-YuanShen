package com.opamb.barotrauma.client.equipment;

import com.opamb.barotrauma.equipment.HandsSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * 容器界面（加工台/解构仪/反应堆/电池/柜子）里的双手持栏位。
 *
 * <p>菜单把 {@link HandsSlot} 放在装备背包区左侧的空位里，这里只负责画格子底和
 * 空槽的 "2H" 提示；物品本身由原版槽位渲染。背包界面那一份在
 * {@link EquipmentScreenOverlay}（47 号位，面板外）。</p>
 */
public final class HandsSlotOverlay {

    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int HINT_COLOR = 0xFF5A5A5A;

    private HandsSlotOverlay() {
    }

    public static void renderIn(GuiGraphics graphics, AbstractContainerMenu menu, int left, int top) {
        Slot hands = find(menu);
        if (hands == null || !hands.isActive()) {
            return;
        }
        int x = left + hands.x;
        int y = top + hands.y;
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
        if (!hands.hasItem()) {
            Font font = Minecraft.getInstance().font;
            String hint = "2H";
            graphics.drawString(font, hint, x + (16 - font.width(hint)) / 2, y + 4,
                    HINT_COLOR, false);
        }
    }

    @Nullable
    private static Slot find(AbstractContainerMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot instanceof HandsSlot) {
                return slot;
            }
        }
        return null;
    }
}
