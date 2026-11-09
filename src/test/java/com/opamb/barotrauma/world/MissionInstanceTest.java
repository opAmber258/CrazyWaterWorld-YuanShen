package com.opamb.barotrauma.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务海域实例的锚点布局（地形已改用主世界开阔海域）。 */
class MissionInstanceTest {

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
}
