package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import com.opamb.barotrauma.menu.JunctionBoxMenu;
import net.minecraft.world.entity.player.Inventory;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.PowerBindPacket;
import com.opamb.barotrauma.network.PowerStatePacket;
import net.minecraft.client.Minecraft;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
public class JunctionBoxScreen extends ScaledContainerScreen<JunctionBoxMenu> {

    private static final int W = 440;
    private static final int H = 340;
    private static final int ROW = 22;
    private static final int MAX_ROWS = 7;

    private PowerStatePacket state;
    /** 设备列表滚动偏移（第 scroll 项显示在列表第一行）。 */
    private int scroll;
    /** 绑定按钮点击后的临时禁用截止时间；服务端没回包（例如盒子被拆）时自动恢复。 */
    private long bindReEnableAt;
    /** 等绑定回包的最长时间（毫秒）。 */
    private static final long BIND_TIMEOUT_MS = 1500L;

    public JunctionBoxScreen(JunctionBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        state = new PowerStatePacket(menu.boxCell(), 0, 0, 0, 100, 0, false, 0, 0, 0, List.of());
    }


    /** 面板按设计坐标绘制，缩放/居中由 {@link ScaledContainerScreen} 统一处理。 */
    private int panelWidth() {
        return W;
    }

    private int panelHeight() {
        return H;
    }

    /** 可见行数由设计高度决定（列表从 y=140 开始，底部留给关闭按钮）。 */
    private int visibleRows() {
        return Math.max(1, Math.min(MAX_ROWS, (H - 150) / ROW));
    }

    public static void updateOrOpen(PowerStatePacket state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof JunctionBoxScreen screen
                && screen.state.boxPos().equals(state.boxPos())) {
            screen.update(state);
            return;
        }
    }

    private void update(PowerStatePacket next) {
        boolean wiringChanged = !sameWiring(state.devices(), next.devices());
        this.state = next;
        this.scroll = Mth.clamp(scroll, 0, maxScroll());
        if (wiringChanged || bindReEnableAt != 0L) {
            this.bindReEnableAt = 0L;
            rebuildWidgets();
        }
    }

    private static boolean sameWiring(List<PowerStatePacket.Device> before, List<PowerStatePacket.Device> after) {
        if (before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            PowerStatePacket.Device a = before.get(i);
            PowerStatePacket.Device b = after.get(i);
            if (!a.pos().equals(b.pos()) || !java.util.Objects.equals(a.box(), b.box())) return false;
        }
        return true;
    }

    private int maxScroll() {
        return Math.max(0, state.devices().size() - visibleRows());
    }

    @Override
    protected void init() {
        super.init();
        int left = 0;
        int top = 0;
        List<PowerStatePacket.Device> devices = state.devices();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        int rows = Math.min(visibleRows(), Math.max(0, devices.size() - scroll));
        for (int i = 0; i < rows; i++) {
            PowerStatePacket.Device device = devices.get(scroll + i);
            boolean boundHere = state.boxPos().equals(device.box());
            Component label = Component.translatable(boundHere ? "power.unbind" : "power.bind");
            int y = top + 140 + i * ROW;
            addRenderableWidget(PanelStyle.button(label, b -> {
                ModNetwork.sendToServer(new PowerBindPacket(state.boxPos(), device.pos(), !boundHere));
                b.active = false;
                bindReEnableAt = System.currentTimeMillis() + BIND_TIMEOUT_MS;
            }).bounds(left + panelWidth() - 92, y, 72, 20));
        }
        addRenderableWidget(PanelStyle.button(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + panelWidth() / 2 - 40, top + panelHeight() - 30, 80, 20));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (bindReEnableAt != 0L && System.currentTimeMillis() >= bindReEnableAt) {
            // 服务端没回包（比如接线盒在这期间被拆了）：按钮不能永远禁用。
            bindReEnableAt = 0L;
            rebuildWidgets();
        }
        int left = 0;
        int top = 0;
        PanelStyle.frame(g, left, top, W, H, PanelStyle.AMBER);
        g.drawCenteredString(font, Component.translatable("block.barotrauma.junction_box"),
                left + panelWidth() / 2, top + 10, 0xFFFFFFFF);

        int x = left + 18;
        int y = top + 34;
        List<PowerStatePacket.Device> devices = state.devices();
        double voltage = state.voltage();
        boolean pulse = net.minecraft.Util.getMillis() % 500L < 250L;
        indicator(g, x, y, "power.indicator.powered", voltage > 0.0D, 0xFF79C172);
        indicator(g, x, y + 16, "power.indicator.high", pulse && voltage > 1.2D, 0xFFD66651);
        indicator(g, x, y + 32, "power.indicator.low", pulse && voltage > 0.0D && voltage < 0.8D, 0xFFD66651);
        display(g, x + 188, y - 2, "power.supply", state.supplyKw());
        display(g, x + 188, y + 18, "power.load", state.demandKw());
        int voltageColor = state.voltage() > 1.7D ? 0xFFFF5555
                : state.voltage() < 0.8D ? 0xFFFFC857 : 0xFF77DD77;
        g.drawString(font, line("power.voltage", voltage * 100.0D), x + 188, y + 42, voltageColor);
        g.drawString(font, line("power.box_load", state.boxLoadKw()), x + 188, y + 56, PanelStyle.TEXT);
        g.drawString(font, line("power.range", devices.size()), x + 188, y + 70, PanelStyle.DIM);
        Component batteryText = state.storageCapacityKwm() <= 1.0E-6D
                ? Component.translatable("power.no_battery")
                : Component.translatable("power.battery_line",
                        Math.round(state.storedKwm() / state.storageCapacityKwm() * 100.0D),
                        fmt(state.storedKwm()), fmt(state.storageCapacityKwm()),
                        fmt(state.batteryOutputKw()));
        int batteryColor = state.batteryOutputKw() > 0.5D ? 0xFFFFC857 : 0xFF77DD77;
        g.drawString(font, font.plainSubstrByWidth(batteryText.getString(), W - 36), x, y + 86, batteryColor, false);

        // 耐久只在独立修理窗口里画；这里给一行状态文字。
        double displayCondition = menu.repair().data().get(com.opamb.barotrauma.menu.RepairSession.CONDITION) / 10.0D;
        Component conditionText = displayCondition <= 0.0D
                ? Component.translatable("power.broken_hint")
                : Component.translatable("power.condition_line", Math.round(displayCondition));
        g.drawString(font, conditionText, x, y + 50, displayCondition <= 0.0D
                ? 0xFFFF5555 : 0xFF9AA6B2);

        g.fill(left + 16, top + 120, left + W - 16, top + 122, 0xFF3A444E);
        g.drawString(font, Component.translatable("power.devices"), left + 18, top + 126, 0xFFB8C4D0);

        if (devices.isEmpty()) {
            g.drawString(font, Component.translatable("power.no_devices"), left + 18, top + 146, 0xFF8A96A2);
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        int rows = Math.min(visibleRows(), Math.max(0, devices.size() - scroll));
        for (int i = 0; i < rows; i++) {
            PowerStatePacket.Device device = devices.get(scroll + i);
            int rowY = top + 140 + i * ROW;
            boolean boundHere = state.boxPos().equals(device.box());
            String name = Component.translatable(device.nameKey()).getString();
            String pos = Component.translatable("power.device_pos",
                    device.pos().getX(), device.pos().getY(), device.pos().getZ()).getString();
            String text = device.kind() == PowerStatePacket.KIND_BATTERY
                    ? name + "  " + fmt(device.chargeFraction() * 100.0D) + "%  "
                            + fmt(device.storedKwm()) + "/" + fmt(device.capacityKwm()) + " kW·min"
                    : name + "  " + fmt(device.demandKw()) + " kW  ≥"
                            + fmt(device.minVoltage() * 100.0D) + "%";
            int color = boundHere ? 0xFF77DD77 : 0xFFE8EDF2;
            g.drawString(font, text, left + 18, rowY, color);
            // 坐标单独一行：窄窗口/同型号设备也能区分，不会和绑定按钮抢宽度。
            String sub = device.box() != null && !boundHere
                    ? pos + "  " + Component.translatable("power.other_box").getString()
                    : pos;
            g.drawString(font, sub, left + 18, rowY + 10, 0xFF8A96A2);
        }
        if (maxScroll() > 0) {
            Component hint = Component.translatable("power.scroll_hint",
                    scroll + 1, scroll + rows, devices.size());
            g.drawString(font, hint, left + 18, top + 140 + MAX_ROWS * ROW, 0xFF8A96A2);
        }
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (delta != 0.0D) {
            int next = Mth.clamp(scroll + (delta > 0.0D ? -1 : 1), 0, maxScroll());
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(screenX, screenY, delta);
    }

    private void indicator(GuiGraphics g, int x, int y, String label, boolean active, int color) {
        g.fill(x, y, x + 10, y + 10, 0xFF101711);
        g.fill(x + 2, y + 2, x + 8, y + 8, active ? color : 0xFF41473B);
        g.drawString(font, Component.translatable(label), x + 15, y + 1, PanelStyle.TEXT, false);
    }

    private void display(GuiGraphics g, int x, int y, String label, double kw) {
        g.fill(x, y, x + 212, y + 17, 0xFF101A14);
        g.drawString(font, Component.translatable(label), x + 5, y + 4, PanelStyle.DIM, false);
        String reading = fmt(kw) + " kW";
        g.drawString(font, reading, x + 206 - font.width(reading), y + 4, 0xFFAADB89, false);
    }

    private static Component line(String key, double value) {
        return Component.translatable(key).append(Component.literal(": " + fmt(value)));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
