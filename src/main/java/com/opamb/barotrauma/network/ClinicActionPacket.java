package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：治疗一条病症，或 {@code "*"} = 全部治疗；{@code maxPrice} 是玩家确认的报价上限。 */
public record ClinicActionPacket(String afflictionId, String limb, int maxPrice) {

    public static final String HEAL_ALL = "*";

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(afflictionId == null ? "" : afflictionId, 64);
        buf.writeUtf(limb == null ? "" : limb, 32);
        buf.writeVarInt(Math.max(0, maxPrice));
    }

    public static ClinicActionPacket decode(FriendlyByteBuf buf) {
        return new ClinicActionPacket(buf.readUtf(64), buf.readUtf(32), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                com.opamb.barotrauma.station.MedicalClinicService.handle(player, this);
            }
        });
        context.setPacketHandled(true);
    }
}
