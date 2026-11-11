package com.opamb.barotrauma.medical.affliction;

import java.util.ArrayList;
import java.util.List;

/**
 * MC 伤害 → 病症的转化表（纯 Java，L1 可测）。
 *
 * <p>模型（2026-11-07 设计指令 + 原作源码/wiki 核对）：**病症即血量**——
 * 原版 MC 伤害结算被取消，全部转成病症；vitality = 100 − Σ 病症消耗（实时计算）。
 * 钝器伤/烧伤在原作里都是 **1 强度 = 1 vitality**（线性到满强度的 2.0 分数），
 * 所以换算基准是 1 HP（半颗心 = 5% 血条）→ 5 强度 → 5 vitality，1:1 对齐。</p>
 *
 * <p>数值均为【默认】待 M0 评审。</p>
 */
public final class DamageConversions {

    /** 桥接层把 DamageSource 归类成这几种；未列出的原版伤害照常结算、不产生病症。 */
    public enum Kind {
        FALL, FIRE, EXPLOSION, NONE
    }

    /** 一次转化要注入的 (类型, 部位, 强度)。 */
    public record Application(AfflictionType type, Limb limb, double strength) {
    }

    /** 摔落：1 HP → 5 强度钝器伤（= 5 vitality）。摔伤只伤双腿（用户指令：按来源鉴定部位）。 */
    public static final double BLUNT_PER_HP = 5.0;
    /** 火焰：1 HP → 5 强度烧伤（着火期间持续积累，离开火后继续掉血）。 */
    public static final double BURN_PER_HP = 5.0;
    /** 爆炸：1 HP → 5 钝器伤 + 2.5 烧伤（胸为主）。 */
    public static final double EXPLOSION_BURN_PER_HP = 2.5;
    /** 未映射近战（生物/玩家）：钝器伤按 3/HP 折算。 */
    public static final double GENERIC_BLUNT_PER_HP = 3.0;
    /** 近战附加出血：1 HP → 5 强度出血、50% 概率（原作咬击规律：出血强度≈主伤同级×0.5 概率，
     * CrawlerDefaultRagdoll.xml:58/76、MudraptorDefaultRagdoll.xml:16/37、TigerthresherDefaultRagdoll.xml:12）。 */
    public static final double GENERIC_BLEED_PER_HP = 5.0;
    /** 近战出血概率（原作咬击 50%~100%，v1 统一取下限 50%，生物模块接入时逐只对表）。 */
    public static final double GENERIC_BLEED_PROBABILITY = 0.5;
    /** 投射物（箭等）：1 HP → 5 强度钝器伤；命中部位按命中高度鉴定。 */
    public static final double PROJECTILE_BLUNT_PER_HP = 5.0;
    /** 投射物附加出血：1 HP → 2.5 强度、**100%**（原作穿刺武器必出血，
     * 出血≈主伤 1.25~5 倍：weapons.xml 手枪 338 / 步枪 217 / 刀 896）。 */
    public static final double PROJECTILE_BLEED_PER_HP = 2.5;
    /** 爆炸附加出血：单次 ≥8 HP 时 5% 概率 40 强度（原作精确值 explosives.xml:33/78/124/170/403）。 */
    public static final double EXPLOSION_BLEED_MIN_HP = 8.0;
    public static final double EXPLOSION_BLEED_PROBABILITY = 0.05;
    public static final double EXPLOSION_BLEEDING_STRENGTH = 40.0;
    /** 摔落附加腿部出血：单次 ≥6 HP（≈原作断腿级）双腿各 10 强度（MC 适配，用户指令"重度才附加"）。 */
    public static final double FALL_BLEED_THRESHOLD_HP = 6.0;
    public static final double FALL_BLEED_PER_LEG = 10.0;
    /** 低于此 HP 的伤害不产生病症（防蚊子腿堆积）。 */
    public static final double MIN_DAMAGE_HP = 0.5;

    private DamageConversions() {
    }

    /**
     * 摔落伤 → **只伤双腿**（左右均分）；单次 ≥ {@link #FALL_BLEED_THRESHOLD_HP} 的重度摔伤
     * 双腿各附加 {@link #FALL_BLEED_PER_LEG} 强度出血（原作摔落无出血，此项是用户 MC 适配指令）。
     */
    public static List<Application> fromFall(double damageHp) {
        List<Application> plan = new ArrayList<>();
        if (damageHp < MIN_DAMAGE_HP) {
            return plan;
        }
        double strength = damageHp * BLUNT_PER_HP;
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, Limb.LEFT_LEG, strength * 0.5));
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, Limb.RIGHT_LEG, strength * 0.5));
        if (damageHp >= FALL_BLEED_THRESHOLD_HP) {
            plan.add(new Application(AfflictionType.BLEEDING, Limb.LEFT_LEG, FALL_BLEED_PER_LEG));
            plan.add(new Application(AfflictionType.BLEEDING, Limb.RIGHT_LEG, FALL_BLEED_PER_LEG));
        }
        return plan;
    }

    /** 火焰伤 → 烧伤，肢体按权重选（躯干 2/6，四肢各 1/6；头部不烧——原作头部烧伤极少见）。 */
    public static Application fromFire(double damageHp, double random01) {
        if (damageHp < MIN_DAMAGE_HP) {
            return new Application(AfflictionType.BURN, Limb.TORSO, 0.0);
        }
        // 权重表：TORSO×2, 左右臂×1, 左右腿×1（共 6 份）
        Limb[] weighted = {
                Limb.TORSO, Limb.TORSO,
                Limb.LEFT_ARM, Limb.RIGHT_ARM,
                Limb.LEFT_LEG, Limb.RIGHT_LEG
        };
        int index = (int) (clamp01(random01) * weighted.length);
        if (index >= weighted.length) {
            index = weighted.length - 1;
        }
        return new Application(AfflictionType.BURN, weighted[index], damageHp * BURN_PER_HP);
    }

    /**
     * 爆炸伤 → 胸部为主的钝器伤 + 烧伤；单次 ≥ {@link #EXPLOSION_BLEED_MIN_HP} 时
     * {@link #EXPLOSION_BLEED_PROBABILITY} 概率附加 {@link #EXPLOSION_BLEEDING_STRENGTH} 强度出血
     * （原作爆炸出血是固定强度不随伤害缩放——explosives.xml 各型炸弹统一 20~50 × 5%）。
     */
    public static List<Application> fromExplosion(double damageHp, double random01) {
        List<Application> plan = new ArrayList<>();
        if (damageHp < MIN_DAMAGE_HP) {
            return plan;
        }
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, Limb.TORSO, damageHp * BLUNT_PER_HP * 0.8));
        plan.add(new Application(AfflictionType.BURN, Limb.TORSO, damageHp * EXPLOSION_BURN_PER_HP));
        if (damageHp >= EXPLOSION_BLEED_MIN_HP && clamp01(random01) < EXPLOSION_BLEED_PROBABILITY) {
            plan.add(new Application(AfflictionType.BLEEDING, Limb.TORSO, EXPLOSION_BLEEDING_STRENGTH));
        }
        return plan;
    }

    /**
     * 投射物（箭/三叉戟/雪球等）：**按命中高度鉴定部位**（用户指令，参考硬核生存模组做法）。
     * heightRatio = 命中点在玩家包围盒里的相对高度（0=脚，1=头顶）：
     * ≤0.55 → 胸（**头部不做外伤**——用户指令）、≤0.70 手臂、其余腿。
     * 穿刺必出血 {@link #PROJECTILE_BLEED_PER_HP}/HP（原作枪械/猎叉出血 100%，出血挂同一部位）。
     */
    public static List<Application> fromProjectile(double damageHp, double heightRatio, boolean hitFromLeft) {
        List<Application> plan = new ArrayList<>();
        if (damageHp < MIN_DAMAGE_HP) {
            return plan;
        }
        Limb limb = limbByHeightRatio(heightRatio, hitFromLeft);
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, limb, damageHp * PROJECTILE_BLUNT_PER_HP));
        plan.add(new Application(AfflictionType.BLEEDING, limb, damageHp * PROJECTILE_BLEED_PER_HP));
        return plan;
    }

    /** 相对高度 → 部位；手臂/腿按 hitFromLeft 分左右。头部命中并入胸部（v1 头部无外伤）。 */
    public static Limb limbByHeightRatio(double heightRatio, boolean hitFromLeft) {
        double ratio = Math.max(0.0, Math.min(1.0, heightRatio));
        if (ratio <= 0.55) {
            return Limb.TORSO;
        }
        if (ratio <= 0.70) {
            return hitFromLeft ? Limb.LEFT_ARM : Limb.RIGHT_ARM;
        }
        return hitFromLeft ? Limb.LEFT_LEG : Limb.RIGHT_LEG;
    }

    /**
     * 未映射近战（生物/玩家）的过渡期折算：钝器伤 + 概率出血（原作三件套的"失"由
     * {@code AfflictionContainer.tickSecond} 的出血推失血自动承担，无需独立来源）。
     * 武器/生物模块接入后替换为按 Attack 的逐只/逐武器数值（设计指令：武器伤害延后）。
     */
    public static List<Application> fromGeneric(double damageHp, double random01) {
        List<Application> plan = new ArrayList<>();
        if (damageHp < MIN_DAMAGE_HP) {
            return plan;
        }
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, Limb.TORSO, damageHp * GENERIC_BLUNT_PER_HP));
        if (clamp01(random01) < GENERIC_BLEED_PROBABILITY) {
            plan.add(new Application(AfflictionType.BLEEDING, Limb.TORSO, damageHp * GENERIC_BLEED_PER_HP));
        }
        return plan;
    }

    /**
     * 非近战的无主伤害（深海压力、仙人掌、卡墙、凋零、中毒/魔法兜底）→ **只有钝器伤，不出血**。
     *
     * <p>这些来源不是咬击/穿刺/爆炸：原版压力、凋零类不出血（见
     * {@code docs/medical/bleeding-sources.md} §1.4/§2）。尤其深海压力是每秒持续伤害，
     * 若走 {@link #fromGeneric} 会每秒掷一次 50% 出血，出血量随持续时间无限叠加。</p>
     */
    public static List<Application> fromBluntOnly(double damageHp) {
        List<Application> plan = new ArrayList<>();
        if (damageHp < MIN_DAMAGE_HP) {
            return plan;
        }
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, Limb.TORSO, damageHp * GENERIC_BLUNT_PER_HP));
        return plan;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
