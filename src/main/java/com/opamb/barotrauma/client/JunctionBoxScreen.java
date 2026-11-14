package com.opamb.barotrauma.client;

import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.PowerBindPacket;
import com.opamb.barotrauma.network.PowerStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * 接线盒电力面板：显示全船供电/负载/电压、本盒负载/耐久，以及附近设备列表。
 *
 * <p>“绑定”只改设备的 PowerBox 字段，不铺线；一个设备只绑一个盒子。
 * 服务端会再次校验距离和设备类型。</p>
 */
public class JunctionBoxScreen extends Screen {

    private static final int W = 440;
    private static final int H = 340;
    private static final int ROW = 22;
    private static final int MAX_ROWS = 8;

    private PowerStatePacket state;
    /** 设备列表滚动偏移（第 scroll 项显示在列表第一行）。 */
    private int scroll;

    public JunctionBoxScreen(PowerStatePacket state) {
        super(Component.translatable("block.barotrauma.junction_box"));
        this.state = state;
    }

    public static void updateOrOpen(PowerStatePacket state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof JunctionBoxScreen screen
                && screen.state.boxPos().equals(state.boxPos())) {
            screen.update(state);
            return;
        }
        mc.setScreen(new JunctionBoxScreen(state));
    }

    private void update(PowerStatePacket next) {
        this.state = next;
        this.scroll = Mth.clamp(scroll, 0, maxScroll());
        rebuildWidgets();
    }

    private int maxScroll() {
        return Math.max(0, state.devices().size() - MAX_ROWS);
    }

    @Override
    protected void init() {
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        List<PowerStatePacket.Device> devices = state.devices();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        int rows = Math.min(MAX_ROWS, Math.max(0, devices.size() - scroll));
        for (int i = 0; i < rows; i++) {
            PowerStatePacket.Device device = devices.get(scroll + i);
            boolean boundHere = state.boxPos().equals(device.box());
            String label = boundHere ? "解绑" : "绑定";
            int y = top + 118 + i * ROW;
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                ModNetwork.sendToServer(new PowerBindPacket(state.boxPos(), device.pos(), !boundHere));
                b.active = false;
            }).bounds(left + W - 92, y, 72, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
                .bounds(left + W / 2 - 40, top + H - 30, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int left = (width - W) / 2;
        int top = (height - H) / 2;
        g.fill(left, top, left + W, top + H, 0xE0101418);
        g.fill(left, top, left + W, top + 2, 0xFF5A6672);
        g.drawCenteredString(font, Component.translatable("block.barotrauma.junction_box"),
                left + W / 2, top + 10, 0xFFFFFFFF);

        int x = left + 18;
        int y = top + 34;
        List<PowerStatePacket.Device> devices = state.devices();
        g.drawString(font, line("power.supply", state.supplyKw()), x, y, 0xFFE8EDF2);
        g.drawString(font, line("power.load", state.demandKw()), x, y + 14, 0xFFE8EDF2);
        int voltageColor = state.voltage() > 1.7D ? 0xFFFF5555
                : state.voltage() < 0.8D ? 0xFFFFC857 : 0xFF77DD77;
        g.drawString(font, line("power.voltage", state.voltage() * 100.0D), x, y + 28, voltageColor);
        g.drawString(font, line("power.box_load", state.boxLoadKw()), x + 220, y, 0xFFE8EDF2);
        int condColor = state.condition() <= 0.0D ? 0xFFFF5555
                : state.condition() < 50.0D ? 0xFFFFC857 : 0xFF77DD77;
        g.drawString(font, line("power.condition", state.condition()), x + 220, y + 14, condColor);
        g.drawString(font, line("power.range", devices.size()), x + 220, y + 28, 0xFF9AA6B2);

        g.fill(left + 16, top + 96, left + W - 16, top + 98, 0xFF3A444E);
        g.drawString(font, Component.translatable("power.devices"), left + 18, top + 102, 0xFFB8C4D0);

        if (devices.isEmpty()) {
            g.drawString(font, Component.translatable("power.no_devices"), left + 18, top + 122, 0xFF8A96A2);
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        int rows = Math.min(MAX_ROWS, Math.max(0, devices.size() - scroll));
        for (int i = 0; i < rows; i++) {
            PowerStatePacket.Device device = devices.get(scroll + i);
            int rowY = top + 118 + i * ROW;
            boolean boundHere = state.boxPos().equals(device.box());
            String name = Component.translatable(device.nameKey()).getString();
            String text = name + "  " + fmt(device.demandKw()) + " kW  ≥" + fmt(device.minVoltage() * 100.0D) + "%";
            int color = boundHere ? 0xFF77DD77 : 0xFFE8EDF2;
            g.drawString(font, text, left + 18, rowY, color);
            if (device.box() != null && !boundHere) {
                g.drawString(font, "→ 其他盒子", left + 18, rowY + 10, 0xFF8A96A2);
            }
        }
        if (maxScroll() > 0) {
            String hint = "第 " + (scroll + 1) + "–" + (scroll + rows) + " / "
                    + devices.size() + " 项 · 滚轮翻页";
            g.drawString(font, hint, left + 18, top + 118 + MAX_ROWS * ROW, 0xFF8A96A2);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0.0D) {
            int next = Mth.clamp(scroll + (delta > 0.0D ? -1 : 1), 0, maxScroll());
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static Component line(String key, double value) {
        return Component.translatable(key).append(Component.literal(": " + fmt(value)));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
