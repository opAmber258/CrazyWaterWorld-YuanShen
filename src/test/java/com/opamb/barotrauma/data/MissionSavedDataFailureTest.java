package com.opamb.barotrauma.data;

import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.mission.MissionType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FAILED 状态、超时点、多任务接取都要完整写进 NBT，跨存档恢复后行为一致。 */
class MissionSavedDataFailureTest {

    /** 当前任务池里那条限时紧急运送。 */
    private static Mission urgent(MissionSavedData data) {
        return data.missions().values().stream()
                .filter(m -> m.type == MissionType.URGENT_DELIVERY)
                .findFirst().orElseThrow();
    }

    @Test
    void FAILED状态与超时点写进NBT并能恢复() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        Mission mission = urgent(data);
        assertTrue(data.accept(player, mission.id, 1000L));
        assertEquals(25000L, mission.deadlineAtGameTime());
        data.failMission(mission);

        CompoundTag tag = data.save(new CompoundTag());
        MissionSavedData restored = MissionSavedData.load(tag);
        Mission restoredMission = restored.missions().get(mission.id);
        assertEquals(Mission.State.FAILED, restoredMission.state());
        assertEquals(25000L, restoredMission.deadlineAtGameTime());
        assertEquals(player, restoredMission.assignedTo());
        assertEquals(1, restored.resetFailed());
        assertEquals(Mission.State.OFFERED, restored.missions().get(mission.id).state());
    }

    @Test
    void 放弃FAILED只解除占用不改状态() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        Mission mission = urgent(data);
        data.accept(player, mission.id, 0L);
        data.failMission(data.missions().get(mission.id));

        data.abandon(player, mission.id);
        assertNull(data.activeMission(player), "放弃后不应再占用该任务");
        assertEquals(Mission.State.FAILED, data.missions().get(mission.id).state(), "FAILED 不提前回池");
        // 固定 UUID：测试输入可复现（随机 UUID 无法按固定随机源重放）。
        UUID otherPlayer = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID anotherPlayer = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
        assertFalse(data.accept(otherPlayer, mission.id, 0L), "FAILED 不可被接取");
        assertEquals(1, data.resetFailed());
        assertTrue(data.accept(anotherPlayer, mission.id, 0L), "本轮抵达后回池可接取");
    }

    @Test
    void 一趟最多接三条且不能重复() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        var ids = data.missions().keySet().stream().limit(4).toList();
        assertTrue(data.accept(player, ids.get(0), 0L));
        assertFalse(data.accept(player, ids.get(0), 0L), "同一条不能重复接");
        assertTrue(data.accept(player, ids.get(1), 0L));
        assertTrue(data.accept(player, ids.get(2), 0L));
        assertEquals(MissionSavedData.MAX_ACTIVE, data.activeMissions(player).size());
        assertFalse(data.accept(player, ids.get(3), 0L), "第 4 条超上限");
        assertEquals(3, data.activeMissions(player).size());
    }

    @Test
    void 完成一条只摘掉这一条多任务NBT可恢复() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        var ids = data.missions().keySet().stream().limit(2).toList();
        assertTrue(data.accept(player, ids.get(0), 0L));
        assertTrue(data.accept(player, ids.get(1), 0L));
        data.finish(player, data.missions().get(ids.get(0)));
        assertEquals(1, data.activeMissions(player).size());
        assertEquals(ids.get(1), data.activeMissions(player).get(0).id);

        MissionSavedData restored = MissionSavedData.load(data.save(new CompoundTag()));
        assertEquals(1, restored.activeMissions(player).size());
        assertEquals(ids.get(1), restored.activeMissions(player).get(0).id);
    }

    @Test
    void 本轮重置会清掉指向已重置任务的占用() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        Mission mission = urgent(data);
        data.accept(player, mission.id, 0L);
        data.failMission(data.missions().get(mission.id));

        assertEquals(1, data.resetFailed());
        assertNull(data.activeMission(player), "原玩家不应再被 OFFERED 任务占用");
        assertTrue(data.accept(player, 1, 0L), "原玩家可以接其他任务");
    }
}
