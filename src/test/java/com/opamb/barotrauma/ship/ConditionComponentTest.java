package com.opamb.barotrauma.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通用耐久组件：宽限、劣化、下限、修理。 */
class ConditionComponentTest {

    @Test
    void 活动时按每秒速度掉耐久() {
        ConditionComponent condition = new ConditionComponent(100.0D, 1.0D, 0, 0, 0.0D, 0.8D);
        for (int i = 0; i < 20; i++) {
            condition.tick(null, true);
        }
        assertEquals(99.0D, condition.condition(), 1.0E-9D);
    }

    @Test
    void 不活动不劣化() {
        ConditionComponent condition = new ConditionComponent(100.0D, 1.0D, 0, 0, 0.0D, 0.8D);
        for (int i = 0; i < 100; i++) {
            condition.tick(null, false);
        }
        assertEquals(100.0D, condition.condition(), 1.0E-9D);
    }

    @Test
    void 不会低于最小劣化耐久() {
        ConditionComponent condition = new ConditionComponent(100.0D, 100.0D, 0, 0, 10.0D, 0.8D);
        for (int i = 0; i < 200; i++) {
            condition.tick(null, true);
        }
        assertEquals(10.0D, condition.condition(), 1.0E-9D);
    }

    @Test
    void 修理到满并重置比例() {
        ConditionComponent condition = new ConditionComponent(100.0D, 1.0D, 0, 0, 0.0D, 0.8D);
        condition.damage(50.0D);
        assertEquals(0.5D, condition.fraction(), 1.0E-9D);
        assertTrue(condition.canRepair());
        condition.repair(60.0D);
        assertEquals(100.0D, condition.condition(), 1.0E-9D);
        assertTrue(condition.isFull());
        assertFalse(condition.canRepair());
    }

    @Test
    void 修理阈值按比例判定() {
        ConditionComponent condition = new ConditionComponent(100.0D, 0.0D, 0, 0, 0.0D, 0.8D);
        condition.damage(21.0D); // 79%
        assertTrue(condition.canRepair());
        condition.repair(1.0D); // 80%
        assertFalse(condition.canRepair());
        condition.repair(1.0D); // 81%
        assertFalse(condition.canRepair());
    }

    @Test
    void 损坏判定为零() {
        ConditionComponent condition = new ConditionComponent(100.0D, 0.0D, 0, 0, 0.0D, 0.8D);
        condition.damage(100.0D);
        assertTrue(condition.broken());
        assertEquals(0.0D, condition.fraction(), 1.0E-9D);
    }

    @Test
    void 低耐久耗电惩罚() {
        ConditionComponent condition = new ConditionComponent(100.0D, 0.0D, 0, 0, 0.0D, 0.8D);
        assertEquals(1.0D, condition.powerPenalty(), 1.0E-9D);
        condition.damage(70.0D); // 30% 耐久
        // 原作 Repairable.AdjustPowerConsumption：Lerp(1.5, 1, 0.3) = 1.35
        assertEquals(1.35D, condition.powerPenalty(), 1.0E-9D);
        condition.damage(30.0D); // 0%
        assertEquals(1.5D, condition.powerPenalty(), 1.0E-9D);
    }

    @Test
    void oldFullDeviceWithoutGraceGetsItsInitialDelay() {
        var saved = new net.minecraft.nbt.CompoundTag();
        saved.putDouble("Condition", 100);
        saved.putLong("ConditionGrace", 0);
        var device = new ConditionComponent(100, 1, 2, 2, 0, 0.8);
        device.load(saved, "Condition");

        device.tick(null, true, 2, 0.5);
        device.tick(null, true, 2, 0.5);
        assertEquals(100, device.condition(), 1.0E-9);
        device.tick(null, true, 1, 0.5);
        assertEquals(99, device.condition(), 1.0E-9);
    }

    @Test
    void startingANewVoyageRenewsGraceWithoutRepairingPreviousWear() {
        var device = new ConditionComponent(100, 1, 2, 2, 0, 0.8);
        device.tick(null, true, 2);
        device.tick(null, true, 10);
        assertEquals(90, device.condition(), 1.0E-9);

        device.renewWearGrace();
        device.tick(null, true, 2);
        assertEquals(90, device.condition(), 1.0E-9);
        device.tick(null, true, 1);
        assertEquals(89, device.condition(), 1.0E-9);
    }
}
