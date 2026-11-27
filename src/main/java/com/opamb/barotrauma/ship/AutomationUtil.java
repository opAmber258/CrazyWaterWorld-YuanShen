package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.block.HatchBlock;

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
     * Original assemblies sample a rectangle at the opening. Extend it to three
     * dimensions and reject rays crossing a wall or another closed doorway.
     */
    public static boolean playerNear(ServerLevel level, ShipState ship, BlockPos pos, Direction facing,
                                     boolean hatch) {
        boolean physical = isPhysicalShip(ship);
        BlockPos cell = physical ? VsPilot.toAssembly(pos) : pos;
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        Vec3 worldCenter = physical ? VsPilot.assemblyToWorld(cell) : center;
        if (worldCenter == null) return false;

        Direction width = facing.getClockWise();
        for (ServerPlayer player : level.players()) {
            Vec3 torso = player.position().add(0, player.getBbHeight() * 0.5, 0);
            if (!player.isAlive() || player.isSpectator() || torso.distanceToSqr(worldCenter) > 20.0D) continue;
            if (physical) {
                torso = VsPilot.worldToAssemblyPoint(level, torso);
                if (torso == null) continue;
                torso = torso.add(pos.getX() - cell.getX(), pos.getY() - cell.getY(), pos.getZ() - cell.getZ());
            }
            Vec3 offset = torso.subtract(center);
            double along = Math.abs(offset.x * facing.getStepX() + offset.z * facing.getStepZ());
            double across = Math.abs(offset.x * width.getStepX() + offset.z * width.getStepZ());
            if (along > (hatch ? 0.8D : 1.8D) || across > (hatch ? 1.8D : 0.8D)
                    || Math.abs(offset.y) > (hatch ? 2.0D : 1.0D)) continue;

            // Aim into the opening rather than along the top edge of a block.
            Vec3 opening = hatch ? center.add(0, -0.0625D, 0) : center;
            Vec3 torsoPosition = torso;
            boolean blocked = BlockGetter.traverseBlocks(torso, opening, level, (world, rayCell) -> {
                if (!world.hasChunkAt(rayCell)) return true;
                var state = world.getBlockState(rayCell);
                if (hatch && state.getBlock() instanceof HatchBlock && rayCell.getY() == pos.getY()
                        && (rayCell.equals(pos) || rayCell.equals(pos.relative(width))
                        || rayCell.equals(pos.relative(width.getOpposite())))) return null;
                if (!hatch && state.getBlock() instanceof BulkheadDoorBlock
                        && (rayCell.equals(pos) || rayCell.equals(pos.above()))) return null;
                return state.getCollisionShape(world, rayCell).clip(torsoPosition, opening, rayCell) == null
                        ? null : true;
            }, world -> false);
            if (!blocked) return true;
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
