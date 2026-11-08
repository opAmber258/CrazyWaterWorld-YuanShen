package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 医疗调试器（开发者工具）用到的容器能力：
 * 直接设置/增减病症强度、活力的调试覆盖、以及覆盖值的存档行往返。
 */
class AfflictionContainerDebugTest {

    @Test
    void setStrengthWritesExactValueAndClamps() {
        AfflictionContainer container = new AfflictionContainer();

        assertEquals(60, container.setStrength(AfflictionType.BLUNT_TRAUMA, Limb.LEFT_LEG, 60), 1.0E-6);
        assertEquals(60, container.strength(AfflictionType.BLUNT_TRAUMA, Limb.LEFT_LEG), 1.0E-6);

        // 超过上限钳到 maxStrength
        assertEquals(AfflictionType.BURN.maxStrength(),
                container.setStrength(AfflictionType.BURN, Limb.TORSO, 9999), 1.0E-6);
        // 负数钳到 0（实例被清掉）
        container.setStrength(AfflictionType.BURN, Limb.TORSO, -5);
        assertEquals(0, container.strength(AfflictionType.BURN, Limb.TORSO), 1.0E-6);
        // 非有限值按 0
        container.setStrength(AfflictionType.BLEEDING, Limb.HEAD, Double.NaN);
        assertEquals(0, container.strength(AfflictionType.BLEEDING, Limb.HEAD), 1.0E-6);
    }

    @Test
    void addStrengthSaturatesAtBothEnds() {
        AfflictionContainer container = new AfflictionContainer();

        assertEquals(30, container.addStrength(AfflictionType.BLEEDING, Limb.LEFT_ARM, 30), 1.0E-6);
        assertEquals(20, container.addStrength(AfflictionType.BLEEDING, Limb.LEFT_ARM, -10), 1.0E-6);
        assertEquals(0, container.addStrength(AfflictionType.BLEEDING, Limb.LEFT_ARM, -999), 1.0E-6);
        assertEquals(AfflictionType.BLOODLOSS.maxStrength(),
                container.addStrength(AfflictionType.BLOODLOSS, null, 9999), 1.0E-6);
    }

    @Test
    void limbSpecificTypeWithoutLimbIsIgnored() {
        AfflictionContainer container = new AfflictionContainer();
        assertEquals(0, container.setStrength(AfflictionType.BLUNT_TRAUMA, null, 50), 1.0E-6);
        assertTrue(container.rows().isEmpty());
    }

    @Test
    void vitalityOverrideTakesPrecedenceOverAfflictions() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.BLUNT_TRAUMA, Limb.TORSO, 80);
        double computed = container.rawVitality(100.0);
        assertEquals(20, computed, 1.0E-6);

        // 覆盖后不再看病症，负数直接进倒地区间
        container.setVitalityOverride(-40);
        assertEquals(-40, container.rawVitality(100.0), 1.0E-6);
        assertEquals(0, container.vitality(100.0), 1.0E-6);

        // 覆盖也会被钳到 [−100, max]
        container.setVitalityOverride(1.0E9);
        assertEquals(100, container.rawVitality(100.0), 1.0E-6);
        container.setVitalityOverride(-1.0E9);
        assertEquals(AfflictionContainer.MIN_VITALITY, container.rawVitality(100.0), 1.0E-6);

        // 取消覆盖回到按病症计算
        container.clearVitalityOverride();
        assertNull(container.vitalityOverride());
        assertEquals(20, container.rawVitality(100.0), 1.0E-6);
    }

    @Test
    void overrideRowRoundTripsThroughRows() {
        AfflictionContainer container = new AfflictionContainer();
        container.setStrength(AfflictionType.BLEEDING, Limb.RIGHT_LEG, 25);
        container.setVitalityOverride(-12.5);

        List<String> rows = container.rows();
        assertTrue(rows.contains("v|-12.5"), "覆盖值应写进存档行: " + rows);

        AfflictionContainer restored = new AfflictionContainer();
        for (String row : rows) {
            restored.loadRow(row);
        }
        assertEquals(-12.5, restored.vitalityOverride(), 1.0E-6);
        assertEquals(25, restored.strength(AfflictionType.BLEEDING, Limb.RIGHT_LEG), 1.0E-6);
        assertEquals(-12.5, restored.rawVitality(100.0), 1.0E-6);
    }

    @Test
    void clearAndCopyHandleOverride() {
        AfflictionContainer container = new AfflictionContainer();
        container.setVitalityOverride(50);
        container.clear();
        assertNull(container.vitalityOverride());

        container.setVitalityOverride(-30);
        AfflictionContainer clone = new AfflictionContainer();
        clone.copyFrom(container);
        assertEquals(-30, clone.vitalityOverride(), 1.0E-6);

        // 克隆是值拷贝：原容器后续改/清覆盖，克隆保持自己的值
        container.clearVitalityOverride();
        container.setVitalityOverride(40);
        assertEquals(-30, clone.vitalityOverride(), 1.0E-6);
    }

    @Test
    void clearAllInDebuggerAlsoResetsOverride() {
        // 对应 CLEAR_ALL 动作：清空全部后活力回到按病症计算 = 满值
        AfflictionContainer container = new AfflictionContainer();
        container.setStrength(AfflictionType.BURN, Limb.TORSO, 100);
        container.setVitalityOverride(-100);
        container.clear();
        assertEquals(100, container.rawVitality(100.0), 1.0E-6);
        assertTrue(container.rows().isEmpty());
    }
}
