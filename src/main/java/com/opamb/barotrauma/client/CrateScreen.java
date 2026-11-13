package com.opamb.barotrauma.client;

import com.opamb.barotrauma.equipment.EquipmentSlot;
import com.opamb.barotrauma.equipment.HandsSlot;
import com.opamb.barotrauma.menu.CrateMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
public class CrateScreen extends AbstractContainerScreen<CrateMenu> {

    private static final int BG = 0xFF1A1D20;
    private static final int EDGE = 0xFF4A5157;
    private static final int EDGE_DARK = 0xFF0B0D0F;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 赃物格子上的红手（任务货物）：画在物品图标之上。
        StolenMarker.renderSlots(graphics, menu, leftPos, topPos);
        // 原版 AbstractContainerScreen 不自己画悬停物品 tooltip（背包界面是在
        // InventoryScreen.render 里显式调的），自建界面必须自己补这一句。
        this.renderTooltip(graphics, mouseX, mouseY);
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
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BORDER);
            graphics.fill(sx, sy, sx + 16, sy + 16, SLOT_FILL);
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
        graphics.fill(x, y, x + width, y + height, BG);
        graphics.fill(x, y, x + width, y + 1, EDGE);
        graphics.fill(x, y + height - 1, x + width, y + height, EDGE_DARK);
        graphics.fill(x, y, x + 1, y + height, EDGE);
        graphics.fill(x + width - 1, y, x + width, y + height, EDGE_DARK);
        // 顶部标题栏
        graphics.fill(x + 1, y + 1, x + width - 1, y + 18, 0xFF23282D);
        graphics.fill(x + 1, y + 18, x + width - 1, y + 19, EDGE_DARK);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 箱子名画在右侧面板标题栏，左侧不要原版的"物品栏"字样。
        graphics.drawString(font, title, titleLabelX, titleLabelY, TITLE, false);
    }
}
