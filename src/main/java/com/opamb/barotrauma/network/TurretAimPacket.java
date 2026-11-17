package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：炮塔瞄准的绝对角度。
 *
 * <p>服务端不相信客户端的速度，只按每 tick 最大转角截断；客户端本地也按同一上限
 * 限制鼠标输入，所以正常情况下两边角度一致。</p>
 */
public record TurretAimPacket(BlockPos pos, float yaw, float pitch) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static TurretAimPacket decode(FriendlyByteBuf buf) {
        return new TurretAimPacket(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.level().getBlockEntity(pos) instanceof TurretBlockEntity turret) {
                turret.aim(player, yaw, pitch);
            }
        });
        context.setPacketHandled(true);
    }
}
