package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.equipment.BagOverlay;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.equipment.BagSlots;
import com.opamb.barotrauma.menu.DeconstructorMenu;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 解构仪界面：8 个输入格（4×2）+ 5 个输出格 + 拆解/取消按钮 + 进度条，
 * 下面是玩家物品栏和工具带/背包格。
 *
 * <p>物品栏那块 3×9 区域本身就和玩家物品栏一样：装备了工具带/背包就在里面
 * 居中画格子，没装备就是一块黑底 + 提示，不再另外单开一块背包面板。</p>
 */
public class DeconstructorScreen extends ScaledContainerScreen<DeconstructorMenu> {

    private static final int W = 268;
    private static final int H = 216;

    private static final int EDGE = PanelStyle.EDGE;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.AMBER;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int TRACK = PanelStyle.RECESS;

    public DeconstructorScreen(DeconstructorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, ACCENT, false);
        graphics.drawString(font, Component.translatable(
                "gui.barotrauma.deconstructor.input").getString(), 16, 16, DIM, false);
        graphics.drawString(font, Component.translatable(
                "gui.barotrauma.deconstructor.output").getString(), 16, 72, DIM, false);

        int condition = menu.data().get(DeconstructorMenu.DATA_CONDITION);
        int working = menu.data().get(DeconstructorMenu.DATA_WORKING);
        int power = menu.data().get(DeconstructorMenu.DATA_POWER);
        String status = condition <= 0
                ? Component.translatable("gui.barotrauma.deconstructor.broken").getString()
                : power == 0
                        ? Component.translatable(working != 0
                                ? "gui.barotrauma.deconstructor.no_power_paused"
                                : "gui.barotrauma.deconstructor.no_power").getString()
                        : Component.translatable("gui.barotrauma.deconstructor.ready").getString();
        int color = condition <= 0 ? RED : power == 0 ? 0xFFD8B44A : DIM;
        graphics.drawString(font, font.plainSubstrByWidth(status, 100), 151, 100, color, false);


    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        PanelStyle.frame(graphics, x, y, W, H, PanelStyle.AMBER);

        PanelStyle.well(graphics, x + 10, y + 26, 119, 83);
        PanelStyle.well(graphics, x + 140, y + 26, W - 150, 89);
        PanelStyle.divider(graphics, x + 10, y + 121, W - 20);

        // 8 进 5 出的格子底
        for (int i = 0; i < DeconstructorMenu.INPUT_SLOTS; i++) {
            slotBg(graphics, x + 16 + (i % 4) * 20 - 1, y + 30 + (i / 4) * 20 - 1);
        }
        for (int i = 0; i < DeconstructorMenu.OUTPUT_SLOTS; i++) {
            slotBg(graphics, x + 16 + i * 20 - 1, y + 86 - 1);
        }

        boolean working = menu.data().get(DeconstructorMenu.DATA_WORKING) != 0;
        boolean canStart = !working && menu.data().get(DeconstructorMenu.DATA_CONDITION) > 0
                && menu.data().get(DeconstructorMenu.DATA_POWER) != 0;
        drawButton(graphics, x + 150, y + 30, 96, 18,
                Component.translatable("gui.barotrauma.deconstructor.start").getString(),
                canStart, mouseX, mouseY);
        drawButton(graphics, x + 150, y + 54, 96, 18,
                Component.translatable("gui.barotrauma.deconstructor.cancel").getString(),
                working, mouseX, mouseY);

        int progress = menu.data().get(DeconstructorMenu.DATA_PROGRESS);
        graphics.fill(x + 150, y + 82, x + 246, y + 92, TRACK);
        int filled = Math.round(96 * Mth.clamp(progress / 1000.0F, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(x + 150, y + 82, x + 150 + filled, y + 92, GREEN);
        }
        String percent = (progress / 10) + "%";
        graphics.drawString(font, percent, x + 150 + (96 - font.width(percent)) / 2,
                y + 83, TEXT, false);

        // 快捷栏格子底；上面那 27 格是"装备背包区"，由 BagOverlay 整块画。
        for (int col = 0; col < 9; col++) {
            slotBg(graphics, x + DeconstructorMenu.INV_AREA_X + col * 18 - 1,
                    y + DeconstructorMenu.INV_AREA_Y + 58 - 1);
        }
        if (minecraft != null && minecraft.player != null) {
            // 背包换了（容量变化）先重建格子位置，再整块画背包区。
            BagSlots.rebuildClientAt(menu, minecraft.player, DeconstructorMenu.INV_START,
                    DeconstructorMenu.INV_AREA_X, DeconstructorMenu.INV_AREA_Y,
                    DeconstructorMenu.INV_AREA_W, DeconstructorMenu.INV_AREA_H);
            BagOverlay.renderIn(graphics, x, y, minecraft.player,
                    DeconstructorMenu.INV_AREA_X, DeconstructorMenu.INV_AREA_Y,
                    DeconstructorMenu.INV_AREA_W, DeconstructorMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.HandsSlotOverlay.renderIn(graphics, menu, x, y);
        }
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, String label,
                            boolean active, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        PanelStyle.control(graphics, x, y, w, h, active, hovered && active);
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + 5,
                active ? TEXT : DIM, false);
    }

    private void slotBg(GuiGraphics graphics, int x, int y) {
        PanelStyle.slot(graphics, x, y);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button == 0) {
            int x = leftPos;
            int y = topPos;
            boolean working = menu.data().get(DeconstructorMenu.DATA_WORKING) != 0;
            if (inside(mouseX, mouseY, x + 150, y + 30, 96, 18)) {
                if (!working && minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            DeconstructorMenu.BUTTON_START);
                }
                return true;
            }
            if (inside(mouseX, mouseY, x + 150, y + 54, 96, 18)) {
                if (working && minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            DeconstructorMenu.BUTTON_CANCEL);
                }
                return true;
            }
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
