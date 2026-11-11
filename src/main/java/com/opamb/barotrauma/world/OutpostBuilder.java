package com.opamb.barotrauma.world;

import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 哨站（M1 版本）：一个气密的盒子建筑，内部分成前厅 / 医疗 / 补给等房间。
 *
 * <p>主站（教程哨站）保持原样；副本哨站按 slot 轮换 3 套外形/内装（小型中继站、
 * 大型矿业站、研究站），避免每次下潜头顶都是同一栋房子。对接舱、竖井和停靠
 * 判定仍由 {@link DockingBay} 复用用户做的那套，不随变体改变。</p>
 *
 * <p>位置：挂在<b>顶部岩壳的底面</b>，顶部装甲与压平后的顶板底面（y=-44）重合
 * （用户要求：之前哨站悬在 carveCavern 挖出的椭球洞正中，四周全是光滑洞壁，看起来"生在洞里"）。
 * 顶板厚度由 {@code DeepSeaChunkGenerator} 的 {@code ceilingY} 决定：哨站核心区固定压平在
 * {@code y=-44}，外圈渐变到噪声岩壳；哨站上方是一整片平岩壁，四周岩壳更厚，自然包住哨站两侧。</p>
 *
 * <p>生成方式是"按坐标算方块"，每个区块只写和自己相交的部分，所以可以跨区块无缝拼起来。
 * 内墙都是双层装甲板，只有气闸门洞例外——门用橡木门（可以手开，关着的时候挡水），
 * 玩家一开门，海水就会灌进来，这就是《潜渊症》的进水机制。</p>
 */
public final class OutpostBuilder {

    /** 哨站外墙半径。 */
    public static final int HALF_X = 20;
    public static final int HALF_Z = 12;
    /**
     * 外墙 y 范围。顶 {@code MAX_Y=-44} 与压平后的顶板底面重合（装甲替换最后一块岩，密封），
     * 往下 21 格。
     */
    public static final int MIN_Y = -64;
    public static final int MAX_Y = -44;

    /** 内部空腔范围（玩家活动的空间）。 */
    private static final int INNER_HALF_X = 18;
    private static final int INNER_HALF_Z = 10;
    private static final int INNER_MIN_Y = -62;
    private static final int INNER_MAX_Y = -46;

    private OutpostBuilder() {
    }

    /**
     * 哨站外形/内装变体。主站保持原教程哨站；副本站按 slot 轮换 1..3，
     * 让每次下潜钻出水面的站不是同一套房子（对接舱/竖井仍由
     * {@link DockingBay#buildStation} 复用用户做的那套，不在这里改）。
     *
     * @param splitX 纵向隔墙（|x| 等于它），0 = 不切
     * @param splitZ 横向隔墙（|z| 等于它），0 = 不切
     * @param accent 隔墙材质
     * @param terminalX/terminalZ 任务终端相对站点中心的偏移
     */
    private record Layout(int halfX, int halfZ, int innerHalfX, int innerHalfZ,
                          int splitX, int splitZ, Block accent,
                          int terminalX, int terminalZ) {
    }

    /** 主站 = 教程哨站原样；副本出发站沿用“你离开的那座站”的外观，目的站按轮次轮换。 */
    private static Layout layout(DockingBay.Site site) {
        if (DockingBay.Site.MAIN.equals(site)) {
            return tutorialLayout();
        }
        int slot = Math.max(0, Math.round((site.x() - MissionInstance.BASE_X)
                / (float) MissionInstance.SPACING));
        if (isRouteStart(site, slot)) {
            // 出发站 = 上一轮的目的站：保持你在上一轮看到的站不变
            return slot <= 0 ? tutorialLayout() : destinationLayout(slot - 1);
        }
        return destinationLayout(slot);
    }

    private static Layout tutorialLayout() {
        return new Layout(HALF_X, HALF_Z, INNER_HALF_X, INNER_HALF_Z,
                6, 0, ModShipBlocks.STATION_PLATING.get(), 8, 0);
    }

    /** 目的站按轮次轮换：大型矿业站 / 研究站。 */
    private static Layout destinationLayout(int slot) {
        return switch (Math.floorMod(slot, 2)) {
            // 大型矿业站：宽两圈，深板岩隔墙，终端在远侧房间
            case 0 -> new Layout(24, 14, 22, 12, 8, 0, Blocks.DEEPSLATE_TILES, 13, 0);
            // 研究站：纵深更大，多一道横向隔墙（低三格留门洞）
            default -> new Layout(18, 15, 16, 13, 6, 7, Blocks.IRON_BLOCK, 8, -4);
        };
    }

    private static boolean isRouteStart(DockingBay.Site site, int slot) {
        return MissionInstance.startSite(MissionInstance.anchorForRound(slot)).equals(site);
    }

    public static boolean intersects(ChunkPos pos, DockingBay.Site site) {
        Layout layout = layout(site);
        return pos.getMaxBlockX() >= site.x() - layout.halfX()
                && pos.getMinBlockX() <= site.x() + layout.halfX()
                && pos.getMaxBlockZ() >= site.z() - layout.halfZ()
                && pos.getMinBlockZ() <= site.z() + layout.halfZ();
    }

    /** 哨站水池的判定范围（比建筑本体大）。 */
    public static boolean intersectsCavern(ChunkPos pos, DockingBay.Site site) {
        return pos.getMaxBlockX() >= site.x() - 36 && pos.getMinBlockX() <= site.x() + 36
                && pos.getMaxBlockZ() >= site.z() - 36 && pos.getMinBlockZ() <= site.z() + 36;
    }

    /**
     * 在哨站下面和周围挖一个椭球水池：半径 34×26×34，水平中心就是哨站中心，
     * 垂直中心 y=-72——**顶部只到 y=-46，不碰 y=-44 的顶板岩壁**（上一版中心 -150、
     * 椭球顶 -124 直接把顶板挖穿，哨站就悬在洞正中，被用户否了）。
     * 观感是哨站下半截挂在水腔顶部、上半截嵌在岩壁里；气闸门外、初始潜艇停的位置
     * 现在都是空气，水下感由客户端滤镜表现。
     */
    public static void carveCavern(ChunkAccess chunk, DockingBay.Site site) {
        ChunkPos pos = chunk.getPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                for (int y = -98; y <= -46; y++) {
                    double dx = (x - site.x()) / 34.0D;
                    double dy = (y + 72) / 26.0D;
                    double dz = (z - site.z()) / 34.0D;
                    if (dx * dx + dy * dy + dz * dz < 1.0D) {
                        chunk.setBlockState(new BlockPos(x, y, z), air, false);
                    }
                }
            }
        }
    }

    /**
     * 副本站不在大洞里：只在码头正下方挖一条矩形竖井，上端接舱口、下端接任务洞穴。
     * 哨站本体挂在岩壁上，船在竖井里停靠/下潜。
     *
     * <p>21×19：儒艮号放置时转 90°，长轴沿 x（17 格），旧船 9×15 也装得下。</p>
     */
    public static boolean intersectsWell(ChunkPos pos, DockingBay.Site site) {
        return pos.getMaxBlockX() >= site.x() - 11 && pos.getMinBlockX() <= site.x() + 11
                && pos.getMaxBlockZ() >= site.z() - 10 && pos.getMinBlockZ() <= site.z() + 10;
    }

    public static void carveDockWell(ChunkAccess chunk, DockingBay.Site site) {
        ChunkPos pos = chunk.getPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
            if (Math.abs(x - site.x()) > 10) {
                continue;
            }
            for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                if (Math.abs(z - site.z()) > 9) {
                    continue;
                }
                // 上界到中层舱口下方（-69）：-71..-69 是艇顶舱口/船体占的那几格，不能留岩石。
                for (int y = -116; y <= DockingBay.MID_HATCH_Y - 1; y++) {
                    chunk.setBlockState(new BlockPos(x, y, z), air, false);
                }
            }
        }
    }

    public static void build(ChunkAccess chunk, DockingBay.Site site) {
        ChunkPos pos = chunk.getPos();
        Layout layout = layout(site);
        int minX = site.x() - layout.halfX();
        int maxX = site.x() + layout.halfX();
        int minZ = site.z() - layout.halfZ();
        int maxZ = site.z() + layout.halfZ();
        int x0 = Math.max(pos.getMinBlockX(), minX);
        int x1 = Math.min(pos.getMaxBlockX(), maxX);
        int z0 = Math.max(pos.getMinBlockZ(), minZ);
        int z1 = Math.min(pos.getMaxBlockZ(), maxZ);

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    BlockState state = stateAt(x - site.x(), y, z - site.z(), layout);
                    if (state != null) {
                        setWithBe(chunk, new BlockPos(x, y, z), state);
                    }
                }
            }
        }

        // 外部门口平台（在水里，出门就是海）
        BlockState plating = plating();
        int platformY = ModDimensions.OUTPOST_SPAWN_Y - 1;
        for (int lx = -3; lx <= 3; lx++) {
            for (int lz = layout.halfZ() + 1; lz <= layout.halfZ() + 5; lz++) {
                int x = site.x() + lx;
                int z = site.z() + lz;
                if (inChunk(pos, x, z)) {
                    chunk.setBlockState(new BlockPos(x, platformY, z), plating, false);
                }
            }
        }

        // 气闸门（两格高）
        if (inChunk(pos, site.x(), site.z() + layout.halfZ())) {
            placeDoor(chunk, site.x(), ModDimensions.OUTPOST_SPAWN_Y, site.z() + layout.halfZ());
        }

        // 任务终端已弃用：接单/交付/商店改在导航终端（船核心）上操作。

        // 对接通道：站内地板开洞、向下 3长×4宽×6高的垂直气密通道、底部水平对接舱口
        DockingBay.buildStation(chunk, site);
    }

    /** 局部坐标（相对站点中心）取方块；y 仍是绝对高度。 */
    private static BlockState stateAt(int x, int y, int z, Layout layout) {
        if (Math.abs(x) > layout.halfX() || Math.abs(z) > layout.halfZ()
                || y < MIN_Y || y > MAX_Y) {
            return null;
        }

        boolean interior = Math.abs(x) <= layout.innerHalfX()
                && Math.abs(z) <= layout.innerHalfZ()
                && y >= INNER_MIN_Y && y <= INNER_MAX_Y;

        if (interior) {
            // 纵向隔墙：把内部切成几间，中间 |z|<=1 且下三格留门洞
            if (layout.splitX() > 0 && Math.abs(x) == layout.splitX()
                    && y >= INNER_MIN_Y && y <= INNER_MAX_Y) {
                boolean doorway = Math.abs(z) <= 1 && y <= INNER_MIN_Y + 2;
                if (!doorway) {
                    return layout.accent().defaultBlockState();
                }
            }
            // 横向隔墙（研究站）：|x|<=1 且下三格留门洞，别挡住去气闸门的路
            if (layout.splitZ() > 0 && Math.abs(z) == layout.splitZ()
                    && y >= INNER_MIN_Y && y <= INNER_MAX_Y) {
                boolean doorway = Math.abs(x) <= 1 && y <= INNER_MIN_Y + 2;
                if (!doorway) {
                    return layout.accent().defaultBlockState();
                }
            }
            // 天花板灯
            if (y == INNER_MAX_Y && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) {
                return Blocks.SEA_LANTERN.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }

        // 气闸门洞：把外墙挖穿，等下面放门
        if (x == 0 && z == layout.halfZ() && (y == ModDimensions.OUTPOST_SPAWN_Y
                || y == ModDimensions.OUTPOST_SPAWN_Y + 1)) {
            return Blocks.AIR.defaultBlockState();
        }

        return plating();
    }

    private static void placeDoor(ChunkAccess chunk, int x, int y, int z) {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.OPEN, Boolean.FALSE)
                .setValue(DoorBlock.POWERED, Boolean.FALSE)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        chunk.setBlockState(new BlockPos(x, y, z), lower, false);
        chunk.setBlockState(new BlockPos(x, y + 1, z), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), false);
    }

    private static BlockState plating() {
        // 站点用专属装甲，避免被船核心当成"船体"扫进去
        return ModShipBlocks.STATION_PLATING.get().defaultBlockState();
    }

    /**
     * 放置方块，并在世界生成阶段就为带方块实体的方块建好 BE。
     *
     * <p>只 {@code chunk.setBlockState} 的话，GeckoLib 的舱口/舱壁门/任务终端在
     * 客户端没有 BE，模型要等玩家右键让 blockstate 变化后才出现（用户反馈
     * "到了哨站那些模型不点一下刷新不出来"）。世界生成时显式建 BE，区块第一次
     * 发给客户端就会带上它们。</p>
     */
    public static void setWithBe(ChunkAccess chunk, BlockPos pos, BlockState state) {
        chunk.setBlockState(pos, state, false);
        if (state.getBlock() instanceof EntityBlock entityBlock) {
            BlockEntity be = entityBlock.newBlockEntity(pos, state);
            if (be != null) {
                chunk.setBlockEntity(be);
            }
        }
    }

    private static boolean inChunk(ChunkPos pos, int x, int z) {
        return x >= pos.getMinBlockX() && x <= pos.getMaxBlockX()
                && z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }

    /** 任务终端的绝对位置（位置随布局变体走；旧存档补 BE 用）。 */
    public static BlockPos terminalPos(DockingBay.Site site) {
        Layout layout = layout(site);
        return new BlockPos(site.x() + layout.terminalX(),
                ModDimensions.OUTPOST_SPAWN_Y - 1, site.z() + layout.terminalZ());
    }
}
