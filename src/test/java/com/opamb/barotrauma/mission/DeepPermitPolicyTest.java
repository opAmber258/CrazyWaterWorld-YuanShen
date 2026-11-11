package com.opamb.barotrauma.mission;

import com.opamb.barotrauma.ship.ShipPressure;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 深潜许可门槛与潜水服耐压策略（纯数值）。 */
class DeepPermitPolicyTest {

    @Test
    void 任务池里只有深水任务需要许可() {
        for (Mission mission : com.opamb.barotrauma.world.PoiNetwork.definitions()) {
            boolean expected = -mission.target.getY() > 320;
            assertEquals(expected, mission.requiresDeepPermit(),
                    "任务 #" + mission.id + " 深度 " + -mission.target.getY() + "m");
        }
    }

    @Test
    void 门槛按目标深度判定且B中继站不受影响() {
        assertFalse(new Mission(9, MissionType.DELIVERY,
                new BlockPos(0, -301, 0), 12, 150).requiresDeepPermit(), "B 中继站 301m");
        assertTrue(new Mission(9, MissionType.SALVAGE,
                new BlockPos(0, -401, 0), 20, 200).requiresDeepPermit(), "401m");
        assertFalse(new Mission(9, MissionType.SALVAGE,
                new BlockPos(0, -320, 0), 20, 200).requiresDeepPermit(), "正好 320m 不需要");
        assertTrue(new Mission(9, MissionType.SALVAGE,
                new BlockPos(0, -321, 0), 20, 200).requiresDeepPermit(), "321m 需要");
    }

    @Test
    void 潜水服按原作把出舱安全深度提升到4000m() {
        assertEquals(300, ShipPressure.PLAYER_SAFE_DEPTH);
        // 原作 Content/Items/Diving/divinggear.xml 的 PressureProtection=4000；
        // 寒冷深穴约 1000–2300m，常规潜水服全覆盖。
        assertEquals(4000, ShipPressure.DIVING_SUIT_SAFE_DEPTH);
        assertTrue(ShipPressure.DIVING_SUIT_SAFE_DEPTH > ShipPressure.PLAYER_SAFE_DEPTH);
    }
}
