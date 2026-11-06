package com.opamb.barotrauma.network;

import com.opamb.barotrauma.campaign.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;

public record CampaignStatePacket(int current, RoundPhase phase, int destination, int credits, List<String> missions) {
    public CampaignStatePacket { missions = List.copyOf(missions); }
    public static CampaignStatePacket build(CampaignState state) {
        return new CampaignStatePacket(state.currentNode(), state.round().phase(), state.round().destination(),
                state.credits(), state.selectedMissions());
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(current); buf.writeEnum(phase); buf.writeVarInt(destination); buf.writeVarInt(credits);
        buf.writeVarInt(missions.size()); for (String id : missions) buf.writeUtf(id, 64);
    }
    public static CampaignStatePacket decode(FriendlyByteBuf buf) {
        int current = buf.readVarInt(); RoundPhase phase = buf.readEnum(RoundPhase.class);
        int destination = buf.readVarInt(), credits = buf.readVarInt(), count = buf.readVarInt();
        if (count < 0 || count > CampaignMission.LIMIT) throw new IllegalArgumentException("campaign mission count");
        var ids = new java.util.ArrayList<String>();
        for (int i = 0; i < count; i++) ids.add(buf.readUtf(64));
        return new CampaignStatePacket(current, phase, destination, credits, ids);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        if (ctx.getDirection().getReceptionSide().isClient()) ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.opamb.barotrauma.client.CampaignClient.accept(this)));
        ctx.setPacketHandled(true);
    }
}
