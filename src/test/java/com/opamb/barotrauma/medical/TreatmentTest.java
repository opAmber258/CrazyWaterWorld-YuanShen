package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 治疗 buffer：每秒治疗量 × 持续时间（原作 ReduceAffliction 语义），含副作用与持久化。
 */
class TreatmentTest {

    @Test
    void bandageReducesBleedingOverTwentySeconds() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 30);
        // 血袋同款：一次治疗登记后逐秒结算
        container.applyTreatment(AfflictionType.BLEEDING, null, 1.5, 20);
        // tick 1：治疗 -1.5 → 28.5；推失血用治疗结算后的强度 28.5/60；出血再自愈 -0.1 → 28.4
        container.tickSecond();
        assertEquals(28.4, container.strength(AfflictionType.BLEEDING, Limb.TORSO), 1.0E-6);
        assertEquals(28.5 / 60.0 - 0.1, container.strength(AfflictionType.BLOODLOSS, null), 1.0E-6);
    }

    @Test
    void bloodPackFullyCuresMaxBloodlossOverFifteenSeconds() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLOODLOSS, null, 100);
        container.applyTreatment(AfflictionType.BLOODLOSS, null, 8, 15);
        for (int i = 0; i < 13; i++) {
            container.tickSecond(); // 8/s × 13 = 104 ≥ 100，13 秒内清空（衰减会更快）
        }
        assertEquals(0, container.strength(AfflictionType.BLOODLOSS, null), 1.0E-6);
    }

    @Test
    void morphineReducesBluntTraumaButAddsOxygenLow() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLUNT_TRAUMA, Limb.TORSO, 40);
        container.applyTreatment(AfflictionType.BLUNT_TRAUMA, null, 2, 20);
        container.applyTreatment(AfflictionType.OXYGEN_LOW, null, -1, 20);
        container.tickSecond();
        // 钝器伤 40 - 2（治疗，最强优先）= 38（无自愈）
        assertEquals(38, container.strength(AfflictionType.BLUNT_TRAUMA, Limb.TORSO), 1.0E-6);
        // 副作用：缺氧 +1/s（原作：吗啡抑制呼吸）
        assertEquals(1, container.strength(AfflictionType.OXYGEN_LOW, null), 1.0E-6);
    }

    @Test
    void glueSideEffectLandsOnTorsoWhenNoLimbGiven() {
        AfflictionContainer container = new AfflictionContainer();
        // 凝胶副作用：器官损伤 → 钝器伤 0.6/s × 5s，未指定部位 → 躯干
        container.applyTreatment(AfflictionType.BLUNT_TRAUMA, null, -0.6, 5);
        container.tickSecond();
        assertEquals(0.6, container.strength(AfflictionType.BLUNT_TRAUMA, Limb.TORSO), 1.0E-6);
        // 5 秒后治疗到期，不再叠加
        for (int i = 0; i < 6; i++) {
            container.tickSecond();
        }
        assertEquals(0.6 * 5, container.strength(AfflictionType.BLUNT_TRAUMA, Limb.TORSO), 1.0E-3);
    }

    @Test
    void treatmentsPersistThroughRows() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLOODLOSS, null, 100);
        container.applyTreatment(AfflictionType.BLOODLOSS, null, 8, 15);
        AfflictionContainer restored = new AfflictionContainer();
        for (String row : container.rows()) {
            restored.loadRow(row);
        }
        restored.tickSecond();
        // 100 − 8（治疗）− 0.1（自衰）= 91.9
        assertEquals(91.9, restored.strength(AfflictionType.BLOODLOSS, null), 1.0E-6);
    }

    @Test
    void deathClearCancelsTreatments() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 50);
        container.applyTreatment(AfflictionType.BLEEDING, null, 1.5, 20);
        container.clear();
        assertTrue(container.isEmpty());
        // 清空后 tick 不再有任何效果
        container.tickSecond();
        assertEquals(0, container.strength(AfflictionType.BLEEDING, Limb.TORSO), 1.0E-6);
    }
}
