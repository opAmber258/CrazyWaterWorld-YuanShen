package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.PumpMenu;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.PumpActionPacket;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
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
public class PumpScreen extends ScaledContainerScreen<PumpMenu> {

    private static final int W = 220;
    private static final int H = 176;

    private static final int EDGE = PanelStyle.EDGE;
    private static final int PANEL = PanelStyle.RECESS;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.AMBER;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int YELLOW = 0xFFD8B44A;
    private static final int TRACK = PanelStyle.RECESS;

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
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 9, 7, ACCENT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        PanelStyle.frame(graphics, x, y, W, H, PanelStyle.AMBER);

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
        if (menu.data().get(PumpMenu.DATA_CONDITION) <= 0) {
            state = "泵已损坏，需要扳手修理";
        } else if (!powered) {
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
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button != 0) {
            return super.mouseClicked(screenX, screenY, button);
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
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean mouseDragged(double screenX, double screenY, int button, double dragX, double dragY) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (dragging && button == 0) {
            sendFlow(flowAt(mouseX));
            return true;
        }
        return super.mouseDragged(screenX, screenY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        dragging = false;
        return super.mouseReleased(screenX, screenY, button);
    }

    private void sendFlow(int value) {
        ModNetwork.CHANNEL.sendToServer(
                new PumpActionPacket(menu.pumpPos(), PumpActionPacket.SET_FLOW, value));
    }
}
