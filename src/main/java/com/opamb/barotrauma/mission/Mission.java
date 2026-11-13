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
 * <p>状态机：{@code OFFERED →（接取）ACTIVE →（达成条件）READY →（靠泊目的站结算）DONE}；
 * 放弃回到 {@code OFFERED}；限时任务超时进入 {@code FAILED}（零报酬，
 * 本轮抵达目的地后回到 {@code OFFERED}）。DONE 的委托会被站务员复位成新委托再次发放。</p>
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
    /** 货运任务实际装到船上的箱数（接取时确定，0 = 还没装/不是货运）。 */
    private int cargoTotal;
    /** 货运任务每箱报酬（接取时从货物清单抄一份，方便存档/结算）。 */
    private int cargoPerCrate;
    /** 站务员在哪个站发放的（-1 = 还没发放）。 */
    private int originNode = -1;
    /** 这条任务对应的目的地节点（-1 = 还没发放；海图按它过滤）。 */
    private int destinationNode = -1;

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

    /** 深潜许可门槛：目标深度（米）超过该值需要「深潜许可」（B 中继站 301m 不受影响）。 */
    public static final int DEEP_PERMIT_DEPTH = 320;

    /** 目标深度超过许可门槛时，接取需要「深潜许可」（商店有售，不消耗）。 */
    public boolean requiresDeepPermit() {
        return -target.getY() > DEEP_PERMIT_DEPTH;
    }

    /**
     * 交付结算比例：打捞/侦察一旦达成即全额。
     *
     * <p>货运（{@code DELIVERY}/{@code URGENT_DELIVERY}）不走终端交付：靠泊目的站时
     * 按 {@link MissionCargo} 的 98% 判定自动结算，报酬 = 箱数 × 每箱单价。</p>
     */
    public double rewardFraction(boolean atDeliveryEndpoint) {
        return 1.0D;
    }

    public boolean isTaken() {
        return assignedTo != null || clientTaken;
    }

    // ------------------------------------------------------------------ 货运

    /** 接取货运任务时装了几箱、每箱多少钱。 */
    public void setCargo(int total, int perCrate) {
        this.cargoTotal = Math.max(0, total);
        this.cargoPerCrate = Math.max(0, perCrate);
    }

    public int cargoTotal() {
        return cargoTotal;
    }

    public int cargoPerCrate() {
        return cargoPerCrate;
    }

    /** 发放这条任务的哨站节点；-1 = 还没被任何站务员发放。 */
    public int originNode() {
        return originNode;
    }

    /** 任务目的地节点（海图按目的地过滤）；-1 = 还没发放。 */
    public int destinationNode() {
        return destinationNode;
    }

    /** 站务员发放时绑定起点/目的地；任务会一直挂在这个起点站，直到被接走。 */
    public void bind(int origin, int destination) {
        this.originNode = origin;
        this.destinationNode = destination;
    }

    /** 货运全额报酬：箱数 × 每箱单价（达不到 98% 就是 0）。 */
    public int cargoPayout() {
        return cargoTotal * cargoPerCrate;
    }

    public boolean isCargo() {
        return cargoTotal > 0 || com.opamb.barotrauma.mission.MissionCargo.manifestFor(this) != null;
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
        cargoTotal = 0;
        cargoPerCrate = 0;
    }

    /** 交付成功：任务关闭，解除站点绑定，之后可以被站务员当成新委托再次发放。 */
    public void complete() {
        state = State.DONE;
        assignedTo = null;
        assignedAtGameTime = 0;
        deadlineAtGameTime = 0;
        cargoTotal = 0;
        cargoPerCrate = 0;
        originNode = -1;
        destinationNode = -1;
    }

    /** 已完成的委托再次发放时复位成可接取状态（相当于原作里生成一条新委托）。 */
    public void resetForRegrant() {
        state = State.OFFERED;
        assignedTo = null;
        assignedAtGameTime = 0;
        deadlineAtGameTime = 0;
        cargoTotal = 0;
        cargoPerCrate = 0;
        originNode = -1;
        destinationNode = -1;
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
        return clientBrief(id, type, target, radius, reward, deadlineTicks, state, taken,
                deadlineAt, -1, -1);
    }

    /** 带起点/目的地节点的客户端简报（海图按目的地过滤任务）。 */
    public static Mission clientBrief(int id, MissionType type, BlockPos target, int radius,
                                      int reward, long deadlineTicks, State state, boolean taken,
                                      long deadlineAt, int originNode, int destinationNode) {
        Mission mission = new Mission(id, type, target, radius, reward, deadlineTicks);
        mission.state = state;
        mission.clientTaken = taken;
        mission.deadlineAtGameTime = deadlineAt;
        mission.originNode = originNode;
        mission.destinationNode = destinationNode;
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
        if (cargoTotal > 0) {
            tag.putInt("cargoTotal", cargoTotal);
            tag.putInt("cargoPerCrate", cargoPerCrate);
        }
        if (originNode >= 0) {
            tag.putInt("originNode", originNode);
        }
        if (destinationNode >= 0) {
            tag.putInt("destinationNode", destinationNode);
        }
        return tag;
    }

    /** 从存档恢复运行时状态；id/type 等静态定义以池内为准。 */
    public void load(CompoundTag tag) {
        state = State.valueOf(tag.getString("state"));
        assignedTo = tag.hasUUID("assignedTo") ? tag.getUUID("assignedTo") : null;
        assignedAtGameTime = tag.getLong("assignedAt");
        deadlineAtGameTime = tag.getLong("deadlineAt");
        cargoTotal = tag.getInt("cargoTotal");
        cargoPerCrate = tag.getInt("cargoPerCrate");
        originNode = tag.contains("originNode") ? tag.getInt("originNode") : -1;
        destinationNode = tag.contains("destinationNode") ? tag.getInt("destinationNode") : -1;
    }
}
