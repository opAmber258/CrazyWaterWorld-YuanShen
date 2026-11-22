package com.opamb.barotrauma.world;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.ShipWaterBoxes;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

/**
 * 空气海域的“是否在水下”判定。
 *
 * <p>海里不再有真实水方块，所以不能靠 {@code FluidState} 判断。这里用三层规则：</p>
 * <ol>
 *   <li>必须在深海维度的水柱高度范围内（y = -1..-1000）；</li>
 *   <li>哨站内部是干燥区；</li>
 *   <li>未进水的船舱是干燥区，由服务端/客户端各自注册的 {@link DryZone} 判断。</li>
 * </ol>
 *
 * <p>这个类必须能在服务端和客户端同时加载，不能引用任何客户端专用类。</p>
 */
public final class OceanEnvironment {

    /** 客户端的船内干湿判定。服务端直接读取流体场。 */
    @FunctionalInterface
    public interface DryZone {
        /** true 干燥，false 浸没，null 不在已知船体范围内。 */
        @Nullable Boolean dryAt(Level level, Vec3 pos);
    }

    private static volatile DryZone clientDryZone;

    private OceanEnvironment() {
    }

    /** 客户端（渲染/滤镜）用的干燥区；由客户端初始化时注册。 */
    public static void setClientDryZone(DryZone zone) {
        clientDryZone = zone;
    }

    /** 这个位置是否位于深海的水柱体积内（不代表一定浸没，还要排除干燥区）。 */
    public static boolean isInOcean(Level level, Vec3 pos) {
        return level != null && pos != null
                && level.dimension().equals(ModDimensions.THE_DEEP)
                && pos.y <= ModDimensions.WATER_TOP_Y
                && pos.y >= ModDimensions.WATER_BOTTOM_Y;
    }

    /** 哨站内部固定是干燥的；门外平台不算。主站和每个副本顶部的站都一样。 */
    public static boolean isInOutpostDryZone(Level level, Vec3 pos) {
        if (pos == null) {
            return false;
        }
        DockingBay.Site site = DockingBay.siteAt((int) Math.floor(pos.x), (int) Math.floor(pos.z));
        if (site != null
                && Math.abs(pos.x - site.x()) <= OutpostBuilder.HALF_X
                && Math.abs(pos.z - site.z()) <= OutpostBuilder.HALF_Z
                && pos.y >= OutpostBuilder.MIN_Y
                && pos.y <= OutpostBuilder.MAX_Y) {
            return true;
        }
        // 对接通道（站内地板到潜艇顶舱口之间）也算站内干燥区
        if (DockingBay.isDryPassage(level, pos)) {
            return true;
        }
        // B 中继站（任务交付点）的内部干燥区
        BlockPos block = BlockPos.containing(pos);
        return PoiNetwork.isInsideOutpostBDryZone(block);
    }

    /**
     * 方块船（还没收成 VS2 刚体的初始潜艇等）的干燥区。
     *
     * <p>服务端没有客户端水面包，只能找附近已加载的船核心；玩家出生在初始潜艇里时，
     * 这条判断保证不会一进游戏就开始掉氧。物理船的干燥区由 VsState 负责，
     * 这里在物理船激活时直接跳过。</p>
     */
    public static boolean isInsideBlockShipDryZone(Level level, Vec3 pos) {
        if (!isInOcean(level, pos) || isInOutpostDryZone(level, pos)) {
            return false;
        }
        return Boolean.TRUE.equals(blockShipDryAt(level, pos));
    }

    @Nullable
    private static Boolean blockShipDryAt(Level level, Vec3 pos) {
        // 只看最近的核心会选错船：A 的干舱覆盖这个点，但更近的核心属于另一艘船 B，
        // B 的水盒不覆盖它 → 返回未知 → 回落到“海里”。按距离依次问所有候选核心，
        // 第一个“盒子覆盖到该点”的结果才算数；都不覆盖才是真的不在方块船里。
        for (ShipCoreBlockEntity core : ShipCores.coresNear(level, BlockPos.containing(pos), 32.0D)) {
            ShipState state = core.state();
            if (state == null || state == VsState.activeState()) {
                continue;
            }
            // 按游戏刻缓存：同一 tick 里每个玩家都会问一次，不能每次都重建水面包
            // （build 里的矩形分解和连海空腔松弛远比这次包围盒判定贵）。
            float[] boxes = state.waterBoxesFor(level, level.getGameTime()).boxes();
            Boolean dry = ShipWaterBoxes.dryAt(boxes, pos.x, pos.y, pos.z);
            if (dry != null) {
                return dry;
            }
        }
        return null;
    }

    /** 检查真实碰撞形状。pos 与 cell 使用同一坐标系（世界或船坞）。 */
    public static boolean insideCollision(Level level, BlockPos cell, Vec3 pos) {
        if (!level.hasChunkAt(cell)) {
            return false;
        }
        var shape = level.getBlockState(cell).getCollisionShape(level, cell);
        if (shape.isEmpty()) {
            return false;
        }
        if (shape == Shapes.block()) {
            return true;
        }
        double x = pos.x - cell.getX();
        double y = pos.y - cell.getY();
        double z = pos.z - cell.getZ();
        for (AABB box : shape.toAabbs()) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这个位置是否应该按“浸没”处理：在水柱内、不在哨站、也不在未进水的船舱里。
     */
    public static boolean isSubmerged(Level level, Vec3 pos) {
        if (!isInOcean(level, pos) || insideCollision(level, BlockPos.containing(pos), pos)) {
            return false;
        }
        Boolean dry;
        if (level.isClientSide()) {
            DryZone zone = clientDryZone;
            dry = zone == null ? null : zone.dryAt(level, pos);
        } else {
            dry = VsState.queryPlayerInDryCompartment(level, pos);
            if (dry == null) {
                dry = blockShipDryAt(level, pos);
            }
        }
        // 已知船舱的水位优先，进水舱靠在站内干燥区也仍然有水。
        return dry != null ? !dry : !isInOutpostDryZone(level, pos);
    }

    /**
     * 这个位置是否在某艘船的舱室里（干舱/进水舱都算）。
     *
     * <p>潜行防掉边必须在这里关掉：VS2 物理船的方块对 {@code level.noCollision}
     * 不可见，原版 {@code maybeBackOffFromEdge} 会把整个舱室当成悬崖边缘，
     * 在干舱里按 Shift 移动会被"挤开"甚至挤穿船壳（用户实测小泵上 Shift+移动）。</p>
     */
    public static boolean isInsideShipCompartment(Level level, Vec3 pos) {
        if (!isInOcean(level, pos)) {
            return false;
        }
        // 物理船用实时逆变换查询：返回值非 null 就说明在舱内（干舱/进水舱都算），
        // 不能拿干湿布尔值判断，否则进水舱里的潜行保护会失效。
        if (level.isClientSide()) {
            DryZone zone = clientDryZone;
            return zone != null && zone.dryAt(level, pos) != null;
        }
        return Boolean.TRUE.equals(VsState.queryInsideCompartment(level, pos))
                || blockShipDryAt(level, pos) != null;
    }

    /** 只判物理船舱室（不含方块船干舱）：潜行防掉边 mixin 专用。 */
    public static boolean isInsidePhysicalShipCompartment(Level level, Vec3 pos) {
        if (!isInOcean(level, pos)) {
            return false;
        }
        if (level.isClientSide()) {
            // 客户端（尤其是连专用服务器时）拿不到服务端的 compartmentQuery，
            // 只能读同步过来的水面盒；方块船没有物理刚体，这条分支显式不算。
            return Boolean.TRUE.equals(net.minecraftforge.fml.DistExecutor.unsafeCallWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.opamb.barotrauma.client.ShipWaterRenderer
                            .isInsidePhysicalCompartment(level, pos)));
        }
        return Boolean.TRUE.equals(VsState.queryInsideCompartment(level, pos));
    }

    /**
     * 游泳/溺水与相机滤镜共用同一套浸没判定。
     */
    public static boolean isOceanWaterAt(Level level, Vec3 pos) {
        return isSubmerged(level, pos);
    }


}
