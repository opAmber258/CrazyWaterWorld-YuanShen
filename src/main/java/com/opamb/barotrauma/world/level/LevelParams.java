package com.opamb.barotrauma.world.level;

/**
 * 一种关卡类型的地形/内容参数。纯数据，不碰 Minecraft 注册表，单元测试可直接构造/比较。
 *
 * <p>数值来源：本机 Barotrauma 1.13.5
 * {@code Content/Map/Biomes/ColdCaverns/ColdCaverns.xml}。原作单位≈厘米，
 * 本模组按 <b>0.5 比例</b>换算：{@code blocks(units) = round(units / 200)}，
 * 即 70000 → 350 格、60000 → 300 格、7000 → 35 格。</p>
 *
 * <p>当前深海开阔水域是 -44..-544（500 格高），所以：
 * 150（tutorial）/300（basic）/350（advanced）/500（maze）/200（outpost）
 * 都能放进现有维度，不需要改维度高度。</p>
 */
public record LevelParams(
        LevelType type,
        int commonness,
        int minDifficulty,
        int maxDifficulty,
        Dimensions dims,
        TerrainShape shape,
        Content content,
        Route route) {

    /** 原作厘米 → 本模组格：0.5 比例（1 格 = 1 米 = 100 厘米，再乘 0.5）。 */
    public static final double SCALE = 0.5D;
    public static final double UNITS_PER_BLOCK = 100.0D;
    /**
     * 玩法放大：原作 0.5 比例适合做洞窟宽度，但用户实测后要求"洞像大海域、站点
     * 之间开得更远"。高度在原作 0.5 比例上再 ×2（basic 300→600 格），路线长度
     * ×2.5（basic 280→700、advanced 600→1500）。
     */
    public static final double HEIGHT_SCALE = 2.0D;
    public static final double ROUTE_SCALE = 2.5D;
    /** 新地形的原作尺度倍率，旧关卡保留原来的距离和高度。 */
    public static final double TERRAIN_SCALE = 1.3D;
    /** 开阔带顶（= 哨站屋顶）；所有关卡共用。 */
    public static final int BAND_CEILING_Y = -44;
    /** 海底最低不能低于这个值，否则掉出 OceanEnvironment 的水柱。 */
    public static final int MIN_FLOOR_Y = -1000;
    /**
     * 路线长度上限：advanced 600×2.5=1500，留一点余量到 1600。
     * 超过 {@link #LONG_ROUTE_THRESHOLD} 的关卡走南北方向：槽位只在 x 方向分带，
     * z 方向不限，所以 1500 也能留在同一个 x 槽位里。
     */
    public static final int MAX_ROUTE = 1600;
    /** 东西向出发站的保守上限（按旧的 1024 半槽位算：512 - 站点半径 64 - 16 余量）。 */
    public static final int LONG_ROUTE_THRESHOLD = 432;
    /** 哨站关卡影响半径（原作宽 40000 → 200 格）。 */
    public static final int OUTPOST_INFLUENCE = 200;

    public record Dimensions(int widthMin, int widthMax, int height, int tunnelRadius) {
    }

    public record TerrainShape(
            int sideTunnelMin, int sideTunnelMax,
            int sideTunnelRadiusMin, int sideTunnelRadiusMax,
            int caveCount,
            int voronoiInterval, int voronoiVariance,
            double cellSubdivision, double cellRounding, double cellIrregularity,
            int mainPathIntervalMin, int mainPathIntervalMax, double mainPathVariance) {
    }

    public record Content(
            int itemCount, int levelObjectAmount, int backgroundCreatureAmount,
            int resourceIntervalMin, int resourceIntervalMax,
            int caveResourceIntervalMin, int caveResourceIntervalMax,
            int resourceClusterMin, int resourceClusterMax,
            double resourceSpawnChance, double caveResourceSpawnChance,
            int wreckMin, int wreckMax, int ruinCount, double thalamusProbability) {
    }

    public record Route(double startX, double startY, double endX, double endY,
                        boolean createHoleNextToEnd) {
    }

    /** 原作厘米 → 格（0.5 比例）。 */
    public static int blocks(double units) {
        return (int) Math.round(units * SCALE / UNITS_PER_BLOCK);
    }

    /** 关卡高度：原作 0.5 比例后再 ×{@link #HEIGHT_SCALE}（即 1:1 厘米→格）。 */
    private static int heightBlocks(double units) {
        return blocks(units * HEIGHT_SCALE);
    }

    /** 开阔带底面（海底）：-44 - 关卡高度，最低 -1000。 */
    public int bandFloor() {
        return Math.max(MIN_FLOOR_Y, BAND_CEILING_Y - dims.height());
    }

    public int bandCeiling() {
        return BAND_CEILING_Y;
    }

    public int averageWidth() {
        return (dims.widthMin() + dims.widthMax()) / 2;
    }

    /** 出发站到目的站的距离：原作 StartPosition→EndPosition 的宽度比例 × 玩法放大，封顶 1600。 */
    public int routeLength() {
        // 先按原作比例取整，再乘玩法放大：避免 0.84-0.15 的浮点误差把 517.5 舍成 517。
        int base = (int) Math.round((route.endX() - route.startX()) * averageWidth());
        int length = (int) Math.round(base * ROUTE_SCALE);
        return Math.max(1, Math.min(MAX_ROUTE, length));
    }

    public int terrainWidth() {
        return (int) Math.round(averageWidth() * TERRAIN_SCALE / SCALE);
    }

    /** 原作二维截面之外的 3D 展宽，给侧洞和绕礁留航行空间。 */
    public int terrainCrossWidth() {
        return (int) Math.round(terrainWidth() * 0.8D);
    }

    public int terrainHeight() {
        return Math.min(BAND_CEILING_Y - MIN_FLOOR_Y, (int) Math.round(dims.height() * TERRAIN_SCALE));
    }

    public int terrainTunnelRadius() {
        return (int) Math.round(dims.tunnelRadius() * TERRAIN_SCALE / SCALE);
    }

    /** 同原作在内缩后的区间插值两个端点。 */
    public int terrainRouteLength() {
        int width = terrainWidth();
        int inset = Math.min(terrainTunnelRadius(), width / 5);
        return Math.max(1, (int) Math.round((width - 2 * inset) * (route.endX() - route.startX())));
    }

    /**
     * 大洞窟的横向半宽：关卡宽度的 55%，最大 300 格。
     * tutorial 165（洞宽 330）、basic 220（洞宽 440）、advanced/maze 300（洞宽 600）。
     */
    public int cavernHalfWidth() {
        return Math.max(80, Math.min(300, (int) Math.round(averageWidth() * 0.55D)));
    }

    /**
     * 大洞窟的纵向半高：关卡高度的 55%，最大 280 格。
     * tutorial 165（洞高 330）、basic/advanced/maze 280（洞高 560）。
     */
    public int cavernHalfHeight() {
        return Math.max(80, Math.min(280, (int) Math.round(dims.height() * 0.55D)));
    }

    /** 侧隧道横向半宽：主洞的 40%，最大 110 格（侧洞本身也要宽到能开潜艇）。 */
    public int branchHalfWidth() {
        return Math.max(30, Math.min(110, (int) Math.round(cavernHalfWidth() * 0.40D)));
    }

    /** 侧隧道纵向半高：主洞的 70%，最大 190 格。 */
    public int branchHalfHeight() {
        return Math.max(40, Math.min(190, (int) Math.round(cavernHalfHeight() * 0.70D)));
    }

    /**
     * 关卡岩体最外缘：主洞 + 侧洞环 + 中间 16 格岩壁。地形影响半径用它再加 32。
     * 侧洞房间比通道再鼓 1.2 倍，这里按最大鼓包算；加上航道横向摆动后必须
     * 小于 {@link com.opamb.barotrauma.world.MissionInstance#SPACING} 的一半（1024），
     * 否则相邻两个槽位的侧洞会连起来。
     */
    public int cavernOuterRadius() {
        int hw = cavernHalfWidth();
        if (shape.sideTunnelMax() <= 0) {
            return hw + 16;
        }
        return hw + (int) Math.round(branchHalfWidth() * 2.3D) + 16;
    }

    public boolean isConnection() {
        return type.isConnection();
    }

    /** 海底矿脉密度模数（basic 0.2 → 1/1024）。 */
    public int seafloorVeinModulus() {
        return veinModulus(content.resourceSpawnChance(), 0.2D, 1024);
    }

    /** 岩壁矿脉密度模数（basic 0.35 → 1/256）。 */
    public int wallVeinModulus() {
        return veinModulus(content.caveResourceSpawnChance(), 0.35D, 256);
    }

    /**
     * 环境矿脉的“每隔多少个区块允许一簇”。
     *
     * <p>原作资源点间隔 19200–38400 单位（0.5 比例 96–192 格），再乘
     * {@code ResourceSpawnChance} 0.2–0.3；一个区块 16 格，约 6–12 个区块一个
     * 候选点。这里取 basic 0.2 → 16、0.3 → 11，配合“每区块最多一簇”把
     * 声纳上一扫全是矿的情况压回原作密度。</p>
     */
    public int ambientChunkGate() {
        double chance = Math.max(0.05D, content.resourceSpawnChance());
        return Math.max(8, Math.min(32, (int) Math.round(16.0D * 0.2D / chance)));
    }

    /**
     * 按“每 N 列/采样命中一次”的模数算密度。
     *
     * <p>生成器用 {@code Math.floorMod(hash, modulus) == 0} 判定：概率恰好是
     * 1/modulus。之前返回的是位掩码，但掩码可能不连续（如 682=1010101010），
     * 实际通过率变成 1/2^popcount，和设计密度差几十倍。</p>
     */
    private static int veinModulus(double chance, double baselineChance, int baselineModulus) {
        if (chance <= 0.0D) {
            return baselineModulus * 4;
        }
        long scaled = Math.round(baselineModulus * baselineChance / chance);
        return (int) Math.max(128L, Math.min(baselineModulus * 4L, scaled));
    }

    private static final LevelParams[] CACHE = new LevelParams[LevelType.values().length];

    public static LevelParams forType(LevelType type) {
        int index = type.ordinal();
        LevelParams cached = CACHE[index];
        if (cached == null) {
            cached = switch (type) {
                case TUTORIAL -> tutorial();
                case BASIC -> basic();
                case ADVANCED -> advanced();
                case MAZE -> maze();
                case OUTPOST -> outpost();
            };
            CACHE[index] = cached;
        }
        return cached;
    }

    /** tutorial：宽 600、高 300、隧道 6500、无洞穴、资源 0.3/0.6。 */
    private static LevelParams tutorial() {
        return new LevelParams(
                LevelType.TUTORIAL, 0, 0, 15,
                new Dimensions(blocks(60000), blocks(60000), heightBlocks(30000), blocks(6500)),
                new TerrainShape(
                        0, 1, blocks(2000), blocks(6000), 0,
                        blocks(2000), blocks(1200),
                        500 / 200.0D, 0.2D, 0.5D,
                        blocks(10000), blocks(22500), 0.5D),
                new Content(
                        200, 500, 30,
                        blocks(19200), blocks(38400), blocks(9600), blocks(19200),
                        3, 4, 0.3D, 0.6D,
                        0, 0, 0, 0.0D),
                new Route(0.15D, 0.1D, 0.84D, 0.1D, false));
    }

    /** basic：宽 700–900、高 600、隧道 7000、1 洞穴、资源 0.2/0.35。 */
    private static LevelParams basic() {
        return new LevelParams(
                LevelType.BASIC, 100, 0, 5,
                new Dimensions(blocks(70000), blocks(90000), heightBlocks(60000), blocks(7000)),
                new TerrainShape(
                        0, 1, blocks(2000), blocks(6000), 1,
                        blocks(2000), blocks(1200),
                        500 / 200.0D, 0.2D, 0.5D,
                        blocks(10000), blocks(30000), 0.5D),
                new Content(
                        300, 2000, 80,
                        blocks(19200), blocks(38400), blocks(9600), blocks(19200),
                        3, 4, 0.2D, 0.35D,
                        0, 0, 0, 0.0D),
                new Route(0.15D, 0.1D, 0.85D, 0.1D, true));
    }

    /** advanced：宽 1400–1600、高 700、隧道 6500、2 洞穴、资源 0.3/0.5。 */
    private static LevelParams advanced() {
        return new LevelParams(
                LevelType.ADVANCED, 100, 5, 15,
                new Dimensions(blocks(140000), blocks(160000), heightBlocks(70000), blocks(6500)),
                new TerrainShape(
                        0, 1, blocks(2000), blocks(6000), 2,
                        blocks(2000), blocks(1200),
                        500 / 200.0D, 0.2D, 0.5D,
                        blocks(10000), blocks(30000), 0.5D),
                new Content(
                        400, 2000, 80,
                        blocks(19200), blocks(38400), blocks(9600), blocks(19200),
                        3, 4, 0.3D, 0.5D,
                        0, 0, 0, 0.0D),
                new Route(0.1D, 0.1D, 0.9D, 0.5D, true));
    }

    /** maze：宽 1200–1400、高 1000、3 侧隧道、3 洞穴、Voronoi 1500/700。 */
    private static LevelParams maze() {
        return new LevelParams(
                LevelType.MAZE, 25, 8, 15,
                new Dimensions(blocks(120000), blocks(140000), heightBlocks(100000), blocks(6500)),
                new TerrainShape(
                        3, 3, blocks(1000), blocks(3000), 3,
                        blocks(1500), blocks(700),
                        500 / 200.0D, 0.2D, 0.5D,
                        blocks(8000), blocks(10000), 0.5D),
                new Content(
                        400, 2000, 80,
                        blocks(19200), blocks(38400), blocks(9600), blocks(19200),
                        3, 4, 0.3D, 0.5D,
                        0, 0, 0, 0.0D),
                new Route(0.1D, 0.1D, 0.9D, 0.5D, true));
    }

    /** outpost：宽高 400、主隧道 25000（大空腔）、无洞穴/资源物品、更少岩柱。 */
    private static LevelParams outpost() {
        return new LevelParams(
                LevelType.OUTPOST, 1, 0, 15,
                new Dimensions(blocks(40000), blocks(40000), heightBlocks(40000), blocks(25000)),
                new TerrainShape(
                        0, 0, blocks(25000), blocks(25000), 0,
                        blocks(2000), blocks(1200),
                        1000 / 200.0D, 0.2D, 0.1D,
                        blocks(500), blocks(500), 0.0D),
                new Content(
                        0, 2000, 80,
                        blocks(19200), blocks(38400), blocks(9600), blocks(19200),
                        3, 6, 0.3D, 0.6D,
                        0, 0, 0, 0.0D),
                new Route(0.3D, 0.1D, 0.7D, 0.3D, false));
    }
}
