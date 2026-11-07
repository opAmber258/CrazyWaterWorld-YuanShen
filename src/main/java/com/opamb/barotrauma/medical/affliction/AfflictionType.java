package com.opamb.barotrauma.medical.affliction;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 病症类型定义——数值全部来自原作 v1.13.5.0 的 Content/Afflictions.xml，
 * 见 docs/medical/vanilla-baseline.md（M0 底表）。
 *
 * <p>本类保持零 MC 依赖，让 L1 单元测试可以直接跑数学。
 * 原作语义：vitality 消耗按强度在分段区间内线性插值（Effect 的
 * min/maxvitalitydecrease × multiplybymaxvitality），strengthchange 是
 * 每秒自然变化（负=自愈/自衰）。低于 activationThreshold 的强度完全无效果。</p>
 */
public final class AfflictionType {

    /** 一段效果：[minStrength, maxStrength) 内的 vitality 消耗率与自然变化。 */
    public record Tier(double minStrength, double maxStrength,
                       double minDrainPercentPerSecond, double maxDrainPercentPerSecond,
                       double strengthChangePerSecond) {
    }

    // ------------------------------------------------------------------
    // v1 病种（数值 = vanilla-baseline.md §2，逐条抄自 XML）
    // ------------------------------------------------------------------

    /** 钝器伤（REQUIREMENTS「内伤」）。原作：部位 0→2 分数，无自然变化。 */
    public static final AfflictionType BLUNT_TRAUMA = new AfflictionType(
            "blunttrauma", true, 200.0, 0.0,
            List.of(new Tier(0, 200, 0, 2, 0)));

    /** 出血（REQUIREMENTS「流血」）。原作：部位，四段递增掉血，-0.1/s 轻微自愈。 */
    public static final AfflictionType BLEEDING = new AfflictionType(
            "bleeding", true, 100.0, 0.0,
            List.of(new Tier(0, 20, 0, 2, -0.1),
                    new Tier(20, 40, 2, 4, -0.1),
                    new Tier(40, 60, 4, 6, -0.1),
                    new Tier(60, 100, 6, 10, -0.1)));

    /** 烧伤。原作：部位，三段递增掉血，无自然变化。 */
    public static final AfflictionType BURN = new AfflictionType(
            "burn", true, 200.0, 0.0,
            List.of(new Tier(0, 30, 0, 0.3, 0),
                    new Tier(30, 60, 0.3, 0.6, 0),
                    new Tier(60, 200, 0.6, 2, 0)));

    /** 失血。原作：全身，低于 5 无害，自衰 -0.1/s（止血才是根治）。 */
    public static final AfflictionType BLOODLOSS = new AfflictionType(
            "bloodloss", false, 100.0, 5.0,
            List.of(new Tier(0, 5, 0, 0, -0.1),
                    new Tier(5, 45, 0, 0.75, -0.1),
                    new Tier(45, 100, 0.75, 2, -0.1)));

    /** 缺氧。原作：全身，低于 1 无效果，1–50 只是预警不扣血；写入方是氧气模块。 */
    public static final AfflictionType OXYGEN_LOW = new AfflictionType(
            "oxygenlow", false, 200.0, 1.0,
            List.of(new Tier(1, 50, 0, 0, 0),
                    new Tier(50, 200, 0, 2, 0)));

    /** 眩晕。原作实测：0 伤害、无图标、-1/s 快衰——v1 定位为瞬时控制载体（见底表 §2.6）。头部指示。 */
    public static final AfflictionType STUN = new AfflictionType(
            "stun", false, 30.0, 0.0,
            List.of(new Tier(0, 30, 0, 0, -1.0)), true, true, 0.0);

    /**
     * 阿片中毒（底表 §7.5 头部系）。原作：全身/头部指示，0–50 无害仅色差，
     * 50–100 致死段 **maxvitalitydecrease=200 为原始点数（无 multiplybymaxvitality）**，
     * -0.5/s 自衰，图标阈值 50。
     */
    public static final AfflictionType OPIATE_OVERDOSE = new AfflictionType(
            "opiateoverdose", false, 100.0, 0.0,
            List.of(new Tier(0, 50, 0, 0, -0.5),
                    new Tier(50, 100, 0, 200, -0.5)), false, true, 0.0);

    /** 内脏损伤。原作：全身/躯干指示，0→2 分数，无自然变化（抗生素凝胶的副作用，原作同款）。 */
    public static final AfflictionType ORGAN_DAMAGE = new AfflictionType(
            "organdamage", false, 200.0, 0.0,
            List.of(new Tier(0, 200, 0, 2, 0)));

    /** 阿片类药物成瘾。原作：全身/头部指示，max 100，80+ 段有 0→20 原始点数消耗，
     * 分段自衰 −0.05/−0.1/s，并按段位推高戒断（0.15/0.25/0.5 每秒），图标阈值 20。 */
    public static final AfflictionType OPIATE_ADDICTION = new AfflictionType(
            "opiateaddiction", false, 100.0, 0.0,
            List.of(new Tier(0, 20, 0, 0, -0.05),
                    new Tier(20, 40, 0, 0, -0.05),
                    new Tier(40, 80, 0, 0, -0.1),
                    new Tier(80, 100, 0, 20, -0.1)), false, true, 20.0);

    /** 阿片类药物戒断反应。原作：全身/头部指示，20+ 段起有 0→20/20→50/50→200 **原始点数**消耗
     * （无 multiplybymaxvitality），分段自衰 −0.05/−0.1/−0.2/s，图标阈值 20。 */
    public static final AfflictionType OPIATE_WITHDRAWAL = new AfflictionType(
            "opiatewithdrawal", false, 100.0, 0.0,
            List.of(new Tier(0, 20, 0, 0, -0.05),
                    new Tier(20, 40, 0, 20, -0.1),
                    new Tier(40, 80, 20, 50, -0.1),
                    new Tier(80, 100, 50, 200, -0.2)), false, true, 20.0);

    /** v1 全部病症，顺序即文档顺序。 */
    public static final List<AfflictionType> ALL =
            List.of(BLUNT_TRAUMA, BLEEDING, BURN, BLOODLOSS, OXYGEN_LOW, STUN,
                    OPIATE_OVERDOSE, OPIATE_ADDICTION, OPIATE_WITHDRAWAL, ORGAN_DAMAGE);

    private static final Map<String, AfflictionType> BY_ID =
            ALL.stream().collect(Collectors.toMap(t -> t.id, Function.identity()));

    /** 原作各强度段的中文文案（afflictiondescription.*，简中官方文本）。 */
    private static final Map<String, List<DescRange>> DESCRIPTIONS = Map.of(
            "blunttrauma", List.of(
                    new DescRange(0, 40, "只有一处浅浅的淤青，算不上伤筋动骨。"),
                    new DescRange(40, 200, "受创处已经泛紫了，摸起来很痛。你怀疑有根骨头断了。")),
            "bleeding", List.of(
                    new DescRange(0, 20, "血液正从伤口处慢慢滴出。"),
                    new DescRange(20, 60, "血液从这个开放性伤口喷涌而出。"),
                    new DescRange(60, 100, "血液正以惊人的速度涌出。")),
            "burn", List.of(
                    new DescRange(0, 30, "皮肤泛红且干燥的。"),
                    new DescRange(30, 60, "红肿起泡，皮肤剥落。"),
                    new DescRange(60, 200, "皮肤发白，部分烧焦；这看起来很严重。")),
            "bloodloss", List.of(
                    new DescRange(0, 30, "你感到寒冷和疲惫。"),
                    new DescRange(30, 100, "你感到寒冷和头晕，可能离昏迷不远了。")),
            "oxygenlow", List.of(
                    new DescRange(0, 50, "焦虑和头痛暗示着低血氧水平。"),
                    new DescRange(50, 200, "嘴唇发青，皮肤苍白，大量出汗。")),
            "opiateoverdose", List.of(
                    new DescRange(0, 100, "你感到头晕目眩，难以呼吸。世界非常寒冷，甚至比平时更冷。")),
            "opiateaddiction", List.of(
                    new DescRange(0, 100, "你感觉很好...下次还能继续嗑。")),
            "opiatewithdrawal", List.of(
                    new DescRange(0, 50, "泪眼朦胧，汗流浃背，焦躁不安，渴望愈发强烈。"),
                    new DescRange(50, 100, "患者呼吸无力，肢体不自主震颤，有明显恶心症状。")),
            "organdamage", List.of(
                    new DescRange(0, 200, "你感到腹部一阵剧痛。没有看到任何伤口。")));

    /** 当前强度对应的原作阶段文案（无文案的类型返回空串）。 */
    public String descriptionAt(double strength) {
        for (DescRange range : DESCRIPTIONS.getOrDefault(id, List.of())) {
            if (strength >= range.minStrength() && strength < range.maxStrength()) {
                return range.text();
            }
        }
        return "";
    }

    private final String id;
    private final boolean limbSpecific;
    private final double maxStrength;
    private final double activationThreshold;
    private final List<Tier> tiers;
    /** true = XML 值是最大生命的分数（×MaxVitality）；false = 原始 vitality 点数。 */
    private final boolean multiplyByMaxVitality;
    /** 头部指示（原作 indicatorlimb="Head"）：UI 上显示在头部悬停里。 */
    private final boolean headIndicator;
    /** 低于此强度的病症在 UI 列表里隐藏（原作 showiconthreshold）。 */
    private final double showThreshold;

    /** 一段描述：[minStrength, maxStrength) 内显示的文案。 */
    public record DescRange(double minStrength, double maxStrength, String text) {
    }

    private AfflictionType(String id, boolean limbSpecific, double maxStrength,
                           double activationThreshold, List<Tier> tiers) {
        this(id, limbSpecific, maxStrength, activationThreshold, tiers, true, false, 0.0);
    }

    private AfflictionType(String id, boolean limbSpecific, double maxStrength,
                           double activationThreshold, List<Tier> tiers,
                           boolean multiplyByMaxVitality, boolean headIndicator, double showThreshold) {
        this.id = id;
        this.limbSpecific = limbSpecific;
        this.maxStrength = maxStrength;
        this.activationThreshold = activationThreshold;
        this.tiers = List.copyOf(tiers);
        this.multiplyByMaxVitality = multiplyByMaxVitality;
        this.headIndicator = headIndicator;
        this.showThreshold = showThreshold;
    }

    /** 原作 identifier，也是存档/NBT 里的稳定键。 */
    public String id() {
        return id;
    }

    /** 部位归属（true=按部位分实例；false=全身单实例）。 */
    public boolean limbSpecific() {
        return limbSpecific;
    }

    public double maxStrength() {
        return maxStrength;
    }

    public double activationThreshold() {
        return activationThreshold;
    }

    /**
     * 该强度下的 vitality 消耗（点数，vitality 满值 maxVitality 的语义）。
     *
     * <p>原作语义（Affliction.GetVitalityDecrease，源码已核对）：multiplybymaxvitality=true 时
     * XML 值是最大生命的分数（消耗 = Lerp × MaxVitality）；false 时为原始点数（如阿片中毒的 200）。
     * 低于激活阈值返回 0。</p>
     */
    public double vitalityDecrease(double strength, double maxVitality) {
        if (strength < activationThreshold) {
            return 0.0;
        }
        for (Tier tier : tiers) {
            if (strength < tier.maxStrength() || (strength == maxStrength && strength <= tier.maxStrength())) {
                double span = tier.maxStrength() - tier.minStrength();
                double t = span <= 0 ? 0 : (strength - tier.minStrength()) / span;
                t = Math.max(0, Math.min(1, t));
                double value = tier.minDrainPercentPerSecond()
                        + (tier.maxDrainPercentPerSecond() - tier.minDrainPercentPerSecond()) * t;
                return multiplyByMaxVitality ? value * maxVitality : value;
            }
        }
        return 0.0;
    }

    /** 该强度下每秒自然强度变化（负=自愈）。找不到段时按 0 处理。 */
    public double strengthChangePerSecond(double strength) {
        for (Tier tier : tiers) {
            if (strength >= tier.minStrength() && strength < tier.maxStrength()) {
                return tier.strengthChangePerSecond();
            }
        }
        Tier last = tiers.get(tiers.size() - 1);
        return strength >= last.maxStrength() ? last.strengthChangePerSecond() : 0.0;
    }

    /** 按 id 查类型；未知 id 返回 null（存档兼容用）。 */
    public static AfflictionType byId(String id) {
        return BY_ID.get(id);
    }

    /** 头部指示（原作 indicatorlimb="Head"）：UI 显示在头部悬停里。 */
    public boolean headIndicator() {
        return headIndicator;
    }

    /** 低于此强度不在 UI 列表显示（原作 showiconthreshold）。 */
    public double showThreshold() {
        return showThreshold;
    }
}
