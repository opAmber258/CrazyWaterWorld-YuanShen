package com.opamb.barotrauma.network;

import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.shipyard.ShipyardSavedData;
import com.opamb.barotrauma.world.shipyard.ShipyardServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：退出编辑器时把玩家放到当前轨道焦点附近，便于第一人称继续建造。 */
public record ShipyardTeleportPacket(BlockPos pos, float yaw, float pitch) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static ShipyardTeleportPacket decode(FriendlyByteBuf buf) {
        return new ShipyardTeleportPacket(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(ModDimensions.SHIPYARD)) {
                return;
            }
            ShipyardSavedData.Plot plot = ShipyardSavedData.get(level).plot(player.getUUID());
            if (plot == null || !ShipyardServer.insidePlot(plot, pos)
                    || pos.getY() < plot.originY()) {
                return;
            }
            BlockPos target = safeStand(level, plot, pos);
            if (target == null) {
                // 焦点在虚空上方时回退到平台中心，避免玩家从高空坠入虚空。
                target = safeStand(level, plot,
                        new BlockPos(plot.originX(), plot.originY() + 1, plot.originZ()));
            }
            if (target == null) {
                return;
            }
            level.getChunkAt(target);
            player.teleportTo(level, target.getX() + 0.5D, target.getY(),
                    target.getZ() + 0.5D, yaw, pitch);
        });
        context.setPacketHandled(true);
    }

    /** 从 pos 上方往下找一块有碰撞的地面，返回其上方两格空气的落脚点。 */
    private static BlockPos safeStand(ServerLevel level, ShipyardSavedData.Plot plot, BlockPos pos) {
        int top = Math.min(pos.getY() + 6, plot.originY() + ShipyardServer.PLOT_RADIUS - 1);
        for (int y = top; y >= plot.originY(); y--) {
            BlockPos floor = new BlockPos(pos.getX(), y, pos.getZ());
            if (!ShipyardServer.insidePlot(plot, floor)) {
                continue;
            }
            var state = level.getBlockState(floor);
            if (!state.isAir() && !state.getCollisionShape(level, floor).isEmpty()
                    && level.getBlockState(floor.above()).isAir()
                    && level.getBlockState(floor.above(2)).isAir()) {
                return floor.above();
            }
        }
        return null;
    }
}
