package com.opamb.barotrauma.mission;

import com.opamb.barotrauma.data.MissionSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务深度：时限、超时失败、部分奖励比例与本轮重置。 */
class MissionDepthTest {

    private static Mission urgent() {
        return new Mission(6, MissionType.URGENT_DELIVERY,
                new BlockPos(320, -301, 0), 12, 260, 24000L);
    }

    @Test
    void 接取后按时限计算超时点() {
        Mission mission = urgent();
        UUID player = UUID.randomUUID();
        assertTrue(mission.assign(player, 1000L));
        assertEquals(25000L, mission.deadlineAtGameTime());
        assertFalse(mission.isOverdue(24999L));
        assertTrue(mission.isOverdue(25000L));
    }

    @Test
    void 不限时任务永不超时() {
        Mission mission = new Mission(1, MissionType.SALVAGE,
                new BlockPos(80, -122, 40), 20, 80);
        assertTrue(mission.assign(UUID.randomUUID(), 5L));
        assertEquals(0L, mission.deadlineAtGameTime());
        assertFalse(mission.isOverdue(Long.MAX_VALUE / 2L));
    }

    @Test
    void 超时失败后本轮抵达重置回任务池() {
        MissionSavedData data = new MissionSavedData();
        Mission mission = data.missions().get(6);
        UUID player = UUID.randomUUID();
        assertTrue(data.accept(player, 6, 0L));
        assertEquals(Mission.State.ACTIVE, mission.state());
        assertEquals(24000L, mission.deadlineAtGameTime());
        data.failMission(mission);
        assertEquals(Mission.State.FAILED, mission.state());
        assertEquals(player, mission.assignedTo());
        assertEquals(1, data.resetFailed());
        assertEquals(Mission.State.OFFERED, mission.state());
        assertNull(mission.assignedTo());
        assertEquals(0L, mission.deadlineAtGameTime());
    }

    @Test
    void 运送类按交付终端结算全额或半额() {
        Mission delivery = new Mission(5, MissionType.DELIVERY,
                new BlockPos(320, -301, 0), 12, 150);
        assertEquals(1.0D, delivery.rewardFraction(true), 1.0E-9);
        assertEquals(0.5D, delivery.rewardFraction(false), 1.0E-9);
        assertEquals(1.0D, urgent().rewardFraction(true), 1.0E-9);
        assertEquals(0.5D, urgent().rewardFraction(false), 1.0E-9);
        Mission recon = new Mission(4, MissionType.RECON,
                new BlockPos(-60, -452, 150), 14, 120);
        assertEquals(1.0D, recon.rewardFraction(false), 1.0E-9);
        assertEquals((int) Math.round(260 * 0.5D), 130);
    }
}
