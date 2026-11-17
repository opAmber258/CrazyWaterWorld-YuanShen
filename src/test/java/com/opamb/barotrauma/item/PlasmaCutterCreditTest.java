package com.opamb.barotrauma.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** 切割次数结算：每 10 次扣 1 点氧气，退出前未结算的次数不能丢。 */
class PlasmaCutterCreditTest {

    @Test
    @DisplayName("9 次 + 第 10 次：扣 1 点氧气，计数归零")
    void tenthCutConsumesOxygen() {
        assertArrayEquals(new int[]{0, 1}, PlasmaCutterCredit.applyCredit(9));
    }

    @Test
    @DisplayName("普通一次切割只累计，不扣氧")
    void singleCutOnlyAccumulates() {
        assertArrayEquals(new int[]{1, 0}, PlasmaCutterCredit.applyCredit(0));
    }

    @Test
    @DisplayName("跨过多个单位：19 次 + 1 = 扣 1 点、剩 10")
    void carriesAcrossUnits() {
        assertArrayEquals(new int[]{10, 1}, PlasmaCutterCredit.applyCredit(19));
    }
}
