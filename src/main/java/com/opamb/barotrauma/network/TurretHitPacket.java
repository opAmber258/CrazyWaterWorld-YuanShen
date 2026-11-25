package com.opamb.barotrauma.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：炮塔这一发确实造成了伤害。
 *
 * <p>只发给操作者。客户端据此播放准星反馈：普通命中闪白色随机角指针，
 * 击杀生物闪黄色 X 扩散特效。不要用客户端弹道预测代替这个包。</p>
 */
public record TurretHitPacket(BlockPos pos, boolean killed) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(killed);
    }

    public static TurretHitPacket decode(FriendlyByteBuf buf) {
        return new TurretHitPacket(buf.readBlockPos(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.TurretClient.onHitConfirmed(pos, killed)));
        context.setPacketHandled(true);
    }
}
