package com.opamb.barotrauma.client.gui;

import com.opamb.barotrauma.menu.DeviceMenu;
import com.opamb.barotrauma.menu.RepairSession;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.RepairActionPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;

/** The maintenance frame is laid out below the operational panel. */
final class DeviceRepairPanel {

    static final int HEIGHT = 66;
    static final int GAP = 8;

    private final DeviceMenu menu;
    private double condition = 1.0D;
    private double pointer = 1.0D;
    private long lastFrame;

    DeviceRepairPanel(DeviceMenu menu) {
        this.menu = menu;
    }

    void render(GuiGraphics g, Font font, int width, int y, int mouseX, int mouseY, boolean standalone) {
        if (!visible(standalone)) return;
        int x = Math.max(0, (width - 340) / 2);
        int right = width - x;
        int inner = x + 9;
        int buttonStart = right - 97;
        ContainerData data = menu.repair().data();
        boolean wrench = data.get(RepairSession.TOOL) != 0;
        boolean active = data.get(RepairSession.ACTIVE) != 0;
        boolean enabled = enabled();
        int result = data.get(RepairSession.RESULT);
        updateAnimation(data, active);

        PanelStyle.frame(g, x, y, right - x, HEIGHT, PanelStyle.AMBER);
        g.drawString(font, text(wrench ? "mechanical" : "electrical"), inner, y + 9,
                PanelStyle.TEXT, false);
        PanelStyle.divider(g, inner, y + 20, right - inner - 9);
        Component tool = text(wrench ? "need_wrench" : "need_screwdriver");
        Component skill = Component.translatable("gui.barotrauma.repair.skill",
                data.get(RepairSession.SKILL), RepairSession.FIXED_SKILL);
        g.drawString(font, font.plainSubstrByWidth(tool.getString(), buttonStart - inner - 3), inner, y + 24,
                data.get(RepairSession.HAS_TOOL) != 0 ? PanelStyle.DIM : 0xFFDD7766, false);
        g.drawString(font, font.plainSubstrByWidth(skill.getString(), buttonStart - inner - 3), inner, y + 35,
                data.get(RepairSession.SKILL) <= RepairSession.FIXED_SKILL ? PanelStyle.DIM : PanelStyle.AMBER, false);
        if (!active && result < 0 && data.get(RepairSession.COOLDOWN) > 0) {
            g.drawCenteredString(font, text("failed"), buttonStart + 44, y + 32, 0xFFE69378);
        }
        int barWidth = buttonStart - inner - 7;
        int color = result > 0 ? 0xFFBCE391 : result < 0 ? 0xFFAA5645 : 0xFF55917B;
        PanelStyle.meter(g, inner, y + 46, barWidth, 15, condition, color, 0);
        if (active) {
            int marker = inner + 2 + Mth.floor((barWidth - 4) * pointer);
            g.fill(marker, y + 46, marker + 1, y + 61, 0xFFF2EAD5);
        }
        g.drawCenteredString(font, Math.round(data.get(RepairSession.CONDITION) / 10.0F) + "%",
                inner + barWidth / 2, y + 49, PanelStyle.TEXT);
        PanelStyle.control(g, buttonStart, y + 44, 88, 16, enabled,
                enabled && mouseX >= buttonStart && mouseX < right - 9 && mouseY >= y + 44 && mouseY < y + 60);
        String label = font.plainSubstrByWidth(text(active ? "repairing" : "button").getString(), 82);
        g.drawCenteredString(font, label, buttonStart + 44, y + 48, enabled ? PanelStyle.TEXT : PanelStyle.DIM);
    }

    boolean mouseClicked(int width, int y, double x, double mouseY, int button, boolean standalone) {
        if (!visible(standalone) || mouseY < y || mouseY >= y + HEIGHT
                || x < Math.max(0, (width - 340) / 2) || x >= width - Math.max(0, (width - 340) / 2)) {
            return false;
        }
        int right = width - Math.max(0, (width - 340) / 2);
        if (button == 0 && enabled() && x >= right - 97 && x < right - 9
                && mouseY >= y + 44 && mouseY < y + 60) {
            byte action = menu.repair().data().get(RepairSession.ACTIVE) != 0
                    ? RepairActionPacket.ACTION_QTE : RepairActionPacket.ACTION_START;
            ModNetwork.sendToServer(new RepairActionPacket(menu.containerId, action));
        }
        return true;
    }

    private boolean visible(boolean standalone) {
        ContainerData data = menu.repair().data();
        return standalone || data.get(RepairSession.HAS_TOOL) != 0
                && (data.get(RepairSession.ELIGIBLE) != 0 || data.get(RepairSession.ACTIVE) != 0);
    }

    private boolean enabled() {
        ContainerData data = menu.repair().data();
        return data.get(RepairSession.HAS_TOOL) != 0 && (data.get(RepairSession.ACTIVE) != 0
                || data.get(RepairSession.ELIGIBLE) != 0 && data.get(RepairSession.COOLDOWN) == 0);
    }

    private void updateAnimation(ContainerData data, boolean active) {
        long now = System.nanoTime();
        double amount = Math.min(1.0D, (now - lastFrame) / 50_000_000.0D);
        condition = Mth.lerp(amount, condition, data.get(RepairSession.CONDITION) / 1000.0D);
        double target = data.get(RepairSession.POINTER) / 1000.0D;
        pointer = !active || target > pointer ? target : Mth.lerp(amount, pointer, target);
        lastFrame = now;
    }

    private static Component text(String key) {
        return Component.translatable("gui.barotrauma.repair." + key);
    }
}
