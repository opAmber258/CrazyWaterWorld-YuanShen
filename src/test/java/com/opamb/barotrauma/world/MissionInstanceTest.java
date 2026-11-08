package com.opamb.barotrauma.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
