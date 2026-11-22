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

    public static final int MOTION_HOLD_TICKS = 80;

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

    /**
     * 玩家是否在门/舱口附近、且**和门在同一片舱室里**。
     *
     * <p>原作的运动探测器只是门口一小块矩形（{@code Content/Items/Assemblies/
     * AutoClosingDoor.xml}：{@code MotionSensor RangeX=120 RangeY=100}，scale 0.5），
     * 不会隔着船壳/隔壁舱室触发；合并进门的自动化后如果只算直线距离，玩家在
     * 隔壁房间贴墙站着也会把门打开（用户实测：引擎室门自己开、跟着进水）。</p>
     *
     * <p>这里先按距离筛选，再要求玩家所在的水体和门格自身/六个邻居属于同一批
     * 水体——门两侧的舱室都算，隔一堵墙的第三个舱室不算。物理船用实时逆变换
     * 查玩家舱室，方块船直接用世界坐标。</p>
     */
    public static boolean playerNear(ServerLevel level, ShipState ship, BlockPos cell, double range) {
        if (ship == null || level == null || cell == null) {
            return false;
        }
        double rangeSq = range * range;
        // 物理船的 cell 是装配坐标；先换到世界坐标再和玩家世界位置比较。
        net.minecraft.world.phys.Vec3 center = isPhysicalShip(ship)
                ? VsPilot.assemblyToWorld(cell)
                : net.minecraft.world.phys.Vec3.atCenterOf(cell);
        if (center == null) {
            return false;
        }
        java.util.Set<Integer> bodies = bodiesAround(ship, cell);
        // 站在门洞/门格里的玩家常常查不到舱室（门格是水密格，不属于任何水体），
        // 这时只要人离门很近就直接算“在门口”，否则自动门会在人站着不动时判定
        // “附近没人”而不开/关人（用户报“门有时候自动识别有问题不会开”）。
        double doorwaySq = 1.5D * 1.5D;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double distanceSq = player.position().distanceToSqr(center);
            if (distanceSq > rangeSq) {
                continue;
            }
            int body = playerBody(level, ship, player);
            if (body < 0) {
                if (distanceSq <= doorwaySq) {
                    return true;
                }
                continue;
            }
            if (!bodies.isEmpty() && !bodies.contains(body)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** 门格自身 + 六邻居所在的水体（密闭舱室或连海空腔）；门两侧都要算。 */
    private static java.util.Set<Integer> bodiesAround(ShipState ship, BlockPos cell) {
        java.util.Set<Integer> bodies = new java.util.HashSet<>();
        int own = ship.simulation().bodyOf(cell);
        if (own >= 0) {
            bodies.add(own);
        }
        for (Direction direction : Direction.values()) {
            int body = ship.simulation().bodyOf(cell.relative(direction));
            if (body >= 0) {
                bodies.add(body);
            }
        }
        return bodies;
    }

    /** 玩家所在的水体下标；不在船上/没有物理船时返回 -1。 */
    private static int playerBody(ServerLevel level, ShipState ship, ServerPlayer player) {
        if (isPhysicalShip(ship)) {
            Integer index = VsState.queryCompartmentIndex(level, player.position());
            return index == null ? -1 : index;
        }
        return ship.compartmentAt(BlockPos.containing(player.position()));
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
