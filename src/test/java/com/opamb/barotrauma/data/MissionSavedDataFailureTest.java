package com.opamb.barotrauma.data;

import com.opamb.barotrauma.mission.Mission;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FAILED 状态与超时点要完整写进 NBT，跨存档恢复后行为一致。 */
class MissionSavedDataFailureTest {

    @Test
    void FAILED状态与超时点写进NBT并能恢复() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        assertTrue(data.accept(player, 6, 1000L));
        Mission mission = data.missions().get(6);
        assertEquals(25000L, mission.deadlineAtGameTime());
        data.failMission(mission);

        CompoundTag tag = data.save(new CompoundTag());
        MissionSavedData restored = MissionSavedData.load(tag);
        Mission restoredMission = restored.missions().get(6);
        assertEquals(Mission.State.FAILED, restoredMission.state());
        assertEquals(25000L, restoredMission.deadlineAtGameTime());
        assertEquals(player, restoredMission.assignedTo());
        assertEquals(1, restored.resetFailed());
        assertEquals(Mission.State.OFFERED, restored.missions().get(6).state());
    }

    @Test
    void 放弃FAILED只解除占用不改状态() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        data.accept(player, 6, 0L);
        data.failMission(data.missions().get(6));

        data.abandon(player);
        assertNull(data.activeMission(player), "放弃后不应再占用该任务");
        assertEquals(Mission.State.FAILED, data.missions().get(6).state(), "FAILED 不提前回池");
        assertFalse(data.accept(UUID.randomUUID(), 6, 0L), "FAILED 不可被接取");
        assertEquals(1, data.resetFailed());
        assertTrue(data.accept(UUID.randomUUID(), 6, 0L), "本轮抵达后回池可接取");
    }

    @Test
    void 运送货物丢失时READY退回ACTIVE并保留时限() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        data.accept(player, 6, 0L);
        Mission mission = data.missions().get(6);
        data.markReady(mission);
        data.demoteToActive(mission);
        assertEquals(Mission.State.ACTIVE, mission.state());
        assertEquals(player, mission.assignedTo(), "认领人保留");
        assertEquals(24000L, mission.deadlineAtGameTime(), "时限保留");
    }

    @Test
    void 本轮重置会清掉指向已重置任务的占用() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        data.accept(player, 6, 0L);
        data.failMission(data.missions().get(6));

        assertEquals(1, data.resetFailed());
        assertNull(data.activeMission(player), "原玩家不应再被 OFFERED 任务占用");
        assertTrue(data.accept(player, 1, 0L), "原玩家可以接其他任务");
    }
}
