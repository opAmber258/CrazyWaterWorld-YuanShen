package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 原型炮塔接收单次开火，电磁炮接收扳机状态。 */
public record TurretFirePacket(BlockPos pos, boolean held) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(held);
    }

    public static TurretFirePacket decode(FriendlyByteBuf buf) {
        return new TurretFirePacket(buf.readBlockPos(), buf.readBoolean());
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
                turret.fireInput(player, held);
            }
        });
        context.setPacketHandled(true);
    }
}
