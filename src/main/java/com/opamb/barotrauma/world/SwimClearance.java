package com.opamb.barotrauma.world;

/**
 * 站立/游泳判定的纯逻辑部分：把“头顶净空”换算成泳姿。
 *
 * <p>不引用任何 Minecraft 类型，方便 L1 单测。真正量净空的射线在
 * {@link AirOceanSwimmer#canSwimAtDepth} 里打，这里只负责阈值和迟滞。</p>
 *
 * <p>迟滞是必要的：净空刚好等于身高时，浮点抖动和水面小波动会让判定每 tick 变一次，
 * 玩家在水里静止也会看到站立/游泳来回切。带内保持上一次的状态，带外才切换。</p>
 */
public final class SwimClearance {

    /** 够站：净空 ≥ 身高 + 这个余量。 */
    public static final double STAND_MARGIN = 0.05D;
    /** 不够站：净空 < 身高 − 这个余量（进游泳更积极，出游泳更保守）。 */
    public static final double SWIM_MARGIN = 0.10D;

    private SwimClearance() {
    }

    /**
     * @param available       从脚底向上量到的可用净空（格）
     * @param height          站立姿态身高（格）
     * @param alreadySwimming 当前是否已经在游泳（用于迟滞带内保持）
     * @return true = 应该游泳
     */
    public static boolean decideSwim(double available, double height, boolean alreadySwimming) {
        if (available >= height + STAND_MARGIN) {
            return false;
        }
        if (available < height - SWIM_MARGIN) {
            return true;
        }
        return alreadySwimming;
    }
}
