package com.opamb.barotrauma.network;

import com.opamb.barotrauma.client.ShipyardClient;
import com.opamb.barotrauma.world.shipyard.ShipyardSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 → 客户端：当前玩家的船厂地块/核心状态。 */
public record ShipyardStatePacket(int originX, int originY, int originZ,
                                  int minX, int minY, int minZ,
                                  int maxX, int maxY, int maxZ,
                                  String shipId, boolean dirty) {

    public static ShipyardStatePacket of(ShipyardSavedData.Plot plot) {
        int cx = plot.originX();
        int cy = plot.originY();
        int cz = plot.originZ();
        int radius = com.opamb.barotrauma.world.ModDimensions.SHIPYARD_PLOT_RADIUS;
        return new ShipyardStatePacket(cx, cy, cz,
                cx - radius, cy - 8, cz - radius,
                cx + radius, cy + radius, cz + radius,
                plot.shipId, plot.dirty);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(originX);
        buf.writeVarInt(originY);
        buf.writeVarInt(originZ);
        buf.writeVarInt(minX);
        buf.writeVarInt(minY);
        buf.writeVarInt(minZ);
        buf.writeVarInt(maxX);
        buf.writeVarInt(maxY);
        buf.writeVarInt(maxZ);
        buf.writeUtf(shipId == null ? "" : shipId, 128);
        buf.writeBoolean(dirty);
    }

    public static ShipyardStatePacket decode(FriendlyByteBuf buf) {
        return new ShipyardStatePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(128), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShipyardClient.accept(this)));
        context.setPacketHandled(true);
    }
}
