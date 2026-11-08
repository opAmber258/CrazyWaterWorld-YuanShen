package com.opamb.barotrauma.world;

import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 新手哨站（M1 版本）：一个 40×21×24 的气密盒子，内部分成前厅 / 医疗 / 补给三间。
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

    public static boolean intersects(ChunkPos pos, DockingBay.Site site) {
        return pos.getMaxBlockX() >= site.x() - HALF_X && pos.getMinBlockX() <= site.x() + HALF_X
                && pos.getMaxBlockZ() >= site.z() - HALF_Z && pos.getMinBlockZ() <= site.z() + HALF_Z;
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
        int minX = site.x() - HALF_X;
        int maxX = site.x() + HALF_X;
        int minZ = site.z() - HALF_Z;
        int maxZ = site.z() + HALF_Z;
        int x0 = Math.max(pos.getMinBlockX(), minX);
        int x1 = Math.min(pos.getMaxBlockX(), maxX);
        int z0 = Math.max(pos.getMinBlockZ(), minZ);
        int z1 = Math.min(pos.getMaxBlockZ(), maxZ);

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    BlockState state = stateAt(x - site.x(), y, z - site.z());
                    if (state != null) {
                        chunk.setBlockState(new BlockPos(x, y, z), state, false);
                    }
                }
            }
        }

        // 外部门口平台（在水里，出门就是海）
        BlockState plating = plating();
        int platformY = ModDimensions.OUTPOST_SPAWN_Y - 1;
        for (int lx = -3; lx <= 3; lx++) {
            for (int lz = HALF_Z + 1; lz <= HALF_Z + 5; lz++) {
                int x = site.x() + lx;
                int z = site.z() + lz;
                if (inChunk(pos, x, z)) {
                    chunk.setBlockState(new BlockPos(x, platformY, z), plating, false);
                }
            }
        }

        // 气闸门（两格高）
        if (inChunk(pos, site.x(), site.z() + HALF_Z)) {
            placeDoor(chunk, site.x(), ModDimensions.OUTPOST_SPAWN_Y, site.z() + HALF_Z);
        }

        // 任务终端：前厅右侧（医疗间门口），接任务/交付/商店都在这里
        if (inChunk(pos, site.x() + 8, site.z())) {
            chunk.setBlockState(new BlockPos(site.x() + 8, ModDimensions.OUTPOST_SPAWN_Y - 1, site.z()),
                    com.opamb.barotrauma.registry.ModBlocks.MISSION_TERMINAL.get().defaultBlockState(),
                    false);
        }

        // 对接通道：站内地板开洞、向下 3长×4宽×6高的垂直气密通道、底部水平对接舱口
        DockingBay.buildStation(chunk, site);
    }

    /** 局部坐标（相对站点中心）取方块；y 仍是绝对高度。 */
    private static BlockState stateAt(int x, int y, int z) {
        if (Math.abs(x) > HALF_X || Math.abs(z) > HALF_Z || y < MIN_Y || y > MAX_Y) {
            return null;
        }

        boolean interior = Math.abs(x) <= INNER_HALF_X
                && Math.abs(z) <= INNER_HALF_Z
                && y >= INNER_MIN_Y && y <= INNER_MAX_Y;

        if (interior) {
            // 隔墙：把内部切成三间，中间 |z|<=1 且下三格留门洞
            if (Math.abs(x) == 6 && y >= INNER_MIN_Y && y <= INNER_MAX_Y) {
                boolean doorway = Math.abs(z) <= 1 && y <= INNER_MIN_Y + 2;
                if (!doorway) {
                    return plating();
                }
            }
            // 天花板灯
            if (y == INNER_MAX_Y && Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0) {
                return Blocks.SEA_LANTERN.defaultBlockState();
            }
            return Blocks.AIR.defaultBlockState();
        }

        // 气闸门洞：把外墙挖穿，等下面放门
        if (x == 0 && z == HALF_Z && (y == ModDimensions.OUTPOST_SPAWN_Y
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

    private static boolean inChunk(ChunkPos pos, int x, int z) {
        return x >= pos.getMinBlockX() && x <= pos.getMaxBlockX()
                && z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }
}
