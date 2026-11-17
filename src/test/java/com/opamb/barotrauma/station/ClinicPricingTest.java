package com.opamb.barotrauma.station;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 医疗诊所收费公式（原作 base + strength × multiplier）。 */
class ClinicPricingTest {

    @Test
    @DisplayName("普通病症默认倍率 1")
    void defaultMultiplier() {
        assertEquals(1.0D, ClinicPricing.multiplier("blunttrauma"));
        assertEquals(100, ClinicPricing.healCost("blunttrauma", 100));
        assertEquals(200, ClinicPricing.healCost("organdamage", 200));
    }

    @Test
    @DisplayName("原作例外倍率：出血 2.5、烧伤 2.25、辐射 4")
    void specialMultipliers() {
        assertEquals(125, ClinicPricing.healCost("bleeding", 50));
        assertEquals(225, ClinicPricing.healCost("burn", 100));
        assertEquals(400, ClinicPricing.healCost("radiationsickness", 100));
        assertEquals(50, ClinicPricing.healCost("oxygenlow", 100));
        assertEquals(75, ClinicPricing.healCost("bloodloss", 50));
    }

    @Test
    @DisplayName("强度为 0/负数/非有限值时免费")
    void invalidStrengthIsFree() {
        assertEquals(0, ClinicPricing.healCost("bleeding", 0));
        assertEquals(0, ClinicPricing.healCost("bleeding", -10));
        assertEquals(0, ClinicPricing.healCost("bleeding", Double.NaN));
    }
}
