package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.ReactorMenu;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ReactorActionPacket;
import com.opamb.barotrauma.reactor.ReactorFuel;
import com.opamb.barotrauma.reactor.ReactorState;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Analog controls, status lamps, and a live generator trace. */
public class ReactorScreen extends ScaledContainerScreen<ReactorMenu> {

    private static final int W = 462;
    private static final int H = 292;

    private static final int EDGE = PanelStyle.EDGE;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.AMBER;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int YELLOW = 0xFFD8B44A;
    private static final int BLUE = 0xFF6FA8D0;
    private static final int TRACK = PanelStyle.RECESS;

    private static final int FUEL_X = ReactorMenu.FUEL_X;
    private static final int FUEL_Y = ReactorMenu.FUEL_Y;
    private static final int FUEL_STEP = ReactorMenu.FUEL_STEP;

    private static final int GAUGE1_X = 120;
    private static final int GAUGE2_X = 237;
    private static final int GAUGE_PIVOT_Y = 125;
    private static final int GAUGE_R = 49;

    private static final int SLIDER1_X = 70;
    private static final int SLIDER2_X = 187;
    private static final int SLIDER_W = 100;
    private static final int SLIDER_Y = 132;

    private static final int AUTO_X = 330;
    private static final int POWER_X = 409;
    private static final int TOP_CONTROLS_Y = 28;
    private static final int BOOST_X = 311;
    private static final int BOOST_UP_Y = 67;
    private static final int BOOST_DOWN_Y = 171;

    private static final String[] WARNINGS = {
            "温度过低", "输出过低", "燃料不足", "熔毁",
            "炉芯过热", "输出过高", "燃料耗尽", "紧急停堆"
    };

    private static final int GRAPH_SAMPLES = 32;
    private final double[] outputHistory = new double[GRAPH_SAMPLES];
    private final double[] loadHistory = new double[GRAPH_SAMPLES];
    private int historyTick;

    private boolean draggingFission;
    private boolean draggingTurbine;

    public ReactorScreen(ReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    private int data(int index) {
        return menu.data().get(index);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (++historyTick % 2 != 0) {
            return;
        }
        System.arraycopy(outputHistory, 0, outputHistory, 1, outputHistory.length - 1);
        System.arraycopy(loadHistory, 0, loadHistory, 1, loadHistory.length - 1);
        outputHistory[0] = data(ReactorMenu.DATA_OUTPUT);
        loadHistory[0] = data(ReactorMenu.DATA_LOAD);
    }


    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 9, TEXT, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        PanelStyle.frame(graphics, x, y, W, H, PanelStyle.AMBER);
        PanelStyle.divider(graphics, 12, 23, W - 24);
        PanelStyle.divider(graphics, 12, 57, W - 24);
        PanelStyle.well(graphics, 11, 65, 44, 129);
        PanelStyle.well(graphics, 62, 65, 233, 128);
        PanelStyle.well(graphics, 304, 63, 147, 131);
        PanelStyle.divider(graphics, 12, 205, W - 24);

        boolean powerOn = data(ReactorMenu.DATA_POWER_ON) != 0;
        boolean auto = data(ReactorMenu.DATA_AUTO) != 0;
        int targetFission = data(ReactorMenu.DATA_TARGET_FISSION);
        int targetTurbine = data(ReactorMenu.DATA_TARGET_TURBINE);
        double fission = data(ReactorMenu.DATA_FISSION) / 10.0D;
        double turbine = data(ReactorMenu.DATA_TURBINE) / 10.0D;
        double temperature = data(ReactorMenu.DATA_TEMPERATURE) / 10.0D;
        double fuel = data(ReactorMenu.DATA_FUEL) / 10.0D;
        int load = data(ReactorMenu.DATA_LOAD);
        int output = data(ReactorMenu.DATA_OUTPUT);
        double meltdownTimer = data(ReactorMenu.DATA_MELTDOWN) / 10.0D;
        boolean melted = data(ReactorMenu.DATA_MELTED) != 0;
        double correct = data(ReactorMenu.DATA_CORRECT_TURBINE);

        drawStatus(graphics, 19, 42, "临界热量", temperature > ReactorState.ALLOWED_TEMP_MAX);
        drawStatus(graphics, 109, 42, "临界输出", output > Math.max(1, load) * 1.5D);
        drawStatus(graphics, 199, 42, "临界低温", temperature < ReactorState.ALLOWED_TEMP_MIN);

        graphics.drawString(font, "燃料棒", 16, 70, TEXT, false);
        for (int i = 0; i < ReactorMenu.FUEL_SLOTS; i++) {
            int sy = y + FUEL_Y + i * FUEL_STEP;
            slotBg(graphics, x + FUEL_X, sy);
            ItemStack stack = menu.getSlot(i).getItem();
            double percent = ReactorFuel.conditionPercent(stack);
            int barX = x + FUEL_X + 21;
            int barH = 18;
            graphics.fill(barX, sy, barX + 4, sy + barH, TRACK);
            int filled = (int) Math.round(barH * percent / 100.0D);
            if (filled > 0) {
                int color = percent > 50 ? GREEN : percent > 20 ? YELLOW : RED;
                graphics.fill(barX, sy + barH - filled, barX + 4, sy + barH, color);
            }
        }

        // 两张半圆表
        double optFMin = ReactorState.optimalFissionMin(fuel);
        double optFMax = ReactorState.optimalFissionMax(fuel);
        double allowFMin = ReactorState.allowedFissionMin(fuel);
        double allowFMax = ReactorState.allowedFissionMax(fuel);
        drawGauge(graphics, x + GAUGE1_X, y + GAUGE_PIVOT_Y, GAUGE_R, fission,
                optFMin, optFMax, allowFMin, allowFMax, "裂变速率");
        double optTMin = Math.max(0.0D, correct - 10.0D);
        double optTMax = Math.min(100.0D, correct + 10.0D);
        drawGauge(graphics, x + GAUGE2_X, y + GAUGE_PIVOT_Y, GAUGE_R, turbine,
                optTMin, optTMax, Math.max(0.0D, correct - 20.0D),
                Math.min(100.0D, correct + 20.0D), "涡轮输出功率");

        boolean slidersEnabled = powerOn && !auto && !melted;
        PanelStyle.slider(graphics, SLIDER1_X, SLIDER_Y, SLIDER_W, targetFission / 100.0D,
                slidersEnabled, draggingFission);
        PanelStyle.slider(graphics, SLIDER2_X, SLIDER_Y, SLIDER_W, targetTurbine / 100.0D,
                slidersEnabled, draggingTurbine);
        graphics.drawCenteredString(font, targetFission + "%", GAUGE1_X, 144, slidersEnabled ? TEXT : DIM);
        graphics.drawCenteredString(font, targetTurbine + "%", GAUGE2_X, 144, slidersEnabled ? TEXT : DIM);

        boolean lowTemp = temperature < ReactorState.OPTIMAL_TEMP_MIN;
        boolean lowOutput = output < load * 0.9D;
        boolean lowFuel = fuel < fission;
        double faultDelay = data(ReactorMenu.DATA_CAPACITY) == 3000 ? 30.0D : ReactorState.MELTDOWN_DELAY;
        boolean meltdownWarn = meltdownTimer > faultDelay * 0.5D || melted;
        boolean overheat = temperature > ReactorState.OPTIMAL_TEMP_MAX;
        boolean highOutput = output > load * 1.1D;
        boolean fuelOut = fuel < 1.0D;
        boolean scram = temperature > 0.1D && !powerOn;
        boolean[] states = {lowTemp, lowOutput, lowFuel, meltdownWarn, overheat, highOutput, fuelOut, scram};
        for (int i = 0; i < WARNINGS.length; i++) {
            int wx = 69 + (i % 4) * 56;
            int wy = 157 + (i / 4) * 17;
            PanelStyle.well(graphics, wx, wy, 52, 13);
            if (states[i]) graphics.fill(wx + 1, wy + 1, wx + 51, wy + 12, 0xFF472B27);
            graphics.drawCenteredString(font, WARNINGS[i], wx + 26, wy + 2,
                    states[i] ? 0xFFFFB1A0 : DIM);
        }

        PanelStyle.control(graphics, AUTO_X, TOP_CONTROLS_Y, 71, 25, powerOn,
                powerOn && inRect(mouseX, mouseY, AUTO_X, TOP_CONTROLS_Y, 71, 25));
        PanelStyle.lamp(graphics, AUTO_X + 11, 40, auto && powerOn, YELLOW);
        graphics.drawString(font, "自动控制", AUTO_X + 21, 36, TEXT, false);
        PanelStyle.control(graphics, POWER_X, TOP_CONTROLS_Y, 42, 25, true,
                inRect(mouseX, mouseY, POWER_X, TOP_CONTROLS_Y, 42, 25));
        PanelStyle.lamp(graphics, POWER_X + 10, 40, powerOn, GREEN);
        drawCircle(graphics, POWER_X + 29, 40, 8, 0xFF753D35);
        drawCircle(graphics, POWER_X + 29, 39, 6, RED);
        graphics.fill(POWER_X + 26, 36, POWER_X + 31, 37, 0x60FFFFFF);

        drawButton(graphics, BOOST_X, BOOST_UP_Y, 14, "+");
        drawButton(graphics, BOOST_X, BOOST_DOWN_Y, 14, "−");
        int tempBarX = 313;
        int tempBarY = 85;
        int tempBarW = 10;
        int tempBarH = 81;
        PanelStyle.well(graphics, tempBarX, tempBarY, tempBarW, tempBarH);
        int fillH = (int) Math.round((tempBarH - 2) * Mth.clamp(temperature / 100.0D, 0.0D, 1.0D));
        if (fillH > 0) {
            graphics.fill(tempBarX + 1, tempBarY + tempBarH - 1 - fillH,
                    tempBarX + tempBarW - 1, tempBarY + tempBarH - 1, temperatureColor(temperature));
        }
        drawTempMarker(graphics, tempBarX, tempBarY, tempBarW, tempBarH, ReactorState.OPTIMAL_TEMP_MIN);
        drawTempMarker(graphics, tempBarX, tempBarY, tempBarW, tempBarH, ReactorState.OPTIMAL_TEMP_MAX);

        graphics.drawString(font, "负载  " + load + " kW", 333, 72, BLUE, false);
        graphics.drawString(font, "输出  " + output + " kW", 333, 174, YELLOW, false);
        drawGraph(graphics, 332, 85, 111, 81);

        if (data(ReactorMenu.DATA_CONDITION) <= 0) {
            graphics.drawString(font, melted ? "已熔毁" : "已损坏，需要螺丝刀", 12, 195, RED, false);
        }

        // 快捷栏格子底；上面那 27 格是装备背包区，由 BagOverlay 整块画。
        for (int col = 0; col < 9; col++) {
            slotBg(graphics, x + ReactorMenu.INV_AREA_X + col * 18 - 1,
                    y + ReactorMenu.INV_AREA_Y + 58 - 1);
        }
        if (minecraft != null && minecraft.player != null) {
            com.opamb.barotrauma.equipment.BagSlots.rebuildClientAt(menu, minecraft.player,
                    ReactorMenu.INV_START, ReactorMenu.INV_AREA_X, ReactorMenu.INV_AREA_Y,
                    ReactorMenu.INV_AREA_W, ReactorMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.BagOverlay.renderIn(graphics, x, y,
                    minecraft.player, ReactorMenu.INV_AREA_X, ReactorMenu.INV_AREA_Y,
                    ReactorMenu.INV_AREA_W, ReactorMenu.INV_AREA_H);
            com.opamb.barotrauma.client.equipment.HandsSlotOverlay.renderIn(graphics, menu, x, y);
        }
    }

    // ------------------------------------------------------------------ 交互

    private boolean inRect(double mouseX, double mouseY, int rx, int ry, int rw, int rh) {
        return mouseX >= leftPos + rx && mouseX < leftPos + rx + rw
                && mouseY >= topPos + ry && mouseY < topPos + ry + rh;
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button == 0) {
            boolean powerOn = data(ReactorMenu.DATA_POWER_ON) != 0;
            boolean auto = data(ReactorMenu.DATA_AUTO) != 0;
            if (inRect(mouseX, mouseY, AUTO_X, TOP_CONTROLS_Y, 71, 25)) {
                if (powerOn) {
                    send(ReactorActionPacket.TOGGLE_AUTO, 0);
                }
                return true;
            }
            if (inRect(mouseX, mouseY, POWER_X, TOP_CONTROLS_Y, 42, 25)) {
                send(ReactorActionPacket.TOGGLE_POWER, 0);
                return true;
            }
            if (inRect(mouseX, mouseY, BOOST_X, BOOST_UP_Y, 14, 14)) {
                send(ReactorActionPacket.TEMP_BOOST_UP, 0);
                return true;
            }
            if (inRect(mouseX, mouseY, BOOST_X, BOOST_DOWN_Y, 14, 14)) {
                send(ReactorActionPacket.TEMP_BOOST_DOWN, 0);
                return true;
            }
            if (powerOn && !auto && data(ReactorMenu.DATA_MELTED) == 0) {
                if (inRect(mouseX, mouseY, SLIDER1_X - 3, SLIDER_Y - 4, SLIDER_W + 6, 18)) {
                    draggingFission = true;
                    send(ReactorActionPacket.SET_FISSION, sliderValue(mouseX, SLIDER1_X));
                    return true;
                }
                if (inRect(mouseX, mouseY, SLIDER2_X - 3, SLIDER_Y - 4, SLIDER_W + 6, 18)) {
                    draggingTurbine = true;
                    send(ReactorActionPacket.SET_TURBINE, sliderValue(mouseX, SLIDER2_X));
                    return true;
                }
            }
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean mouseDragged(double screenX, double screenY, int button, double dragX, double dragY) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (draggingFission) {
            send(ReactorActionPacket.SET_FISSION, sliderValue(mouseX, SLIDER1_X));
            return true;
        }
        if (draggingTurbine) {
            send(ReactorActionPacket.SET_TURBINE, sliderValue(mouseX, SLIDER2_X));
            return true;
        }
        return super.mouseDragged(screenX, screenY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        draggingFission = false;
        draggingTurbine = false;
        return super.mouseReleased(screenX, screenY, button);
    }

    private int sliderValue(double mouseX, int sliderX) {
        double t = Mth.clamp((mouseX - leftPos - sliderX - 4) / (SLIDER_W - 8), 0.0D, 1.0D);
        return (int) Math.round(t * 100.0D);
    }

    private void send(int action, int value) {
        ModNetwork.CHANNEL.sendToServer(
                new ReactorActionPacket(menu.reactorPos(), action, value));
    }

    // ------------------------------------------------------------------ 绘图工具

    private void slotBg(GuiGraphics g, int x, int y) {
        PanelStyle.slot(g, x - 1, y - 1);
    }

    private void drawStatus(GuiGraphics g, int x, int y, String label, boolean active) {
        PanelStyle.lamp(g, x, y, active, RED);
        g.drawString(font, label, x + 10, y - 4, active ? TEXT : DIM, false);
    }

    private static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                 int color, int thickness) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            graphics.fill(x0, y0, x0 + thickness, y0 + thickness, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int px = x0 + (x1 - x0) * i / steps;
            int py = y0 + (y1 - y0) * i / steps;
            graphics.fill(px, py, px + thickness, py + thickness, color);
        }
    }

    /** 半圆表：枢轴在底边中心，0 在左、100 在右，和原作表盘一致。 */
    private void drawGauge(GuiGraphics graphics, int cx, int pivotY, int radius, double value,
                           double optimalMin, double optimalMax,
                           double allowedMin, double allowedMax, String label) {
        // 表盘（上半圆）
        for (int dy = -radius; dy <= 0; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(cx - dx, pivotY + dy, cx + dx + 1, pivotY + dy + 1, 0xFF18252B);
        }
        graphics.fill(cx - radius, pivotY, cx + radius + 1, pivotY + 1, EDGE);
        // 彩色量程环
        for (int degree = 0; degree <= 180; degree += 2) {
            double v = degree / 180.0D * 100.0D;
            int color;
            if (v < allowedMin || v > allowedMax) {
                color = RED;
            } else if (v < optimalMin || v > optimalMax) {
                color = ACCENT;
            } else {
                color = GREEN;
            }
            double angle = Math.toRadians(180.0D + degree);
            int px = (int) Math.round(cx + Math.cos(angle) * radius);
            int py = (int) Math.round(pivotY + Math.sin(angle) * radius);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, color);
            graphics.fill(px - 1, py - 2, px + 1, py - 1, color);
        }
        // 刻度 + 数字
        for (int v = 0; v <= 100; v += 10) {
            double angle = Math.toRadians(180.0D + v / 100.0D * 180.0D);
            int x0 = (int) Math.round(cx + Math.cos(angle) * (radius - 6));
            int y0 = (int) Math.round(pivotY + Math.sin(angle) * (radius - 6));
            int x1 = (int) Math.round(cx + Math.cos(angle) * radius);
            int y1 = (int) Math.round(pivotY + Math.sin(angle) * radius);
            drawLine(graphics, x0, y0, x1, y1, EDGE, 1);
            if (v == 20 || v == 50 || v == 80) {
                String text = Integer.toString(v);
                int tx = (int) Math.round(cx + Math.cos(angle) * (radius - 16));
                int ty = (int) Math.round(pivotY + Math.sin(angle) * (radius - 16));
                graphics.drawCenteredString(font, text, tx, ty - 3, TEXT);
            }
        }
        // 指针
        double angle = Math.toRadians(180.0D + Mth.clamp(value, 0.0D, 100.0D) / 100.0D * 180.0D);
        int nx = (int) Math.round(cx + Math.cos(angle) * (radius - 9));
        int ny = (int) Math.round(pivotY + Math.sin(angle) * (radius - 9));
        drawLine(graphics, cx, pivotY, nx, ny, 0xFFE0554A, 2);
        drawLine(graphics, cx, pivotY, (int) Math.round(cx - Math.cos(angle) * 5),
                (int) Math.round(pivotY - Math.sin(angle) * 5), 0xFF3A3A36, 2);
        drawCircle(graphics, cx, pivotY, 2, 0xFFE8E2D0);
        // 标签
        graphics.drawString(font, label, cx - font.width(label) / 2,
                pivotY - radius - 12, TEXT, false);
    }

    private static int temperatureColor(double temperature) {
        if (temperature < ReactorState.ALLOWED_TEMP_MIN) {
            return BLUE;
        }
        if (temperature <= ReactorState.OPTIMAL_TEMP_MAX) {
            return GREEN;
        }
        if (temperature <= ReactorState.ALLOWED_TEMP_MAX) {
            return ACCENT;
        }
        return RED;
    }

    private static void drawTempMarker(GuiGraphics graphics, int x, int y, int width,
                                       int height, double mark) {
        int py = y + height - (int) Math.round(height * mark / 100.0D);
        graphics.fill(x, py, x + width, py + 1, GREEN);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int size, String label) {
        PanelStyle.control(graphics, x, y, size, size, true, false);
        graphics.drawCenteredString(font, label, x + size / 2, y + 3, TEXT);
    }

    private void drawGraph(GuiGraphics graphics, int x, int y, int width, int height) {
        PanelStyle.well(graphics, x, y, width, height);
        for (int i = 1; i < 4; i++) {
            int gridY = y + i * height / 4;
            graphics.fill(x + 1, gridY, x + width - 1, gridY + 1, 0xFF202F36);
        }
        double max = Math.max(1000.0D, data(ReactorMenu.DATA_CAPACITY));
        for (int i = 0; i < GRAPH_SAMPLES; i++) {
            max = Math.max(max, Math.max(loadHistory[i], outputHistory[i]));
        }
        for (int i = 0; i < GRAPH_SAMPLES - 1; i++) {
            double ov = Math.min(outputHistory[i], max);
            double ov2 = Math.min(outputHistory[i + 1], max);
            int x1 = x + width - 2 - i * (width - 4) / (GRAPH_SAMPLES - 1);
            int x2 = x + width - 2 - (i + 1) * (width - 4) / (GRAPH_SAMPLES - 1);
            int y1 = y + height - 2 - (int) Math.round(ov / max * (height - 4));
            int y2 = y + height - 2 - (int) Math.round(ov2 / max * (height - 4));
            drawLine(graphics, x1, y1, x2, y2, YELLOW, 1);

            double lv = Math.min(loadHistory[i], max);
            double lv2 = Math.min(loadHistory[i + 1], max);
            int ly1 = y + height - 2 - (int) Math.round(lv / max * (height - 4));
            int ly2 = y + height - 2 - (int) Math.round(lv2 / max * (height - 4));
            drawLine(graphics, x1, ly1, x2, ly2, BLUE, 1);
        }
    }
}
