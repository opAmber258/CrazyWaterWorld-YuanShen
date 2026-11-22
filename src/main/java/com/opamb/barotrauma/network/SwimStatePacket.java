package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：权威的游泳条件、疾游姿态和随船拖拽的船 id。
 *
 * <p>客户端以 {@code swimMovement} 决定是否启用水中移动，以自己的疾跑键预测泳姿。
 * {@code swimPose} 为服务端确认的疾游状态，其他玩家通过原版实体数据看到该姿态。
 * {@code dragShipId} 用于跟随服务端认定的物理船。</p>
 */
public record SwimStatePacket(boolean swimMovement, boolean swimPose, long dragShipId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(swimMovement);
        buf.writeBoolean(swimPose);
        buf.writeLong(dragShipId);
    }

    public static SwimStatePacket decode(FriendlyByteBuf buf) {
        return new SwimStatePacket(buf.readBoolean(), buf.readBoolean(), buf.readLong());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.ClientSwimState.accept(this)));
        context.setPacketHandled(true);
    }
}
