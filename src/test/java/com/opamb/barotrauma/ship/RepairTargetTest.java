package com.opamb.barotrauma.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 原作 Repairable：低于 80% 才能修，持续 25 秒（低技能档），QTE +3。 */
class RepairTargetTest {

    private static RepairTarget target(double condition) {
        return new RepairTarget() {
            @Override
            public double condition() {
                return condition;
            }

            @Override
            public double maxCondition() {
                return 100.0D;
            }

            @Override
            public void applyRepair(double points) {
            }
        };
    }

    @Test
    void 低于八十才能修() {
        assertTrue(target(79.0D).canRepair());
        assertFalse(target(80.0D).canRepair());
        assertFalse(target(81.0D).canRepair());
    }

    @Test
    void 满耐久判定() {
        assertFalse(target(99.99D).isFull());
        assertTrue(target(100.0D).isFull());
    }

    @Test
    void 耐久比例() {
        assertEquals(0.5D, target(50.0D).conditionFraction(), 1.0E-9D);
        assertEquals(1.0D, target(120.0D).conditionFraction(), 1.0E-9D);
    }

    @Test
    void electricalAndMechanicalRepairsDefaultToSkill55() {
        assertEquals(55, target(50.0D).requiredSkill());
    }

    @Test
    void 反应堆阈值可以覆盖成五十() {
        RepairTarget reactor = new RepairTarget() {
            @Override
            public double condition() {
                return 60.0D;
            }

            @Override
            public double maxCondition() {
                return 100.0D;
            }

            @Override
            public double repairThreshold() {
                return 0.5D;
            }

            @Override
            public void applyRepair(double points) {
            }
        };
        assertFalse(reactor.canRepair());
        assertTrue(target(60.0D).canRepair());
    }
}
