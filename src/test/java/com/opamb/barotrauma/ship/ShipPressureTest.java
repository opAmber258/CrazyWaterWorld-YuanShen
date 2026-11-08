package com.opamb.barotrauma.ship;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 船壳耐压深度按原作数值（基础 3500m，加固每级 +15%）。 */
class ShipPressureTest {

    private static ShipState emptyShip() {
        ShipGeometry.Probe probe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                return false;
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }
        };
        return new ShipState(ShipGeometry.scanShip(BlockPos.ZERO, 4, p -> false, probe),
                MachineCount.EMPTY);
    }

    @Test
    @DisplayName("基础耐压 3500m，每级加固 +15%（原作 Structure.CrushDepth / UpgradeModule）")
    void crushDepthFollowsOriginal() {
        ShipState state = emptyShip();
        assertEquals(3500, ShipPressure.ratingMeters(state), "基础耐压应为原作的 3500m");
        state.setHullHealthMultiplier(1.2F);
        assertEquals(4025, ShipPressure.ratingMeters(state));
        state.setHullHealthMultiplier(1.4F);
        assertEquals(4550, ShipPressure.ratingMeters(state));
        state.setHullHealthMultiplier(1.6F);
        assertEquals(5075, ShipPressure.ratingMeters(state));
    }
}
