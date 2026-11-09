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
    @Test void optionalEmptyAndAllOfferedSelectionAllowed() {
        assertTrue(CampaignMission.valid(RoundPhase.AT_NODE,0,0,1,List.of()));
        assertTrue(CampaignMission.valid(RoundPhase.AT_NODE,0,0,1,
                CampaignMission.offered(0,1).stream().map(CampaignMission::id).toList()));
    }
    @Test void duplicateUnknownForeignAndTooManyMissionsRejected() {
        String id = CampaignMission.offered(0,1).get(0).id();
        assertFalse(CampaignMission.valid(RoundPhase.AT_NODE,0,0,1,List.of(id,id)));
        assertFalse(CampaignMission.valid(RoundPhase.AT_NODE,0,0,1,List.of("missing")));
        assertFalse(CampaignMission.valid(RoundPhase.AT_NODE,0,0,1,List.of("1_2_salvage")));
        assertFalse(CampaignMission.valid(RoundPhase.AT_NODE,0,0,1,List.of(id,id,id,id)));
    }
    @Test void phaseOriginAndAdjacencyGuarded() {
        assertFalse(CampaignMission.valid(RoundPhase.DIVING,0,0,1,List.of()));
        assertFalse(CampaignMission.valid(RoundPhase.FAILED,0,0,1,List.of()));
        assertFalse(CampaignMission.valid(RoundPhase.AT_NODE,1,0,2,List.of()));
        assertFalse(CampaignMission.valid(RoundPhase.AT_NODE,0,0,4,List.of()));
        assertFalse(CampaignMission.valid(RoundPhase.AT_NODE,0,0,-1,List.of()));
    }
    @Test void selectedMissionsSurviveNbtAndCoreRollback() {
        CampaignState state = new CampaignState();
        state.takeSnapshot();
        var ids = List.of(CampaignMission.offered(0,1).get(2).id());
        state.selectMissions(ids); state.beginTravel(1,42);
        CampaignState copy = CampaignState.load(state.save(new CompoundTag()));
        assertEquals(ids,copy.selectedMissions());
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
