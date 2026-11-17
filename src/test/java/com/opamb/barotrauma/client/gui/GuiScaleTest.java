package com.opamb.barotrauma.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GuiScale} 的纯数学部分：所有模组界面缩放的上/下限和居中偏移。
 *
 * <p>这些数字就是玩家实际看到的界面大小，改之前先看 docs/GUI_SCALING_CONVENTION.md。</p>
 */
class GuiScaleTest {

    private static final float EPS = 1.0E-4F;

    @Test
    void 大屏按设计尺寸放大但不超过上限() {
        // 960×540 的 GUI 空间放 440×336 的面板：fitH = (540-24)/336 = 1.535…
        float scale = GuiScale.factor(960, 540, 440, 336);
        assertEquals(1.5F, scale, EPS, "大屏最多放大到 1.50");

        float huge = GuiScale.factor(4000, 3000, 254, 216);
        assertEquals(GuiScale.MAX, huge, EPS);
    }

    @Test
    void 小屏缩到下限为止() {
        // 100×100 的极端窗口放 620×340：算出来 0.12，被夹到 0.30。
        float scale = GuiScale.factor(100, 100, 620, 340);
        assertEquals(GuiScale.MIN, scale, EPS, "再小也不会低于 0.30");
    }

    @Test
    void 按更紧的一边决定倍率() {
        // 宽度够、高度不够 → 取高度。
        float wide = GuiScale.factor(2000, 300, 200, 200);
        assertEquals((300 - 24.0F) / 200.0F, wide, EPS);
    }

    @Test
    void 面板始终居中且四周留边距() {
        int designW = 440;
        int designH = 336;
        float scale = GuiScale.factor(960, 540, designW, designH);
        int x = GuiScale.offsetX(960, designW, scale);
        int y = GuiScale.offsetY(540, designH, scale);
        assertTrue(x >= GuiScale.MARGIN - 1, "左边距不足");
        assertTrue(y >= GuiScale.MARGIN - 1, "上边距不足");
        // 左右、上下留白基本对称（允许 1px 取整误差）。
        assertTrue(Math.abs(x - (960 - x - designW * scale)) <= 1.0F);
        assertTrue(Math.abs(y - (540 - y - designH * scale)) <= 1.0F);
    }

    @Test
    void 屏幕坐标换算回设计坐标可逆() {
        float scale = GuiScale.factor(800, 600, 440, 336);
        int ox = GuiScale.offsetX(800, 440, scale);
        int oy = GuiScale.offsetY(600, 336, scale);
        double designX = 123.0D;
        double screenX = ox + designX * scale;
        assertEquals(designX, GuiScale.toDesignX(screenX, ox, scale), 1.0E-3D);
        double designY = 77.0D;
        double screenY = oy + designY * scale;
        assertEquals(designY, GuiScale.toDesignY(screenY, oy, scale), 1.0E-3D);
    }
}
