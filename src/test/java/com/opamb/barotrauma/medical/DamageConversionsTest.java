package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.DamageConversions;
import com.opamb.barotrauma.medical.affliction.Limb;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 伤害转化表：摔/烧/爆炸/投射物命中高度/近战 → 病症注入计划。
 * 换算基准 1 HP = 5 强度（1 强度 = 1 vitality）。
 *
 * <p>出血来源（2026-11-08 用户拍板方案 A，原作 XML 数值见
 * docs/medical/bleeding-sources.md）：近战 5×HP×50%、投射物 2.5×HP×100%、
 * 爆炸 ≥8HP 时 5% 概率 40 强度、摔落 ≥6HP 双腿各 10。失血无直接来源，
 * 由 {@code AfflictionContainer.tickSecond} 的出血推失血自动产生（原作 AfflictionBleeding）。</p>
 */
class DamageConversionsTest {

    @Test
    void lightFallOnlyInjuresLegsWithBlunt() {
        // 4 HP 摔伤 = 20 强度，只伤双腿（左/右各 10），低于 6 HP 阈值无出血
        List<DamageConversions.Application> plan = DamageConversions.fromFall(4.0);
        assertEquals(2, plan.size());
        assertEquals(10.0, plan.get(0).strength(), 1.0E-6);
        assertEquals(Limb.LEFT_LEG, plan.get(0).limb());
        assertEquals(10.0, plan.get(1).strength(), 1.0E-6);
        assertEquals(Limb.RIGHT_LEG, plan.get(1).limb());
        assertTrue(plan.stream().noneMatch(a -> a.type() == AfflictionType.BLEEDING));
        assertTrue(plan.stream().noneMatch(a -> a.type() == AfflictionType.BLOODLOSS));
        assertTrue(plan.stream().noneMatch(a -> a.limb() == Limb.TORSO));
    }

    @Test
    void severeFallAddsLegBleeding() {
        // 10 HP 重摔：钝器 25+25 全在腿，≥6 HP 阈值 → 双腿各附加 10 强度出血
        List<DamageConversions.Application> plan = DamageConversions.fromFall(10.0);
        assertEquals(4, plan.size());
        assertEquals(AfflictionType.BLUNT_TRAUMA, plan.get(0).type());
        assertEquals(25.0, plan.get(0).strength(), 1.0E-6);
        assertEquals(Limb.LEFT_LEG, plan.get(0).limb());
        assertEquals(AfflictionType.BLUNT_TRAUMA, plan.get(1).type());
        assertEquals(25.0, plan.get(1).strength(), 1.0E-6);
        assertEquals(Limb.RIGHT_LEG, plan.get(1).limb());
        assertEquals(AfflictionType.BLEEDING, plan.get(2).type());
        assertEquals(DamageConversions.FALL_BLEED_PER_LEG, plan.get(2).strength(), 1.0E-6);
        assertEquals(Limb.LEFT_LEG, plan.get(2).limb());
        assertEquals(AfflictionType.BLEEDING, plan.get(3).type());
        assertEquals(Limb.RIGHT_LEG, plan.get(3).limb());
    }

    @Test
    void fallBleedingThresholdIsInclusive() {
        // 恰好 6 HP：达到阈值（>= 语义），双腿附加出血
        List<DamageConversions.Application> plan = DamageConversions.fromFall(6.0);
        assertEquals(4, plan.size());
        // 5.9 HP：不触发
        assertTrue(DamageConversions.fromFall(5.9).stream().noneMatch(a -> a.type() == AfflictionType.BLEEDING));
    }

    @Test
    void tinyDamageProducesNothing() {
        assertTrue(DamageConversions.fromFall(0.4).isEmpty());
        assertTrue(DamageConversions.fromExplosion(0.1, 0.0).isEmpty());
        assertTrue(DamageConversions.fromGeneric(0.2, 0.0).isEmpty());
        assertTrue(DamageConversions.fromProjectile(0.2, 0.5, true).isEmpty());
        // 火焰返回 0 强度的占位，注入时 amount<=0 会被容器忽略
        assertEquals(0.0, DamageConversions.fromFire(0.4, 0.9).strength(), 1.0E-6);
    }

    @Test
    void fireDamagePicksLimbFromRandomAndKeepsStrengthMatched() {
        // 随机数决定部位，但部位和强度必须来自同一次调用（回归：曾拆成两次随机）
        DamageConversions.Application low = DamageConversions.fromFire(3.0, 0.0);
        assertEquals(Limb.TORSO, low.limb());
        assertEquals(15.0, low.strength(), 1.0E-6);

        // 随机 1.0 越界钳制到最后一个权重位（右腿）
        DamageConversions.Application last = DamageConversions.fromFire(3.0, 1.0);
        assertEquals(Limb.RIGHT_LEG, last.limb());
        assertEquals(15.0, last.strength(), 1.0E-6);
    }

    @Test
    void smallExplosionKeepsBluntAndBurnOnly() {
        // 5 HP 爆炸 < 8 HP 门槛：无论随机数都不出血（原作爆炸出血是固定强度×5% 概率）
        List<DamageConversions.Application> plan = DamageConversions.fromExplosion(5.0, 0.0);
        assertEquals(2, plan.size());
        assertEquals(Limb.TORSO, plan.get(0).limb());
        assertEquals(20.0, plan.get(0).strength(), 1.0E-6);
        assertEquals(Limb.TORSO, plan.get(1).limb());
        assertEquals(12.5, plan.get(1).strength(), 1.0E-6);
        assertTrue(plan.stream().noneMatch(a -> a.type() == AfflictionType.BLEEDING));
    }

    @Test
    void bigExplosionBleedsOnFivePercentRoll() {
        // 9 HP 爆炸 + 掷中 5%（random01=0.04）：附加 40 强度出血（原作固定值，不随 HP 缩放）
        List<DamageConversions.Application> lucky = DamageConversions.fromExplosion(9.0, 0.04);
        assertEquals(3, lucky.size());
        assertEquals(AfflictionType.BLEEDING, lucky.get(2).type());
        assertEquals(DamageConversions.EXPLOSION_BLEEDING_STRENGTH, lucky.get(2).strength(), 1.0E-6);
        assertEquals(Limb.TORSO, lucky.get(2).limb());

        // 未掷中（0.06 ≥ 5%）：只有钝+烧
        assertTrue(DamageConversions.fromExplosion(9.0, 0.06).stream()
                .noneMatch(a -> a.type() == AfflictionType.BLEEDING));
        // 恰好 8 HP 达到门槛
        assertEquals(3, DamageConversions.fromExplosion(8.0, 0.0).size());
        // 7.9 HP 不达门槛
        assertEquals(2, DamageConversions.fromExplosion(7.9, 0.0).size());
    }

    @Test
    void projectileAlwaysBleedsOnTheHitLimb() {
        // 命中高度 0.3（胸口）→ 胸部钝器伤 15 + 出血 7.5（100%，原作穿刺武器规律）
        List<DamageConversions.Application> chest = DamageConversions.fromProjectile(3.0, 0.3, true);
        assertEquals(2, chest.size());
        assertEquals(Limb.TORSO, chest.get(0).limb());
        assertEquals(AfflictionType.BLUNT_TRAUMA, chest.get(0).type());
        assertEquals(15.0, chest.get(0).strength(), 1.0E-6);
        assertEquals(AfflictionType.BLEEDING, chest.get(1).type());
        assertEquals(7.5, chest.get(1).strength(), 1.0E-6);
        assertEquals(Limb.TORSO, chest.get(1).limb());
        // 命中高度 0.9 → 腿（按命中侧分左右），出血同部位
        List<DamageConversions.Application> leg = DamageConversions.fromProjectile(3.0, 0.9, false);
        assertEquals(Limb.RIGHT_LEG, leg.get(0).limb());
        assertEquals(Limb.RIGHT_LEG, leg.get(1).limb());
        // 命中高度 0.62 → 手臂
        List<DamageConversions.Application> arm = DamageConversions.fromProjectile(3.0, 0.62, true);
        assertEquals(Limb.LEFT_ARM, arm.get(0).limb());
        assertEquals(Limb.LEFT_ARM, arm.get(1).limb());
    }

    @Test
    void meleeBleedsOnHalfProbability() {
        // 3 HP 僵尸一拳 + 掷中 50%（random01=0.3）：钝器 9 + 出血 15，都在躯干
        List<DamageConversions.Application> bleeding = DamageConversions.fromGeneric(3.0, 0.3);
        assertEquals(2, bleeding.size());
        assertEquals(AfflictionType.BLUNT_TRAUMA, bleeding.get(0).type());
        assertEquals(9.0, bleeding.get(0).strength(), 1.0E-6);
        assertEquals(Limb.TORSO, bleeding.get(0).limb());
        assertEquals(AfflictionType.BLEEDING, bleeding.get(1).type());
        assertEquals(15.0, bleeding.get(1).strength(), 1.0E-6);
        assertEquals(Limb.TORSO, bleeding.get(1).limb());

        // 未掷中（0.5 及以上）：只有钝器伤
        List<DamageConversions.Application> dry = DamageConversions.fromGeneric(3.0, 0.5);
        assertEquals(1, dry.size());
        assertEquals(AfflictionType.BLUNT_TRAUMA, dry.get(0).type());
        assertTrue(DamageConversions.fromGeneric(3.0, 0.99).stream()
                .noneMatch(a -> a.type() == AfflictionType.BLEEDING));
        // 任何转化都不直接产生失血（失血只由出血推进产生——原作 AfflictionBleeding）
        assertTrue(bleeding.stream().noneMatch(a -> a.type() == AfflictionType.BLOODLOSS));
    }

    @Test
    void nonMeleeFallbackNeverBleeds() {
        // 压力/仙人掌/卡墙/凋零/无主伤害走 fromBluntOnly：只有钝器伤，任何随机数都不出血
        List<DamageConversions.Application> plan = DamageConversions.fromBluntOnly(3.0);
        assertEquals(1, plan.size());
        assertEquals(AfflictionType.BLUNT_TRAUMA, plan.get(0).type());
        assertEquals(9.0, plan.get(0).strength(), 1.0E-6);
        assertEquals(Limb.TORSO, plan.get(0).limb());
        assertTrue(plan.stream().noneMatch(a -> a.type() == AfflictionType.BLEEDING),
                "持续压力/无主伤害不能附加出血，否则每秒掷骰会滚雪球");
        assertTrue(DamageConversions.fromBluntOnly(0.2).isEmpty(), "低于最小伤害不产生病症");
    }
}
