package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 破口空腔仍然是"有空气的房间"：
 * <ul>
 *   <li>几何的 interiorCells 被外部洪泛裁小/裁空时，逐格场要用历史舱内格；</li>
 *   <li>人站在连海空腔的水面以上时，可用氧不应是 0。</li>
 * </ul>
 */
class ShipPocketAirTest {

    private static Set<BlockPos> fullRoom() {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                cells.add(new BlockPos(x, 0, z));
            }
        }
        return cells;
    }

    private static ShipState breachedRoom() {
        Set<BlockPos> cells = fullRoom();
        Set<BlockPos> clipped = Set.of(new BlockPos(0, 0, 0));
        ShipGeometry.Compartment pocket = new ShipGeometry.Compartment(cells, 0, clipped);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(), List.of(pocket), 0, cells.size(),
                new ShipGeometry.Bounds(0, 0, 0, 4, 0, 4),
                Set.of(), List.of(), Set.of());
        ShipState state = new ShipState(geometry, MachineCount.EMPTY);
        // 破口前这间房是完整舱室；历史舱内格还没被外部洪泛清掉。
        state.restoreKnownInterior(cells);
        return state;
    }

    @Test
    @DisplayName("interiorCells 被裁成 1 格时，逐格场仍覆盖历史完整房间")
    void fieldUsesKnownInteriorWhenClipped() {
        ShipState state = breachedRoom();
        assertEquals(25 * ShipSimulation.UNITS_PER_BLOCK,
                state.simulation().fluid().bodyCapacity(0), 1.0E-6D);
    }

    @Test
    @DisplayName("连海空腔水面以上的可用氧是 100，不是 0")
    void openPocketAirIsBreathable() {
        ShipState state = breachedRoom();
        assertEquals(100.0D, state.simulation().oxygenPercent(0), 1.0E-6D);
    }

    @Test
    @DisplayName("水在历史完整房间里能横向铺开，不会卡在破口几格")
    void waterSpreadsAcrossKnownRoom() {
        ShipState state = breachedRoom();
        state.simulation().fluid().addBody(0, 500.0D, Double.POSITIVE_INFINITY);
        for (int i = 0; i < 60; i++) {
            state.simulation().tickWaterFlow(0.1D);
        }
        assertTrue(state.simulation().fluid().waterAt(new BlockPos(4, 0, 4)) > 0.0D,
                "远端格也应该有薄薄一层水");
    }
}
