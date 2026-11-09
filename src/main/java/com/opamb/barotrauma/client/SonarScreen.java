package com.opamb.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.opamb.barotrauma.campaign.CampaignMap;
import com.opamb.barotrauma.campaign.RoundPhase;
import com.opamb.barotrauma.menu.SonarMenu;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipActionPacket;
import com.opamb.barotrauma.network.SonarPingPacket;
import com.opamb.barotrauma.sonar.ClientSonarCache;
import com.opamb.barotrauma.sonar.SonarScan;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * 声纳 / 操舵台。
 *
 * <p>版式按原作《潜渊症》的操舵终端重排（2026-11-08）：</p>
 * <ul>
 *   <li><b>左：舰况 + 通讯</b>——上半是艇体侧视示意图（船壳点 + 各舱室进水高度），
 *       下半是通讯/状态栏；</li>
 *   <li><b>中：圆形声纳</b>——脉冲从圆心向外扩散，推过的地方才亮；单击画目标点；</li>
 *   <li><b>右上：声纳设置</b>（被动/主动/定向、量程）；<b>右中：操舵</b>（手动/自动）；</li>
 *   <li><b>右下：圆形「离港 / 对接」按钮</b>——停靠时按「离港」直接打开海图选任务；
 *       出海后靠近哨站变成「对接」，把物理船吸回码头并拆回方块船。</li>
 * </ul>
 */
public class SonarScreen extends AbstractContainerScreen<SonarMenu> {

    // ---- 版式 ----
    private static final int PANEL_W = 620;
    private static final int PANEL_H = 340;
    private static final int SCOPE_CX = 310;
    private static final int SCOPE_CY = 172;
    private static final int SCOPE_R = 112;
    private static final int FIELD_R = 88;

    private static final int LEFT_X = 10;
    private static final int LEFT_W = 176;
    private static final int STATUS_Y = 26;
    private static final int STATUS_H = 190;
    private static final int LOG_Y = 222;
    private static final int LOG_H = 92;

    private static final int COL_X = 434;
    private static final int COL_W = 176;

    // ---- 翻页页签 ----
    private static final int TAB_W = 52;
    private static final int TAB_H = 14;
    private static final int TAB_Y = 6;
    /** 第 0 页是声纳（默认），第 1 页是核心。 */
    private int page;

    // 控制盒的相对位置
    private static final int SONAR_BOX_Y = 26;
    private static final int SONAR_BOX_H = 88;
    private static final int HELM_BOX_Y = 120;
    private static final int HELM_BOX_H = 132;
    private static final int DOCK_CX = COL_X + COL_W / 2;
    private static final int DOCK_CY = 292;
    private static final int DOCK_R = 32;

    /** 一次声纳脉冲由内向外扩散的周期（毫秒）。 */
    private static final float PING_MS = 2600.0F;

    // ---- 配色 ----
    private static final int PANEL_BG = 0xF01E2420;
    private static final int PANEL_EDGE = 0xFF3B463C;
    private static final int BOX_BG = 0xFF2A322C;
    private static final int BOX_EDGE = 0xFF4A554B;
    private static final int TEXT = 0xFFD8D2BF;
    private static final int DIM = 0xFF8B9184;
    private static final int ACCENT = 0xFFE0762F;
    private static final int LED = 0xFF7BE38B;
    private static final int SCOPE_BG = 0xFF05090A;
    private static final int SCOPE_RIM = 0xFF3A4238;
    private static final int BRASS = 0xFF7E5B36;
    private static final int TARGET_COLOR = 0xFFFFB03A;
    private static final int BTN_BG = 0xFF232B25;
    private static final int BTN_ON = 0xFF3F5A40;
    private static final int BANNER_BG = 0xFF3B3222;
    private static final int BANNER_EDGE = 0xFFC3AD72;
    private static final int BANNER_TEXT = 0xFFEDE1BE;
    private static final int WATER_BLUE = 0xCC2E7FA8;

    private float camYaw;
    private float camPitch = 0.34F;

    private int range;
    private int pingCooldown;
    private long openedAt;

    private boolean orbiting;
    private boolean draggingZoom;
    private boolean synced;
    private double lastMouseX;
    private double lastMouseY;
    private double pressX;
    private double pressY;
    private boolean draggedFarEnough;

    private int headingX;
    private int headingY;
    private int headingZ;
    private int power = 100;
    private boolean autoMode;
    private SonarScan.Mode mode = SonarScan.Mode.ACTIVE;

    /** 本地先显示的标记点（相对玩家），服务端回报到达后以回报为准。 */
    private boolean localTarget;
    private int localTargetX;
    private int localTargetY;
    private int localTargetZ;

    // ---- 右下圆形按钮的四种状态 ----
    private static final int DOCK_NONE = 0;
    private static final int DOCK_READY = 1;
    private static final int DOCK_FAR = 2;
    private static final int DOCK_DEPART = 3;
    private static final int DOCK_LAUNCH = 4;

    public SonarScreen(SonarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.range = menu.range();
    }

    @Override
    protected void init() {
        super.init();
        ClientSonarCache.clear();
        openedAt = System.currentTimeMillis();
        sendPing();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        pollKeys();
        if (--pingCooldown <= 0) {
            sendPing();
        }
    }

    private void sendPing() {
        double cp = Math.cos(camPitch);
        ModNetwork.send(new SonarPingPacket(menu.sonarPos(), range, mode.id(),
                (float) (Math.sin(camYaw) * cp), (float) Math.sin(camPitch), (float) (Math.cos(camYaw) * cp)));
        pingCooldown = 20;
    }

    // ------------------------------------------------------------------ 键盘（手动模式）

    private void pollKeys() {
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }
        long window = minecraft.getWindow().getWindow();
        boolean forward = down(window, GLFW.GLFW_KEY_W) || down(window, GLFW.GLFW_KEY_UP);
        boolean back = down(window, GLFW.GLFW_KEY_S) || down(window, GLFW.GLFW_KEY_DOWN);
        boolean left = down(window, GLFW.GLFW_KEY_A) || down(window, GLFW.GLFW_KEY_LEFT);
        boolean right = down(window, GLFW.GLFW_KEY_D) || down(window, GLFW.GLFW_KEY_RIGHT);
        boolean up = down(window, GLFW.GLFW_KEY_SPACE);
        boolean sink = down(window, GLFW.GLFW_KEY_LEFT_SHIFT) || down(window, GLFW.GLFW_KEY_RIGHT_SHIFT);

        if (autoMode) {
            if (headingX != 0 || headingY != 0 || headingZ != 0) {
                headingX = headingY = headingZ = 0;
                sendHeading(0, 0, 0);
            }
            return;
        }

        float fwd = (forward ? 1 : 0) - (back ? 1 : 0);
        float strafe = (right ? 1 : 0) - (left ? 1 : 0);
        int dy = (up ? 1 : 0) - (sink ? 1 : 0);

        int dx;
        int dz;
        if (fwd == 0.0F && strafe == 0.0F) {
            dx = 0;
            dz = 0;
        } else {
            float sin = Mth.sin(camYaw);
            float cos = Mth.cos(camYaw);
            float wx = sin * fwd + (-cos) * strafe;
            float wz = cos * fwd + sin * strafe;
            dx = Math.abs(wx) > 0.38F ? (wx > 0 ? 1 : -1) : 0;
            dz = Math.abs(wz) > 0.38F ? (wz > 0 ? 1 : -1) : 0;
        }

        if (dx != headingX || dz != headingZ) {
            headingX = dx;
            headingZ = dz;
            sendHeading(dx, headingY, dz);
        }
        if (dy != headingY) {
            headingY = dy;
            sendHeading(headingX, dy, headingZ);
        }
    }

    private static boolean down(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }

    private void sendHeading(int x, int y, int z) {
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.SET_HEADING, x, y, z));
    }

    @Override
    public void onClose() {
        if (!autoMode && (headingX != 0 || headingY != 0 || headingZ != 0)) {
            sendHeading(0, 0, 0);
        }
        super.onClose();
    }

    // ------------------------------------------------------------------ 背景

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xB0020506);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL_BG);
        graphics.fill(x, y, x + PANEL_W, y + 1, PANEL_EDGE);
        graphics.fill(x, y + PANEL_H - 1, x + PANEL_W, y + PANEL_H, PANEL_EDGE);
        graphics.fill(x, y, x + 1, y + PANEL_H, PANEL_EDGE);
        graphics.fill(x + PANEL_W - 1, y, x + PANEL_W, y + PANEL_H, PANEL_EDGE);
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!synced && ClientSonarCache.frame() != null) {
            power = ClientSonarCache.power();
            autoMode = ClientSonarCache.autopilot();
            headingX = ClientSonarCache.headingX();
            headingY = ClientSonarCache.headingY();
            headingZ = ClientSonarCache.headingZ();
            synced = true;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        drawBanner(graphics);
        if (page == 0) {
            drawScope(graphics, mouseX, mouseY);
            drawStatusPanel(graphics);
            drawLogPanel(graphics);
            drawSonarBox(graphics);
            drawHelmBox(graphics, mouseX, mouseY);
            drawDockButton(graphics, mouseX, mouseY);
            drawBottomHint(graphics);
        } else {
            drawCorePage(graphics);
        }
        drawTabs(graphics, mouseX, mouseY);
    }

    // ---------------------------------------------------------- 顶部横幅 / 页签

    /** 原作顶部那条任务横幅：停靠时显示「选择任务并离开 <站点>」，航行中显示目的地。 */
    private void drawBanner(GuiGraphics graphics) {
        String text = "声纳 / 操舵台";
        var state = CampaignClient.state();
        if (state != null && state.phase() == RoundPhase.AT_NODE && CampaignClient.available()) {
            text = "选择任务并离开  " + nodeName(state.current());
        } else if (state != null && state.phase() != RoundPhase.AT_NODE) {
            text = "航行中 · 前往  " + nodeName(state.destination());
        }
        int w = 300;
        int x = leftPos + SCOPE_CX - w / 2;
        int y = topPos + 5;
        graphics.fill(x, y, x + w, y + 17, BANNER_BG);
        graphics.fill(x, y, x + w, y + 1, BANNER_EDGE);
        graphics.fill(x, y + 16, x + w, y + 17, BANNER_EDGE);
        // 两端的菱形装饰，和原作的站点旗标一个意思
        graphics.fill(x + 5, y + 7, x + 9, y + 11, BANNER_EDGE);
        graphics.fill(x + w - 9, y + 7, x + w - 5, y + 11, BANNER_EDGE);
        graphics.drawCenteredString(font, text, x + w / 2, y + 5, BANNER_TEXT);
    }

    private static String nodeName(int id) {
        var node = CampaignMap.node(id);
        return node == null ? "#" + id : node.name();
    }

    /** 左上两个页签：声纳 / 核心。 */
    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + LEFT_X;
        int y = topPos + TAB_Y;
        String[] labels = {"声纳", "核心"};
        for (int i = 0; i < labels.length; i++) {
            boolean active = page == i;
            boolean hover = mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H;
            graphics.fill(x, y, x + TAB_W, y + TAB_H, active ? BTN_ON : (hover ? 0xFF39443B : BTN_BG));
            graphics.fill(x, y + TAB_H - 1, x + TAB_W, y + TAB_H + 1, active ? ACCENT : BOX_EDGE);
            graphics.drawCenteredString(font, labels[i], x + TAB_W / 2, y + 3,
                    active ? TEXT : DIM);
            x += TAB_W + 2;
        }
    }

    /**
     * 核心页：声纳台同时就是船核心，这一页讲这艘船本身。
     *
     * <p>内容由服务端拼成多行文本随声纳数据包一起下来（船体扫描 + 机器清单 + 实时状态），
     * 客户端只负责换行和裁剪，避免把一堆数值字段再各开一条协议。</p>
     */
    private void drawCorePage(GuiGraphics graphics) {
        int x = leftPos + LEFT_X;
        int y = topPos + STATUS_Y;
        int w = COL_X + COL_W - LEFT_X;
        int h = PANEL_H - STATUS_Y - 22;
        graphics.fill(x, y, x + w, y + h, BOX_BG);
        graphics.fill(x, y, x + w, y + 1, BOX_EDGE);
        graphics.drawString(font, "船 核 心", x + 8, y + 6, ACCENT, false);

        String core = ClientSonarCache.core();
        if (core == null || core.isEmpty()) {
            graphics.drawString(font, "等待核心数据…", x + 8, y + 22, DIM, false);
            return;
        }
        int lineY = y + 22;
        int bottom = y + h - 6;
        for (String line : core.split("\n")) {
            if (lineY > bottom) {
                graphics.drawString(font, "…", x + 8, lineY, DIM, false);
                break;
            }
            graphics.drawString(font, line, x + 8, lineY, TEXT, false);
            lineY += 11;
        }
    }

    // ---------------------------------------------------------- 左：舰况

    /** 左舷舰况：艇体侧视示意图 + 船壳/进水条 + 深度航速。 */
    private void drawStatusPanel(GuiGraphics graphics) {
        int x = leftPos + LEFT_X;
        int y = topPos + STATUS_Y;
        box(graphics, x, y, LEFT_W, STATUS_H);
        graphics.drawString(font, "舰 况", x + 8, y + 6, ACCENT, false);
        drawShipDiagram(graphics, x + 8, y + 18, LEFT_W - 16, 78);

        int barY = y + 104;
        bar(graphics, x + 8, barY, LEFT_W - 16, "船壳", ClientSonarCache.hullPercent(),
                ClientSonarCache.hullPercent() > 60 ? LED : ClientSonarCache.hullPercent() > 30
                        ? TARGET_COLOR : 0xFFFF5A4A);
        bar(graphics, x + 8, barY + 22, LEFT_W - 16, "进水", ClientSonarCache.waterPercent(),
                ClientSonarCache.waterPercent() > 0 ? 0xFF4FA8D8 : LED);

        int lineY = barY + 46;
        graphics.drawString(font, String.format("深度  %d m", ClientSonarCache.depth()),
                x + 8, lineY, LED, false);
        graphics.drawString(font, String.format("航速  %.1f km/h", ClientSonarCache.speedTenths() / 10.0D),
                x + 8, lineY + 12, TEXT, false);
        int dive = ClientSonarCache.diveTenths();
        graphics.drawString(font, String.format("垂速  %s%.1f km/h", dive < 0 ? "↓ " : dive > 0 ? "↑ " : "",
                Math.abs(dive) / 10.0D), x + 8, lineY + 24, TEXT, false);
    }

    /** 标签 + 百分比 + 进度条。 */
    private void bar(GuiGraphics graphics, int x, int y, int w, String label, int percent, int color) {
        graphics.drawString(font, label, x, y, DIM, false);
        String value = percent + "%";
        graphics.drawString(font, value, x + w - font.width(value), y, color, false);
        graphics.fill(x, y + 11, x + w, y + 17, 0xFF141A16);
        int filled = x + Math.round(w * Mth.clamp(percent / 100.0F, 0.0F, 1.0F));
        graphics.fill(x, y + 11, filled, y + 17, color);
    }

    /**
     * 艇体侧视示意图：点在声纳帧里本来就是船体方块（TYPE_HULL），
     * 减去核心偏移就回到船体局部坐标；舱室矩形由服务端随包给，按进水比例灌蓝。
     */
    private void drawShipDiagram(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0xFF07100D);
        graphics.fill(x, y, x + w, y + 1, 0xFF16241E);
        int[] rooms = ClientSonarCache.rooms();
        if (rooms.length < 5) {
            graphics.drawCenteredString(font, "无船体数据", x + w / 2, y + h / 2 - 4, DIM);
            return;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i + 4 < rooms.length; i += 5) {
            minX = Math.min(minX, rooms[i]);
            maxX = Math.max(maxX, rooms[i + 1]);
            minY = Math.min(minY, rooms[i + 2]);
            maxY = Math.max(maxY, rooms[i + 3]);
        }
        if (minX > maxX || minY > maxY) {
            return;
        }
        float spanX = Math.max(1, maxX - minX + 1);
        float spanY = Math.max(1, maxY - minY + 1);
        float scale = Math.min((w - 8) / spanX, (h - 8) / spanY);
        int ox = x + Math.round((w - spanX * scale) / 2.0F);
        int oy = y + Math.round((h - spanY * scale) / 2.0F);

        // 船壳点：只画在示意图范围里，别把旁边的地形/其它船算进来
        SonarScan.Frame frame = ClientSonarCache.frame();
        if (frame != null) {
            int cdx = ClientSonarCache.coreDX();
            int cdy = ClientSonarCache.coreDY();
            for (int point : frame.points()) {
                if (SonarScan.type(point) != SonarScan.TYPE_HULL) {
                    continue;
                }
                int lx = SonarScan.x(point) - cdx;
                int ly = SonarScan.y(point) - cdy;
                if (lx < minX - 2 || lx > maxX + 2 || ly < minY - 2 || ly > maxY + 2) {
                    continue;
                }
                int px = ox + Math.round((lx - minX) * scale);
                int py = oy + Math.round((maxY - ly) * scale);
                graphics.fill(px, py, px + 2, py + 2, 0xFF6FA98B);
            }
        }

        // 舱室：边框 + 从舱底往上灌的水
        for (int i = 0; i + 4 < rooms.length; i += 5) {
            int x0 = ox + Math.round((rooms[i] - minX) * scale);
            int x1 = ox + Math.round((rooms[i + 1] + 1 - minX) * scale);
            int yTop = oy + Math.round((maxY - (rooms[i + 3] + 1)) * scale);
            int yBottom = oy + Math.round((maxY - rooms[i + 2]) * scale);
            int water = Mth.clamp(rooms[i + 4], 0, 100);
            if (water > 0) {
                int fillTop = yBottom - Math.round((yBottom - yTop) * water / 100.0F);
                graphics.fill(x0, fillTop, x1, yBottom, WATER_BLUE);
            }
            frameRect(graphics, x0, yTop, x1, yBottom, 0xFF3C6A55);
        }
    }

    private static void frameRect(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    // ---------------------------------------------------------- 左：通讯/状态

    private void drawLogPanel(GuiGraphics graphics) {
        int x = leftPos + LEFT_X;
        int y = topPos + LOG_Y;
        box(graphics, x, y, LEFT_W, LOG_H);
        graphics.drawString(font, "通 讯", x + 8, y + 6, ACCENT, false);

        int lineY = y + 20;
        for (String line : ClientSonarCache.status().split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            graphics.drawString(font, line, x + 8, lineY, TEXT, false);
            lineY += 11;
        }

        // 对接/任务提示：停靠时告诉玩家右下按钮会打开海图
        String hint = dockHint();
        if (hint != null && lineY <= y + LOG_H - 12) {
            graphics.drawString(font, hint, x + 8, lineY, TARGET_COLOR, false);
        }
    }

    private String dockHint() {
        int state = ClientSonarCache.dockState();
        String label = ClientSonarCache.dockLabel();
        if (state == 2 && CampaignClient.available()) {
            return "已停靠 " + label + " · 离港选任务";
        }
        if (state == 2) {
            return "已停靠 " + label;
        }
        if (state == 1) {
            return "已进入 " + label + " 对接口范围";
        }
        if (ClientSonarCache.dockDistance() >= 0) {
            return "距 " + label + " 对接口 " + ClientSonarCache.dockDistance() + " 格";
        }
        return null;
    }

    // ---------------------------------------------------------- 中：声纳

    private void drawScope(GuiGraphics graphics, int mouseX, int mouseY) {
        int cx = leftPos + SCOPE_CX;
        int cy = topPos + SCOPE_CY;

        disc(graphics, cx, cy, SCOPE_R, SCOPE_RIM);
        // 黄铜表圈：一圈刻度 + 两道内圈，原作的声纳就是这种老式雷达镜面
        ring(graphics, cx, cy, SCOPE_R - 3, 0xFF4A3B26);
        for (int deg = 0; deg < 360; deg += 15) {
            int inner = SCOPE_R - (deg % 90 == 0 ? 11 : 5);
            radial(graphics, cx, cy, inner, SCOPE_R - 2, deg, BRASS);
        }
        disc(graphics, cx, cy, SCOPE_R - 12, 0xFF121815);
        disc(graphics, cx, cy, FIELD_R + 2, 0xFF2C3329);
        disc(graphics, cx, cy, FIELD_R, SCOPE_BG);
        ring(graphics, cx, cy, FIELD_R / 2, 0xFF16302A);

        SonarScan.Frame frame = ClientSonarCache.frame();
        int viewRange = frame == null ? range : frame.range();
        if (frame != null) {
            drawCloud(graphics, cx, cy, frame, viewRange);
        }
        if (mode != SonarScan.Mode.PASSIVE) {
            drawWavefront(graphics, cx, cy);
        }
        if (frame == null) {
            graphics.drawCenteredString(font, "扫描中…", cx, cy - 4, DIM);
        }

        drawHeadingArrow(graphics, cx, cy);
        drawTargetMarker(graphics, cx, cy, viewRange);
        drawTargetPreview(graphics, cx, cy, mouseX, mouseY, viewRange);
        drawWaypoint(graphics, cx, cy, viewRange);
        drawBearings(graphics, cx, cy);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFD8FFE8);

        String headingText = headingX == 0 && headingZ == 0
                ? (autoMode ? "自动·无航向" : "停车")
                : headingName(headingX, headingY, headingZ) + " " + String.format("%03d°",
                        bearingDegrees(headingX, headingZ));
        graphics.drawString(font, headingText, cx - font.width(headingText) / 2, cy + SCOPE_R + 4,
                autoMode ? TARGET_COLOR : 0xFF9FE8C8, false);
        if (ClientSonarCache.hasWaypoint()) {
            int wx = ClientSonarCache.waypointX();
            int wy = ClientSonarCache.waypointY();
            int wz = ClientSonarCache.waypointZ();
            double dist = Math.sqrt((double) wx * wx + (double) wy * wy + (double) wz * wz);
            String text = String.format("目标 %s  %03d°  %.0f m", ClientSonarCache.waypointLabel(),
                    bearingDegrees(wx, wz), dist);
            graphics.drawString(font, text, cx - font.width(text) / 2, cy + SCOPE_R + 16,
                    0xFFFFD24A, false);
        }
    }

    /** 东南西北四个方位字，跟着视角转——转完也知道哪边是哪儿。 */
    private void drawBearings(GuiGraphics graphics, int cx, int cy) {
        float[] b = basis();
        float rx = b[3], rz = b[5], ux = b[6], uz = b[8];
        String[] names = {"北", "东", "南", "西"};
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            float sr = dirs[i][0] * rx + dirs[i][1] * rz;
            float su = dirs[i][0] * ux + dirs[i][1] * uz;
            double len = Math.sqrt((double) sr * sr + (double) su * su);
            if (len < 0.15D) {
                continue;
            }
            int px = cx + (int) Math.round(sr / len * (FIELD_R + 11));
            int py = cy - (int) Math.round(su / len * (FIELD_R + 11));
            graphics.drawString(font, names[i], px - font.width(names[i]) / 2, py - 4,
                    i == 0 ? ACCENT : 0xFF4E6A52, false);
        }
    }

    /** 相机基向量：前 / 右 / 上。right = forward × up，取反整个画面会左右镜像。 */
    private float[] basis() {
        float cp = Mth.cos(camPitch);
        float sp = Mth.sin(camPitch);
        float cosYaw = Mth.cos(camYaw);
        float sinYaw = Mth.sin(camYaw);
        return new float[]{
                sinYaw * cp, sp, cosYaw * cp,
                -cosYaw, 0.0F, sinYaw,
                -sp * sinYaw, cp, -sp * cosYaw
        };
    }

    private void drawCloud(GuiGraphics graphics, int cx, int cy, SonarScan.Frame frame, int viewRange) {
        float[] b = basis();
        float fx = b[0], fy = b[1], fz = b[2];
        float rx = b[3], rz = b[5];
        float ux = b[6], uy = b[7], uz = b[8];

        float camDistance = viewRange * 1.5F;
        float focal = FIELD_R * 0.95F / 0.57735F;
        float max = Math.max(1.0F, frame.range());
        int fieldSq = FIELD_R * FIELD_R;

        // 声纳是"发一炮、等回声"：亮点从圆心往外推，推过的地方才亮
        float wavefront = pingProgress();

        for (int point : frame.points()) {
            int dx = SonarScan.x(point);
            int dy = SonarScan.y(point);
            int dz = SonarScan.z(point);
            int type = SonarScan.type(point);

            float depth = dx * fx + dy * fy + dz * fz + camDistance;
            if (depth < 2.0F) {
                continue;
            }
            if (type == SonarScan.TYPE_TERRAIN && depth < camDistance) {
                continue;
            }
            int px = cx + Math.round((dx * rx + dz * rz) * focal / depth);
            int py = cy - Math.round((dx * ux + dy * uy + dz * uz) * focal / depth);
            int ox = px - cx;
            int oy = py - cy;
            if (ox * ox + oy * oy > fieldSq) {
                continue;
            }

            float reveal = 1.0F;
            if (type == SonarScan.TYPE_TERRAIN) {
                // 屏幕半径 = 这条回声走了多远
                float sr = (float) Math.sqrt((double) (ox * ox + oy * oy)) / FIELD_R;
                float age = wavefront - sr;
                if (age < 0.0F) {
                    // 这一轮波前还没推到这里：保留上一次探测的暗底，不要清屏。
                    // 清屏的话每次脉冲都黑一下，看起来像"重新探测"，实际是同一份数据在刷新。
                    reveal = 0.30F;
                } else {
                    // 波前刚推过最亮，之后慢慢回落到暗底
                    reveal = (float) (0.45D + 0.55D * Math.exp(-age * 1.8D));
                }
            }

            float distance = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            float t = Mth.clamp(distance / max, 0.0F, 1.0F);
            // 点画大一点：1 像素在深色底上几乎看不见，糊成一片就没法判断形状
            int size = distance < max * 0.3F ? 3 : 2;
            graphics.fill(px, py, px + size, py + size, cloudColor(type, t, reveal));
        }
    }

    private static int cloudColor(int type, float t, float reveal) {
        if (type == SonarScan.TYPE_HULL) {
            return argb(210, (int) (60 + 60 * (1 - t)), (int) (170 + 80 * (1 - t)), (int) (130 + 60 * (1 - t)));
        }
        if (type == SonarScan.TYPE_CONTACT) {
            return argb(255, 255, 90, 74);
        }
        int alpha = (int) ((1.05F - 0.45F * t) * reveal * 255.0F);
        int r = (int) (30 + 80 * (1 - t));
        int g = (int) (225 - 90 * t);
        int b = (int) (232 - 20 * t);
        return argb(Mth.clamp(alpha, 0, 255), r, g, b);
    }

    /** 声纳脉冲的波前：从圆心往外推的几圈，越外越暗。 */
    private void drawWavefront(GuiGraphics graphics, int cx, int cy) {
        float wavefront = pingProgress();
        for (int trail = 3; trail >= 0; trail--) {
            float ratio = wavefront - trail * 0.035F;
            if (ratio <= 0.02F || ratio > 1.0F) {
                continue;
            }
            int alpha = trail == 0 ? 150 : 46 - trail * 11;
            if (alpha <= 0) {
                continue;
            }
            ring(graphics, cx, cy, Math.round(ratio * FIELD_R), argb(alpha, 120, 255, 200));
        }
    }

    /** 本次脉冲扩散到量程的百分之几（0..1）；打开界面就从圆心重新开始。 */
    private float pingProgress() {
        long elapsed = System.currentTimeMillis() - openedAt;
        if (elapsed < 0L) {
            return 0.0F;
        }
        return (float) (elapsed % (long) PING_MS) / PING_MS;
    }

    /** 圆心箭头：指向船实际在走的方向。 */
    private void drawHeadingArrow(GuiGraphics graphics, int cx, int cy) {
        if (headingX == 0 && headingY == 0 && headingZ == 0) {
            graphics.fill(cx - 6, cy - 6, cx - 4, cy + 6, DIM);
            graphics.fill(cx + 4, cy - 6, cx + 6, cy + 6, DIM);
            graphics.fill(cx - 6, cy - 6, cx + 6, cy - 4, DIM);
            graphics.fill(cx - 6, cy + 4, cx + 6, cy + 6, DIM);
            return;
        }
        float[] b = basis();
        float sxRight = headingX * b[3] + headingZ * b[5];
        float sxUp = headingX * b[6] + headingY * b[7] + headingZ * b[8];
        double len = Math.sqrt((double) sxRight * sxRight + (double) sxUp * sxUp);
        if (len < 0.15D) {
            int dir = headingY >= 0 ? -1 : 1;
            graphics.fill(cx - 5, cy + dir * 12, cx + 5, cy + dir * 14, ACCENT);
            graphics.fill(cx - 2, cy + dir * 16, cx + 2, cy + dir * 20, ACCENT);
            return;
        }
        double nx = sxRight / len;
        double ny = -sxUp / len;
        float reach = 26.0F + 30.0F * (power / 100.0F);
        int tipX = cx + (int) Math.round(nx * reach);
        int tipY = cy + (int) Math.round(ny * reach);
        line(graphics, cx, cy, tipX, tipY, ACCENT);
        line(graphics, cx, cy, tipX, tipY, 0xFFFFC08A);
        double px = -ny;
        double py = nx;
        line(graphics, tipX, tipY, tipX - (int) Math.round(nx * 7 + px * 5),
                tipY - (int) Math.round(ny * 7 + py * 5), ACCENT);
        line(graphics, tipX, tipY, tipX - (int) Math.round(nx * 7 - px * 5),
                tipY - (int) Math.round(ny * 7 - py * 5), ACCENT);
    }

    /** 下一个目标点（本轮任务的目的地前哨站）：金色菱形，超出量程就压在边缘上。 */
    private void drawWaypoint(GuiGraphics graphics, int cx, int cy, int viewRange) {
        if (!ClientSonarCache.hasWaypoint()) {
            return;
        }
        int tx = ClientSonarCache.waypointX();
        int ty = ClientSonarCache.waypointY();
        int tz = ClientSonarCache.waypointZ();
        float[] b = basis();
        float depth = tx * b[0] + ty * b[1] + tz * b[2] + camDistanceOf(viewRange);
        if (depth < 2.0F) {
            return;
        }
        float focal = FIELD_R * 0.95F / 0.57735F;
        int px = cx + Math.round((tx * b[3] + tz * b[5]) * focal / depth);
        int py = cy - Math.round((tx * b[6] + ty * b[7] + tz * b[8]) * focal / depth);
        int ox = px - cx;
        int oy = py - cy;
        if (ox * ox + oy * oy > FIELD_R * FIELD_R) {
            // 目标在量程外：贴在圆周上，给个"就在那边"的提示
            double len = Math.sqrt((double) ox * ox + (double) oy * oy);
            int ex = cx + (int) Math.round(ox / len * (FIELD_R - 6));
            int ey = cy + (int) Math.round(oy / len * (FIELD_R - 6));
            diamond(graphics, ex, ey, 3, 0xFFFFD24A);
            return;
        }
        line(graphics, cx, cy, px, py, 0x55FFD24A);
        diamond(graphics, px, py, 6, 0xFFFFD24A);
        graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFF0B8);
        String label = ClientSonarCache.waypointLabel();
        if (!label.isEmpty() && px + 9 + font.width(label) <= cx + FIELD_R) {
            graphics.drawString(font, label, px + 9, py - 3, 0xFFFFD24A, false);
        }
    }

    /** 菱形标记（四段直线，金色目标点用）。 */
    private static void diamond(GuiGraphics graphics, int cx, int cy, int r, int color) {
        line(graphics, cx, cy - r, cx + r, cy, color);
        line(graphics, cx + r, cy, cx, cy + r, color);
        line(graphics, cx, cy + r, cx - r, cy, color);
        line(graphics, cx - r, cy, cx, cy - r, color);
    }

    /** 自动驾驶目标点标记。 */
    private void drawTargetMarker(GuiGraphics graphics, int cx, int cy, int viewRange) {
        boolean has = ClientSonarCache.hasTarget() || localTarget;
        if (!has) {
            return;
        }
        int tx = ClientSonarCache.hasTarget() ? ClientSonarCache.targetX() : localTargetX;
        int ty = ClientSonarCache.hasTarget() ? ClientSonarCache.targetY() : localTargetY;
        int tz = ClientSonarCache.hasTarget() ? ClientSonarCache.targetZ() : localTargetZ;

        float[] b = basis();
        float camDistance = viewRange * 1.5F;
        float focal = FIELD_R * 0.95F / 0.57735F;
        float depth = tx * b[0] + ty * b[1] + tz * b[2] + camDistance;
        if (depth < 2.0F) {
            return;
        }
        int px = cx + Math.round((tx * b[3] + tz * b[5]) * focal / depth);
        int py = cy - Math.round((tx * b[6] + ty * b[7] + tz * b[8]) * focal / depth);
        int ox = px - cx;
        int oy = py - cy;
        if (ox * ox + oy * oy > FIELD_R * FIELD_R) {
            double len = Math.sqrt((double) ox * ox + (double) oy * oy);
            int ex = cx + (int) Math.round(ox / len * (FIELD_R - 6));
            int ey = cy + (int) Math.round(oy / len * (FIELD_R - 6));
            graphics.fill(ex - 3, ey - 3, ex + 4, ey + 4, TARGET_COLOR);
            return;
        }
        line(graphics, cx, cy, px, py, 0x66FFB03A);
        graphics.fill(px - 5, py - 5, px - 3, py + 6, TARGET_COLOR);
        graphics.fill(px + 4, py - 5, px + 6, py + 6, TARGET_COLOR);
        graphics.fill(px - 5, py - 5, px + 6, py - 3, TARGET_COLOR);
        graphics.fill(px - 5, py + 4, px + 6, py + 6, TARGET_COLOR);
    }

    /**
     * 鼠标悬停预览：点下去之前就把目标位置画出来（虚线十字 + 距离），
     * 免得玩家“看不清会点到的位置”。
     */
    private void drawTargetPreview(GuiGraphics graphics, int cx, int cy,
                                   int mouseX, int mouseY, int viewRange) {
        if (page != 0 || orbiting) {
            return;
        }
        int dx = mouseX - cx;
        int dy = mouseY - cy;
        if (dx * dx + dy * dy > SCOPE_R * SCOPE_R) {
            return;
        }
        int[] target = computeTarget(mouseX, mouseY);
        if (target == null) {
            return;
        }
        float[] b = basis();
        float camDistance = camDistanceOf(viewRange);
        float focal = FIELD_R * 0.95F / 0.57735F;
        float depth = target[0] * b[0] + target[1] * b[1] + target[2] * b[2] + camDistance;
        if (depth < 2.0F) {
            return;
        }
        int px = cx + Math.round((target[0] * b[3] + target[2] * b[5]) * focal / depth);
        int py = cy - Math.round((target[0] * b[6] + target[1] * b[7] + target[2] * b[8]) * focal / depth);
        if ((long) (px - cx) * (px - cx) + (long) (py - cy) * (py - cy) > (long) FIELD_R * FIELD_R) {
            return;
        }
        line(graphics, cx, cy, px, py, 0x44FFFFFF);
        int color = 0xCCE8FFF2;
        int r = 8;
        // 四角括号
        line(graphics, px - r, py - r, px - 3, py - r, color);
        line(graphics, px - r, py - r, px - r, py - 3, color);
        line(graphics, px + 3, py - r, px + r, py - r, color);
        line(graphics, px + r, py - r, px + r, py - 3, color);
        line(graphics, px - r, py + 3, px - r, py + r, color);
        line(graphics, px - r, py + r, px - 3, py + r, color);
        line(graphics, px + 3, py + r, px + r, py + r, color);
        line(graphics, px + r, py + 3, px + r, py + r, color);
        double dist = Math.sqrt((double) target[0] * target[0]
                + (double) target[1] * target[1] + (double) target[2] * target[2]);
        String label = String.format("%.0f m", dist);
        graphics.drawString(font, label, px + 10, py + 2, color, false);
        if (dist > 0.5D) {
            graphics.drawString(font, "点击设为自动驾驶目标", px + 10, py + 12,
                    0x99E8FFF2, false);
        }
    }

    // ---------------------------------------------------------- 右上：声纳设置

    private void drawSonarBox(GuiGraphics graphics) {
        int x = leftPos + COL_X;
        int y = topPos + SONAR_BOX_Y;
        box(graphics, x, y, COL_W, SONAR_BOX_H);

        radio(graphics, x + 8, y + 8, "被动声纳", mode == SonarScan.Mode.PASSIVE);
        radio(graphics, x + 8, y + 22, "主动声纳", mode != SonarScan.Mode.PASSIVE);
        switchRow(graphics, x + 8, y + 38, "定向脉冲 " + (int) SonarScan.CONE_DEGREES + "°",
                mode == SonarScan.Mode.DIRECTIONAL);

        graphics.drawString(font, "量程", x + 8, y + 56, TEXT, false);
        String zoom = range + " m";
        graphics.drawString(font, zoom, x + COL_W - 8 - font.width(zoom), y + 56, LED, false);
        int barX = x + 8;
        int barW = COL_W - 16;
        int barY = y + 70;
        graphics.fill(barX, barY, barX + barW, barY + 6, 0xFF141A16);
        int knob = barX + Math.round(barW * (range - SonarScan.MIN_RANGE)
                / (float) (SonarScan.MAX_RANGE - SonarScan.MIN_RANGE));
        graphics.fill(barX, barY, knob, barY + 6, 0xFF4E6A52);
        graphics.fill(knob - 2, barY - 2, knob + 2, barY + 8, 0xFFC8CBB4);
    }

    // ---------------------------------------------------------- 右中：操舵

    private void drawHelmBox(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + COL_X;
        int y = topPos + HELM_BOX_Y;
        box(graphics, x, y, COL_W, HELM_BOX_H);

        radio(graphics, x + 8, y + 8, "手动驾驶", !autoMode);
        radio(graphics, x + 8, y + 22, "自动驾驶", autoMode);
        graphics.fill(x + 8, y + 36, x + COL_W - 8, y + 37, BOX_EDGE);

        if (autoMode) {
            drawAutoPanel(graphics, x, y, mouseX, mouseY);
        } else {
            drawManualPanel(graphics, x, y, mouseX, mouseY);
        }

        // 推力条（两种模式共用）
        graphics.drawString(font, "推力", x + 8, y + 108, DIM, false);
        int barX = x + 42;
        int barW = COL_W - 92;
        int barY = y + 111;
        graphics.fill(barX, barY, barX + barW, barY + 6, 0xFF141A16);
        int filled = barX + Math.round(barW * power / 100.0F);
        graphics.fill(barX, barY, filled, barY + 6, 0xFF1FD3B8);
        String pct = power + "%";
        graphics.drawString(font, pct, x + COL_W - 8 - font.width(pct), y + 108, LED, false);
    }

    private void drawManualPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        String[] labels = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        int[][] dirs = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
        for (int i = 0; i < 8; i++) {
            int bx = x + 8 + (i % 4) * 40;
            int by = y + 42 + (i / 4) * 18;
            boolean on = headingX == dirs[i][0] && headingZ == dirs[i][1];
            boolean hover = mouseX >= bx && mouseX < bx + 38 && mouseY >= by && mouseY < by + 16;
            graphics.fill(bx, by, bx + 38, by + 16, on ? BTN_ON : hover ? 0xFF39483A : BTN_BG);
            graphics.fill(bx, by, bx + 38, by + 1, BOX_EDGE);
            graphics.drawCenteredString(font, labels[i], bx + 19, by + 4, on ? 0xFFE8FFE8 : TEXT);
        }
        String[] vert = {"上浮", "停车", "下潜"};
        int[] values = {1, 0, -1};
        for (int i = 0; i < 3; i++) {
            int bx = x + 8 + i * 54;
            int by = y + 80;
            boolean on = values[i] == 0
                    ? (headingX == 0 && headingY == 0 && headingZ == 0)
                    : headingY == values[i];
            boolean hover = mouseX >= bx && mouseX < bx + 52 && mouseY >= by && mouseY < by + 16;
            graphics.fill(bx, by, bx + 52, by + 16, on ? BTN_ON : hover ? 0xFF39483A : BTN_BG);
            graphics.fill(bx, by, bx + 52, by + 1, BOX_EDGE);
            graphics.drawCenteredString(font, vert[i], bx + 26, by + 4, on ? 0xFFE8FFE8 : TEXT);
        }
        graphics.drawString(font, "WASD / 空格 / Shift", x + 8, y + 99, 0xFF5F6A5C, false);
    }

    private void drawAutoPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        boolean has = ClientSonarCache.hasTarget() || localTarget;
        String line1;
        if (!has) {
            line1 = "目标：未标记";
        } else {
            int tx = ClientSonarCache.hasTarget() ? ClientSonarCache.targetX() : localTargetX;
            int ty = ClientSonarCache.hasTarget() ? ClientSonarCache.targetY() : localTargetY;
            int tz = ClientSonarCache.hasTarget() ? ClientSonarCache.targetZ() : localTargetZ;
            double dist = Math.sqrt((double) tx * tx + (double) ty * ty + (double) tz * tz);
            line1 = String.format("目标 %s %03d°  %.0f m", headingName(tx, ty, tz), bearingDegrees(tx, tz), dist);
        }
        graphics.drawString(font, line1, x + 8, y + 44, has ? TARGET_COLOR : DIM, false);
        graphics.drawString(font, has ? "单击左侧画面可改标记" : "单击左侧画面设置目标",
                x + 8, y + 56, 0xFF5F6A5C, false);

        int bx = x + 8;
        int by = y + 70;
        boolean hover = mouseX >= bx && mouseX < bx + 120 && mouseY >= by && mouseY < by + 16;
        graphics.fill(bx, by, bx + 120, by + 16, hover ? 0xFF5A3A2A : 0xFF3A2A22);
        graphics.fill(bx, by, bx + 120, by + 1, BOX_EDGE);
        graphics.drawCenteredString(font, "取消标记", bx + 60, by + 4, TEXT);
    }

    // ---------------------------------------------------------- 右下：离港 / 对接

    /**
     * 右下圆形按钮，对应原作操舵终端右下那颗大红钮：
     * <ul>
     *   <li>方块船停在站里 + 战役在节点 → 「离港」，点一下直接打开 ~ 海图；</li>
     *   <li>物理船已进入对接口范围 → 「对接」，把船吸回码头拆成方块船；</li>
     *   <li>物理船离得远 → 「对接」压暗并显示距离；</li>
     *   <li>其它情况（非战役/非停靠）→ 保留「下潜」手动启动物理船。</li>
     * </ul>
     */
    private int dockMode() {
        int state = ClientSonarCache.dockState();
        boolean physical = ClientSonarCache.physical();
        if (physical) {
            return state == 1 ? DOCK_READY : DOCK_FAR;
        }
        if (state == 2) {
            return CampaignClient.available() ? DOCK_DEPART : DOCK_LAUNCH;
        }
        return DOCK_LAUNCH;
    }

    private void drawDockButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int cx = leftPos + DOCK_CX;
        int cy = topPos + DOCK_CY;
        int mode = dockMode();
        boolean enabled = mode != DOCK_FAR;
        boolean hover = enabled && inCircle(mouseX, mouseY, cx, cy, DOCK_R);

        // 外层金属圈
        disc(graphics, cx, cy, DOCK_R, 0xFF3B4143);
        disc(graphics, cx, cy, DOCK_R - 3, 0xFF1B2022);
        // 四段卡箍，和原作的机械钮一个味道
        for (int deg = 45; deg < 360; deg += 90) {
            radial(graphics, cx, cy, DOCK_R - 8, DOCK_R - 3, deg - 10, 0xFF6E5F45);
            radial(graphics, cx, cy, DOCK_R - 8, DOCK_R - 3, deg + 10, 0xFF6E5F45);
        }

        int inner;
        int labelColor = 0xFFF4EFE3;
        String label;
        String sub;
        switch (mode) {
            case DOCK_DEPART -> {
                inner = hover ? 0xFFC0362A : 0xFF9C2A20;
                label = "离港";
                sub = "打开海图";
            }
            case DOCK_READY -> {
                inner = hover ? 0xFF2F7A56 : 0xFF256044;
                label = "对接";
                sub = "已就位";
            }
            case DOCK_FAR -> {
                inner = 0xFF3A3F41;
                labelColor = 0xFF8B9184;
                label = "对接";
                sub = ClientSonarCache.dockDistance() >= 0
                        ? ClientSonarCache.dockDistance() + " 格" : "找哨站";
            }
            case DOCK_LAUNCH -> {
                inner = hover ? 0xFF4A5E3F : 0xFF3A4A32;
                label = "下潜";
                sub = "启动物理船";
            }
            default -> {
                inner = 0xFF3A3F41;
                label = "—";
                sub = "";
            }
        }
        disc(graphics, cx, cy, DOCK_R - 9, inner);
        // 高光：上半圈亮一点，不会像纯色圆盘
        disc(graphics, cx, cy - 5, DOCK_R - 15, argb(40, 255, 255, 255));
        graphics.drawCenteredString(font, label, cx, cy - 9, labelColor);
        String subText = sub == null ? "" : sub;
        if (!subText.isEmpty()) {
            graphics.drawCenteredString(font, subText, cx, cy + 1,
                    enabled ? 0xFFE8DDB5 : 0xFF9BA096);
        }
    }

    // ---------------------------------------------------------- 底部提示

    private void drawBottomHint(GuiGraphics graphics) {
        String hint = "拖拽转视角 · 滚轮调速 · 单击画面标记目标";
        graphics.drawString(font, hint, leftPos + LEFT_X, topPos + PANEL_H - 16, 0xFF5F6A5C, false);
    }

    // ---------------------------------------------------------- 小工具

    private void box(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, BOX_BG);
        graphics.fill(x, y, x + w, y + 1, BOX_EDGE);
        graphics.fill(x, y + h - 1, x + w, y + h, BOX_EDGE);
        graphics.fill(x, y, x + 1, y + h, BOX_EDGE);
        graphics.fill(x + w - 1, y, x + w, y + h, BOX_EDGE);
    }

    private void radio(GuiGraphics graphics, int x, int y, String label, boolean selected) {
        graphics.fill(x, y, x + 8, y + 8, 0xFF11160F);
        if (selected) {
            graphics.fill(x + 2, y + 2, x + 6, y + 6, ACCENT);
        }
        graphics.drawString(font, label, x + 13, y, selected ? TEXT : DIM, false);
    }

    private void switchRow(GuiGraphics graphics, int x, int y, String label, boolean on) {
        graphics.fill(x, y, x + 22, y + 10, 0xFF11160F);
        graphics.fill(x + (on ? 12 : 1), y + 1, x + (on ? 21 : 10), y + 9,
                on ? ACCENT : 0xFF5A6256);
        graphics.drawString(font, label, x + 28, y + 1, on ? TEXT : DIM, false);
    }

    private static void disc(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** 空心圆：声纳波前和量程参考线都用它（不是实心盘，看着才像示波器）。 */
    private static void ring(GuiGraphics graphics, int cx, int cy, int r, int color) {
        if (r <= 0) {
            return;
        }
        int steps = Math.max(32, r * 6);
        for (int i = 0; i < steps; i++) {
            double angle = Math.PI * 2.0D * i / steps;
            int x = cx + (int) Math.round(Math.cos(angle) * r);
            int y = cy + (int) Math.round(Math.sin(angle) * r);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void radial(GuiGraphics graphics, int cx, int cy, int r0, int r1, float degrees, int color) {
        double rad = Math.toRadians(degrees);
        double sx = Math.sin(rad);
        double cyv = -Math.cos(rad);
        for (int r = r0; r <= r1; r++) {
            int x = cx + (int) Math.round(sx * r);
            int y = cy + (int) Math.round(cyv * r);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((Mth.clamp(r, 0, 255) & 0xFF) << 16)
                | ((Mth.clamp(g, 0, 255) & 0xFF) << 8) | (Mth.clamp(b, 0, 255) & 0xFF);
    }

    private static int bearingDegrees(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return 0;
        }
        int deg = (int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)));
        return (deg + 360) % 360;
    }

    private static String headingName(int dx, int dy, int dz) {
        String base;
        if (dz < 0) {
            base = dx == 0 ? "北" : dx > 0 ? "东北" : "西北";
        } else if (dz > 0) {
            base = dx == 0 ? "南" : dx > 0 ? "东南" : "西南";
        } else {
            base = dx > 0 ? "东" : "西";
        }
        if (dy > 0) {
            return base + "·上浮";
        }
        if (dy < 0) {
            return base + "·下潜";
        }
        return base;
    }

    // ---------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 页签优先：任何时候都能切页
        if (button == 0) {
            int tabX = leftPos + LEFT_X;
            int tabY = topPos + TAB_Y;
            if (mouseY >= tabY && mouseY < tabY + TAB_H) {
                for (int i = 0; i < 2; i++) {
                    int x0 = tabX + i * (TAB_W + 2);
                    if (mouseX >= x0 && mouseX < x0 + TAB_W) {
                        page = i;
                        return true;
                    }
                }
            }
        }
        // 核心页只展示数据，不响应示波器拖拽之类的操作
        if (page == 1) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int cx = leftPos + SCOPE_CX;
        int cy = topPos + SCOPE_CY;
        double ox = mouseX - cx;
        double oy = mouseY - cy;
        boolean inScope = ox * ox + oy * oy <= (double) SCOPE_R * SCOPE_R;

        if (button == 0 && inScope) {
            orbiting = true;
            pressX = mouseX;
            pressY = mouseY;
            draggedFarEnough = false;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // 右下圆形按钮：离港 / 对接 / 下潜
        if (inCircle(mouseX, mouseY, leftPos + DOCK_CX, topPos + DOCK_CY, DOCK_R)) {
            clickDockButton();
            return true;
        }

        int x = leftPos + COL_X;
        int sy = topPos + SONAR_BOX_Y;
        if (inRect(mouseX, mouseY, x + 4, sy + 6, 150, 14)) {
            setMode(SonarScan.Mode.PASSIVE);
            return true;
        }
        if (inRect(mouseX, mouseY, x + 4, sy + 20, 150, 14)) {
            setMode(SonarScan.Mode.ACTIVE);
            return true;
        }
        if (inRect(mouseX, mouseY, x + 4, sy + 36, 160, 14)) {
            setMode(mode == SonarScan.Mode.DIRECTIONAL ? SonarScan.Mode.ACTIVE : SonarScan.Mode.DIRECTIONAL);
            return true;
        }
        if (inRect(mouseX, mouseY, x + 4, sy + 66, COL_W - 8, 14)) {
            draggingZoom = true;
            applyZoom(mouseX);
            return true;
        }

        int hy = topPos + HELM_BOX_Y;
        if (inRect(mouseX, mouseY, x + 4, hy + 6, 160, 14)) {
            setAuto(false);
            return true;
        }
        if (inRect(mouseX, mouseY, x + 4, hy + 20, 160, 14)) {
            setAuto(true);
            return true;
        }
        if (inRect(mouseX, mouseY, x + 40, hy + 108, COL_W - 50, 14)) {
            applyPower(mouseX, x);
            return true;
        }
        if (autoMode) {
            if (inRect(mouseX, mouseY, x + 8, hy + 70, 120, 16)) {
                clearTarget();
                return true;
            }
        } else {
            for (int i = 0; i < 8; i++) {
                if (inRect(mouseX, mouseY, x + 8 + (i % 4) * 40, hy + 42 + (i / 4) * 18, 38, 16)) {
                    int[][] dirs = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
                    steer(dirs[i][0], headingY, dirs[i][1]);
                    return true;
                }
            }
            for (int i = 0; i < 3; i++) {
                if (inRect(mouseX, mouseY, x + 8 + i * 54, hy + 80, 52, 16)) {
                    int[] values = {1, 0, -1};
                    if (values[i] == 0) {
                        steer(0, 0, 0);
                    } else {
                        steer(headingX, values[i], headingZ);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 右下按钮：停靠时打开海图；在码头范围内对接；否则下潜。 */
    private void clickDockButton() {
        int mode = dockMode();
        switch (mode) {
            case DOCK_DEPART -> {
                if (CampaignClient.available() && minecraft != null) {
                    // 去海图前先把舵松开，别带着航向离开操舵台
                    if (!autoMode && (headingX != 0 || headingY != 0 || headingZ != 0)) {
                        headingX = headingY = headingZ = 0;
                        sendHeading(0, 0, 0);
                    }
                    minecraft.setScreen(new CampaignMapScreen());
                }
            }
            case DOCK_READY -> ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.DOCK));
            case DOCK_FAR -> {
                if (minecraft != null && minecraft.player != null) {
                    int distance = ClientSonarCache.dockDistance();
                    minecraft.player.displayClientMessage(Component.literal(distance >= 0
                            ? "离对接口还有 " + distance + " 格，开到码头附近再对接"
                            : "附近没有可对接的哨站"), true);
                }
            }
            case DOCK_LAUNCH -> ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.LAUNCH));
            default -> {
            }
        }
    }

    private static boolean inCircle(double mouseX, double mouseY, int cx, int cy, double radius) {
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingZoom) {
            applyZoom(mouseX);
            return true;
        }
        if (orbiting) {
            camYaw -= (float) (mouseX - lastMouseX) * 0.008F;
            camPitch = Mth.clamp(camPitch - (float) (mouseY - lastMouseY) * 0.008F, -1.45F, 1.45F);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 6) {
                draggedFarEnough = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // 单击 = 标记目标，两种模式都生效（手动模式下点一下就切自动，省得先去点单选）
        if (orbiting && !draggedFarEnough && button == 0) {
            markTargetAt(mouseX, mouseY);
        }
        orbiting = false;
        draggingZoom = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int cx = leftPos + SCOPE_CX;
        int cy = topPos + SCOPE_CY;
        double ox = mouseX - cx;
        double oy = mouseY - cy;
        if (ox * ox + oy * oy <= (double) SCOPE_R * SCOPE_R) {
            int next = Mth.clamp(power + (delta > 0 ? 10 : -10), 10, 100);
            if (next != power) {
                power = next;
                ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.SET_POWER, power));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 在示波器上点一下：沿视线找最近的声纳点当目标；没点中取视线与量程球的交点。 */
    private void markTargetAt(double mouseX, double mouseY) {
        int[] target = computeTarget(mouseX, mouseY);
        if (target == null) {
            return;
        }
        localTarget = true;
        localTargetX = target[0];
        localTargetY = target[1];
        localTargetZ = target[2];
        autoMode = true;
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.SET_AUTOPILOT_TARGET,
                target[0], target[1], target[2]));
        sendPing();
    }

    /**
     * 鼠标位置对应的自动驾驶目标（相对声纳中心）；视点方向退化时返回 null。
     *
     * <p>没命中地形时不再按相机距离随手给点，而是取视线与"以声纳中心为球心、
     * 量程为半径"的球的远交点——目标一定落在量程内，屏幕上的预览标记和实际
     * 目标也不会一个近一个远。</p>
     */
    private int[] computeTarget(double mouseX, double mouseY) {
        int cx = leftPos + SCOPE_CX;
        int cy = topPos + SCOPE_CY;
        float focal = FIELD_R * 0.95F / 0.57735F;

        float[] b = basis();
        float fx = b[0], fy = b[1], fz = b[2];
        float rx = b[3], ry = b[4], rz = b[5];
        float ux = b[6], uy = b[7], uz = b[8];

        float nx = (float) (mouseX - cx) / focal;
        float ny = (float) -(mouseY - cy) / focal;
        double dx = fx + rx * nx + ux * ny;
        double dy = fy + ry * nx + uy * ny;
        double dz = fz + rz * nx + uz * ny;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4D) {
            return null;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        SonarScan.Frame frame = ClientSonarCache.frame();
        int viewRange = frame == null ? range : frame.range();
        // 相机在球外 camDistance 处，画点云时也是这么算的。射线必须从这里出发，
        // 从球心出发的话，点到射线的距离会系统性偏大一个 camDistance，点击位置整个跑偏。
        double camDistance = camDistanceOf(viewRange);
        double camX = -fx * camDistance;
        double camY = -fy * camDistance;
        double camZ = -fz * camDistance;

        double bestDist = -1.0D;
        double bestOffset = 4.5D;
        if (frame != null) {
            for (int point : frame.points()) {
                if (SonarScan.type(point) != SonarScan.TYPE_TERRAIN) {
                    continue;
                }
                double px = SonarScan.x(point) - camX;
                double py = SonarScan.y(point) - camY;
                double pz = SonarScan.z(point) - camZ;
                double t = px * dx + py * dy + pz * dz;
                if (t <= 2.0D || t > viewRange * 2.5D) {
                    continue;
                }
                double qx = px - dx * t;
                double qy = py - dy * t;
                double qz = pz - dz * t;
                double off = Math.sqrt(qx * qx + qy * qy + qz * qz);
                if (off < bestOffset) {
                    bestOffset = off;
                    bestDist = t;
                }
            }
        }
        double dist;
        if (bestDist > 0.0D) {
            dist = bestDist;
        } else {
            // |O + t·d|² = R²，O = 相机位置，R = 量程；取远交点
            double bDot = camX * dx + camY * dy + camZ * dz;
            double c = camX * camX + camY * camY + camZ * camZ
                    - (double) viewRange * viewRange;
            double disc = bDot * bDot - c;
            dist = disc >= 0.0D ? -bDot + Math.sqrt(disc) : camDistance - viewRange * 0.5D;
            dist = Math.max(2.0D, dist);
        }
        return new int[]{
                (int) Math.round(camX + dx * dist),
                (int) Math.round(camY + dy * dist),
                (int) Math.round(camZ + dz * dist)};
    }

    /** 相机到球心的距离，画点云和反投影必须用同一个值。 */
    private static float camDistanceOf(int viewRange) {
        return viewRange * 1.5F;
    }

    private void clearTarget() {
        localTarget = false;
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.CLEAR_AUTOPILOT_TARGET));
        sendPing();
    }

    private void steer(int x, int y, int z) {
        if (x == headingX && y == headingY && z == headingZ) {
            return;
        }
        headingX = x;
        headingY = y;
        headingZ = z;
        sendHeading(x, y, z);
    }

    private void setAuto(boolean auto) {
        if (autoMode == auto) {
            return;
        }
        autoMode = auto;
        if (!auto) {
            headingX = headingY = headingZ = 0;
        }
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.SET_AUTOPILOT, auto ? 1 : 0));
        sendPing();
    }

    private void setMode(SonarScan.Mode next) {
        if (mode == next) {
            return;
        }
        mode = next;
        sendPing();
    }

    private void applyPower(double mouseX, int colX) {
        int barX = colX + 42;
        int barW = COL_W - 92;
        float ratio = Mth.clamp((float) ((mouseX - barX) / (double) barW), 0.0F, 1.0F);
        int next = Mth.clamp(Math.round(ratio * 100.0F / 5.0F) * 5, 10, 100);
        if (next == power) {
            return;
        }
        power = next;
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.SET_POWER, power));
    }

    private void applyZoom(double mouseX) {
        int barX = leftPos + COL_X + 8;
        int barW = COL_W - 16;
        float ratio = Mth.clamp((float) ((mouseX - barX) / (double) barW), 0.0F, 1.0F);
        int next = Math.round(SonarScan.MIN_RANGE + ratio * (SonarScan.MAX_RANGE - SonarScan.MIN_RANGE));
        next = Mth.clamp(Math.round(next / 8.0F) * 8, SonarScan.MIN_RANGE, SonarScan.MAX_RANGE);
        if (next != range) {
            range = next;
            sendPing();
        }
    }

    private static boolean inRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 文字统一在 render() 里画
    }
}
