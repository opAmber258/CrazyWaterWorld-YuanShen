package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.MissionMenu;
import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.network.MissionActionPacket;
import com.opamb.barotrauma.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * 任务终端界面：上半是任务板（接取/放弃/交付），下半是商店（修船/氧气瓶/焊枪/加固）。
 *
 * <p>状态来自 {@link ClientMissionCache}（服务端推送）；所有按钮只发
 * {@link MissionActionPacket}，成败由服务端判定并回包刷新。</p>
 */
public class MissionScreen extends AbstractContainerScreen<MissionMenu> {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 226;
    private static final int ROW_H = 26;

    private static final int BG = 0xF01B2320;
    private static final int EDGE = 0xFF3B463C;
    private static final int BOX = 0xFF232B25;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF8B9184;
    private static final int ACCENT = 0xFFE0762F;
    private static final int GREEN = 0xFF7BE38B;
    private static final int RED = 0xFFE06666;
    private static final int HOVER = 0xFF39483A;

    public MissionScreen(MissionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题用菜单的本地化名字（中文环境=任务终端，英文环境=Mission Terminal），
        // 不再在 renderBg 里手写中文，否则会和原版标题叠成中英文双重影（用户实测）。
        graphics.drawString(font, title, 10, 6, ACCENT, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    /** 绘制任务板、共享余额和商店按钮。 */
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, BG);
        graphics.fill(x, y, x + PANEL_W, y + 1, EDGE);
        graphics.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, EDGE);
        graphics.fill(x, y, x + 1, y + PANEL_H, EDGE);
        graphics.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, EDGE);

        Component wallet = Component.translatable("campaign.barotrauma.wallet",
                ClientMissionCache.credits());
        graphics.drawString(font, wallet,
                x + PANEL_W - 8 - font.width(wallet), y + 6, GREEN, false);

        List<Mission> offered = ClientMissionCache.offered();
        int rowY = y + 20;
        for (Mission mission : offered) {
            if (mission.state() == Mission.State.DONE) {
                continue;
            }
            drawMissionRow(graphics, x, rowY, mouseX, mouseY, mission);
            rowY += ROW_H + 2;
        }

        // 商店行
        int shopY = y + PANEL_H - 52;
        graphics.fill(x + 8, shopY - 4, x + PANEL_W - 8, shopY - 3, EDGE);
        graphics.drawString(font, "商店", x + 10, shopY, ACCENT, false);
        String[] labels = {"修船（1/HP）", "氧气瓶（40）", "焊枪（30）",
                "船体加固（150 起）", "深潜许可（500）"};
        int[] actions = {MissionActionPacket.BUY_REPAIR, MissionActionPacket.BUY_OXYGEN,
                MissionActionPacket.BUY_WELDER, MissionActionPacket.BUY_UPGRADE,
                MissionActionPacket.BUY_DEEP_PERMIT};
        for (int i = 0; i < labels.length; i++) {
            int bx = x + 8 + i * 80;
            boolean hover = mouseX >= bx && mouseX < bx + 76 && mouseY >= shopY + 12
                    && mouseY < shopY + 26;
            graphics.fill(bx, shopY + 12, bx + 76, shopY + 26, hover ? HOVER : BOX);
            graphics.fill(bx, shopY + 12, bx + 76, shopY + 13, EDGE);
            graphics.drawCenteredString(font, labels[i], bx + 38, shopY + 16, TEXT);
        }
        graphics.drawString(font, "修船/加固需要对着一艘已靠泊的船（24 格内）",
                x + 10, shopY + 30, DIM, false);
    }

    /** 画一条任务：类型/奖励/目标、状态与倒计时、操作按钮。 */
    private void drawMissionRow(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
                                Mission mission) {
        graphics.fill(x + 8, y, x + PANEL_W - 8, y + ROW_H, BOX);
        graphics.fill(x + 8, y, x + PANEL_W - 8, y + 1, EDGE);

        String type = switch (mission.type) {
            case SALVAGE -> "打捞";
            case RECON -> "侦察";
            case DELIVERY -> "运输";
            case URGENT_DELIVERY -> "紧急运输";
        };
        String timed = mission.deadlineTicks > 0 ? " · 限时" : "";
        String gated = mission.requiresDeepPermit() ? " · 需许可" : "";
        int dist = (int) Math.sqrt(minecraft.player.blockPosition().distSqr(mission.target));
        String title = String.format("#%d %s%s%s · 奖励 %d · 目标 %s %dm", mission.id, type,
                timed, gated, mission.reward, coord(mission), dist);
        graphics.drawString(font, title, x + 14, y + 4, TEXT, false);

        String stateLabel = switch (mission.state()) {
            case OFFERED -> "可接取";
            case ACTIVE -> "进行中";
            case READY -> "已达成！回终端交付";
            case DONE -> "已完成";
            case FAILED -> "已超时失败";
        };
        graphics.drawString(font, stateLabel, x + 14, y + 15,
                mission.state() == Mission.State.READY ? GREEN
                        : mission.state() == Mission.State.FAILED ? RED : DIM, false);

        boolean mine = ClientMissionCache.active() != null
                && ClientMissionCache.active().id == mission.id;
        if (mine && mission.deadlineAtGameTime() > 0
                && minecraft.level != null) {
            // 用客户端世界时间实时递减，不用发包时的快照（CodeRabbit 审查意见）。
            long left = mission.deadlineAtGameTime() - minecraft.level.getGameTime();
            String leftLabel = left > 0
                    ? String.format("剩余 %d:%02d", left / 1200L, (left / 20L) % 60L)
                    : "已超时";
            // 放在按钮左侧，避免被按钮盖住。
            graphics.drawString(font, leftLabel, x + PANEL_W - 150, y + 15,
                    left > 0 ? ACCENT : RED, false);
        }
        String button = mine
                ? (mission.state() == Mission.State.READY ? "交付" : "放弃")
                : (mission.isTaken() ? "已被接走" : "接取");
        boolean enabled = mission.state() != Mission.State.DONE
                && (!mission.isTaken() || mine);
        int bx = x + PANEL_W - 78;
        boolean hover = enabled && mouseX >= bx && mouseX < bx + 62
                && mouseY >= y + 5 && mouseY < y + 21;
        graphics.fill(bx, y + 5, bx + 62, y + 21,
                !enabled ? 0xFF1A211C : hover ? HOVER : 0xFF2F3A2F);
        graphics.drawCenteredString(font, button, bx + 31, y + 9,
                enabled ? TEXT : 0xFF5A6256);
    }

    private static String coord(Mission mission) {
        return mission.target.getX() + ", " + mission.target.getY() + ", " + mission.target.getZ();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 任务按钮在点击时重新判定，不能用渲染时缓存的 hover——
        // 鼠标移开后缓存不会清空，点商店按钮会被误判成上一个任务的「放弃」。
        if (button == 0) {
            int rowY = topPos + 20;
            for (Mission mission : ClientMissionCache.offered()) {
                if (mission.state() == Mission.State.DONE) {
                    continue;
                }
                int bx = leftPos + PANEL_W - 78;
                if (mouseX >= bx && mouseX < bx + 62
                        && mouseY >= rowY + 5 && mouseY < rowY + 21) {
                    boolean mine = ClientMissionCache.active() != null
                            && ClientMissionCache.active().id == mission.id;
                    boolean enabled = mission.state() != Mission.State.DONE
                            && (!mission.isTaken() || mine);
                    if (enabled) {
                        int action = mine
                                ? (mission.state() == Mission.State.READY
                                        ? MissionActionPacket.TURN_IN : MissionActionPacket.ABANDON)
                                : MissionActionPacket.ACCEPT;
                        ModNetwork.CHANNEL.sendToServer(new MissionActionPacket(action,
                                mission.id, menu.terminalPos()));
                    }
                    return true;
                }
                rowY += ROW_H + 2;
            }
        }
        // 商店按钮（无 missionId）
        int shopY = topPos + PANEL_H - 52;
        int localX = (int) mouseX - leftPos;
        // shopY 用的是屏幕坐标（含 topPos），这里也必须用 mouseY 比较；
        // 用 localY 会再减一次 topPos，面板居中偏移时整排商店按钮点不到。
        if (button == 0 && mouseY >= shopY + 12 && mouseY < shopY + 26
                && localX >= 8 && localX < PANEL_W - 8) {
            int rel = localX - 8;
            int idx = rel / 80;
            int off = rel - idx * 80;
            int[] actions = {MissionActionPacket.BUY_REPAIR, MissionActionPacket.BUY_OXYGEN,
                    MissionActionPacket.BUY_WELDER, MissionActionPacket.BUY_UPGRADE,
                    MissionActionPacket.BUY_DEEP_PERMIT};
            if (idx < 0 || idx >= actions.length || off >= 76) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            ModNetwork.CHANNEL.sendToServer(new MissionActionPacket(actions[idx]));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
