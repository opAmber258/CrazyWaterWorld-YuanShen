package com.opamb.barotrauma.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Shared materials for the mod's instrument and service panels. */
public final class PanelStyle {

    public static final int SHELL = 0xFF232A27;
    public static final int EDGE = 0xFF656C60;
    public static final int RECESS = 0xFF171E1A;
    public static final int TEXT = 0xFFE7E4D2;
    public static final int DIM = 0xFFADB4A5;
    public static final int AMBER = 0xFFDD8E47;
    public static final int SERVICE = 0xFF83ACA1;
    public static final int MEDICAL = 0xFFA9B99B;

    private PanelStyle() {
    }

    public static void frame(GuiGraphics g, int x, int y, int width, int height, int accent) {
        int right = x + width;
        int bottom = y + height;
        g.fill(x + 2, y + 2, right + 2, bottom + 2, 0x8C050706);
        g.fill(x, y, right, bottom, 0xFF101410);
        g.fill(x + 1, y + 1, right - 1, bottom - 1, EDGE);
        g.fill(x + 2, y + 2, right - 2, bottom - 2, SHELL);
        g.fillGradient(x + 3, y + 3, right - 3, y + 24, 0xFF343D35, SHELL);
        g.fill(x + 3, y + 3, x + 28, y + 4, accent);
        g.fill(x + 3, bottom - 4, right - 3, bottom - 3, RECESS);
    }

    public static void control(GuiGraphics g, int x, int y, int width, int height,
                               boolean enabled, boolean highlighted) {
        g.fill(x, y, x + width, y + height, 0xFF0C110E);
        g.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1,
                highlighted ? 0xFF535B45 : enabled ? 0xFF414A41 : 0xFF2A312B,
                highlighted ? 0xFF303D2E : enabled ? 0xFF29332C : 0xFF202621);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, highlighted ? AMBER : EDGE);
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
