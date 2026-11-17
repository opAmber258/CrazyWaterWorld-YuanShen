package com.opamb.barotrauma.ship;

/**
 * 可修理设备（对应原作 {@code Repairable}）。
 *
 * <p>耐久低于 {@link #REPAIR_THRESHOLD}（原作 80%）才能修。修理分两部分：
 * 持续恢复（满耐久约 {@link #repairDurationSeconds()} 秒）和 QTE 加成
 * （指针扫进已恢复区域时点击，额外 {@link #qteBoostPoints()} 点）。</p>
 *
 * <p>电池、接线盒、泵、反应堆、引擎、导航/声纳核心、门/舱口都已实现。
 * 手持对应工具右键设备会打开独立修理窗口（{@code RepairScreen}），
 * 窗口里点“修理”开始持续恢复，指针扫进已恢复区域时再点给 QTE 加成。</p>
 */
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

    /** 从 0 修到满的持续时长（秒）；无技能系统时取原作低技能档 25 秒。 */
    default double repairDurationSeconds() {
        return 25.0D;
    }

    /** 一次成功 QTE 的额外耐久（原作 RepairDegreeOfSuccess × 3，无技能 = 3）。 */
    default double qteBoostPoints() {
        return 3.0D;
    }
}
