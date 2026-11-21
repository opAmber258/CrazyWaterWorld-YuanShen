package com.opamb.barotrauma.ship.vs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * 让物理船实际占用的船坞区块常驻已加载。
 *
 * <p>{@code level.getChunk(cx,cz)} 只挂 1 tick 的 {@code TicketType.UNKNOWN} 票，
 * 生成完下一 tick 就可能被卸载；adopt 到玩家进入之间一旦无票，VS2 的
 * {@code isCollidingWithUnloadedShips} 会判定玩家撞到未加载的船，取消移动包并把
 * 位置打回旧点（日志 {@code moved while colliding with unloaded ships}，实测
 * 260 条/2 秒）。这里用无 timeout 的自定义票，票级 33（FULL，不 block/entity
 * ticking）让区块一直可用，又不额外跑方块实体。</p>
 *
 * <p>只在服务端主线程调用；票是内存态，不写 level.dat，关服/拆船/换世界时
 * {@link #clear(ServerLevel)} 移除，不会跨重启泄漏。</p>
 */
public final class VsChunkTickets {

    private static final TicketType<ChunkPos> SHIP_CHUNKS = TicketType.create(
            "barotrauma_ship_chunks", Comparator.comparingLong(ChunkPos::toLong));

    /** 当前已经挂票的区块；船移动后旧票要移除。 */
    private static final Set<ChunkPos> ACTIVE = new HashSet<>();

    private VsChunkTickets() {
    }

    /** 按船当前的 activeChunks 对齐票据：新占用区块加票并生成，不再占用的移除。 */
    public static void sync(ServerLevel level, Ship ship) {
        if (level == null || ship == null) {
            return;
        }
        Set<ChunkPos> now = new HashSet<>();
        try {
            ship.getActiveChunksSet().forEach((cx, cz) -> now.add(new ChunkPos(cx, cz)));
        } catch (Throwable ignored) {
            return;
        }
        for (ChunkPos pos : new ArrayList<>(ACTIVE)) {
            if (!now.contains(pos)) {
                try {
                    level.getChunkSource().removeRegionTicket(SHIP_CHUNKS, pos, 0, pos);
                } catch (Throwable ignored) {
                    // 世界正在卸载时移除失败无所谓。
                }
                ACTIVE.remove(pos);
            }
        }
        for (ChunkPos pos : now) {
            if (!ACTIVE.add(pos)) {
                continue;
            }
            try {
                // distance=0 -> 票级 ChunkLevel.byStatus(FULL)=33：区块完整生成，
                // 但不做 block/entity tick。先挂票再阻塞生成，避免生成完票已过期。
                level.getChunkSource().addRegionTicket(SHIP_CHUNKS, pos, 0, pos);
                level.getChunk(pos.x, pos.z);
            } catch (Throwable ignored) {
                ACTIVE.remove(pos);
            }
        }
    }

    /** 船销毁/拆解/关服：移除所有船区块票。 */
    public static void clear(ServerLevel level) {
        if (level != null) {
            for (ChunkPos pos : new ArrayList<>(ACTIVE)) {
                try {
                    level.getChunkSource().removeRegionTicket(SHIP_CHUNKS, pos, 0, pos);
                } catch (Throwable ignored) {
                    // 关服途中 level 已不可用，忽略。
                }
            }
        }
        ACTIVE.clear();
    }
}
