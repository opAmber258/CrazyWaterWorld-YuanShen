package com.opamb.barotrauma.network;

import com.opamb.barotrauma.client.ShipyardClient;
import com.opamb.barotrauma.world.shipyard.ShipLibrary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端：船库条目列表。 */
public record ShipyardLibraryListPacket(List<Entry> entries) {

    public record Entry(String id, String name, int blocks, int sizeX, int sizeY, int sizeZ,
                        String ownerName, boolean verified) {
    }

    public static ShipyardLibraryListPacket of(List<ShipLibrary.Entry> entries) {
        return new ShipyardLibraryListPacket(entries.stream()
                .map(entry -> new Entry(entry.id(), entry.name(), entry.blocks(),
                        entry.sizeX(), entry.sizeY(), entry.sizeZ(),
                        entry.ownerName(), entry.verified()))
                .toList());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.id(), 64);
            buf.writeUtf(entry.name(), 64);
            buf.writeVarInt(entry.blocks());
            buf.writeVarInt(entry.sizeX());
            buf.writeVarInt(entry.sizeY());
            buf.writeVarInt(entry.sizeZ());
            buf.writeUtf(entry.ownerName(), 48);
            buf.writeBoolean(entry.verified());
        }
    }

    public static ShipyardLibraryListPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(64), buf.readUtf(64),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(48), buf.readBoolean()));
        }
        return new ShipyardLibraryListPacket(entries);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShipyardClient.acceptLibrary(this)));
        context.setPacketHandled(true);
    }
}
