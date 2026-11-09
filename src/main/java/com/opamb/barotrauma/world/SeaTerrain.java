package com.opamb.barotrauma.world;

import java.util.List;

/**
 * 开阔海域的高度场：纯数学，不碰 MC 注册表，单元测试可以直接调。
 *
 * <p>形状对照原作 Cold Caverns：关卡高度远大于潜艇高度、上下都是岩壁，中间是开阔水域。
 * 这里用两条 2D 噪声分别定"顶部岩壳底面"和"底部岩床顶面"，两者之间就是可航行空间；
 * 最高接近 1000 格，没有分层岩板，也没有竖井。</p>
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

    private SeaTerrain() {
    }

    /** 顶部岩壳底面：哨站核心压平在 -44，其它地方往噪声深处最多加厚 196 格。 */
    public static int ceilingY(long seed, int x, int z) {
        if (isOutpostCore(x, z)) {
            return ROOF_FLAT_Y;
        }
        Integer instanceX = siteXWithinBlend(x, z);
        double n = DeepSeaNoise.fbm2(seed ^ SALT_ROOF, x / 150.0D, z / 150.0D, 3);
        int world = ROOF_FLAT_Y - (int) Math.round((n * 0.5D + 0.5D) * ROOF_DEPTH);
        int ceiling = instanceX != null
                ? blendAt(ROOF_FLAT_Y, world, Math.max(Math.abs(x - instanceX), Math.abs(z)))
                : blendOutpost(ROOF_FLAT_Y, world, x, z);
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
        double n = DeepSeaNoise.fbm2(seed ^ SALT_FLOOR, x / 130.0D, z / 130.0D, 3);
        // 用户要求去掉哨站岩台：海底在哨站下方也按世界噪声走，潜艇直接悬在开阔水域。
        int floor = FLOOR_BASE_Y + (int) Math.round((n * 0.5D + 0.5D) * FLOOR_RISE);
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
        // 副本站（任务海域实例）也要留出同样的机动净空，否则岩柱会把船围住。
        Integer instanceX = MissionInstance.siteXAt(x, z);
        if (instanceX != null) {
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

    /** 哨站核心区取哨站高度，外圈 {@link #OUTPOST_BLEND} 格平滑过渡到世界高度。 */
    private static boolean isOutpostCore(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) <= OUTPOST_CORE;
    }

    /** 副本站处理半径（核心 + 渐变），和主站的 {@link #OUTPOST_BLEND} 一致。 */
    private static Integer siteXWithinBlend(int x, int z) {
        if (Math.abs(z) > OUTPOST_BLEND) {
            return null;
        }
        int slot = Math.round((x - MissionInstance.BASE_X) / (float) MissionInstance.SPACING);
        if (slot < 0) {
            return null;
        }
        int ax = MissionInstance.BASE_X + slot * MissionInstance.SPACING;
        return Math.abs(x - ax) <= OUTPOST_BLEND ? ax : null;
    }

    /** 按到站点中心的最大轴距做平滑过渡。 */
    private static int blendAt(int postValue, int worldValue, double distance) {
        double t = smooth((distance - OUTPOST_CORE) / (double) (OUTPOST_BLEND - OUTPOST_CORE));
        return (int) Math.round(postValue + (worldValue - postValue) * t);
    }

    private static int blendOutpost(int postValue, int worldValue, int x, int z) {
        return blendAt(postValue, worldValue, Math.max(Math.abs(x), Math.abs(z)));
    }

    private static double smooth(double t) {
        if (t <= 0.0D) {
            return 0.0D;
        }
        if (t >= 1.0D) {
            return 1.0D;
        }
        return t * t * (3.0D - 2.0D * t);
    }
}
