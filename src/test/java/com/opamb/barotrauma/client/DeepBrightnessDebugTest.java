package com.opamb.barotrauma.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeepBrightnessDebugTest {
    @Test
    void cyclesAllFiveBrightnessLevels() {
        int current = 0;
        for (int expected : new int[]{25, 50, 75, 100, 0}) {
            current = DeepBrightnessDebug.nextPercent(current, false);
            assertEquals(expected, current);
        }
    }

    @Test
    void resetReturnsToNormalDarknessFromAnyLevel() {
        for (int current : new int[]{0, 25, 50, 75, 100}) {
            assertEquals(0, DeepBrightnessDebug.nextPercent(current, true));
        }
    }
}
