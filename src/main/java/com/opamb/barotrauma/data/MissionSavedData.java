package com.opamb.barotrauma.data;

import com.opamb.barotrauma.campaign.CampaignState;
import com.opamb.barotrauma.mission.Mission;
import com.opamb.barotrauma.mission.MissionType;
import com.opamb.barotrauma.world.PoiNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务的世界存档：任务池状态和每个玩家的进行中任务。
 *
 * <p>任务定义（id/类型/目标/奖励）来自 {@link PoiNetwork#definitions()}，是确定性的；
 * 这里只存运行时状态。一个玩家一趟最多同时持有 {@link #MAX_ACTIVE} 条任务
 * （原作海图上一趟最多勾 3 条）。credits 已统一到 {@link CampaignState} 的共享钱包，
 * 这里的 {@code credits} 字段只用于旧存档的一次性迁移。</p>
 */
public final class MissionSavedData extends SavedData {

    public static final String NAME = "barotrauma_missions";

    /** 一趟最多接几条任务（原作海图勾选上限）。 */
    public static final int MAX_ACTIVE = 3;
    /** 站务员每次访问最多发几条任务（原作站长一次给 3 条）。 */
    public static final int GRANTS_PER_VISIT = 3;

    private final Map<Integer, Mission> missions = new LinkedHashMap<>();
    /** 玩家 → 进行中的任务 id（按接取顺序，0..{@link #MAX_ACTIVE} 条）。 */
    private final Map<UUID, List<Integer>> active = new HashMap<>();
    /**
     * 站点节点 → （任务 id → 目的地节点）。站务员每次靠泊都会重新发一批报价，
     * 未接的报价留在站里；同一个任务模板可以在多个站同时挂着报价（原作每个站
     * 生成自己的委托实例，这里用模板 + 站点报价来近似）。
     */
    private final Map<Integer, Map<Integer, Integer>> offers = new LinkedHashMap<>();
    /** 本轮访问已经发过任务的站点（离站时清掉，重新靠泊会补发新委托）。 */
    private final java.util.Set<Integer> visited = new java.util.HashSet<>();
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

    /** 玩家当前所有进行中任务（0..3 条，按接取顺序）。 */
    public List<Mission> activeMissions(UUID player) {
        List<Integer> ids = active.get(player);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Mission> out = new ArrayList<>(ids.size());
        for (int id : ids) {
            Mission mission = missions.get(id);
            if (mission != null) {
                out.add(mission);
            }
        }
        return out;
    }

    /** 所有还有进行中任务的玩家（含离线）；抵达结算要按持有者遍历。 */
    public java.util.Set<UUID> playersWithActiveMissions() {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        for (Map.Entry<UUID, List<Integer>> entry : active.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    /** 玩家进行中的第一条任务；没有返回 null。 */
    @Nullable
    public Mission activeMission(UUID player) {
        List<Integer> ids = active.get(player);
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return missions.get(ids.get(0));
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

    /**
     * 玩家在 {@code node} 和站务员对话：本轮第一次访问返回 true（可以发任务），
     * 同一站同一轮再对话返回 false（避免反复刷任务）。离站时用 {@link #endVisit} 清掉。
     */
    public boolean beginVisit(int node) {
        if (visited.add(node)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** 离开站点（下潜成功）后清访问标记：下次靠泊可以再补发任务。 */
    public void endVisit(int node) {
        if (visited.remove(node)) {
            setDirty();
        }
    }

    /** 这个站在 {@code missionId} 上挂着的报价目的地；没有返回 -1。 */
    public int offerDestination(int node, int missionId) {
        Map<Integer, Integer> list = offers.get(node);
        Integer destination = list == null ? null : list.get(missionId);
        return destination == null ? -1 : destination;
    }

    /** 这个站挂着的、还能接的报价任务 id（按发放顺序）。 */
    public List<Integer> offerIds(int node) {
        Map<Integer, Integer> list = offers.get(node);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (int id : list.keySet()) {
            Mission mission = missions.get(id);
            if (mission != null && mission.state() == Mission.State.OFFERED
                    && !mission.isTaken()) {
                out.add(id);
            }
        }
        return out;
    }

    /** 一个站当前发着的任务（未接的会一直留着；失败/完成的先不列）。 */
    public List<Mission> grantedMissions(int node) {
        List<Mission> out = new ArrayList<>();
        for (int id : offerIds(node)) {
            Mission mission = missions.get(id);
            if (mission != null) {
                out.add(mission);
            }
        }
        return out;
    }

    /**
     * 站务员发放一批报价（最多 {@code limit} 条），每条分配一个目的地。
     *
     * <p>同一个任务模板可以在多个站同时挂报价（原作每个站生成自己的委托实例）；
     * 但任务被接走后是唯一的，其他站的报价会从海图上消失。已完成（DONE）的模板
     * 会先复位成新委托再发。目的地从 {@code destinations} 里轮着分配。</p>
     *
     * @return 这次实际新挂出去的任务 id
     */
    public List<Integer> grantBatch(int node, List<Integer> destinations, int limit) {
        if (destinations == null || destinations.isEmpty() || limit <= 0) {
            return List.of();
        }
        Map<Integer, Integer> list = offers.computeIfAbsent(node, key -> new LinkedHashMap<>());
        List<Mission> candidates = new ArrayList<>();
        for (Mission mission : missions.values()) {
            if (mission.state() == Mission.State.FAILED || mission.isTaken()
                    || mission.state() == Mission.State.ACTIVE
                    || mission.state() == Mission.State.READY) {
                continue;
            }
            if (!list.containsKey(mission.id)) {
                candidates.add(mission);
            }
        }
        candidates.sort(java.util.Comparator.comparingInt(mission -> mission.id));
        List<Integer> out = new ArrayList<>();
        for (Mission mission : candidates) {
            if (out.size() >= limit) {
                break;
            }
            if (mission.state() == Mission.State.DONE) {
                mission.resetForRegrant();
            }
            int destination = destinations.get(out.size() % destinations.size());
            list.put(mission.id, destination);
            out.add(mission.id);
        }
        if (!out.isEmpty()) {
            setDirty();
        }
        return out;
    }

    /** 接取一条（不带站点绑定的旧入口，单元测试/迁移用）。 */
    public boolean accept(UUID player, int missionId, long gameTime) {
        return accept(player, missionId, -1, -1, gameTime);
    }

    /** 接取一条，并把这条报价的起点/目的地绑到任务上（结算/HUD 用）。 */
    public boolean accept(UUID player, int missionId, int origin, int destination,
                          long gameTime) {
        List<Integer> ids = active.computeIfAbsent(player, key -> new ArrayList<>());
        if (ids.size() >= MAX_ACTIVE || ids.contains(missionId)) {
            return false;
        }
        Mission mission = missions.get(missionId);
        if (mission == null || mission.state() != Mission.State.OFFERED) {
            return false;
        }
        if (origin >= 0) {
            mission.bind(origin, destination);
        }
        if (!mission.assign(player, gameTime)) {
            return false;
        }
        ids.add(missionId);
        setDirty();
        return true;
    }

    /**
     * 放弃一条：任务回到池里；货运货物由调用方清掉（原版任务结束会移除所有货物）。
     * FAILED 任务只解除占用，状态留到本轮抵达时重置。
     */
    public void abandon(UUID player, int missionId) {
        List<Integer> ids = active.get(player);
        // 先确认这条确实属于该玩家，避免误重置别人持有的任务。
        if (ids == null || !ids.contains(missionId)) {
            return;
        }
        Mission mission = missions.get(missionId);
        if (mission != null && mission.state() != Mission.State.FAILED) {
            mission.abandon();
        }
        ids.remove(Integer.valueOf(missionId));
        if (ids.isEmpty()) {
            active.remove(player);
        }
        setDirty();
    }

    /** 达成（进 READY），由 {@code MissionProgress} 在条件满足时调用。 */
    public void markReady(Mission mission) {
        mission.setState(Mission.State.READY);
        setDirty();
    }

    /** 交付成功：任务关闭并从进行中列表摘除（奖励由调用方打进共享钱包）。 */
    public void finish(UUID player, Mission mission) {
        mission.complete();
        // 完成的报价从所有站的列表里摘掉：之后站务员可以当成新委托再发。
        for (Map<Integer, Integer> list : offers.values()) {
            list.remove(mission.id);
        }
        List<Integer> ids = active.get(player);
        if (ids != null) {
            ids.remove(Integer.valueOf(mission.id));
            if (ids.isEmpty()) {
                active.remove(player);
            }
        }
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
            // 清掉仍指向已重置任务的 active 条目：否则玩家的进行中列表
            // 会返回一条 OFFERED 任务，既接不了新任务也可能误伤他人（CodeRabbit 审查意见）。
            active.values().removeIf(ids -> {
                ids.removeIf(id -> {
                    Mission m = missions.get(id);
                    return m == null || (m.state() == Mission.State.OFFERED && m.assignedTo() == null);
                });
                return ids.isEmpty();
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
        for (Map.Entry<UUID, List<Integer>> entry : active.entrySet()) {
            int[] ids = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            activeTag.put(entry.getKey().toString(), new IntArrayTag(ids));
        }
        tag.put("active", activeTag);
        CompoundTag offersTag = new CompoundTag();
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : offers.entrySet()) {
            CompoundTag nodeTag = new CompoundTag();
            for (Map.Entry<Integer, Integer> offer : entry.getValue().entrySet()) {
                nodeTag.putInt(String.valueOf(offer.getKey()), offer.getValue());
            }
            offersTag.put(String.valueOf(entry.getKey()), nodeTag);
        }
        tag.put("offers", offersTag);
        int[] visitDone = visited.stream().sorted().mapToInt(Integer::intValue).toArray();
        tag.putIntArray("visitdone", visitDone);
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
                UUID player = UUID.fromString(key);
                List<Integer> ids = new ArrayList<>();
                Tag value = activeTag.get(key);
                if (value instanceof IntArrayTag array) {
                    for (int id : array.getAsIntArray()) {
                        ids.add(id);
                    }
                } else {
                    // 旧存档：一个玩家只存了一条 int。
                    ids.add(activeTag.getInt(key));
                }
                if (!ids.isEmpty()) {
                    data.active.put(player, ids);
                }
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
        CompoundTag offersTag = tag.getCompound("offers");
        // 新存档即使 offers 为空也以它为准；只有完全没有这个键时才读旧的 granted。
        if (tag.contains("offers")) {
            for (String key : offersTag.getAllKeys()) {
                try {
                    int node = Integer.parseInt(key);
                    CompoundTag nodeTag = offersTag.getCompound(key);
                    Map<Integer, Integer> list = new LinkedHashMap<>();
                    for (String missionKey : nodeTag.getAllKeys()) {
                        try {
                            list.put(Integer.parseInt(missionKey), nodeTag.getInt(missionKey));
                        } catch (NumberFormatException ignored) {
                            // 非法的任务 key，跳过
                        }
                    }
                    if (!list.isEmpty()) {
                        data.offers.put(node, list);
                    }
                } catch (NumberFormatException ignored) {
                    // 非法的节点 key，跳过
                }
            }
        } else {
            // 旧开发版存档：granted = 节点 → 任务 id 数组，目的地记在任务本身上。
            CompoundTag grantedTag = tag.getCompound("granted");
            for (String key : grantedTag.getAllKeys()) {
                try {
                    int node = Integer.parseInt(key);
                    Map<Integer, Integer> list = new LinkedHashMap<>();
                    for (int id : grantedTag.getIntArray(key)) {
                        Mission mission = data.missions.get(id);
                        int destination = mission == null ? -1 : mission.destinationNode();
                        if (destination >= 0) {
                            list.put(id, destination);
                        }
                    }
                    if (!list.isEmpty()) {
                        data.offers.put(node, list);
                    }
                } catch (NumberFormatException ignored) {
                    // 同上
                }
            }
        }
        for (int node : tag.getIntArray("visitdone")) {
            data.visited.add(node);
        }
        return data;
    }
}
