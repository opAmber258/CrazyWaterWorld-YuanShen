package com.opamb.barotrauma.client;

import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.menu.CabinetMenu;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 钢柜 / 货架 / 潜水服柜 / 氧气瓶架的容器界面。
 *
 * <p>按原作容器 UI 的深色钢板风格自绘：深色面板、细边框、方格槽位、
 * 顶部标题与分区标签；槽位布局和数量跟方块实体一致。玩家那半边和
 * 加工台/解构仪一样：3×9 装备背包区 + 快捷栏，不再画原版 27 格。</p>
 */
public class CabinetScreen extends ScaledContainerScreen<CabinetMenu> {

    private static final int W = 176;
    private static final int BG = 0xFF1A1D20;
    private static final int HEADER = 0xFF23282D;
    private static final int EDGE = 0xFF4A5157;
    private static final int EDGE_DARK = 0xFF0B0D0F;
    private static final int SLOT = 0xFF0E1012;
    private static final int SLOT_HI = 0xFF3B434A;
    private static final int SLOT_LO = 0xFF050607;
    private static final int TITLE = 0xFFC8CDD2;
    private static final int DIM = 0xFF8A9198;
    private static final int ACCENT = 0xFF5FA8C8;

    private final int panelHeight;

    public CabinetScreen(CabinetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        CabinetBlock.Kind kind = menu.kind();
        this.panelHeight = kind == CabinetBlock.Kind.DIVING_SUIT_STATION ? 196
                : kind.tall() ? 222 : 166;
        this.imageHeight = panelHeight;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int h = panelHeight;

        graphics.fill(x, y, x + W, y + h, BG);
        graphics.fill(x, y, x + W, y + 1, EDGE);
        graphics.fill(x, y + h - 1, x + W, y + h, EDGE_DARK);
        graphics.fill(x, y, x + 1, y + h, EDGE);
        graphics.fill(x + W - 1, y, x + W, y + h, EDGE_DARK);
        graphics.fill(x + 1, y + 1, x + W - 1, y + 17, HEADER);
        graphics.fill(x + 1, y + 17, x + W - 1, y + 18, EDGE_DARK);

        CabinetBlock.Kind kind = menu.kind();
        graphics.drawString(font, Component.translatable("container.barotrauma." + kind.id()),
                x + 8, y + 6, TITLE, false);

        if (kind == CabinetBlock.Kind.DIVING_SUIT_STATION) {
            graphics.drawString(font, Component.translatable("container.barotrauma.section.suit"),
                    x + 8, y + 32, ACCENT, false);
            graphics.drawString(font, Component.translatable("container.barotrauma.section.oxygen"),
                    x + 8, y + 66, ACCENT, false);
            graphics.drawString(font, Component.translatable("container.barotrauma.inventory"),
                    x + 8, y + 92, DIM, false);
        } else if (kind.tall()) {
            if (kind != CabinetBlock.Kind.CRATE_SHELF) {
                graphics.drawString(font, Component.translatable("container.barotrauma.section.sub"),
                        x + 43, y + 96, ACCENT, false);
            }
            graphics.drawString(font, Component.translatable("container.barotrauma.inventory"),
                    x + 8, y + 130, DIM, false);
        } else {
            graphics.drawString(font,
                    Component.translatable(kind == CabinetBlock.Kind.DIVING_SUIT_LOCKER
                            ? "container.barotrauma.section.suit"
                            : "container.barotrauma.section.oxygen"),
                    x + 8, y + 32, ACCENT, false);
            graphics.drawString(font, Component.translatable("container.barotrauma.inventory"),
                    x + 8, y + 72, DIM, false);
        }

        // 容器格：仍用钢柜自己的深色格。
        int containerSlots = Math.min(menu.kind().slots(), menu.slots.size());
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);
            drawSlot(graphics, x + slot.x - 1, y + slot.y - 1);
        }
        // 玩家区：快捷栏格子底；上面那 27 格是"装备背包区"，由 BagOverlay 整块画。
        int areaY = menu.playerAreaY();
        for (int col = 0; col < 9; col++) {
            slotBg(graphics, x + CabinetMenu.INV_AREA_X + col * 18 - 1,
                    y + areaY + 58 - 1);
        }
        if (minecraft != null && minecraft.player != null) {
            // 背包换了（容量变化）先重建格子位置，再整块画背包区。
            BagSlots.rebuildClientAt(menu, minecraft.player, menu.invStart(),
                    CabinetMenu.INV_AREA_X, areaY,
                    CabinetMenu.INV_AREA_W, CabinetMenu.INV_AREA_H);
            BagOverlay.renderIn(graphics, x, y, minecraft.player,
                    CabinetMenu.INV_AREA_X, areaY,
                    CabinetMenu.INV_AREA_W, CabinetMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.HandsSlotOverlay.renderIn(graphics, menu, x, y);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT);
        graphics.fill(x, y, x + 18, y + 1, SLOT_HI);
        graphics.fill(x, y, x + 1, y + 18, SLOT_HI);
        graphics.fill(x, y + 17, x + 18, y + 18, SLOT_LO);
        graphics.fill(x + 17, y, x + 18, y + 18, SLOT_LO);
    }

    /** 玩家物品栏格子底（和加工台/解构仪的 {@code slotBg} 同款）。 */
    private static void slotBg(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题与分区标签已在 renderBg 里绘制（面板坐标系）。
    }
}
