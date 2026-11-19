package com.opamb.barotrauma.ship;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * 设备耐久/劣化组件（对应原作 {@code Repairable} 的通用部分）。
 *
 * <p>每个设备自己决定"活动"条件（泵在抽水、反应堆开机、引擎有推力），
 * 组件只负责：</p>
 * <ul>
 *   <li>初始宽限期（原作 Min/MaxDeteriorationDelay）；</li>
 *   <li>活动时按 {@code deteriorationspeed}/秒 掉耐久；</li>
 *   <li>不低于 MinDeteriorationCondition 时停止自然劣化；</li>
 *   <li>修理恢复、满了重置宽限期；</li>
 *   <li>NBT 存读。</li>
 * </ul>
 *
 * <p>电池/接线盒已经有自己的泡水/过压规则，继续用原来的字段；这个组件给
 * 泵/反应堆/引擎/导航/门舱口这类设备复用。</p>
 */
public final class ConditionComponent {

    private final double maxCondition;
    private final double decayPerSecond;
    private final int minDelayTicks;
    private final int maxDelayTicks;
    private final double minDeteriorationCondition;
    private final double repairThreshold;

    private double condition;
    private long graceTicks = -1L;

    public ConditionComponent(double maxCondition, double decayPerSecond,
                              int minDelaySeconds, int maxDelaySeconds,
                              double minDeteriorationCondition, double repairThreshold) {
        this.maxCondition = Math.max(1.0D, maxCondition);
        this.decayPerSecond = Math.max(0.0D, decayPerSecond);
        this.minDelayTicks = Math.max(0, minDelaySeconds) * 20;
        this.maxDelayTicks = Math.max(minDelaySeconds, maxDelaySeconds) * 20;
        this.minDeteriorationCondition = Mth.clamp(minDeteriorationCondition, 0.0D,
                this.maxCondition);
        this.repairThreshold = Mth.clamp(repairThreshold, 0.0D, 1.0D);
        this.condition = this.maxCondition;
    }

    /** 活动时每 tick 调一次；不活动不劣化，也不初始化宽限。返回持久字段是否改变。 */
    public boolean tick(Level level, boolean active) {
        return tick(level, active, 1.0D / 20.0D);
    }

    /** 活动时按给定的模拟秒数劣化（船核心每 2 tick 才推进一次时用 0.1s）。 */
    public boolean tick(Level level, boolean active, double seconds) {
        if (!active || decayPerSecond <= 0.0D || condition <= minDeteriorationCondition
                || seconds <= 0.0D) {
            return false;
        }
        if (graceTicks < 0L) {
            if (level == null) {
                graceTicks = minDelayTicks;
            } else {
                int span = Math.max(1, maxDelayTicks - minDelayTicks + 1);
                graceTicks = minDelayTicks + level.random.nextInt(span);
            }
        }
        if (graceTicks > 0L) {
            long step = Math.max(1L, Math.round(seconds * 20.0D));
            graceTicks = Math.max(0L, graceTicks - step);
            return true;
        }
        double before = condition;
        condition = Math.max(minDeteriorationCondition, condition - decayPerSecond * seconds);
        return condition != before;
    }

    /** 外力伤害（破口、爆炸等）。 */
    public void damage(double points) {
        if (points > 0.0D) {
            condition = Math.max(0.0D, condition - points);
        }
    }

    public void repair(double points) {
        if (points <= 0.0D) {
            return;
        }
        condition = Mth.clamp(condition + points, 0.0D, maxCondition);
        if (condition >= maxCondition - 1.0E-6D) {
            graceTicks = -1L;
        }
    }

    public double condition() {
        return condition;
    }

    public double maxCondition() {
        return maxCondition;
    }

    public double fraction() {
        return Mth.clamp(condition / maxCondition, 0.0D, 1.0D);
    }

    public boolean broken() {
        return condition <= 0.0D;
    }

    public boolean canRepair() {
        return fraction() < repairThreshold;
    }

    /** 修理阈值（0..1），给修理窗口读。 */
    public double repairThreshold() {
        return repairThreshold;
    }

    /**
     * 原作 {@code Repairable.AdjustPowerConsumption}：耐久低于 RepairThreshold
     * （默认/引擎都是 80%）时，耗电乘 {@code Lerp(1.5, 1, condition/maxCondition)}。
     *
     * <p>是直接按耐久比例插值，不是把 80% 归一到 1.0：耐久 79% 时倍率约 1.105，
     * 0% 时 1.5；耐久 ≥80% 时不调整。之前写成 {@code 1.5 - 0.5*f/0.8} 会让
     * 80% 以下少算约 10% 耗电。</p>
     */
    public double powerPenalty() {
        double fraction = fraction();
        if (fraction >= repairThreshold) {
            return 1.0D;
        }
        return 1.5D - 0.5D * fraction;
    }

    public boolean isFull() {
        return condition >= maxCondition - 1.0E-6D;
    }

    public double decayPerSecond() {
        return decayPerSecond;
    }

    public void save(CompoundTag tag, String key) {
        tag.putDouble(key, condition);
        tag.putLong(key + "Grace", graceTicks);
    }

    public void load(CompoundTag tag, String key) {
        condition = tag.contains(key) ? Mth.clamp(tag.getDouble(key), 0.0D, maxCondition)
                : maxCondition;
        graceTicks = tag.contains(key + "Grace") ? tag.getLong(key + "Grace") : -1L;
    }
}
