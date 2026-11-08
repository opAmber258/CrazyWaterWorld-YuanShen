package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.PumpMenu;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.PumpActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 水泵面板，布局照原版 {@code pump + water detector} 的组合界面：
 *
 * <pre>
 *   能源 ●        自动控制 ●
 *   泵出速度: -100%
 *   [====滑块====]
 *   泵出                泵入
 * </pre>
 *
 * <p>原版是水泵读水传感器；这里传感器已经并进泵里，所以"自动控制"就是
 * 有水自动泵出、没水停机。自动控制打开时滑块置灰，等同原版传感器接管。</p>
 */
public class PumpScreen extends AbstractContainerScreen<PumpMenu> {

    private static final int W = 220;
    private static final int H = 118;

    private static final int BG = 0xF0141B16;
    private static final int EDGE = 0xFF3B463C;
    private static final int PANEL = 0xFF1E2A22;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF7C8478;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int YELLOW = 0xFFD8B44A;
    private static final int TRACK = 0xFF0C120E;

    /** 滑块的百分比下限/上限。 */
    private static final int FLOW_MIN = -100;
    private static final int FLOW_MAX = 100;

    private boolean dragging;

    public PumpScreen(PumpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 7, ACCENT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + W, y + H, BG);
        graphics.fill(x, y, x + W, y + 1, EDGE);
        graphics.fill(x, y + H - 1, x + W, y + H, EDGE);
        graphics.fill(x, y, x + 1, y + H, EDGE);
        graphics.fill(x + W - 1, y, x + W, y + H, EDGE);

        boolean auto = menu.data().get(PumpMenu.DATA_AUTO) != 0;
        int flow = menu.data().get(PumpMenu.DATA_FLOW);
        boolean powered = menu.data().get(PumpMenu.DATA_POWER) != 0;
        boolean ballast = menu.data().get(PumpMenu.DATA_BALLAST) != 0;

        // 能源灯（原版左上角那颗）
        graphics.drawString(font, "能源", x + 10, y + 24, TEXT, false);
        drawLamp(graphics, x + 44, y + 27, powered ? GREEN : RED);

        // 自动控制开关
        graphics.drawString(font, "自动控制", x + W - 74, y + 24, TEXT, false);
        drawLamp(graphics, x + W - 22, y + 27, auto ? YELLOW : 0xFF39423A);

        // 泵出速度
        int percentY = y + 44;
        graphics.drawString(font, "泵出速度: " + flow + "%", x + 10, percentY,
                auto ? DIM : TEXT, false);

        // 滑块
        int trackX = x + 10;
        int trackW = W - 20;
        int trackY = y + 60;
        graphics.fill(trackX, trackY, trackX + trackW, trackY + 8, TRACK);
        graphics.fill(trackX, trackY, trackX + trackW, trackY + 1, EDGE);
        // 中位刻度（0%）
        int midX = trackX + trackW / 2;
        graphics.fill(midX, trackY, midX + 1, trackY + 8, EDGE);
        int knobX = trackX + Math.round((flow - FLOW_MIN) / (float) (FLOW_MAX - FLOW_MIN) * trackW);
        graphics.fill(knobX - 3, trackY - 2, knobX + 3, trackY + 10,
                auto ? 0xFF4A544B : 0xFFB8C0B4);

        // 两端说明
        graphics.drawString(font, "泵出", trackX, trackY + 14, flow < 0 ? GREEN : DIM, false);
        graphics.drawString(font, "泵入", trackX + trackW - font.width("泵入"),
                trackY + 14, flow > 0 ? ACCENT : DIM, false);

        // 状态/提示
        String state;
        if (!powered) {
            state = "无电力，泵无法运行";
        } else if (auto && ballast) {
            state = flow > 0 ? "跟随舵台：下潜，泵入压载舱"
                    : flow < 0 ? "跟随舵台：上浮，泵出压载舱"
                    : "跟随舵台：停车，保持压载水位";
        } else if (auto) {
            state = flow < 0 ? "自动：检测到积水，全速泵出" : "自动：舱内干燥，待机";
        } else if (flow == 0) {
            state = "已停机";
        } else if (flow < 0) {
            state = "手动：泵出 " + (-flow) + "%";
        } else {
            state = "手动：泵入 " + flow + "%";
        }
        graphics.drawString(font, state, x + 10, y + 92,
                powered ? DIM : RED, false);
    }

    /** 圆角指示灯（4 层像素圆，避免引入额外贴图）。 */
    private static void drawLamp(GuiGraphics graphics, int cx, int cy, int color) {
        graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, color);
        graphics.fill(cx - 4, cy - 2, cx + 4, cy + 2, color);
        graphics.fill(cx - 2, cy - 4, cx + 2, cy + 4, color);
    }

    private int trackLeft() {
        return leftPos + 10;
    }

    private int trackWidth() {
        return W - 20;
    }

    private int flowAt(double mouseX) {
        double t = Mth.clamp((mouseX - trackLeft()) / trackWidth(), 0.0D, 1.0D);
        int value = (int) Math.round(FLOW_MIN + t * (FLOW_MAX - FLOW_MIN));
        // 对齐滑块刻度，避免鼠标抖动发出大量包
        return Math.round(value / 5.0F) * 5;
    }

    private boolean inSlider(double mouseX, double mouseY) {
        int y = topPos + 56;
        return mouseX >= trackLeft() - 4 && mouseX <= trackLeft() + trackWidth() + 4
                && mouseY >= y && mouseY <= y + 20;
    }

    private boolean inAutoToggle(double mouseX, double mouseY) {
        int x = leftPos + W - 84;
        int y = topPos + 18;
        return mouseX >= x && mouseX <= leftPos + W - 10 && mouseY >= y && mouseY <= y + 22;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (inAutoToggle(mouseX, mouseY)) {
            ModNetwork.CHANNEL.sendToServer(
                    new PumpActionPacket(menu.pumpPos(), PumpActionPacket.TOGGLE_AUTO, 0));
            return true;
        }
        if (inSlider(mouseX, mouseY) && menu.data().get(PumpMenu.DATA_AUTO) == 0) {
            dragging = true;
            sendFlow(flowAt(mouseX));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            sendFlow(flowAt(mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void sendFlow(int value) {
        ModNetwork.CHANNEL.sendToServer(
                new PumpActionPacket(menu.pumpPos(), PumpActionPacket.SET_FLOW, value));
    }
}
