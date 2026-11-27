package com.opamb.barotrauma.network;

import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.shipyard.ShipyardSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：打开编辑器时请求当前地块状态。 */
public record ShipyardRequestPacket() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static ShipyardRequestPacket decode(FriendlyByteBuf buf) {
        return new ShipyardRequestPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(ModDimensions.SHIPYARD)) {
                return;
            }
            ShipyardSavedData.Plot plot = ShipyardSavedData.get(level).plot(player.getUUID());
            if (plot != null) {
                ModNetwork.sendToPlayer(player, ShipyardStatePacket.of(plot));
                ModNetwork.sendToPlayer(player, ShipyardStatusPacket.of(
                        com.opamb.barotrauma.world.shipyard.ShipyardValidator.validate(level, plot)));
            }
        });
        context.setPacketHandled(true);
    }
}
