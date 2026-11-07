package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求某玩家的医疗快照（打开队友的治疗面板时）。
 * 服务端把目标快照按 {@link MedicalSyncPacket} 定向回给请求者。
 */
public record MedicalRequestPacket(UUID target) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(target);
    }

    public static MedicalRequestPacket decode(FriendlyByteBuf buf) {
        return new MedicalRequestPacket(buf.readUUID());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer requester = context.getSender();
            if (requester == null) {
                return;
            }
            ServerPlayer targetPlayer = requester.server.getPlayerList().getPlayer(target);
            if (targetPlayer != null) {
                MedicalSyncPacket.respondSnapshot(requester, targetPlayer);
            }
        });
        context.setPacketHandled(true);
    }
}
