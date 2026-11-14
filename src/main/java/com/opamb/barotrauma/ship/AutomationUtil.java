package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 机器内置自动化的公共判定：附近有没有玩家、门/舱口两侧进水比例。
 *
 * <p>原版这些是“运动探测器/水探测器 + 逻辑门”；合并进机器后，门自己查
 * 玩家距离和水位，不再需要独立传感器方块。</p>
 */
public final class AutomationUtil {

    private AutomationUtil() {
    }

    /** 装配坐标下，玩家是否在 cell 的 range 格内（物理船会把玩家坐标转成装配坐标）。 */
    public static boolean playerNear(ServerLevel level, BlockPos cell, double range) {
        double rangeSq = range * range;
        for (ServerPlayer player : level.players()) {
            BlockPos pos = VsState.active()
                    ? VsPilot.toAssembly(player.blockPosition())
                    : player.blockPosition();
            if (pos.distSqr(cell) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    /**
     * 门/舱口周围 6 个相邻格里最高的进水比例（0..1）。
     *
     * <p>门格本身是“水密墙”，不在任何舱室里；要看它两侧的邻居。取最大值，
     * 任意一侧进水超过阈值就关门。</p>
     */
    public static double maxWaterFraction(ShipState ship, BlockPos cell) {
        double max = 0.0D;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = cell.relative(direction);
            int body = ship.simulation().bodyOf(neighbor);
            if (body < 0) {
                body = ship.simulation().pumpBody(neighbor);
            }
            if (body >= 0) {
                max = Math.max(max, ship.simulation().bodyWaterFraction(body));
            }
        }
        return max;
    }
}
