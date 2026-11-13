package com.opamb.barotrauma.client;

import com.opamb.barotrauma.campaign.*;
import com.opamb.barotrauma.mission.CargoManifest;
import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.mission.MissionCatalog;
import com.opamb.barotrauma.mission.MissionSpec;
import com.opamb.barotrauma.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * M1 海图：原作首群系底图 + 虚线航路 + 站点图标。
 *
 * <p>照原作的任务流程：和站务员对话领取委托（每次访问最多 3 条，绑定目的地）→ 打开这张图 →
 * 左键选相邻目的站 → 右侧只列该目的地能接的任务、勾选（一趟最多 3 条）→ 点「下潜」时
 * 连同勾选一起发给服务端，服务端接取任务（货运同时装货）并开始航程。</p>
 */
public final class CampaignMapScreen extends Screen {
    private static final ResourceLocation MAP_PIECES =
            new ResourceLocation("barotrauma", "textures/gui/map_pieces.png");
    private static final ResourceLocation MAP_ICONS =
            new ResourceLocation("barotrauma", "textures/gui/map_icons.png");
    /** map_icons.png 里的图标槽位（见生成脚本）。 */
    private static final int ICON_OUTPOST_U = 0;
    private static final int ICON_CITY_U = 128;
    private static final int ICON_WAYPOINT_U = 256;
    private static final int ICON_CURRENT_U = 384;
    private static final int ICON_SELECTED_V = 128;
    private static final int ICON_MISSION_U = 200;

    private int selected = -1, origin = -1, scroll, maxScroll;
    private final Set<Integer> checked = new LinkedHashSet<>();
    private int mapX, mapY, mapW, mapH, panelX, panelW, bodyY, bodyBottom;
    private double scale;
    private Button dive;
    private boolean pending;

    public CampaignMapScreen() { super(tr("title")); }

    /** 服务端发 OpenCampaignMapPacket 后调用（右键站务员）。 */
    public static void openFromNpc() {
        Minecraft.getInstance().setScreen(new CampaignMapScreen());
    }

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
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(why, true);
                }
                return;
            }
            pending = true; dive.active = false;
            List<String> ids = new ArrayList<>();
            for (int id : checked) {
                ids.add(Integer.toString(id));
            }
            ModNetwork.CHANNEL.sendToServer(new CampaignActionPacket(origin, selected, ids));
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
        onMissionUpdate();
    }

    /** 任务状态包更新后调用：清掉已失效的勾选并刷新下潜按钮。 */
    public void onMissionUpdate() {
        if (CampaignMap.isValid(selected)) {
            List<Mission> current = available();
            checked.removeIf(id -> current.stream().noneMatch(mission -> mission.id == id));
        } else {
            checked.clear();
        }
        if (dive != null) dive.active = canDive();
    }
    private boolean canDive() {
        var state = CampaignClient.state();
        return !pending && state != null && CampaignClient.available()
                && state.phase() == RoundPhase.AT_NODE && state.current() == origin
                && CampaignMap.connects(origin, selected) && checked.size() <= CampaignMission.LIMIT;
    }
    private int x(MapNode n) { return mapX + (int) Math.round(n.mapX() * scale); }
    private int y(MapNode n) { return mapY + (int) Math.round(n.mapY() * scale); }
    private MapNode hit(double mx, double my) {
        for (MapNode n : CampaignMap.nodes()) if (Math.abs(mx - x(n)) <= 12 && Math.abs(my - y(n)) <= 12) return n;
        return null;
    }

    /** 当前站挂出来的、能接去所选目的地的那些（原作海图按目的地过滤）。 */
    private List<Mission> available() {
        return ClientMissionCache.grantedFor(selected);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g);
        g.fill(5, 5, width - 5, height - 5, 0xF0081116);
        g.drawString(font, title, 12, 12, 0xFFC1E8D9);
        g.drawString(font, tr("biome"), 12, 28, 0xFF80CEAF);
        g.drawString(font, tr("wallet", CampaignClient.state() == null ? 0 : CampaignClient.state().credits()), panelX, 28, 0xFFE3D78D);
        g.drawCenteredString(font, tr("biome"), mapX + mapW / 2, Math.max(46, mapY - 12), 0xAA9FBDB4);
        g.fill(mapX - 2, mapY - 2, mapX + mapW + 2, mapY + mapH + 2, 0xFF02070A);

        g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        drawBackground(g);
        // 极淡的坐标网格，只为参照距离
        for (int i = 0; i <= CampaignMap.VIEW_WIDTH; i += 200) {
            int gx = mapX + (int) (i * scale);
            g.fill(gx, mapY, gx + 1, mapY + mapH, 0x1680CEAF);
        }
        for (int i = 0; i <= CampaignMap.MAP_HEIGHT; i += 200) {
            int gy = mapY + (int) (i * scale);
            g.fill(mapX, gy, mapX + mapW, gy + 1, 0x1680CEAF);
        }
        // 航线：原作是虚线，不是树状实线
        for (MapNode n : CampaignMap.nodes()) {
            for (int other : n.neighbors()) if (n.id() < other) {
                boolean highlight = (n.id() == origin && other == selected)
                        || (n.id() == selected && other == origin);
                dashedLine(g, x(n), y(n), x(CampaignMap.node(other)), y(CampaignMap.node(other)),
                        highlight ? 0xFFFFDD80 : 0x99A9C4BC);
            }
        }
        // 站点：方形图标按类型区分，当前站盖潜艇罗盘
        for (MapNode n : CampaignMap.nodes()) {
            int cx = x(n), cy = y(n);
            if (n.id() == selected) {
                g.blit(MAP_ICONS, cx - 23, cy - 25, 46, 50, 0, ICON_SELECTED_V, 186, 205, 512, 512);
            }
            if (n.id() == origin) {
                g.blit(MAP_ICONS, cx - 17, cy - 17, 34, 34, ICON_CURRENT_U, 0, 128, 128, 512, 512);
            } else {
                int u = n.locationType().equals("city") ? ICON_CITY_U
                        : n.type() == NodeType.OUTPOST ? ICON_OUTPOST_U : ICON_WAYPOINT_U;
                int size = n.type() == NodeType.OUTPOST ? 30 : 20;
                g.blit(MAP_ICONS, cx - size / 2, cy - size / 2, size, size, u, 0, 128, 128, 512, 512);
            }
            // 有去这个目的地的报价：角标感叹号
            if (n.id() != origin && CampaignMap.connects(origin, n.id())
                    && !ClientMissionCache.grantedFor(n.id()).isEmpty()) {
                g.blit(MAP_ICONS, cx + 9, cy - 19, 12, 12, ICON_MISSION_U, 128, 30, 30, 512, 512);
            }
        }
        g.disableScissor();

        g.fill(panelX, 44, width - 10, height - 49, 0xFF172B35);
        g.enableScissor(panelX + 4, bodyY, width - 14, bodyBottom);
        if (!CampaignMap.isValid(selected)) {
            wrapped(g, tr("choose"), panelX + 8, bodyY + 6 - scroll, panelW - 16, 0xFFB6CEC8);
        } else {
            MapNode target = CampaignMap.node(selected);
            int yy = bodyY - scroll;
            yy = wrapped(g, tr("route", nodeName(CampaignMap.node(origin)), nodeName(target)), panelX + 8, yy, panelW - 16, 0xFFE4F1E9) + 6;
            yy = wrapped(g, tr("missions_accepted", checked.size(), CampaignMission.LIMIT),
                    panelX + 8, yy, panelW - 16, 0xFF80CEAF) + 4;
            List<Mission> available = available();
            if (available.isEmpty()) {
                // 这个目的地没有站务员发过的委托：回码头对话领取。
                yy = wrapped(g, tr("missions_hint"), panelX + 8, yy, panelW - 16, 0xFFA8BEBB) + 8;
            } else {
                for (Mission mission : available) {
                    boolean boxed = checked.contains(mission.id);
                    g.fill(panelX + 8, yy + 1, panelX + 17, yy + 10,
                            boxed ? 0xFF80CEAF : 0xFF07151E);
                    yy = wrapped(g, tr("mission_line", mission.id, missionName(mission),
                            missionReward(mission)), panelX + 23, yy, panelW - 31, 0xFFDBEAE3) + 4;
                }
                yy += 4;
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

    static String missionName(Mission mission) {
        MissionSpec spec = MissionCatalog.offer(mission.id);
        if (spec != null) {
            String name = Component.translatable(spec.nameKey()).getString();
            if (!name.contains(".barotrauma.") && !name.isBlank()) {
                return name;
            }
        }
        return switch (mission.type) {
            case SALVAGE -> "打捞";
            case RECON -> "侦察";
            case DELIVERY -> "货运";
            case URGENT_DELIVERY -> "紧急货运";
        };
    }

    static String missionReward(Mission mission) {
        MissionSpec spec = MissionCatalog.offer(mission.id);
        CargoManifest manifest = spec == null ? null : CargoManifest.forMission(spec.id());
        if (manifest != null) {
            return Component.translatable("campaign.barotrauma.reward_per_crate",
                    manifest.rewardPerCrate()).getString();
        }
        return Component.translatable("campaign.barotrauma.reward_flat", mission.reward).getString();
    }

    /** 用原作寒冷洞穴的地形切片平铺；三个变体轮着来，避免一眼重复。 */
    private void drawBackground(GuiGraphics g) {
        int tile = Math.max(48, Math.min(mapW / 3, mapH / 2));
        int cols = (mapW + tile - 1) / tile;
        int rows = (mapH + tile - 1) / tile;
        for (int ix = 0; ix < cols; ix++) {
            for (int iy = 0; iy < rows; iy++) {
                int variant = Math.floorMod(ix * 2 + iy * 3, 3);
                g.blit(MAP_PIECES, mapX + ix * tile, mapY + iy * tile, tile, tile,
                        variant * 512, 0, 512, 512, 1536, 512);
            }
        }
        g.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0x3A04090C);
    }

    /** 原作航路是白色虚线；这里按 7px 步长画 4px 线段。 */
    private static void dashedLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        if (len < 1.0D) {
            return;
        }
        for (double t = 0; t < len; t += 7.0D) {
            double t1 = Math.min(len, t + 4.0D);
            int ax = (int) Math.round(x0 + (x1 - x0) * t / len);
            int ay = (int) Math.round(y0 + (y1 - y0) * t / len);
            int bx = (int) Math.round(x0 + (x1 - x0) * t1 / len);
            int by = (int) Math.round(y0 + (y1 - y0) * t1 / len);
            line(g, ax, ay, bx, by, color);
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
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.translatable("campaign.barotrauma.adjacent_only"), true);
                }
            }
            // 任务勾选
            if (mx >= panelX && mx < width - 10 && my >= bodyY && my < bodyBottom && !pending
                    && CampaignMap.isValid(selected)) {
                int yy = bodyY - scroll;
                yy += font.split(tr("route", nodeName(CampaignMap.node(origin)),
                        nodeName(CampaignMap.node(selected))), panelW - 16).size() * 10 + 6;
                yy += font.split(tr("missions_accepted", checked.size(), CampaignMission.LIMIT),
                        panelW - 16).size() * 10 + 4;
                for (Mission mission : available()) {
                    int hh = font.split(tr("mission_line", mission.id, missionName(mission),
                            missionReward(mission)), panelW - 31).size() * 10;
                    if (my >= yy && my < yy + hh + 2) {
                        if (!checked.remove(mission.id)) {
                            if (checked.size() < CampaignMission.LIMIT) {
                                checked.add(mission.id);
                            } else if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.translatable("campaign.barotrauma.mission_limit",
                                                CampaignMission.LIMIT), true);
                            }
                        }
                        dive.active = canDive();
                        return true;
                    }
                    yy += hh + 4;
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
