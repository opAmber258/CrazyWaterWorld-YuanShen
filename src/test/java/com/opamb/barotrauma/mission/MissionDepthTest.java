package com.opamb.barotrauma.mission;

import com.opamb.barotrauma.data.MissionSavedData;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 任务深度：时限、超时失败、货运箱数结算与 98% 阈值、本轮重置。 */
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
        Mission mission = data.missions().values().stream()
                .filter(m -> m.type == MissionType.URGENT_DELIVERY)
                .findFirst().orElseThrow();
        UUID player = UUID.randomUUID();
        assertTrue(data.accept(player, mission.id, 0L));
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
    void 货运按箱数乘单价结算且没有部分完成() {
        Mission delivery = new Mission(5, MissionType.DELIVERY,
                new BlockPos(320, -301, 0), 12, 560);
        // 货运不再有"送到别的站只给一半"的规则：只有 98% 达成或失败。
        assertEquals(1.0D, delivery.rewardFraction(true), 1.0E-9);
        assertEquals(1.0D, delivery.rewardFraction(false), 1.0E-9);
        assertEquals(1.0D, urgent().rewardFraction(false), 1.0E-9);
        Mission recon = new Mission(4, MissionType.RECON,
                new BlockPos(-60, -452, 150), 14, 120);
        assertEquals(1.0D, recon.rewardFraction(false), 1.0E-9);
    }

    @Test
    void 货运报酬等于箱数乘每箱单价() {
        Mission delivery = new Mission(5, MissionType.DELIVERY,
                new BlockPos(320, -301, 0), 12, 560);
        assertEquals(0, delivery.cargoPayout(), "还没装货时没有报酬");
        delivery.setCargo(4, 140);
        assertEquals(4, delivery.cargoTotal());
        assertEquals(140, delivery.cargoPerCrate());
        assertEquals(560, delivery.cargoPayout());
    }

    @Test
    void 九十八百分比阈值等于箱箱必到() {
        assertTrue(CargoManifest.deliveredEnough(4, 4));
        assertFalse(CargoManifest.deliveredEnough(4, 3), "4 箱丢 1 箱 = 75%");
        assertTrue(CargoManifest.deliveredEnough(20, 20));
        assertFalse(CargoManifest.deliveredEnough(20, 19), "20 箱丢 1 箱 = 95%");
        // 一次运到 50 箱以上时，丢 1 箱才刚好是 98%。
        assertTrue(CargoManifest.deliveredEnough(50, 49));
        assertFalse(CargoManifest.deliveredEnough(50, 48));
        assertFalse(CargoManifest.deliveredEnough(0, 0), "没装货不算完成");
    }
}
