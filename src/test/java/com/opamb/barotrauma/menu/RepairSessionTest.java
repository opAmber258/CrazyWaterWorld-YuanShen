package com.opamb.barotrauma.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepairSessionTest {
    @Test
    void stockTerminalAndReactorUseTheirOwnSkillRequirements() {
        double terminal = RepairSession.degreeOfSuccess(RepairSession.FIXED_SKILL, 40);
        double reactor = RepairSession.degreeOfSuccess(RepairSession.FIXED_SKILL, 55);
        assertEquals(0.5D, terminal, 1.0E-9);
        assertEquals(0.425D, reactor, 1.0E-9);
        assertEquals(15.0D, RepairSession.durationSeconds(terminal), 1.0E-9);
        assertEquals(16.5D, RepairSession.durationSeconds(reactor), 1.0E-9);
    }
}
