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

    /**
     * 这艘船是不是当前装配中的物理船。
     *
     * <p>不能只看全局 {@code VsState.active()}：物理船 A 活动时，方块船 B 的
     * 门也会被当成装配坐标换算，导致 B 的门用 A 的世界位置做判定。</p>
     */
    public static boolean isPhysicalShip(ShipState ship) {
        return ship != null && VsState.active() && VsState.activeState() == ship;
    }

    /** 装配坐标下，玩家是否在 cell 的 range 格内（物理船会把玩家坐标转成装配坐标）。 */
    public static boolean playerNear(ServerLevel level, ShipState ship, BlockPos cell, double range) {
        double rangeSq = range * range;
        // 物理船的 cell 是装配坐标；先换到世界坐标再和玩家世界位置比较。
        net.minecraft.world.phys.Vec3 center = isPhysicalShip(ship)
                ? VsPilot.assemblyToWorld(cell)
                : net.minecraft.world.phys.Vec3.atCenterOf(cell);
        if (center == null) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= rangeSq) {
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
    /**
     * 这扇门/舱口是否直接连到外海。
     *
     * <p>水密模式要单独看这一项：舱内干燥时 {@link #maxWaterFraction} 是 0，
     * 但门一开海水就会灌进来。</p>
     *
     * <p>两种“通海”：门格直接开在船壳外表面（{@code SEA_BODY} 连线），或者门外
     * 空气在包围盒内部、形成一个连到包围盒边界的空腔（船壳有突出部分时外门会
     * 落在包围盒里面）。后者在几何里是 {@code openPockets}，编号从
     * {@code compartments().size()} 起算——只认 {@code SEA_BODY} 会让这扇门在
     * 空腔水量为 0 时被水密模式自动打开。</p>
     */
    public static boolean opensToSea(ShipState ship, BlockPos cell) {
        int sealedBodies = ship.geometry().compartments().size();
        for (ShipGeometry.DoorLink link : ship.geometry().doorLinks()) {
            if (!link.cell().equals(cell)) {
                continue;
            }
            if (link.body() == ShipGeometry.SEA_BODY || link.other() == ShipGeometry.SEA_BODY) {
                return true;
            }
            // 连海空腔（openPocket）也算外海一侧。
            if (link.body() >= sealedBodies || link.other() >= sealedBodies) {
                return true;
            }
        }
        return false;
    }

    public static double maxWaterFraction(ShipState ship, BlockPos cell) {
        double max = 0.0D;
        for (Direction direction : Direction.values()) {
            // 只看直接相邻的水体。pumpBody 会为了实心泵再多跳一格，
            // 用在这里会穿过一格隔墙读到另一个舱室，把干舱之间的门关掉。
            int body = ship.simulation().bodyOf(cell.relative(direction));
            if (body >= 0) {
                max = Math.max(max, ship.simulation().bodyWaterFraction(body));
            }
        }
        return max;
    }
}
