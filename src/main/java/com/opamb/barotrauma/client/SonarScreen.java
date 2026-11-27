package com.opamb.barotrauma.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.opamb.barotrauma.campaign.CampaignMap;
import com.opamb.barotrauma.campaign.RoundPhase;
import com.opamb.barotrauma.menu.SonarMenu;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipActionPacket;
import com.opamb.barotrauma.network.SonarPingPacket;
import com.opamb.barotrauma.sonar.ClientSonarCache;
import com.opamb.barotrauma.ship.vs.VsClientCompartments;
import com.opamb.barotrauma.ship.vs.VsSupport;
import com.opamb.barotrauma.sonar.SonarScan;
import com.opamb.barotrauma.sonar.SonarPulse;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import com.opamb.barotrauma.client.gui.PanelStyle;
import net.minecraft.client.gui.GuiGraphics;
import com.opamb.barotrauma.client.gui.ScaledContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/** 3D 声纳、操舵和靠泊终端。 */
public class SonarScreen extends ScaledContainerScreen<SonarMenu> {

    // ---- 版式 ----
    private static final int PANEL_W = 510;
    private static final int PANEL_H = 356;
    private static final int MARGIN = 10;
    private static final int SCOPE_CX = 164;
    private static final int SCOPE_CY = 167;
    private static final int SCOPE_R = 130;
    private static final int FIELD_R = 121;
    private static final int TARGET_GRAB_RADIUS = 9;
    private static final double ORBIT_THRESHOLD = 6.0D;

    private static final int COL_X = 319;
    private static final int COL_W = PANEL_W - COL_X - MARGIN;
    private static final int SONAR_BOX_Y = 34;
    private static final int SONAR_BOX_H = 88;
    private static final int HELM_BOX_Y = 130;
    private static final int HELM_BOX_H = 132;
    private static final int DOCK_CX = COL_X + 35;
    private static final int DOCK_CY = 297;
    private static final int DOCK_R = 24;

    private static final int STATUS_INTERVAL = 10;
    private static final int CONTROL_INTERVAL = 4;
    private static final int HELM_PAD_X = 43;
    private static final int HELM_PAD_Y = 70;
    private static final int HELM_PAD_R = 24;

    // ---- 配色 ----
    private static final int BOX_EDGE = 0xFF385459;
    private static final int TEXT = PanelStyle.TEXT;
    private static final int DIM = PanelStyle.DIM;
    private static final int ACCENT = PanelStyle.AMBER;
    private static final int LED = 0xFF7BE38B;
    private static final int SCOPE_BG = 0xFF05090A;
    private static final int SCOPE_RIM = 0xFF294246;
    private static final int TARGET_COLOR = 0xFFFFB03A;
    private static final int BANNER_TEXT = 0xFFEDE1BE;
    private static final int WAYPOINT_COLOR = 0xFF83BCCE;

    private float camYaw = -0.55F;
    private float camPitch = -0.34F;

    private int range;
    private int pingCooldown;
    private final SonarScope scope = new SonarScope(FIELD_R);
    private double sonarTime;
    private Vec3 renderDelta = Vec3.ZERO;
    private org.joml.Quaternionfc renderRotation = new org.joml.Quaternionf();
    private float zoom = 1.0F;
    private Vec3 aim = new Vec3(0.0D, 0.0D, 1.0D);
    private boolean aiming;
    private boolean settingsDirty;
    private int settingsRevision;

    private boolean orbiting;
    private boolean draggingZoom;
    private boolean synced;
    private double lastMouseX;
    private double lastMouseY;
    private double pressX;
    private double pressY;
    private boolean draggedFarEnough;

    private int power = 100;
    private int powerAckTicks;
    private boolean autoMode;
    private SonarScan.Mode mode = SonarScan.Mode.ACTIVE;

    private BlockPos pendingTarget;
    private BlockPos sentTarget;
    private int targetAckTicks;
    private int targetSendCooldown;
    private TargetDrag targetDrag;
    private boolean targetDragMoved;
    private record TargetDrag(BlockPos plane, double cursorOffsetX, double cursorOffsetY) {}
    private boolean draggingHelm;
    private boolean draggingBallast;
    private boolean keyboardDriving;
    private int manualX;
    private int manualY;
    private int manualZ;
    private boolean manualDirty;
    private int helmSendCooldown;
    private Boolean pendingAutoMode;
    private int modeAckTicks;
    /** 自动驾驶目标微调的按键重复冷却（tick）。 */
    private int targetNudgeCooldown;
    /** 1 = 每 tick 走 1 格（20 格/秒）。 */
    private static final int TARGET_NUDGE_INTERVAL = 1;
    private static final int TARGET_NUDGE_STEP = 1;
    /** 一次连续微调期间锁存的基准方向（世界水平单位向量：船→目标，缺省船头）。 */
    private boolean nudgeBasisLatched;
    private float nudgeDirX;
    private float nudgeDirZ;
    // ---- 右下圆形按钮的四种状态 ----
    private static final int DOCK_READY = 1;
    private static final int DOCK_FAR = 2;
    private static final int DOCK_DEPART = 3;
    private static final int DOCK_EXIT = 5;
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
        if (!synced) {
            ClientSonarCache.open(menu.containerId);
            sendPing();
        }
        scope.clear();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        readNavigationState();
        pollKeys();
        if (targetSendCooldown > 0) {
            targetSendCooldown--;
        }
        if (targetSendCooldown == 0) {
            flushTarget();
        }
        if (--pingCooldown <= 0) {
            sendPing();
        }
    }

    private void sendPing() {
        if (settingsDirty) {
            settingsRevision++;
        }
        int pending = settingsRevision > ClientSonarCache.settingsRevision() ? settingsRevision : 0;
        ModNetwork.send(new SonarPingPacket(menu.sonarPos(), menu.containerId, range, mode.id(),
                (float) aim.x, (float) aim.y, (float) aim.z, ClientSonarCache.latestTick(), pending));
        settingsDirty = false;
        pingCooldown = STATUS_INTERVAL;
    }

    // ------------------------------------------------------------------ 键盘（手动模式）

    private void readNavigationState() {
        if (ClientSonarCache.frame() != null) {
            int serverPower = ClientSonarCache.power();
            if (powerAckTicks == 0 || power == serverPower || --powerAckTicks == 0) {
                power = serverPower;
                powerAckTicks = 0;
            }
        }
        if (pendingAutoMode != null && (ClientSonarCache.autopilot() == pendingAutoMode || --modeAckTicks <= 0)) {
            pendingAutoMode = null;
        }
        if (pendingAutoMode == null && ClientSonarCache.frame() != null) {
            autoMode = ClientSonarCache.autopilot();
            if (autoMode) {
                manualX = manualY = manualZ = 0;
                manualDirty = keyboardDriving = false;
            }
        }
        if (pendingTarget != null && pendingTarget.equals(sentTarget)
                && (pendingTarget.equals(serverTarget()) || --targetAckTicks <= 0 && targetDrag == null)) {
            pendingTarget = null;
            sentTarget = null;
        }
    }

    private void pollKeys() {
        if (minecraft == null || minecraft.getWindow() == null || !synced) {
            return;
        }
        if (!ClientSonarCache.online()) {
            manualX = manualY = manualZ = 0;
            manualDirty = keyboardDriving = false;
            return;
        }
        long window = minecraft.getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_FALSE) {
            draggingHelm = draggingBallast = keyboardDriving = false;
            setManualAxes(0, 0, 0);
            return;
        }
        boolean forward = down(window, GLFW.GLFW_KEY_W) || down(window, GLFW.GLFW_KEY_UP);
        boolean back = down(window, GLFW.GLFW_KEY_S) || down(window, GLFW.GLFW_KEY_DOWN);
        boolean left = down(window, GLFW.GLFW_KEY_A) || down(window, GLFW.GLFW_KEY_LEFT);
        boolean right = down(window, GLFW.GLFW_KEY_D) || down(window, GLFW.GLFW_KEY_RIGHT);
        boolean up = down(window, GLFW.GLFW_KEY_SPACE);
        boolean sink = down(window, GLFW.GLFW_KEY_LEFT_SHIFT) || down(window, GLFW.GLFW_KEY_RIGHT_SHIFT);

        if (autoMode) {
            if (targetDrag == null) {
                nudgeTarget(forward, back, left, right, up, sink);
            }
            return;
        }
        int forwardAxis = (forward ? 1 : 0) - (back ? 1 : 0);
        int rightAxis = (right ? 1 : 0) - (left ? 1 : 0);
        int vertical = (up ? 1 : 0) - (sink ? 1 : 0);
        boolean keys = forwardAxis != 0 || rightAxis != 0 || vertical != 0;
        if (!draggingHelm && !draggingBallast && (keys || keyboardDriving)) {
            setManualAxes(forwardAxis * 100, vertical * 100, rightAxis * 100);
            keyboardDriving = keys;
        }
        if (--helmSendCooldown <= 0 && (manualDirty || manualX != 0 || manualY != 0 || manualZ != 0)) {
            sendManual();
        }
    }

    private void setManualAxes(int forward, int vertical, int right) {
        if (forward == manualX && vertical == manualY && right == manualZ) {
            return;
        }
        manualX = forward;
        manualY = vertical;
        manualZ = right;
        manualDirty = true;
        if (ClientSonarCache.online() && !draggingHelm && !draggingBallast) {
            sendManual();
        }
    }

    private void sendManual() {
        int forward = manualX;
        int yaw = -manualZ;
        if (!ClientSonarCache.physical()) {
            // Discrete ships accept a world-space thrust vector.
            double sin = Math.sin(camYaw);
            double cos = Math.cos(camYaw);
            forward = (int) Math.round(manualX * sin - manualZ * cos);
            yaw = (int) Math.round(manualX * cos + manualZ * sin);
        }
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.SET_ANALOG_HELM,
                forward, manualY, yaw));
        manualDirty = false;
        helmSendCooldown = CONTROL_INTERVAL;
    }

    private static boolean down(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }

    @Override
    public void onClose() {
        draggingHelm = draggingBallast = false;
        if (!autoMode && (manualX != 0 || manualY != 0 || manualZ != 0)) {
            setManualAxes(0, 0, 0);
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
        PanelStyle.frame(graphics, x, y, PANEL_W, PANEL_H, PanelStyle.AMBER);
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    protected void renderScaled(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!synced && ClientSonarCache.frame() != null) {
            power = ClientSonarCache.power();
            autoMode = ClientSonarCache.autopilot();
            synced = true;
        }
        Minecraft client = Minecraft.getInstance();
        sonarTime = client.level == null ? 0.0D : client.level.getGameTime() + partialTick;
        if (!settingsDirty && !aiming && ClientSonarCache.frame() != null
                && ClientSonarCache.settingsRevision() >= settingsRevision) {
            mode = ClientSonarCache.mode();
            range = ClientSonarCache.frame().range();
            aim = ClientSonarCache.direction();
        }
        renderRotation = ClientSonarCache.hullRotation();
        renderDelta = ClientSonarCache.hullOffset();
        if (VsSupport.available() && ClientSonarCache.physicalShipId() >= 0L) {
            var pose = VsClientCompartments.sonarPose(client.level,
                    ClientSonarCache.physicalShipId(), ClientSonarCache.shipAnchor());
            if (pose != null) {
                renderDelta = pose.center().subtract(Vec3.atCenterOf(ClientSonarCache.center()));
                renderRotation = pose.rotation();
            }
        }
        renderBg(graphics, partialTick, mouseX, mouseY);
        drawBanner(graphics);
        drawScope(graphics, mouseX, mouseY);
        drawNavigationInstruments(graphics);
        drawSonarBox(graphics);
        drawHelmBox(graphics, mouseX, mouseY);
        drawDockButton(graphics, mouseX, mouseY);
        drawBottomHint(graphics);
    }

    private void drawBanner(GuiGraphics graphics) {
        String text = "声纳 / 操舵台";
        var state = CampaignClient.state();
        if (state != null && state.phase() == RoundPhase.AT_NODE && CampaignClient.available()) {
            text = "准备出航  " + nodeName(state.current());
        } else if (state != null && state.phase() != RoundPhase.AT_NODE) {
            text = "航行中 · 前往  " + nodeName(state.destination());
        }
        int x = leftPos + MARGIN;
        int y = topPos + 9;
        int w = PANEL_W - MARGIN * 2;
        PanelStyle.divider(graphics, x, y + 17, w);
        graphics.drawString(font, "声纳 / 导航", x + 3, y + 4, TEXT, false);
        if (state != null) {
            graphics.drawString(font, text, x + w - 3 - font.width(text), y + 4, BANNER_TEXT, false);
        }
    }

    private static String nodeName(int id) {
        var node = CampaignMap.node(id);
        return node == null ? "#" + id : node.name();
    }

    private void drawNavigationInstruments(GuiGraphics graphics) {
        int y = topPos + 304;
        box(graphics, leftPos + MARGIN, y, COL_X - MARGIN * 2, 24);
        int hx = ClientSonarCache.headingX();
        int hz = ClientSonarCache.headingZ();
        String heading = hx == 0 && hz == 0 ? "—" : String.format("%03d°", bearingDegrees(hx, hz));
        instrument(graphics, 47, y, "深度", ClientSonarCache.depth() + " m");
        instrument(graphics, 121, y, "航速", String.format(java.util.Locale.ROOT, "%.1f km/h",
                ClientSonarCache.speedTenths() / 10.0D));
        instrument(graphics, 195, y, "垂速", String.format(java.util.Locale.ROOT, "%+.1f km/h",
                ClientSonarCache.diveTenths() / 10.0D));
        instrument(graphics, 269, y, "航向", heading);
    }

    private void instrument(GuiGraphics graphics, int x, int y, String name, String value) {
        graphics.drawCenteredString(font, name, leftPos + x, y + 3, DIM);
        graphics.drawCenteredString(font, value, leftPos + x, y + 13, 0xFFD4F2E7);
    }

    private void drawScope(GuiGraphics graphics, int mouseX, int mouseY) {
        int cx = leftPos + SCOPE_CX;
        int cy = topPos + SCOPE_CY;

        PanelStyle.well(graphics, MARGIN, SONAR_BOX_Y, COL_X - MARGIN * 2, 265);
        disc(graphics, cx, cy, SCOPE_R, SCOPE_RIM);
        disc(graphics, cx, cy, SCOPE_R - 2, 0xFF102126);
        disc(graphics, cx, cy, FIELD_R, SCOPE_BG);
        // 表圈刻度与两道量程参考线。
        ring(graphics, cx, cy, SCOPE_R - 3, 0xFF446167);
        for (int deg = 0; deg < 360; deg += 15) {
            int inner = SCOPE_R - (deg % 90 == 0 ? 11 : 5);
            radial(graphics, cx, cy, inner, SCOPE_R - 2, deg, 0xFF55777A);
        }
        ring(graphics, cx, cy, FIELD_R, 0xFF345C58);
        ring(graphics, cx, cy, FIELD_R / 2, 0xFF16302A);
        line(graphics, cx - FIELD_R + 1, cy, cx + FIELD_R - 1, cy, 0xFF111F23);
        line(graphics, cx, cy - FIELD_R + 1, cx, cy + FIELD_R - 1, 0xFF111F23);

        SonarScan.Frame frame = ClientSonarCache.frame();
        int viewRange = viewRange();
        float[] view = basis();
        if (frame == null || !ClientSonarCache.online()) {
            graphics.drawCenteredString(font, frame == null ? "正在连接声纳…" : ClientSonarCache.status(),
                    cx, cy - 4, frame == null ? DIM : ACCENT);
            scope.clear();
        } else {
            double camera = camDistanceOf(viewRange);
            drawWavefront(graphics, cx, cy, view, viewRange);
            scope.drawEchoes(graphics, cx, cy, camera, view, sonarTime, renderDelta, zoom);
            drawHullOutline(graphics, cx, cy, frame, view, camera);
            drawWaypoint(graphics, cx, cy, view, viewRange);
            drawTargetPreview(graphics, cx, cy, mouseX, mouseY, view, camera);
            drawTargetMarker(graphics, cx, cy, mouseX, mouseY, view, camera);
        }
        drawBearings(graphics, cx, cy);
        if (ClientSonarCache.online()) {
            drawHeadingTick(graphics, cx, cy);
        }
    }

    /** 东南西北四个方位字，跟着视角转——转完也知道哪边是哪儿。 */
    private void drawBearings(GuiGraphics graphics, int cx, int cy) {
        // 只跟随水平偏航，抬头或低头时罗盘仍保持稳定。
        float cosYaw = Mth.cos(camYaw);
        float sinYaw = Mth.sin(camYaw);
        String[] names = {"北", "东", "南", "西"};
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            float sr = dirs[i][0] * -cosYaw + dirs[i][1] * sinYaw;
            float su = dirs[i][0] * sinYaw + dirs[i][1] * cosYaw;
            double len = Math.sqrt((double) sr * sr + (double) su * su);
            if (len < 0.15D) {
                continue;
            }
            int px = cx + (int) Math.round(sr / len * (SCOPE_R - 3));
            int py = cy - (int) Math.round(su / len * (SCOPE_R - 3));
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

    private int viewRange() {
        return Math.max(8, Math.round(range / zoom));
    }

    private void drawHullOutline(GuiGraphics graphics, int cx, int cy, SonarScan.Frame frame,
                                 float[] view, double camera) {
        for (SonarScope.Segment edge : scope.hullEdges(frame.points(), Vec3.ZERO, renderRotation, view, camera)) {
            line(graphics, cx + edge.x1(), cy + edge.y1(), cx + edge.x2(), cy + edge.y2(), 0x80ADD8E6);
        }
    }

    private void drawWavefront(GuiGraphics graphics, int cx, int cy, float[] view, int viewRange) {
        SonarPulse pulse = ClientSonarCache.latestPulse();
        if (mode == SonarScan.Mode.DIRECTIONAL) {
            int[] cursor = scope.aimMarker(aim, view, camDistanceOf(viewRange));
            if (cursor != null && cursor[0] * cursor[0] + cursor[1] * cursor[1] <= (FIELD_R - 6) * (FIELD_R - 6)) {
                int x = cx + cursor[0];
                int y = cy + cursor[1];
                ring(graphics, x, y, 5, 0xC68CD6CE);
                line(graphics, x - 2, y, x + 2, y, 0xC68CD6CE);
                line(graphics, x, y - 2, x, y + 2, 0xC68CD6CE);
            }
        }
        if (pulse == null || pulse.mode() == SonarScan.Mode.PASSIVE || mode == SonarScan.Mode.PASSIVE) {
            return;
        }
        float progress = pulse.progress(sonarTime);
        if (progress <= 0.0F || progress >= 1.0F) {
            return;
        }
        BlockPos center = ClientSonarCache.center();
        Vec3 origin = new Vec3(pulse.origin().getX() - center.getX(),
                pulse.origin().getY() - center.getY(), pulse.origin().getZ() - center.getZ()).subtract(renderDelta);
        Vec3 axis = pulse.direction();
        Vec3 right = new Vec3(view[3], view[4], view[5]);
        Vec3 up = new Vec3(view[6], view[7], view[8]);
        double radius = pulse.range() * progress;
        if (pulse.mode() == SonarScan.Mode.DIRECTIONAL) {
            right = axis.cross(Math.abs(axis.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            up = axis.cross(right);
            double halfAngle = Math.toRadians(SonarScan.CONE_DEGREES / 2.0D);
            origin = origin.add(axis.scale(radius * Math.cos(halfAngle)));
            radius *= Math.sin(halfAngle);
        }

        int color = argb((int) ((1.0F - progress) * 160.0F), 130, 229, 225);
        int[] previous = null;
        for (int i = 0; i <= 32; i++) {
            double angle = i * Math.PI * 2 / 32;
            Vec3 offset = origin.add(right.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle)));
            int[] next = scope.marker(offset, view, camDistanceOf(viewRange));
            if (next != null && next[0] * next[0] + next[1] * next[1] >= (FIELD_R - 1) * (FIELD_R - 1)) {
                next = null;
            }
            if (previous != null && next != null) {
                line(graphics, cx + previous[0], cy + previous[1], cx + next[0], cy + next[1], color);
            }
            previous = next;
        }
    }

    /** 表圈上的航向标与罗盘共用水平朝向。 */
    private void drawHeadingTick(GuiGraphics graphics, int cx, int cy) {
        float x = ClientSonarCache.headingX();
        float z = ClientSonarCache.headingZ();
        if (x == 0.0F && z == 0.0F) {
            return;
        }
        if (renderRotation != ClientSonarCache.hullRotation()) {
            var bow = new org.joml.Vector3f(x, 0.0F, z);
            ClientSonarCache.hullRotation().transformInverse(bow);
            renderRotation.transform(bow);
            x = bow.x;
            z = bow.z;
        }
        double angle = Math.atan2(-x * Mth.cos(camYaw) + z * Mth.sin(camYaw),
                x * Mth.sin(camYaw) + z * Mth.cos(camYaw));
        int tipX = cx + (int) Math.round(Math.sin(angle) * (FIELD_R - 3));
        int tipY = cy - (int) Math.round(Math.cos(angle) * (FIELD_R - 3));
        for (double wing : new double[]{angle - 0.033D, angle + 0.033D}) {
            line(graphics, tipX, tipY, cx + (int) Math.round(Math.sin(wing) * (FIELD_R + 3)),
                    cy - (int) Math.round(Math.cos(wing) * (FIELD_R + 3)), 0xFFD0E8E9);
        }
    }

    private void drawWaypoint(GuiGraphics graphics, int cx, int cy, float[] view, int viewRange) {
        if (!ClientSonarCache.hasWaypoint()) {
            return;
        }
        Vec3 offset = new Vec3(ClientSonarCache.waypointX(), ClientSonarCache.waypointY(),
                ClientSonarCache.waypointZ()).subtract(renderDelta);
        SonarScope.NavMarker marker = scope.stationMarker(offset, view[3], view[5], viewRange);
        int x = cx + marker.x();
        int y = cy + marker.y();
        line(graphics, x - 3, y, x, y - 3, WAYPOINT_COLOR);
        line(graphics, x, y - 3, x + 3, y, WAYPOINT_COLOR);
        line(graphics, x + 3, y, x, y + 3, WAYPOINT_COLOR);
        line(graphics, x, y + 3, x - 3, y, WAYPOINT_COLOR);

        String station = ClientSonarCache.waypointLabel();
        String label = font.plainSubstrByWidth(station.isBlank() ? "航点" : station, FIELD_R);
        int meters = (int) offset.length();
        if (marker.onRim()) {
            meters = Math.max(meters, ClientSonarCache.waypointDistance());
        }
        String distance = meters + " m";
        int width = Math.max(font.width(label), font.width(distance)) + 4;
        int height = 2 * font.lineHeight + 2;
        SonarScope.LabelPosition caption = scope.waypointLabel(marker, width, height);
        int left = cx + caption.x();
        int top = cy + caption.y();
        graphics.fill(left, top, left + width, top + height, 0xB0071111);
        graphics.drawString(font, label, left + 2, top + 1, WAYPOINT_COLOR, false);
        graphics.drawString(font, distance, left + 2, top + 1 + font.lineHeight, WAYPOINT_COLOR, false);
    }

    private SonarScope.NavMarker targetMarker(BlockPos offset, float[] view, double camera) {
        return scope.navigationMarker(Vec3.atLowerCornerOf(offset).subtract(renderDelta), view, camera, false);
    }

    private void drawTargetMarker(GuiGraphics graphics, int cx, int cy, int mouseX, int mouseY,
                                  float[] view, double camera) {
        BlockPos target = targetOffset();
        if (!autoMode || target == null) {
            return;
        }
        SonarScope.NavMarker marker = targetMarker(target, view, camera);
        int x = cx + marker.x();
        int y = cy + marker.y();
        boolean active = targetDrag != null || !marker.onRim()
                && inCircle(mouseX, mouseY, x, y, TARGET_GRAB_RADIUS);
        if (!marker.onRim()) {
            double length = Math.hypot(marker.x(), marker.y());
            for (double d = 11; d < length - 8; d += 5) {
                int dotX = cx + (int) Math.round(marker.x() * d / length);
                int dotY = cy + (int) Math.round(marker.y() * d / length);
                graphics.fill(dotX, dotY, dotX + 1, dotY + 1, 0x70E6AD65);
            }
        }
        reticle(graphics, x, y, marker.onRim() ? 4 : 6, active ? 0xFFFFDBA4 : TARGET_COLOR);
    }

    private void drawTargetPreview(GuiGraphics graphics, int cx, int cy, int mouseX, int mouseY,
                                   float[] view, double camera) {
        if (orbiting || aiming || targetDrag != null || !autoMode
                || !inCircle(mouseX, mouseY, cx, cy, FIELD_R)) {
            return;
        }
        BlockPos selected = targetOffset();
        if (selected != null) {
            SonarScope.NavMarker marker = targetMarker(selected, view, camera);
            if (inCircle(mouseX, mouseY, cx + marker.x(), cy + marker.y(), TARGET_GRAB_RADIUS)) {
                return;
            }
        }
        int[] target = computeTarget(mouseX, mouseY);
        if (target == null) {
            return;
        }
        int[] at = scope.marker(new Vec3(target[0], target[1], target[2]).subtract(renderDelta), view, camera);
        if (at != null && inCircle(at[0], at[1], 0, 0, FIELD_R - 5)) {
            reticle(graphics, cx + at[0], cy + at[1], 4, 0x997CA2A1);
        }
    }

    private static void reticle(GuiGraphics graphics, int x, int y, int radius, int color) {
        ring(graphics, x, y, radius, color);
        graphics.fill(x, y, x + 1, y + 1, color);
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
        PanelStyle.slider(graphics, barX, barY - 2, barW,
                (range - SonarScan.MIN_RANGE) / (double) (SonarScan.MAX_RANGE - SonarScan.MIN_RANGE),
                true, false);
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
        PanelStyle.meter(graphics, barX, barY - 2, barW, 9, power / 100.0D, 0xFF589E98, 10);
        String pct = power + "%";
        graphics.drawString(font, pct, x + COL_W - 8 - font.width(pct), y + 108, LED, false);
    }

    private void drawManualPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int cx = x + HELM_PAD_X;
        int cy = y + HELM_PAD_Y;
        disc(graphics, cx, cy, HELM_PAD_R, 0xFF111917);
        ring(graphics, cx, cy, HELM_PAD_R, 0xFF63796A);
        line(graphics, cx - HELM_PAD_R + 3, cy, cx + HELM_PAD_R - 3, cy, BOX_EDGE);
        line(graphics, cx, cy - HELM_PAD_R + 3, cx, cy + HELM_PAD_R - 3, BOX_EDGE);
        int px = cx + manualZ * (HELM_PAD_R - 4) / 100;
        int py = cy - manualX * (HELM_PAD_R - 4) / 100;
        line(graphics, cx, cy, px, py, 0xAA79C6AA);
        disc(graphics, px, py, 3, LED);
        graphics.drawString(font, ClientSonarCache.physical() ? "前后 / 转向" : "水平舵令",
                x + 83, y + 43, TEXT, false);
        graphics.drawString(font, "升潜", x + 83, y + 58, DIM, false);
        int sliderX = x + 124;
        int sliderY = y + 59;
        graphics.fill(sliderX, sliderY, sliderX + 37, sliderY + 6, 0xFF141A16);
        int thumb = sliderX + (manualY + 100) * 36 / 200;
        graphics.fill(thumb - 1, sliderY - 1, thumb + 2, sliderY + 7, LED);
        controlButton(graphics, x + 83, y + 79, 78, "舵令归零", mouseX, mouseY);
    }

    private void drawAutoPanel(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        BlockPos target = targetOffset();
        String readout = target == null ? "正在锁定船位"
                : String.format(java.util.Locale.ROOT, "目标 %03d°  %.1f m",
                        bearingDegrees(target.getX(), target.getZ()), Math.sqrt(target.distSqr(BlockPos.ZERO)));
        graphics.drawString(font, navigationLabel(), x + 8, y + 43, TARGET_COLOR, false);
        graphics.drawString(font, readout, x + 8, y + 56, TEXT, false);
        controlButton(graphics, x + 8, y + 76, 118, "锁定当前位置", mouseX, mouseY);
    }

    private void controlButton(GuiGraphics graphics, int x, int y, int w, String label, int mouseX, int mouseY) {
        boolean hover = inRect(mouseX, mouseY, x, y, w, 16);
        PanelStyle.control(graphics, x, y, w, 16, true, hover);
        graphics.drawCenteredString(font, label, x + w / 2, y + 4, TEXT);
    }

    private String navigationLabel() {
        if (pendingTarget != null) {
            return "前往目标";
        }
        return switch (ClientSonarCache.navigationMode()) {
            case TRANSIT -> "前往目标";
            case BLOCKED -> "航行受阻";
            case SETTLING -> "正在稳船";
            case HOLD -> "定点保持";
            case MANUAL -> "正在锁定船位";
        };
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
            if (CampaignClient.available()) return DOCK_DEPART;
            if (state == 3) return DOCK_EXIT;
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

        PanelStyle.well(graphics, COL_X, 270, COL_W, 58);
        disc(graphics, cx, cy, DOCK_R, 0xFF405A60);
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
            case DOCK_EXIT -> {
                inner = hover ? 0xFF2F7A56 : 0xFF256044;
                label = "驶出";
                sub = "结束航程";
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
        graphics.drawCenteredString(font, label, cx, cy - 4, labelColor);
        int dockTextX = cx + DOCK_R + 8;
        String station = ClientSonarCache.dockLabel();
        boolean namedDock = (mode == DOCK_READY || mode == DOCK_FAR || mode == DOCK_EXIT) && !station.isBlank();
        graphics.drawString(font, sub, dockTextX, cy - (namedDock ? 9 : 4), enabled ? TEXT : DIM, false);
        if (namedDock) {
            graphics.drawString(font, font.plainSubstrByWidth(station, PANEL_W - MARGIN * 2 - dockTextX),
                    dockTextX, cy + 3, DIM, false);
        }
    }

    // ---------------------------------------------------------- 底部提示

    private void drawBottomHint(GuiGraphics graphics) {
        String hint = autoMode
                ? "拖圆环移目标 · 单击设点 · 拖空处转视角 · WASD/空格/Shift 微调"
                : "WASD/空格/Shift 松手回中 · 鼠标舵令保持 · 拖主屏转视角";
        hint += " · Ctrl+滚轮缩放";
        if (mode == SonarScan.Mode.DIRECTIONAL) {
            hint += " · 右键瞄准";
        }
        graphics.drawString(font, hint, leftPos + MARGIN, topPos + PANEL_H - 16, DIM, false);
    }

    // ---------------------------------------------------------- 小工具

    private void box(GuiGraphics graphics, int x, int y, int w, int h) {
        PanelStyle.well(graphics, x, y, w, h);
    }

    private void radio(GuiGraphics graphics, int x, int y, String label, boolean selected) {
        PanelStyle.lamp(graphics, x + 4, y + 4, selected, ACCENT);
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

    // ---------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) { double mx = designX(mouseX); double my = designY(mouseY);
        int cx = leftPos + SCOPE_CX;
        int cy = topPos + SCOPE_CY;
        double ox = mx - cx;
        double oy = my - cy;
        boolean inScope = ox * ox + oy * oy <= (double) FIELD_R * FIELD_R;

        if (button == 1 && inScope && !orbiting && targetDrag == null && mode == SonarScan.Mode.DIRECTIONAL) {
            aiming = true;
            aimAt(mx, my);
            return true;
        }
        if (button == 0 && inScope && !aiming) {
            if (autoMode && beginTargetDrag(mx, my)) {
                return true;
            }
            orbiting = true;
            pressX = mx;
            pressY = my;
            draggedFarEnough = false;
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // 右下圆形按钮：离港 / 对接 / 下潜
        if (inCircle(mx, my, leftPos + DOCK_CX, topPos + DOCK_CY, DOCK_R)) {
            clickDockButton();
            return true;
        }

        int x = leftPos + COL_X;
        int sy = topPos + SONAR_BOX_Y;
        if (inRect(mx, my, x + 4, sy + 6, 150, 14)) {
            setMode(SonarScan.Mode.PASSIVE);
            return true;
        }
        if (inRect(mx, my, x + 4, sy + 20, 150, 14)) {
            setMode(SonarScan.Mode.ACTIVE);
            return true;
        }
        if (inRect(mx, my, x + 4, sy + 36, 160, 14)) {
            setMode(mode == SonarScan.Mode.DIRECTIONAL ? SonarScan.Mode.ACTIVE : SonarScan.Mode.DIRECTIONAL);
            return true;
        }
        if (inRect(mx, my, x + 4, sy + 66, COL_W - 8, 14)) {
            draggingZoom = true;
            applyZoom(mx);
            return true;
        }

        int hy = topPos + HELM_BOX_Y;
        if (inRect(mx, my, x + 4, hy + 6, 160, 14)) {
            setAuto(false);
            return true;
        }
        if (inRect(mx, my, x + 4, hy + 20, 160, 14)) {
            setAuto(true);
            return true;
        }
        if (inRect(mx, my, x + 40, hy + 108, COL_W - 50, 14)) {
            applyPower(mx, x);
            return true;
        }
        if (autoMode) {
            if (inRect(mx, my, x + 8, hy + 76, 118, 16)) {
                clearTarget();
                return true;
            }
        } else {
            if (inCircle(mx, my, x + HELM_PAD_X, hy + HELM_PAD_Y, HELM_PAD_R + 3)) {
                draggingHelm = true;
                keyboardDriving = false;
                updateHelmPad(mx, my);
                return true;
            }
            if (inRect(mx, my, x + 118, hy + 52, 49, 21)) {
                draggingBallast = true;
                keyboardDriving = false;
                updateBallastSlider(mx);
                return true;
            }
            if (inRect(mx, my, x + 83, hy + 79, 78, 16)) {
                setManualAxes(0, 0, 0);
                return true;
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
                    if (!autoMode) {
                        setManualAxes(0, 0, 0);
                    }
                    minecraft.setScreen(new CampaignMapScreen());
                }
            }
            case DOCK_READY, DOCK_EXIT -> ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.DOCK));
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { double mx = designX(mouseX); double my = designY(mouseY);
        if (aiming && button == 1) {
            aimAt(mx, my);
            return true;
        }
        if (button == 0 && targetDrag != null) {
            targetDragMoved = true;
            moveTargetDrag(mx, my);
            return true;
        }
        if (button == 0 && draggingHelm) {
            updateHelmPad(mx, my);
            return true;
        }
        if (button == 0 && draggingBallast) {
            updateBallastSlider(mx);
            return true;
        }
        if (button == 0 && draggingZoom) {
            applyZoom(mx);
            return true;
        }
        if (button == 0 && orbiting) {
            if (!draggedFarEnough && Math.hypot(mx - pressX, my - pressY) <= ORBIT_THRESHOLD) {
                return true;
            }
            draggedFarEnough = true;
            camYaw -= (float) (mx - lastMouseX) * 0.008F;
            camPitch = Mth.clamp(camPitch - (float) (my - lastMouseY) * 0.008F, -1.45F, 1.45F);
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) { double mx = designX(mouseX); double my = designY(mouseY);
        if (button == 0 && targetDrag != null) {
            if (targetDragMoved) {
                moveTargetDrag(mx, my);
            }
            targetDrag = null;
            flushTarget();
            return true;
        }
        if (button == 0 && (draggingHelm || draggingBallast)) {
            draggingHelm = draggingBallast = false;
            if (manualDirty) {
                sendManual();
            }
            return true;
        }
        if (button == 1 && aiming) {
            aimAt(mx, my);
            aiming = false;
            settingsDirty = true;
            return true;
        }
        if (button == 0 && orbiting) {
            orbiting = false;
            if (autoMode && !draggedFarEnough && Math.hypot(mx - pressX, my - pressY) <= ORBIT_THRESHOLD
                    && inCircle(mx, my, leftPos + SCOPE_CX, topPos + SCOPE_CY, FIELD_R)) {
                markTargetAt(mx, my);
            }
            return true;
        }
        if (button == 0 && draggingZoom) {
            draggingZoom = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) { double mx = designX(mouseX); double my = designY(mouseY);
        int cx = leftPos + SCOPE_CX;
        int cy = topPos + SCOPE_CY;
        double ox = mx - cx;
        double oy = my - cy;
        if (ox * ox + oy * oy <= (double) FIELD_R * FIELD_R) {
            if (targetDrag != null || aiming) {
                return true;
            }
            if (hasControlDown()) {
                zoom = Mth.clamp(zoom + (delta > 0 ? 0.25F : -0.25F), 1.0F, 4.0F);
                return true;
            }
            int next = Mth.clamp(power + (delta > 0 ? 10 : -10), 10, 100);
            setPower(next);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void aimAt(double mx, double my) {
        aim = scope.aimDirection(mx - leftPos - SCOPE_CX, my - topPos - SCOPE_CY,
                basis(), camDistanceOf(viewRange()));
    }

    private BlockPos serverTarget() {
        return ClientSonarCache.hasTarget() ? ClientSonarCache.center().offset(ClientSonarCache.targetX(),
                ClientSonarCache.targetY(), ClientSonarCache.targetZ()) : null;
    }

    private BlockPos targetOffset() {
        BlockPos target = pendingTarget != null ? pendingTarget : serverTarget();
        if (target == null) {
            return null;
        }
        BlockPos center = ClientSonarCache.center();
        return target.offset(-center.getX(), -center.getY(), -center.getZ());
    }

    private boolean beginTargetDrag(double mx, double my) {
        BlockPos target = targetOffset();
        if (target == null || !ClientSonarCache.online()) {
            return false;
        }
        SonarScope.NavMarker marker = targetMarker(target, basis(), camDistanceOf(viewRange()));
        if (marker.onRim()) {
            return false;
        }
        double x = mx - leftPos - SCOPE_CX - marker.x();
        double y = my - topPos - SCOPE_CY - marker.y();
        if (Math.hypot(x, y) > TARGET_GRAB_RADIUS) {
            return false;
        }
        targetDrag = new TargetDrag(ClientSonarCache.center().offset(target), x, y);
        targetDragMoved = false;
        return true;
    }

    private void moveTargetDrag(double mx, double my) {
        BlockPos world = scope.dragTarget(targetDrag.plane(), ClientSonarCache.center(), renderDelta,
                mx - leftPos - SCOPE_CX - targetDrag.cursorOffsetX(),
                my - topPos - SCOPE_CY - targetDrag.cursorOffsetY(), basis(), camDistanceOf(viewRange()));
        if (world != null && !world.equals(pendingTarget != null ? pendingTarget : serverTarget())) {
            pendingTarget = world;
        }
    }

    private void markTargetAt(double mouseX, double mouseY) {
        int[] target = computeTarget(mouseX, mouseY);
        if (target != null) {
            pendingTarget = ClientSonarCache.center().offset(target[0], target[1], target[2]);
            flushTarget();
        }
    }

    private void flushTarget() {
        if (!autoMode || !ClientSonarCache.online() || pendingTarget == null || pendingTarget.equals(sentTarget)) {
            return;
        }
        sentTarget = pendingTarget;
        targetAckTicks = STATUS_INTERVAL * 4;
        targetSendCooldown = CONTROL_INTERVAL;
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.SET_AUTOPILOT_TARGET,
                sentTarget.getX(), sentTarget.getY(), sentTarget.getZ()));
    }

    private void updateHelmPad(double mx, double my) {
        double x = (mx - leftPos - COL_X - HELM_PAD_X) / (HELM_PAD_R - 4.0D);
        double y = -(my - topPos - HELM_BOX_Y - HELM_PAD_Y) / (HELM_PAD_R - 4.0D);
        double scale = Math.max(1.0D, Math.hypot(x, y));
        int forward = (int) Math.round(y * 100 / scale);
        int right = (int) Math.round(x * 100 / scale);
        setManualAxes(Math.abs(forward) < 5 ? 0 : forward, manualY, Math.abs(right) < 5 ? 0 : right);
    }

    private void updateBallastSlider(double mx) {
        double t = (mx - leftPos - COL_X - 124) / 36.0D;
        int vertical = Mth.clamp((int) Math.round(t * 200 - 100), -100, 100);
        setManualAxes(manualX, Math.abs(vertical) < 5 ? 0 : vertical, manualZ);
    }

    /**
     * 自动驾驶模式下用 WASD/空格/Shift 微调目标点（每 tick 1 格，约 20 格/秒）。
     *
     * <p>W/S 沿“船→目标”方向前后、A/D 垂直、空格/Shift 上下；方向在一次
     * “按住”期间锁存。每次微调写入同一个待发送的世界目标，和鼠标拖动共用限频。</p>
     */
    private void nudgeTarget(boolean forward, boolean back, boolean left, boolean right,
                             boolean up, boolean sink) {
        if (!autoMode) {
            return;
        }
        int f = (forward ? 1 : 0) - (back ? 1 : 0);
        int r = (right ? 1 : 0) - (left ? 1 : 0);
        int v = (up ? 1 : 0) - (sink ? 1 : 0);
        if (f == 0 && r == 0 && v == 0) {
            targetNudgeCooldown = 0;
            nudgeBasisLatched = false;
            return;
        }
        BlockPos target = targetOffset();
        if (target == null) {
            return;
        }
        int tx = target.getX();
        int tz = target.getZ();
        if (!nudgeBasisLatched) {
            // 基准取“船 → 当前目标”的水平方向，而不是船头：自动驾驶时船头
            // 可能还没转过来，用船头当基准会让 W 把目标往侧面推，船也跟着
            // 往船头方向扭。目标就在脚下时才退回船头。
            double hx = tx;
            double hz = tz;
            double len = Math.sqrt(hx * hx + hz * hz);
            if (len < 1.0E-6D) {
                hx = ClientSonarCache.headingX();
                hz = ClientSonarCache.headingZ();
                len = Math.sqrt(hx * hx + hz * hz);
            }
            if (len < 1.0E-6D) {
                hx = Math.sin(camYaw);
                hz = Math.cos(camYaw);
                len = 1.0D;
            }
            nudgeDirX = (float) (hx / len);
            nudgeDirZ = (float) (hz / len);
            nudgeBasisLatched = true;
        }
        if (targetNudgeCooldown > 0) {
            targetNudgeCooldown--;
            return;
        }
        targetNudgeCooldown = TARGET_NUDGE_INTERVAL - 1;
        // 基准方向 (bx,bz) 的右方是 (-bz,bx)。
        int dx = (int) Math.round((nudgeDirX * f - nudgeDirZ * r) * TARGET_NUDGE_STEP);
        int dz = (int) Math.round((nudgeDirZ * f + nudgeDirX * r) * TARGET_NUDGE_STEP);
        int dy = v * TARGET_NUDGE_STEP;
        pendingTarget = ClientSonarCache.center().offset(target).offset(dx, dy, dz);
    }

    /** 已显示的回波可直接点选，空白处在当前目标的视线深度平面上设点。 */
    private int[] computeTarget(double mouseX, double mouseY) {
        if (!ClientSonarCache.online()) {
            return null;
        }
        double x = mouseX - leftPos - SCOPE_CX;
        double y = mouseY - topPos - SCOPE_CY;
        BlockPos center = ClientSonarCache.center();
        BlockPos world = scope.pick(x, y);
        if (world == null) {
            BlockPos anchor = pendingTarget != null ? pendingTarget : serverTarget();
            if (anchor == null) {
                anchor = center;
            }
            world = scope.dragTarget(anchor, center, renderDelta, x, y, basis(), camDistanceOf(viewRange()));
            if (world == null) {
                world = scope.dragTarget(center, center, renderDelta, x, y, basis(), camDistanceOf(viewRange()));
            }
        }
        return world == null ? null : new int[]{world.getX() - center.getX(),
                world.getY() - center.getY(), world.getZ() - center.getZ()};
    }

    /** 相机到球心的距离，画点云和反投影必须用同一个值。 */
    private static float camDistanceOf(int viewRange) {
        return viewRange * 1.5F;
    }

    private void clearTarget() {
        pendingTarget = sentTarget = null;
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.CLEAR_AUTOPILOT_TARGET));
    }

    private void setAuto(boolean auto) {
        if (autoMode == auto) {
            return;
        }
        if (auto) {
            setManualAxes(0, 0, 0);
        }
        keyboardDriving = false;
        pendingTarget = sentTarget = null;
        autoMode = auto;
        pendingAutoMode = auto;
        modeAckTicks = STATUS_INTERVAL * 4;
        ModNetwork.send(new ShipActionPacket(menu.sonarPos(), ShipActionPacket.SET_AUTOPILOT, auto ? 1 : 0));
    }

    private void setMode(SonarScan.Mode next) {
        if (mode == next) {
            return;
        }
        mode = next;
        if (next == SonarScan.Mode.DIRECTIONAL) {
            float[] view = basis();
            aim = new Vec3(view[0], view[1], view[2]);
        }
        settingsDirty = true;
    }

    private void applyPower(double mouseX, int colX) {
        int barX = colX + 42;
        int barW = COL_W - 92;
        float ratio = Mth.clamp((float) ((mouseX - barX) / (double) barW), 0.0F, 1.0F);
        int next = Mth.clamp(Math.round(ratio * 100.0F / 5.0F) * 5, 10, 100);
        setPower(next);
    }

    private void setPower(int next) {
        if (next == power || !ClientSonarCache.online()) {
            return;
        }
        power = next;
        powerAckTicks = STATUS_INTERVAL * 2;
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
            settingsDirty = true;
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
