package com.opamb.barotrauma.ship;

/**
 * 原版电网的纯数学部分，方便单测。
 *
 * <p>原作 {@code Powered.UpdatePower}：每个 grid 的
 * {@code Voltage = 总供电 / 总负载}，不封顶 1；设备在
 * {@code Voltage >= MinVoltage} 时才会正常工作。负载为 0 时电压取 1（有堆）
 * 或 0（没堆），避免除零。</p>
 */
public final class PowerGrid {

    /** 电压上限只用于防止数值爆炸；原版实际也会把极端值钳到 1000。 */
    public static final double MAX_VOLTAGE = 1000.0D;

    private PowerGrid() {
    }

    public static double voltage(double supplyKw, double loadKw) {
        if (!Double.isFinite(supplyKw) || supplyKw <= 0.0D) {
            return 0.0D;
        }
        if (!Double.isFinite(loadKw) || loadKw <= 1.0E-9D) {
            return 1.0D;
        }
        return Math.min(MAX_VOLTAGE, supplyKw / loadKw);
    }

    public static boolean canRun(double voltage, double minVoltage) {
        return voltage + 1.0E-9D >= Math.max(0.0D, minVoltage);
    }

    /** 引擎/泵按原作把电压钳到 2 再算效率；0 以下按 0。 */
    public static double effectiveFactor(double voltage) {
        return Math.min(Math.max(voltage, 0.0D), 2.0D);
    }

    public static boolean overvoltage(double voltage, double threshold) {
        return voltage > Math.max(1.0D, threshold);
    }
}
