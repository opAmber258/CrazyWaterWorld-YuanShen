package com.opamb.barotrauma.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepBrightnessDebugTest {
    @Test
    void cyclesAllFiveBrightnessLevelsAndKeepsFogSetting() {
        var settings = new DeepBrightnessDebug.Settings(0, true);
        for (int expected : new int[]{25, 50, 75, 100, 0}) {
            settings = settings.afterUse(false);
            assertEquals(expected, settings.brightness());
            assertTrue(settings.noSeaFog());
        }
    }

    @Test
    void togglesFogBothWaysWithoutResettingBrightness() {
        for (int brightness : new int[]{0, 25, 50, 75, 100}) {
            var clear = new DeepBrightnessDebug.Settings(brightness, false).afterUse(true);
            assertTrue(clear.noSeaFog());
            assertEquals(brightness, clear.brightness());
            var restored = clear.afterUse(true);
            assertFalse(restored.noSeaFog());
            assertEquals(brightness, restored.brightness());
        }
    }
}
