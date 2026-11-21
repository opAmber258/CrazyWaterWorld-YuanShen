package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 逐格水位的 NBT 往返：读档后水量不能凭空归零。 */
class ShipWaterNbtTest {

    private static ShipState state() {
        ShipGeometry.Compartment room = new ShipGeometry.Compartment(
                Set.of(new BlockPos(0, 0, 0), new BlockPos(0, 1, 0)), 0);
        ShipGeometry.Result geometry = new ShipGeometry.Result(
                List.of(room), List.of(), 0, 2,
                new ShipGeometry.Bounds(0, 0, 0, 0, 1, 0),
                Set.of(), List.of(), Set.of());
        return new ShipState(geometry, MachineCount.EMPTY);
    }

    @Test
    @DisplayName("saveWaterNbt/readWaterNbt 保留每格水量")
    void waterNbtRoundTrip() {
        ShipState original = state();
        original.simulation().flood(0, 150.0D);
        ListTag tag = original.simulation().saveWaterNbt();
        Map<BlockPos, Double> read = ShipSimulation.readWaterNbt(tag);

        assertEquals(150.0D, read.values().stream().mapToDouble(Double::doubleValue).sum(),
                1.0E-3D);

        ShipState restored = state();
        restored.simulation().seedWaterByCell(read, restored.geometry());
        assertEquals(original.simulation().waterUnits(), restored.simulation().waterUnits(),
                1.0E-3D);
    }
}
