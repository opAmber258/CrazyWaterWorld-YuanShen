package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.RepairMenu;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.RepairActionPacket;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * 独立修理窗口，照原作 {@code Repairable} 的 GUI 帧：
 *
 * <pre>
 *            电气维修
 *   需要：螺丝刀
 *   [=======耐久条========][指针]
 *  [ 修理 ] / [ 修理中... ]
 * </pre>
 *
 * <p>进度条读数来自服务端每 tick 同步的耐久数据位，渲染时逐帧插值，
 * 所以是平滑推进而不是 20Hz 一跳。指针按原作规则运行：从右往左、速度按
 * {@code q/0.5} 指数衰减，每次点击后 0.5 秒复位。</p>
 */
public class RepairScreen extends ScaledContainerScreen<RepairMenu> {

    private static final int W = 244;
    private static final int H = 152;

    private static final int BG = 0xF0141B16;
    private static final int EDGE = 0xFF3B463C;
    private static final int PANEL = 0xFF1E2A22;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF7C8478;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF6FD07A;
    private static final int YELLOW = 0xFFD8B44A;
    private static final int RED = 0xFFC8483C;
    private static final int TRACK = 0xFF0C120E;

    private int startPendingTicks;
    /** 上一 tick / 当前 tick 的服务端 QTE 指针，渲染时插值。 */
    private float prevMarker = 1.0F;
    private float curMarker = 1.0F;

    private double prevFraction;
    private double curFraction;

    public RepairScreen(RepairMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        // 首帧用菜单里已有的数据位，避免开窗瞬间画成 0%。
        double max = Math.max(1.0D, menu.data().get(RepairMenu.DATA_MAX) / 100.0D);
        double start = menu.data().get(RepairMenu.DATA_CONDITION) / 100.0D / max;
        this.curFraction = Mth.clamp(start, 0.0D, 1.0D);
        this.prevFraction = this.curFraction;
    }

    /* ---------------------------------------------------------------- 状态 */

    private boolean repairingView() {
        return menu.data().get(RepairMenu.DATA_REPAIRING) != 0 || startPendingTicks > 0;
    }

    private boolean hasTool() {
        return menu.data().get(RepairMenu.DATA_HAS_TOOL) != 0;
    }

    private boolean isFull() {
        return curFraction >= 1.0D - 1.0E-4D;
    }

    private boolean canRepair() {
        return menu.data().get(RepairMenu.DATA_CAN_REPAIR) != 0;
    }

    private double threshold() {
        return menu.data().get(RepairMenu.DATA_THRESHOLD) / 100.0D;
    }

    private boolean toolIsWrench() {
        return menu.data().get(RepairMenu.DATA_TOOL) == 1;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        prevMarker = curMarker;
        curMarker = markerFraction();
        if (startPendingTicks > 0) {
            startPendingTicks--;
        }
        double max = Math.max(1.0D, menu.data().get(RepairMenu.DATA_MAX) / 100.0D);
        double fraction = menu.data().get(RepairMenu.DATA_CONDITION) / 100.0D / max;
        prevFraction = curFraction;
        curFraction = Mth.clamp(fraction, 0.0D, 1.0D);
        if (isFull()) {
            startPendingTicks = 0;
        }
    }

    /** 指针位置完全由服务端数据位决定（服务端才是判定方）。 */
    private float markerFraction() {
        return Mth.clamp(menu.data().get(RepairMenu.DATA_QTE) / 1000.0F, 0.0F, 1.0F);
    }

    /** 两个 tick 之间插值，指针不会一格一格跳。 */
    private float shownMarker() {
        double partial = minecraft == null ? 1.0D : minecraft.getFrameTime();
        return Mth.clamp((float) Mth.lerp(partial, prevMarker, curMarker), 0.0F, 1.0F);
    }

    /* ---------------------------------------------------------------- 交互 */

    private int buttonX() {
        return leftPos + W / 2 - 52;
    }

    private int buttonY() {
        return topPos + 92;
    }

    private int buttonW() {
        return 104;
    }

    private int buttonH() {
        return 20;
    }

    private boolean buttonEnabled() {
        if (!hasTool()) {
            return false;
        }
        if (isFull()) {
            return false;
        }
        return repairingView() || canRepair();
    }

    private Component buttonLabel() {
        if (!hasTool()) {
            return Component.translatable(toolIsWrench()
                    ? "gui.barotrauma.repair.need_wrench" : "gui.barotrauma.repair.need_screwdriver");
        }
        if (isFull()) {
            return Component.translatable("gui.barotrauma.repair.full");
        }
        if (!repairingView() && !canRepair()) {
            return Component.translatable("gui.barotrauma.repair.not_needed");
        }
        if (repairingView()) {
            int dots = (int) (Util.getMillis() / 500L) % 3 + 1;
            return Component.translatable("gui.barotrauma.repair.repairing")
                    .append(".".repeat(dots));
        }
        return Component.translatable("gui.barotrauma.repair.button");
    }

    private void pressButton() {
        if (!buttonEnabled()) {
            return;
        }
        if (!repairingView()) {
            // 原作第一次点击只开始持续修理（OnButtonDown 早于开始修理，不算 QTE）。
            startPendingTicks = 3;
            send(RepairActionPacket.ACTION_START, 0.0F);
            return;
        }
        // 指针位置/冷却全在服务端；客户端只提交点击。
        send(RepairActionPacket.ACTION_QTE, markerFraction());
    }

    private void send(byte action, float marker) {
        ModNetwork.CHANNEL.sendToServer(
                new RepairActionPacket(menu.pos(), action, marker));
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        if (button == 0
                && mouseX >= buttonX() && mouseX < buttonX() + buttonW()
                && mouseY >= buttonY() && mouseY < buttonY() + buttonH()) {
            if (buttonEnabled()) {
                pressButton();
                return true;
            }
        }
        return super.mouseClicked(screenX, screenY, button);
    }

    @Override
    public void onClose() {
        send(RepairActionPacket.ACTION_STOP, 0.0F);
        super.onClose();
    }

    /* ---------------------------------------------------------------- 渲染 */

    private double shownFraction() {
        double partial = minecraft == null ? 1.0D : minecraft.getFrameTime();
        return Mth.clamp(repairingView()
                ? Mth.lerp(partial, prevFraction, curFraction) : curFraction, 0.0D, 1.0D);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Component title = Component.translatable(toolIsWrench()
                ? "gui.barotrauma.repair.mechanical" : "gui.barotrauma.repair.electrical");
        graphics.drawString(font, title, (W - font.width(title)) / 2, 10, ACCENT, false);

        Component tool = Component.translatable(toolIsWrench()
                ? "gui.barotrauma.repair.need_wrench" : "gui.barotrauma.repair.need_screwdriver");
        graphics.drawString(font, tool, (W - font.width(tool)) / 2, 30,
                hasTool() ? DIM : RED, false);
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

        double fraction = shownFraction();
        int barX = x + 16;
        int barY = y + 52;
        int barW = W - 32;
        int barH = 16;

        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, EDGE);
        graphics.fill(barX, barY, barX + barW, barY + barH, TRACK);
        int fillColor = fraction <= 0.2D ? RED : fraction <= 0.5D ? YELLOW : GREEN;
        graphics.fill(barX, barY, barX + (int) Math.round(barW * fraction), barY + barH,
                fillColor);

        // 阈值刻度：低于它才允许开始修理（反应堆 50%，其余 80%）。
        int thresholdX = barX + (int) Math.round(barW * threshold());
        graphics.fill(thresholdX, barY, thresholdX + 1, barY + barH, 0x88FFFFFF);

        // QTE 指针：按用户要求始终白色，不做成功/失败着色。
        float marker = shownMarker();
        int markerX = barX + Math.round(barW * marker);
        graphics.fill(markerX - 1, barY - 3, markerX + 2, barY + barH + 3, 0xFFFFFFFF);

        String percent = String.format("%.0f%%", fraction * 100.0D);
        graphics.drawString(font, percent,
                barX + (barW - font.width(percent)) / 2,
                barY + (barH - 8) / 2 + 1, 0xFF101810, false);

        // 修理按钮。
        boolean enabled = buttonEnabled();
        int bx = buttonX();
        int by = buttonY();
        boolean hovered = mouseX >= bx && mouseX < bx + buttonW()
                && mouseY >= by && mouseY < by + buttonH();
        graphics.fill(bx - 1, by - 1, bx + buttonW() + 1, by + buttonH() + 1, EDGE);
        graphics.fill(bx, by, bx + buttonW(), by + buttonH(),
                enabled ? (hovered ? 0xFF2C4034 : PANEL) : 0xFF171F1A);
        Component label = buttonLabel();
        graphics.drawString(font, label, bx + (buttonW() - font.width(label)) / 2,
                by + (buttonH() - 8) / 2 + 1, enabled ? TEXT : DIM, false);

        // 底部提示。
        Component hint;
        int hintColor = DIM;
        if (!hasTool()) {
            hint = Component.translatable("gui.barotrauma.repair.hint.tool");
            hintColor = RED;
        } else if (isFull()) {
            hint = Component.translatable("gui.barotrauma.repair.hint.full");
        } else if (!canRepair() && !repairingView()) {
            hint = Component.translatable("gui.barotrauma.repair.hint.threshold");
            hintColor = RED;
        } else {
            hint = Component.translatable("gui.barotrauma.repair.hint.qte");
        }
        graphics.drawString(font, hint, (W - font.width(hint)) / 2, 124, hintColor, false);
    }
}
