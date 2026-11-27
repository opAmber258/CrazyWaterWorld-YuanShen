package com.opamb.barotrauma.world.shipyard;

import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipyardPlotTest {

    @Test
    void plotIndexAndOriginFollowSpacing() {
        int x = ModDimensions.SHIPYARD_ORIGIN_X + 3 * ModDimensions.SHIPYARD_PLOT_SPACING;
        assertEquals(3, ShipyardPlot.plotIndexAt(x));
        assertEquals(0, ShipyardPlot.plotIndexAt(ModDimensions.SHIPYARD_ORIGIN_X));
    }

    @Test
    void plotRecordKeepsIdentityAndReturnPoint() {
        UUID player = UUID.randomUUID();
        ShipyardSavedData data = new ShipyardSavedData();
        ShipyardSavedData.Plot first = data.allocate(player);
        ShipyardSavedData.Plot again = data.allocate(player);
        assertEquals(first, again);
        assertEquals(0, first.index);
        assertEquals(ModDimensions.SHIPYARD_ORIGIN_X, first.originX());

        first.shipId = "talos";
        first.dirty = true;
        CompoundTag tag = first.save();
        ShipyardSavedData.Plot loaded = ShipyardSavedData.Plot.load(tag);
        assertEquals(player, loaded.player);
        assertEquals(first.index, loaded.index);
        assertEquals("talos", loaded.shipId);
        assertTrue(loaded.dirty);
    }
}
