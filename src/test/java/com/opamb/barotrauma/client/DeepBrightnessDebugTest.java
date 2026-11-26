package com.opamb.barotrauma.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepBrightnessDebugTest {

    @BeforeEach
    void resetSettings() {
        DeepBrightnessDebug.reset();
    }

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

    @Test
    void modAmbientClampsToZeroHundred() {
        DeepBrightnessDebug.setPercent(-10);
        assertEquals(0, DeepBrightnessDebug.percent());
        DeepBrightnessDebug.setPercent(150);
        assertEquals(100, DeepBrightnessDebug.percent());
        DeepBrightnessDebug.setPercent(37);
        assertEquals(37, DeepBrightnessDebug.percent());
    }

    @Test
    void vanillaAmbientClampsToZeroTwoTenths() {
        DeepBrightnessDebug.setVanillaAmbient(-1.0F);
        assertEquals(0.0F, DeepBrightnessDebug.vanillaAmbient(), 1.0E-6F);
        DeepBrightnessDebug.setVanillaAmbient(1.0F);
        assertEquals(0.2F, DeepBrightnessDebug.vanillaAmbient(), 1.0E-6F);
        DeepBrightnessDebug.setVanillaAmbient(0.15F);
        assertEquals(0.15F, DeepBrightnessDebug.vanillaAmbient(), 1.0E-6F);
    }

    @Test
    void waterAndFogOnlyHiddenInVanillaModeWithRestoreOn() {
        assertFalse(DeepBrightnessDebug.hidesWaterAndFog());
        DeepBrightnessDebug.setMode(DeepBrightnessDebug.Mode.VANILLA);
        assertTrue(DeepBrightnessDebug.hidesWaterAndFog());
        DeepBrightnessDebug.setVanillaRestoresWater(false);
        assertFalse(DeepBrightnessDebug.hidesWaterAndFog());
        DeepBrightnessDebug.setMode(DeepBrightnessDebug.Mode.MOD);
        DeepBrightnessDebug.setVanillaRestoresWater(true);
        assertFalse(DeepBrightnessDebug.hidesWaterAndFog());
    }

    @Test
    void resetRestoresModeAndDefaults() {
        DeepBrightnessDebug.setMode(DeepBrightnessDebug.Mode.VANILLA);
        DeepBrightnessDebug.setPercent(75);
        DeepBrightnessDebug.setVanillaAmbient(0.2F);
        DeepBrightnessDebug.setVanillaRestoresWater(false);

        DeepBrightnessDebug.reset();

        assertEquals(DeepBrightnessDebug.Mode.MOD, DeepBrightnessDebug.mode());
        assertEquals(0, DeepBrightnessDebug.percent());
        assertEquals(0.1F, DeepBrightnessDebug.vanillaAmbient(), 1.0E-6F);
        assertTrue(DeepBrightnessDebug.vanillaRestoresWater());
    }
}
