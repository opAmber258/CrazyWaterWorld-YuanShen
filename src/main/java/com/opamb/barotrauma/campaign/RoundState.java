package com.opamb.barotrauma.campaign;

import net.minecraft.nbt.CompoundTag;

/** 一轮出勤的运行时状态：从出发到抵达/失败。 */
public final class RoundState {

    private RoundPhase phase = RoundPhase.AT_NODE;
    private int origin = CampaignMap.START_NODE;
    private int destination = -1;
    private long startedAtGameTime;
    /** 本轮任务海域实例的锚点；没出发过就是 MIN_VALUE。 */
    private int instanceX = Integer.MIN_VALUE;
    private int instanceY = Integer.MIN_VALUE;
    private int instanceZ = Integer.MIN_VALUE;
    /** 本轮起点/终点有没有哨站（原作 Location.Type.HasOutpost；无人航路点两端都不建站）。 */
    private boolean startHasOutpost = true;
    private boolean endHasOutpost = true;
    /** 本轮航行关卡类型/种子/难度；旧存档没有就是 basic/0。 */
    private String levelType = "coldcavernsbasic";
    private long levelSeed;
    private int levelDifficulty;
    /**
     * 本轮出发站到目的站的实际距离。存下来是为了读档/改代码后不会把已经生成的
     * 航道端点挪走；旧存档没有这个字段（0）时回退到关卡参数的 routeLength。
     */
    private int levelDistance;
    /**
     * 写这轮实例时用的槽位间距。旧存档没有（0）：按 1024 的旧布局还原，
     * 否则 instanceX 会被新间距算到别的槽位，航道和已生成的站点错开。
     */
    private int levelSpacing;

    public RoundPhase phase() {
        return phase;
    }

    public int origin() {
        return origin;
    }

    public int destination() {
        return destination;
    }

    public long startedAtGameTime() {
        return startedAtGameTime;
    }

    public boolean isDiving() {
        return phase == RoundPhase.DIVING;
    }

    public boolean hasInstance() {
        return instanceX != Integer.MIN_VALUE;
    }

    public int instanceX() {
        return instanceX;
    }

    public int instanceY() {
        return instanceY;
    }

    public int instanceZ() {
        return instanceZ;
    }

    public boolean startHasOutpost() {
        return startHasOutpost;
    }

    public boolean endHasOutpost() {
        return endHasOutpost;
    }

    public String levelType() {
        return levelType;
    }

    public long levelSeed() {
        return levelSeed;
    }

    public int levelDifficulty() {
        return levelDifficulty;
    }

    public int levelDistance() {
        return levelDistance;
    }

    public int levelSpacing() {
        return levelSpacing;
    }

    /** 记录本轮关卡类型/种子/难度（在 beginTravel 之后调用，失败回滚会恢复上一轮）。 */
    public void setLevel(String typeId, long seed, int difficulty) {
        setLevel(typeId, seed, difficulty, 0);
    }

    /** 记录本轮关卡类型/种子/难度/实际路线距离。 */
    public void setLevel(String typeId, long seed, int difficulty, int distance) {
        setLevel(typeId, seed, difficulty, distance, 0);
    }

    /** 记录本轮关卡类型/种子/难度/实际路线距离/槽位间距。 */
    public void setLevel(String typeId, long seed, int difficulty, int distance, int spacing) {
        this.levelType = typeId == null || typeId.isBlank() ? "coldcavernsbasic" : typeId;
        this.levelSeed = seed;
        this.levelDifficulty = Math.max(0, difficulty);
        this.levelDistance = Math.max(0, distance);
        this.levelSpacing = Math.max(0, spacing);
    }

    /** 记录本轮任务海域实例锚点（同一轮内不变）。 */
    public void setInstance(int x, int y, int z) {
        instanceX = x;
        instanceY = y;
        instanceZ = z;
    }

    public void beginTravel(int from, int to, long gameTime) {
        beginTravel(from, to, gameTime, true, true);
    }

    public void beginTravel(int from, int to, long gameTime,
                            boolean startOutpost, boolean endOutpost) {
        phase = RoundPhase.DIVING;
        origin = from;
        destination = to;
        startedAtGameTime = gameTime;
        startHasOutpost = startOutpost;
        endHasOutpost = endOutpost;
    }

    /** 抵达：回到节点状态，终点清空。保留本轮的哨站标记，供重启后恢复关卡配置。 */
    public void arrive(int node) {
        phase = RoundPhase.AT_NODE;
        origin = node;
        destination = -1;
        startedAtGameTime = 0L;
    }

    public void fail() {
        phase = RoundPhase.FAILED;
    }

    public void resetAt(int node, long gameTime) {
        phase = RoundPhase.AT_NODE;
        origin = node;
        destination = -1;
        startedAtGameTime = gameTime;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", phase.name());
        tag.putInt("origin", origin);
        tag.putInt("destination", destination);
        tag.putLong("startedAt", startedAtGameTime);
        tag.putInt("instanceX", instanceX);
        tag.putInt("instanceY", instanceY);
        tag.putInt("instanceZ", instanceZ);
        tag.putBoolean("startHasOutpost", startHasOutpost);
        tag.putBoolean("endHasOutpost", endHasOutpost);
        tag.putString("levelType", levelType);
        tag.putLong("levelSeed", levelSeed);
        tag.putInt("levelDifficulty", levelDifficulty);
        tag.putInt("levelDistance", levelDistance);
        tag.putInt("levelSpacing", levelSpacing);
        return tag;
    }

    public void load(CompoundTag tag) {
        try {
            phase = RoundPhase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            // 旧存档/空 tag：按停在节点处理
            phase = RoundPhase.AT_NODE;
        }
        origin = tag.getInt("origin");
        destination = tag.getInt("destination");
        startedAtGameTime = tag.getLong("startedAt");
        instanceX = tag.contains("instanceX") ? tag.getInt("instanceX") : Integer.MIN_VALUE;
        instanceY = tag.contains("instanceY") ? tag.getInt("instanceY") : Integer.MIN_VALUE;
        instanceZ = tag.contains("instanceZ") ? tag.getInt("instanceZ") : Integer.MIN_VALUE;
        if (instanceX == Integer.MIN_VALUE || instanceY == Integer.MIN_VALUE || instanceZ == Integer.MIN_VALUE) {
            instanceX = Integer.MIN_VALUE;
            instanceY = Integer.MIN_VALUE;
            instanceZ = Integer.MIN_VALUE;
        }
        startHasOutpost = !tag.contains("startHasOutpost") || tag.getBoolean("startHasOutpost");
        endHasOutpost = !tag.contains("endHasOutpost") || tag.getBoolean("endHasOutpost");
        levelType = com.opamb.barotrauma.world.level.LevelType
                .fromId(tag.contains("levelType") ? tag.getString("levelType") : null).id();
        levelSeed = tag.contains("levelSeed") ? tag.getLong("levelSeed") : 0L;
        levelDifficulty = tag.contains("levelDifficulty")
                ? Math.max(0, tag.getInt("levelDifficulty")) : 0;
        levelDistance = tag.contains("levelDistance")
                ? Math.max(0, tag.getInt("levelDistance")) : 0;
        levelSpacing = tag.contains("levelSpacing")
                ? Math.max(0, tag.getInt("levelSpacing")) : 0;
        if (hasInstance()) {
            // 旧存档：当时固定出发距离 192、槽位间距 1024。留 0 会让恢复逻辑
            // 用新关卡的默认值，把已经生成的出发站/航道端点挪走。
            if (levelDistance <= 0) {
                levelDistance = 192;
            }
            if (levelSpacing <= 0) {
                levelSpacing = 1024;
            }
        }
        if (!CampaignMap.isValid(origin)) {
            origin = CampaignMap.START_NODE;
        }
        if (!CampaignMap.isValid(destination)) {
            destination = -1;
        }
        if (phase == RoundPhase.AT_NODE) {
            destination = -1;
            startedAtGameTime = 0L;
        } else if (phase == RoundPhase.DIVING && destination < 0) {
            // 存档里的目的地丢了：不能卡在下潜态
            phase = RoundPhase.AT_NODE;
            startedAtGameTime = 0L;
        }
    }
}
