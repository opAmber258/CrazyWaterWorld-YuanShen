package com.opamb.barotrauma.mission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 寒冷深穴任务目录：全量登记 + 只发布依赖已实装的条目。 */
class MissionCatalogTest {

    @Test
    void 目录登记了寒冷深穴能出的任务() {
        assertTrue(MissionCatalog.specs().size() >= 80,
                "寒冷深穴任务目录应包含货运/护航/猎杀/巢穴/遗物/矿采/信标/废弃哨站等");
    }

    @Test
    void 终端只发布已实装且地形满足的任务() {
        var offers = MissionCatalog.offers();
        assertFalse(offers.isEmpty());
        for (MissionSpec spec : offers) {
            assertTrue(spec.implemented(), "发布的任务必须依赖已实装: " + spec.id());
            String spawn = spec.spawnType() == null ? "none" : spec.spawnType().toLowerCase();
            assertTrue(spawn.equals("none") || spawn.equals("cave"),
                    "寒冷深穴实例没有沉船/遗迹生成，不能发布: " + spec.id());
        }
    }

    @Test
    void 发布顺序可按id反查() {
        var offers = MissionCatalog.offers();
        for (int i = 1; i <= offers.size(); i++) {
            MissionSpec spec = MissionCatalog.offer(i);
            assertNotNull(spec);
            assertEquals(offers.get(i - 1).id(), spec.id());
        }
    }
}
