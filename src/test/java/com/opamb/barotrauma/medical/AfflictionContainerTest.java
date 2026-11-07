package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 容器语义：部位实例、全身合并、最强优先清减、tick 清空、存档行、
 * 以及核心中的核心——vitality = max − Σ(病症消耗)（原作 CalculateVitality 语义）。
 */
class AfflictionContainerTest {

    @Test
    void limbSpecificTypesKeepSeparateInstances() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLEEDING, Limb.LEFT_ARM, 30);
        container.add(AfflictionType.BLEEDING, Limb.RIGHT_ARM, 60);
        assertEquals(30, container.strength(AfflictionType.BLEEDING, Limb.LEFT_ARM), 1.0E-6);
        assertEquals(60, container.strength(AfflictionType.BLEEDING, Limb.RIGHT_ARM), 1.0E-6);
        assertEquals(90, container.totalStrength(AfflictionType.BLEEDING), 1.0E-6);
    }

    @Test
    void globalTypesMergeIntoOneInstance() {
        // 原作 global 语义：多来源失血累加到同一个实例
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLOODLOSS, null, 30);
        container.add(AfflictionType.BLOODLOSS, null, 20);
        assertEquals(50, container.strength(AfflictionType.BLOODLOSS, null), 1.0E-6);
        assertEquals(1, container.rows().size());
    }

    @Test
    void reduceOnLimbTypeWithoutLimbHitsStrongestFirst() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLEEDING, Limb.LEFT_ARM, 20);
        container.add(AfflictionType.BLEEDING, Limb.RIGHT_ARM, 60);
        // 包扎 50：先打最强的右臂（60→10），左臂不动
        double reduced = container.reduce(AfflictionType.BLEEDING, null, 50);
        assertEquals(50, reduced, 1.0E-6);
        assertEquals(20, container.strength(AfflictionType.BLEEDING, Limb.LEFT_ARM), 1.0E-6);
        assertEquals(10, container.strength(AfflictionType.BLEEDING, Limb.RIGHT_ARM), 1.0E-6);
    }

    @Test
    void tickSecondRemovesFadedInstances() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.STUN, null, 2);   // -1/s，2 秒后消失
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 100);

        container.tickSecond();
        assertFalse(container.isEmpty());
        container.tickSecond();
        container.tickSecond();
        // 眩晕 2 秒后归零被移除，只剩出血
        assertEquals(0, container.strength(AfflictionType.STUN, null), 1.0E-6);
        assertTrue(container.strength(AfflictionType.BLEEDING, Limb.TORSO) > 0);
    }

    @Test
    void bleedingPushesBloodlossAtStrengthOverSixtyPerSecond() {
        // 原作 AfflictionBleeding.Update：失血 += 出血强度/60 每秒（底表 §8.4）
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 60);
        container.tickSecond();
        // 推入 60/60 = 1.0，失血自身衰减 -0.1 → 0.9
        assertEquals(0.9, container.strength(AfflictionType.BLOODLOSS, null), 1.0E-6);
        // 两个部位同时在流血：推量相加；出血自身也在衰减（60→59.9），推量用衰减后的强度
        container.add(AfflictionType.BLEEDING, Limb.LEFT_ARM, 30);
        container.tickSecond();
        double expected = 0.9 + (59.9 + 30) / 60.0 - 0.1;
        assertEquals(expected, container.strength(AfflictionType.BLOODLOSS, null), 1.0E-6);
    }

    @Test
    void vitalityIsComputedFromAfflictionsLikeVanilla() {
        // 原作 CharacterHealth.CalculateVitality：vitality = max − Σ GetVitalityDecrease
        // （分数语义：消耗点数 = Lerp(min,max,强度) × MaxVitality）
        AfflictionContainer container = new AfflictionContainer();
        assertEquals(100.0, container.vitality(100.0), 1.0E-6);

        // 出血 4（0–20 段）→ Lerp(0,2,0.2)=0.4 分数 → 40 点 → vitality 60
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 4);
        assertEquals(60.0, container.vitality(100.0), 1.0E-6);

        // 同一个伤害再多加一点强度，血量立即跟着算——没有"每秒积分"
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 2);
        // 强度 6 → Lerp(0,2,0.3)=0.6 分数 → 60 点 → vitality 40
        assertEquals(40.0, container.vitality(100.0), 1.0E-6);
    }

    @Test
    void vitalityRespectsActivationThresholds() {
        AfflictionContainer container = new AfflictionContainer();
        // 失血 3 < 激活阈值 5：无害
        container.add(AfflictionType.BLOODLOSS, null, 3);
        assertEquals(100.0, container.vitality(100.0), 1.0E-6);
        // 缺氧 25 < 50：只是预警，不扣血
        container.add(AfflictionType.OXYGEN_LOW, null, 25);
        assertEquals(100.0, container.vitality(100.0), 1.0E-6);
        // 缺氧 75 → Lerp(0,2,25/150)=1/3 分数 → 33.33 点 → vitality 66.67
        container.add(AfflictionType.OXYGEN_LOW, null, 50);
        assertEquals(100.0 - 100.0 * (2.0 * 25.0 / 150.0), container.vitality(100.0), 1.0E-6);
    }

    @Test
    void multipleAfflictionsSumUpAndClampAtZero() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 4);   // 0.4 分数 → 40 点
        container.add(AfflictionType.BLUNT_TRAUMA, Limb.TORSO, 20); // 0.2 分数 → 20 点
        container.add(AfflictionType.OXYGEN_LOW, null, 50);      // 50 = 预警上限，0 点
        assertEquals(40.0, container.vitality(100.0), 1.0E-6);

        // 复合伤害把消耗推过 100：钳制到 0（死亡）
        container.add(AfflictionType.BLEEDING, Limb.LEFT_LEG, 6);  // +0.6 分数 → 60 点
        assertEquals(0.0, container.vitality(100.0), 1.0E-6);
    }

    @Test
    void clearingAfflictionsRestoresFullVitality() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 100);
        container.add(AfflictionType.BLOODLOSS, null, 100);
        assertTrue(container.vitality(100.0) < 100.0);
        container.clear();
        assertEquals(100.0, container.vitality(100.0), 1.0E-6);
    }

    @Test
    void rawVitalityMatchesVanillaDownedAndDeathLines() {
        // 原作 MinVitality 语义：>0 正常；(−100, 0] 重伤倒地；≤ −100 真死
        AfflictionContainer container = new AfflictionContainer();
        assertEquals(100.0, container.rawVitality(100.0), 1.0E-6);

        // 出血 5 → Lerp(0,2,0.25)×100 = 50 点 → raw 50（正常，掉一半血）
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 5);
        assertEquals(50.0, container.rawVitality(100.0), 1.0E-6);
        assertTrue(container.rawVitality(100.0) > 0);

        // 加重到出血 10 → 1.0 分数 ×100 = 100 点 → raw 0（倒地！）
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 5);
        assertEquals(0.0, container.rawVitality(100.0), 1.0E-6);

        // 再加重出血 20（满强度的 1/10）→ 2.0 分数 ×100 = 200 点 → 钳到 −100（真死线）
        container.add(AfflictionType.BLEEDING, Limb.TORSO, 20);
        assertEquals(AfflictionContainer.MIN_VITALITY, container.rawVitality(100.0), 1.0E-6);

        // 失血 25 → Lerp(0,0.75,0.5)×100 = 37.5 点 → raw 62.5（重伤但未倒地）
        AfflictionContainer moderate = new AfflictionContainer();
        moderate.add(AfflictionType.BLOODLOSS, null, 25);
        assertEquals(62.5, moderate.rawVitality(100.0), 1.0E-6);
        assertTrue(moderate.rawVitality(100.0) > 0);
    }

    @Test
    void rowsRoundTripThroughStrings() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLEEDING, Limb.LEFT_LEG, 33.456);
        container.add(AfflictionType.OXYGEN_LOW, null, 125.0);

        AfflictionContainer restored = new AfflictionContainer();
        for (String row : container.rows()) {
            restored.loadRow(row);
        }
        assertEquals(33.46, restored.strength(AfflictionType.BLEEDING, Limb.LEFT_LEG), 1.0E-6);
        assertEquals(125.0, restored.strength(AfflictionType.OXYGEN_LOW, null), 1.0E-6);
        assertEquals(List.copyOf(container.rows()), List.copyOf(restored.rows()));
    }

    @Test
    void unknownRowsAndBadValuesAreIgnored() {
        AfflictionContainer container = new AfflictionContainer();
        container.loadRow("notarealaffliction|TORSO|50");   // 未知 id 忽略
        container.loadRow("garbage");                        // 段数不足忽略
        container.loadRow("bleeding|NOTALIMB|50");           // 坏部位忽略
        container.loadRow("bleeding|TORSO|notanumber");      // 坏数值忽略
        assertTrue(container.isEmpty());
    }
}
