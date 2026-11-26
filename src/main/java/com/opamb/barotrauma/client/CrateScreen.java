package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.equipment.EquipmentSlot;
import com.opamb.barotrauma.equipment.HandsSlot;
import com.opamb.barotrauma.menu.CrateMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 货运箱界面：左边是和 E 界面一样的玩家物品栏（装备槽/背包/双手格/热键栏 +
 * 人物预览，没有合成栏、合成书），右边是箱子内部（每行 5 格，末行居中）。
 *
 * <p>和钢柜界面同一套深色钢板配色；打开期间隐藏右下角生命/状态 HUD。</p>
 */
public class CrateScreen extends ScaledContainerScreen<CrateMenu> {

    private static final int EDGE = PanelStyle.EDGE;
    private static final int HINT = 0xFF5A5A5A;
    private static final int TITLE = 0xFFC8CDD2;

    public CrateScreen(CrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.windowWidth();
        this.imageHeight = menu.windowHeight();
    }

    @Override
    protected void init() {
        super.init();
        // 标题离右侧面板左边缘留一点距离。
        this.titleLabelX = CrateMenu.CRATE_PANEL_X + 8;
        this.titleLabelY = 8;
    }

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderScaled(graphics, mouseX, mouseY, partialTick);
        // 赃物格子上的红手（任务货物）：画在物品图标之上。
        StolenMarker.renderSlots(graphics, menu, leftPos, topPos);
    }

    @Override
    protected java.util.List<Component> getTooltipFromContainerItem(
            net.minecraft.world.item.ItemStack stack) {
        java.util.List<Component> lines = new java.util.ArrayList<>(
                super.getTooltipFromContainerItem(stack));
        if (com.opamb.barotrauma.item.Illegitimate.is(stack)) {
            lines.add(Component.translatable("tooltip.barotrauma.stolen")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return lines;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int h = imageHeight;

        // 左边玩家面板（和原版背包一样 176 宽）+ 右边箱子面板。
        panel(graphics, x, y, CrateMenu.PLAYER_WIDTH, h);
        panel(graphics, x + CrateMenu.CRATE_PANEL_X, y,
                menu.cratePanelWidth() + 8, h);

        Font font = Minecraft.getInstance().font;
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int sx = x + slot.x;
            int sy = y + slot.y;
            PanelStyle.slot(graphics, sx - 1, sy - 1);
            if (slot.hasItem()) {
                continue;
            }
            String hint = null;
            if (slot instanceof EquipmentSlot equipment) {
                hint = equipment.type().hint();
            } else if (slot instanceof HandsSlot) {
                hint = "2H";
            }
            if (hint != null && !hint.isEmpty()) {
                graphics.drawString(font, hint, sx + (16 - font.width(hint)) / 2, sy + 4,
                        HINT, false);
            }
        }

        // 人物预览：坐标和原版背包界面一致。
        LivingEntity player = Minecraft.getInstance().player;
        if (player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    x + 51, y + 75, 30,
                    (float) (x + 51) - mouseX, (float) (y + 75 - 50) - mouseY, player);
        }
    }

    /** 一块深色钢板面板：底色 + 上/左亮边、下/右暗边。 */
    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        PanelStyle.frame(graphics, x, y, width, height, PanelStyle.SERVICE);
        // 顶部标题栏
        PanelStyle.divider(graphics, x + 8, y + 20, width - 16);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 箱子名画在右侧面板标题栏，左侧不要原版的"物品栏"字样。
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE, false);
    }
}
