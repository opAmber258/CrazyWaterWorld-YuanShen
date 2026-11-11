package com.opamb.barotrauma.equipment;

import com.opamb.barotrauma.medical.affliction.AfflictionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 原作 {@code damagemodifier} 数值锁定（本机 Barotrauma 1.13.5 Content）。
 *
 * <p>重点是"护甲没有点数"：只有这些按病症的倍率。改数值时先对照原作 XML。</p>
 */
class ArmorModifierTableTest {

    private static final double EPS = 1.0E-9;

    @Test
    void 潜水服数值来自原作() {
        assertEquals(0.9, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.DIVING_SUIT, AfflictionType.BLUNT_TRAUMA), EPS);
        assertEquals(0.9, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.DIVING_SUIT, AfflictionType.BLEEDING), EPS);
        assertEquals(0.75, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.DIVING_SUIT, AfflictionType.BURN), EPS);
        assertEquals(0.75, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.DIVING_SUIT, AfflictionType.RADIATION), EPS);
        // 潜水服不管缺氧/钝器以外的病症
        assertEquals(1.0, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.DIVING_SUIT, AfflictionType.OXYGEN_LOW), EPS);
    }

    @Test
    void 防弹衣数值来自原作() {
        assertEquals(0.3, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.BODY_ARMOR, AfflictionType.BLUNT_TRAUMA), EPS);
        assertEquals(0.2, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.BODY_ARMOR, AfflictionType.BLEEDING), EPS);
        assertEquals(0.9, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.BODY_ARMOR, AfflictionType.BURN), EPS);
    }

    @Test
    void 防弹头盔数值来自原作() {
        assertEquals(0.3, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.HELMET, AfflictionType.BLUNT_TRAUMA), EPS);
        assertEquals(0.1, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.HELMET, AfflictionType.BLEEDING), EPS);
    }

    @Test
    void 其余槽位没有减伤() {
        assertEquals(1.0, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.ID_CARD, AfflictionType.BLUNT_TRAUMA), EPS);
        assertEquals(1.0, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.BAG, AfflictionType.BLEEDING), EPS);
        assertEquals(1.0, ArmorModifierTable.multiplierForSlot(
                EquipmentSlotType.HEADSET, AfflictionType.BURN), EPS);
    }
}
