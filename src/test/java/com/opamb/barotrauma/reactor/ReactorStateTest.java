package com.opamb.barotrauma.reactor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 反应堆纯数值状态机：温度平衡、自动控制、燃料上限、起火/熔毁。 */
class ReactorStateTest {

    private static final double DT = 0.05D;

    private static void tick(ReactorState state, double seconds, double fuel, double load) {
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            state.tick(DT, fuel, load);
        }
    }

    @Test
    void 停堆默认不发电也不升温() {
        ReactorState state = new ReactorState();
        tick(state, 10.0D, 100.0D, 0.0D);
        assertFalse(state.powerOn());
        assertEquals(0.0D, state.outputKw(), 1.0E-6);
        assertEquals(0.0D, state.temperature(), 1.0E-6);
    }

    @Test
    void 裂变与涡轮把温度带到平衡点() {
        ReactorState state = new ReactorState();
        state.setAutoTemp(false);
        state.setPowerOn(true);
        state.setTargetFission(50.0D);
        state.setTargetTurbine(50.0D);
        // 平衡温度 = 50 × (100/100) × 2 - 50 = 50
        tick(state, 30.0D, 100.0D, 0.0D);
        assertEquals(50.0D, state.temperature(), 3.0D);
        assertEquals(50.0D, state.fission(), 2.0D);
        assertEquals(2500.0D, state.outputKw(), 200.0D);
    }

    @Test
    void 自动控制把温度压在允许区并跟随负载() {
        ReactorState state = new ReactorState();
        state.setPowerOn(true);
        state.setAutoTemp(true);
        tick(state, 120.0D, 100.0D, 2000.0D);
        assertTrue(state.temperature() > ReactorState.ALLOWED_TEMP_MIN - 1.0D
                        && state.temperature() < ReactorState.ALLOWED_TEMP_MAX + 1.0D,
                "温度应在允许区，实际 " + state.temperature());
        assertTrue(state.outputKw() > 1000.0D, "有负载时应有输出，实际 " + state.outputKw());
        assertTrue(state.outputKw() <= ReactorState.MAX_POWER_KW + 1.0E-6D);
    }

    @Test
    void 可用燃料限制裂变目标() {
        ReactorState state = new ReactorState();
        state.setAutoTemp(false);
        state.setPowerOn(true);
        state.setTargetFission(100.0D);
        state.setTargetTurbine(100.0D);
        tick(state, 20.0D, 20.0D, 0.0D);
        assertTrue(state.fission() <= 20.0D + 1.0E-6D, "裂变不能超过可用燃料");
    }

    @Test
    void 没有燃料就没有输出() {
        ReactorState state = new ReactorState();
        state.setAutoTemp(false);
        state.setPowerOn(true);
        state.setTargetFission(100.0D);
        state.setTargetTurbine(100.0D);
        tick(state, 20.0D, 0.0D, 2000.0D);
        assertEquals(0.0D, state.fission(), 1.0E-6);
        assertEquals(0.0D, state.outputKw(), 1.0E-6);
    }

    @Test
    void 持续超温最终熔毁且输出归零() {
        ReactorState state = new ReactorState();
        state.setAutoTemp(false);
        state.setPowerOn(true);
        state.setTargetFission(100.0D);
        state.setTargetTurbine(0.0D);
        int events = 0;
        int steps = (int) Math.round(160.0D / DT);
        for (int i = 0; i < steps; i++) {
            events |= state.tick(DT, 400.0D, 0.0D);
        }
        assertTrue(state.meltedDown(), "超过允许温度 120 秒应熔毁");
        assertTrue((events & ReactorState.EVENT_MELTDOWN) != 0);
        assertEquals(0.0D, state.outputKw(), 1.0E-6);
        assertFalse(state.powerOn());
    }

    @Test
    void 超过最佳温度会触发起火事件() {
        ReactorState state = new ReactorState();
        state.setAutoTemp(false);
        state.setPowerOn(true);
        state.setTargetFission(100.0D);
        state.setTargetTurbine(0.0D);
        state.setTemperatureForTest(65.0D);
        int events = 0;
        for (int i = 0; i < (int) Math.round(25.0D / DT); i++) {
            events |= state.tick(DT, 400.0D, 0.0D);
        }
        assertTrue((events & ReactorState.EVENT_FIRE) != 0, "温度高于 60 持续 20 秒应起火");
    }

    @Test
    void 温度增益会衰减() {
        ReactorState state = new ReactorState();
        state.setAutoTemp(false);
        state.applyTemperatureBoost(25.0D);
        assertEquals(25.0D, state.temperatureBoost(), 1.0E-6);
        tick(state, 5.0D, 100.0D, 0.0D);
        assertEquals(20.0D, state.temperatureBoost(), 1.0D);
    }

    @Test
    void 最佳与允许裂变区随燃料变化() {
        assertTrue(ReactorState.optimalFissionMin(100.0D) <= ReactorState.optimalFissionMax(100.0D));
        assertTrue(ReactorState.allowedFissionMin(100.0D) <= ReactorState.allowedFissionMax(100.0D));
        assertEquals(100.0D, ReactorState.allowedFissionMax(400.0D), 1.0E-6);
        assertEquals(0.0D, ReactorState.allowedFissionMax(0.0D), 1.0E-6);
    }

    @Test
    void 快照恢复保留停机与熔毁状态() {
        ReactorState state = new ReactorState();
        state.setAutoTemp(false);
        state.setPowerOn(true);
        state.setTargetFission(42.0D);
        state.setTargetTurbine(24.0D);
        tick(state, 5.0D, 100.0D, 0.0D);

        ReactorState restored = new ReactorState();
        restored.restore(state.snapshot());
        assertEquals(state.powerOn(), restored.powerOn());
        assertEquals(state.targetFission(), restored.targetFission(), 1.0E-6);
        assertEquals(state.temperature(), restored.temperature(), 1.0E-6);
    }
}
