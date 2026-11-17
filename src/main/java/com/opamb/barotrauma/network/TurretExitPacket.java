package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：离开炮塔视角（左 Shift）。 */
public record TurretExitPacket(BlockPos pos) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static TurretExitPacket decode(FriendlyByteBuf buf) {
        return new TurretExitPacket(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.level().getBlockEntity(pos) instanceof TurretBlockEntity turret) {
                turret.exit(player);
            }
        });
        context.setPacketHandled(true);
    }
}
