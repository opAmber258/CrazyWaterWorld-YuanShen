package com.opamb.barotrauma.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 泳姿判定的纯逻辑测试：净空足够/不足/迟滞带三种情况。
 */
class SwimClearanceTest {

    private static final double HEIGHT = 1.8D;

    @Test
    @DisplayName("净空足够：不管当前站还是游，都判站立")
    void enoughClearanceStands() {
        assertFalse(SwimClearance.decideSwim(HEIGHT + 0.5D, HEIGHT, false));
        assertFalse(SwimClearance.decideSwim(HEIGHT + 0.5D, HEIGHT, true));
    }

    @Test
    @DisplayName("净空不足：不管当前站还是游，都判游泳")
    void lowCeilingSwims() {
        assertTrue(SwimClearance.decideSwim(HEIGHT - 0.5D, HEIGHT, false));
        assertTrue(SwimClearance.decideSwim(HEIGHT - 0.5D, HEIGHT, true));
    }

    @Test
    @DisplayName("迟滞带内保持当前状态，不会自己翻")
    void bandKeepsCurrentState() {
        double available = HEIGHT; // 正好等于身高，落在带内
        assertFalse(SwimClearance.decideSwim(available, HEIGHT, false),
                "站在带内不应该被踢成游泳");
        assertTrue(SwimClearance.decideSwim(available, HEIGHT, true),
                "游在带内不应该被踢成站立");
    }

    @Test
    @DisplayName("带内反复调用跟随输入状态，不产生双稳态振荡")
    void bandDoesNotOscillate() {
        boolean state = false;
        for (int i = 0; i < 20; i++) {
            boolean next = SwimClearance.decideSwim(HEIGHT + 0.02D, HEIGHT, state);
            assertFalse(next, "站在带内第 " + i + " 次调用被翻成游泳");
            state = next;
        }
        state = true;
        for (int i = 0; i < 20; i++) {
            boolean next = SwimClearance.decideSwim(HEIGHT - 0.08D, HEIGHT, state);
            assertTrue(next, "游在带内第 " + i + " 次调用被翻成站立");
            state = next;
        }
    }

    @Test
    @DisplayName("阈值边界：刚好够站 / 刚好不够站")
    void thresholdEdges() {
        assertFalse(SwimClearance.decideSwim(HEIGHT + SwimClearance.STAND_MARGIN, HEIGHT, true),
                "达到够站阈值就应该站");
        assertTrue(SwimClearance.decideSwim(HEIGHT - SwimClearance.SWIM_MARGIN - 1.0E-6D,
                HEIGHT, false), "低于游泳阈值就应该游");
    }
}
