package com.opamb.barotrauma.medical.affliction;

import java.util.List;

/**
 * 药品的治疗规格——数值逐条来自 vanilla-baseline.md §4（原作 medical.xml，
 * OnSuccess 路径；v1 无技能系统恒走 Success）。
 *
 * <p>perSecond &gt; 0 = 治疗（按秒 ReduceAffliction）；&lt; 0 = 副作用（按秒施加病症，
 * 原作吗啡会加剧缺氧、凝胶会造成器官损伤——v1 用钝器伤近似器官损伤）。</p>
 */
public record TreatmentSpec(String displayName, List<Effect> effects, boolean multiUse) {

    /** 一条效果：目标病症、每秒治疗量（负=副作用）、持续秒数。 */
    public record Effect(AfflictionType type, double perSecond, double seconds) {
    }

    /** 绷带：止血 1.5/s×20s，烧伤 0.75/s×20s。一次性。 */
    public static final TreatmentSpec BANDAGE = new TreatmentSpec("绷带", List.of(
            new Effect(AfflictionType.BLEEDING, 1.5, 20),
            new Effect(AfflictionType.BURN, 0.75, 20)), false);

    /** 抗生素凝胶：止血 30/s×2s（猛药），副作用内脏损伤 0.6/s×5s（原作 organdamage 同款数值）。可使用 8 次。 */
    public static final TreatmentSpec ANTIBIOTIC_GLUE = new TreatmentSpec("抗生素凝胶", List.of(
            new Effect(AfflictionType.BLEEDING, 30, 2),
            new Effect(AfflictionType.BURN, 7.5, 2),
            new Effect(AfflictionType.ORGAN_DAMAGE, -0.6, 5)), true);

    /**
     * 吗啡：基于原作数值，**缺氧副作用按用户指令移除**（2026-11-07：原作确有缺氧 +1/s，
     * 用户明确要求去掉——已按流程询问确认）。损伤类 −2/s、器官损伤 −2/s、
     * 烧伤 −0.1/s、副作用：阿片成瘾 +1/s、阿片中毒 +1/s。一次性。
     */
    public static final TreatmentSpec MORPHINE = new TreatmentSpec("吗啡", List.of(
            new Effect(AfflictionType.BLUNT_TRAUMA, 2, 20),
            new Effect(AfflictionType.ORGAN_DAMAGE, 2, 20),
            new Effect(AfflictionType.BURN, 0.1, 20),
            new Effect(AfflictionType.OPIATE_ADDICTION, -1, 20),
            new Effect(AfflictionType.OPIATE_OVERDOSE, -1, 20)), false);

    /**
     * 芬太尼：**严格原作数值**（antidama2：猛效段 5s + 成瘾尾段 25s）——
     * 损伤类 −15/s×5s、烧伤 −1/s×5s、副作用：缺氧 +5/s×5s、阿片中毒 +5/s×5s、阿片成瘾 +1/s×25s。一次性。
     */
    public static final TreatmentSpec FENTANYL = new TreatmentSpec("芬太尼", List.of(
            new Effect(AfflictionType.BLUNT_TRAUMA, 15, 5),
            new Effect(AfflictionType.ORGAN_DAMAGE, 15, 5),
            new Effect(AfflictionType.BURN, 1, 5),
            new Effect(AfflictionType.OXYGEN_LOW, -5, 5),
            new Effect(AfflictionType.OPIATE_OVERDOSE, -5, 5),
            new Effect(AfflictionType.OPIATE_ADDICTION, -1, 25)), false);

    /** 血袋：失血 8/s×15s（合计 120 ≥ 失血上限 100，能完全回满）。一次性。 */
    public static final TreatmentSpec BLOOD_PACK = new TreatmentSpec("血袋", List.of(
            new Effect(AfflictionType.BLOODLOSS, 8, 15)), false);

    /** 生理盐水：失血 2.5/s×10s（合计 25，廉价慢速）。一次性。 */
    public static final TreatmentSpec SALINE = new TreatmentSpec("生理盐水", List.of(
            new Effect(AfflictionType.BLOODLOSS, 2.5, 10)), false);

    /** 纳洛酮：阿片中毒/成瘾各 2/s×30s（原作 antinarc 同款；戒断 v1 无来源暂不处理）。一次性。 */
    public static final TreatmentSpec NALOXONE = new TreatmentSpec("纳洛酮", List.of(
            new Effect(AfflictionType.OPIATE_OVERDOSE, 2, 30),
            new Effect(AfflictionType.OPIATE_ADDICTION, 2, 30)), false);
}
