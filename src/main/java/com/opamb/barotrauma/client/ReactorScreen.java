package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.ReactorMenu;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ReactorActionPacket;
import com.opamb.barotrauma.reactor.ReactorFuel;
import com.opamb.barotrauma.reactor.ReactorState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 核反应堆面板，布局按原作 Reactor GUI 重排：
 *
 * <pre>
 *   燃料棒×4 | ● 临界热量   ● 临界输出   ● 临界低温      拉杆 ◉ 自动控制   ◉ 能源 (红按钮)
 *            | [裂变速率半圆表]  [涡轮输出半圆表]        +  负载: 30 千瓦
 *            | [滑条]            [滑条]                   [温度条] [曲线]
 *            | 温度过低 输出过低 燃料供应不足 熔毁        -  输出: 0 千瓦
 *            | 炉芯过热 输出过高 燃料耗尽   紧急停堆
 *   物品栏
 * </pre>
 *
 * <p>红按钮只负责开关，旁边的指示灯才显示能源状态；自动控制是拉杆，
 * 右侧黄灯显示自动控制是否生效，拉杆本身不变黄。</p>
 */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {

    private static final int W = 400;
    private static final int H = 300;

    private static final int BG = 0xF0141B16;
    private static final int EDGE = 0xFF3B463C;
    private static final int INNER = 0xFF243027;
    private static final int PANEL = 0xFF1A241D;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF7C8478;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF6FD07A;
    private static final int RED = 0xFFC8483C;
    private static final int YELLOW = 0xFFD8B44A;
    private static final int BLUE = 0xFF6FA8D0;
    private static final int TRACK = 0xFF0C120E;
    private static final int BUTTON_FACE = 0xFF3E3A2E;
    private static final int BUTTON_BORDER = 0xFF6E6450;
    private static final int BUTTON_TEXT = 0xFFC9BFA8;

    private static final int FUEL_X = 12;
    private static final int FUEL_Y = 32;
    private static final int FUEL_STEP = 24;

    private static final int GAUGE1_X = 140;
    private static final int GAUGE2_X = 235;
    private static final int GAUGE_PIVOT_Y = 112;
    private static final int GAUGE_R = 45;

    private static final int SLIDER1_X = 95;
    private static final int SLIDER2_X = 190;
    private static final int SLIDER_W = 90;
    private static final int SLIDER_Y = 120;

    private static final String[] WARNINGS = {
            "温度过低", "输出过低", "燃料供应不足", "熔毁",
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, ACCENT, false);
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
        graphics.fill(x + 4, y + 4, x + W - 4, y + 4 + 1, INNER);
        graphics.fill(x + 4, y + H - 5, x + W - 4, y + H - 4, INNER);

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

        // 顶部三颗临界灯
        drawLamp(graphics, x + 64, y + 18, temperature > ReactorState.ALLOWED_TEMP_MAX ? RED : 0xFF39423A);
        graphics.drawString(font, "临界热量", x + 72, y + 14, TEXT, false);
        drawLamp(graphics, x + 152, y + 18, output > Math.max(1, load) * 1.5D ? RED : 0xFF39423A);
        graphics.drawString(font, "临界输出", x + 160, y + 14, TEXT, false);
        drawLamp(graphics, x + 240, y + 18, temperature < ReactorState.ALLOWED_TEMP_MIN ? RED : 0xFF39423A);
        graphics.drawString(font, "临界低温", x + 248, y + 14, TEXT, false);

        // 燃料槽
        graphics.drawString(font, "燃料棒", x + FUEL_X, y + 18, TEXT, false);
        for (int i = 0; i < ReactorMenu.FUEL_SLOTS; i++) {
            int sy = y + FUEL_Y + i * FUEL_STEP;
            slotBg(graphics, x + FUEL_X, sy);
            ItemStack stack = menu.getSlot(i).getItem();
            double percent = ReactorFuel.conditionPercent(stack);
            int barX = x + FUEL_X + 22;
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
        drawSlider(graphics, x + SLIDER1_X, y + SLIDER_Y, SLIDER_W, targetFission, slidersEnabled);
        drawSlider(graphics, x + SLIDER2_X, y + SLIDER_Y, SLIDER_W, targetTurbine, slidersEnabled);
        graphics.drawString(font, targetFission + "%", x + SLIDER1_X, y + SLIDER_Y + 12,
                slidersEnabled ? TEXT : DIM, false);
        graphics.drawString(font, targetTurbine + "%", x + SLIDER2_X, y + SLIDER_Y + 12,
                slidersEnabled ? TEXT : DIM, false);

        // 8 个告警按钮
        boolean lowTemp = temperature < ReactorState.OPTIMAL_TEMP_MIN;
        boolean lowOutput = output < load * 0.9D;
        boolean lowFuel = fuel < fission;
        boolean meltdownWarn = meltdownTimer > ReactorState.MELTDOWN_DELAY * 0.5D || melted;
        boolean overheat = temperature > ReactorState.OPTIMAL_TEMP_MAX;
        boolean highOutput = output > load * 1.1D;
        boolean fuelOut = fuel < 1.0D;
        boolean scram = temperature > 0.1D && !powerOn;
        boolean[] states = {lowTemp, lowOutput, lowFuel, meltdownWarn,
                overheat, highOutput, fuelOut, scram};
        for (int i = 0; i < WARNINGS.length; i++) {
            int wx = x + 10 + (i % 4) * 67;
            int wy = y + 146 + (i / 4) * 18;
            int fill = states[i] ? 0xFF5A1E18 : BUTTON_FACE;
            int border = states[i] ? RED : BUTTON_BORDER;
            graphics.fill(wx, wy, wx + 64, wy + 15, fill);
            graphics.fill(wx, wy, wx + 64, wy + 1, border);
            graphics.fill(wx, wy + 14, wx + 64, wy + 15, border);
            graphics.fill(wx, wy, wx + 1, wy + 15, border);
            graphics.fill(wx + 63, wy, wx + 64, wy + 15, border);
            int color = states[i] ? 0xFFFFC8C0 : BUTTON_TEXT;
            graphics.drawString(font, WARNINGS[i], wx + (64 - font.width(WARNINGS[i])) / 2,
                    wy + 4, color, false);
        }

        // 右侧：自动控制拉杆 + 指示灯
        graphics.drawString(font, "自动控制", x + 310, y + 32, TEXT, false);
        int autoTrackX = x + 292;
        int autoTrackY = y + 28;
        graphics.fill(autoTrackX, autoTrackY, autoTrackX + 8, autoTrackY + 28, TRACK);
        graphics.fill(autoTrackX, autoTrackY, autoTrackX + 8, autoTrackY + 1, EDGE);
        int knobY = auto ? autoTrackY + 1 : autoTrackY + 16;
        graphics.fill(autoTrackX - 1, knobY, autoTrackX + 9, knobY + 11,
                auto ? 0xFFB8C0B4 : 0xFF7C8478);
        graphics.fill(autoTrackX + 1, knobY + 1, autoTrackX + 7, knobY + 3, 0xFFE8E2D0);
        drawLamp(graphics, x + 304, y + 38, auto && powerOn ? YELLOW : 0xFF39423A);

        // 右侧：能源按钮 + 独立状态灯
        graphics.drawString(font, "能源", x + 352, y + 16, TEXT, false);
        boolean powerHover = inRect(mouseX, mouseY, 346, 24, 42, 36);
        graphics.fill(x + 354, y + 26, x + 386, y + 58, PANEL);
        graphics.fill(x + 354, y + 26, x + 386, y + 27, EDGE);
        graphics.fill(x + 354, y + 57, x + 386, y + 58, EDGE);
        drawLamp(graphics, x + 348, y + 40, powerOn ? GREEN : 0xFF39423A);
        int red = powerHover ? 0xFFFF6B5E : 0xFFB5372F;
        drawCircle(graphics, x + 370, y + 42, 8, red);
        drawCircle(graphics, x + 370, y + 40, 3, powerHover ? 0xFFFFD0C8 : 0xFFFF8070);

        // 右侧：温度 / 负载 / 输出 / 曲线
        graphics.drawString(font, "温度", x + 292, y + 78, TEXT, false);
        drawButton(graphics, x + 292, y + 62, 14, "+");
        drawButton(graphics, x + 292, y + 140, 14, "-");
        int tempBarX = x + 294;
        int tempBarY = y + 90;
        int tempBarW = 10;
        int tempBarH = 46;
        graphics.fill(tempBarX, tempBarY, tempBarX + tempBarW, tempBarY + tempBarH, TRACK);
        int fillH = (int) Math.round(tempBarH * Mth.clamp(temperature / 100.0D, 0.0D, 1.0D));
        if (fillH > 0) {
            graphics.fill(tempBarX, tempBarY + tempBarH - fillH,
                    tempBarX + tempBarW, tempBarY + tempBarH, temperatureColor(temperature));
        }
        drawTempMarker(graphics, tempBarX, tempBarY, tempBarW, tempBarH, ReactorState.OPTIMAL_TEMP_MIN);
        drawTempMarker(graphics, tempBarX, tempBarY, tempBarW, tempBarH, ReactorState.OPTIMAL_TEMP_MAX);

        graphics.drawString(font, "负载: " + load + " 千瓦", x + 312, y + 64, BLUE, false);
        graphics.drawString(font, "输出: " + output + " 千瓦", x + 312, y + 142, YELLOW, false);
        drawGraph(graphics, x + 312, y + 84, 76, 50);
        if (melted) {
            graphics.drawString(font, "已熔毁", x + 292, y + 158, RED, false);
        }

        // 玩家物品栏槽位背景
        for (int i = ReactorMenu.FUEL_SLOTS; i < menu.slots.size(); i++) {
            Slot slot = menu.getSlot(i);
            slotBg(graphics, x + slot.x, y + slot.y);
        }
    }

    // ------------------------------------------------------------------ 交互

    private boolean inRect(double mouseX, double mouseY, int rx, int ry, int rw, int rh) {
        return mouseX >= leftPos + rx && mouseX < leftPos + rx + rw
                && mouseY >= topPos + ry && mouseY < topPos + ry + rh;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean powerOn = data(ReactorMenu.DATA_POWER_ON) != 0;
            boolean auto = data(ReactorMenu.DATA_AUTO) != 0;
            if (inRect(mouseX, mouseY, 288, 26, 56, 34)) {
                if (powerOn) {
                    send(ReactorActionPacket.TOGGLE_AUTO, 0);
                }
                return true;
            }
            if (inRect(mouseX, mouseY, 346, 24, 42, 36)) {
                send(ReactorActionPacket.TOGGLE_POWER, 0);
                return true;
            }
            if (inRect(mouseX, mouseY, 292, 62, 16, 16)) {
                send(ReactorActionPacket.TEMP_BOOST_UP, 0);
                return true;
            }
            if (inRect(mouseX, mouseY, 292, 140, 16, 16)) {
                send(ReactorActionPacket.TEMP_BOOST_DOWN, 0);
                return true;
            }
            if (powerOn && !auto) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingFission) {
            send(ReactorActionPacket.SET_FISSION, sliderValue(mouseX, SLIDER1_X));
            return true;
        }
        if (draggingTurbine) {
            send(ReactorActionPacket.SET_TURBINE, sliderValue(mouseX, SLIDER2_X));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingFission = false;
        draggingTurbine = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int sliderValue(double mouseX, int sliderX) {
        double t = Mth.clamp((mouseX - (leftPos + sliderX)) / SLIDER_W, 0.0D, 1.0D);
        return (int) Math.round(t * 100.0D);
    }

    private void send(int action, int value) {
        ModNetwork.CHANNEL.sendToServer(
                new ReactorActionPacket(menu.reactorPos(), action, value));
    }

    // ------------------------------------------------------------------ 绘图工具

    private void slotBg(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 19, y + 19, 0xFF0A0F0C);
        graphics.fill(x, y, x + 18, y + 18, 0xFF111812);
    }

    private static void drawLamp(GuiGraphics graphics, int cx, int cy, int color) {
        graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, color);
        graphics.fill(cx - 4, cy - 2, cx + 4, cy + 2, color);
        graphics.fill(cx - 2, cy - 4, cx + 2, cy + 4, color);
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
            graphics.fill(cx - dx, pivotY + dy, cx + dx + 1, pivotY + dy + 1, 0xFF0A0F0C);
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
            if (v > 0) {
                String text = Integer.toString(v);
                int tx = (int) Math.round(cx + Math.cos(angle) * (radius - 16));
                int ty = (int) Math.round(pivotY + Math.sin(angle) * (radius - 16));
                graphics.pose().pushPose();
                graphics.pose().translate(tx, ty, 0);
                graphics.pose().scale(0.75F, 0.75F, 1.0F);
                graphics.drawString(font, text, -font.width(text) / 2, -4, TEXT, false);
                graphics.pose().popPose();
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

    private void drawSlider(GuiGraphics graphics, int x, int y, int width, int value,
                            boolean enabled) {
        graphics.fill(x, y, x + width, y + 6, TRACK);
        graphics.fill(x, y, x + width, y + 1, EDGE);
        for (int tick = 25; tick < 100; tick += 25) {
            int tx = x + width * tick / 100;
            graphics.fill(tx, y, tx + 1, y + 6, EDGE);
        }
        int knob = x + Math.round(width * value / 100.0F);
        graphics.fill(knob - 3, y - 4, knob + 4, y + 10,
                enabled ? 0xFFB8C0B4 : 0xFF4A544B);
        graphics.fill(knob - 2, y - 3, knob + 3, y - 1,
                enabled ? 0xFFE8E2D0 : 0xFF5C665D);
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
        graphics.fill(x, y, x + size, y + size, BUTTON_FACE);
        graphics.fill(x, y, x + size, y + 1, BUTTON_BORDER);
        graphics.fill(x, y + size - 1, x + size, y + size, BUTTON_BORDER);
        graphics.fill(x, y, x + 1, y + size, BUTTON_BORDER);
        graphics.fill(x + size - 1, y, x + size, y + size, BUTTON_BORDER);
        graphics.drawString(font, label, x + (size - font.width(label)) / 2,
                y + (size - 8) / 2, BUTTON_TEXT, false);
    }

    private void drawGraph(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, TRACK);
        graphics.fill(x, y, x + width, y + 1, EDGE);
        graphics.fill(x, y + height - 1, x + width, y + height, EDGE);
        graphics.fill(x, y, x + 1, y + height, EDGE);
        graphics.fill(x + width - 1, y, x + width, y + height, EDGE);
        double max = 10000.0D;
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
