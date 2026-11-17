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

    @Test
    void 满电电池按额定输出() {
        assertEquals(1000.0D, PowerGrid.batteryOutputAvailable(
                2000.0D, 2000.0D, 1000.0D, 0.1D), 1.0E-9D);
    }

    @Test
    void 电量低于一成时线性降额() {
        // 5% 电量 = 0.5 × 额定输出
        assertEquals(500.0D, PowerGrid.batteryOutputAvailable(
                100.0D, 2000.0D, 1000.0D, 0.1D), 1.0E-9D);
        // 2% 电量 = 0.2 × 额定输出
        assertEquals(200.0D, PowerGrid.batteryOutputAvailable(
                40.0D, 2000.0D, 1000.0D, 0.1D), 1.0E-9D);
    }

    @Test
    void 单步可用能量限制输出() {
        // 10 kW·min / 1 s = 600 kW，低于额定 10000 kW
        assertEquals(600.0D, PowerGrid.batteryOutputAvailable(
                10.0D, 20.0D, 10000.0D, 1.0D), 1.0E-9D);
    }

    @Test
    void 空电池不能输出() {
        assertEquals(0.0D, PowerGrid.batteryOutputAvailable(
                0.0D, 2000.0D, 1000.0D, 0.1D), 1.0E-9D);
        assertEquals(0.0D, PowerGrid.batteryOutputAvailable(
                -5.0D, 2000.0D, 1000.0D, 0.1D), 1.0E-9D);
    }

    @Test
    void 电池充电按效率折算成千瓦分钟() {
        // 500 kW 充 60 秒、效率 0.95 = 475 kW·min
        assertEquals(475.0D, PowerGrid.batteryChargeDeltaKwm(
                500.0D, 60.0D, 0.95D), 1.0E-9D);
        // 一个 0.1 s 模拟步
        assertEquals(0.7916666666666666D, PowerGrid.batteryChargeDeltaKwm(
                500.0D, 0.1D, 0.95D), 1.0E-9D);
        assertEquals(0.0D, PowerGrid.batteryChargeDeltaKwm(
                0.0D, 0.1D, 0.95D), 1.0E-9D);
    }
}
