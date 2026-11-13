package com.opamb.barotrauma.campaign;

import com.opamb.barotrauma.data.MissionSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 战役的世界存档：当前节点、共享资金、已访问节点、当前巡回、回档快照。
 *
 * <p>共享钱包是 v1 唯一的经济来源；旧版 {@link MissionSavedData} 里按玩家存的
 * credits 会在第一次访问钱包时一次性迁移进来（见 {@link #wallet}）。</p>
 */
public final class CampaignState extends SavedData {

    public static final String NAME = "barotrauma_campaign";
    /** 原版战役初始资金 2,500 mk；先作为设计默认值。 */
    private static final int INITIAL_CREDITS = 2500;

    private int currentNode = CampaignMap.START_NODE;
    private int credits = INITIAL_CREDITS;
    private int roundsCompleted;
    private java.util.List<String> selectedMissions = java.util.List.of();
    private final Set<Integer> visited = new LinkedHashSet<>();
    private final RoundState round = new RoundState();
    @Nullable
    private CompoundTag snapshot;
    /** 旧版按玩家余额是否已迁入；标记放在战役存档顶层，不参与回档快照。 */
    private boolean legacyCreditsMigrated;
    /**
     * 下一个可用的副本 slot。只增不减，**不参与回档快照**：失败/回滚后重试会换新 slot，
     * 避免复用一个已经按“无人航路点”生成过（没有站）的区块。
     */
    private int nextInstanceSlot;

    public CampaignState() {
        visited.add(currentNode);
    }

    public static CampaignState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CampaignState::load, CampaignState::new, NAME);
    }

    /**
     * 共享钱包入口：读取战役状态，并顺带把旧版按玩家余额迁入（一次性）。
     *
     * <p>只应在 {@code barotrauma:the_deep} 维度调用——钱包固定挂在这个维度。</p>
     */
    public static CampaignState wallet(ServerLevel level) {
        CampaignState state = get(level);
        MissionSavedData.get(level).migrateLegacyCreditsTo(state);
        return state;
    }

    public java.util.List<String> selectedMissions() { return selectedMissions; }

    public void selectMissions(java.util.List<String> ids) {
        selectedMissions = java.util.List.copyOf(ids);
        setDirty();
    }

    public int currentNode() {
        return currentNode;
    }

    public int credits() {
        return credits;
    }

    public int roundsCompleted() {
        return roundsCompleted;
    }

    public Set<Integer> visited() {
        return Set.copyOf(visited);
    }

    public RoundState round() {
        return round;
    }

    /**
     * 取一个未使用过的副本 slot（至少是已完成轮数）。失败重试不会复用旧 slot，
     * 已经按另一种站点类型生成过的区块不会被错误沿用。
     */
    public int takeInstanceSlot() {
        int slot = Math.max(roundsCompleted, nextInstanceSlot);
        nextInstanceSlot = slot + 1;
        setDirty();
        return slot;
    }

    public boolean hasSnapshot() {
        return snapshot != null;
    }

    /** 旧版按玩家余额是否已经迁入共享钱包。 */
    public boolean legacyCreditsMigrated() {
        return legacyCreditsMigrated;
    }

    /**
     * 把旧版按玩家余额并入钱包，并同步更新已有的回档快照。
     *
     * <p>只执行一次：标记和余额都在战役存档里，迁移源保持只读，避免两个
     * {@link SavedData} 分别落盘时中断导致旧余额丢失或重复迁入。</p>
     */
    public void applyLegacyMigration(int amount) {
        if (legacyCreditsMigrated) {
            return;
        }
        if (amount > 0) {
            credits = saturatedAdd(credits, amount);
            if (snapshot != null) {
                // 快照直接存的就是 saveCore()（credits 在顶层），不能再套一层 "core"。
                CompoundTag updated = snapshot.copy();
                updated.putInt("credits", saturatedAdd(updated.getInt("credits"), amount));
                snapshot = updated;
            }
        }
        legacyCreditsMigrated = true;
        setDirty();
    }

    private static int saturatedAdd(int value, int amount) {
        long sum = (long) value + amount;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, sum));
    }

    /** 增加共享钱包余额（饱和到 int 上限）。 */
    public void addCredits(int amount) {
        credits = saturatedAdd(credits, amount);
        setDirty();
    }

    public boolean spendCredits(int amount) {
        if (amount <= 0 || credits < amount) {
            return false;
        }
        credits -= amount;
        setDirty();
        return true;
    }

    public void beginTravel(int destination, long gameTime) {
        beginTravel(destination, gameTime, true, true);
    }

    public void beginTravel(int destination, long gameTime,
                            boolean startHasOutpost, boolean endHasOutpost) {
        round.beginTravel(currentNode, destination, gameTime, startHasOutpost, endHasOutpost);
        setDirty();
    }

    /** 记录本轮任务海域实例锚点；跟巡回一起写进 core，回档时一起恢复。 */
    public void setInstance(int x, int y, int z) {
        round.setInstance(x, y, z);
        setDirty();
    }

    /** 抵达目的地：推进节点、记一轮、清空终点。 */
    public void arrive() {
        if (round.destination() >= 0) {
            currentNode = round.destination();
            visited.add(currentNode);
        }
        roundsCompleted++;
        selectedMissions = java.util.List.of();
        round.arrive(currentNode);
        // 抵达已经提交，出发前快照失效——不清掉的话 rollback() 还能撤销一次已完成的抵达。
        snapshot = null;
        setDirty();
    }

    public void failRound() {
        round.fail();
        setDirty();
    }

    /** 出发前拍快照；回档时恢复到这一刻。 */
    public void takeSnapshot() {
        snapshot = saveCore();
        setDirty();
    }

    /** 回到出发前。成功后快照清空，下一次出发会重新拍。 */
    public boolean rollback() {
        if (snapshot == null) {
            return false;
        }
        loadCore(snapshot.copy());
        snapshot = null;
        setDirty();
        return true;
    }

    private CompoundTag saveCore() {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag missions = new net.minecraft.nbt.ListTag();
        for (String id : selectedMissions) missions.add(net.minecraft.nbt.StringTag.valueOf(id));
        tag.put("selectedMissions", missions);
        tag.putInt("currentNode", currentNode);
        tag.putInt("credits", credits);
        tag.putInt("roundsCompleted", roundsCompleted);
        tag.putIntArray("visited", visited.stream().mapToInt(Integer::intValue).toArray());
        tag.put("round", round.save());
        return tag;
    }

    private void loadCore(CompoundTag tag) {
        currentNode = CampaignMap.isValid(tag.getInt("currentNode"))
                ? tag.getInt("currentNode") : CampaignMap.START_NODE;
        credits = Math.max(0, tag.getInt("credits"));
        roundsCompleted = Math.max(0, tag.getInt("roundsCompleted"));
        visited.clear();
        for (int id : tag.getIntArray("visited")) {
            if (CampaignMap.isValid(id)) {
                visited.add(id);
            }
        }
        visited.add(currentNode);
        round.load(tag.getCompound("round"));
        var missions = tag.getList("selectedMissions", net.minecraft.nbt.Tag.TAG_STRING);
        var ids = new java.util.ArrayList<String>();
        for (int i = 0; i < Math.min(missions.size(), CampaignMission.LIMIT); i++) ids.add(missions.getString(i));
        // 任务改由站务员/海图勾选、下潜时落到 MissionSavedData；这个旧字段只保留写入，
        // 载入时不再当作有效任务源（旧档里是假的航线委托 id）。
        selectedMissions = java.util.List.of();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("core", saveCore());
        tag.putBoolean("legacyCreditsMigrated", legacyCreditsMigrated);
        tag.putInt("nextInstanceSlot", nextInstanceSlot);
        if (snapshot != null) {
            tag.put("snapshot", snapshot.copy());
        }
        return tag;
    }

    static CampaignState load(CompoundTag tag) {
        CampaignState state = new CampaignState();
        if (tag.contains("core")) {
            state.loadCore(tag.getCompound("core"));
        }
        if (tag.contains("snapshot")) {
            state.snapshot = tag.getCompound("snapshot").copy();
        }
        state.legacyCreditsMigrated = tag.getBoolean("legacyCreditsMigrated");
        state.nextInstanceSlot = Math.max(0, tag.getInt("nextInstanceSlot"));
        return state;
    }
}
