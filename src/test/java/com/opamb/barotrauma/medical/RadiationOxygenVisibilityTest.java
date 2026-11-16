package com.opamb.barotrauma.medical;

import com.opamb.barotrauma.client.MedicalBodyRenderer;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复现用户报告："注入辐射病/缺氧后，健康栏里看不到"。
 *
 * <p>重点排查两件事：</p>
 * <ol>
 *   <li>这两种病症是否有**自然衰减**，导致注入后很快消失；</li>
 *   <li>衰减到 0 之前，UI 是否**一直**能显示（还是只有某个窗口期）。</li>
 * </ol>
 */
class RadiationOxygenVisibilityTest {

    /** 辐射病注入 75 后，每秒自衰多少？多久彻底消失？ */
    @Test
    void radiationDecaysSlowlyButEventuallyReachesZero() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.RADIATION, null, 75);

        // 25+ 段自衰 -0.2/s：75 → 25 用 250 秒，之后 -0.07/s 又拖几百秒。
        // 结论：辐射病**能撑 15 分钟以上**，不存在"注入后立刻消失"的说法。
        int seconds = 0;
        while (container.strength(AfflictionType.RADIATION, null) > 0 && seconds < 5000) {
            container.tickSecond();
            seconds++;
        }
        System.out.printf("[诊断] 辐射病 75 自衰到 0 用了 %d 秒（约 %.1f 分钟）%n",
                seconds, seconds / 60.0);
        assertEquals(0.0, container.strength(AfflictionType.RADIATION, null), 1.0E-9,
                "辐射病最终会自衰到 0");
        assertTrue(seconds > 600,
                "辐射病必须能撑过 10 分钟——如果几秒就没了，说明注入被某处清掉了");
    }

    /** 缺氧注入 100 后会不会自衰？ */
    @Test
    void oxygenLowDoesNotDecayOnItsOwn() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.OXYGEN_LOW, null, 100);
        for (int sec = 0; sec < 30; sec++) {
            container.tickSecond();
        }
        assertEquals(100.0, container.strength(AfflictionType.OXYGEN_LOW, null), 1.0E-9,
                "缺氧没有自然衰减，必须靠回氧路径降低");
        // 而且必须一直能在健康栏看到
        assertTrue(MedicalBodyRenderer.globalLines(container.rows()).stream()
                        .anyMatch(l -> l.text().contains("缺氧")),
                "缺氧 100 必须在健康栏显示");
    }

    /** 注入的**第一秒内**就能看到（不是要等某个 tick）。 */
    @Test
    void bothVisibleImmediatelyAfterInjection() {
        AfflictionContainer container = new AfflictionContainer();
        container.add(AfflictionType.RADIATION, null, 75);
        container.add(AfflictionType.OXYGEN_LOW, null, 100);

        var texts = MedicalBodyRenderer.globalLines(container.rows())
                .stream().map(MedicalBodyRenderer.DisplayLine::text).toList();
        System.out.println("[诊断] 注入后立刻可显示: " + texts);
        assertTrue(texts.stream().anyMatch(t -> t.contains("辐射病")), "实际: " + texts);
        assertTrue(texts.stream().anyMatch(t -> t.contains("缺氧")), "实际: " + texts);
    }
}
