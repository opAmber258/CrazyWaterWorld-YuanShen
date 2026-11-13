package com.opamb.barotrauma.campaign;

import com.opamb.barotrauma.network.CampaignActionPacket;
import com.opamb.barotrauma.network.CampaignStatePacket;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CampaignSelectionTest {
    @Test void mapIsColdCavernsWithSourceDimensions() {
        assertEquals(10, CampaignMap.MAP_WIDTH / CampaignMap.MAP_HEIGHT);
        for (MapNode node : CampaignMap.nodes()) {
            assertEquals("coldcaverns", node.biome());
            assertTrue(node.mapX() < CampaignMap.VIEW_WIDTH);
            assertTrue(node.mapY() < CampaignMap.MAP_HEIGHT);
            for (int other : node.neighbors()) assertTrue(CampaignMap.connects(other, node.id()));
        }
    }
    @Test void metadataDistinguishesOutpostsFromEmptyWaypoints() {
        assertEquals("outpost", CampaignMap.start().locationType());
        assertEquals(15, CampaignMap.start().reputation());
        assertFalse(CampaignMap.start().services().isEmpty());
        assertEquals("none", CampaignMap.node(2).faction());
        assertTrue(CampaignMap.node(2).services().isEmpty());
    }
    @Test void 一趟任务上限与任务系统一致() {
        // 海图勾选、下潜包、任务存档三处用的是同一个上限。
        assertEquals(com.opamb.barotrauma.data.MissionSavedData.MAX_ACTIVE, CampaignMission.LIMIT);
    }
    @Test void adjacentOnlyStillEnforcedByMap() {
        assertTrue(CampaignMap.connects(0, 1));
        assertFalse(CampaignMap.connects(0, 4));
    }
    @Test void selectedMissionsSurviveNbtAndCoreRollback() {
        CampaignState state = new CampaignState();
        state.takeSnapshot();
        // 旧字段仍会被写进 NBT，但载入时按“已弃用”清空；回档要能恢复航程状态。
        state.selectMissions(List.of("0_1_salvage")); state.beginTravel(1,42);
        CampaignState copy = CampaignState.load(state.save(new CompoundTag()));
        assertTrue(copy.selectedMissions().isEmpty(), "旧航线委托已弃用");
        assertEquals(RoundPhase.DIVING,copy.round().phase());
        assertTrue(copy.rollback()); assertTrue(copy.selectedMissions().isEmpty());
    }
    @Test void networkRoundTrips() {
        var action = new CampaignActionPacket(0,1,List.of("0_1_salvage"));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try { action.encode(buf); assertEquals(action,CampaignActionPacket.decode(buf)); } finally { buf.release(); }
        var state = new CampaignStatePacket(0,RoundPhase.DIVING,1,2500,action.missions());
        buf = new FriendlyByteBuf(Unpooled.buffer());
        try { state.encode(buf); assertEquals(state,CampaignStatePacket.decode(buf)); } finally { buf.release(); }
    }
    @Test void oversizedNetworkSelectionRejectedBeforeAllocation() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try { buf.writeVarInt(0);buf.writeVarInt(1);buf.writeVarInt(100000);
            assertThrows(IllegalArgumentException.class,()->CampaignActionPacket.decode(buf));
        } finally { buf.release(); }
    }
}
