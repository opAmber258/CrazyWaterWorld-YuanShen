package com.opamb.barotrauma.client.gui;

import net.minecraft.util.Mth;

/**
 * 界面缩放规范（所有模组自建界面统一走这里）：
 *
 * <ul>
 *   <li>每个界面有一个固定的<b>设计尺寸</b>（就是 {@code imageWidth × imageHeight}，
 *       也是子类绘制时用的坐标空间，左上角固定在 (0,0)）；</li>
 *   <li>显示时按 GUI 可用空间等比缩放：{@code min(fitW, fitH)}，夹在
 *       {@link #MIN}..{@link #MAX} 之间；</li>
 *   <li>面板在屏幕上居中，四周留 {@link #MARGIN} 边距；</li>
 *   <li>鼠标坐标统一换算回设计坐标后再交给原版逻辑，所以槽位、拖拽、Shift 点击、
 *       按钮命中都不用额外处理。</li>
 * </ul>
 *
 * <p>不允许子类自己再算一套 scale —— 新界面只要继承
 * {@link ScaledContainerScreen} / {@link ScaledScreen} 就自动带上。</p>
 */
public final class GuiScale {

    /** 面板四周留的屏幕像素边距。 */
    public static final float MARGIN = 12.0F;
    /** 最小缩放（再小字就糊了）。 */
    public static final float MIN = 0.30F;
    /** 最大缩放（大屏/GUI Scale 1 时界面更饱满）。 */
    public static final float MAX = 1.50F;

    private GuiScale() {
    }

    /** 按屏幕可用空间和设计尺寸算缩放倍率。 */
    public static float factor(int screenWidth, int screenHeight, int designWidth, int designHeight) {
        float fitW = (screenWidth - MARGIN * 2.0F) / Math.max(1, designWidth);
        float fitH = (screenHeight - MARGIN * 2.0F) / Math.max(1, designHeight);
        return Mth.clamp(Math.min(fitW, fitH), MIN, MAX);
    }

    /** 缩放后面板左上角的屏幕坐标（水平居中）。 */
    public static int offsetX(int screenWidth, int designWidth, float scale) {
        return Math.round((screenWidth - designWidth * scale) * 0.5F);
    }

    /** 缩放后面板左上角的屏幕坐标（垂直居中）。 */
    public static int offsetY(int screenHeight, int designHeight, float scale) {
        return Math.round((screenHeight - designHeight * scale) * 0.5F);
    }

    /** 屏幕 X → 设计 X。 */
    public static double toDesignX(double screenX, int offsetX, float scale) {
        return (screenX - offsetX) / scale;
    }

    /** 屏幕 Y → 设计 Y。 */
    public static double toDesignY(double screenY, int offsetY, float scale) {
        return (screenY - offsetY) / scale;
    }
}
