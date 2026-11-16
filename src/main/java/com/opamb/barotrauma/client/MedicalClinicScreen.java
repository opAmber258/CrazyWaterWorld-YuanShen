package com.opamb.barotrauma.client;

import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.network.ClinicActionPacket;
import com.opamb.barotrauma.network.ClinicStatePacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 哨站医生界面（独立于商店/任务终端）。
 *
 * <p>列出玩家当前病症和原作公式算出的治疗价；左键一行治疗该病症，
 * 底部按钮一次治疗全部。收费与治疗都在服务端校验/执行。</p>
 */
public final class MedicalClinicScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 218;
    private static final int ROW_H = 18;
    private static final int VISIBLE_ROWS = 9;

    private static final int BG = 0xF01B2320;
    private static final int EDGE = 0xFF3B463C;
    private static final int BOX = 0xFF232B25;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF8B9184;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF7BE38B;
    private static final int RED = 0xFFFF7A6A;
    private static final int HOVER = 0xFF39483A;

    private ClinicStatePacket state;
    private int scroll;

    private MedicalClinicScreen(ClinicStatePacket state) {
        super(Component.translatable("clinic.title"));
        this.state = state;
    }

    public static void receive(ClinicStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MedicalClinicScreen screen) {
            screen.state = packet;
            screen.scroll = Math.max(0, Math.min(screen.scroll,
                    Math.max(0, packet.entries().size() - VISIBLE_ROWS)));
            return;
        }
        // 治疗后的刷新包：玩家已经按 Esc 或换了界面，不能把诊所重新弹出来。
        if (!packet.open()) {
            return;
        }
        minecraft.setScreen(new MedicalClinicScreen(packet));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int leftPos() {
        return (width - PANEL_W) / 2;
    }

    private int topPos() {
        return (height - PANEL_H) / 2;
    }

    private int listTop() {
        return topPos() + 34;
    }

    private int totalPrice() {
        int total = 0;
        for (ClinicStatePacket.Entry entry : state.entries()) {
            total += entry.price();
        }
        return total;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = leftPos();
        int y = topPos();
        g.fill(x, y, x + PANEL_W, y + PANEL_H, BG);
        g.fill(x, y, x + PANEL_W, y + 1, EDGE);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, EDGE);
        g.fill(x, y, x + 1, y + PANEL_H, EDGE);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, EDGE);

        g.drawString(font, Component.translatable("clinic.title"), x + 10, y + 6, ACCENT, false);
        String wallet = Component.translatable("clinic.wallet", state.wallet()).getString();
        g.drawString(font, wallet, x + PANEL_W - 10 - font.width(wallet), y + 6, GREEN, false);
        g.drawString(font, Component.translatable("clinic.price_hint"),
                x + 10, y + 20, DIM, false);
        g.fill(x + 8, listTop() - 3, x + PANEL_W - 8, listTop() - 2, EDGE);

        List<ClinicStatePacket.Entry> entries = state.entries();
        int shown = Math.min(VISIBLE_ROWS, Math.max(0, entries.size() - scroll));
        for (int i = 0; i < shown; i++) {
            ClinicStatePacket.Entry entry = entries.get(scroll + i);
            int rowY = listTop() + i * ROW_H;
            boolean hover = mouseX >= x + 8 && mouseX < x + PANEL_W - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            g.fill(x + 8, rowY, x + PANEL_W - 8, rowY + ROW_H, hover ? HOVER : BOX);
            g.drawString(font, label(entry), x + 14, rowY + 5, TEXT, false);
            String price = entry.price() + " mk";
            g.drawString(font, price, x + PANEL_W - 16 - font.width(price), rowY + 5,
                    state.wallet() >= entry.price() ? GREEN : RED, false);
        }
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("clinic.empty"), x + 14, listTop() + 6,
                    GREEN, false);
        }

        int buttonY = y + PANEL_H - 42;
        int total = totalPrice();
        boolean canAll = !entries.isEmpty() && state.wallet() >= total;
        boolean hover = !entries.isEmpty() && mouseX >= x + 10 && mouseX < x + 190
                && mouseY >= buttonY && mouseY < buttonY + 18;
        g.fill(x + 10, buttonY, x + 190, buttonY + 18,
                entries.isEmpty() ? BOX : hover ? HOVER : 0xFF3A4A3C);
        g.fill(x + 10, buttonY, x + 190, buttonY + 1, EDGE);
        g.drawCenteredString(font, entries.isEmpty()
                        ? Component.translatable("clinic.heal_all")
                        : Component.translatable("clinic.heal_all_price", total),
                x + 100, buttonY + 5, canAll ? TEXT : RED);

        g.drawString(font, Component.translatable("clinic.hint"),
                x + 10, y + PANEL_H - 14, DIM, false);
    }

    private static String label(ClinicStatePacket.Entry entry) {
        AfflictionType type = AfflictionType.byId(entry.afflictionId());
        String name;
        if (type == null) {
            name = entry.afflictionId();
        } else {
            name = TreatmentRenderer.label(type, entry.strength());
        }
        Limb limb = parseLimb(entry.limb());
        if (limb != null) {
            name = TreatmentRenderer.limbName(limb) + " · " + name;
        }
        return name;
    }

    private static Limb parseLimb(String limb) {
        if (limb == null || limb.isBlank() || "-".equals(limb)) {
            return null;
        }
        try {
            return Limb.valueOf(limb);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = leftPos();
        int y = topPos();
        int buttonY = y + PANEL_H - 42;
        if (!state.entries().isEmpty() && mouseX >= x + 10 && mouseX < x + 190
                && mouseY >= buttonY && mouseY < buttonY + 18) {
            ModNetwork.sendToServer(new ClinicActionPacket(ClinicActionPacket.HEAL_ALL, ""));
            return true;
        }
        int top = listTop();
        if (mouseY >= top && mouseY < top + VISIBLE_ROWS * ROW_H
                && mouseX >= x + 8 && mouseX < x + PANEL_W - 8) {
            int index = scroll + (int) ((mouseY - top) / ROW_H);
            List<ClinicStatePacket.Entry> entries = state.entries();
            if (index >= 0 && index < entries.size()) {
                ClinicStatePacket.Entry entry = entries.get(index);
                ModNetwork.sendToServer(new ClinicActionPacket(entry.afflictionId(), entry.limb()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            int max = Math.max(0, state.entries().size() - VISIBLE_ROWS);
            scroll = Math.max(0, Math.min(max, scroll + (delta > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
