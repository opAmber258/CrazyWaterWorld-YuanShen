package com.opamb.barotrauma.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：炮塔的权威状态。
 *
 * <p>{@code operatorEntityId} 等于本地玩家实体 id 时进入炮塔视角；变回 -1 则强制退出。
 * 其他人的客户端只用 yaw/pitch/cooldown 渲染炮管和后坐。</p>
 */
public record TurretStatePacket(BlockPos pos, float yaw, float pitch, int cooldown, int operatorEntityId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeVarInt(cooldown);
        buf.writeVarInt(operatorEntityId);
    }

    public static TurretStatePacket decode(FriendlyByteBuf buf) {
        return new TurretStatePacket(buf.readBlockPos(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.TurretClient.onState(this)));
        context.setPacketHandled(true);
    }
}
