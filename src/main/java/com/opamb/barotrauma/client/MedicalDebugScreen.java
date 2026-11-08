package com.opamb.barotrauma.client;

import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.network.MedicalDebugPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 医疗调试器面板（开发者工具）：自由摆布六部位 + 全身的病症强度与活力。
 *
 * <p>数据源是 {@link ClientMedicalCache} 的服务端快照（含 "v|值" 调试覆盖行）；
 * 所有编辑发 {@link MedicalDebugPacket} 由服务端校验并回发快照。
 * 布局仿任务终端：深色面板 + 左侧部位页签 + 右侧病症行。</p>
 */
public final class MedicalDebugScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 236;

    private static final int BG = 0xF01B2320;
    private static final int EDGE = 0xFF3B463C;
    private static final int BOX = 0xFF232B25;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF8B9184;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF7BE38B;
    private static final int RED = 0xFFE06666;
    private static final int HOVER = 0xFF39483A;

    /** 左侧页签：六部位 + 全身（null）。 */
    private static final Limb[] TABS = {
            Limb.HEAD, Limb.TORSO, Limb.LEFT_ARM, Limb.RIGHT_ARM, Limb.LEFT_LEG, Limb.RIGHT_LEG, null
    };

    /** 分部位病症（选中部位时显示）。 */
    private static final AfflictionType[] LIMB_TYPES = {
            AfflictionType.BLUNT_TRAUMA, AfflictionType.BLEEDING, AfflictionType.BURN
    };

    /** 全身病症（选中"全身"时显示）。 */
    private static final AfflictionType[] GLOBAL_TYPES = {
            AfflictionType.BLOODLOSS, AfflictionType.OXYGEN_LOW, AfflictionType.STUN,
            AfflictionType.OPIATE_OVERDOSE, AfflictionType.OPIATE_ADDICTION,
            AfflictionType.OPIATE_WITHDRAWAL, AfflictionType.ORGAN_DAMAGE
    };

    private static final double[] STEPS = {-100, -10, -1, 1, 10, 100};

    /** 当前选中页签下标（TABS 索引，6 = 全身）。 */
    private int tab = 1;

    public MedicalDebugScreen() {
        super(Component.literal("医疗调试器"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.setScreen(new MedicalDebugScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;

        g.fill(x, y, x + PANEL_W, y + PANEL_H, BG);
        g.fill(x, y, x + PANEL_W, y + 1, EDGE);
        g.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, EDGE);
        g.fill(x, y, x + 1, y + PANEL_H, EDGE);
        g.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, EDGE);

        g.drawString(font, "医疗调试器（开发者）", x + 8, y + 6, ACCENT, false);
        g.drawString(font, "创造/管理员限定", x + PANEL_W - 8 - font.width("创造/管理员限定"),
                y + 6, DIM, false);

        drawVitalityRow(g, x, y, mouseX, mouseY);
        drawTabs(g, x, y, mouseX, mouseY);
        drawAfflictionRows(g, x, y, mouseX, mouseY);
        drawBottomButtons(g, x, y, mouseX, mouseY);

        g.drawString(font, "点击立即写入服务端；活力覆盖可用「跟随」取消",
                x + 8, y + PANEL_H - 14, DIM, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 顶部活力行：当前值 + 覆盖状态 + 六档增减 + 跟随病症。 */
    private void drawVitalityRow(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<String> rows = rows();
        float vitality = snapshotVitality();
        Double override = overrideOf(rows);
        g.drawString(font, "活力", x + 8, y + 24, TEXT, false);
        g.drawString(font, String.format("%.1f", vitality), x + 30, y + 24,
                override != null ? ACCENT : GREEN, false);
        if (override != null) {
            g.drawString(font, "覆盖", x + 62, y + 24, ACCENT, false);
        }
        int bx = x + PANEL_W - 8 - (STEPS.length * 30 + 34);
        for (int i = 0; i < STEPS.length; i++) {
            int bxi = bx + i * 30;
            boolean hover = in(mouseX, mouseY, bxi, y + 20, 27, 14);
            drawButton(g, bxi, y + 20, 27, 14, stepLabel(STEPS[i]), hover, true);
        }
        int clearX = bx + STEPS.length * 30;
        drawButton(g, clearX, y + 20, 31, 14, "跟随",
                in(mouseX, mouseY, clearX, y + 20, 31, 14), override != null);
    }

    private void drawTabs(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < TABS.length; i++) {
            int ty = tabY(y, i);
            boolean selected = i == tab;
            boolean hover = in(mouseX, mouseY, x + 8, ty, 76, 18);
            g.fill(x + 8, ty, x + 84, ty + 18, selected ? HOVER : BOX);
            if (selected) {
                g.fill(x + 8, ty, x + 10, ty + 18, ACCENT);
            }
            String name = TABS[i] == null ? "全身" : MedicalBodyRenderer.limbName(TABS[i]);
            g.drawString(font, name, x + 16, ty + 5, selected ? TEXT : hover ? TEXT : DIM, false);
        }
    }

    private void drawAfflictionRows(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        AfflictionType[] types = TABS[tab] == null ? GLOBAL_TYPES : LIMB_TYPES;
        List<String> rows = rows();
        int rowH = TABS[tab] == null ? 20 : 28;
        for (int i = 0; i < types.length; i++) {
            AfflictionType type = types[i];
            double strength = MedicalBodyRenderer.strengthOf(rows, type, TABS[tab]);
            int ry = rowY(y, i, rowH);
            int right = x + PANEL_W - 8;
            g.fill(x + 92, ry, right, ry + rowH - 2, BOX);
            g.fill(x + 92, ry, right, ry + 1, EDGE);
            int color = strength > 0
                    ? MedicalBodyRenderer.severityColor(
                            MedicalBodyRenderer.severityRatio(type, strength))
                    : DIM;
            // 只取病名（去掉 label() 的百分比后缀），百分比由右侧数值体现，避免长名压住按钮
            String full = MedicalBodyRenderer.label(type, strength);
            int cut = full.lastIndexOf(' ');
            String name = cut > 0 ? full.substring(0, cut) : full;
            g.drawString(font, name, x + 96, ry + 4, color, false);
            String value = String.format("%.0f", strength);
            g.drawString(font, value, right - 106 - font.width(value), ry + 4, TEXT, false);
            for (int j = 0; j < STEPS.length; j++) {
                int bxi = right - 104 + j * 26;
                boolean hover = in(mouseX, mouseY, bxi, ry + 3, 24, 14);
                drawButton(g, bxi, ry + 3, 24, 14, stepLabel(STEPS[j]), hover, true);
            }
        }
    }

    private void drawBottomButtons(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int by = y + PANEL_H - 34;
        String clearLabel = TABS[tab] == null ? "清空全身病症" : "清空该部位";
        drawButton(g, x + 8, by, 100, 16, clearLabel,
                in(mouseX, mouseY, x + 8, by, 100, 16), true);
        drawButton(g, x + 112, by, 76, 16, "清空全部",
                in(mouseX, mouseY, x + 112, by, 76, 16), true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = (width - PANEL_W) / 2;
        int y = (height - PANEL_H) / 2;

        // 活力增减 / 跟随
        int bx = x + PANEL_W - 8 - (STEPS.length * 30 + 34);
        for (int i = 0; i < STEPS.length; i++) {
            int bxi = bx + i * 30;
            if (in(mouseX, mouseY, bxi, y + 20, 27, 14)) {
                send(MedicalDebugPacket.ADD_VITALITY, "", "-", STEPS[i]);
                return true;
            }
        }
        int clearX = bx + STEPS.length * 30;
        if (in(mouseX, mouseY, clearX, y + 20, 31, 14)) {
            send(MedicalDebugPacket.CLEAR_VITALITY, "", "-", 0.0);
            return true;
        }

        // 部位页签
        for (int i = 0; i < TABS.length; i++) {
            if (in(mouseX, mouseY, x + 8, tabY(y, i), 76, 18)) {
                tab = i;
                return true;
            }
        }

        // 病症增减
        AfflictionType[] types = TABS[tab] == null ? GLOBAL_TYPES : LIMB_TYPES;
        int rowH = TABS[tab] == null ? 20 : 28;
        int right = x + PANEL_W - 8;
        for (int i = 0; i < types.length; i++) {
            int ry = rowY(y, i, rowH);
            for (int j = 0; j < STEPS.length; j++) {
                int bxi = right - 104 + j * 26;
                if (in(mouseX, mouseY, bxi, ry + 3, 24, 14)) {
                    send(MedicalDebugPacket.ADD_AFFLICTION, types[i].id(),
                            TABS[tab] == null ? "-" : TABS[tab].name(), STEPS[j]);
                    return true;
                }
            }
        }

        // 清空按钮
        int by = y + PANEL_H - 34;
        if (in(mouseX, mouseY, x + 8, by, 100, 16)) {
            send(MedicalDebugPacket.CLEAR_LIMB, "",
                    TABS[tab] == null ? "-" : TABS[tab].name(), 0.0);
            return true;
        }
        if (in(mouseX, mouseY, x + 112, by, 76, 16)) {
            send(MedicalDebugPacket.CLEAR_ALL, "", "-", 0.0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------
    // 数据与布局
    // ------------------------------------------------------------------

    private List<String> rows() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return List.of();
        }
        ClientMedicalCache.Snapshot snapshot = ClientMedicalCache.snapshot(mc.player.getUUID());
        return snapshot == null ? List.of() : snapshot.rows();
    }

    private float snapshotVitality() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0.0F;
        }
        ClientMedicalCache.Snapshot snapshot = ClientMedicalCache.snapshot(mc.player.getUUID());
        return snapshot == null ? 0.0F : snapshot.vitality();
    }

    /** 调试活力覆盖行（v|值）；没有覆盖返回 null。 */
    private static Double overrideOf(List<String> rows) {
        for (String row : rows) {
            if (row.startsWith("v|")) {
                try {
                    return Double.parseDouble(row.substring(2));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static int tabY(int top, int index) {
        return top + 44 + index * 22;
    }

    private static int rowY(int top, int index, int rowH) {
        return top + 44 + index * rowH;
    }

    private static String stepLabel(double step) {
        return (step > 0 ? "+" : "") + (int) step;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, String label,
                            boolean hover, boolean enabled) {
        g.fill(x, y, x + w, y + h, !enabled ? 0xFF1A211C : hover ? HOVER : 0xFF2F3A2F);
        g.fill(x, y, x + w, y + 1, EDGE);
        int color = enabled ? TEXT : 0xFF5A6256;
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, color);
    }

    private void send(int action, String typeId, String limb, double value) {
        ModNetwork.sendToServer(new MedicalDebugPacket(action, typeId, limb, value));
    }
}
