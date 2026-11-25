package com.opamb.barotrauma.ship;

/** An installation with condition and a maintenance requirement. */
public interface RepairTarget {

    /** 修理工具：电气类用螺丝刀，机械类用扳手（原作 RequiredItem）。 */
    enum Tool {
        SCREWDRIVER, WRENCH
    }

    /** 原作 RepairThreshold：耐久低于 80% 才能修。 */
    double REPAIR_THRESHOLD = 0.8D;

    /** 当前耐久（0..100）。 */
    double condition();

    /** 最大耐久。 */
    double maxCondition();

    default double conditionFraction() {
        double max = maxCondition();
        return max <= 1.0E-9D ? 0.0D : Math.max(0.0D, Math.min(1.0D, condition() / max));
    }

    /** 修理阈值（0..1）：低于它才会出现修理窗口。反应堆是 50%，其余 80%。 */
    default double repairThreshold() {
        return REPAIR_THRESHOLD;
    }

    default boolean canRepair() {
        return conditionFraction() < repairThreshold();
    }

    /** 默认电气设备用螺丝刀；机械类实现覆盖成 {@link Tool#WRENCH}。 */
    default Tool requiredTool() {
        return Tool.SCREWDRIVER;
    }

    default boolean isFull() {
        return conditionFraction() >= 1.0D - 1.0E-6D;
    }

    /** 增加耐久（点），到满时由实现重置劣化计时。 */
    void applyRepair(double points);

    /** 外力伤害（爆炸等）；默认无耐久概念的实现可以忽略。 */
    default void damage(double points) {
    }

    default int requiredSkill() {
        return 55;
    }
}
