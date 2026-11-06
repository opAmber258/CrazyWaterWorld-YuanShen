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
     * <p>只在第一次调用时生效：迁移后清空旧余额，避免每次打开终端都重复加钱。
     * 返回实际迁入的总额。</p>
     */
    public int migrateLegacyCreditsTo(CampaignState wallet) {
        if (legacyCredits.isEmpty()) {
            return 0;
        }
        long total = 0L;
        for (int value : legacyCredits.values()) {
            total += Math.max(0, value);
        }
        int moved = (int) Math.min(Integer.MAX_VALUE, total);
        legacyCredits.clear();
        if (moved > 0) {
            wallet.addCredits(moved);
        }
        setDirty();
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

    /** 放弃：任务回到池里（已拿的货物不回收）。 */
    public void abandon(UUID player) {
        Mission mission = activeMission(player);
        if (mission != null) {
            mission.abandon();
            active.remove(player);
            setDirty();
        }
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

    // ------------------------------------------------------------------ NBT

    @Override
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
