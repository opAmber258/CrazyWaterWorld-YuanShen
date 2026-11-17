package com.opamb.barotrauma.ship;

/**
 * 门/舱口的内置自动化模式（替代原版 motion detector + 逻辑门）。
 */
public enum AutomationMode {
    MANUAL,
    MOTION,
    WATERTIGHT;

    public AutomationMode next() {
        AutomationMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String translationKey() {
        return "automation.mode." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
