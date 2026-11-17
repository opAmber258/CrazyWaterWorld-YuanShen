package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门/舱口的水量判定：只看直接相邻的水体，不能穿过一格实心隔墙读到
 * 另一侧独立舱室的水，否则干舱之间的门会在水密模式下被误关。
 */
class AutomationWaterTest {

    private static final BlockPos DOOR = new BlockPos(0, 0, 0);
    private static final BlockPos WALL = new BlockPos(1, 0, 0);
    private static final BlockPos FAR_ROOM = new BlockPos(2, 0, 0);
    private static final BlockPos NEAR_ROOM = new BlockPos(-1, 0, 0);

    @Test
    @DisplayName("隔墙另一侧舱室进水不影响门的水量判定")
    void waterBehindWallDoesNotCount() {
        Set<BlockPos> solid = new HashSet<>();
        for (int x = -8; x <= 8; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    solid.add(new BlockPos(x, y, z));
                }
            }
        }
        // 门格和两个独立舱室挖空；(1,0,0) 保留为实心隔墙。
        solid.remove(DOOR);
        solid.remove(NEAR_ROOM);
        solid.remove(FAR_ROOM);

        Set<BlockPos> doors = Set.of(DOOR);
        ShipGeometry.Probe probe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return solid.contains(pos);
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
                return false;
            }
        };
        ShipGeometry.Result geometry = ShipGeometry.scanShip(WALL, 8, solid::contains, probe);
        assertEquals(2, geometry.compartments().size(), "门两侧各一个独立舱室");

        ShipState ship = new ShipState(geometry, MachineCount.EMPTY);
        int far = -1;
        for (int i = 0; i < geometry.compartments().size(); i++) {
            if (geometry.compartments().get(i).cells().contains(FAR_ROOM)) {
                far = i;
            }
        }
        assertTrue(far >= 0, "远舱必须被识别为独立水体");
        ship.simulation().addWater(far, 1.0E9D);
        assertTrue(ship.simulation().bodyWaterFraction(far) > 0.9D, "远舱已经进水");

        assertEquals(0.0D, AutomationUtil.maxWaterFraction(ship, DOOR), 1.0E-9D,
                "门只邻接干舱和实心隔墙，不能读到隔墙后的水");
    }
}
