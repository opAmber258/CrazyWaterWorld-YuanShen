package com.opamb.barotrauma.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 任务海域实例（"副本"）的锚点布局。
 *
 * <p>2026-11-08 按用户要求改成<b>直接复用主世界海域地形</b>：不再为每轮任务挖
 * 封闭洞穴，锚点周围就是 {@link SeaTerrain} 生成的开阔海域（洞顶 -44、海底
 * -500 左右，岩柱/浮岩岛照常，但锚点 64 格内留机动净空）。副本站本身仍由
 * {@code OutpostBuilder} 挂在压平的洞顶上，船从站下方 3×1 竖井进入大海域。</p>
 *
 * <ul>
 *   <li>第 n 轮用第 n 个 slot，锚点间距 {@link #SPACING}，互不重叠；</li>
 *   <li>地形完全由坐标 + 世界种子决定，同一轮重进/读档完全一致；</li>
 *   <li>没去过的 slot 永远不会生成（区块惰性加载）。</li>
 * </ul>
 */
public final class MissionInstance {

    /** 实例带起点 x，远离战役海图区域（0..960）。 */
    public static final int BASE_X = 20000;
    /** 每轮占用的 x 间距；大于 2×{@link #SITE_RADIUS}，相邻副本站不会互相干扰。 */
    public static final int SPACING = 1024;
    /** 副本站/主力舰活动区半径（和 SeaTerrain 的机动净空一致）。 */
    public static final int SITE_RADIUS = 64;

    /**
     * 潜艇在本实例里的出生高度（船体中心）。
     *
     * <p>2026-11-09 用户实测"离头顶哨站太远"，从 -100 上移 20 格到 -80：
     * 核心大约在 -79，离码头标准位（-72）只有 7 格，抬头就能回到前哨站对接。</p>
     */
    public static final int ANCHOR_Y = -80;

    /** 一次出勤对应的实例锚点。 */
    public record Anchor(int slot, int x, int y, int z) {
        /** 潜艇落点（船体中心）。 */
        public Vec3 spawn() {
            return new Vec3(x + 0.5D, y, z + 0.5D);
        }
    }

    private MissionInstance() {
    }

    /** 第 {@code roundIndex} 轮出勤的锚点；轮次从 0 开始（= 战役已完成的轮数）。 */
    public static Anchor anchorForRound(int roundIndex) {
        int slot = Math.max(0, roundIndex);
        return new Anchor(slot, BASE_X + slot * SPACING, ANCHOR_Y, 0);
    }

    /** 坐标附近的实例站点锚点 x（世界生成用；不在任何站净空范围内返回 null）。 */
    public static Integer siteXAt(int x, int z) {
        if (Math.abs(z) > SITE_RADIUS) {
            return null;
        }
        int slot = Math.round((x - BASE_X) / (float) SPACING);
        if (slot < 0) {
            return null;
        }
        int ax = BASE_X + slot * SPACING;
        return Math.abs(x - ax) <= SITE_RADIUS ? ax : null;
    }

    // ------------------------------------------------------------------
    // 传送前预加载
    // ------------------------------------------------------------------

    /**
     * 把锚点周围的区块同步加载出来。
     *
     * <p>物理船传送过去之后，VS2 会在下一 tick 把船的 activeChunks 更新到新位置；
     * 如果那时区块还没加载，玩家一移动就会被 VS2 判定成"撞到未加载的船"并回弹
     * （日志里的 {@code moved while colliding with unloaded ships}）。所以先加载再传送。</p>
     */
    public static int preload(ServerLevel level, Anchor anchor, int chunkRadius) {
        int centerX = anchor.x() >> 4;
        int centerZ = anchor.z() >> 4;
        int loaded = 0;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                level.getChunk(centerX + dx, centerZ + dz);
                loaded++;
            }
        }
        return loaded;
    }
}
