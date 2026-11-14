package com.opamb.barotrauma.station;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 商店价格/出售/补货纯数学。 */
class StorePricingTest {

    @Test
    @DisplayName("购买价随商店修正 ±5% 浮动")
    void buyPriceUsesModifier() {
        assertEquals(100, StorePricing.buyPrice(100, 0));
        assertEquals(105, StorePricing.buyPrice(100, 5));
        assertEquals(95, StorePricing.buyPrice(100, -5));
        assertEquals(1, StorePricing.buyPrice(1, -5));
        assertEquals(0, StorePricing.buyPrice(0, 5));
    }

    @Test
    @DisplayName("出售价是原价 30%")
    void sellPriceIsThirtyPercent() {
        assertEquals(30, StorePricing.sellPrice(100, 0));
        assertEquals(32, StorePricing.sellPrice(100, 5));
        assertEquals(29, StorePricing.sellPrice(100, -5));
        assertEquals(75, StorePricing.sellPrice(250, 0));
    }

    @Test
    @DisplayName("每次到访补 1 个，封顶初始 +5")
    void restockIsCapped() {
        assertEquals(6, StorePricing.restock(5, 5));
        assertEquals(10, StorePricing.restock(9, 5));
        assertEquals(10, StorePricing.restock(10, 5));
        assertEquals(1, StorePricing.restock(0, 0));
    }

    @Test
    @DisplayName("站点价格修正固定在 ±5 且确定性")
    void modifierIsDeterministic() {
        int modifier = StorePricing.modifierFor(20000, 0);
        assertTrue(modifier >= -5 && modifier <= 5);
        assertEquals(modifier, StorePricing.modifierFor(20000, 0));
    }
}
