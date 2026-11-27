package com.opamb.barotrauma.ship;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CoilgunTriggerTest {
    @Test
    void earlyAndLateRefreshesDoNotChangeFirstShotOrCadence() {
        CoilgunTrigger trigger = new CoilgunTrigger();
        List<Long> fired = new ArrayList<>();
        Set<Long> inputs = Set.of(100L, 103L, 109L, 112L, 117L, 120L);
        for (long tick = 100; tick < 124; tick++) {
            if (inputs.contains(tick)) trigger.press(tick);
            if (trigger.held(tick) && trigger.cooldown(tick) == 0) {
                fired.add(tick);
                trigger.shot(tick);
            }
        }
        assertEquals(List.of(100L, 105L, 110L, 115L, 120L), fired);
    }

    @Test
    void tapAndReleaseDoNotSkipReloadAndLostReleaseExpires() {
        CoilgunTrigger trigger = new CoilgunTrigger();
        assertTrue(trigger.press(50));
        trigger.shot(50);
        trigger.release();
        assertFalse(trigger.held(51));
        assertTrue(trigger.press(52));
        assertEquals(3, trigger.cooldown(52));
        assertFalse(trigger.press(55));
        assertEquals(0, trigger.cooldown(55));
        assertTrue(trigger.held(64));
        assertFalse(trigger.held(65));
    }
}
