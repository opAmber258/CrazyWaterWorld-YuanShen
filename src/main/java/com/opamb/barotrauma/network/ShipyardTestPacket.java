package com.opamb.barotrauma.network;

import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.shipyard.ShipyardTestService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：开始/结束试航。 */
public record ShipyardTestPacket(boolean start) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(start);
    }

    public static ShipyardTestPacket decode(FriendlyByteBuf buf) {
        return new ShipyardTestPacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.level().dimension().equals(ModDimensions.SHIPYARD_TEST)) {
                ShipyardTestService.stop(player);
            } else if (start) {
                ShipyardTestService.start(player);
            } else {
                ShipyardTestService.stop(player);
            }
        });
        context.setPacketHandled(true);
    }
}
