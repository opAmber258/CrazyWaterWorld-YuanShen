package com.opamb.barotrauma.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 新的开阔海域高度场：哨站台地、洞顶/洞底范围、POI 净空、竖井已撤。 */
class DeepSeaTerrainTest {

    private static final long SEED = 0x5EED1234L;

    @Test
    @DisplayName("哨站核心区顶板压平在 -44，海底按世界噪声（无岩台）")
    void outpostRoofIsFlat() {
        boolean floorVaries = false;
        int firstFloor = SeaTerrain.floorY(SEED, -40, -40);
        for (int x = -40; x <= 40; x += 8) {
            for (int z = -40; z <= 40; z += 8) {
                assertEquals(-44, SeaTerrain.ceilingY(SEED, x, z),
                        "哨站顶板 @" + x + "," + z);
                int floor = SeaTerrain.floorY(SEED, x, z);
                assertTrue(floor <= -500 && floor >= -544, "哨站下方海底越界 @" + x + "," + z + " = " + floor);
                floorVaries |= floor != firstFloor;
            }
        }
        assertTrue(floorVaries, "去掉岩台后，哨站下方海底应随世界噪声起伏");
    }

    @Test
    @DisplayName("洞顶不高于 -44，洞底在 -544..-500")
    void terrainStaysInRange() {
        for (int x = -600; x <= 600; x += 37) {
            for (int z = -600; z <= 600; z += 41) {
                if (Math.max(Math.abs(x), Math.abs(z)) <= SeaTerrain.OUTPOST_BLEND) {
                    continue; // 哨站台地到世界岩床的渐变带
                }
                int ceiling = SeaTerrain.ceilingY(SEED, x, z);
                int floor = SeaTerrain.floorY(SEED, x, z);
                assertTrue(ceiling <= -44, "洞顶太浅 @" + x + "," + z + " = " + ceiling);
                assertTrue(ceiling >= -164, "洞顶太深 @" + x + "," + z + " = " + ceiling);
                assertTrue(floor <= -500 && floor >= -544,
                        "洞底越界 @" + x + "," + z + " = " + floor);
                assertTrue(ceiling - floor <= 1000, "开阔高度超过 1000 @" + x + "," + z);
            }
        }
    }

    @Test
    @DisplayName("哨站和 POI 周围不生成岩柱，周围强制留空")
    void clearZonesProtectStructures() {
        assertTrue(SeaTerrain.inClearZone(0, 0));
        assertTrue(SeaTerrain.inClearZone(60, 0));
        assertTrue(SeaTerrain.inClearZone(140, 0), "哨站东侧出口通道必须留空");
        assertFalse(SeaTerrain.inClearZone(400, 400));
        for (var poi : new net.minecraft.core.BlockPos[]{
                PoiNetwork.WRECK_A, PoiNetwork.WRECK_B, PoiNetwork.WRECK_C, PoiNetwork.POST_B_TERMINAL}) {
            assertTrue(SeaTerrain.inClearZone(poi.getX(), poi.getZ()),
                    "POI 净空缺失 @" + poi);
            int ceiling = SeaTerrain.ceilingY(SEED, poi.getX(), poi.getZ());
            int floor = SeaTerrain.floorY(SEED, poi.getX(), poi.getZ());
            assertTrue(ceiling >= poi.getY() + 30, "POI 上方被岩壳埋住 @" + poi);
            assertTrue(floor <= poi.getY() - 30, "POI 下方被岩床埋住 @" + poi);
        }
    }

    @Test
    @DisplayName("寒冷深穴开阔高度上限 500 格")
    void openHeightIsFiveHundred() {
        assertEquals(500, SeaTerrain.MAX_OPEN_HEIGHT, "寒冷深穴应为 500 格高");
    }
}
