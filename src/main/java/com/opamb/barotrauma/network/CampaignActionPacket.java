package com.opamb.barotrauma.network;

import com.opamb.barotrauma.campaign.CampaignManager;
import com.opamb.barotrauma.campaign.CampaignMission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;

/** C2S：携带观察到的当前站点，防止过期界面在另一站误启航。 */
public record CampaignActionPacket(int origin, int destination, List<String> missions) {
    public CampaignActionPacket { missions = List.copyOf(missions); }
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(origin); buf.writeVarInt(destination); buf.writeVarInt(missions.size());
        for (String id : missions) buf.writeUtf(id, 64);
    }
    public static CampaignActionPacket decode(FriendlyByteBuf buf) {
        int origin = buf.readVarInt(), destination = buf.readVarInt(), count = buf.readVarInt();
        if (count < 0 || count > CampaignMission.LIMIT) throw new IllegalArgumentException("campaign selection count");
        var ids = new java.util.ArrayList<String>();
        for (int i = 0; i < count; i++) ids.add(buf.readUtf(64));
        return new CampaignActionPacket(origin, destination, ids);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        if (ctx.getSender() != null) ctx.enqueueWork(() -> {
            var player = ctx.getSender();
            if (player != null) {
                com.opamb.barotrauma.BarotraumaMod.LOGGER.info(
                        "[campaign] 收到下潜请求 origin={} dest={} missions={}",
                        origin, destination, missions);
                boolean ok = CampaignManager.startTravel(player, origin, destination, missions);
                com.opamb.barotrauma.BarotraumaMod.LOGGER.info("[campaign] 下潜请求结果={}", ok);
            }
        });
        ctx.setPacketHandled(true);
    }
}
