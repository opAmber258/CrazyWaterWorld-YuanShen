package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：切换炮塔单发/全自动。服务端校验占用者与操作距离。 */
public record TurretModePacket(BlockPos pos) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static TurretModePacket decode(FriendlyByteBuf buf) {
        return new TurretModePacket(buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.level().hasChunkAt(pos)) {
                return;
            }
            if (player.level().getBlockEntity(pos) instanceof TurretBlockEntity turret) {
                turret.toggleFireMode(player);
            }
        });
        context.setPacketHandled(true);
    }
}
