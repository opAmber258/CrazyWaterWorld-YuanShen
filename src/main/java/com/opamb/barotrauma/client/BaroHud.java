package com.opamb.barotrauma.client;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 潜渊症风格的游戏内 HUD：右下角人物头像框 + 长方形活力条 + 血条上方的状态图标。
 *
 * <p>布局以原作 {@code HUDLayoutSettings.CreateAreas()} 为基准（1080p 像素，
 * 按屏幕高度等比缩放），再按用户要求做过两点调整：
 * <ul>
 *   <li>活力条压扁：高度 50 → {@link #BAR_H}；信息框宽度与血条对齐；</li>
 *   <li>信息框内只留实时 3D 头像（原作这里还有职业图标和名字，MC 没有对应物）。</li>
 * </ul>
 * 其余沿用原作：血条底边压住信息框顶边 10、状态图标在血条上方间距 11、
 * 图标与血条同高、横向间距 5、右对齐。</p>
 *
 * <p>数值来自 {@link ClientMedicalCache}（活力 + 病症行）；活力条动画照原作
 * {@code CharacterHealth}：受伤后绿色残影延迟 1 秒再以 1 倍条速追上，同时整条
 * 以 5 rad/s 竖向脉动 1 秒。底槽用纯黑，填充色走原作
 * {@code GradientLerp(vitality, 红, 橙, 绿)}。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BaroHud {

    // ---- 原作 HUDLayoutSettings（1080p 像素） ----
    private static final float PADDING = 11f;
    private static final float INFO_W = 142f;
    private static final float INFO_H = 98f;
    /** 血条 9-slice 边框在放大时只按 1 倍补（原作 {@code NonSliceSize.X * min(Scale,1)}）。 */
    private static final float BAR_FRAME_W = 46f;
    /** 血条高度：原作 50，按用户要求压扁。 */
    private static final float BAR_H = 34f;
    private static final float BAR_OVERLAP = 10f;
    /** 状态图标沿用原作 50 高（与血条同高的旧比例已随血条压扁改回独立尺寸）。 */
    private static final float ICON_SIZE = 50f;
    private static final float ICON_GAP = 5f;

    // ---- 配色（原作 GUIStyle + 金属边框） ----
    private static final int FRAME = 0xFF3A4046;
    private static final int FRAME_LIGHT = 0xFF69727A;
    private static final int FRAME_DARK = 0xFF23282C;
    private static final int TRACK = 0xFF000000;
    private static final int PANEL = 0xE6101417;
    /** 用户要求：空血槽/残影用纯黑，和原版 BG 的深色底一致。 */
    private static final int SHADOW = 0xFF000000;
    private static final int HEALTH_LOW = 0xFFFF0000;
    private static final int HEALTH_MID = 0xFFFFA500;
    private static final int HEALTH_HIGH = 0xFF4E7258;

    // ---- 动画状态（原作 CharacterHealth） ----
    private static float barSize = 1f;
    private static float shadowSize = 1f;
    private static float shadowDelay;
    private static float pulseTimer;
    private static float pulsePhase;
    private static float lastTarget = Float.NaN;
    private static long lastFrameNanos;
    /** 头像取景中心（脚底往上多少格）；站/蹲/游泳切换时插值，避免瞬移。 */
    private static float headCenterSmooth = Float.NaN;
    /** 正在渲染右下角头像时置位：DivingSuitLayer 据此保留头盔（不要藏头）。 */
    private static boolean renderingPortrait;

    private BaroHud() {
    }

    public static boolean renderingPortrait() {
        return renderingPortrait;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.options.hideGui || minecraft.player.isSpectator()) {
            return;
        }
        // 箱子容器界面走的是"背包 + 箱子"两栏布局，右键箱子打开时不要生命/状态 HUD。
        if (minecraft.screen instanceof CrateScreen) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width < 320 || height < 200) {
            return;
        }
        float s = height / 1080f;

        ClientMedicalCache.Snapshot snapshot =
                ClientMedicalCache.snapshot(minecraft.player.getUUID());
        float vitality = snapshot == null ? 100f : snapshot.vitality();

        float delta = frameDelta();
        updateAnimation(vitality, delta);
        updateHeadFollow(minecraft.player, delta);
        // 先画信息框/头像（内部会 flush），再画血条和图标，保证血条压在信息框顶边上。
        drawPortrait(graphics, minecraft, width, height, s);
        drawBar(graphics, minecraft, width, height, s, vitality);
        drawStatusIcons(graphics, minecraft, snapshot, width, height, s);
    }

    /** 退出登录时清掉动画状态，下一局从满血、站立取景开始。 */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        headCenterSmooth = Float.NaN;
        lastFrameNanos = 0L;
        lastTarget = Float.NaN;
        barSize = 1f;
        shadowSize = 1f;
        shadowDelay = 0f;
        pulseTimer = 0f;
        pulsePhase = 0f;
    }

    /** 每帧真实时间差（秒），封顶 0.1 避免卡顿时动画跳变。 */
    private static float frameDelta() {
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L
                ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        return delta;
    }

    /** 头像镜头跟随眼睛高度：站立 1.62、蹲下 1.27、游泳/滑翔 0.4，切换时缓动。 */
    private static void updateHeadFollow(AbstractClientPlayer player, float delta) {
        double eyeHeight = player.getEyePosition(1f).y - player.getY();
        float target = (float) Mth.clamp(eyeHeight + 0.12, 0.2, 2.0);
        if (Float.isNaN(headCenterSmooth)) {
            headCenterSmooth = target;
            return;
        }
        headCenterSmooth += (target - headCenterSmooth) * Math.min(1f, delta * 6f);
    }

    /** 原作 {@code CharacterHealth.UpdateHUD}：条、残影、脉动。 */
    private static void updateAnimation(float vitality, float delta) {
        float target = Mth.clamp(vitality / 100f, 0f, 1f);
        if (vitality < 0f) {
            // 原作 MinVitality：倒地后条从 0 反向涨到 1（越接近真死越长）。
            target = Mth.clamp(1f - vitality / (float) AfflictionContainer.MIN_VITALITY, 0f, 1f);
        }

        if (!Float.isNaN(lastTarget) && vitality < lastTarget - 0.05f) {
            pulseTimer = 1f;
            pulsePhase = 0f;
            shadowDelay = 1f;
        }
        lastTarget = vitality;

        // 网络一秒一包，显示值用短 lerp 追上；残影按原作 1 条/秒追。
        barSize += (target - barSize) * Math.min(1f, delta * 12f);
        if (shadowDelay > 0f) {
            shadowDelay -= delta;
        } else if (barSize > shadowSize) {
            shadowSize = Math.min(shadowSize + delta, barSize);
        } else {
            shadowSize = Math.max(shadowSize - delta, barSize);
        }
        if (pulseTimer > 0f) {
            pulsePhase += delta * 5f;
            pulseTimer -= delta;
        }
    }

    private static void drawBar(GuiGraphics graphics, Minecraft minecraft, int width, int height,
                                float s, float vitality) {
        float infoRight = width - PADDING * 2 * s;
        float infoTop = height - PADDING * 2 * s - INFO_H * s;
        float left = infoRight - barWidth(s);
        float top = infoTop - (BAR_H - BAR_OVERLAP) * s;
        float right = infoRight;

        // 原作的脉动是整个条沿 Y 轴拉伸（1 → 1.5 倍）。
        float pulse = 1f + (Mth.sin(pulsePhase) + 1f) * 0.5f * Math.max(0f, pulseTimer) * 0.5f;
        float barH = BAR_H * s;
        float centerY = top + barH * 0.5f;
        float barTop = centerY - barH * pulse * 0.5f;
        float barBottom = centerY + barH * pulse * 0.5f;

        frame(graphics, left, barTop, right, barBottom);
        float inset = Math.max(2f, 4f * s);
        float innerLeft = left + inset;
        float innerRight = right - inset;
        float innerTop = barTop + inset;
        float innerBottom = barBottom - inset;
        graphics.fill(Math.round(innerLeft), Math.round(innerTop),
                Math.round(innerRight), Math.round(innerBottom), TRACK);

        float trackWidth = innerRight - innerLeft;
        float shadowW = Math.round(trackWidth * Mth.clamp(shadowSize, 0f, 1f));
        if (shadowW > 0f) {
            graphics.fill(Math.round(innerLeft), Math.round(innerTop),
                    Math.round(innerLeft + shadowW), Math.round(innerBottom), SHADOW);
        }

        float fillW = Math.round(trackWidth * Mth.clamp(barSize, 0f, 1f));
        if (fillW > 0f) {
            int color = healthColor(Mth.clamp(vitality / 100f, 0f, 1f));
            int fillRight = Math.round(innerLeft + fillW);
            graphics.fill(Math.round(innerLeft), Math.round(innerTop), fillRight,
                    Math.round(innerBottom), color);
            // 上亮下暗，模拟原作金属条的倒角。
            graphics.fill(Math.round(innerLeft), Math.round(innerTop), fillRight,
                    Math.round(innerTop + Math.max(1f, s)), lighten(color, 40));
            graphics.fill(Math.round(innerLeft), Math.round(innerBottom - Math.max(1f, s)),
                    fillRight, Math.round(innerBottom), darken(color, 45));
        }
    }

    private static void drawStatusIcons(GuiGraphics graphics, Minecraft minecraft,
                                        ClientMedicalCache.Snapshot snapshot,
                                        int width, int height, float s) {
        if (snapshot == null) {
            return;
        }
        List<Status> statuses = collectStatuses(snapshot);
        if (statuses.isEmpty()) {
            return;
        }
        float infoRight = width - PADDING * 2 * s;
        float infoTop = height - PADDING * 2 * s - INFO_H * s;
        float barTop = infoTop - (BAR_H - BAR_OVERLAP) * s;
        float right = infoRight;
        float top = barTop - PADDING * s - ICON_SIZE * s;
        float iconSize = ICON_SIZE * s;
        float step = (ICON_SIZE + ICON_GAP) * s;
        int maxIcons = Math.max(1, (int) Math.floor((barWidth(s) + ICON_GAP * s) / step));
        boolean overflow = statuses.size() > maxIcons;
        int shown = overflow ? maxIcons - 1 : statuses.size();

        Font font = minecraft.font;
        int index = 0;
        for (int i = 0; i < shown; i++) {
            Status status = statuses.get(i);
            float x = right - (index + 1) * step + ICON_GAP * s;
            drawStatusIcon(graphics, font, x, top, iconSize, s, status.label(), status.color());
            index++;
        }
        if (overflow) {
            int hidden = statuses.size() - shown;
            float x = right - (index + 1) * step + ICON_GAP * s;
            drawStatusIcon(graphics, font, x, top, iconSize, s, "+" + hidden, FRAME_LIGHT);
        }
    }

    private static float barWidth(float s) {
        return INFO_W * s + BAR_FRAME_W * Math.min(s, 1f);
    }

    private static void drawStatusIcon(GuiGraphics graphics, Font font, float x, float y,
                                       float size, float s, String label, int color) {
        int l = Math.round(x);
        int t = Math.round(y);
        int r = Math.round(x + size);
        int b = Math.round(y + size);
        graphics.fill(l, t, r, b, 0xCC0A0C0E);
        graphics.fill(l + 1, t + 1, r - 1, b - 1, 0xCC171C1F);
        frameLine(graphics, l, t, r, b, color);
        if (size < 8f || label.isEmpty()) {
            return;
        }
        // 图标可能比两个字窄（血条压扁/低分辨率），整体缩放文字而不是把“缺氧”截成一个字。
        int colorText = color | 0xFF000000;
        float textScale = Math.min(1f, (size - 2f) / Math.max(1, font.width(label)));
        com.mojang.blaze3d.vertex.PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((l + r) * 0.5f, (t + b) * 0.5f, 0f);
        pose.scale(textScale, textScale, 1f);
        graphics.drawString(font, label, -font.width(label) / 2,
                -font.lineHeight / 2 + 1, colorText, false);
        pose.popPose();
    }

    private static void drawPortrait(GuiGraphics graphics, Minecraft minecraft,
                                     int width, int height, float s) {
        float infoW = barWidth(s);
        float infoH = INFO_H * s;
        float infoLeft = width - PADDING * 2 * s - infoW;
        float infoTop = height - PADDING * 2 * s - infoH;
        float infoRight = infoLeft + infoW;
        float infoBottom = infoTop + infoH;

        int left = Math.round(infoLeft);
        int top = Math.round(infoTop);
        int right = Math.round(infoRight);
        int bottom = Math.round(infoBottom);
        graphics.fill(left, top, right, bottom, PANEL);

        AbstractClientPlayer player = minecraft.player;
        if (player != null) {
            int inset = Math.max(2, Math.round(3 * s));
            renderPlayerHead(graphics, player,
                    left + inset, top + inset, right - inset, bottom - inset);
        }
        frame(graphics, infoLeft, infoTop, infoRight, infoBottom);
    }

    /**
     * 把玩家渲染在头像框里，只露头：镜头对准头部、按框大小放大，再用 scissor 裁掉身体。
     *
     * <p>身体固定朝镜头，但**头部不锁定**——保留玩家当前的转头/俯仰，
     * 这样看哪边头像就朝哪边，而不是永远正对玩家。</p>
     */
    private static void renderPlayerHead(GuiGraphics graphics, AbstractClientPlayer player,
                                         int x1, int y1, int x2, int y2) {
        int size = Math.min(x2 - x1, y2 - y1);
        if (size < 12) {
            return;
        }
        float cx = (x1 + x2) * 0.5f;
        float cy = (y1 + y2) * 0.5f;
        int scale = Math.round(size * 1.32f);
        // 跟随当前姿态（更新在 updateHeadFollow，带缓动）。
        float headCenter = Float.isNaN(headCenterSmooth) ? 1.75f : headCenterSmooth;

        float bodyRot = player.yBodyRot;
        float bodyRotO = player.yBodyRotO;
        float headRot = player.yHeadRot;
        float headRotO = player.yHeadRotO;
        float headOffset = Mth.wrapDegrees(player.yHeadRot - player.yBodyRot);
        player.yBodyRot = 180f;
        player.yBodyRotO = 180f;
        player.yHeadRot = 180f + headOffset;
        player.yHeadRotO = player.yHeadRot;

        renderingPortrait = true;
        graphics.enableScissor(x1, y1, x2, y2);
        try {
            // 头部中心按当前眼高计算；rotateZ(PI) 让世界 Y 向上映射到屏幕上方。
            InventoryScreen.renderEntityInInventory(graphics,
                    Math.round(cx), Math.round(cy + headCenter * scale), scale,
                    new Quaternionf().rotateZ((float) Math.PI), null, player);
        } finally {
            graphics.disableScissor();
            renderingPortrait = false;
            player.yBodyRot = bodyRot;
            player.yBodyRotO = bodyRotO;
            player.yHeadRot = headRot;
            player.yHeadRotO = headRotO;
        }
    }

    private static List<Status> collectStatuses(ClientMedicalCache.Snapshot snapshot) {
        Map<String, Double> strengths = new LinkedHashMap<>();
        for (String row : snapshot.rows()) {
            if (row == null || row.startsWith("t|")) {
                continue;
            }
            String[] parts = row.split("\\|");
            if (parts.length < 2) {
                continue;
            }
            double strength;
            try {
                strength = Double.parseDouble(parts[parts.length - 1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (strength > 0.0) {
                strengths.merge(parts[0], strength, Double::sum);
            }
        }
        List<Status> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : strengths.entrySet()) {
            AfflictionType type = AfflictionType.byId(entry.getKey());
            if (type == null || entry.getValue() < Math.max(0.01, type.showThreshold())) {
                continue;
            }
            Status def = describe(entry.getKey());
            result.add(new Status(def.label(), def.color(), entry.getValue(),
                    type.vitalityDecrease(entry.getValue(), 100.0)));
        }
        result.sort(Comparator.comparingDouble(Status::drain).reversed()
                .thenComparing(Comparator.comparingDouble(Status::strength).reversed()));
        return result;
    }

    private record Status(String label, int color, double strength, double drain) {
    }

    /** 病症 id → HUD 图标短名/颜色（原作图标是位图，这里用两字标签代替）。 */
    private static Status describe(String id) {
        int color = switch (id) {
            case "blunttrauma" -> 0xFFC46A3A;
            case "bleeding" -> 0xFFD9433F;
            case "burn" -> 0xFFE07A2A;
            case "bloodloss" -> 0xFF9E2B2B;
            case "oxygenlow" -> 0xFF43B7C7;
            case "stun" -> 0xFFE0C44A;
            case "opiateoverdose" -> 0xFF7BA05B;
            case "opiateaddiction" -> 0xFF8C8F52;
            case "opiatewithdrawal" -> 0xFF9A8A55;
            case "organdamage" -> 0xFFB060C0;
            case "radiationsickness" -> 0xFF9BD13C;
            default -> 0xFFB0B6BB;
        };
        String key = switch (id) {
            case "blunttrauma", "bleeding", "burn", "bloodloss", "oxygenlow", "stun",
                    "opiateoverdose", "opiateaddiction", "opiatewithdrawal",
                    "organdamage", "radiationsickness" -> id;
            default -> "unknown";
        };
        String label = net.minecraft.network.chat.Component
                .translatable("hud.barotrauma.status." + key).getString();
        return new Status(label, color, 0, 0);
    }

    /** 原作 {@code GradientLerp(vitality, Low, Medium, High)}。 */
    private static int healthColor(float t) {
        if (t <= 0f) {
            return HEALTH_LOW;
        }
        if (t >= 1f) {
            return HEALTH_HIGH;
        }
        float num = t * 2f;
        return num < 1f ? lerpColor(HEALTH_LOW, HEALTH_MID, num)
                : lerpColor(HEALTH_MID, HEALTH_HIGH, num - 1f);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Mth.clamp(t, 0f, 1f);
        int a = channelLerp(from >> 24, to >> 24, clamped);
        int r = channelLerp(from >> 16, to >> 16, clamped);
        int g = channelLerp(from >> 8, to >> 8, clamped);
        int b = channelLerp(from, to, clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channelLerp(int from, int to, float t) {
        int a = (int) Math.round(((from & 0xFF) * (1f - t) + (to & 0xFF) * t));
        return Math.max(0, Math.min(255, a));
    }

    private static int lighten(int color, int amount) {
        return lerpColor(color, 0xFFFFFFFF, amount / 255f);
    }

    private static int darken(int color, int amount) {
        return lerpColor(color, 0xFF000000, amount / 255f);
    }

    /** 金属边框：外深、内浅、顶亮、底暗。 */
    private static void frame(GuiGraphics graphics, float left, float top, float right, float bottom) {
        int l = Math.round(left);
        int t = Math.round(top);
        int r = Math.round(right);
        int b = Math.round(bottom);
        graphics.fill(l, t, r, t + 1, FRAME_LIGHT);
        graphics.fill(l, b - 1, r, b, FRAME_DARK);
        graphics.fill(l, t, l + 1, b, FRAME_LIGHT);
        graphics.fill(r - 1, t, r, b, FRAME_DARK);
        graphics.fill(l + 1, t + 1, r - 1, t + 2, FRAME);
        graphics.fill(l + 1, b - 2, r - 1, b - 1, FRAME);
    }

    private static void frameLine(GuiGraphics graphics, int l, int t, int r, int b, int color) {
        graphics.fill(l, t, r, t + 1, color);
        graphics.fill(l, b - 1, r, b, color);
        graphics.fill(l, t, l + 1, b, color);
        graphics.fill(r - 1, t, r, b, color);
    }
}
