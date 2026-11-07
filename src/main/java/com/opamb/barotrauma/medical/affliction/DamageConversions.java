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
    /** 未映射近战（生物/玩家）：只有钝器伤（出血/失血暂无来源——用户指令 2026-11-07）。 */
    public static final double GENERIC_BLUNT_PER_HP = 3.0;
    /** 投射物（箭等）：1 HP → 1 钝器伤（出血/失血暂无来源）；命中部位按命中高度鉴定。 */
    public static final double PROJECTILE_BLUNT_PER_HP = 5.0;
    /** 低于此 HP 的伤害不产生病症（防蚊子腿堆积）。 */
    public static final double MIN_DAMAGE_HP = 0.5;

    private DamageConversions() {
    }

    /** 摔落伤 → **只伤双腿**（左右均分），只有钝器伤。 */
    public static List<Application> fromFall(double damageHp) {
        List<Application> plan = new ArrayList<>();
        if (damageHp < MIN_DAMAGE_HP) {
            return plan;
        }
        double strength = damageHp * BLUNT_PER_HP;
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, Limb.LEFT_LEG, strength * 0.5));
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, Limb.RIGHT_LEG, strength * 0.5));
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

    /** 爆炸伤 → 胸部为主的钝器伤 + 烧伤。 */
    public static List<Application> fromExplosion(double damageHp) {
        List<Application> plan = new ArrayList<>();
        if (damageHp < MIN_DAMAGE_HP) {
            return plan;
        }
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, Limb.TORSO, damageHp * BLUNT_PER_HP * 0.8));
        plan.add(new Application(AfflictionType.BURN, Limb.TORSO, damageHp * EXPLOSION_BURN_PER_HP));
        return plan;
    }

    /** 溺水/无氧：每秒注入 12 强度缺氧（原作 cause 注入量级 10–12，见底表 §2.5）。 */
    public static double hypoxiaPerSecond() {
        return 12.0;
    }

    /**
     * 投射物（箭/三叉戟/雪球等）：**按命中高度鉴定部位**（用户指令，参考硬核生存模组做法）。
     * heightRatio = 命中点在玩家包围盒里的相对高度（0=脚，1=头顶）：
     * ≤0.55 → 胸（**头部不做外伤**——用户指令）、≤0.70 手臂、其余腿。
     * 只有钝器伤（出血/失血暂无来源——用户指令）。
     */
    public static List<Application> fromProjectile(double damageHp, double heightRatio, boolean hitFromLeft) {
        List<Application> plan = new ArrayList<>();
        if (damageHp < MIN_DAMAGE_HP) {
            return plan;
        }
        Limb limb = limbByHeightRatio(heightRatio, hitFromLeft);
        plan.add(new Application(AfflictionType.BLUNT_TRAUMA, limb, damageHp * PROJECTILE_BLUNT_PER_HP));
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
     * 未映射近战（生物/玩家）的过渡期折算：只有钝器伤（出血/失血暂无来源——用户指令）。
     * 武器/生物模块接入后替换为按 Attack 的部位归属（设计指令：武器伤害延后）。
     */
    public static List<Application> fromGeneric(double damageHp) {
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
