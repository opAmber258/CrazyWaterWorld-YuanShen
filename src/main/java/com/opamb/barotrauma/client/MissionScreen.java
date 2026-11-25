package com.opamb.barotrauma.client;

import com.opamb.barotrauma.menu.MissionMenu;
import com.opamb.barotrauma.network.MissionActionPacket;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 哨站商店终端：只卖补给（修船/氧气瓶/焊枪/加固/引擎/深潜许可）。
 *
 * <p>任务已改成在码头上找站务员 NPC 接，终端不再列任务；所有任务在靠泊目的站时
 * 自动结算，所以这里也没有"交付"按钮。</p>
 */
public class MissionScreen extends ScaledContainerScreen<MissionMenu> {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 96;

    private static final int EDGE = PanelStyle.EDGE;
    private static final int BOX = 0xFF232B25;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.SERVICE;
    private static final int GREEN = 0xFF7BE38B;
    private static final int HOVER = 0xFF39483A;

    public MissionScreen(MissionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 10, 6, ACCENT, false);
    }


    /** 商店按钮的顶边（屏幕坐标）。 */
    private int shopTop() {
        return topPos + 40;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        PanelStyle.frame(graphics, x, y, PANEL_W, PANEL_H, PanelStyle.SERVICE);

        Component wallet = Component.translatable("campaign.barotrauma.wallet",
                ClientMissionCache.credits());
        graphics.drawString(font, wallet,
                x + PANEL_W - 8 - font.width(wallet), y + 6, GREEN, false);
        graphics.drawString(font, "任务在码头上找站务员接；靠泊目的站后自动结算",
                x + 10, y + 22, DIM, false);

        int shopY = shopTop();
        graphics.fill(x + 8, shopY - 4, x + PANEL_W - 8, shopY - 3, EDGE);
        graphics.drawString(font, "商店", x + 10, shopY, ACCENT, false);
        // 报价必须和服务端扣款一致（MissionActionPacket 的 COST_*）：
        // 焊枪卖 150（和商店基础价一致，防止旧接口低价买入再高价卖给商店）。
        String[] labels = {"修船（1/HP）", "氧气瓶（40）", "焊枪（150）",
                "船体加固（150 起）", "引擎（150 起）", "深潜许可（500）"};
        for (int i = 0; i < labels.length; i++) {
            int bx = x + 8 + (i % 3) * 80;
            int by = shopY + 12 + (i / 3) * 20;
            boolean hover = mouseX >= bx && mouseX < bx + 76
                    && mouseY >= by && mouseY < by + 14;
            graphics.fill(bx, by, bx + 76, by + 14, hover ? HOVER : BOX);
            graphics.fill(bx, by, bx + 76, by + 1, EDGE);
            graphics.drawCenteredString(font, labels[i], bx + 38, by + 3, TEXT);
        }
    }

    @Override
    public boolean mouseClicked(double screenX, double screenY, int button) {
        double mouseX = designX(screenX);
        double mouseY = designY(screenY);
        // 商店按钮（无 missionId）：两行三列
        int shopY = shopTop();
        int localX = (int) mouseX - leftPos;
        if (button == 0 && mouseY >= shopY + 12 && mouseY < shopY + 52
                && localX >= 8 && localX < PANEL_W - 8) {
            int relX = localX - 8;
            int col = relX / 80;
            int offX = relX - col * 80;
            int relY = (int) mouseY - (shopY + 12);
            int row = relY / 20;
            int offY = relY - row * 20;
            int[] actions = {MissionActionPacket.BUY_REPAIR, MissionActionPacket.BUY_OXYGEN,
                    MissionActionPacket.BUY_WELDER, MissionActionPacket.BUY_UPGRADE,
                    MissionActionPacket.BUY_ENGINE_UPGRADE, MissionActionPacket.BUY_DEEP_PERMIT};
            if (relX < 0 || col >= 3 || offX >= 76
                    || relY < 0 || row >= 2 || offY >= 14) {
                return super.mouseClicked(screenX, screenY, button);
            }
            int idx = row * 3 + col;
            ModNetwork.CHANNEL.sendToServer(new MissionActionPacket(actions[idx]));
            return true;
        }
        return super.mouseClicked(screenX, screenY, button);
    }
}
