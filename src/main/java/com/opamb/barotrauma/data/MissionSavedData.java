package com.opamb.barotrauma.data;

import com.opamb.barotrauma.campaign.CampaignState;
import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.mission.MissionType;
import com.opamb.barotrauma.world.PoiNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 任务的世界存档：任务池状态和每个玩家的进行中任务。
 *
 * <p>任务定义（id/类型/目标/奖励）来自 {@link PoiNetwork#definitions()}，是确定性的；
 * 这里只存运行时状态。credits 已统一到 {@link CampaignState} 的共享钱包，
 * 这里的 {@code credits} 字段只用于旧存档的一次性迁移。</p>
 */
public final class MissionSavedData extends SavedData {

    public static final String NAME = "barotrauma_missions";

    private final Map<Integer, Mission> missions = new LinkedHashMap<>();
    /** 玩家 → 进行中的任务 id（一人同时一条）。 */
    private final Map<UUID, Integer> active = new HashMap<>();
    /** 旧版按玩家保存的余额；迁移进共享钱包后清空。 */
    private final Map<UUID, Integer> legacyCredits = new HashMap<>();

    public MissionSavedData() {
        for (Mission mission : PoiNetwork.definitions()) {
            missions.put(mission.id, mission);
        }
    }

    public static MissionSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                MissionSavedData::load, MissionSavedData::new, NAME);
    }

    public Map<Integer, Mission> missions() {
        return missions;
    }

    @Nullable
    public Mission activeMission(UUID player) {
        Integer id = active.get(player);
        return id == null ? null : missions.get(id);
    }

    /**
     * 把旧版按玩家保存的 credits 一次性合并进共享钱包。
     *
     * <p>迁移标记和余额都在 {@link CampaignState} 里，旧余额保持只读：即使两个
     * SavedData 的落盘时机不同，也不会丢失或重复迁入。返回实际迁入的总额。</p>
     */
    public int migrateLegacyCreditsTo(CampaignState wallet) {
        if (legacyCredits.isEmpty() || wallet.legacyCreditsMigrated()) {
            return 0;
        }
        long total = 0L;
        for (int value : legacyCredits.values()) {
            total += Math.max(0, value);
        }
        int moved = (int) Math.min(Integer.MAX_VALUE, total);
        wallet.applyLegacyMigration(moved);
        return moved;
    }

    /** 接取。false = 已有进行中任务/任务被别人接走。 */
    public boolean accept(UUID player, int missionId, long gameTime) {
        if (active.containsKey(player)) {
            return false;
        }
        Mission mission = missions.get(missionId);
        if (mission == null || !mission.assign(player, gameTime)) {
            return false;
        }
        active.put(player, missionId);
        setDirty();
        return true;
    }

    /** 放弃：任务回到池里（已拿的货物不回收）。FAILED 任务只解除占用，状态留到本轮抵达时重置。 */
    public void abandon(UUID player) {
        Mission mission = activeMission(player);
        if (mission == null) {
            return;
        }
        if (mission.state() == Mission.State.FAILED) {
            // 超时失败的任务不提前回池：保留 FAILED 到本轮抵达时统一重置，
            // 否则其他玩家会立刻接走一条马上又能超时的任务（CodeRabbit 审查意见）。
            active.remove(player);
            setDirty();
            return;
        }
        mission.abandon();
        active.remove(player);
        setDirty();
    }

    /** 达成（进 READY），由 {@code MissionProgress} 在条件满足时调用。 */
    public void markReady(Mission mission) {
        mission.setState(Mission.State.READY);
        setDirty();
    }

    /** 交付成功：任务关闭并从 active 摘除（奖励由调用方打进共享钱包）。 */
    public void finish(UUID player, Mission mission) {
        mission.complete();
        active.remove(player);
        setDirty();
    }

    /** 运送类货物丢失：READY 退回 ACTIVE，保留认领人与时限（可放弃后重新接取补货）。 */
    public void demoteToActive(Mission mission) {
        mission.setState(Mission.State.ACTIVE);
        setDirty();
    }

    /** 超时失败：零报酬，任务保留到本轮抵达目的地时重置。 */
    public void failMission(Mission mission) {
        mission.fail();
        setDirty();
    }

    /** 本轮抵达目的地：所有 FAILED 回到 OFFERED 可再次接取。返回重置条数。 */
    public int resetFailed() {
        int reset = 0;
        for (Mission mission : missions.values()) {
            if (mission.resetIfFailed()) {
                reset++;
            }
        }
        if (reset > 0) {
            // 清掉仍指向已重置任务的 active 条目：否则原玩家的 activeMission
            // 会返回一条 OFFERED 任务，既接不了新任务也可能误伤他人（CodeRabbit 审查意见）。
            active.values().removeIf(id -> {
                Mission m = missions.get(id);
                return m == null
                        || (m.state() == Mission.State.OFFERED && m.assignedTo() == null);
            });
            setDirty();
        }
        return reset;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    /** 序列化任务池、进行中任务和旧版余额（迁移源）。 */
    public CompoundTag save(CompoundTag tag) {
        ListTag missionList = new ListTag();
        for (Mission mission : missions.values()) {
            missionList.add(mission.save());
        }
        tag.put("missions", missionList);
        CompoundTag activeTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : active.entrySet()) {
            activeTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("active", activeTag);
        CompoundTag creditsTag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : legacyCredits.entrySet()) {
            creditsTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put("credits", creditsTag);
        return tag;
    }

    /** 包级可见：只给同包测试构造旧存档用。 */
    static MissionSavedData load(CompoundTag tag) {
        MissionSavedData data = new MissionSavedData();
        ListTag missionList = tag.getList("missions", Tag.TAG_COMPOUND);
        for (int i = 0; i < missionList.size(); i++) {
            CompoundTag entry = missionList.getCompound(i);
            Mission mission = data.missions.get(entry.getInt("id"));
            if (mission != null) {
                mission.load(entry);
            }
        }
        CompoundTag activeTag = tag.getCompound("active");
        for (String key : activeTag.getAllKeys()) {
            try {
                data.active.put(UUID.fromString(key), activeTag.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // 存档里混进了非法 key，跳过
            }
        }
        CompoundTag creditsTag = tag.getCompound("credits");
        for (String key : creditsTag.getAllKeys()) {
            try {
                data.legacyCredits.put(UUID.fromString(key), creditsTag.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // 同上
            }
        }
        return data;
    }
}
