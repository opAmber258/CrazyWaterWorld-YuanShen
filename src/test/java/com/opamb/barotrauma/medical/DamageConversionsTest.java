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
 * **出血/失血暂无来源**（用户指令 2026-11-07）：所有转化只产生钝器伤/烧伤，
 * 出血/失血只能通过命令注入，等后续设计来源。
 */
class DamageConversionsTest {

    @Test
    void fallDamageOnlyInjuresLegs() {
        // 4 HP 摔伤 = 20 强度，只伤双腿（左/右各 10），只有钝器伤
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
    void severeFallStillOnlyBluntOnLegs() {
        // 10 HP 重摔：钝器 25+25 全在腿，无流血
        List<DamageConversions.Application> plan = DamageConversions.fromFall(10.0);
        assertEquals(2, plan.size());
        assertEquals(25.0, plan.get(0).strength(), 1.0E-6);
        assertEquals(Limb.LEFT_LEG, plan.get(0).limb());
        assertEquals(25.0, plan.get(1).strength(), 1.0E-6);
        assertEquals(Limb.RIGHT_LEG, plan.get(1).limb());
        assertTrue(plan.stream().noneMatch(a -> a.type() == AfflictionType.BLEEDING));
    }

    @Test
    void tinyDamageProducesNothing() {
        assertTrue(DamageConversions.fromFall(0.4).isEmpty());
        assertTrue(DamageConversions.fromExplosion(0.1).isEmpty());
        assertTrue(DamageConversions.fromGeneric(0.2).isEmpty());
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
    void explosionCombinesBluntAndBurnOnTorso() {
        List<DamageConversions.Application> plan = DamageConversions.fromExplosion(5.0);
        assertEquals(2, plan.size());
        assertEquals(Limb.TORSO, plan.get(0).limb());
        assertEquals(20.0, plan.get(0).strength(), 1.0E-6);
        assertEquals(Limb.TORSO, plan.get(1).limb());
        assertEquals(12.5, plan.get(1).strength(), 1.0E-6);
    }

    @Test
    void projectileLimbComesFromHitHeight() {
        // 命中高度 0.3（胸口）→ 胸部钝器伤（头部命中并入胸部，无出血/失血）
        List<DamageConversions.Application> chest = DamageConversions.fromProjectile(3.0, 0.3, true);
        assertEquals(1, chest.size());
        assertEquals(Limb.TORSO, chest.get(0).limb());
        assertEquals(AfflictionType.BLUNT_TRAUMA, chest.get(0).type());
        assertEquals(15.0, chest.get(0).strength(), 1.0E-6);
        // 命中高度 0.9 → 腿（按命中侧分左右）
        List<DamageConversions.Application> leg = DamageConversions.fromProjectile(3.0, 0.9, false);
        assertEquals(Limb.RIGHT_LEG, leg.get(0).limb());
        // 命中高度 0.62 → 手臂
        List<DamageConversions.Application> arm = DamageConversions.fromProjectile(3.0, 0.62, true);
        assertEquals(Limb.LEFT_ARM, arm.get(0).limb());
    }

    @Test
    void genericMeleeIsBluntOnly() {
        // 3 HP 僵尸一拳：只有钝器伤 9（出血/失血暂无来源）
        List<DamageConversions.Application> plan = DamageConversions.fromGeneric(3.0);
        assertEquals(1, plan.size());
        assertEquals(AfflictionType.BLUNT_TRAUMA, plan.get(0).type());
        assertEquals(9.0, plan.get(0).strength(), 1.0E-6);
        assertEquals(Limb.TORSO, plan.get(0).limb());
    }
}
