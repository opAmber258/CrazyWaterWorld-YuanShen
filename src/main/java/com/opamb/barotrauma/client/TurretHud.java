package com.opamb.barotrauma.client;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.jetbrains.annotations.Nullable;

/**
 * 炮塔独立 HUD：黑色观察窗边框 + 专属准星 + 方位/俯仰/装填状态。
 *
 * <p>只在 {@link TurretClient#isActive()} 时绘制；原版准星、热键栏和经验条由
 * {@link TurretClient#onRenderOverlay} 关掉，避免两个准星叠在一起。</p>
 *
 * <p>自由瞄准炮塔额外画一个可以拖到屏幕任意位置的光标：它是“引导点”，
 * 中心准星代表炮塔当前真实朝向，视角会限速朝光标转、光标随之收回中心。</p>
 */
public final class TurretHud implements IGuiOverlay {

    private static final int TEXT = 0xFFB9E8D8;
    private static final int TEXT_DIM = 0xFF7F9A92;
    private static final int READY = 0xFF8FF0D0;
    private static final int COOLING = 0xFFFF7A5C;
    private static final int PANEL = 0xA0101418;
    private static final int PANEL_BORDER = 0xFF2E4A48;
    private static final int FREE_CURSOR = 0xFFFFC46B;
    private static final int FREE_CURSOR_SHADOW = 0xB0201206;
    private static final int TRACKING_RETICLE = 0xFFFF8A5C;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!TurretClient.isActive()) {
            return;
        }
        int centerX = width / 2;
        int centerY = height / 2;
        boolean ready = TurretClient.cooldown() <= 0;
        int reticle = ready ? READY : COOLING;

        drawFrame(graphics, width, height);
        if (TurretClient.isFreeAim()) {
            // 先画鼠标拖动光标，炮塔准心随后覆盖上去：图层顺序 = 光标在下、炮台准心在上。
            drawFreeAim(graphics, width, height);
        }
        drawReticle(graphics, centerX, centerY, reticle, ready);
        TurretClient.ImpactPrediction impact =
                TurretClient.isTracking() ? TurretClient.predictedImpact() : null;
        if (impact != null) {
            drawTrackingImpact(graphics, width, height, impact);
        }
        drawPanel(graphics, width, height, ready, impact);
    }

    /** 观察窗式的暗色边框 + 四角支撑条。 */
    private static void drawFrame(GuiGraphics graphics, int width, int height) {
        int band = 14;
        graphics.fill(0, 0, width, band, 0xC006090B);
        graphics.fill(0, height - band, width, height, 0xC006090B);
        graphics.fill(0, 0, band, height, 0xC006090B);
        graphics.fill(width - band, 0, width, height, 0xC006090B);

        int inset = band + 4;
        int arm = 26;
        int color = 0x663B5A56;
        // 左上
        graphics.fill(inset, inset, inset + arm, inset + 1, color);
        graphics.fill(inset, inset, inset + 1, inset + arm, color);
        // 右上
        graphics.fill(width - inset - arm, inset, width - inset, inset + 1, color);
        graphics.fill(width - inset - 1, inset, width - inset, inset + arm, color);
        // 左下
        graphics.fill(inset, height - inset - 1, inset + arm, height - inset, color);
        graphics.fill(inset, height - inset - arm, inset + 1, height - inset, color);
        // 右下
        graphics.fill(width - inset - arm, height - inset - 1, width - inset, height - inset, color);
        graphics.fill(width - inset - 1, height - inset - arm, width - inset, height - inset, color);
    }

    private static void drawReticle(GuiGraphics graphics, int centerX, int centerY, int color, boolean ready) {
        int gap = 7;
        int length = 13;
        // 上
        graphics.fill(centerX, centerY - gap - length, centerX + 1, centerY - gap, color);
        // 下
        graphics.fill(centerX, centerY + gap, centerX + 1, centerY + gap + length, color);
        // 左
        graphics.fill(centerX - gap - length, centerY, centerX - gap, centerY + 1, color);
        // 右
        graphics.fill(centerX + gap, centerY, centerX + gap + length, centerY + 1, color);
        // 中心点
        graphics.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, color);

        // 装填条：冷却中显示进度，就绪时只剩一条细底线。
        int barWidth = 76;
        int barY = centerY + gap + length + 10;
        int left = centerX - barWidth / 2;
        graphics.fill(left - 1, barY - 1, left + barWidth + 1, barY + 3, 0x90000000);
        if (ready) {
            graphics.fill(left, barY, left + barWidth, barY + 2, 0x668FF0D0);
        } else {
            float progress = 1.0F - Mth.clamp(TurretClient.cooldown()
                    / (float) (TurretBlockEntity.FIRE_COOLDOWN_TICKS + 2), 0.0F, 1.0F);
            graphics.fill(left, barY, left + (int) (barWidth * progress), barY + 2, COOLING);
        }
    }

    /**
     * 自由瞄准光标：按角度偏移投影到屏幕上，画四角括号 + 中心点，并用虚线连到
     * 中心准星，直观显示“视角正在追这个点”。
     */
    private static void drawFreeAim(GuiGraphics graphics, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;
        float halfFovY = Minecraft.getInstance().options.fov().get() * 0.5F;
        float aspect = height > 0 ? width / (float) height : 16.0F / 9.0F;
        float halfFovX = (float) Math.toDegrees(Math.atan(
                Math.tan(Math.toRadians(halfFovY)) * aspect));
        float cursorX = centerX + TurretClient.cursorYawOffset() / halfFovX * centerX;
        float cursorY = centerY + TurretClient.cursorPitchOffset() / halfFovY * centerY;

        int steps = 14;
        for (int i = 2; i < steps; i += 2) {
            float t = i / (float) steps;
            int dotX = Math.round(Mth.lerp(t, centerX, cursorX));
            int dotY = Math.round(Mth.lerp(t, centerY, cursorY));
            graphics.fill(dotX, dotY, dotX + 2, dotY + 2, 0x55FFC46B);
        }

        int cursorPixelX = Math.round(cursorX);
        int cursorPixelY = Math.round(cursorY);
        int half = 8;
        int arm = 4;
        // 先画一层暗色描边，深色海底背景上也能看清。
        drawBrackets(graphics, cursorPixelX + 1, cursorPixelY + 1, half, arm, FREE_CURSOR_SHADOW);
        drawBrackets(graphics, cursorPixelX, cursorPixelY, half, arm, FREE_CURSOR);
    }

    /**
     * 跟随炮塔的落点准心：把本地射线算出的 3D 命中点投影到当前自由视角的屏幕上，
     * 用左右方括号标出。视角中心仍是原版中心准星；这个括号才是炮塔实际弹道落点。
     *
     * <p>括号按被命中物体的<b>屏幕投影尺寸</b>缩放：上下取物体投影高度（限幅到很短），
     * 左右取投影宽度并保证至少 3 倍于高度，所以永远是一个“上下短、左右长”的长方形；
     * 物体越大（或越近），长方形越长。</p>
     */
    private static void drawTrackingImpact(GuiGraphics graphics, int width, int height,
                                           TurretClient.ImpactPrediction prediction) {
        Vec3 camera = TurretClient.cameraPosition();
        if (camera == null) {
            return;
        }
        int centerX = width / 2;
        int centerY = height / 2;
        float halfFovY = Minecraft.getInstance().options.fov().get() * 0.5F;
        float aspect = height > 0 ? width / (float) height : 16.0F / 9.0F;
        float tanY = (float) Math.tan(Math.toRadians(halfFovY));
        float tanX = tanY * aspect;

        Vec3 forward = TurretClient.lookVector(TurretClient.viewYaw(), TurretClient.viewPitch());
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        Vec3 delta = prediction.point().subtract(camera);
        double depth = delta.dot(forward);
        if (depth <= 0.1D) {
            // 落点在相机背后（炮塔还在身后），不画。
            return;
        }
        int reticleX = Math.round(centerX
                + (float) ((delta.dot(right) / depth) / tanX * centerX));
        int reticleY = Math.round(centerY
                - (float) ((delta.dot(up) / depth) / tanY * centerY));
        reticleX = Mth.clamp(reticleX, 26, width - 26);
        reticleY = Mth.clamp(reticleY, 26, height - 26);

        // 物体半宽/半高按透视投到屏幕，换算成像素；竖边收窄，横边跟物体大小走。
        float halfWidthPx = (float) ((prediction.halfWidth() / depth) / tanX * centerX);
        float halfHeightPx = (float) ((prediction.halfHeight() / depth) / tanY * centerY);
        int halfHeight = Mth.clamp(Math.round(halfHeightPx), 3, 7);
        int minWidth = Math.round(halfHeight * 3.0F);
        int widthOffset = Mth.clamp(Math.round(halfWidthPx), minWidth, 34);
        int arm = Math.max(2, Math.round(halfHeight * 0.5F));

        // 中心到落点的虚线，直观显示炮塔落后多少。
        int steps = 16;
        for (int i = 2; i < steps; i += 2) {
            float t = i / (float) steps;
            int dotX = Math.round(Mth.lerp(t, centerX, reticleX));
            int dotY = Math.round(Mth.lerp(t, centerY, reticleY));
            graphics.fill(dotX, dotY, dotX + 2, dotY + 2, 0x55FF8A5C);
        }

        drawSquareBrackets(graphics, reticleX + 1, reticleY + 1,
                halfHeight, widthOffset, arm, FREE_CURSOR_SHADOW);
        drawSquareBrackets(graphics, reticleX, reticleY,
                halfHeight, widthOffset, arm, TRACKING_RETICLE);

        // 瞄准中心点：左边括号 + 中心点 + 右边括号，组成一个长方形。
        graphics.fill(reticleX - 1, reticleY - 1, reticleX + 2, reticleY + 2, FREE_CURSOR_SHADOW);
        graphics.fill(reticleX, reticleY, reticleX + 1, reticleY + 1, TRACKING_RETICLE);
    }

    /** 左右方括号 [ ]：两条竖线 + 上下短横臂。 */
    private static void drawSquareBrackets(GuiGraphics graphics, int cx, int cy,
                                           int halfHeight, int width, int arm, int color) {
        // [
        graphics.fill(cx - width, cy - halfHeight, cx - width + 1, cy + halfHeight, color);
        graphics.fill(cx - width, cy - halfHeight, cx - width + arm, cy - halfHeight + 1, color);
        graphics.fill(cx - width, cy + halfHeight - 1, cx - width + arm, cy + halfHeight, color);
        // ]
        graphics.fill(cx + width, cy - halfHeight, cx + width + 1, cy + halfHeight, color);
        graphics.fill(cx + width - arm, cy - halfHeight, cx + width, cy - halfHeight + 1, color);
        graphics.fill(cx + width - arm, cy + halfHeight - 1, cx + width, cy + halfHeight, color);
    }

    private static void drawBrackets(GuiGraphics graphics, int cx, int cy, int half, int arm, int color) {
        // 左上
        graphics.fill(cx - half, cy - half, cx - half + arm, cy - half + 1, color);
        graphics.fill(cx - half, cy - half, cx - half + 1, cy - half + arm, color);
        // 右上
        graphics.fill(cx + half - arm, cy - half, cx + half, cy - half + 1, color);
        graphics.fill(cx + half - 1, cy - half, cx + half, cy - half + arm, color);
        // 左下
        graphics.fill(cx - half, cy + half - 1, cx - half + arm, cy + half, color);
        graphics.fill(cx - half, cy + half - arm, cx - half + 1, cy + half, color);
        // 右下
        graphics.fill(cx + half - arm, cy + half - 1, cx + half, cy + half, color);
        graphics.fill(cx + half - 1, cy + half - arm, cx + half, cy + half, color);
        // 不画中心点：拖动光标只在四角留括号，中心让给炮塔准心。
    }

    private static void drawPanel(GuiGraphics graphics, int width, int height, boolean ready,
                                  @Nullable TurretClient.ImpactPrediction impact) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean freeAim = TurretClient.isFreeAim();
        boolean tracking = TurretClient.isTracking();
        boolean extra = freeAim || tracking;
        int panelX = 18;
        int panelY = 18;
        int panelWidth = extra ? 210 : 172;
        int panelHeight = tracking ? 72 : freeAim ? 60 : 48;
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, PANEL_BORDER);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, PANEL_BORDER);

        int textX = panelX + 7;
        Component title = Component.translatable(tracking
                ? "gui.barotrauma.turret.tracking.title"
                : freeAim
                ? "gui.barotrauma.turret.free_aim.title"
                : "gui.barotrauma.turret.title");
        graphics.drawString(minecraft.font, title, textX, panelY + 6, TEXT, true);

        String yaw = String.format("%.0f", Mth.wrapDegrees(TurretClient.aimYaw()));
        String pitch = String.format("%.0f", TurretClient.aimPitch());
        graphics.drawString(minecraft.font,
                Component.translatable("gui.barotrauma.turret.aim", yaw, pitch),
                textX, panelY + 19, TEXT_DIM, false);
        graphics.drawString(minecraft.font,
                Component.translatable("gui.barotrauma.turret.ammo"), textX, panelY + 31, TEXT_DIM, false);
        if (freeAim) {
            graphics.drawString(minecraft.font,
                    Component.translatable("gui.barotrauma.turret.free_aim.offset",
                            String.format("%.1f", TurretClient.cursorYawOffset()),
                            String.format("%.1f", TurretClient.cursorPitchOffset())),
                    textX, panelY + 43, TEXT_DIM, false);
        } else if (tracking) {
            float deltaYaw = Mth.wrapDegrees(TurretClient.aimYaw() - TurretClient.viewYaw());
            float deltaPitch = TurretClient.aimPitch() - TurretClient.viewPitch();
            graphics.drawString(minecraft.font,
                    Component.translatable("gui.barotrauma.turret.tracking.offset",
                            String.format("%.1f", deltaYaw),
                            String.format("%.1f", deltaPitch)),
                    textX, panelY + 43, TEXT_DIM, false);
            if (impact != null) {
                Vec3 camera = TurretClient.cameraPosition();
                double distance = camera != null ? camera.distanceTo(impact.point()) : 0.0D;
                graphics.drawString(minecraft.font,
                        Component.translatable("gui.barotrauma.turret.tracking.range",
                                String.format("%.1f", distance)),
                        textX, panelY + 55, TEXT_DIM, false);
            }
        }

        Component status = Component.translatable(ready
                ? "gui.barotrauma.turret.ready" : "gui.barotrauma.turret.reloading");
        graphics.drawString(minecraft.font, status, panelX + panelWidth - 7 - minecraft.font.width(status),
                panelY + 6, ready ? READY : COOLING, true);

        Component hint = Component.translatable(tracking
                ? "gui.barotrauma.turret.tracking.hint"
                : freeAim
                ? "gui.barotrauma.turret.free_aim.hint"
                : "gui.barotrauma.turret.hint");
        graphics.drawString(minecraft.font, hint, (width - minecraft.font.width(hint)) / 2,
                height - 30, TEXT_DIM, false);
    }
}
