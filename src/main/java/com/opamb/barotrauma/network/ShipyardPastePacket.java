package com.opamb.barotrauma.network;

import com.opamb.barotrauma.world.shipyard.ShipyardServer;
import com.opamb.barotrauma.world.shipyard.ShipyardShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 客户端 → 服务端：选区剪贴板批量粘贴（支持旋转后快照与镜像对称）。 */
public record ShipyardPastePacket(BlockPos anchor, List<Entry> entries,
                                  byte mirrorAxis, int mirrorCoord) {

    public record Entry(BlockPos rel, CompoundTag state) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchor);
        int count = Math.min(ShipyardShapes.MAX_VOLUME, entries.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i);
            buf.writeBlockPos(entry.rel());
            buf.writeNbt(entry.state());
        }
        buf.writeByte(mirrorAxis);
        buf.writeVarInt(mirrorCoord);
    }

    public static ShipyardPastePacket decode(FriendlyByteBuf buf) {
        BlockPos anchor = buf.readBlockPos();
        int count = Math.min(ShipyardShapes.MAX_VOLUME, Math.max(0, buf.readVarInt()));
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos rel = buf.readBlockPos();
            CompoundTag state = buf.readNbt();
            entries.add(new Entry(rel, state == null ? new CompoundTag() : state));
        }
        byte mirrorAxis = buf.readByte();
        int mirrorCoord = buf.readVarInt();
        return new ShipyardPastePacket(anchor, List.copyOf(entries), mirrorAxis, mirrorCoord);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ShipyardServer.paste(player, anchor, entries, mirrorAxis, mirrorCoord);
            }
        });
        context.setPacketHandled(true);
    }
}
