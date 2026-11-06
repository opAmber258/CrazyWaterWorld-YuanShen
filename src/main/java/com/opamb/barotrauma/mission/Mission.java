package com.opamb.barotrauma.mission;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 一条任务：静态定义 + 运行时状态。
 *
 * <p>任务池是**确定性的**（见 {@link com.opamb.barotrauma.world.PoiNetwork}），世界里每次
 * 都是这批任务；SavedData 只存每条的状态和认领人，存档小、逻辑简单。</p>
 *
 * <p>状态机：{@code OFFERED →（接取）ACTIVE →（达成条件）READY →（终端交付）DONE}；
 * 放弃回到 {@code OFFERED}；限时任务超时进入 {@code FAILED}（零报酬，
 * 本轮抵达目的地后回到 {@code OFFERED}）。</p>
 */
public final class Mission {

    public enum State {
        OFFERED, ACTIVE, READY, DONE, FAILED
    }

    /** 池内唯一 id（从 1 开始，任务定义顺序即 id）。 */
    public final int id;
    public final MissionType type;
    /** 目标点：沉船中心 / 侦察点 / 交付终端位置。 */
    public final BlockPos target;
    /** 到达判定半径（RECON 用；其它类型存着给 HUD 显示距离）。 */
    public final int radius;
    /** 交付奖励（credits）。 */
    public final int reward;
    /** 时限时长（游戏刻）；0 = 不限时。 */
    public final long deadlineTicks;

    private State state = State.OFFERED;
    private UUID assignedTo;
    private long assignedAtGameTime;
    /** 接取时刻算出的绝对超时点；0 = 不限时。 */
    private long deadlineAtGameTime;
    /** 仅客户端展示用：这条是否已被谁接走。 */
    private boolean clientTaken;

    public Mission(int id, MissionType type, BlockPos target, int radius, int reward) {
        this(id, type, target, radius, reward, 0L);
    }

    /** 带时限的任务定义；{@code deadlineTicks=0} 表示不限时。 */
    public Mission(int id, MissionType type, BlockPos target, int radius, int reward,
                   long deadlineTicks) {
        this.id = id;
        this.type = type;
        this.target = target.immutable();
        this.radius = radius;
        this.reward = reward;
        this.deadlineTicks = Math.max(0L, deadlineTicks);
    }

    public State state() {
        return state;
    }

    public void setState(State next) {
        this.state = next;
    }

    public UUID assignedTo() {
        return assignedTo;
    }

    public long assignedAtGameTime() {
        return assignedAtGameTime;
    }

    /** 绝对超时点（游戏刻）；0 = 不限时。 */
    public long deadlineAtGameTime() {
        return deadlineAtGameTime;
    }

    /** 是否已过时限（不限时永远 false）。 */
    public boolean isOverdue(long gameTime) {
        return deadlineAtGameTime > 0L && gameTime >= deadlineAtGameTime;
    }

    /**
     * 交付结算比例：1.0 全额；&lt;1 部分完成。
     *
     * <p>运送类送到 B 中继站终端 = 全额，送到其他终端 = 50%（部分完成）；
     * 打捞/侦察一旦达成即全额。</p>
     */
    public double rewardFraction(boolean atDeliveryEndpoint) {
        return switch (type) {
            case SALVAGE, RECON -> 1.0D;
            case DELIVERY, URGENT_DELIVERY -> atDeliveryEndpoint ? 1.0D : 0.5D;
        };
    }

    public boolean isTaken() {
        return assignedTo != null || clientTaken;
    }

    public void setClientTaken(boolean taken) {
        this.clientTaken = taken;
    }

    /** 接取。返回 false = 已被别人接走/状态不允许。 */
    public boolean assign(UUID player, long gameTime) {
        if (state != State.OFFERED) {
            return false;
        }
        state = State.ACTIVE;
        assignedTo = player;
        assignedAtGameTime = gameTime;
        deadlineAtGameTime = deadlineTicks > 0L ? gameTime + deadlineTicks : 0L;
        return true;
    }

    /** 放弃：回到可接取。 */
    public void abandon() {
        state = State.OFFERED;
        assignedTo = null;
        assignedAtGameTime = 0;
        deadlineAtGameTime = 0;
    }

    /** 交付成功：从池里关掉。 */
    public void complete() {
        state = State.DONE;
        assignedTo = null;
        deadlineAtGameTime = 0;
    }

    /** 超时失败：零报酬，保留认领人到本轮抵达时重置。 */
    public void fail() {
        state = State.FAILED;
    }

    /** FAILED → OFFERED（本轮抵达目的地时调用）；其他状态不动，返回是否重置。 */
    public boolean resetIfFailed() {
        if (state != State.FAILED) {
            return false;
        }
        state = State.OFFERED;
        assignedTo = null;
        assignedAtGameTime = 0;
        deadlineAtGameTime = 0;
        return true;
    }

    /** 客户端简报构造（MissionStatePacket 用；不带 assignedTo）。 */
    public static Mission clientBrief(int id, MissionType type, BlockPos target, int radius,
                                      int reward, State state, boolean taken) {
        return clientBrief(id, type, target, radius, reward, 0L, state, taken, 0L);
    }

    /** 带时限的客户端简报（限时任务显示倒计时用）。 */
    public static Mission clientBrief(int id, MissionType type, BlockPos target, int radius,
                                      int reward, long deadlineTicks, State state, boolean taken,
                                      long deadlineAt) {
        Mission mission = new Mission(id, type, target, radius, reward, deadlineTicks);
        mission.state = state;
        mission.clientTaken = taken;
        mission.deadlineAtGameTime = deadlineAt;
        return mission;
    }

    // ------------------------------------------------------------------ NBT

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("state", state.name());
        if (assignedTo != null) {
            tag.putUUID("assignedTo", assignedTo);
            tag.putLong("assignedAt", assignedAtGameTime);
        }
        if (deadlineAtGameTime > 0L) {
            tag.putLong("deadlineAt", deadlineAtGameTime);
        }
        return tag;
    }

    /** 从存档恢复运行时状态；id/type 等静态定义以池内为准。 */
    public void load(CompoundTag tag) {
        state = State.valueOf(tag.getString("state"));
        assignedTo = tag.hasUUID("assignedTo") ? tag.getUUID("assignedTo") : null;
        assignedAtGameTime = tag.getLong("assignedAt");
        deadlineAtGameTime = tag.getLong("deadlineAt");
    }
}
