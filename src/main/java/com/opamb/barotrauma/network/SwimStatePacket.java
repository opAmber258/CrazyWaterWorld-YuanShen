package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：权威的「脚部浸没」状态（疾跑游泳的移动入口用）。
 *
 * <p>issue #31 审查意见：{@code isSwimming} 是泳姿标志，医疗钩子也会设置它，
 * 不能当干湿状态用；服务端按脚上方 0.4 格的权威判定推给客户端。</p>
 */
public record SwimStatePacket(boolean waterAtFeet) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(waterAtFeet);
    }

    public static SwimStatePacket decode(FriendlyByteBuf buf) {
        return new SwimStatePacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.ClientSwimState.accept(this)));
        context.setPacketHandled(true);
    }
}
