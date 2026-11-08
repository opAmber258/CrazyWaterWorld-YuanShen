package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对接舱口的水密性回归：潜艇顶部的 3×1 水平舱口打开时，不能把艇内判成"连海"。
 *
 * <p>舱口格在水体扫描里永远是 doorway（水密隔断），开合只影响 DoorLink 的开关位。
 * 如果哪天有人把舱口从 doorway 里删掉，这个测试会立刻抓到"一开舱口就进水"。</p>
 */
class DockingHatchWatertightTest {

    /** 和 StarterSub 一致的 9×5×15 船壳；顶部 3×1 换成打开的舱口。 */
    private static Set<BlockPos> hullWithOpenHatch() {
        Set<BlockPos> hull = new HashSet<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -76; y <= -72; y++) {
                for (int z = -7; z <= 7; z++) {
                    boolean shell = x == -4 || x == 4 || y == -76 || y == -72 || z == -7 || z == 7;
                    if (shell) {
                        hull.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return hull;
    }

    private static Set<BlockPos> hatchCells() {
        Set<BlockPos> doors = new HashSet<>();
        for (int x = -1; x <= 1; x++) {
            doors.add(new BlockPos(x, -72, 0));
        }
        return doors;
    }

    @Test
    void 打开的顶部舱口不会把艇内判成连海() {
        Set<BlockPos> hull = hullWithOpenHatch();
        Set<BlockPos> doors = hatchCells();
        ShipGeometry.Probe probe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return hull.contains(pos);
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }

            @Override
            public boolean isDoorway(BlockPos pos) {
                return doors.contains(pos);
            }

            @Override
            public boolean isOpenDoorway(BlockPos pos) {
                return doors.contains(pos);
            }
        };
        ShipGeometry.Result geometry = ShipGeometry.scanShip(
                new BlockPos(1, -75, -3), 32, hull::contains, probe);

        assertTrue(geometry.sealed(), "打开舱口不能产生连海空腔");
        assertEquals(0, geometry.openPockets().size());
        assertEquals(1, geometry.compartments().size(), "艇内仍然是一个密封舱室");
        assertTrue(geometry.doorLinks().isEmpty(), "舱口外没有水体，不应该有 DoorLink");
    }
}
