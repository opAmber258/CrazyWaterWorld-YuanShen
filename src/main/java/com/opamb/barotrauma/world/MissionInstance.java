package com.opamb.barotrauma.world;

import com.opamb.barotrauma.world.level.LevelParams;
import com.opamb.barotrauma.world.level.LevelType;
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
    /**
     * 每轮占用的 x 间距。大洞窟横向半宽最大 300、侧隧道最远约 560、航道左右摆动 ±250，
     * 1024 已经放不下，改成 2048 给"宽敞 + 大幅摆动"留空间；旧槽位仍在原位，
     * 新一趟从新的锚点开始。
     */
    public static final int SPACING = 2048;
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
    private static volatile int routeSlot = Integer.MIN_VALUE;
    private static volatile boolean routeStartOutpost = true;
    private static volatile boolean routeEndOutpost = true;

    /**
     * 本轮航行关卡的静态配置。世界生成在 worker 线程跑，只能读这个不可变 record；
     * 由 {@code CampaignManager.startTravel} 在预加载新区块之前写入，
     * {@code tick} 和服务器启动时从 {@code RoundState} 重放。
     *
     * <p>{@code anchorX} 是保存下来的实际锚点 x：读档时旧存档的槽位间距可能和
     * 当前 {@link #SPACING} 不同，不能再用 slot 乘间距重新算锚点。</p>
     *
     * <p>{@code null} = 没有活动关卡，地形完全走原来的全局 {@link SeaTerrain}。</p>
     */
    public record RouteLevel(int slot, LevelType type, long seed, int difficulty,
                             int startDistance, int anchorX) {
    }

    private static volatile RouteLevel routeLevel;

    /**
     * 关卡切换与区块生成的读写锁：区块生成任务持读锁，{@link #setRouteLevel} 持写锁。
     * 否则生成线程可能在一个区块内先读到旧关卡、再读到新航道。
     */
    private static final java.util.concurrent.locks.ReentrantReadWriteLock ROUTE_LOCK =
            new java.util.concurrent.locks.ReentrantReadWriteLock();

    /** 生成任务持有的关卡读锁；关闭即释放。 */
    public static final class RouteLock implements AutoCloseable {
        private final java.util.concurrent.locks.Lock lock;

        private RouteLock(java.util.concurrent.locks.Lock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            lock.unlock();
        }
    }

    /** 区块生成持读锁：同一区块内不会读到切换前后的两套关卡配置。 */
    public static RouteLock holdRouteReadLock() {
        java.util.concurrent.locks.Lock lock = ROUTE_LOCK.readLock();
        lock.lock();
        return new RouteLock(lock);
    }

    /** 主航道缓存：关卡配置不变就复用同一条折线，生成器每列都会查它。 */
    private record PathCache(RouteLevel level, RoutePath path) {
    }

    private static volatile PathCache pathCache;

    /** 设置本轮关卡；{@code null} 清除（回到全局地形）。 */
    public static void setRouteLevel(RouteLevel level) {
        java.util.concurrent.locks.Lock lock = ROUTE_LOCK.writeLock();
        lock.lock();
        try {
            routeLevel = level;
        } finally {
            lock.unlock();
        }
    }

    /** 当前活动关卡；没有返回 null。 */
    public static RouteLevel routeLevel() {
        return routeLevel;
    }

    /** 当前活动关卡参数；没有返回 null。 */
    public static LevelParams activeParams() {
        RouteLevel level = routeLevel;
        return level == null ? null : level.type().params();
    }

    /** 某个槽位的关卡参数；不是活动槽位返回 null。 */
    public static LevelParams paramsForSlot(int slot) {
        RouteLevel level = routeLevel;
        return level != null && level.slot() == slot ? level.type().params() : null;
    }

    /**
     * 当前活动关卡的主航道（左右蜿蜒、上下起伏的 3D 折线）。
     *
     * <p>生成器在 worker 线程逐列调用，必须缓存：关卡配置（record）不变就复用同一条
     * 折线。缓存用不可变 record 整体发布，避免读到"新关卡 + 旧航道"的半成品。</p>
     */
    public static RoutePath activePath() {
        RouteLevel level = routeLevel;
        if (level == null) {
            return null;
        }
        PathCache cache = pathCache;
        if (cache != null && cache.level().equals(level)) {
            return cache.path();
        }
        Anchor anchor = anchorForRound(level.slot());
        DockingBay.Site start = startSite(anchor, level.startDistance());
        RoutePath built = RoutePath.build(level.type().params(), level.seed(),
                start.x(), start.z(), anchor.x(), anchor.z());
        pathCache = new PathCache(level, built);
        return built;
    }

    /** 本轮出发站到目的站的距离：活动槽位用关卡类型的路线长度，其它槽位用默认 192。 */
    public static int startDistanceFor(Anchor anchor) {
        RouteLevel level = routeLevel;
        return level != null && level.slot() == anchor.slot()
                ? level.startDistance() : START_DISTANCE;
    }

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
        RouteLevel level = routeLevel;
        if (level != null) {
            // 旧存档的槽位间距可能不是当前 SPACING，活动关卡的站只能按保存的锚点认。
            Anchor anchor = anchorForRound(level.slot());
            if (site.equals(DockingBay.Site.forSlot(level.slot()))
                    || site.equals(startSite(anchor))) {
                return level.slot();
            }
        }
        return Math.max(0, Math.round((site.x() - BASE_X) / (float) SPACING));
    }

    /**
     * 本轮的出发站：目的站 {@link #startDistanceFor(Anchor)} 格外，方向按轮次轮换
     * （0 西 / 1 北 / 2 东 / 3 南）。
     *
     * <p>原作每个航行关卡同时有 {@code Level.StartOutpost} 和 {@code Level.EndOutpost}，
     * 中间由 MainPath 隧道连接；这里的出发站就是关卡里的当前地点，目的站是要去的节点。
     * 出发距离由关卡类型决定（tutorial 207、basic 280、advanced 600、maze 520、outpost 80）。
     * 超过 {@link LevelParams#LONG_ROUTE_THRESHOLD} 的路线强制走南北，x 与目的站相同，
     * 这样 600/520 也能留在 1024 的 x 槽位里，不用改 {@link #SPACING}。</p>
     */
    public static DockingBay.Site startSite(Anchor anchor) {
        return startSite(anchor, startDistanceFor(anchor));
    }

    /** 按指定距离算出发站（航道缓存用它，避免重复读可能已变化的静态关卡）。 */
    public static DockingBay.Site startSite(Anchor anchor, int distance) {
        int dir = Math.floorMod(anchor.slot(), 4);
        if (distance > LevelParams.LONG_ROUTE_THRESHOLD) {
            // 南北方向：x 不变，z 拉长；槽位只在 x 方向分带。
            dir = Math.floorMod(anchor.slot(), 2) == 0 ? 1 : 3;
        }
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
        return new DockingBay.Site(anchor.x() + dx * distance,
                anchor.z() + dz * distance);
    }

    /**
     * 这个 (x,z) 是否落在副本的“出发站—目的站”大海域里。
     *
     * <p>世界生成时这里不摆岩柱/岩墙：两个站各留 64 格净空，中间再沿航线留一条
     * 48 格半径的开阔水道（原作 MainPath 的放大版）。</p>
     */
    public static boolean inRouteZone(int x, int z) {
        int base = Math.max(0, Math.round((x - BASE_X) / (float) SPACING));
        int min = Math.max(0, base - 1);
        int max = base + 1;
        RouteLevel active = routeLevel;
        if (active != null) {
            // 旧存档的锚点不按当前 SPACING 排列：活动槽位始终参与判定，
            // 否则 x 落在两个新槽位之间时整条活动航道都不会清空。
            min = Math.min(min, Math.max(0, active.slot() - 1));
            max = Math.max(max, active.slot() + 1);
        }
        for (int slot = min; slot <= max; slot++) {
            Anchor anchor = anchorForRound(slot);
            DockingBay.Site start = startSite(anchor);
            if (Math.hypot(x - anchor.x(), z - anchor.z()) <= 64.0D) {
                return true;
            }
            if (Math.hypot(x - start.x(), z - start.z()) <= 64.0D) {
                return true;
            }
            RouteLevel level = routeLevel;
            if (level != null && level.slot() == slot) {
                // 活动关卡：主航道 + 侧隧道是网络，净空按到整个网络的距离算。
                RoutePath path = activePath();
                if (path != null && path.distanceToNetwork(x, z)
                        <= Math.max(48.0D, routeClearRadius(slot))) {
                    return true;
                }
            } else if (distanceToSegment(x, z, anchor.x(), anchor.z(),
                    start.x(), start.z()) <= routeClearRadius(slot)) {
                // 旧槽位没有活动关卡，保持原来的直线水道。
                return true;
            }
        }
        return false;
    }

    /** 主航道净空半径：活动槽位用大洞窟横向半宽，其它槽位用原来的 48。 */
    public static double routeClearRadius(int slot) {
        LevelParams params = paramsForSlot(slot);
        return params == null ? 48.0D : Math.max(48.0D, params.cavernHalfWidth());
    }

    /** 到活动关卡隧道网络（主航道 + 侧隧道）的最近距离；没有活动关卡返回 MAX_VALUE。 */
    public static double distanceToActiveRoute(int x, int z) {
        RoutePath path = activePath();
        return path == null ? Double.MAX_VALUE : path.distanceToNetwork(x, z);
    }

    /**
     * 活动关卡的侧隧道/洞穴是否覆盖 (x,z)：侧洞挂在蜿蜒主航道上，
     * 位置由 {@link RoutePath} 按关卡种子确定性生成。
     */
    public static boolean inSideBranch(int x, int z) {
        RoutePath path = activePath();
        return path != null && path.inChamber(x, z);
    }

    /**
     * 采矿任务的矿脉生成区：副本航线附近（含海底），但避开两个码头正下方
     * 24 格，免得矿脉长在站体/竖井里。
     *
     * <p>只在有实例轮次的 x 范围内生效，主站附近不会生成矿脉。</p>
     */
    public static boolean inMiningZone(int x, int z) {
        int base = Math.max(0, Math.round((x - BASE_X) / (float) SPACING));
        int min = Math.max(0, base - 1);
        int max = base + 1;
        RouteLevel active = routeLevel;
        if (active != null) {
            // 和 inRouteZone 一样：旧存档的活动槽位坐标不按当前 SPACING 排列。
            min = Math.min(min, Math.max(0, active.slot() - 1));
            max = Math.max(max, active.slot() + 1);
        }
        for (int slot = min; slot <= max; slot++) {
            Anchor anchor = anchorForRound(slot);
            DockingBay.Site start = startSite(anchor);
            if (Math.hypot(x - anchor.x(), z - anchor.z()) <= 24.0D
                    || Math.hypot(x - start.x(), z - start.z()) <= 24.0D) {
                continue;
            }
            if (Math.hypot(x - anchor.x(), z - anchor.z()) <= 150.0D
                    || Math.hypot(x - start.x(), z - start.z()) <= 150.0D) {
                return true;
            }
            RouteLevel level = routeLevel;
            if (level != null && level.slot() == slot) {
                RoutePath path = activePath();
                double radius = level.type().params().cavernHalfWidth() + 80.0D;
                if (path != null && path.distanceToNetwork(x, z) <= Math.max(140.0D, radius)) {
                    return true;
                }
            } else if (distanceToSegment(x, z, anchor.x(), anchor.z(),
                    start.x(), start.z()) <= 120.0D) {
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
        RouteLevel level = routeLevel;
        // 读档恢复的关卡用保存下来的锚点：旧存档的槽位间距可能是 1024。
        int x = level != null && level.slot() == slot ? level.anchorX() : BASE_X + slot * SPACING;
        return new Anchor(slot, x, ANCHOR_Y, 0);
    }

    /** 从实例坐标反推槽位；{@code spacing <= 0} 时用当前布局。 */
    public static int slotForX(int x, int spacing) {
        int step = spacing > 0 ? spacing : SPACING;
        return Math.max(0, Math.round((x - BASE_X) / (float) step));
    }

    /** 坐标附近的实例站点锚点 x（世界生成用；不在任何站净空范围内返回 null）。 */
    public static Integer siteXAt(int x, int z) {
        if (Math.abs(z) > SITE_RADIUS) {
            return null;
        }
        RouteLevel level = routeLevel;
        if (level != null) {
            // 活动关卡按保存的 anchorX 认站：旧存档槽位 3 用当前间距会算出别的 x。
            Anchor anchor = anchorForRound(level.slot());
            if (Math.abs(x - anchor.x()) <= SITE_RADIUS
                    && Math.abs(z - anchor.z()) <= SITE_RADIUS) {
                return anchor.x();
            }
            DockingBay.Site start = startSite(anchor);
            if (Math.abs(x - start.x()) <= SITE_RADIUS
                    && Math.abs(z - start.z()) <= SITE_RADIUS) {
                return start.x();
            }
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
