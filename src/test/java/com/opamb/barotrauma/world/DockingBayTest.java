package com.opamb.barotrauma.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 哨站—潜艇对接口的几何约束：10 格通道、上下舱口对齐、出生点在艇内。 */
class DockingBayTest {

    @Test
    @DisplayName("哨站地板到潜艇顶舱口正好 10 格")
    void dockGapIsTenBlocks() {
        assertEquals(-64, DockingBay.STATION_FLOOR_Y);
        assertEquals(-65, DockingBay.STATION_HATCH_Y);
        assertEquals(-74, DockingBay.SUB_HATCH_Y);
        assertEquals(10, DockingBay.STATION_FLOOR_Y - DockingBay.SUB_HATCH_Y);
        assertTrue(DockingBay.TUBE_BOTTOM_Y > DockingBay.SUB_HATCH_Y, "通道必须位于两道舱口之间");
        assertTrue(DockingBay.TUBE_TOP_Y < DockingBay.STATION_HATCH_Y, "通道顶部应在站内舱口下方");
    }

    @Test
    @DisplayName("通道要同时打穿两层地板，且与舱口连续（issue #22）")
    void shaftCutsBothFloorLayers() {
        assertEquals(-63, DockingBay.STATION_FLOOR_UPPER_Y);
        assertEquals(-64, DockingBay.STATION_FLOOR_Y);
        assertEquals(-65, DockingBay.STATION_HATCH_Y);
        // -63 → -64 → -65 必须连续：站内(-62 起)到通道无遮挡。
        assertEquals(DockingBay.STATION_FLOOR_UPPER_Y - 1, DockingBay.STATION_FLOOR_Y);
        assertEquals(DockingBay.STATION_FLOOR_Y - 1, DockingBay.STATION_HATCH_Y);
    }

    @Test
    @DisplayName("潜艇顶部舱口正对通道，出生点在艇内")
    void hatchAlignsAndSpawnIsInside() {
        assertTrue(DockingBay.inTube(0, 0));
        assertTrue(DockingBay.inTube(-1, 0));
        assertFalse(DockingBay.inTube(2, 0));
        assertFalse(DockingBay.inTube(0, 1));

        var spawn = StarterSub.spawnPos();
        assertTrue(spawn.x > StarterSub.ORIGIN_X && spawn.x < StarterSub.ORIGIN_X + StarterSub.SIZE_X);
        assertTrue(spawn.z > StarterSub.ORIGIN_Z && spawn.z < StarterSub.ORIGIN_Z + StarterSub.SIZE_Z);
        assertTrue(spawn.y > StarterSub.ORIGIN_Y && spawn.y < StarterSub.ORIGIN_Y + StarterSub.SIZE_Y);
        // 潜艇顶部舱口的 y 必须等于 DockingBay 定义的对接面
        assertEquals(DockingBay.SUB_HATCH_Y, StarterSub.ORIGIN_Y + StarterSub.SIZE_Y - 1);
    }

    @Test
    @DisplayName("潜艇水平中心对准通道中心")
    void subIsCenteredUnderPort() {
        double centerX = StarterSub.ORIGIN_X + StarterSub.SIZE_X / 2.0D;
        double centerZ = StarterSub.ORIGIN_Z + StarterSub.SIZE_Z / 2.0D;
        assertEquals(DockingBay.X + 0.5D, centerX, 0.01D);
        assertEquals(DockingBay.Z + 0.5D, centerZ, 0.01D);
    }
}
