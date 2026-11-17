package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AutomationUtil#opensToSea} 的纯逻辑单测。
 *
 * <p>水密门要判断“门外是不是海”：除了门格直接开在包围盒边界（SEA_BODY），
 * 船壳有突出部分时外门会落在包围盒<b>内部</b>，门外空气变成连海空腔
 * （openPocket）。只认 SEA_BODY 会让这扇门在空腔水量为 0 时被自动打开。</p>
 */
class AutomationUtilTest {

    private static ShipGeometry.Probe probe(Set<BlockPos> solid, Set<BlockPos> doors) {
        return new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return solid.contains(pos) && !doors.contains(pos);
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }

            @Override
            public boolean isDoorway(BlockPos pos) {
                return doors.contains(pos);
            }
        };
    }

    private static Set<BlockPos> hollowCube(int half) {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    if (Math.abs(x) == half || Math.abs(y) == half || Math.abs(z) == half) {
                        solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return solid;
    }

    @Test
    @DisplayName("凹进包围盒的外门：门外连海空腔也算通海")
    void recessedOuterDoorOpensToSea() {
        // 7³ 空壳（-3..3）+ 外门 (3,0,0)；船壳在 x=4..5 有一片突出，
        // 包围盒因此扩到 x=5，门外空气 (4,0,0) 落在包围盒内部，
        // 几何里只能记成 openPocket，而不是 SEA_BODY。
        Set<BlockPos> solid = hollowCube(3);
        for (int x = 4; x <= 5; x++) {
            for (int y = -1; y <= 1; y++) {
                solid.add(new BlockPos(x, y, 3));
            }
        }
        BlockPos door = new BlockPos(3, 0, 0);
        Set<BlockPos> doors = Set.of(door);

        ShipGeometry.Result geometry = ShipGeometry.scanShip(
                BlockPos.ZERO, 8, solid::contains, probe(solid, doors), 0, null,
                pos -> false);
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);

        assertFalse(geometry.compartments().isEmpty(), "船内应该还有一个密闭舱室");
        assertFalse(geometry.openPockets().isEmpty(), "门外应该是连海空腔");
        assertTrue(AutomationUtil.opensToSea(state, door),
                "门外是包围盒内的连海空腔，水密模式必须把它当外门");
    }

    @Test
    @DisplayName("舱内隔门不通海")
    void interiorDoorDoesNotOpenToSea() {
        // 7³ 空壳 + x=0 的整面隔墙，隔墙中心开一扇门；两侧都是密闭舱室。
        Set<BlockPos> solid = hollowCube(3);
        for (int y = -2; y <= 2; y++) {
            for (int z = -2; z <= 2; z++) {
                solid.add(new BlockPos(0, y, z));
            }
        }
        BlockPos door = new BlockPos(0, 0, 0);
        Set<BlockPos> doors = Set.of(door);

        ShipGeometry.Result geometry = ShipGeometry.scanShip(
                BlockPos.ZERO, 8, solid::contains, probe(solid, doors), 0, null,
                pos -> false);
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);

        assertFalse(AutomationUtil.opensToSea(state, door),
                "隔墙两侧都是密闭舱室，不是外门");
    }
}
