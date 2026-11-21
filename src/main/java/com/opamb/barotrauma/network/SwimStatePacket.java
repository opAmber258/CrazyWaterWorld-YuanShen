package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：权威的「脚部浸没」+「泳姿」+「随船拖拽的船 id」。
 *
 * <p>issue #31 审查意见：{@code isSwimming} 是泳姿标志，医疗钩子也会设置它，
 * 不能当干湿状态用；服务端按脚上方 0.4 格的权威判定推给客户端。</p>
 *
 * <p>{@code swimPose} 和 {@code dragShipId} 也由服务端统一决定，客户端只跟随。
 * 之前两边各自用水面盒算位置：船变换/玩家坐标差一 tick 就会一边站一边游、
 * 一边拖一边不拖，表现为贴墙时视角卡、位置被拉回（用户实测）。</p>
 */
public record SwimStatePacket(boolean waterAtFeet, boolean swimPose, long dragShipId) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(waterAtFeet);
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
