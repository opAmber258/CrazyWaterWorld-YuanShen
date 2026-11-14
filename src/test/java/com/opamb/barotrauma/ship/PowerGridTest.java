package com.opamb.barotrauma.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 原版电网公式：Voltage = 供电 / 负载；设备按 MinVoltage 工作。 */
class PowerGridTest {

    @Test
    void 供电刚好等于负载时电压为一() {
        assertEquals(1.0D, PowerGrid.voltage(1000.0D, 1000.0D), 1.0E-9D);
    }

    @Test
    void 欠压是小于一而不是零() {
        assertEquals(0.5D, PowerGrid.voltage(500.0D, 1000.0D), 1.0E-9D);
        assertTrue(PowerGrid.canRun(0.5D, 0.5D));
        assertTrue(PowerGrid.canRun(0.5D, 0.3D));
        assertFalse(PowerGrid.canRun(0.29D, 0.3D));
    }

    @Test
    void 过压不封顶一() {
        assertEquals(2.5D, PowerGrid.voltage(2500.0D, 1000.0D), 1.0E-9D);
        assertTrue(PowerGrid.overvoltage(2.5D, 1.7D));
        assertFalse(PowerGrid.overvoltage(1.2D, 1.7D));
    }

    @Test
    void 负载为零时有堆取一没堆取零() {
        assertEquals(1.0D, PowerGrid.voltage(5000.0D, 0.0D), 1.0E-9D);
        assertEquals(0.0D, PowerGrid.voltage(0.0D, 0.0D), 1.0E-9D);
        assertEquals(0.0D, PowerGrid.voltage(0.0D, 1000.0D), 1.0E-9D);
    }

    @Test
    void 设备效率按电压钳到二() {
        assertEquals(2.0D, PowerGrid.effectiveFactor(5.0D), 1.0E-9D);
        assertEquals(0.0D, PowerGrid.effectiveFactor(-1.0D), 1.0E-9D);
        assertEquals(1.5D, PowerGrid.effectiveFactor(1.5D), 1.0E-9D);
    }
}
