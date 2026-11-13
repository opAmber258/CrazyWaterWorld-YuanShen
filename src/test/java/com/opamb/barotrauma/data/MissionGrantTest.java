package com.opamb.barotrauma.data;

import com.opamb.barotrauma.mission.Mission;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站务员发任务的规则（照原作）：
 * <ul>
 *   <li>每个站每次访问最多发 3 条报价，同一轮重复对话不补发；</li>
 *   <li>报价绑定目的地，海图按目的地过滤；未接的留在站里；</li>
 *   <li>重新靠泊会刷新一批（同一个任务模板可以在另一个站重新挂出来）；</li>
 *   <li>完成的委托会复位成新委托再次发放。</li>
 * </ul>
 */
class MissionGrantTest {

    @Test
    void 每次访问最多三条并绑定目的地() {
        MissionSavedData data = new MissionSavedData();
        assertTrue(data.beginVisit(0), "第一次对话可以发任务");
        List<Integer> granted = data.grantBatch(0, List.of(1), MissionSavedData.GRANTS_PER_VISIT);
        assertEquals(3, granted.size());
        for (int id : granted) {
            assertEquals(1, data.offerDestination(0, id));
        }
        assertEquals(3, data.grantedMissions(0).size());
        assertFalse(data.beginVisit(0), "同一轮再对话不补发");
    }

    @Test
    void 重新进站会刷新一批报价() {
        MissionSavedData data = new MissionSavedData();
        data.beginVisit(0);
        data.grantBatch(0, List.of(1), MissionSavedData.GRANTS_PER_VISIT);
        data.endVisit(0);

        // 另一个站第一次访问：任务模板可以重新挂报价（原作每个站生成自己的委托）。
        List<Integer> atRelay = data.grantBatch(1, List.of(0, 2, 3, 5),
                MissionSavedData.GRANTS_PER_VISIT);
        assertEquals(3, atRelay.size(), "新站第一趟也能拿到 3 条");
        assertEquals(3, data.grantedMissions(1).size());
        assertEquals(0, data.offerDestination(1, atRelay.get(0)));
        assertEquals(2, data.offerDestination(1, atRelay.get(1)));
    }

    @Test
    void 同一个站不会重复挂同一条() {
        MissionSavedData data = new MissionSavedData();
        List<Integer> first = data.grantBatch(0, List.of(1), 3);
        List<Integer> again = data.grantBatch(0, List.of(1), 3);
        for (int id : again) {
            assertFalse(first.contains(id), "同一站不会重复挂同一条");
        }
        assertEquals(3, first.size());
        assertEquals(data.missions().size() - 3, again.size(), "只会补没挂过的");
    }

    @Test
    void 完成的委托会复位成新委托再次发放() {
        MissionSavedData data = new MissionSavedData();
        UUID player = UUID.randomUUID();
        List<Integer> granted = data.grantBatch(0, List.of(1), MissionSavedData.GRANTS_PER_VISIT);
        int completed = granted.get(0);
        assertTrue(data.accept(player, completed, 0, 1, 0L));
        data.finish(player, data.missions().get(completed));

        assertEquals(-1, data.offerDestination(0, completed), "完成的报价从站里摘掉");
        List<Integer> regranted = data.grantBatch(1, List.of(4), MissionSavedData.GRANTS_PER_VISIT);
        assertTrue(regranted.contains(completed));
        Mission mission = data.missions().get(completed);
        assertEquals(Mission.State.OFFERED, mission.state());
        assertEquals(4, data.offerDestination(1, completed));
    }

    @Test
    void 发放记录与访问标记写进NBT() {
        MissionSavedData data = new MissionSavedData();
        data.beginVisit(2);
        List<Integer> granted = data.grantBatch(2, List.of(1, 4), MissionSavedData.GRANTS_PER_VISIT);
        assertEquals(3, granted.size());

        MissionSavedData restored = MissionSavedData.load(data.save(new CompoundTag()));
        assertEquals(3, restored.grantedMissions(2).size());
        assertFalse(restored.beginVisit(2), "访问标记也要恢复，避免读档后刷任务");
        assertEquals(1, restored.offerDestination(2, granted.get(0)));
        assertEquals(4, restored.offerDestination(2, granted.get(1)));
    }

    /** 造一份带旧 granted 的存档：任务 1 绑定在 0 号站、目的地 1。 */
    private static CompoundTag legacyTag() {
        MissionSavedData source = new MissionSavedData();
        Mission mission = source.missions().get(1);
        mission.bind(0, 1);
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag missions = new net.minecraft.nbt.ListTag();
        missions.add(mission.save());
        tag.put("missions", missions);
        CompoundTag granted = new CompoundTag();
        granted.put("0", new net.minecraft.nbt.IntArrayTag(new int[]{1}));
        tag.put("granted", granted);
        return tag;
    }

    @Test
    void offers为空时不回落到旧granted() {
        CompoundTag tag = legacyTag();
        tag.put("offers", new CompoundTag());
        MissionSavedData loaded = MissionSavedData.load(tag);
        assertEquals(-1, loaded.offerDestination(0, 1),
                "新格式即使 offers 为空，也不能把旧 granted 复活");
    }

    @Test
    void 只有granted的旧存档仍能迁移() {
        MissionSavedData loaded = MissionSavedData.load(legacyTag());
        assertEquals(1, loaded.offerDestination(0, 1), "旧格式仍能迁移出报价");
    }
}
