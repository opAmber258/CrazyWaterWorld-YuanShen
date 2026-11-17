package com.opamb.barotrauma.network;

/**
 * 声纳/自动驾驶的物理船归属判定（纯逻辑，便于单测）。
 *
 * <p>玩家可能在方块船上，而世界上还有另一艘已装配的物理船；归属不能只看距离。
 * 规则：已经确认在物理船上时优先；否则只有在“离物理船中心足够近且附近没有
 * 方块船核心”时才把玩家算作物理船。</p>
 */
public final class SonarShipOwnership {

    /** 距离兜底：玩家离物理船中心 128 格内才考虑算物理船。 */
    public static final double NEAR_SHIP_RANGE = 128.0D;
    public static final double NEAR_SHIP_RANGE_SQ = NEAR_SHIP_RANGE * NEAR_SHIP_RANGE;
    /** 方块船核心消歧范围：48 格内有核心就算玩家在方块船上。 */
    public static final double BLOCK_SHIP_RANGE = 48.0D;

    private SonarShipOwnership() {
    }

    /**
     * @param confirmed      玩家/声纳已被确认属于物理船（舱室判定等）
     * @param nearShip       玩家离物理船中心在 {@link #NEAR_SHIP_RANGE} 内
     * @param blockShipNearby 玩家附近有方块船核心
     */
    public static boolean ownedByPhysicalShip(boolean confirmed, boolean nearShip,
                                              boolean blockShipNearby) {
        return confirmed || (nearShip && !blockShipNearby);
    }
}
