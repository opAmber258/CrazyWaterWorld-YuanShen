package com.opamb.barotrauma.world;

import com.opamb.barotrauma.world.level.LevelParams;
import com.opamb.barotrauma.world.level.LevelType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务海域实例的锚点布局（地形已改用主世界开阔海域）。 */
class MissionInstanceTest {

    @BeforeEach
    void resetBefore() {
        clearRouteLevel();
    }

    @AfterEach
    void resetAfter() {
        clearRouteLevel();
    }

    private static void clearRouteLevel() {
        MissionInstance.setRouteLevel(null);
        MissionInstance.setRouteOutposts(Integer.MIN_VALUE, true, true);
    }

    @Test
    @DisplayName("锚点按轮次递增、间距固定、互不重叠")
    void anchorsAreSpaced() {
        MissionInstance.Anchor first = MissionInstance.anchorForRound(0);
        MissionInstance.Anchor second = MissionInstance.anchorForRound(1);
        assertEquals(MissionInstance.BASE_X, first.x());
        assertEquals(MissionInstance.BASE_X + MissionInstance.SPACING, second.x());
        assertEquals(MissionInstance.ANCHOR_Y, first.y());
        assertEquals(0, first.z());
        assertTrue(MissionInstance.SPACING > MissionInstance.SITE_RADIUS * 2,
                "间距必须大于两个站点净空");
        assertEquals(first, MissionInstance.anchorForRound(0), "同一轮次必须是同一锚点");
        assertEquals(0, MissionInstance.anchorForRound(-3).slot(), "负轮次按 0 处理");
    }

    @Test
    @DisplayName("站点识别只认锚点 64 格内")
    void siteLookupCoversClearZone() {
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(2);
        assertEquals(Integer.valueOf(anchor.x()), MissionInstance.siteXAt(anchor.x(), 0));
        assertEquals(Integer.valueOf(anchor.x()),
                MissionInstance.siteXAt(anchor.x() + MissionInstance.SITE_RADIUS, 0));
        assertNull(MissionInstance.siteXAt(anchor.x() + MissionInstance.SITE_RADIUS + 1, 0));
        assertNull(MissionInstance.siteXAt(anchor.x(), MissionInstance.SITE_RADIUS + 1));
        assertNull(MissionInstance.siteXAt(MissionInstance.BASE_X - MissionInstance.SPACING, 0),
                "负 slot 不算站点");
    }

    @Test
    @DisplayName("双站关卡：出发站在目的站 192 格外，方向按轮次轮换")
    void startSiteRotates() {
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(0);
        var start = MissionInstance.startSite(anchor);
        assertEquals(anchor.x() - MissionInstance.START_DISTANCE, start.x(), "0 轮出发站靠西");
        assertEquals(anchor.z(), start.z());
        var north = MissionInstance.startSite(MissionInstance.anchorForRound(1));
        assertEquals(-MissionInstance.START_DISTANCE, north.z(), "1 轮出发站靠北");
        assertTrue(MissionInstance.inRouteZone(anchor.x(), anchor.z()), "目的站净空");
        assertTrue(MissionInstance.inRouteZone(start.x(), start.z()), "出发站净空");
        int midX = (anchor.x() + start.x()) / 2;
        assertTrue(MissionInstance.inRouteZone(midX, anchor.z()), "两站之间是开阔水道");
        assertFalse(MissionInstance.inRouteZone(anchor.x(), anchor.z() + 400), "远离航线");
    }

    @Test
    @DisplayName("副本站顶板压平在 -44，周围留出机动净空")
    void instanceSiteReusesOpenSeaTerrain() {
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(3);
        assertEquals(-44, SeaTerrain.ceilingY(0x5EED1234L, anchor.x(), anchor.z()),
                "副本站顶板必须压平在 -44");
        assertTrue(SeaTerrain.inClearZone(anchor.x(), anchor.z()), "副本站脚下不能长岩柱");
        assertTrue(SeaTerrain.inClearZone(anchor.x() + 40, 0));
        int floor = SeaTerrain.floorY(0x5EED1234L, anchor.x(), anchor.z());
        assertTrue(floor <= -500 && floor >= -544, "副本站下方是开放海域的海底，不是岩台");
    }

    @Test
    @DisplayName("活动关卡的出发站距离按类型路线长度，其它槽位回退 192")
    void routeLevelStartDistance() {
        LevelParams basic = LevelType.BASIC.params();
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(4);
        MissionInstance.setRouteLevel(new MissionInstance.RouteLevel(
                4, LevelType.BASIC, 123L, 3, basic.routeLength(), anchor.x()));

        assertEquals(basic.routeLength(), MissionInstance.startDistanceFor(anchor));
        assertEquals(anchor.x(), MissionInstance.startSite(anchor).x(),
                "basic 700 已超过 432，改走南北：x 与目的站相同");
        assertEquals(anchor.z() - basic.routeLength(), MissionInstance.startSite(anchor).z(),
                "slot 4 偶数走北，距离是 basic 的路线长度");
        assertEquals(MissionInstance.START_DISTANCE,
                MissionInstance.startDistanceFor(MissionInstance.anchorForRound(5)),
                "非活动槽位仍用默认 192");
        assertEquals(LevelType.BASIC, MissionInstance.paramsForSlot(4).type());
        assertNull(MissionInstance.paramsForSlot(5));
    }

    @Test
    @DisplayName("旧存档 1024 间距：活动槽位按保存的 anchorX 判定")
    void legacySpacingAnchors() {
        int legacy = 1024;
        LevelParams basic = LevelType.BASIC.params();

        // 槽位 2：锚点 22048 在当前 2048 间距下会被反推成槽位 1。
        int anchor2 = MissionInstance.BASE_X + 2 * legacy;
        MissionInstance.Anchor a2 = new MissionInstance.Anchor(
                2, anchor2, MissionInstance.ANCHOR_Y, 0);
        MissionInstance.setRouteLevel(new MissionInstance.RouteLevel(
                2, LevelType.BASIC, 11L, 4, basic.routeLength(), anchor2));
        MissionInstance.setRouteOutposts(2, true, false);

        assertEquals(anchor2, MissionInstance.anchorForRound(2).x());
        assertEquals(2, MissionInstance.siteSlot(DockingBay.Site.forSlot(2)));
        assertEquals(2, MissionInstance.siteSlot(MissionInstance.startSite(a2)),
                "出发站也要认回同一槽位");
        assertEquals(Integer.valueOf(anchor2), MissionInstance.siteXAt(anchor2, 0));
        assertFalse(MissionInstance.outpostExpected(DockingBay.Site.forSlot(2)),
                "routeEndOutpost=false 的航路点不能生成哨站");

        // 槽位 3：锚点 23072 用当前间距反推会漏掉整个活动槽位。
        int anchor3 = MissionInstance.BASE_X + 3 * legacy;
        MissionInstance.setRouteLevel(new MissionInstance.RouteLevel(
                3, LevelType.BASIC, 12L, 4, basic.routeLength(), anchor3));
        MissionInstance.setRouteOutposts(3, true, true);

        assertEquals(3, MissionInstance.siteSlot(DockingBay.Site.forSlot(3)));
        assertEquals(Integer.valueOf(anchor3), MissionInstance.siteXAt(anchor3, 0));
        assertTrue(MissionInstance.inRouteZone(anchor3 - 1, 0),
                "锚点净空必须算在航道内（当前间距反推的 base 落在槽位 1）");
        assertTrue(MissionInstance.inMiningZone(anchor3 - 100, 0),
                "锚点 24..150 格内是矿区");
    }

    @Test
    @DisplayName("没有活动关卡时出发站距离回退 192")
    void noRouteLevelFallback() {
        MissionInstance.setRouteLevel(null);
        assertEquals(MissionInstance.START_DISTANCE,
                MissionInstance.startDistanceFor(MissionInstance.anchorForRound(0)));
    }

    @Test
    @DisplayName("长路线（advanced 600 / maze 520）走南北，x 留在同一槽位")
    void longRouteUsesNorthSouth() {
        LevelParams advanced = LevelType.ADVANCED.params();
        MissionInstance.Anchor even = MissionInstance.anchorForRound(8);
        MissionInstance.setRouteLevel(new MissionInstance.RouteLevel(
                8, LevelType.ADVANCED, 1L, 9, advanced.routeLength(), even.x()));
        var north = MissionInstance.startSite(even);
        assertEquals(even.x(), north.x(), "长路线 x 不变");
        assertEquals(even.z() - advanced.routeLength(), north.z(), "slot 8 偶数走北");
        assertEquals(8, MissionInstance.siteSlot(north), "出发站仍在同一槽位");

        MissionInstance.Anchor odd = MissionInstance.anchorForRound(9);
        MissionInstance.setRouteLevel(new MissionInstance.RouteLevel(
                9, LevelType.ADVANCED, 2L, 9, advanced.routeLength(), odd.x()));
        var south = MissionInstance.startSite(odd);
        assertEquals(odd.x(), south.x());
        assertEquals(odd.z() + advanced.routeLength(), south.z(), "slot 9 奇数走南");
        assertEquals(9, MissionInstance.siteSlot(south));
    }
}
