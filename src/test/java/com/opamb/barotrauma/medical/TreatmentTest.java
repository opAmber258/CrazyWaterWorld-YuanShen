package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import com.opamb.barotrauma.medical.affliction.TreatmentSpec;
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

    /**
     * 回归（CodeRabbit）：纳洛酮必须**真的**能治阿片戒断。
     *
     * <p>原作 `antinarc` 的 OnSuccess 段三条 `ReduceAffliction` 分别是
     * opiatewithdrawal / opiateaddiction / opiateoverdose，全为 `amount="2.0"`
     * （medical.xml:849-853），适用性表也把戒断列为 suitability 60。
     * 之前只实现了中毒/成瘾，面板却把纳洛酮推荐给戒断——点了消耗物品却毫无效果。</p>
     *
     * <p>断言用**未治疗对照组**比较：戒断自身每秒自衰 −0.1/s，只测"绝对值下降"
     * 无法区分药效与自衰（删掉 NALOXONE 的戒断条目，50→49.9 也能过）。</p>
     */
    @Test
    void naloxoneTreatsWithdrawalToo() {
        AfflictionContainer treated = new AfflictionContainer();
        treated.add(AfflictionType.OPIATE_WITHDRAWAL, null, 50);
        AfflictionContainer control = new AfflictionContainer();
        control.add(AfflictionType.OPIATE_WITHDRAWAL, null, 50);

        for (TreatmentSpec.Effect effect : TreatmentSpec.NALOXONE.effects()) {
            treated.applyTreatment(effect.type(), null, effect.perSecond(), effect.seconds());
        }
        treated.tickSecond();
        control.tickSecond();

        double treatedAfter = treated.strength(AfflictionType.OPIATE_WITHDRAWAL, null);
        double controlAfter = control.strength(AfflictionType.OPIATE_WITHDRAWAL, null);
        // 纳洛酮对戒断是 −2/s，第一秒比对照组多减 2.0
        assertEquals(2.0, controlAfter - treatedAfter, 1.0E-6,
                "纳洛酮必须比自然自衰多降 2 点戒断强度；治疗组=" + treatedAfter
                        + " 对照组=" + controlAfter);
    }

    @Test
    void antiradClearsRadiationInOneShot() {        // 原作 antirad：radiationsickness −100/s×1s（OnSuccess 路径）
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.RADIATION, null, 75); // 反应堆熔毁注入量
        for (TreatmentSpec.Effect effect : TreatmentSpec.ANTIRAD.effects()) {
            container.applyTreatment(effect.type(), null, effect.perSecond(), effect.seconds());
        }
        container.tickSecond();
        assertEquals(0.0, container.strength(AfflictionType.RADIATION, null), 1.0E-6,
                "消辐灵一剂必须清空 75 强度的辐射病");
    }

    @Test
    void liquidOxygeniteSlowReleasesAndDamagesOrgans() {
        // 原作 liquidoxygenite：缺氧 −2.5/s×60s（缓释），副作用器官损伤 +1.5/s×5s
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.OXYGEN_LOW, null, 200);
        for (TreatmentSpec.Effect effect : TreatmentSpec.LIQUID_OXYGENITE.effects()) {
            container.applyTreatment(effect.type(), null, effect.perSecond(), effect.seconds());
        }
        container.tickSecond();
        // 缺氧 200 → 197.5（每秒只减 2.5，这就是"缓释"）
        assertEquals(197.5, container.strength(AfflictionType.OXYGEN_LOW, null), 1.0E-6);
        // 器官损伤 0 → 1.5（注入必然带一点毒性，原作注释 "comes with some organ damage"）
        assertEquals(1.5, container.strength(AfflictionType.ORGAN_DAMAGE, null), 1.0E-6);
        // 副作用只有 5 秒，之后不再叠加
        for (int i = 0; i < 10; i++) {
            container.tickSecond();
        }
        assertEquals(1.5 * 5, container.strength(AfflictionType.ORGAN_DAMAGE, null), 1.0E-3);
        // 缺氧持续削减：11 秒共 −27.5
        assertEquals(172.5, container.strength(AfflictionType.OXYGEN_LOW, null), 1.0E-6);
    }

    @Test
    void everyAfflictionHasChineseDisplayName() {
        // 回归：辐射病曾经没有显示名，UI 里直接露出 "radiationsickness" 这种 id。
        for (AfflictionType type : AfflictionType.ALL) {
            assertTrue(com.opamb.barotrauma.client.TreatmentRenderer.DISPLAY_NAMES.containsKey(type.id()),
                    "病症 " + type.id() + " 缺少中文显示名");
            assertTrue(com.opamb.barotrauma.client.MedicalBodyRenderer.DISPLAY_NAMES.containsKey(type.id()),
                    "病症 " + type.id() + " 缺少中文显示名（BodyRenderer）");
        }
    }
}
