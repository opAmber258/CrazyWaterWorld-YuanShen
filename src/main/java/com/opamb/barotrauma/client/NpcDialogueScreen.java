package com.opamb.barotrauma.client;

import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.NpcDialoguePacket;
import com.opamb.barotrauma.network.NpcTalkPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 站务员对话框：照原作“对话 → 拿到任务 → 海图勾选 → 下潜”。
 *
 * <p>右键站务员先打招呼；点「有什么活儿？」才走服务端发任务的流程，回复里带上
 * 本次发放（或当前站已有）的任务 id，列表从 {@link ClientMissionCache} 里取。</p>
 */
public final class NpcDialogueScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 150;

    private static final int BG = 0xF01B2320;
    private static final int EDGE = 0xFF3B463C;
    private static final int TITLE = 0xFFE0762F;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF8B9184;
    private static final int ACCENT = 0xFF80CEAF;
    private static final int BOX = 0xFF232B25;

    private NpcDialoguePacket packet;
    private Button primary;
    private Button secondary;
    private boolean waiting;
    private long waitingSince;

    private NpcDialogueScreen(NpcDialoguePacket packet) {
        super(Component.translatable("entity.barotrauma.npc"));
        this.packet = packet;
    }

    /** 服务端包处理器调用（客户端主线程）。 */
    public static void receive(NpcDialoguePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof NpcDialogueScreen screen) {
            screen.packet = packet;
            screen.waiting = false;
            screen.rebuildWidgets();
        } else if (packet.phase() == NpcDialoguePacket.GREETING) {
            // 只有打招呼能新开对话；玩家已经关掉对话后的迟到应答直接丢弃。
            minecraft.setScreen(new NpcDialogueScreen(packet));
        }
    }

    private static Component tr(String key) {
        return Component.translatable("dialogue.barotrauma.clerk." + key);
    }

    @Override
    protected void init() {
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        int buttonY = top + PANEL_H - 28;
        boolean asked = packet.phase() != NpcDialoguePacket.GREETING;
        if (asked) {
            primary = addRenderableWidget(Button.builder(tr("open_map"), button -> {
                if (minecraft != null) {
                    minecraft.setScreen(new CampaignMapScreen());
                }
            }).bounds(left + PANEL_W - 184, buttonY, 84, 20).build());
            secondary = addRenderableWidget(Button.builder(tr("leave"), button -> onClose())
                    .bounds(left + PANEL_W - 94, buttonY, 84, 20).build());
        } else {
            primary = addRenderableWidget(Button.builder(tr("ask"), button -> {
                waiting = true;
                waitingSince = System.currentTimeMillis();
                button.active = false;
                ModNetwork.CHANNEL.sendToServer(new NpcTalkPacket(NpcTalkPacket.ASK_FOR_WORK));
            }).bounds(left + PANEL_W - 184, buttonY, 104, 20).build());
            secondary = addRenderableWidget(Button.builder(tr("leave"), button -> onClose())
                    .bounds(left + PANEL_W - 74, buttonY, 64, 20).build());
        }
        if (waiting) {
            primary.active = false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, BG);
        graphics.fill(left, top, left + PANEL_W, top + 1, EDGE);
        graphics.fill(left, top + PANEL_H - 1, left + PANEL_W, top + PANEL_H, EDGE);
        graphics.fill(left, top, left + 1, top + PANEL_H, EDGE);
        graphics.fill(left + PANEL_W - 1, top, left + PANEL_W, top + PANEL_H, EDGE);
        graphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + 18, 0xFF23282D);
        graphics.drawString(font, title, left + 8, top + 5, TITLE, false);

        int x = left + 12;
        int y = top + 26;
        int maxWidth = PANEL_W - 24;
        String line = switch (packet.phase()) {
            case NpcDialoguePacket.GRANTED -> tr("granted").getString();
            case NpcDialoguePacket.ALREADY -> tr("already").getString();
            case NpcDialoguePacket.NO_MISSIONS -> tr("none").getString();
            default -> tr("greeting").getString();
        };
        for (var wrapped : font.split(Component.literal(line), maxWidth)) {
            graphics.drawString(font, wrapped, x, y, TEXT, false);
            y += 10;
        }
        y += 4;

        if (packet.phase() != NpcDialoguePacket.GREETING) {
            for (Mission mission : missions()) {
                String dest = missionDestination(mission);
                String row = "#" + mission.id + " " + CampaignMapScreen.missionName(mission)
                        + (dest.isBlank() ? "" : " → " + dest)
                        + " · " + CampaignMapScreen.missionReward(mission);
                for (var wrapped : font.split(Component.literal(row), maxWidth)) {
                    graphics.drawString(font, wrapped, x, y, ACCENT, false);
                    y += 10;
                }
                y += 3;
                if (y > top + PANEL_H - 34) {
                    break;
                }
            }
        }
        if (waiting) {
            graphics.drawString(font, tr("waiting"), x, top + PANEL_H - 24, DIM, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 对话里要列出的任务：从客户端缓存按 id 找。 */
    private List<Mission> missions() {
        List<Mission> out = new ArrayList<>();
        for (int id : packet.missionIds()) {
            for (Mission mission : ClientMissionCache.offered()) {
                if (mission.id == id) {
                    out.add(mission);
                    break;
                }
            }
        }
        return out;
    }

    private static String missionDestination(Mission mission) {
        int dest = ClientMissionCache.offerDestination(mission.id);
        if (dest < 0) {
            dest = mission.destinationNode();
        }
        return dest < 0 ? ""
                : Component.translatable("campaign.barotrauma.node." + dest).getString();
    }

    @Override
    public void tick() {
        super.tick();
        // 服务端拒绝（例如被移出对话范围）时不会有回包：超时后恢复提问按钮。
        if (waiting && System.currentTimeMillis() - waitingSince > 3000L) {
            waiting = false;
            rebuildWidgets();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
