package com.opamb.barotrauma.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 引擎升级线：级数/倍率换算（ShipState 静态策略）。 */
class EngineUpgradeTest {

    @Test
    void 倍率反推级数() {
        assertEquals(0, ShipState.engineLevel(1.0F));
        assertEquals(1, ShipState.engineLevel(1.2F));
        assertEquals(2, ShipState.engineLevel(1.4F));
        assertEquals(3, ShipState.engineLevel(1.6F));
    }

    @Test
    void 级数算倍率且夹紧到上限() {
        assertEquals(1.0F, ShipState.engineMultiplierForLevel(0), 1.0E-6);
        assertEquals(1.2F, ShipState.engineMultiplierForLevel(1), 1.0E-6);
        assertEquals(1.6F, ShipState.engineMultiplierForLevel(3), 1.0E-6);
        assertEquals(1.6F, ShipState.engineMultiplierForLevel(5), 1.0E-6, "超过上限按上限");
        assertEquals(1.0F, ShipState.engineMultiplierForLevel(-1), 1.0E-6, "负级按 0");
    }

    @Test
    void 升级花费随级数递增() {
        // 与 MissionActionPacket 的 COST_UPGRADE_BASE 保持同一口径
        int base = 150;
        assertEquals(150, base * (ShipState.engineLevel(1.0F) + 1));
        assertEquals(300, base * (ShipState.engineLevel(1.2F) + 1));
        assertEquals(450, base * (ShipState.engineLevel(1.4F) + 1));
    }
}
