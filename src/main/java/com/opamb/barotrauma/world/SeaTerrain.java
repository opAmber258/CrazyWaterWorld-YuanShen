package com.opamb.barotrauma.world;

import com.opamb.barotrauma.world.level.LevelParams;
import com.opamb.barotrauma.world.level.TerrainTemplate;

import java.util.List;

/**
 * 开阔海域的高度场：纯数学，不碰 MC 注册表，单元测试可以直接调。
 *
 * <p>全局地形（没有航行关卡时）是两条 2D 噪声定的顶部岩壳和底部岩床，中间是开阔水域；
 * 有航行关卡时，整片海域会被 {@link RoutePath} 的大洞窟替代：主航道左右蜿蜒、上下起伏，
 * 外面套着横向最大 480 格、纵向最大 440 格的椭圆洞窟，侧洞是能绕行的环路。
 * 潜艇在两站之间看到的是看不到边的洞窟，而不是细管子或长方形大厅。</p>
 *
 * <p>本类只负责返回"这一列从哪里到哪里的水是通的"：{@code ceilingY} 是洞顶（岩壳底面），
 * {@code floorY} 是洞底（岩床顶面）。{@code ceilingY <= floorY} 表示整列都是岩，
 * 生成器会保留原始石头，不雕刻。</p>
 */
public final class SeaTerrain {

    /** 顶部岩壳的压平高度（= 哨站屋顶），以及噪声能让它再往下加厚的最大深度。 */
    public static final int ROOF_FLAT_Y = OutpostBuilder.MAX_Y;
    public static final int ROOF_DEPTH = 120;
    /**
     * 底部岩床的基准高度与噪声抬升量。
     *
     * <p>寒冷深穴先按"洞顶 -44 → 海底 -544"做 500 格高；更深的生物群系以后用各自的
     * 海底基准，不在这版里硬套 1000 格。</p>
     */
    public static final int FLOOR_BASE_Y = -544;
    public static final int FLOOR_RISE = 44;
    /** 哨站压平核心半径、渐变距离、以及不生成岩柱/岩墙的机动净空半径。 */
    public static final int OUTPOST_CORE = 40;
    public static final int OUTPOST_BLEND = 180;
    public static final int OUTPOST_CLEAR = 64;
    /** 理论最大开阔高度：洞顶最浅 -44、洞底最深 -544，正好 500 格。 */
    public static final int MAX_OPEN_HEIGHT = Math.abs(FLOOR_BASE_Y - ROOF_FLAT_Y);

    /** 关卡影响范围边缘的过渡宽度：最外 64 格把隧道岩体渐隐回开阔海。 */
    private static final double LEVEL_EDGE_BLEND = 64.0D;

    private static final long SALT_ROOF = 0x51A7E001L;
    private static final long SALT_FLOOR = 0x9B2C4407L;

    /** POI 附近强制留空的区域：沉船/中继站不能被岩柱埋掉。 */
    public record ClearZone(int x, int z, int y, int radius) {
    }

    private static final List<ClearZone> POI_CLEAR = List.of(
            new ClearZone(PoiNetwork.WRECK_A.getX(), PoiNetwork.WRECK_A.getZ(), PoiNetwork.WRECK_A.getY(), 34),
            new ClearZone(PoiNetwork.WRECK_B.getX(), PoiNetwork.WRECK_B.getZ(), PoiNetwork.WRECK_B.getY(), 34),
            new ClearZone(PoiNetwork.WRECK_C.getX(), PoiNetwork.WRECK_C.getZ(), PoiNetwork.WRECK_C.getY(), 34),
            new ClearZone(PoiNetwork.RECON_POINT.getX(), PoiNetwork.RECON_POINT.getZ(), PoiNetwork.RECON_POINT.getY(), 26),
            new ClearZone(PoiNetwork.POST_B_TERMINAL.getX(), PoiNetwork.POST_B_TERMINAL.getZ(),
                    PoiNetwork.POST_B_TERMINAL.getY(), 26));

    /** 关卡某列的洞窟剖面：洞顶、洞底，以及向全局地形过渡的权重（0=关卡，1=全局）。 */
    private record LevelProfile(double top, double bottom, double blend) {
    }

    private SeaTerrain() {
    }

    /** 顶部岩壳底面：哨站核心压平在 -44，其它地方往噪声深处最多加厚 120 格。 */
    public static int ceilingY(long seed, int x, int z) {
        RouteTerrain terrain = MissionInstance.activeTerrain();
        RouteTerrain.Column column = terrain == null ? null : terrain.column(seed, x, z);
        if (column != null) {
            return column.ceiling();
        }
        int ceiling = baseCeilingY(seed, x, z);
        ceiling = applyLevel(ceiling, seed, x, z, true);
        for (ClearZone zone : POI_CLEAR) {
            double distance = Math.hypot(x - zone.x(), z - zone.z());
            double weight = 1.0D - smooth((distance - zone.radius()) / 30.0D);
            if (weight > 0.0D) {
                int target = Math.max(ceiling, zone.y() + 34);
                ceiling = (int) Math.round(ceiling + (target - ceiling) * weight);
            }
        }
        return ceiling;
    }

    /** 底部岩床顶面：基准 -544，噪声抬升最多 44 格（寒冷深穴 500 格高）。 */
    public static int floorY(long seed, int x, int z) {
        RouteTerrain terrain = MissionInstance.activeTerrain();
        RouteTerrain.Column column = terrain == null ? null : terrain.column(seed, x, z);
        if (column != null) {
            return column.floor();
        }
        int floor = baseFloorY(seed, x, z);
        floor = applyLevel(floor, seed, x, z, false);
        for (ClearZone zone : POI_CLEAR) {
            double distance = Math.hypot(x - zone.x(), z - zone.z());
            double weight = 1.0D - smooth((distance - zone.radius()) / 30.0D);
            if (weight > 0.0D) {
                int target = Math.min(floor, zone.y() - 34);
                floor = (int) Math.round(floor + (target - floor) * weight);
            }
        }
        return floor;
    }

    /** 哨站机动区 / 副本站周围 / POI 周围 / 东侧出口通道不生成岩柱、岩墙、浮岩岛。 */
    public static boolean inClearZone(int x, int z) {
        if (Math.max(Math.abs(x), Math.abs(z)) <= OUTPOST_CLEAR) {
            return true;
        }
        // 副本关卡：主航道（蜿蜒隧道）净空
        if (MissionInstance.inRouteZone(x, z)) {
            return true;
        }
        // 关卡的侧洞环/洞室也不能长岩柱/岩墙
        if (MissionInstance.inSideBranch(x, z)) {
            return true;
        }
        // 哨站东侧留一条宽阔的天然出口：噪声岩墙偶尔会把出生点围成一圈，这里兜底。
        if (x > OUTPOST_CORE && x < 220 && Math.abs(z) <= 30) {
            return true;
        }
        for (ClearZone zone : POI_CLEAR) {
            if (Math.hypot(x - zone.x(), z - zone.z()) <= zone.radius()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 关卡影响半径：主洞 + 侧洞环 + 96 格岩壁，超过这个距离就是全局开阔海。
     *
     * <p>岩壁必须厚过 64 格的边缘混合带，否则洞窟最外缘会被混成开阔海，
     * 玩家能从侧洞直接钻出去。96 = 32 格实心 + 64 格过渡。</p>
     */
    public static double levelInfluence(LevelParams params) {
        return Math.max(96.0D, params.cavernOuterRadius() + 96.0D);
    }

    // ------------------------------------------------------------------
    // 全局地形（没有航行关卡的部分）
    // ------------------------------------------------------------------

    static int baseCeilingY(long seed, int x, int z) {
        if (isOutpostCore(x, z)) {
            return ROOF_FLAT_Y;
        }
        double n = DeepSeaNoise.fbm2(seed ^ SALT_ROOF, x / 150.0D, z / 150.0D, 3);
        int world = ROOF_FLAT_Y - (int) Math.round((n * 0.5D + 0.5D) * ROOF_DEPTH);
        DockingBay.Site instance = siteWithinBlend(x, z);
        return instance != null
                ? blendAt(ROOF_FLAT_Y, world,
                        Math.max(Math.abs(x - instance.x()), Math.abs(z - instance.z())))
                : blendOutpost(ROOF_FLAT_Y, world, x, z);
    }

    static int baseFloorY(long seed, int x, int z) {
        double n = DeepSeaNoise.fbm2(seed ^ SALT_FLOOR, x / 130.0D, z / 130.0D, 3);
        // 用户要求去掉哨站岩台：海底在哨站下方也按世界噪声走，潜艇直接悬在开阔水域。
        return FLOOR_BASE_Y + (int) Math.round((n * 0.5D + 0.5D) * FLOOR_RISE);
    }

    // ------------------------------------------------------------------
    // 关卡主航道
    // ------------------------------------------------------------------

    /**
     * 把全局高度换成关卡隧道剖面。
     *
     * @param ceiling true 取洞顶，false 取洞底
     */
    private static int applyLevel(int world, long seed, int x, int z, boolean ceiling) {
        LevelProfile profile = levelProfile(seed, x, z);
        if (profile == null) {
            return world;
        }
        double target = ceiling ? profile.top() : profile.bottom();
        return (int) Math.round(target + (world - target) * profile.blend());
    }

    private static LevelProfile levelProfile(long seed, int x, int z) {
        // 关卡配置和航道必须来自同一次快照；生成器外层也持同一把读锁（可重入）。
        try (MissionInstance.RouteLock ignored = MissionInstance.holdRouteReadLock()) {
            MissionInstance.RouteLevel level = MissionInstance.routeLevel();
            if (level == null || level.terrain() != TerrainTemplate.LEGACY) {
                return null;
            }
            RoutePath path = MissionInstance.activePath();
            if (path == null) {
                return null;
            }
            LevelParams params = level.type().params();
            // 侧隧道可能离主航道很远：必须按到整个隧道网络的距离判定，
            // 否则侧隧道落在主航道影响半径外就完全不会被挖开。
            double distance = path.distanceToNetwork(x, z);
            double influence = levelInfluence(params);
            if (distance >= influence) {
                return null;
            }
            double blend = smooth((distance - (influence - LEVEL_EDGE_BLEND)) / LEVEL_EDGE_BLEND);
            if (blend >= 1.0D) {
                return null;
            }

            // 洞窟剖面：主航道椭圆 + 侧洞环 + 洞室的并集，夹在关卡开阔带内。
            RoutePath.Open open = path.openAt(x, z, seed);
            double top;
            double bottom;
            if (open == null) {
                // 洞窟之外整列是岩：洞顶压到洞底以上，生成器看到 open<=0 会保留石头。
                top = params.bandCeiling();
                bottom = top + 1.0D;
            } else {
                top = Math.min(open.top(), params.bandCeiling());
                bottom = Math.max(open.bottom(), params.bandFloor());
                if (bottom > top) {
                    top = params.bandCeiling();
                    bottom = top + 1.0D;
                }
            }
            // 站点不再额外挖空腔：主洞在端点处本身就有 240–480 格宽、240–440 格高，
            // 站体（41×25、-44..-64）和码头竖井（-116..-69）都落在洞窟里。
            return new LevelProfile(top, bottom, blend);
        }
    }

    /** 哨站核心区取哨站高度，外圈 {@link #OUTPOST_BLEND} 格平滑过渡到世界高度。 */
    private static boolean isOutpostCore(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) <= OUTPOST_CORE;
    }

    /**
     * 副本关卡里离 (x,z) 最近的站（出发站或目的站），用于把洞顶压平到 -44。
     * 双站关卡两个站都要有平台，否则出发站会挂在自然岩壳下面。
     */
    private static DockingBay.Site siteWithinBlend(int x, int z) {
        int base = Math.max(0, Math.round((x - MissionInstance.BASE_X)
                / (float) MissionInstance.SPACING));
        DockingBay.Site best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int slot = Math.max(0, base - 1); slot <= base + 1; slot++) {
            MissionInstance.Anchor anchor = MissionInstance.anchorForRound(slot);
            for (DockingBay.Site site : new DockingBay.Site[]{
                    new DockingBay.Site(anchor.x(), anchor.z()),
                    MissionInstance.startSite(anchor)}) {
                // 无人航路点虽然不建站，但船舶仍要在 -80 高度出生/抵达：
                // 洞顶必须压平，否则天然岩壳可能低过 -80 把船埋进去。
                double dx = Math.abs(x - site.x());
                double dz = Math.abs(z - site.z());
                if (dx > OUTPOST_BLEND || dz > OUTPOST_BLEND) {
                    continue;
                }
                double distance = Math.max(dx, dz);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = site;
                }
            }
        }
        return best;
    }

    /** 按到站点中心的最大轴距做平滑过渡。 */
    private static int blendAt(int postValue, int worldValue, double distance) {
        double t = smooth((distance - OUTPOST_CORE) / (double) (OUTPOST_BLEND - OUTPOST_CORE));
        return (int) Math.round(postValue + (worldValue - postValue) * t);
    }

    private static int blendOutpost(int postValue, int worldValue, int x, int z) {
        return blendAt(postValue, worldValue, Math.max(Math.abs(x), Math.abs(z)));
    }

    static double smooth(double t) {
        if (t <= 0.0D) {
            return 0.0D;
        }
        if (t >= 1.0D) {
            return 1.0D;
        }
        return t * t * (3.0D - 2.0D * t);
    }
}
