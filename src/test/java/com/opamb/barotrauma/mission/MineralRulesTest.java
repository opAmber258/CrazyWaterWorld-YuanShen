package com.opamb.barotrauma.mission;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 采矿任务的纯逻辑：采多少、上交多少（原作 {@code resourceHandoverAmount}）。
 */
class MineralRulesTest {

    private static Mission mineral(int count, double handover) {
        Mission mission = new Mission(1, MissionType.MINERAL, BlockPos.ZERO, 0, 1200);
        mission.setMineral("barotrauma:quartz", count, handover);
        return mission;
    }

    @Test
    void 石英八个上交两个() {
        assertEquals(2, mineral(8, 0.25D).mineralHandoverCount());
    }

    @Test
    void 铀矿四个上交一个() {
        Mission mission = new Mission(2, MissionType.MINERAL, BlockPos.ZERO, 0, 1400);
        mission.setMineral("barotrauma:uraniumore", 4, 0.25D);
        assertEquals(1, mission.mineralHandoverCount());
    }

    @Test
    void 没有上交比例时一个都不交() {
        assertEquals(0, mineral(8, 0.0D).mineralHandoverCount());
    }

    @Test
    void 采矿任务带着矿物定义() {
        Mission mission = mineral(8, 0.25D);
        assertEquals("barotrauma:quartz", mission.mineralItem());
        assertEquals(8, mission.mineralCount());
        assertEquals(0.25D, mission.mineralHandover());
    }
}
