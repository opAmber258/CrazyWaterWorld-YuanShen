package com.opamb.barotrauma.data;

import com.opamb.barotrauma.mission.Mission;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
