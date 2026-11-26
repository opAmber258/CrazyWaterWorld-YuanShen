package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.gui.PanelStyle;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.menu.PumpMenu;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.PumpActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class PumpScreen extends ScaledContainerScreen<PumpMenu> {

    private static final int WIDTH = 236;
    private static final int HEIGHT = 120;
    private static final int FLOW_LEFT = 79;
    private static final int FLOW_WIDTH = WIDTH - FLOW_LEFT - 12;

    private boolean dragging;
    private int requestedFlow;

    public PumpScreen(PumpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
    }

    private boolean flag(int index) {
        return menu.data().get(index) != 0;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, title, 12, 9, PanelStyle.TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        PanelStyle.frame(g, 0, 0, WIDTH, HEIGHT, PanelStyle.AMBER);
        PanelStyle.divider(g, 12, 23, WIDTH - 24);
        boolean enabled = flag(PumpMenu.DATA_ENABLED);
        boolean motionPower = flag(PumpMenu.DATA_POWER);
        boolean auto = flag(PumpMenu.DATA_AUTO);
        boolean ballast = flag(PumpMenu.DATA_BALLAST);
        int flow = dragging ? requestedFlow : menu.data().get(PumpMenu.DATA_FLOW);
        boolean broken = !flag(PumpMenu.DATA_CONDITION);

        PanelStyle.well(g, 12, 29, 54, 65);
        g.drawCenteredString(font, "能源", 39, 33, PanelStyle.DIM);
        PanelStyle.lamp(g, 39, 48, enabled, motionPower ? 0xFF6CB39D : 0xFFD27465);
        PanelStyle.control(g, 25, 57, 28, 27, true, hit(mouseX, mouseY, 25, 57, 28, 27));
        g.fillGradient(30, 62, 48, 79, enabled ? 0xFFE16C56 : 0xFF823C35, 0xFF5C2928);
        g.hLine(33, 44, 63, 0x70FFFFFF);

        PanelStyle.control(g, FLOW_LEFT, 30, FLOW_WIDTH, 16, true,
                hit(mouseX, mouseY, FLOW_LEFT, 30, FLOW_WIDTH, 16));
        PanelStyle.lamp(g, FLOW_LEFT + 10, 37, auto, PanelStyle.AMBER);
        g.drawString(font, "自动控制", FLOW_LEFT + 21, 34, PanelStyle.TEXT, false);
        String percent = (flow > 0 ? "+" : "") + flow + "%";
        g.drawString(font, "泵速", FLOW_LEFT, 53, PanelStyle.DIM, false);
        g.drawString(font, percent, WIDTH - 12 - font.width(percent), 53, PanelStyle.TEXT, false);
        PanelStyle.slider(g, FLOW_LEFT, 65, FLOW_WIDTH, (flow + 100.0D) / 200.0D,
                enabled && !auto && !broken, dragging);
        g.drawString(font, "泵出", FLOW_LEFT, 82, flow < 0 ? PanelStyle.TEXT : PanelStyle.DIM, false);
        g.drawCenteredString(font, "0", FLOW_LEFT + FLOW_WIDTH / 2, 82, PanelStyle.DIM);
        g.drawString(font, "泵入", WIDTH - 12 - font.width("泵入"), 82,
                flow > 0 ? PanelStyle.TEXT : PanelStyle.DIM, false);

        PanelStyle.divider(g, 12, 98, WIDTH - 24);
        String status = broken ? "泵已损坏" : !enabled ? "泵已关闭" : !motionPower ? "电压不足"
                : auto ? (ballast ? "跟随舵台的压载目标" : "跟随舱室水位") : "手动控制";
        g.drawString(font, status, 12, 104, broken || enabled && !motionPower ? 0xFFD47766 : PanelStyle.DIM, false);
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double x = designX(screenX);
        double y = designY(screenY);
        if (button == 0) {
            if (hit(x, y, 25, 57, 28, 27)) {
                send(PumpActionPacket.TOGGLE_POWER, 0);
                return true;
            }
            if (hit(x, y, FLOW_LEFT, 30, FLOW_WIDTH, 16)) {
                send(PumpActionPacket.TOGGLE_AUTO, 0);
                return true;
            }
            if (hit(x, y, FLOW_LEFT, 62, FLOW_WIDTH, 16)
                    && flag(PumpMenu.DATA_ENABLED) && !flag(PumpMenu.DATA_AUTO) && flag(PumpMenu.DATA_CONDITION)) {
                dragging = true;
                requestedFlow = Integer.MIN_VALUE;
                moveFlow(x);
                return true;
            }
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && dragging) {
            moveFlow(designX(mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void moveFlow(double x) {
        double fraction = Mth.clamp((x - FLOW_LEFT - 4.0D) / (FLOW_WIDTH - 8), 0.0D, 1.0D);
        int next = (int) Math.round((fraction * 200.0D - 100.0D) / 5.0D) * 5;
        if (next != requestedFlow) {
            requestedFlow = next;
            send(PumpActionPacket.SET_FLOW, next);
        }
    }

    private void send(int action, int value) {
        ModNetwork.sendToServer(new PumpActionPacket(menu.pumpPos(), action, value));
    }

    private static boolean hit(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}
