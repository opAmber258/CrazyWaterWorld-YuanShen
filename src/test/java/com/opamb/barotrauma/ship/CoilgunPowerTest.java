package com.opamb.barotrauma.ship;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilgunPowerTest {
    @Test
    void fiveFactoryBatteriesAreBelowTheInstantaneousOutputThreshold() {
        var battery = new CoilgunPower.Source(2000, 1000, 2000);
        assertFalse(CoilgunPower.canFire(List.of(battery, battery, battery, battery, battery), 1000));
        assertTrue(CoilgunPower.canFire(List.of(battery, new CoilgunPower.Source(1, 20_000_000, 20)), 1000));
    }

    @Test
    void nearlyEmptySourceContributesItsRemainingChargeAndOtherSourcesMakeUpTheDeficit() {
        var low = new CoilgunPower.Source(0.01, 1000, 2000);
        var capacitor = new CoilgunPower.Source(1, 20_000_000, 20);
        var sources = List.of(low, capacitor);
        assertTrue(CoilgunPower.canFire(sources, 1000));
        double[] shares = CoilgunPower.pulseShare(sources, 1000);
        assertEquals(0.01, shares[0], 1e-9);
        assertEquals(1000.0 / 3600 - 0.01, shares[1], 1e-9);
    }
}
