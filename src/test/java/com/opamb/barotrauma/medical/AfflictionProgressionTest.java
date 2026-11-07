package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.medical.affliction.AfflictionInstance;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 病症推进数学对照 vanilla-baseline.md §2 的原作数值。
 *
 * <p>vitalityDecrease 语义（原作 Affliction.GetVitalityDecrease，源码已核对）：
 * XML 的 min/maxvitalitydecrease 是最大生命的分数，消耗点数 = Lerp × MaxVitality。
 * wiki 交叉验证：钝器伤满强度 2.0 分数 × 100 = 200 点 / 200 强度 = −1 vitality per strength ✓</p>
 */
class AfflictionProgressionTest {

    private static final double MAX_VITALITY = 100.0;

    @Test
    void bleedingDrainMatchesVanillaTiers() {
        // 0-20 段: Lerp(0,2,s/20)×100 —— 强度 10 → 1.0 分数 → 100 点
        assertEquals(100.0, AfflictionType.BLEEDING.vitalityDecrease(10, MAX_VITALITY), 1.0E-6);
        // 段边界连续：20 → 2 分数 = 200 点，40 → 400，60 → 600
        assertEquals(200.0, AfflictionType.BLEEDING.vitalityDecrease(20, MAX_VITALITY), 1.0E-6);
        assertEquals(400.0, AfflictionType.BLEEDING.vitalityDecrease(40, MAX_VITALITY), 1.0E-6);
        assertEquals(600.0, AfflictionType.BLEEDING.vitalityDecrease(60, MAX_VITALITY), 1.0E-6);
        // 上限 100 → 10 分数 = 1000 点（必死；原作出血是第一优先级威胁）
        assertEquals(1000.0, AfflictionType.BLEEDING.vitalityDecrease(100, MAX_VITALITY), 1.0E-6);
    }

    @Test
    void bloodlossSelfDecaysAndStaysHarmlessBelowActivation() {
        // 激活阈值 5：低于 5 不掉血
        assertEquals(0.0, AfflictionType.BLOODLOSS.vitalityDecrease(3, MAX_VITALITY), 1.0E-6);
        // 5 刚好 0 点，25 → Lerp(0,0.75,0.5)=0.375 分数 → 37.5 点，100 → 200 点（死线以内）
        assertEquals(0.0, AfflictionType.BLOODLOSS.vitalityDecrease(5, MAX_VITALITY), 1.0E-6);
        assertEquals(37.5, AfflictionType.BLOODLOSS.vitalityDecrease(25, MAX_VITALITY), 1.0E-6);
        assertEquals(200.0, AfflictionType.BLOODLOSS.vitalityDecrease(100, MAX_VITALITY), 1.0E-6);
        // 原作：失血自衰 -0.1/s
        assertEquals(-0.1, AfflictionType.BLOODLOSS.strengthChangePerSecond(50), 1.0E-6);
    }

    @Test
    void oxygenLowWarnsBeforeItHurts() {
        // 原作：1–50 只是预警不扣血
        assertEquals(0.0, AfflictionType.OXYGEN_LOW.vitalityDecrease(25, MAX_VITALITY), 1.0E-6);
        assertEquals(0.0, AfflictionType.OXYGEN_LOW.vitalityDecrease(50, MAX_VITALITY), 1.0E-6);
        // 50–200 线性：75 → Lerp(0,2,25/150)=1/3 分数 → 33.33 点
        assertEquals(100.0 * (2.0 * 25.0 / 150.0), AfflictionType.OXYGEN_LOW.vitalityDecrease(75, MAX_VITALITY), 1.0E-6);
        assertEquals(200.0, AfflictionType.OXYGEN_LOW.vitalityDecrease(200, MAX_VITALITY), 1.0E-6);
    }

    @Test
    void bluntTraumaIsOneVitalityPerStrengthLikeWiki() {
        // wiki 内伤行："−1 vitality per strength"（0–200 线性到 2.0 分数）
        assertEquals(100.0, AfflictionType.BLUNT_TRAUMA.vitalityDecrease(100, MAX_VITALITY), 1.0E-6);
        assertEquals(200.0, AfflictionType.BLUNT_TRAUMA.vitalityDecrease(200, MAX_VITALITY), 1.0E-6);
        // 钝器伤无自愈
        assertEquals(0.0, AfflictionType.BLUNT_TRAUMA.strengthChangePerSecond(100), 1.0E-6);
    }

    @Test
    void stunFadesInOneSecondPerPoint() {
        // 原作：-1/s，30 封顶，零伤害
        assertEquals(0.0, AfflictionType.STUN.vitalityDecrease(30, MAX_VITALITY), 1.0E-6);
        assertEquals(-1.0, AfflictionType.STUN.strengthChangePerSecond(30), 1.0E-6);
    }

    @Test
    void instanceTickSecondAppliesNaturalChangeAndClamps() {
        // 眩晕 30 → 每秒 -1，30 秒后归零
        AfflictionInstance stun = new AfflictionInstance(AfflictionType.STUN, null, 30);
        for (int i = 0; i < 30; i++) {
            stun.tickSecond();
        }
        assertTrue(stun.isEmpty());

        // 出血 5 → 每秒 -0.1 自愈但幅度很小，10 秒后仍剩 4
        AfflictionInstance bleeding = new AfflictionInstance(AfflictionType.BLEEDING, Limb.TORSO, 5);
        for (int i = 0; i < 10; i++) {
            bleeding.tickSecond();
        }
        assertEquals(4.0, bleeding.strength(), 1.0E-6);

        // 钳制：add 超上限停在 max
        AfflictionInstance burn = new AfflictionInstance(AfflictionType.BURN, Limb.TORSO, 0);
        assertEquals(200.0, burn.add(999.0), 1.0E-6);
        // reduce 到底（烧伤无自愈，只有治疗能降）
        assertEquals(0.0, burn.reduce(999.0), 1.0E-6);
    }
}
