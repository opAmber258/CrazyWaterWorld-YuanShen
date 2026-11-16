package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.client.MedicalBodyRenderer;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端护栏：**注入 → rows() 序列化 → 客户端解析 → globalLines 输出**。
 *
 * <p>这组测试直接复现用户报告："注入了缺氧/辐射病，但健康栏里看不到"。
 * 逐段验证，任何一段断掉都会红。</p>
 */
class AfflictionDisplayChainTest {

    /** 注入全身病症后，rows() 必须产出 "id|强度"（部位类型才是 "id|LIMB|强度"）。 */
    @Test
    void globalAfflictionsSerializeAsTwoPartRows() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.RADIATION, null, 75);
        container.add(AfflictionType.OXYGEN_LOW, null, 100);

        List<String> rows = container.rows();
        assertTrue(rows.contains("radiationsickness|75.0"), "辐射病行格式: " + rows);
        assertTrue(rows.contains("oxygenlow|100.0"), "缺氧行格式: " + rows);
    }

    /** 客户端解析后必须在 globalLines 里出现（这是健康栏那一行文字的数据源）。 */
    @Test
    void globalLinesShowsRadiationAndOxygenLow() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.RADIATION, null, 75);
        container.add(AfflictionType.OXYGEN_LOW, null, 100);
        List<String> rows = container.rows();

        List<MedicalBodyRenderer.DisplayLine> lines = MedicalBodyRenderer.globalLines(rows);
        String all = String.join(" | ", lines.stream().map(l -> l.text()).toList());
        assertTrue(all.contains("辐射病"), "健康栏必须能看到辐射病，实际: [" + all + "]");
        assertTrue(all.contains("缺氧"), "健康栏必须能看到缺氧，实际: [" + all + "]");
    }

    /** 中文显示名存在（曾经缺 radiationsickness，UI 直接露出 id）。 */
    @Test
    void displayNameIsChineseNotRawId() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.RADIATION, null, 75);
        String text = MedicalBodyRenderer.globalLines(container.rows()).get(0).text();
        assertTrue(text.startsWith("辐射病"), "应以中文名开头，实际: " + text);
        assertTrue(!text.contains("radiation"), "不能露出原始 id，实际: " + text);
    }

    /**
     * 零 vitality 消耗的病症（辐射病/缺氧低段）仍必须产生文字——
     * 它们不直接扣血，但是"你中招了"的唯一信号。
     *
     * <p>注意 `showThreshold` 仍然生效（原作 showiconthreshold）：
     * 辐射病阈值 25，低于它不显示。这里用 25 以上验证。</p>
     */
    @Test
    void zeroDrainAfflictionsStillProduceText() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.RADIATION, null, 40);
        List<MedicalBodyRenderer.DisplayLine> lines = MedicalBodyRenderer.globalLines(container.rows());
        assertEquals(1, lines.size(), "有强度的辐射病必须出现在健康栏");
        assertEquals(0.0, AfflictionType.RADIATION.vitalityDecrease(40, 100.0), 1.0E-9,
                "辐射病本身不直接扣 vitality（伤害由烧伤/器官损伤结算）");
    }

    /** 低于 showiconthreshold 时不显示（原作 showiconthreshold=25）。 */
    @Test
    void belowShowThresholdIsHidden() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.RADIATION, null, 20);
        assertEquals(0, MedicalBodyRenderer.globalLines(container.rows()).size(),
                "辐射病 20 < showiconthreshold 25，不应显示");
    }

    /**
     * 原作 indicatorlimb：缺氧/失血/辐射病/器官损伤挂在**胸口**，
     * 眩晕/阿片系挂在**头部**。点开胸口必须能看到缺氧和辐射病。
     */
    @Test
    void globalAfflictionsAppearUnderTheirIndicatorLimb() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.OXYGEN_LOW, null, 100);
        container.add(AfflictionType.RADIATION, null, 75);
        List<String> rows = container.rows();

        String chest = joinTexts(MedicalBodyRenderer.linesForLimb(rows, Limb.TORSO));
        assertTrue(chest.contains("缺氧"), "胸口必须显示缺氧（原作 indicatorlimb=Torso），实际: " + chest);
        assertTrue(chest.contains("辐射病"), "胸口必须显示辐射病，实际: " + chest);

        String head = joinTexts(MedicalBodyRenderer.linesForLimb(rows, Limb.HEAD));
        assertTrue(!head.contains("缺氧"), "缺氧不应挂在头部，实际: " + head);
    }

    /** 眩晕挂在头部（原作 indicatorlimb=Head）。 */
    @Test
    void stunAppearsUnderHead() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.STUN, null, 10);
        String head = joinTexts(MedicalBodyRenderer.linesForLimb(container.rows(), Limb.HEAD));
        assertTrue(head.contains("眩晕"), "头部必须显示眩晕，实际: " + head);
    }

    private static String joinTexts(List<MedicalBodyRenderer.DisplayLine> lines) {
        return String.join(" | ", lines.stream().map(MedicalBodyRenderer.DisplayLine::text).toList());
    }
}
