package com.opamb.barotrauma.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Shared materials for the mod's instrument and service panels. */
public final class PanelStyle {

    public static final int SHELL = 0xFF141F22;
    public static final int EDGE = 0xFF40585B;
    public static final int RECESS = 0xFF090F13;
    public static final int TEXT = 0xFFF0E7D1;
    public static final int DIM = 0xFF9FADAD;
    public static final int AMBER = 0xFFE9BC71;
    public static final int SERVICE = 0xFF86BDBA;
    public static final int MEDICAL = 0xFFD8AD71;
    public static final int SELECTED = 0xFF294044;

    private PanelStyle() {
    }

    public static void frame(GuiGraphics g, int x, int y, int width, int height, int accent) {
        int right = x + width;
        int bottom = y + height;
        g.fill(x + 2, y + 3, right + 2, bottom + 3, 0x99000000);
        g.fill(x, y, right, bottom, 0xFF080F12);
        g.fillGradient(x + 1, y + 1, right - 1, bottom - 1, 0xFF2C3E41, SHELL);
        g.fill(x + 4, y + 4, right - 4, bottom - 4, RECESS);
        g.fill(x + 4, y + 3, right - 4, y + 4, EDGE);
        g.fill(x + 4, bottom - 4, right - 4, bottom - 3, 0xFF28383C);
        g.fill(x + 1, y + 6, x + 2, y + 16, accent);
    }

    public static void well(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, EDGE);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, RECESS);
    }

    public static void divider(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFF283B41);
    }

    public static void meter(GuiGraphics g, int x, int y, int width, int height,
                             double fraction, int color, int segments) {
        well(g, x, y, width, height);
        g.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF111C21, 0xFF1B282E);
        int amount = (int) Math.round((width - 4) * Mth.clamp(fraction, 0.0D, 1.0D));
        if (amount > 0) {
            g.fill(x + 2, y + 2, x + 2 + amount, y + height - 2, color);
            g.fill(x + 2, y + 2, x + 2 + amount, y + 3, 0x55FFFFFF);
        }
        for (int i = 1; i < segments; i++) {
            int tick = x + 2 + (width - 4) * i / segments;
            g.fill(tick, y + 2, tick + 1, y + height - 2, 0x8805080A);
        }
    }

    public static void slider(GuiGraphics g, int x, int y, int width, double fraction,
                              boolean enabled, boolean selected) {
        well(g, x, y, width, 10);
        g.fill(x + 2, y + 4, x + width - 2, y + 6, 0xFF31444A);
        int thumb = x + 4 + (int) Math.round((width - 8) * Mth.clamp(fraction, 0.0D, 1.0D));
        g.fillGradient(thumb - 3, y + 1, thumb + 4, y + 9,
                enabled ? 0xFFBCC5C2 : 0xFF67777A, 0xFF36484D);
        g.fill(thumb, y + 2, thumb + 1, y + 8, selected ? AMBER : 0xFF19272B);
    }

    public static void lamp(GuiGraphics g, int cx, int cy, boolean active, int color) {
        g.fill(cx - 4, cy - 2, cx + 5, cy + 3, EDGE);
        g.fill(cx - 2, cy - 4, cx + 3, cy + 5, EDGE);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, active ? color : 0xFF223238);
        if (active) g.fill(cx - 1, cy - 2, cx + 2, cy - 1, 0x66FFFFFF);
    }

    public static void slot(GuiGraphics g, int x, int y) {
        well(g, x, y, 18, 18);
        g.fillGradient(x + 1, y + 1, x + 17, y + 17, 0xFF182227, RECESS);
    }

    public static void control(GuiGraphics g, int x, int y, int width, int height,
                               boolean enabled, boolean highlighted) {
        g.fill(x, y, x + width, y + height, enabled ? EDGE : 0xFF29383B);
        g.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1,
                highlighted ? 0xFF3E5050 : enabled ? 0xFF2A3C41 : 0xFF1A252B,
                highlighted ? 0xFF263538 : enabled ? 0xFF17262B : 0xFF12191E);
        if (highlighted) g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, AMBER);
    }

    public static ButtonBuilder button(Component label, Button.OnPress action) {
        return new ButtonBuilder(label, action);
    }

    public record ButtonBuilder(Component label, Button.OnPress action) {

        public Button bounds(int x, int y, int width, int height) {
            return new StyledButton(label, action, x, y, width, height);
        }
    }

    private static final class StyledButton extends Button {

        private StyledButton(Component label, OnPress action, int x, int y, int width, int height) {
            super(x, y, width, height, label, action, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            control(g, getX(), getY(), getWidth(), getHeight(), active, active && isHoveredOrFocused());
            renderString(g, Minecraft.getInstance().font,
                    ((active ? TEXT : DIM) & 0xFFFFFF) | (Mth.ceil(alpha * 255.0F) << 24));
        }
    }
}
