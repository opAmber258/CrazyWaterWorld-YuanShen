package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：开火。服务端校验占用者、距离和冷却后做射线命中。 */
public record TurretFirePacket(BlockPos pos) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static TurretFirePacket decode(FriendlyByteBuf buf) {
        return new TurretFirePacket(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!player.level().hasChunkAt(pos)) {
                return;
            }
            if (player.level().getBlockEntity(pos) instanceof TurretBlockEntity turret) {
                turret.fire(player);
            }
        });
        context.setPacketHandled(true);
    }
}
