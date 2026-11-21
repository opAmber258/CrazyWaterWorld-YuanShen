package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 门洞的两侧水体：开着的门由逐格场的压力边过水，关着的门完全阻断。
 */
class ShipFluidDoorTest {

    private static ShipState state(boolean open) {
        BlockPos door = new BlockPos(1, 0, 0);
        ShipGeometry.Compartment left = new ShipGeometry.Compartment(
                Set.of(new BlockPos(0, 0, 0), door), 0);
        ShipGeometry.Compartment right = new ShipGeometry.Compartment(
                Set.of(new BlockPos(2, 0, 0)), 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(left, right), List.of(), 0, 3,
                new ShipGeometry.Bounds(0, 0, 0, 2, 0, 0),
                Set.of(), List.of(new ShipGeometry.DoorLink(door, 0, 1, open)), Set.of());
        return new ShipState(geometry, MachineCount.EMPTY);
    }

    @Test
    @DisplayName("开着的门让水从高侧流到低侧，水量守恒")
    void openDoorPassesWater() {
        ShipState state = state(true);
        state.simulation().flood(0, 100.0D);
        for (int i = 0; i < 60; i++) {
            state.simulation().tickWaterFlow(0.1D);
        }
        assertTrue(state.simulation().waterUnits(1) > 20.0D, "开着的门应该让水过去");
        assertEquals(100.0D, state.simulation().waterUnits(0)
                + state.simulation().waterUnits(1), 1.0E-6D, "过水不能凭空增减");
    }

    @Test
    @DisplayName("关着的门完全阻断水流")
    void closedDoorBlocksWater() {
        ShipState state = state(false);
        state.simulation().flood(0, 100.0D);
        for (int i = 0; i < 60; i++) {
            state.simulation().tickWaterFlow(0.1D);
        }
        assertEquals(0.0D, state.simulation().waterUnits(1), 1.0E-6D);
        assertEquals(100.0D, state.simulation().waterUnits(0), 1.0E-6D);
    }
}
