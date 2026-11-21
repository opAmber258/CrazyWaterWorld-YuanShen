package com.opamb.barotrauma.ship;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 舱顶净高判定：站得下就站，站不下才保持游泳碰撞箱。 */
class ShipWaterBoxesTest {

    /** 3 格高舱室：y=0..2 是空气，天花板在 y=3。 */
    private static final float[] THREE_HIGH = {0, 0, 0, 0, 2, 0, 0};
    /** 2 格高舱室：站立（1.8）刚好放得下，跳起来就不行。 */
    private static final float[] TWO_HIGH = {0, 0, 0, 0, 1, 0, 0};

    @Test
    @DisplayName("3 格高：脚在舱底能站，顶到天花板就不能站")
    void threeHighRoom() {
        assertEquals(Boolean.TRUE, ShipWaterBoxes.hasStandingRoom(
                THREE_HIGH, 0.5D, 0.0D, 0.5D, 1.8D));
        assertEquals(Boolean.FALSE, ShipWaterBoxes.hasStandingRoom(
                THREE_HIGH, 0.5D, 1.5D, 0.5D, 1.8D),
                "脚在 1.5 时站立碰撞箱顶到 3.3，超过天花板 3");
    }

    @Test
    @DisplayName("2 格高：地面能站，离地 0.3 格就放不下")
    void twoHighRoom() {
        assertEquals(Boolean.TRUE, ShipWaterBoxes.hasStandingRoom(
                TWO_HIGH, 0.5D, 0.0D, 0.5D, 1.8D));
        assertEquals(Boolean.FALSE, ShipWaterBoxes.hasStandingRoom(
                TWO_HIGH, 0.5D, 0.3D, 0.5D, 1.8D));
    }

    @Test
    @DisplayName("上下两段空气：按脚所在那一段算天花板；中间实心格返回 null")
    void stackedRuns() {
        float[] boxes = {
                0, 0, 0, 0, 1, 0, 0,   // y=0..1
                0, 3, 0, 0, 4, 0, 0    // y=3..4，中间 y=2 是楼板
        };
        assertEquals(Boolean.TRUE, ShipWaterBoxes.hasStandingRoom(
                boxes, 0.5D, 0.0D, 0.5D, 1.8D));
        assertEquals(Boolean.TRUE, ShipWaterBoxes.hasStandingRoom(
                boxes, 0.5D, 3.0D, 0.5D, 1.8D));
        assertNull(ShipWaterBoxes.hasStandingRoom(
                boxes, 0.5D, 2.5D, 0.5D, 1.8D),
                "楼板里不在任何空气段，交给原版判定");
    }

    @Test
    @DisplayName("不在任何水盒里返回 null（船外/甲板）")
    void outsideReturnsNull() {
        assertNull(ShipWaterBoxes.hasStandingRoom(
                THREE_HIGH, 5.0D, 0.0D, 5.0D, 1.8D));
    }

    @Test
    @DisplayName("离地高度：贴舱底为 0，浮起来就是实际高度，盒外 NaN")
    void floorDistance() {
        assertEquals(0.0D, ShipWaterBoxes.floorDistance(
                THREE_HIGH, 0.5D, 0.0D, 0.5D), 1.0E-6D);
        assertEquals(1.5D, ShipWaterBoxes.floorDistance(
                THREE_HIGH, 0.5D, 1.5D, 0.5D), 1.0E-6D);
        assertTrue(Double.isNaN(ShipWaterBoxes.floorDistance(
                THREE_HIGH, 5.0D, 0.0D, 5.0D)));
    }
}
