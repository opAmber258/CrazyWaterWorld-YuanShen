package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端：医疗诊所里玩家当前的病症与治疗价格。 */
public record ClinicStatePacket(int wallet, List<Entry> entries, boolean open) {

    /** limb 为空 = 全身病症。 */
    public record Entry(String afflictionId, String limb, double strength, int price) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, wallet));
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.afflictionId(), 64);
            buf.writeUtf(entry.limb() == null ? "" : entry.limb(), 32);
            buf.writeFloat((float) entry.strength());
            buf.writeVarInt(Math.max(0, entry.price()));
        }
        buf.writeBoolean(open);
    }

    public static ClinicStatePacket decode(FriendlyByteBuf buf) {
        int wallet = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > 256) {
            throw new IllegalArgumentException("clinic entry count");
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(64), buf.readUtf(32), buf.readFloat(), buf.readVarInt()));
        }
        boolean open = buf.readBoolean();
        return new ClinicStatePacket(wallet, List.copyOf(entries), open);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.opamb.barotrauma.client.MedicalClinicScreen.receive(this)));
        context.setPacketHandled(true);
    }
}
