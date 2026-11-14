package com.opamb.barotrauma.world;

import com.opamb.barotrauma.world.level.LevelParams;
import com.opamb.barotrauma.world.level.LevelType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 新的开阔海域高度场：哨站台地、洞顶/洞底范围、POI 净空、竖井已撤。 */
class DeepSeaTerrainTest {

    private static final long SEED = 0x5EED1234L;

    @BeforeEach
    void resetBefore() {
        clearRoute();
    }

    @AfterEach
    void resetAfter() {
        clearRoute();
    }

    private static void clearRoute() {
        MissionInstance.setRouteLevel(null);
        MissionInstance.setRouteOutposts(Integer.MIN_VALUE, true, true);
    }

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

    @Test
    @DisplayName("活动关卡是随机上下跳的大洞窟：中心 400+ 格高、网络外是岩、站点在洞里")
    void levelRouteCarvesWindingTunnel() {
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(8);
        MissionInstance.setRouteOutposts(8, true, true);
        LevelParams advanced = LevelType.ADVANCED.params();
        MissionInstance.setRouteLevel(new MissionInstance.RouteLevel(
                8, LevelType.ADVANCED, 55L, 9, advanced.routeLength()));

        RoutePath path = MissionInstance.activePath();
        assertNotNull(path, "活动关卡必须生成主航道");
        RoutePath.Point[] points = path.points();
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (RoutePath.Point point : points) {
            minY = Math.min(minY, point.y());
            maxY = Math.max(maxY, point.y());
        }
        assertTrue(maxY - minY > 100.0D,
                "主航道必须像原作一样上下跳，实际高度差 " + (maxY - minY));

        RoutePath.Point mid = path.pointAt(0.5);
        int cx = (int) Math.round(mid.x());
        int cz = (int) Math.round(mid.z());
        int ceiling = SeaTerrain.ceilingY(SEED, cx, cz);
        int floor = SeaTerrain.floorY(SEED, cx, cz);
        assertTrue(ceiling - floor >= 300,
                "洞窟中心必须是大海域尺度，实际高度 " + (ceiling - floor));
        assertTrue(ceiling <= -44, "洞顶不能高过 -44");
        assertTrue(SeaTerrain.inClearZone(cx, cz), "主航道必须净空");

        // 主洞半宽 300、侧隧道最远约 560；620 格处仍在关卡影响范围内但是岩壁。
        int sideAX = cx + 620;
        int sideBX = cx - 620;
        boolean solidA = SeaTerrain.ceilingY(SEED, sideAX, cz)
                <= SeaTerrain.floorY(SEED, sideAX, cz);
        boolean solidB = SeaTerrain.ceilingY(SEED, sideBX, cz)
                <= SeaTerrain.floorY(SEED, sideBX, cz);
        assertTrue(solidA || solidB, "隧道网络外必须有岩壁");

        int stationCeiling = SeaTerrain.ceilingY(SEED, anchor.x(), anchor.z());
        int stationFloor = SeaTerrain.floorY(SEED, anchor.x(), anchor.z());
        assertEquals(-44, stationCeiling, "目的站顶板仍是 -44");
        assertTrue(stationCeiling - stationFloor >= 200,
                "目的站必须落在洞窟里，实际高度 " + (stationCeiling - stationFloor));

        int farFloor = SeaTerrain.floorY(SEED, cx + 2000, cz);
        assertTrue(farFloor <= -500 && farFloor >= -544,
                "离开关卡影响范围应回退全局海底，实际 " + farFloor);
    }
}
