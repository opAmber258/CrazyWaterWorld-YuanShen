package com.opamb.barotrauma.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 声纳归属判定：已确认优先，未确认时用距离 + 方块船核心消歧。 */
class SonarShipOwnershipTest {

    @Test
    @DisplayName("已确认属于物理船：旁边有方块船核心也仍归物理船")
    void confirmedWinsEvenWithBlockShipNearby() {
        assertTrue(SonarShipOwnership.ownedByPhysicalShip(true, true, true));
        assertTrue(SonarShipOwnership.ownedByPhysicalShip(true, false, true));
    }

    @Test
    @DisplayName("未确认但靠近物理船、附近没有方块船核心：归物理船")
    void nearShipWithoutBlockShipIsPhysical() {
        assertTrue(SonarShipOwnership.ownedByPhysicalShip(false, true, false));
    }

    @Test
    @DisplayName("未确认且附近有方块船核心：归方块船")
    void blockShipNearbyDisambiguates() {
        assertFalse(SonarShipOwnership.ownedByPhysicalShip(false, true, true));
        assertFalse(SonarShipOwnership.ownedByPhysicalShip(false, false, true));
    }

    @Test
    @DisplayName("未确认又不在物理船附近：不归物理船")
    void farAwayIsNotPhysical() {
        assertFalse(SonarShipOwnership.ownedByPhysicalShip(false, false, false));
    }
}
