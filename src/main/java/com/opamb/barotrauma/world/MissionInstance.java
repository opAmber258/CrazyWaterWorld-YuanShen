package com.opamb.barotrauma.world;

import net.minecraft.core.BlockPos;
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

    /** 出发站离目的站的水平距离。 */
    public static final int START_DISTANCE = 192;

    /** 本轮关卡两端的站点类型：无人航路点不建站（原作 Location.Type.HasOutpost）。 */
    private static int routeSlot = Integer.MIN_VALUE;
    private static boolean routeStartOutpost = true;
    private static boolean routeEndOutpost = true;

    /** 下潜时写入本轮起点/终点是否需要哨站；世界生成按这个决定建不建站。 */
    public static void setRouteOutposts(int slot, boolean startOutpost, boolean endOutpost) {
        routeSlot = slot;
        routeStartOutpost = startOutpost;
        routeEndOutpost = endOutpost;
    }

    /** 这个站点本轮要不要生成哨站（主站永远要）。 */
    public static boolean outpostExpected(DockingBay.Site site) {
        if (DockingBay.Site.MAIN.equals(site)) {
            return true;
        }
        int slot = siteSlot(site);
        if (slot != routeSlot) {
            return true;
        }
        Anchor anchor = anchorForRound(slot);
        if (site.equals(startSite(anchor))) {
            return routeStartOutpost;
        }
        if (site.x() == anchor.x() && site.z() == anchor.z()) {
            return routeEndOutpost;
        }
        return true;
    }

    /** 从站点坐标反推轮次（出发站偏移 192 格，仍然落在同一个 slot 里）。 */
    public static int siteSlot(DockingBay.Site site) {
        return Math.max(0, Math.round((site.x() - BASE_X) / (float) SPACING));
    }

    /**
     * 本轮的出发站：目的站 {@link #START_DISTANCE} 格外，方向按轮次轮换
     * （0 西 / 1 北 / 2 东 / 3 南）。
     *
     * <p>原作每个航行关卡同时有 {@code Level.StartOutpost} 和 {@code Level.EndOutpost}，
     * 中间由 MainPath 隧道连接；这里的出发站就是关卡里的当前地点，目的站是要去的节点。</p>
     */
    public static DockingBay.Site startSite(Anchor anchor) {
        int dir = Math.floorMod(anchor.slot(), 4);
        int dx = switch (dir) {
            case 0 -> -1;
            case 2 -> 1;
            default -> 0;
        };
        int dz = switch (dir) {
            case 1 -> -1;
            case 3 -> 1;
            default -> 0;
        };
        return new DockingBay.Site(anchor.x() + dx * START_DISTANCE,
                anchor.z() + dz * START_DISTANCE);
    }

    /**
     * 这个 (x,z) 是否落在副本的“出发站—目的站”大海域里。
     *
     * <p>世界生成时这里不摆岩柱/岩墙：两个站各留 64 格净空，中间再沿航线留一条
     * 48 格半径的开阔水道（原作 MainPath 的放大版）。</p>
     */
    public static boolean inRouteZone(int x, int z) {
        int base = Math.max(0, Math.round((x - BASE_X) / (float) SPACING));
        for (int slot = Math.max(0, base - 1); slot <= base + 1; slot++) {
            Anchor anchor = anchorForRound(slot);
            DockingBay.Site start = startSite(anchor);
            if (Math.hypot(x - anchor.x(), z - anchor.z()) <= 64.0D) {
                return true;
            }
            if (Math.hypot(x - start.x(), z - start.z()) <= 64.0D) {
                return true;
            }
            if (distanceToSegment(x, z, anchor.x(), anchor.z(),
                    start.x(), start.z()) <= 48.0D) {
                return true;
            }
        }
        return false;
    }

    private static double distanceToSegment(double px, double pz,
                                            double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSq = dx * dx + dz * dz;
        if (lengthSq <= 1.0E-6D) {
            return Math.hypot(px - ax, pz - az);
        }
        double t = ((px - ax) * dx + (pz - az) * dz) / lengthSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return Math.hypot(px - (ax + dx * t), pz - (az + dz * t));
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
        return preloadNear(level, new BlockPos(anchor.x(), anchor.y(), anchor.z()), chunkRadius);
    }

    /** 把任意坐标周围的区块同步加载出来（出发海域也用这个）。 */
    public static int preloadNear(ServerLevel level, BlockPos center, int chunkRadius) {
        int centerX = center.getX() >> 4;
        int centerZ = center.getZ() >> 4;
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
