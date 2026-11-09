package com.opamb.barotrauma.world;

import com.opamb.barotrauma.block.BulkheadDoorBlock;
import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 哨站与潜艇之间的对接结构（2026-11-07 按实机存档重做）。
 *
 * <p>竖剖面（中心 x=0、z=0）：</p>
 * <pre>
 *   y=-62  哨站内部地面
 *   y=-63  站内地板表层：3×1 洞口 + 5×3 半砖围沿
 *   y=-64  哨站外壳底层 + 站侧水平舱口（3×1，对接时打开）
 *   -65..-67  气闸室内部 5×3×3，两侧 x=±3 各一扇 2 格高舱壁门
 *   y=-68  气闸室地板（5×3 平台）+ 中层舱口
 *   y=-69  潜艇顶部舱口（紧贴中层平台下方，两层舱口上下叠着）
 *   -70..-72  潜艇内部
 *   y=-73  潜艇底部
 * </pre>
 *
 * <p>铁梯立在 x=0、z=0 的一列：潜艇内部、竖井、气闸室、站内地板洞口各有一段；
 * 三道水平舱口占掉的那一格本身在打开时算攀爬面（{@link HatchBlock#isLadder}），
 * 所以梯子和舱口不需要互相伸进对方方块——这正好是原版"梯子伸出舱门半格"的
 * 方块化等价物。</p>
 *
 * <p>气闸室的四角和地板边条都必须封死：只留两道舱口和两扇侧门，否则开舱口
 * 时海水会从缝隙灌进竖井，再从艇顶舱口进船（用户实测的"对接时船里进水"）。</p>
 */
public final class DockingBay {

    /** 通道中心（= 哨站中心 x/z）。 */
    public static final int X = 0;
    public static final int Z = 0;
    /** 竖井内部 x 方向 3 格：x -1..1。 */
    public static final int HALF_WIDTH = 1;
    /** 气闸室内部：5 长（x -2..2）× 3 深（z -1..1）。 */
    public static final int ROOM_HALF_X = 2;
    public static final int ROOM_HALF_Z = 1;

    /** 哨站外壳底层（也是站侧舱口所在层）。 */
    public static final int STATION_FLOOR_Y = OutpostBuilder.MIN_Y;
    /** 站内地板表层：开洞 + 半砖围沿。 */
    public static final int STATION_FLOOR_UPPER_Y = STATION_FLOOR_Y + 1;
    /** 站侧水平舱口 y。 */
    public static final int STATION_HATCH_Y = STATION_FLOOR_Y;
    /** 气闸室中层水平舱口 y。 */
    public static final int MID_HATCH_Y = -68;
    /** 潜艇顶部水平舱口 y（船顶，紧贴中层平台下方）。 */
    public static final int SUB_HATCH_Y = -69;
    /** 旧存档潜艇仍停在 -78，顶部舱口在 -74；开合舱口时兼容一下。 */
    public static final int LEGACY_SUB_HATCH_Y = -74;

    /** 气闸室内部 y 范围（3 高）。 */
    public static final int ROOM_MIN_Y = -67;
    public static final int ROOM_MAX_Y = -65;
    /** 下层竖井内部 y 范围（3 高）。 */
    public static final int SHAFT_TOP_Y = -69;
    public static final int SHAFT_BOTTOM_Y = -71;
    /** 两侧舱壁门的下半格 y（占 -67..-66 两格）。 */
    public static final int SIDE_DOOR_Y = -67;

    /**
     * 一座哨站的原点（站中心 x/z）。主站是 (0,0)；每个任务副本在自己的锚点
     * 再生成一座一模一样的站，所以对接相关的判定都必须先找到站点再算相对坐标。
     */
    public record Site(int x, int z) {
        public static final Site MAIN = new Site(0, 0);

        /** 第 {@code slot} 个副本站的站点（和 {@link MissionInstance#anchorForRound} 同一套坐标）。 */
        public static Site forSlot(int slot) {
            MissionInstance.Anchor anchor = MissionInstance.anchorForRound(slot);
            return new Site(anchor.x(), anchor.z());
        }

        public BlockPos at(int localX, int y, int localZ) {
            return new BlockPos(x + localX, y, z + localZ);
        }
    }

    /** 站点/水腔的横向判定半径（比建筑外墙大，覆盖站下水腔和停靠的潜艇）。 */
    private static final int SITE_RADIUS = 40;

    /** 世界生成用：这个区块可能碰到的站点（主站 + 本轮副本的出发站/目的站）。 */
    public static java.util.List<Site> sitesNear(ChunkPos pos) {
        java.util.List<Site> sites = new java.util.ArrayList<>(3);
        sites.add(Site.MAIN);
        int centerX = pos.getMiddleBlockX();
        int centerZ = pos.getMiddleBlockZ();
        int base = Math.max(0, Math.round((centerX - MissionInstance.BASE_X)
                / (float) MissionInstance.SPACING));
        for (int slot = Math.max(0, base - 1); slot <= base + 1; slot++) {
            MissionInstance.Anchor anchor = MissionInstance.anchorForRound(slot);
            Site destination = new Site(anchor.x(), anchor.z());
            if (Math.abs(centerX - destination.x()) <= SITE_RADIUS
                    && Math.abs(centerZ - destination.z()) <= SITE_RADIUS) {
                sites.add(destination);
            }
            Site start = MissionInstance.startSite(anchor);
            if (Math.abs(centerX - start.x()) <= SITE_RADIUS
                    && Math.abs(centerZ - start.z()) <= SITE_RADIUS) {
                sites.add(start);
            }
        }
        return sites;
    }

    /**
     * 找出包含 (x,z) 的哨站；不在任何站范围就返回 null。
     * 副本关卡里同时有出发站和目的站，两个都要能认出来。
     */
    public static Site siteAt(int x, int z) {
        if (Math.abs(x) <= SITE_RADIUS && Math.abs(z) <= SITE_RADIUS) {
            return Site.MAIN;
        }
        int base = Math.max(0, Math.round((x - MissionInstance.BASE_X)
                / (float) MissionInstance.SPACING));
        for (int slot = Math.max(0, base - 1); slot <= base + 1; slot++) {
            MissionInstance.Anchor anchor = MissionInstance.anchorForRound(slot);
            Site destination = new Site(anchor.x(), anchor.z());
            if (Math.abs(x - destination.x()) <= SITE_RADIUS
                    && Math.abs(z - destination.z()) <= SITE_RADIUS) {
                return destination;
            }
            Site start = MissionInstance.startSite(anchor);
            if (Math.abs(x - start.x()) <= SITE_RADIUS
                    && Math.abs(z - start.z()) <= SITE_RADIUS) {
                return start;
            }
        }
        return null;
    }

    private DockingBay() {
    }

    /** 该 (x,z) 是否落在梯子所在的 3×1 通道（用户存档的竖井是敞开的，没有额外墙）。 */
    public static boolean inTube(int x, int z) {
        return Math.abs(x - X) <= HALF_WIDTH && z == Z;
    }

    /** 该 (x,z) 是否落在气闸室内部（5×3）。 */
    public static boolean inRoom(int x, int z) {
        return Math.abs(x - X) <= ROOM_HALF_X && Math.abs(z - Z) <= ROOM_HALF_Z;
    }

    /**
     * 上一版误把潜艇顶到 -68、顶掉了站侧 5×3 平台；旧档降 1 格后补回平台和中层舱口。
     * 只用于 {@link StarterSub#PREV_ORIGIN_Y} 的迁移，不参与正常世界生成。
     */
    public static void restoreMidDeck(ServerLevel level) {
        BlockState plating = ModShipBlocks.STATION_PLATING.get().defaultBlockState();
        for (int x = X - ROOM_HALF_X; x <= X + ROOM_HALF_X; x++) {
            for (int z = Z - ROOM_HALF_Z; z <= Z + ROOM_HALF_Z; z++) {
                level.setBlock(new BlockPos(x, MID_HATCH_Y, z), plating, 3);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            level.setBlock(new BlockPos(X + sx * (ROOM_HALF_X + 1), MID_HATCH_Y, Z), plating, 3);
        }
        for (int i = 0; i < 3; i++) {
            int x = X - HALF_WIDTH + i;
            BlockState state = ModShipBlocks.HATCH.get().defaultBlockState()
                    .setValue(HatchBlock.FACING, Direction.NORTH)
                    .setValue(HatchBlock.PART, HatchBlock.Part.values()[i])
                    .setValue(HatchBlock.OPEN, true);
            level.setBlock(new BlockPos(x, MID_HATCH_Y, Z), state, 3);
        }
        ensureHatchRow(level, Site.MAIN, MID_HATCH_Y);
    }

    /**
     * 某个站点「靠泊标准位」的核心坐标。
     *
     * <p>旧布局（9×5×15 小艇）核心在站原点 (+1, -72, −3)；新布局「儒艮号」
     * 旋转 90° 后核心在 (+6, −72, 0)。靠泊吸附、声纳对接判定必须用同一套坐标，
     * 否则新船会被吸到旧船的码头位上，舱口对不上、看起来就是「对接对歪了」。</p>
     */
    public static BlockPos dockCore(Site site, boolean newLayout) {
        if (newLayout) {
            return new BlockPos(site.x() + StarterSub.NEW_CORE_OFFSET_X,
                    StarterSub.NEW_CORE_OFFSET_Y,
                    site.z() + StarterSub.NEW_CORE_OFFSET_Z);
        }
        return new BlockPos(site.x() + StarterSub.ORIGIN_X + 5,
                StarterSub.ORIGIN_Y + 1,
                site.z() + StarterSub.ORIGIN_Z + 4);
    }

    /** 是不是初始潜艇在码头的位置（核心 x/z 相对站点固定，y 覆盖新旧几版）。 */
    public static boolean isDockedCore(BlockPos core) {
        Site site = siteAt(core.getX(), core.getZ());
        if (site == null) {
            return false;
        }
        // 新世界：儒艮号挂在站下方，核心在 (site,-72,-6)；驾驶室改过后可能低一格。
        // 只认 x/z 对齐 + 标称位/低一格，和 StarterSub.starterCore 同一判定。
        BlockPos nominal = StarterSub.newCorePos(site.x(), site.z());
        if (StarterSub.isStarterCoreAt(nominal, core)) {
            return true;
        }
        // 旧出生潜艇在码头里的几版位置。
        return core.getX() == site.x() + 1
                && core.getZ() == site.z() - 3
                && core.getY() >= StarterSub.LEGACY_ORIGIN_Y + 1
                && core.getY() <= StarterSub.ORIGIN_Y + 1;
    }

    /**
     * 对接时潜艇头顶那一层（-68）的“虚拟密封盖”：中层平台、中层舱口和平台外的空气
     * 在船体分舱里都当实心。这样拆掉潜艇顶舱口后，水也只会灌到 -69 的舱口格，
     * 不会把 -68 的空气判成连海；同时舱口水面不会被额外的单格舱室切碎。
     */
    public static boolean isDockCapRow(BlockPos pos) {
        Site site = siteAt(pos.getX(), pos.getZ());
        return site != null
                && pos.getY() == MID_HATCH_Y
                && pos.getX() >= site.x() + StarterSub.ORIGIN_X
                && pos.getX() <= site.x() + StarterSub.ORIGIN_X + StarterSub.SIZE_X - 1
                && pos.getZ() >= site.z() + StarterSub.ORIGIN_Z
                && pos.getZ() <= site.z() + StarterSub.ORIGIN_Z + StarterSub.SIZE_Z - 1;
    }

    /** 站侧/中层 3×1 舱口格（中层舱口现在就是潜艇顶部舱口）。 */
    public static boolean isStationDockHatch(BlockPos pos) {
        Site site = siteAt(pos.getX(), pos.getZ());
        return site != null
                && pos.getZ() == site.z()
                && Math.abs(pos.getX() - site.x()) <= HALF_WIDTH
                && (pos.getY() == STATION_HATCH_Y || pos.getY() == MID_HATCH_Y);
    }

    /** 气闸室两侧的舱壁门：站体方块，不能算进潜艇船体（否则潜艇上移后会连成一片）。 */
    public static boolean isStationSideDoor(BlockPos pos) {
        Site site = siteAt(pos.getX(), pos.getZ());
        return site != null
                && pos.getZ() == site.z()
                && Math.abs(pos.getX() - site.x()) == ROOM_HALF_X + 1
                && (pos.getY() == SIDE_DOOR_Y || pos.getY() == SIDE_DOOR_Y + 1);
    }

    /** 站内竖井/地板洞口的铁梯：不属于潜艇，装配时不能被 collectBlocks 收走。 */
    public static boolean isStationLadder(BlockPos pos) {
        Site site = siteAt(pos.getX(), pos.getZ());
        return site != null
                && pos.getX() == site.x() && pos.getZ() == site.z()
                && pos.getY() >= MID_HATCH_Y && pos.getY() <= STATION_FLOOR_UPPER_Y;
    }

    /** 该位置是否算“通道干燥区”（含气闸室、竖井，给客户端滤镜用）。 */
    public static boolean inDryZone(double x, double z, double y) {
        if (y < SUB_HATCH_Y || y > STATION_FLOOR_UPPER_Y) {
            return false;
        }
        Site site = siteAt((int) Math.floor(x), (int) Math.floor(z));
        if (site == null) {
            return false;
        }
        double lx = x - site.x();
        double lz = z - site.z();
        // 气闸室 + 中层平台 + 站内地板洞口
        if (y >= MID_HATCH_Y
                && lx >= -ROOM_HALF_X && lx <= ROOM_HALF_X + 1
                && lz >= -ROOM_HALF_Z && lz <= ROOM_HALF_Z + 1) {
            return true;
        }
        // 下层梯子通道（3×1）
        if (y >= SUB_HATCH_Y
                && lx >= -HALF_WIDTH && lx <= HALF_WIDTH + 1
                && lz >= 0 && lz <= 1) {
            return true;
        }
        // 旧存档还是 3×4 的井，只有主站有这一版，别把老档的通道误判成水下。
        return site.equals(Site.MAIN)
                && y >= LEGACY_SUB_HATCH_Y
                && Math.abs(lx) <= HALF_WIDTH + 1
                && lz >= -2.5D && lz <= 1.5D;
    }

    // ------------------------------------------------------------------
    // 世界生成：哨站侧通道
    // ------------------------------------------------------------------

    /** 由 {@link OutpostBuilder#build} 调用：挖洞、砌气闸室/竖井、放梯子/侧门/舱口。 */
    public static void buildStation(ChunkAccess chunk, Site site) {
        ChunkPos pos = chunk.getPos();
        int sx = site.x();
        int sz = site.z();
        BlockState plating = ModShipBlocks.STATION_PLATING.get().defaultBlockState();
        BlockState slab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(SlabBlock.WATERLOGGED, Boolean.FALSE);

        // 站内地板两层都开 3×1 洞：只开 -64 会被 -63 的表层挡住。
        for (int y = STATION_FLOOR_Y; y <= STATION_FLOOR_UPPER_Y; y++) {
            for (int x = sx - HALF_WIDTH; x <= sx + HALF_WIDTH; x++) {
                setAir(chunk, x, y, sz);
            }
        }
        // -63 洞口围一圈半砖（5×3），和实机存档一致。
        for (int z = sz - 1; z <= sz + 1; z++) {
            for (int x = sx - 2; x <= sx + 2; x++) {
                if (z == sz && Math.abs(x - sx) <= HALF_WIDTH) {
                    continue;
                }
                set(chunk, x, STATION_FLOOR_UPPER_Y, z, slab);
            }
        }

        // 气闸室内部 5×3×3 先清空。
        for (int y = ROOM_MIN_Y; y <= ROOM_MAX_Y; y++) {
            for (int x = sx - ROOM_HALF_X; x <= sx + ROOM_HALF_X; x++) {
                for (int z = sz - ROOM_HALF_Z; z <= sz + ROOM_HALF_Z; z++) {
                    setAir(chunk, x, y, z);
                }
            }
        }
        // 气闸室墙：只砌前后端墙（z=±2）和左右侧墙（x=±3），
        // 四个角按用户存档保持敞开，不再额外封角。
        for (int y = ROOM_MIN_Y; y <= ROOM_MAX_Y; y++) {
            for (int x = sx - ROOM_HALF_X - 1; x <= sx + ROOM_HALF_X + 1; x++) {
                for (int z = sz - ROOM_HALF_Z - 1; z <= sz + ROOM_HALF_Z + 1; z++) {
                    boolean endWall = Math.abs(z - sz) == ROOM_HALF_Z + 1
                            && Math.abs(x - sx) <= ROOM_HALF_X;
                    boolean sideWall = Math.abs(x - sx) == ROOM_HALF_X + 1
                            && Math.abs(z - sz) <= ROOM_HALF_Z;
                    if (endWall || sideWall) {
                        set(chunk, x, y, z, plating);
                    }
                }
            }
        }
        // 两侧舱壁门：门面朝气闸室内。
        placeBulkheadDoor(chunk, sx - ROOM_HALF_X - 1, SIDE_DOOR_Y, sz, Direction.EAST);
        placeBulkheadDoor(chunk, sx + ROOM_HALF_X + 1, SIDE_DOOR_Y, sz, Direction.WEST);
        // 门框下方的支撑块（-68，x=±3、z=0）。
        for (int side = -1; side <= 1; side += 2) {
            set(chunk, sx + side * (ROOM_HALF_X + 1), MID_HATCH_Y, sz, plating);
        }

        // 中层平台（-68，5×3）+ 中层舱口。
        for (int x = sx - ROOM_HALF_X; x <= sx + ROOM_HALF_X; x++) {
            for (int z = sz - ROOM_HALF_Z; z <= sz + ROOM_HALF_Z; z++) {
                set(chunk, x, MID_HATCH_Y, z, plating);
            }
        }
        placeHatch(chunk, pos, site, MID_HATCH_Y, true);

        // 下层现在就是潜艇内部（船顶 -69），竖井梯子由 StarterSub 在船内铺，
        // 站侧不再额外放梯子，避免和船内梯子重叠/朝向打架。

        // 气闸室内和站内地板洞口的梯子（舱口格本身打开时可攀爬，不占梯子方块）。
        for (int y = ROOM_MIN_Y; y <= ROOM_MAX_Y; y++) {
            set(chunk, sx, y, sz, ironLadder());
        }
        set(chunk, sx, STATION_FLOOR_UPPER_Y, sz, ironLadder());

        // 站侧对接舱口（对接时打开）。
        placeHatch(chunk, pos, site, STATION_HATCH_Y, true);
    }

    /** 免支撑铁梯的默认状态（立在井里，不依赖墙面）。 */
    private static BlockState ironLadder() {
        return ModBlocks.IRON_LADDER.get().defaultBlockState()
                .setValue(LadderBlock.FACING, Direction.EAST)
                .setValue(LadderBlock.WATERLOGGED, Boolean.FALSE);
    }

    /**
     * 玩家进世界时补方块实体并做旧档密封（幂等）。
     *
     * <p>世界生成阶段放的舱口/门没有方块实体（GeckoLib 模型靠方块实体渲染），
     * 而同状态的 {@code setBlock} 是 no-op，必须显式创建中段的
     * {@link com.opamb.barotrauma.blockentity.HatchBlockEntity} / 门下半格实体。</p>
     */
    public static void ensureStationHatch(ServerLevel level) {
        // 旧存档修复：地板两层开洞是新改动，老哨站区块不会重跑 worldgen（issue #22）。
        repairStationShaft(level);
        for (Site site : activeSites(level)) {
            ensureStationHatch(level, site);
        }
    }

    /** 单个站点：补齐三道舱口和两扇侧门的方块实体。 */
    public static void ensureStationHatch(ServerLevel level, Site site) {
        level.getChunk(site.x() >> 4, site.z() >> 4);
        ensureHatchRow(level, site, STATION_HATCH_Y);
        ensureHatchRow(level, site, MID_HATCH_Y);
        ensureHatchRow(level, site, SUB_HATCH_Y);
        ensureBulkhead(level, site.x() - ROOM_HALF_X - 1, SIDE_DOOR_Y, site.z());
        ensureBulkhead(level, site.x() + ROOM_HALF_X + 1, SIDE_DOOR_Y, site.z());
        ensureMissionTerminal(level, site);
    }

    /** 任务终端也靠 BE 渲染：旧存档/世界生成漏建时补一个。 */
    private static void ensureMissionTerminal(ServerLevel level, Site site) {
        BlockPos pos = OutpostBuilder.terminalPos(site);
        level.getChunkAt(pos);
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.MISSION_TERMINAL.get()) && level.getBlockEntity(pos) == null
                && state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock) {
            var be = entityBlock.newBlockEntity(pos, state);
            if (be != null) {
                level.setBlockEntity(be);
            }
        }
    }

    /** 主站 + 本轮副本站（目的站和出发站）；给开局补实体、开合舱口用。 */
    public static java.util.List<Site> activeSites(ServerLevel level) {
        java.util.List<Site> sites = new java.util.ArrayList<>(3);
        sites.add(Site.MAIN);
        try {
            var state = com.opamb.barotrauma.campaign.CampaignState.wallet(level);
            var round = state.round();
            if (round.hasInstance()) {
                Site instance = new Site(round.instanceX(), round.instanceZ());
                if (!instance.equals(Site.MAIN)) {
                    sites.add(instance);
                }
            }
            // 本轮关卡的出发站：和目的站一起补舱口/门/终端的方块实体
            MissionInstance.Anchor anchor =
                    MissionInstance.anchorForRound(state.roundsCompleted());
            Site start = MissionInstance.startSite(anchor);
            if (!sites.contains(start)) {
                sites.add(start);
            }
        } catch (Throwable ignored) {
            // 世界数据还没初始化（测试/非常早的加载）：只处理主站。
        }
        return sites;
    }

    /** 确保某层 3×1 舱口的方块实体存在（只有中段需要）。 */
    private static void ensureHatchRow(ServerLevel level, Site site, int y) {
        for (int i = 0; i < 3; i++) {
            int x = site.x() - HALF_WIDTH + i;
            BlockPos pos = new BlockPos(x, y, site.z());
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HatchBlock)) {
                continue;
            }
            if (state.getValue(HatchBlock.PART) != HatchBlock.Part.MIDDLE) {
                continue;
            }
            if (level.getBlockEntity(pos) == null) {
                level.setBlockEntity(
                        new com.opamb.barotrauma.blockentity.HatchBlockEntity(pos, state));
            }
        }
    }

    /** 确保舱壁门下半格有方块实体（整扇门由它渲染）。 */
    private static void ensureBulkhead(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BulkheadDoorBlock
                && state.getValue(BulkheadDoorBlock.PART) == BulkheadDoorBlock.Part.LOWER
                && level.getBlockEntity(pos) == null) {
            level.setBlockEntity(
                    new com.opamb.barotrauma.blockentity.BulkheadDoorBlockEntity(pos, state));
        }
    }

    /**
     * 旧存档一次性修复：y=-63/-64 的 3×1 通道若仍是哨站装甲则打通。
     *
     * <p>只替换哨站装甲，玩家自己改过的方块不动；跑过一次后写世界级标记，
     * 之后永久跳过（玩家在通道放的装甲不会再被删）。issue #22：老哨站区块
     * 不会重跑 worldgen。</p>
     */
    public static void repairStationShaft(ServerLevel level) {
        // 修复只跑一次：之后玩家在通道放的装甲不再被删（StationRepairSavedData 标记）。
        if (StationRepairSavedData.get(level).done()) {
            return;
        }
        for (int y = STATION_FLOOR_Y; y <= STATION_FLOOR_UPPER_Y; y++) {
            for (int x = X - HALF_WIDTH; x <= X + HALF_WIDTH; x++) {
                BlockPos pos = new BlockPos(x, y, Z);
                if (level.getBlockState(pos).is(ModShipBlocks.STATION_PLATING.get())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        StationRepairSavedData.get(level).markDone();
    }

    // ------------------------------------------------------------------
    // 运行时：开合对接舱口
    // ------------------------------------------------------------------

    /** 潜艇离站前调用：所有已知站点（主站 + 本轮副本站）的站侧、中层和潜艇侧舱口都关上。 */
    public static void close(ServerLevel level) {
        for (Site site : activeSites(level)) {
            setHatchOpen(level, site, STATION_HATCH_Y, false);
            setHatchOpen(level, site, MID_HATCH_Y, false);
            if (!com.opamb.barotrauma.ship.vs.VsState.active()) {
                setHatchOpen(level, site, SUB_HATCH_Y, false);
                setHatchOpen(level, site, LEGACY_SUB_HATCH_Y, false);
            }
        }
    }

    /** 靠泊时调用：各层舱口打开。 */
    public static void open(ServerLevel level) {
        for (Site site : activeSites(level)) {
            setHatchOpen(level, site, STATION_HATCH_Y, true);
            setHatchOpen(level, site, MID_HATCH_Y, true);
            if (!com.opamb.barotrauma.ship.vs.VsState.active()) {
                setHatchOpen(level, site, SUB_HATCH_Y, true);
                setHatchOpen(level, site, LEGACY_SUB_HATCH_Y, true);
            }
        }
    }

    /** 单个站点：开/关该站的三道舱口（物理船状态下不动艇侧）。 */
    public static void setHatchOpen(ServerLevel level, Site site, boolean open) {
        setHatchOpen(level, site, STATION_HATCH_Y, open);
        setHatchOpen(level, site, MID_HATCH_Y, open);
        if (!com.opamb.barotrauma.ship.vs.VsState.active()) {
            setHatchOpen(level, site, SUB_HATCH_Y, open);
            setHatchOpen(level, site, LEGACY_SUB_HATCH_Y, open);
        }
    }

    /** 按行设置某层 3 格舱口的开关。 */
    private static void setHatchOpen(ServerLevel level, Site site, int y, boolean open) {
        for (int x = site.x() - HALF_WIDTH; x <= site.x() + HALF_WIDTH; x++) {
            BlockPos pos = new BlockPos(x, y, site.z());
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof HatchBlock && state.hasProperty(HatchBlock.OPEN)) {
                level.setBlock(pos, state.setValue(HatchBlock.OPEN, open), 2);
            }
        }
    }

    // ------------------------------------------------------------------
    // 方块摆放
    // ------------------------------------------------------------------

    /**
     * 3×1 水平舱口：中段和左右段各占一格，{@code facing} 指长轴方向。
     * 这里固定朝北（长轴沿 x），和潜艇顶部舱口使用同一套部件顺序。
     *
     * <p>站侧对接舱口（-64）的中段用带梯变种 {@code hatch_ladder}：舱口格本身就是
     * 梯子，和下面 -65..-67 的铁梯连成一条；开关舱口不影响梯子。</p>
     */
    public static void placeHatch(ChunkAccess chunk, ChunkPos chunkPos, Site site,
                                  int y, boolean open) {
        HatchBlock.Part[] parts = {
                HatchBlock.Part.LEFT, HatchBlock.Part.MIDDLE, HatchBlock.Part.RIGHT
        };
        for (int i = 0; i < parts.length; i++) {
            int x = site.x() - HALF_WIDTH + i;
            if (!inChunk(chunkPos, x, site.z())) {
                continue;
            }
            boolean withLadder = parts[i] == HatchBlock.Part.MIDDLE;
            BlockState state = (withLadder ? ModShipBlocks.HATCH_LADDER.get() : ModShipBlocks.HATCH.get())
                    .defaultBlockState()
                    .setValue(HatchBlock.FACING, Direction.NORTH)
                    .setValue(HatchBlock.PART, parts[i])
                    .setValue(HatchBlock.OPEN, open);
            chunk.setBlockState(new BlockPos(x, y, site.z()), state, false);
            // 世界生成阶段就把中段的舱口 BE 建好，模型不用右键才出现
            if (state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock) {
                var be = entityBlock.newBlockEntity(new BlockPos(x, y, site.z()), state);
                if (be != null) {
                    chunk.setBlockEntity(be);
                }
            }
        }
    }

    /** 潜艇顶部的 3×1 舱口（由 {@link StarterSub} 放置）；中段也用带梯变种。 */
    public static BlockState subHatchState(int index, boolean open) {
        HatchBlock.Part[] parts = {
                HatchBlock.Part.LEFT, HatchBlock.Part.MIDDLE, HatchBlock.Part.RIGHT
        };
        HatchBlock.Part part = parts[Math.max(0, Math.min(2, index))];
        return (part == HatchBlock.Part.MIDDLE
                ? ModShipBlocks.HATCH_LADDER.get() : ModShipBlocks.HATCH.get())
                .defaultBlockState()
                .setValue(HatchBlock.FACING, Direction.NORTH)
                .setValue(HatchBlock.PART, part)
                .setValue(HatchBlock.OPEN, open);
    }

    /** 2 格高舱壁门（侧向出口，门面朝气闸室内）。 */
    private static void placeBulkheadDoor(ChunkAccess chunk, int x, int y, int z, Direction facing) {
        if (!inChunk(chunk.getPos(), x, z)) {
            return;
        }
        BlockState lower = ModShipBlocks.BULKHEAD_DOOR.get().defaultBlockState()
                .setValue(BulkheadDoorBlock.FACING, facing)
                .setValue(BulkheadDoorBlock.OPEN, Boolean.FALSE)
                .setValue(BulkheadDoorBlock.PART, BulkheadDoorBlock.Part.LOWER);
        chunk.setBlockState(new BlockPos(x, y, z), lower, false);
        chunk.setBlockState(new BlockPos(x, y + 1, z),
                lower.setValue(BulkheadDoorBlock.PART, BulkheadDoorBlock.Part.UPPER), false);
        // 世界生成阶段就把下半格门 BE 建好，模型不用右键才出现
        if (lower.getBlock() instanceof net.minecraft.world.level.block.EntityBlock entityBlock) {
            var be = entityBlock.newBlockEntity(new BlockPos(x, y, z), lower);
            if (be != null) {
                chunk.setBlockEntity(be);
            }
        }
    }

    private static void set(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        if (inChunk(chunk.getPos(), x, z)) {
            chunk.setBlockState(new BlockPos(x, y, z), state, false);
        }
    }

    private static void setAir(ChunkAccess chunk, int x, int y, int z) {
        set(chunk, x, y, z, Blocks.AIR.defaultBlockState());
    }

    private static boolean inChunk(ChunkPos pos, int x, int z) {
        return x >= pos.getMinBlockX() && x <= pos.getMaxBlockX()
                && z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }
}
