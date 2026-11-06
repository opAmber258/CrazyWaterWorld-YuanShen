package com.opamb.barotrauma.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 旧档泵状态迁移：明确关掉的泵必须保持停机（issue #22）。 */
class DrainPumpMigrationTest {

    @Test
    void 旧档明确关泵迁移后保持停机() {
        int[] r = DrainPumpBlockEntity.migrateLegacy(
                true, false, false, true, false, 0, true, 300, 600);
        assertEquals(0, r[0], "应切到手动");
        assertEquals(0, r[1], "流量必须为 0");
    }

    @Test
    void 旧档开泵按档位换算并切手动() {
        int[] r = DrainPumpBlockEntity.migrateLegacy(
                true, true, false, true, false, 0, true, 300, 600);
        assertEquals(0, r[0]);
        assertEquals(-50, r[1], "300/600 = 50% 泵出");
    }

    @Test
    void 新格式优先于旧档位() {
        int[] r = DrainPumpBlockEntity.migrateLegacy(
                false, true, true, false, true, -50, true, 300, 600);
        assertEquals(0, r[0]);
        assertEquals(-50, r[1]);
    }

    @Test
    void 全新方块默认自动泵出() {
        int[] r = DrainPumpBlockEntity.migrateLegacy(
                false, true, false, true, false, 0, false, 0, 600);
        assertEquals(1, r[0]);
        assertEquals(-100, r[1]);
    }
}
