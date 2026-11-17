package com.opamb.barotrauma.station;

import java.util.Map;

/**
 * 医疗诊所收费。原作公式：
 * {@code (1 - Reputation/100 * 0.1) * (BaseHealCost + Strength * HealCostMultiplier)}。
 *
 * <p>本模组暂不做声望，所以只保留后半段；大多数病症 BaseHealCost=0、
 * HealCostMultiplier=1，例外见 {@link #MULTIPLIERS}（数值来自原作
 * {@code Content/Afflictions.xml}）。</p>
 */
public final class ClinicPricing {

    /** 原作 Afflictions.xml 的 healcostmultiplier；未列出的默认 1.0。 */
    private static final Map<String, Double> MULTIPLIERS = Map.of(
            "bleeding", 2.5D,
            "burn", 2.25D,
            "oxygenlow", 0.5D,
            "bloodloss", 1.5D,
            "opiateoverdose", 1.25D,
            "opiateaddiction", 2.85D,
            "radiationsickness", 4.0D);

    private ClinicPricing() {
    }

    public static double multiplier(String afflictionId) {
        return MULTIPLIERS.getOrDefault(afflictionId, 1.0D);
    }

    /** 治疗一条病症的价格：强度 × 倍率，四舍五入；强度 ≤0 返回 0。 */
    public static int healCost(String afflictionId, double strength) {
        if (!Double.isFinite(strength) || strength <= 0.0D) {
            return 0;
        }
        return Math.max(0, (int) Math.round(strength * multiplier(afflictionId)));
    }
}
