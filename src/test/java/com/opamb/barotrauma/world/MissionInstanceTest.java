package com.opamb.barotrauma.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务海域实例的锚点布局与洞穴形状（纯数学，不生成区块）。 */
class MissionInstanceTest {

    private static final long SEED = 0x1234567890ABCDEFL;

    @Test
    @DisplayName("锚点按轮次递增、间距固定、互不重叠")
    void anchorsAreSpaced() {
        MissionInstance.Anchor first = MissionInstance.anchorForRound(0);
        MissionInstance.Anchor second = MissionInstance.anchorForRound(1);
        assertEquals(MissionInstance.BASE_X, first.x());
        assertEquals(MissionInstance.BASE_X + MissionInstance.SPACING, second.x());
        assertEquals(MissionInstance.ANCHOR_Y, first.y());
        assertEquals(0, first.z());
        assertTrue(MissionInstance.SPACING > MissionInstance.RX * 2, "间距必须大于洞穴直径");
        assertEquals(first, MissionInstance.anchorForRound(0), "同一轮次必须是同一锚点");
        assertEquals(0, MissionInstance.anchorForRound(-3).slot(), "负轮次按 0 处理");
    }

    @Test
    @DisplayName("实例种子只跟 slot 有关")
    void seedIsStablePerSlot() {
        assertEquals(MissionInstance.seedFor(SEED, 3), MissionInstance.seedFor(SEED, 3));
        assertNotEquals(MissionInstance.seedFor(SEED, 3), MissionInstance.seedFor(SEED, 4));
        assertNotEquals(MissionInstance.seedFor(SEED, 0), MissionInstance.seedFor(SEED + 1, 0));
    }

    @Test
    @DisplayName("洞顶/洞底始终落在声明的范围里")
    void ceilingAndFloorStayInRange() {
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(0);
        for (int x = anchor.x() - MissionInstance.RX; x <= anchor.x() + MissionInstance.RX; x += 17) {
            for (int z = anchor.z() - MissionInstance.RZ; z <= anchor.z() + MissionInstance.RZ; z += 19) {
                int ceiling = MissionInstance.ceilingY(SEED, anchor, x, z);
                int floor = MissionInstance.floorY(SEED, anchor, x, z);
                assertTrue(ceiling <= MissionInstance.CEILING_BASE
                                && ceiling >= MissionInstance.CEILING_BASE - MissionInstance.CEILING_WOBBLE,
                        "洞顶越界 @" + x + "," + z + " = " + ceiling);
                assertTrue(floor <= MissionInstance.FLOOR_BASE
                                && floor >= MissionInstance.FLOOR_BASE - MissionInstance.FLOOR_WOBBLE,
                        "洞底越界 @" + x + "," + z + " = " + floor);
                assertTrue(floor + 20 < ceiling, "洞高至少要能放下潜艇 @" + x + "," + z);
            }
        }
    }

    @Test
    @DisplayName("船位是洞内空气，洞外/岩壁不是")
    void spawnIsInsideCave() {
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(0);
        assertTrue(MissionInstance.isCaveAir(SEED, anchor, anchor.x(), anchor.y(), anchor.z()),
                "锚点必须是空气");
        assertTrue(MissionInstance.isSpawnClearance(anchor, anchor.x(), anchor.y(), anchor.z()));
        assertFalse(MissionInstance.isSpawnClearance(anchor,
                        anchor.x() + MissionInstance.SPAWN_CLEAR_RADIUS + 2, anchor.y(), anchor.z()),
                "净空球外不能再算净空");
        assertFalse(MissionInstance.isCaveAir(SEED, anchor,
                        anchor.x() + MissionInstance.RX * 3, anchor.y(), anchor.z()),
                "远处不能算洞内");
        assertFalse(MissionInstance.isCaveAir(SEED, anchor, anchor.x(), anchor.y() + 100, anchor.z()),
                "洞顶以上不能算洞内");
    }

    @Test
    @DisplayName("洞壁比洞口更外，且影响半径包住整个洞壁")
    void rimEnclosesCave() {
        assertTrue(MissionInstance.RIM > 1.0D);
        assertTrue(MissionInstance.OUTER >= MissionInstance.RX * MissionInstance.RIM,
                "区块影响范围必须覆盖洞壁外沿");
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(0);
        // 正东方向：洞内边界附近半径必然 <=1，洞壁带 >=1
        double inside = MissionInstance.radiusAt(SEED, anchor,
                anchor.x() + MissionInstance.RX - 64, anchor.z());
        assertTrue(inside <= 1.0D, "洞内半径应 <=1，实际 " + inside);
    }
}
