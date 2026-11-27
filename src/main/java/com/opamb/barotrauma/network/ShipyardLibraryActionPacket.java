package com.opamb.barotrauma.network;

import com.opamb.barotrauma.world.shipyard.ShipyardLibraryServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：船库列表/保存/加载/导出/删除。 */
public record ShipyardLibraryActionPacket(byte action, String id, String name) {

    public static final byte LIST = 1;
    public static final byte SAVE = 2;
    public static final byte LOAD = 3;
    public static final byte EXPORT = 4;
    public static final byte DELETE = 5;

    public static ShipyardLibraryActionPacket list() {
        return new ShipyardLibraryActionPacket(LIST, "", "");
    }

    public static ShipyardLibraryActionPacket save(String name) {
        return new ShipyardLibraryActionPacket(SAVE, "", name);
    }

    public static ShipyardLibraryActionPacket load(String id) {
        return new ShipyardLibraryActionPacket(LOAD, id, "");
    }

    public static ShipyardLibraryActionPacket export(String id) {
        return new ShipyardLibraryActionPacket(EXPORT, id, "");
    }

    public static ShipyardLibraryActionPacket delete(String id) {
        return new ShipyardLibraryActionPacket(DELETE, id, "");
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeUtf(id == null ? "" : id, 64);
        buf.writeUtf(name == null ? "" : name, 64);
    }

    public static ShipyardLibraryActionPacket decode(FriendlyByteBuf buf) {
        return new ShipyardLibraryActionPacket(buf.readByte(), buf.readUtf(64), buf.readUtf(64));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ShipyardLibraryServer.handle(player, action, id, name);
            }
        });
        context.setPacketHandled(true);
    }
}
