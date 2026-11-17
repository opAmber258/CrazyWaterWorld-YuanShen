package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 整船平移后，模拟几何/水位必须跟着新坐标走。 */
class ShipShiftTest {

    /** 半径 r 的空心立方壳（内部 (2r-1)³ 空气）。 */
    private static Set<BlockPos> shell(int r) {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.abs(x) == r || Math.abs(y) == r || Math.abs(z) == r) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return solid;
    }

    private static ShipGeometry.Probe probe(Set<BlockPos> solid) {
        return new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return solid.contains(pos);
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }
        };
    }

    private static ShipState state() {
        Set<BlockPos> solid = shell(3);
        ShipGeometry.Result geometry = ShipGeometry.scanShip(new BlockPos(0, 0, 0), 8,
                solid::contains, probe(solid));
        return new ShipState(geometry, MachineCount.EMPTY);
    }

    @Test
    @DisplayName("平移后 bodyOf 用新坐标，旧坐标失效")
    void shiftedSimulationFollowsNewGeometry() {
        ShipState state = state();
        assertEquals(0, state.simulation().bodyOf(new BlockPos(0, 0, 0)));

        ShipState moved = state.shifted(5, 0, 0);
        assertEquals(-1, moved.simulation().bodyOf(new BlockPos(0, 0, 0)),
                "旧坐标不能再映射到舱室");
        assertEquals(0, moved.simulation().bodyOf(new BlockPos(5, 0, 0)),
                "新坐标必须映射到同一个舱室");
        assertTrue(moved.geometry().hull().contains(new BlockPos(8, 0, 0)),
                "几何也要平移到新位置");
    }

    @Test
    @DisplayName("平移后泡水判定按新坐标的水位")
    void shiftedWaterLevelUsesNewCells() {
        ShipState state = state();
        // 舱内 5×5×5；灌满底层 25 格 → 水面在 y=-2 顶面之上。
        state.simulation().flood(0, 25.0D * ShipSimulation.UNITS_PER_BLOCK);
        double level = state.simulation().waterLevelAt(new BlockPos(0, -2, 0), List.of());
        assertTrue(level > -2.0D, "底层应该已经进水");

        ShipState moved = state.shifted(5, 0, 0);
        assertEquals(level, moved.simulation().waterLevelAt(new BlockPos(5, -2, 0), List.of()),
                1.0E-6D, "平移不改变水量/水位");
        assertEquals(0.0D, moved.simulation().waterLevelAt(new BlockPos(0, -2, 0), List.of()),
                "旧坐标已经不在舱室里");
    }
}
