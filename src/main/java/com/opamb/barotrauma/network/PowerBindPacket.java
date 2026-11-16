package com.opamb.barotrauma.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：绑定/解绑一台设备到接线盒。 */
public record PowerBindPacket(BlockPos boxPos, BlockPos devicePos, boolean bind) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(boxPos);
        buf.writeBlockPos(devicePos);
        buf.writeBoolean(bind);
    }

    public static PowerBindPacket decode(FriendlyByteBuf buf) {
        return new PowerBindPacket(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // 成功失败都要回一次刷新：客户端按"提交后禁用按钮"处理，
            // 失败时没有回包会让按钮永远点不了。
            PowerPanelState.bind(player, boxPos, devicePos, bind);
            PowerPanelState.refresh(player, boxPos);
            context.setPacketHandled(true);
        });
    }
}
