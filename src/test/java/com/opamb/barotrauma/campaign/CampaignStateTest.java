package com.opamb.barotrauma.campaign;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 战役存档：共享资金、快照/回档、NBT 往返。 */
class CampaignStateTest {

    @Test
    @DisplayName("回档恢复节点/资金/轮数/阶段")
    void rollbackRestoresSnapshot() {
        CampaignState state = new CampaignState();
        int startNode = state.currentNode();
        int startCredits = state.credits();

        state.takeSnapshot();
        state.addCredits(500);
        state.beginTravel(1, 100L);
        state.arrive();

        assertEquals(1, state.currentNode());
        assertEquals(startCredits + 500, state.credits());
        assertEquals(1, state.roundsCompleted());

        assertTrue(state.rollback());
        assertEquals(startNode, state.currentNode());
        assertEquals(startCredits, state.credits());
        assertEquals(0, state.roundsCompleted());
        assertEquals(RoundPhase.AT_NODE, state.round().phase());
    }

    @Test
    @DisplayName("没有快照时回档失败")
    void rollbackWithoutSnapshotFails() {
        CampaignState state = new CampaignState();
        assertFalse(state.rollback());
    }

    @Test
    @DisplayName("资金不能扣成负数")
    void creditsCannotGoNegative() {
        CampaignState state = new CampaignState();
        assertTrue(state.spendCredits(100));
        assertFalse(state.spendCredits(Integer.MAX_VALUE));
        assertTrue(state.credits() >= 0);
    }

    @Test
    @DisplayName("NBT 往返保留核心状态和下潜阶段")
    void nbtRoundTrip() {
        CampaignState state = new CampaignState();
        state.addCredits(250);
        state.beginTravel(1, 42L);

        CompoundTag tag = state.save(new CompoundTag());
        CampaignState restored = CampaignState.load(tag);

        assertEquals(state.currentNode(), restored.currentNode());
        assertEquals(state.credits(), restored.credits());
        assertEquals(RoundPhase.DIVING, restored.round().phase());
        assertEquals(1, restored.round().destination());
        assertTrue(restored.visited().contains(state.currentNode()));
    }

    @Test
    @DisplayName("NBT 往返保留快照：重进后仍能回档")
    void snapshotSurvivesNbt() {
        CampaignState state = new CampaignState();
        int creditsBefore = state.credits();
        state.takeSnapshot();
        state.addCredits(1000);
        assertNotEquals(creditsBefore, state.credits());

        CampaignState restored = CampaignState.load(state.save(new CompoundTag()));
        assertTrue(restored.hasSnapshot());
        assertTrue(restored.rollback());
        assertEquals(creditsBefore, restored.credits());
    }
}
