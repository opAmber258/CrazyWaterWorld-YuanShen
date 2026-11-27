package com.opamb.barotrauma.client;

import com.opamb.barotrauma.client.gui.ClinicFocus;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.network.ClinicActionPacket;
import com.opamb.barotrauma.network.ClinicStatePacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.Minecraft;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 哨站医生界面（独立于商店/任务终端）。
 *
 * <p>列出玩家当前病症和原作公式算出的治疗价；左键一行治疗该病症，
 * 底部按钮一次治疗全部。收费与治疗都在服务端校验/执行。</p>
 */
public final class MedicalClinicScreen extends ScaledScreen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 252;
    private static final int ROW_H = 23;
    private static final int VISIBLE_ROWS = 7;

    private static final int EDGE = PanelStyle.EDGE;
    private static final int BOX = PanelStyle.RECESS;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.MEDICAL;
    private static final int GREEN = 0xFF7BE38B;
    private static final int RED = 0xFFFF7A6A;
    private static final int HOVER = PanelStyle.SELECTED;

    private ClinicStatePacket state;
    private int scroll;
    /** 键盘焦点：0 病症行 / 1 全部治疗按钮。 */
    private int focusArea;
    private int focusRow = -1;

    private MedicalClinicScreen(ClinicStatePacket state) {
        super(Component.translatable("clinic.title"), PANEL_W, PANEL_H);
        this.state = state;
    }

    public static void receive(ClinicStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MedicalClinicScreen screen) {
            screen.updateState(packet);
            return;
        }
        // 治疗后的刷新包：玩家已经按 Esc 或换了界面，不能把诊所重新弹出来。
        if (!packet.open()) {
            return;
        }
        minecraft.setScreen(new MedicalClinicScreen(packet));
    }

    /**
     * 治疗后的列表刷新：焦点按 {@code (afflictionId, limb)} 身份重新定位。
     *
     * <p>直接保留旧行号的话，排在前面、刚被治好的病症消失后，Enter 会落到
     * 下一行别的病症上并误扣费；焦点条目自己没了就清空焦点。</p>
     */
    private void updateState(ClinicStatePacket packet) {
        focusRow = ClinicFocus.remap(state.entries(), focusRow, packet.entries());
        state = packet;
        scroll = Math.max(0, Math.min(scroll,
                Math.max(0, packet.entries().size() - VISIBLE_ROWS)));
        if (focusRow >= 0) {
            // 焦点还在时跟着它滚，别让高亮/Enter 目标跑出可视区。
            if (focusRow < scroll) {
                scroll = focusRow;
            } else if (focusRow >= scroll + VISIBLE_ROWS) {
                scroll = focusRow - VISIBLE_ROWS + 1;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 设计坐标原点：缩放由 ScaledScreen 处理，这里恒为 0。 */
    private int leftPos() {
        return 0;
    }

    private int topPos() {
        return 0;
    }

    private int listTop() {
        return topPos() + 42;
    }

    private int totalPrice() {
        int total = 0;
        for (ClinicStatePacket.Entry entry : state.entries()) {
            total += entry.price();
        }
        return total;
    }

    @Override
    protected void renderDesign(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos();
        int y = topPos();
        PanelStyle.frame(g, x, y, PANEL_W, PANEL_H, PanelStyle.MEDICAL);

        g.drawString(font, Component.translatable("clinic.title"), x + 10, y + 9, TEXT, false);
        String wallet = Component.translatable("clinic.wallet", state.wallet()).getString();
        g.drawString(font, wallet, x + PANEL_W - 10 - font.width(wallet), y + 9, GREEN, false);
        g.drawString(font, Component.translatable("clinic.price_hint"),
                x + 10, y + 27, DIM, false);
        PanelStyle.divider(g, x + 10, y + 22, PANEL_W - 20);
        PanelStyle.well(g, x + 8, listTop() - 1, PANEL_W - 16, VISIBLE_ROWS * ROW_H + 2);

        List<ClinicStatePacket.Entry> entries = state.entries();
        int shown = Math.min(VISIBLE_ROWS, Math.max(0, entries.size() - scroll));
        for (int i = 0; i < shown; i++) {
            ClinicStatePacket.Entry entry = entries.get(scroll + i);
            int rowY = listTop() + i * ROW_H;
            boolean hover = mouseX >= x + 8 && mouseX < x + PANEL_W - 8
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean focused = focusArea == 0 && focusRow == scroll + i;
            g.fill(x + 8, rowY, x + PANEL_W - 8, rowY + ROW_H,
                    (hover || focused) ? HOVER : BOX);
            PanelStyle.divider(g, x + 10, rowY + ROW_H - 1, PANEL_W - 20);
            if (focused) {
                g.fill(x + 8, rowY, x + 10, rowY + ROW_H, ACCENT);
            }
            g.drawString(font, label(entry), x + 14, rowY + 7, TEXT, false);
            String price = entry.price() + " mk";
            g.drawString(font, price, x + PANEL_W - 16 - font.width(price), rowY + 7,
                    state.wallet() >= entry.price() ? GREEN : RED, false);
        }
        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("clinic.empty"), x + 14, listTop() + 6,
                    GREEN, false);
        }

        int buttonY = y + PANEL_H - 42;
        int total = totalPrice();
        boolean canAll = !entries.isEmpty() && state.wallet() >= total;
        boolean buttonFocused = focusArea == 1 && !entries.isEmpty();
        boolean hover = !entries.isEmpty() && mouseX >= x + 10 && mouseX < x + 190
                && mouseY >= buttonY && mouseY < buttonY + 18;
        PanelStyle.control(g, x + 10, buttonY, 180, 18, canAll, canAll && (hover || buttonFocused));
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
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button != 0) {
            return super.mouseClicked(screenX, screenY, button);
        }
        int x = leftPos();
        int y = topPos();
        int buttonY = y + PANEL_H - 42;
        if (!state.entries().isEmpty() && mouseX >= x + 10 && mouseX < x + 190
                && mouseY >= buttonY && mouseY < buttonY + 18) {
            focusArea = 1;
            healAll();
            return true;
        }
        int top = listTop();
        if (mouseY >= top && mouseY < top + VISIBLE_ROWS * ROW_H
                && mouseX >= x + 8 && mouseX < x + PANEL_W - 8) {
            int index = scroll + (int) ((mouseY - top) / ROW_H);
            List<ClinicStatePacket.Entry> entries = state.entries();
            if (index >= 0 && index < entries.size()) {
                focusArea = 0;
                focusRow = index;
                healEntry(entries.get(index));
                return true;
            }
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258) { // Tab：列表 / 全部治疗
            focusArea = 1 - focusArea;
            narrate(Component.translatable(focusArea == 0
                    ? "clinic.focus.list" : "clinic.focus.heal_all"));
            return true;
        }
        if (keyCode == 265 || keyCode == 264) { // 上/下
            if (focusArea == 0) {
                moveFocus(keyCode == 265 ? -1 : 1);
            } else {
                focusArea = 0;
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335 || keyCode == 32) { // Enter / Space
            if (focusArea == 1) {
                healAll();
            } else if (focusRow >= 0 && focusRow < state.entries().size()) {
                healEntry(state.entries().get(focusRow));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 键盘上下移动病症行焦点，并保证焦点行在可视区内。 */
    private void moveFocus(int delta) {
        int rows = state.entries().size();
        if (rows <= 0) {
            focusRow = -1;
            return;
        }
        int next = focusRow < 0 ? 0 : Math.max(0, Math.min(focusRow + delta, rows - 1));
        focusRow = next;
        if (next < scroll) {
            scroll = next;
        } else if (next >= scroll + VISIBLE_ROWS) {
            scroll = next - VISIBLE_ROWS + 1;
        }
        ClinicStatePacket.Entry entry = state.entries().get(next);
        narrate(Component.literal(label(entry) + " · " + entry.price() + " mk"));
    }

    private void healEntry(ClinicStatePacket.Entry entry) {
        ModNetwork.sendToServer(new ClinicActionPacket(
                entry.afflictionId(), entry.limb(), entry.price()));
    }

    private void healAll() {
        if (state.entries().isEmpty()) {
            return;
        }
        ModNetwork.sendToServer(new ClinicActionPacket(
                ClinicActionPacket.HEAL_ALL, "", totalPrice()));
    }

    /** 屏幕阅读器旁白；没有旁白模块时静默。 */
    private void narrate(Component text) {
        if (minecraft != null) {
            minecraft.getNarrator().sayNow(text);
        }
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double delta) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (delta != 0) {
            int max = Math.max(0, state.entries().size() - VISIBLE_ROWS);
            scroll = Math.max(0, Math.min(max, scroll + (delta > 0 ? -1 : 1)));
            // 焦点行被滚出可视区后，Enter 不能再去治那一行（会误治并扣费）：
            // 把焦点夹回可视范围，高亮仍在玩家看得见的那一行上。
            if (focusRow >= 0) {
                focusRow = Math.max(scroll,
                        Math.min(focusRow, scroll + VISIBLE_ROWS - 1));
            }
            return true;
        }
        return super.mouseScrolled(screenX, screenY, delta);
    }
}
