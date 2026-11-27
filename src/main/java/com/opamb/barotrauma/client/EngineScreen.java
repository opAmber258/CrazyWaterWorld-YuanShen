package com.opamb.barotrauma.client;

import com.opamb.barotrauma.blockentity.EngineBlockEntity.Control;
import com.opamb.barotrauma.client.gui.PanelStyle;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.menu.EngineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class EngineScreen extends ScaledContainerScreen<EngineMenu> {

    private static final int W = 250;
    private static final int H = 132;
    private static final int SLIDER_X = 15;
    private static final int SLIDER_Y = 64;
    private static final int SLIDER_W = W - 2 * SLIDER_X;

    private boolean dragging;
    private int localStep;

    public EngineScreen(EngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
    }

    private int data(int index) {
        return menu.data().get(index);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, title, 12, 9, PanelStyle.TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        PanelStyle.frame(g, 0, 0, W, H, PanelStyle.AMBER);
        PanelStyle.divider(g, 12, 23, W - 24);

        int force = data(EngineMenu.DATA_FORCE);
        double voltage = data(EngineMenu.DATA_SUPPLY) / 1000.0D;
        boolean local = data(EngineMenu.DATA_CONTROL) == Control.LOCAL.ordinal();
        int target = dragging && local ? localStep * 10 - 100 : data(EngineMenu.DATA_TARGET_FORCE);
        boolean broken = data(EngineMenu.DATA_CONDITION) <= 0;
        boolean powered = voltage >= 0.5D && !broken;

        PanelStyle.lamp(g, 20, 38, powered, 0xFF72AD93);
        g.drawString(font, Component.translatable("power.indicator.powered"), 32, 34,
                powered ? PanelStyle.TEXT : PanelStyle.DIM, false);
        boolean helm = data(EngineMenu.DATA_CONTROL) == Control.HELM.ordinal();
        PanelStyle.lamp(g, 140, 38, helm, PanelStyle.AMBER);
        g.drawString(font, Component.translatable("gui.barotrauma.engine.helm_input"), 152, 34,
                helm ? PanelStyle.TEXT : PanelStyle.DIM, false);

        g.drawCenteredString(font, "目标推力  " + signed(target), W / 2, 50, PanelStyle.TEXT);
        PanelStyle.slider(g, SLIDER_X, SLIDER_Y, SLIDER_W, (target + 100.0D) / 200.0D, local, dragging);
        int marker = SLIDER_X + 4 + (int) Math.round((SLIDER_W - 8) * (force + 100.0D) / 200.0D);
        g.fill(marker - 1, 75, marker + 2, 77, 0xFF6CB7AD);
        g.drawString(font, Component.translatable("gui.barotrauma.engine.reverse"), SLIDER_X, 80, PanelStyle.DIM, false);
        Component ahead = Component.translatable("gui.barotrauma.engine.ahead");
        g.drawString(font, ahead, W - SLIDER_X - font.width(ahead), 80, PanelStyle.DIM, false);
        g.drawCenteredString(font, "实际  " + signed(force), W / 2, 80, 0xFF8EC6C1);

        PanelStyle.well(g, 12, 96, W - 24, 24);
        String power = "电压  " + Math.round(voltage * 100) + "%";
        String draw = "负载请求  " + data(EngineMenu.DATA_DEMAND) + " kW";
        if (!local && !helm && !broken) draw = "装配后可调本机推力";
        g.drawString(font, power, 19, 104, powered ? PanelStyle.TEXT : 0xFFD47766, false);
        g.drawString(font, draw, W - 19 - font.width(draw), 104, PanelStyle.DIM, false);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        double dx = designX(x);
        double dy = designY(y);
        if (button == 0 && data(EngineMenu.DATA_CONTROL) == Control.LOCAL.ordinal()
                && dx >= SLIDER_X && dx < SLIDER_X + SLIDER_W && dy >= SLIDER_Y - 3 && dy < SLIDER_Y + 13) {
            dragging = true;
            localStep = -1;
            setThrust(dx);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (button == 0 && dragging) {
            if (data(EngineMenu.DATA_CONTROL) == Control.LOCAL.ordinal()) {
                setThrust(designX(x));
            } else {
                dragging = false;
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    private void setThrust(double x) {
        int step = Mth.clamp((int) Math.round((x - SLIDER_X - 4) * 20 / (SLIDER_W - 8)), 0, 20);
        if (step != localStep && minecraft != null && minecraft.gameMode != null) {
            localStep = step;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, step);
        }
    }

    private static String signed(int force) {
        return (force > 0 ? "+" : "") + force + "%";
    }
}
