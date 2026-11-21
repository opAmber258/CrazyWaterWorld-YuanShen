package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 把逐格水场翻译成真实水方块（调试/命令用）。
 *
 * <p>正式渲染走客户端自绘水面；这里只把"水量 ≥ 一格"的格写成原版水，方便
 * {@code /ship flood} 和自检。部分填充格原版水方块表示不了，会留空。</p>
 */
public final class ShipWater {

    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** 一个舱室（或破口空腔）的格子。 */
    private record Room(List<BlockPos> cells) {
    }

    private final List<Room> rooms;
    private final ShipSimulation simulation;

    public ShipWater(ShipGeometry.Result geometry, ShipSimulation simulation) {
        this.simulation = simulation;
        this.rooms = new ArrayList<>();
        for (ShipGeometry.Compartment compartment : geometry.compartments()) {
            rooms.add(new Room(sorted(compartment.cells())));
        }
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            rooms.add(new Room(sorted(pocket.cells())));
        }
    }

    private static List<BlockPos> sorted(Iterable<BlockPos> cells) {
        List<BlockPos> list = new ArrayList<>();
        cells.forEach(list::add);
        list.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return list;
    }

    /** 按逐格水场重写水方块（调试/命令用；部分填充格无法用原版水方块表示）。 */
    public int apply(Level level) {
        int changed = 0;
        com.opamb.barotrauma.ship.water.ShipFluidField field = simulation.fluid();
        if (field == null) {
            return 0;
        }
        for (Room room : rooms) {
            for (BlockPos pos : room.cells) {
                BlockState state = level.getBlockState(pos);
                boolean shouldBeWater = field.waterAt(pos) >= ShipSimulation.UNITS_PER_BLOCK - 0.5D;
                if (shouldBeWater && state.isAir()) {
                    level.setBlock(pos, WATER, 3);
                    changed++;
                } else if (!shouldBeWater && state.is(Blocks.WATER)) {
                    level.setBlock(pos, AIR, 3);
                    changed++;
                }
            }
        }
        return changed;
    }

    /**
     * 把船内所有"我们写进去的水"变回空气。
     *
     * <p>只清舱室和连海空腔里的水，船体方块一格不碰。装船前调用，
     * 免得装配之后原来的位置陷下一坨水。</p>
     */
    public int clear(Level level) {
        return clear(level, 0, 0, 0);
    }

    /** 带整数位移的版本：拆船时方块铺回的是"原坐标 + 船位移"。 */
    public int clear(Level level, int dx, int dy, int dz) {
        int changed = 0;
        for (Room room : rooms) {
            for (BlockPos pos : room.cells) {
                BlockPos target = pos.offset(dx, dy, dz);
                if (level.getBlockState(target).is(Blocks.WATER)) {
                    level.setBlock(target, AIR, 3);
                    changed++;
                }
            }
        }
        return changed;
    }
}
