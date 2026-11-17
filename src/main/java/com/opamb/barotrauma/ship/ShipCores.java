package com.opamb.barotrauma.ship;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 船核心的查找 / 重扫辅助。
 */
public final class ShipCores {

    private ShipCores() {
    }

    /** 让 {@code center} 附近的船核心重新扫描（开合门、破口之后用）。 */
    public static int rescanNear(Level level, BlockPos center, int radius) {
        int scanned = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShipCoreBlockEntity core) {
                core.rescan(level);
                scanned++;
            }
        }
        return scanned;
    }

    /**
     * 玩家放下门/舱口后刷新船体几何。
     *
     * <p>新方块不在旧 {@code hull} 里时，{@code DrainPumpBlockEntity.resolveCore} 的
     * {@code tracksHullBlock(pos)} 会返回 false，之后开合门触发的重扫也不会执行，
     * 舱室分隔/水量隔离就一直漏掉这格。放置是低频操作，这里重扫附近方块船核心，
     * 并让物理船重新扫描船坞方块。</p>
     */
    public static void refreshNearby(Level level, BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        // 物理船上新放的方块要进靠港还原快照，否则会留在船坞里回不来。
        com.opamb.barotrauma.ship.vs.VsState.recordPlacedBlock(server, pos);
        for (ShipCoreBlockEntity core : coresNear(level, pos, 64.0D)) {
            core.rescan(level);
        }
        com.opamb.barotrauma.ship.vs.VsState.refreshGeometry(server);
    }

    /**
     * 列出 {@code center} 附近（已加载区块内）的所有船核心。
     *
     * <p>不逐格查方块（那要几十万次），而是直接翻已加载区块的方块实体表——核心很少，
     * 一次几分钟区块的遍历就够了。</p>
     */
    public static List<ShipCoreBlockEntity> coresNear(Level level, BlockPos center, double maxDistance) {
        int chunkRadius = Math.max(1, (int) Math.ceil(maxDistance / 16.0D));
        ChunkPos origin = new ChunkPos(center);
        double limit = maxDistance * maxDistance;
        List<ShipCoreBlockEntity> cores = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunk = level.getChunkSource()
                        .getChunk(origin.x + dx, origin.z + dz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                    BlockEntity be = chunk.getBlockEntity(bePos);
                    if (be instanceof ShipCoreBlockEntity core
                            && core.getBlockPos().distSqr(center) <= limit) {
                        cores.add(core);
                    }
                }
            }
        }
        return cores;
    }

    /**
     * 找离 {@code center} 最近的船核心。
     */
    @Nullable
    public static ShipCoreBlockEntity nearestCore(Level level, BlockPos center, double maxDistance) {
        ShipCoreBlockEntity best = null;
        double bestDistance = maxDistance * maxDistance;
        for (ShipCoreBlockEntity core : coresNear(level, center, maxDistance)) {
            double distance = core.getBlockPos().distSqr(center);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = core;
            }
        }
        return best;
    }

    /**
     * 找 {@code center} 附近的船，船状态还没扫出来就先扫一次。
     *
     * <p>方块船的 {@link ShipState} 不存档，世界重开后是 null；以前只有打开电力
     * 面板/声纳时才会 rescan。于是船上的机器在"没人打开过面板"之前一直以为身边
     * 没有船，表现为加工台/解构仪明明有电却点不动、去面板绑定一次之后又莫名恢复。</p>
     */
    @Nullable
    public static ShipState stateNear(Level level, BlockPos center, double maxDistance) {
        ShipCoreBlockEntity core = nearestCore(level, center, maxDistance);
        if (core == null) {
            return null;
        }
        ShipState state = core.state();
        return state != null ? state : core.rescan(level);
    }

    /**
     * 找几何真正包含 {@code cell} 的船。
     *
     * <p>{@link #stateNear} 只看核心距离：两艘船并排时，A 船设备可能离 B 船核心更近，
     * 取到 B 的几何后 {@code tracksHullBlock} 校验失败，设备就停止上报。这里遍历
     * 附近核心，逐个检查几何归属，找不到再返回 null。</p>
     */
    @Nullable
    public static ShipState stateOwning(Level level, BlockPos cell, double maxDistance) {
        for (ShipCoreBlockEntity core : coresNear(level, cell, maxDistance)) {
            ShipState state = core.state();
            if (state == null) {
                state = core.rescan(level);
            }
            if (state != null && state.tracksHullBlock(cell)) {
                return state;
            }
        }
        return null;
    }

    /**
     * 解析"我所在的船"：先看提示坐标上是不是真有核心（船没动时最准），
     * 船正在开、核心已经挪走时退回"离玩家最近的核心"。
     */
    @Nullable
    public static ShipCoreBlockEntity resolve(Level level, @Nullable BlockPos hint, BlockPos fallback) {
        if (hint != null && level.getBlockEntity(hint) instanceof ShipCoreBlockEntity core) {
            return core;
        }
        return nearestCore(level, fallback, 32.0D);
    }
}
