package com.opamb.barotrauma.client;

import com.opamb.barotrauma.campaign.*;
import com.opamb.barotrauma.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 程序绘制的 M1 海图：等比首群系裁剪，右侧任务滚动区，非原作贴图复刻。 */
public final class CampaignMapScreen extends Screen {
    private int selected = -1, origin = -1, scroll, maxScroll;
    private final Set<String> checked = new LinkedHashSet<>();
    private int mapX, mapY, mapW, mapH, panelX, panelW, bodyY, bodyBottom;
    private double scale;
    private Button dive;
    private boolean pending;

    public CampaignMapScreen() { super(tr("title")); }
    private static Component tr(String key, Object... args) { return Component.translatable("campaign.barotrauma." + key, args); }
    private static Component nodeName(MapNode n) { return tr("node." + n.id()); }

    @Override protected void init() {
        var state = CampaignClient.state();
        if (state == null) { onClose(); return; }
        if (origin != state.current()) { origin = state.current(); selected = -1; checked.clear(); }
        panelW = Math.max(135, Math.min(270, width * 42 / 100));
        panelX = width - panelW - 10;
        int availableW = Math.max(60, panelX - 20), availableH = Math.max(45, height - 82);
        scale = Math.min(availableW / (double) CampaignMap.VIEW_WIDTH, availableH / (double) CampaignMap.MAP_HEIGHT);
        mapW = (int) (CampaignMap.VIEW_WIDTH * scale); mapH = (int) (CampaignMap.MAP_HEIGHT * scale);
        mapX = 10 + (availableW - mapW) / 2; mapY = 45 + (availableH - mapH) / 2;
        bodyY = 53; bodyBottom = height - 54;
        dive = addRenderableWidget(Button.builder(tr("dive"), button -> {
            if (!canDive()) {
                Component why = selected < 0 ? Component.translatable("campaign.barotrauma.select_first")
                        : pending ? Component.translatable("campaign.barotrauma.pending")
                        : Component.translatable("campaign.barotrauma.invalid");
                if (net.minecraft.client.Minecraft.getInstance().player != null) {
                    net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(why, true);
                }
                return;
            }
            pending = true; dive.active = false;
            ModNetwork.CHANNEL.sendToServer(new CampaignActionPacket(origin, selected, List.copyOf(checked)));
            // 服务端会立刻把全员传送进艇；先关掉海图，否则玩家会卡在 GUI 里点不到声纳/水泵。
            onClose();
        }).bounds(panelX + 6, height - 44, panelW - 12, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("campaign.barotrauma.close"), button -> onClose())
                .bounds(10, height - 28, 70, 20).build());
        dive.active = canDive();
    }

    public void onCampaignUpdate() {
        var state = CampaignClient.state();
        if (state == null || state.phase() != RoundPhase.AT_NODE) { onClose(); return; }
        pending = false;
        if (state.current() != origin) { origin = state.current(); selected = -1; checked.clear(); scroll = 0; }
        if (dive != null) dive.active = canDive();
    }
    private boolean canDive() {
        var state = CampaignClient.state();
        return !pending && state != null && CampaignClient.available()
                && CampaignMission.valid(state.phase(), state.current(), origin, selected, List.copyOf(checked));
    }
    private int x(MapNode n) { return mapX + (int) Math.round(n.mapX() * scale); }
    private int y(MapNode n) { return mapY + (int) Math.round(n.mapY() * scale); }
    private MapNode hit(double mx, double my) {
        for (MapNode n : CampaignMap.nodes()) if (Math.abs(mx - x(n)) <= 8 && Math.abs(my - y(n)) <= 8) return n;
        return null;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        g.fill(5, 5, width - 5, height - 5, 0xF0101D26);
        g.drawString(font, title, 12, 12, 0xFFC1E8D9);
        g.drawString(font, tr("biome"), 12, 28, 0xFF80CEAF);
        g.drawString(font, tr("wallet", CampaignClient.state() == null ? 0 : CampaignClient.state().credits()), panelX, 28, 0xFFE3D78D);
        g.fill(mapX - 2, mapY - 2, mapX + mapW + 2, mapY + mapH + 2, 0xFF07151E);
        for (int i = 0; i <= CampaignMap.VIEW_WIDTH; i += 100) {
            int gx = mapX + (int) (i * scale); g.fill(gx, mapY, gx + 1, mapY + mapH, 0xFF18313D);
        }
        for (int i = 0; i <= CampaignMap.MAP_HEIGHT; i += 100) {
            int gy = mapY + (int) (i * scale); g.fill(mapX, gy, mapX + mapW, gy + 1, 0xFF18313D);
        }
        for (MapNode n : CampaignMap.nodes()) {
            for (int other : n.neighbors()) if (n.id() < other) {
                int color = (n.id() == origin && other == selected || n.id() == selected && other == origin)
                        ? 0xFFFFDD80 : 0xFF527C79;
                line(g, x(n), y(n), x(CampaignMap.node(other)), y(CampaignMap.node(other)), color);
            }
        }
        for (MapNode n : CampaignMap.nodes()) {
            int color = n.id() == origin ? 0xFFFFDD80 : n.id() == selected ? 0xFFFFFFFF : 0xFF80CEAF;
            int r = n.type() == NodeType.OUTPOST ? 5 : 3;
            g.fill(x(n) - r, y(n) - r, x(n) + r + 1, y(n) + r + 1, color);
            if (n.id() == origin || n.id() == selected) {
                g.fill(x(n) - 8, y(n) - 9, x(n) + 9, y(n) - 8, color);
                g.fill(x(n) - 8, y(n) + 8, x(n) + 9, y(n) + 9, color);
            }
            // Narrow maps keep station labels in the hover card instead of overlapping.
            if (mapW >= 290) g.drawCenteredString(font, nodeName(n), x(n), y(n) + 12, color);
        }
        g.fill(panelX, 44, width - 10, height - 49, 0xFF172B35);
        g.enableScissor(panelX + 4, bodyY, width - 14, bodyBottom);
        if (!CampaignMap.isValid(selected)) {
            wrapped(g, tr("choose"), panelX + 8, bodyY + 6 - scroll, panelW - 16, 0xFFB6CEC8);
        } else {
            MapNode target = CampaignMap.node(selected);
            int yy = bodyY - scroll;
            yy = wrapped(g, tr("route", nodeName(CampaignMap.node(origin)), nodeName(target)), panelX + 8, yy, panelW - 16, 0xFFE4F1E9) + 6;
            for (CampaignMission mission : CampaignMission.offered(origin, selected)) {
                g.fill(panelX + 8, yy + 1, panelX + 17, yy + 10, checked.contains(mission.id()) ? 0xFF80CEAF : 0xFF07151E);
                yy = wrapped(g, tr("mission." + mission.kind() + ".name"), panelX + 23, yy, panelW - 31, 0xFFDBEAE3);
                yy = wrapped(g, tr("reward", mission.reward()), panelX + 23, yy + 2, panelW - 31, 0xFFE3D78D);
                yy = wrapped(g, tr("mission." + mission.kind() + ".description"), panelX + 8, yy + 3, panelW - 16, 0xFFA8BEBB) + 12;
            }
            int end = wrapped(g, tr("boundary"), panelX + 8, yy, panelW - 16, 0xFFD4AA78);
            maxScroll = Math.max(0, end + scroll - bodyBottom + 6);
        }
        g.disableScissor();
        super.render(g, mx, my, partialTick);
        MapNode hover = hit(mx, my);
        if (hover != null) {
            var text = new ArrayList<Component>();
            text.add(nodeName(hover));
            text.add(tr("type." + hover.locationType()));
            text.add(tr("description." + hover.locationType()));
            if (hover.services().isEmpty()) text.add(tr("services.none"));
            else text.add(tr("services.outpost"));
            text.add(hover.faction().equals("none") ? tr("faction.none") : tr("faction.coalition", hover.reputation()));
            g.renderComponentTooltip(font, text, mx, my);
        }
    }
    private int wrapped(GuiGraphics g, Component text, int xx, int yy, int maxWidth, int color) {
        for (var line : font.split(text, Math.max(30, maxWidth))) { g.drawString(font, line, xx, yy, color, false); yy += 10; }
        return yy;
    }
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            int xx = (int) Math.round(x0 + (x1 - x0) * t), yy = (int) Math.round(y0 + (y1 - y0) * t);
            g.fill(xx, yy, xx + 1, yy + 1, color);
        }
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            MapNode n = hit(mx, my);
            if (n != null && CampaignMap.connects(origin, n.id()) && !pending) {
                selected = n.id(); checked.clear(); scroll = 0; dive.active = canDive(); return true;
            }
            if (n != null) {
                if (net.minecraft.client.Minecraft.getInstance().player != null) {
                    net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                            Component.translatable("campaign.barotrauma.adjacent_only"), true);
                }
            }
            if (mx >= panelX && mx < width - 10 && my >= bodyY && my < bodyBottom && !pending) {
                int yy = bodyY - scroll;
                if (CampaignMap.isValid(selected)) {
                    yy += font.split(tr("route", nodeName(CampaignMap.node(origin)), nodeName(CampaignMap.node(selected))), panelW - 16).size() * 10 + 6;
                    for (CampaignMission mission : CampaignMission.offered(origin, selected)) {
                        int hh = font.split(tr("mission." + mission.kind() + ".name"), panelW - 31).size() * 10;
                        if (my >= yy && my < yy + hh + 2) {
                            if (!checked.remove(mission.id())) checked.add(mission.id());
                            dive.active = canDive(); return true;
                        }
                        yy += hh + 2 + font.split(tr("reward", mission.reward()), panelW - 31).size() * 10
                                + 3 + font.split(tr("mission." + mission.kind() + ".description"), panelW - 16).size() * 10 + 12;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= panelX) { scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (delta * 20))); return true; }
        return super.mouseScrolled(mx, my, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (CampaignClient.OPEN_MAP.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean isPauseScreen() { return false; }
}
