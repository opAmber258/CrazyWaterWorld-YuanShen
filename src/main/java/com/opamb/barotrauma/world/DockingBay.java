package com.opamb.barotrauma.world;

import com.opamb.barotrauma.block.HatchBlock;
import com.opamb.barotrauma.registry.ModShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 哨站与潜艇之间的对接结构（照原版截图搭的第一版）。
 *
 * <p>布局（竖剖面，中心 x=0、z=0）：</p>
 * <pre>
 *   y=-63  哨站内层地板（和 -64 一起开 3×1 洞，否则被装甲挡住）
 *   y=-64  哨站地板外壳层（开 3×1 洞）
 *   y=-65  哨站侧水平对接舱口（3 格宽，对接时打开）
 *   -66..-73  垂直气密通道（3×1，带梯子；两侧 x=±2 是通海舱门）
 *   y=-74  潜艇顶部水平舱口（3 格宽）
 *   -74..-78  潜艇（船顶 -74，船底 -78）
 * </pre>
 *
 * <p>对接时上下两道舱口都打开，玩家可以从潜艇经通道爬进哨站；潜艇离站前两道舱口
 * 都关上，避免海水灌进艇内或哨站。两侧舱门照原版可以打开出去维修潜艇（站内不做进水
 * 数值模拟，开门只是视觉/通行）。</p>
 */
public final class DockingBay {

    /** 通道中心（= 哨站中心 x/z）。 */
    public static final int X = 0;
    public static final int Z = 0;
    /** 通道内部 3×1：x -1..1，z=0。 */
    public static final int HALF_WIDTH = 1;
    /** 哨站地板 y。 */
    public static final int STATION_FLOOR_Y = OutpostBuilder.MIN_Y;
    /** 哨站内层地板 y：通道必须同时打穿两层，否则被 -63 的装甲挡住（issue #22）。 */
    public static final int STATION_FLOOR_UPPER_Y = STATION_FLOOR_Y + 1;
    /** 哨站侧对接舱口 y。 */
    public static final int STATION_HATCH_Y = STATION_FLOOR_Y - 1;
    /** 通道内部上下端。 */
    public static final int TUBE_TOP_Y = STATION_FLOOR_Y - 2;
    public static final int TUBE_BOTTOM_Y = -73;
    /** 潜艇顶部舱口 y（= 潜艇船顶）。 */
    public static final int SUB_HATCH_Y = -74;
    /** 两侧通海舱门的下半格 y（占 -70..-69 两格）。 */
    public static final int SIDE_DOOR_Y = -70;

    private DockingBay() {
    }

    /** 该 (x,z) 是否落在 3×1 通道内部。 */
    public static boolean inTube(int x, int z) {
        return Math.abs(x - X) <= HALF_WIDTH && z == Z;
    }

    // ------------------------------------------------------------------
    // 世界生成：哨站侧通道
    // ------------------------------------------------------------------

    /** 由 {@link OutpostBuilder#build} 调用：挖洞、砌通道、放梯子/侧门/对接舱口。 */
    public static void buildStation(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        BlockState plating = ModShipBlocks.STATION_PLATING.get().defaultBlockState();

        // 哨站地板两层都开 3×1 洞：只开 -64 会被 -63 的内层装甲挡住
        // （issue #22：玩家无法从站内进通道）。
        for (int y = STATION_FLOOR_Y; y <= STATION_FLOOR_UPPER_Y; y++) {
            for (int x = X - HALF_WIDTH; x <= X + HALF_WIDTH; x++) {
                if (inChunk(pos, x, Z)) {
                    chunk.setBlockState(new BlockPos(x, y, Z),
                            Blocks.AIR.defaultBlockState(), false);
                }
            }
        }

        // 通道内部：空气 + 梯子（贴着 z=-1 的岩壁）
        for (int y = STATION_HATCH_Y; y >= SUB_HATCH_Y; y--) {
            for (int x = X - HALF_WIDTH; x <= X + HALF_WIDTH; x++) {
                if (!inChunk(pos, x, Z)) {
                    continue;
                }
                BlockPos here = new BlockPos(x, y, Z);
                if (y == STATION_HATCH_Y) {
                    continue; // 对接舱口单独放
                }
                boolean ladder = x == X && y >= TUBE_TOP_Y && y <= TUBE_BOTTOM_Y;
                chunk.setBlockState(here, ladder
                        ? Blocks.LADDER.defaultBlockState().setValue(
                                net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH)
                        : Blocks.AIR.defaultBlockState(), false);
            }
        }

        // 通道四壁（x=±2 和 z=±1）
        for (int y = STATION_HATCH_Y; y >= SUB_HATCH_Y; y--) {
            for (int x = X - HALF_WIDTH - 1; x <= X + HALF_WIDTH + 1; x++) {
                for (int z = Z - 1; z <= Z + 1; z++) {
                    boolean wall = (Math.abs(x - X) == HALF_WIDTH + 1 && z == Z)
                            || (z != Z && Math.abs(x - X) <= HALF_WIDTH);
                    if (!wall || !inChunk(pos, x, z)) {
                        continue;
                    }
                    chunk.setBlockState(new BlockPos(x, y, z), plating, false);
                }
            }
        }

        // 两侧通海舱门：x=±2 的墙上，2 格高
        for (int side = -1; side <= 1; side += 2) {
            int x = X + side * (HALF_WIDTH + 1);
            if (inChunk(pos, x, Z)) {
                placeSideDoor(chunk, x, SIDE_DOOR_Y, Z, side < 0 ? Direction.WEST : Direction.EAST);
            }
        }

        // 对接处小型泵（照原版：气闸底部一台小泵）
        if (inChunk(pos, X + HALF_WIDTH, Z)) {
            chunk.setBlockState(new BlockPos(X + HALF_WIDTH, TUBE_BOTTOM_Y, Z),
                    com.opamb.barotrauma.registry.ModBlocks.SMALL_PUMP.get().defaultBlockState(), false);
        }

        // 哨站侧对接舱口（对接时打开）
        placeHatch(chunk, pos, STATION_HATCH_Y, true);
    }

    /**
     * 世界生成阶段放的舱口没有方块实体（GeckoLib 模型靠方块实体渲染），
     * 玩家进世界时补一次：同状态 {@code setBlock} 是 no-op，必须显式创建
     * 中段的 {@link com.opamb.barotrauma.blockentity.HatchBlockEntity}（issue #22）。
     */
    public static void ensureStationHatch(ServerLevel level) {
        // 旧存档修复：地板两层开洞是新改动，老哨站区块不会重跑 worldgen（issue #22）。
        repairStationShaft(level);
        for (int i = 0; i < 3; i++) {
            int x = X - HALF_WIDTH + i;
            BlockPos pos = new BlockPos(x, STATION_HATCH_Y, Z);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof HatchBlock) {
                level.setBlock(pos, state, 2);
            } else {
                state = subHatchState(i, true);
                level.setBlock(pos, state, 2);
            }
            if (i == 1 && level.getBlockEntity(pos) == null) {
                level.setBlockEntity(
                        new com.opamb.barotrauma.blockentity.HatchBlockEntity(pos, state));
            }
        }
    }

    /**
     * 旧存档一次性修复：y=-63/-64 的 3×1 通道若仍是哨站装甲则打通。
     *
     * <p>只替换哨站装甲，玩家自己改过的方块不动；结果是幂等的
     * （修复后再跑只看到空气）。issue #22：老哨站区块不会重跑 worldgen。</p>
     */
    public static void repairStationShaft(ServerLevel level) {
        for (int y = STATION_FLOOR_Y; y <= STATION_FLOOR_UPPER_Y; y++) {
            for (int x = X - HALF_WIDTH; x <= X + HALF_WIDTH; x++) {
                BlockPos pos = new BlockPos(x, y, Z);
                if (level.getBlockState(pos).is(ModShipBlocks.STATION_PLATING.get())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 运行时：开合对接舱口
    // ------------------------------------------------------------------

    /** 潜艇离站前调用：哨站侧和潜艇侧舱口都关上。 */
    public static void close(ServerLevel level) {
        setHatchOpen(level, STATION_HATCH_Y, false);
        if (!com.opamb.barotrauma.ship.vs.VsState.active()) {
            setHatchOpen(level, SUB_HATCH_Y, false);
        }
    }

    /** 靠泊时调用：两侧舱口打开。 */
    public static void open(ServerLevel level) {
        setHatchOpen(level, STATION_HATCH_Y, true);
        if (!com.opamb.barotrauma.ship.vs.VsState.active()) {
            setHatchOpen(level, SUB_HATCH_Y, true);
        }
    }

    /** 按行设置某层 3 格舱口的开关。 */
    private static void setHatchOpen(ServerLevel level, int y, boolean open) {
        for (int x = X - HALF_WIDTH; x <= X + HALF_WIDTH; x++) {
            BlockPos pos = new BlockPos(x, y, Z);
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
     */
    public static void placeHatch(ChunkAccess chunk, ChunkPos chunkPos, int y, boolean open) {
        HatchBlock.Part[] parts = {
                HatchBlock.Part.LEFT, HatchBlock.Part.MIDDLE, HatchBlock.Part.RIGHT
        };
        for (int i = 0; i < parts.length; i++) {
            int x = X - HALF_WIDTH + i;
            if (!inChunk(chunkPos, x, Z)) {
                continue;
            }
            BlockState state = ModShipBlocks.HATCH.get().defaultBlockState()
                    .setValue(HatchBlock.FACING, Direction.NORTH)
                    .setValue(HatchBlock.PART, parts[i])
                    .setValue(HatchBlock.OPEN, open);
            chunk.setBlockState(new BlockPos(x, y, Z), state, false);
        }
    }

    /** 潜艇顶部的 3×1 舱口（由 {@link StarterSub} 放置）。 */
    public static BlockState subHatchState(int index, boolean open) {
        HatchBlock.Part[] parts = {
                HatchBlock.Part.LEFT, HatchBlock.Part.MIDDLE, HatchBlock.Part.RIGHT
        };
        return ModShipBlocks.HATCH.get().defaultBlockState()
                .setValue(HatchBlock.FACING, Direction.NORTH)
                .setValue(HatchBlock.PART, parts[Math.max(0, Math.min(2, index))])
                .setValue(HatchBlock.OPEN, open);
    }

    /** 两侧通海舱门（木门，2 格高）。 */
    private static void placeSideDoor(ChunkAccess chunk, int x, int y, int z, Direction facing) {
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, Boolean.FALSE)
                .setValue(net.minecraft.world.level.block.DoorBlock.POWERED, Boolean.FALSE)
                .setValue(net.minecraft.world.level.block.DoorBlock.HINGE,
                        net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        chunk.setBlockState(new BlockPos(x, y, z), lower, false);
        chunk.setBlockState(new BlockPos(x, y + 1, z), lower.setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), false);
    }

    private static boolean inChunk(ChunkPos pos, int x, int z) {
        return x >= pos.getMinBlockX() && x <= pos.getMaxBlockX()
                && z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ();
    }
}
