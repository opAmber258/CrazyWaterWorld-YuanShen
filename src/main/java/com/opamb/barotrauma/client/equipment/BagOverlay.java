package com.opamb.barotrauma.client.equipment;

import com.opamb.barotrauma.equipment.BagLayout;
import com.opamb.barotrauma.equipment.BagManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 背包界面里"装备背包"区域：盖住原版主背包底纹，按工具带/背包画对应格子。
 *
 * <p>底图是**一整张**背包物品剪影，铺在所有格子下面（不是每格一个图标）；
 * 格子上再画半透明底色和边框，所以整体看起来是"一个背包上开了几个格"。</p>
 *
 * <p>{@link #renderIn} 带区域参数，生存背包和创造模式"物品栏"标签页共用。</p>
 */
public final class BagOverlay {

    private static final int PANEL = 0xFF1A1D20;
    private static final int EDGE = 0xFF4A5157;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0x99101214;
    private static final int HINT = 0xFF8A9198;

    private BagOverlay() {
    }

    public static void render(GuiGraphics graphics, int left, int top, Player player) {
        renderIn(graphics, left, top, player,
                BagLayout.AREA_X, BagLayout.AREA_Y, BagLayout.AREA_WIDTH, BagLayout.AREA_HEIGHT);
    }

    public static void renderIn(GuiGraphics graphics, int left, int top, Player player,
                                int areaX, int areaY, int areaWidth, int areaHeight) {
        int capacity = BagManager.capacity(player);
        int panelX = left + areaX - 1;
        int panelY = top + areaY - 1;
        int panelRight = left + areaX + areaWidth + 1;
        int panelBottom = top + areaY + areaHeight + 1;
        graphics.fill(panelX, panelY, panelRight, panelBottom, PANEL);
        graphics.fill(panelX, panelY, panelRight, panelY + 1, EDGE);
        graphics.fill(panelX, panelBottom - 1, panelRight, panelBottom, EDGE);

        Font font = Minecraft.getInstance().font;
        if (capacity <= 0) {
            Component hint = Component.translatable("gui.barotrauma.bag.empty");
            graphics.drawString(font, hint, left + areaX + areaWidth / 2 - font.width(hint) / 2,
                    top + areaY + areaHeight / 2 - 4, HINT, false);
            return;
        }

        BagLayout.Grid grid = BagLayout.forCapacityIn(capacity, areaX, areaY, areaWidth, areaHeight);
        // 格子底色（深色，压住原版底纹）+ 边框。
        for (int i = 0; i < capacity; i++) {
            int x = left + grid.slotX(i) - 1;
            int y = top + grid.slotY(i) - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_BORDER);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
            drawFrame(graphics, x, y);
        }
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 1, SLOT_BORDER);
        graphics.fill(x, y + 17, x + 18, y + 18, SLOT_BORDER);
        graphics.fill(x, y, x + 1, y + 18, SLOT_BORDER);
        graphics.fill(x + 17, y, x + 18, y + 18, SLOT_BORDER);
    }
}
