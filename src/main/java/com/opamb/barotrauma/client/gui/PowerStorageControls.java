package com.opamb.barotrauma.client.gui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The charge meter and recharge control used by both electrical storage devices. */
public final class PowerStorageControls {

    private PowerStorageControls() {
    }

    public static void charge(GuiGraphics g, Font font, int x, int y, int width,
                              int charge, int capacity, int segments) {
        double fraction = Mth.clamp(charge / (double) Math.max(1, capacity), 0.0D, 1.0D);
        String value = String.format(java.util.Locale.ROOT, "%.1f / %.1f kW·min", charge / 10.0D, capacity / 10.0D);
        g.drawString(font, Component.translatable("gui.barotrauma.storage.charge"), x, y, PanelStyle.TEXT, false);
        g.drawString(font, value, x + width - font.width(value), y, PanelStyle.DIM, false);
        int color = fraction < 0.2D ? 0xFFBD5C4E : fraction < 0.5D ? PanelStyle.AMBER : 0xFF599E86;
        PanelStyle.meter(g, x, y + 13, width, 16, fraction, color, segments);
        g.drawCenteredString(font, Math.round(fraction * 100.0D) + "%", x + width / 2, y + 17, PanelStyle.TEXT);
    }

    public static final class RechargeSlider extends AbstractSliderButton {
        private final IntSupplier maximum;
        private final IntConsumer setPercent;
        private boolean dragging;
        private int lastSent = -1;

        public RechargeSlider(int x, int y, int width, IntSupplier maximum, IntConsumer setPercent) {
            super(x, y, width, 12, Component.empty(), 0.0D);
            this.maximum = maximum;
            this.setPercent = setPercent;
            updateMessage();
        }

        public void sync(int rate) {
            if (dragging) return;
            value = Mth.clamp(rate / (double) Math.max(1, maximum.getAsInt()), 0.0D, 1.0D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.barotrauma.storage.recharge_set",
                    Math.round(value * maximum.getAsInt()), Math.round(value * 100.0D)));
        }

        @Override
        protected void applyValue() {
            int percent = (int) Math.round(value * 100.0D);
            if (percent != lastSent) {
                lastSent = percent;
                setPercent.accept(percent);
            }
        }

        @Override
        public void onClick(double x, double y) {
            dragging = true;
            lastSent = -1;
            super.onClick(x, y);
        }

        @Override
        public void onRelease(double x, double y) {
            dragging = false;
            super.onRelease(x, y);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            PanelStyle.slider(g, getX(), getY() + 1, getWidth(), value, active, isHoveredOrFocused());
        }
    }
}
