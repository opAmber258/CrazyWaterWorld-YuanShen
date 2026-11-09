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

    /** 抵达：回到节点状态，终点清空。 */
    public void arrive(int node) {
        phase = RoundPhase.AT_NODE;
        origin = node;
        destination = -1;
        startedAtGameTime = 0L;
        startHasOutpost = true;
        endHasOutpost = true;
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
