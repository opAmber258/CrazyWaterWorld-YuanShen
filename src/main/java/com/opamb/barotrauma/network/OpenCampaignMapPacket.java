package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：右键站务员后打开海图（原作就是「对话 → 海图里勾任务 → 下潜」，
 * 任务列表和目的站选择都在同一张海图上）。
 */
public record OpenCampaignMapPacket() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static OpenCampaignMapPacket decode(FriendlyByteBuf buf) {
        return new OpenCampaignMapPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.CampaignMapScreen.openFromNpc()));
        context.setPacketHandled(true);
    }
}
