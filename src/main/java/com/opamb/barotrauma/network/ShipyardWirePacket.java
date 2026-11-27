package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.blockentity.TurretBlockEntity;
import com.opamb.barotrauma.blockentity.TurretStationBlockEntity;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.shipyard.ShipyardSavedData;
import com.opamb.barotrauma.world.shipyard.ShipyardServer;
import com.opamb.barotrauma.world.shipyard.ShipyardValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：在造船厂编辑器内直接绑定/解绑供电接线盒或炮塔瞄准站。 */
public record ShipyardWirePacket(BlockPos a, BlockPos b) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(a);
        buf.writeBlockPos(b);
    }

    public static ShipyardWirePacket decode(FriendlyByteBuf buf) {
        return new ShipyardWirePacket(buf.readBlockPos(), buf.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)
                    || !level.dimension().equals(ModDimensions.SHIPYARD)) {
                return;
            }
            if (!player.isCreative() && !player.hasPermissions(2)) {
                return;
            }
            ShipyardSavedData data = ShipyardSavedData.get(level);
            ShipyardSavedData.Plot plot = data.plot(player.getUUID());
            if (plot == null || !ShipyardServer.insidePlot(plot, a)
                    || !ShipyardServer.insidePlot(plot, b) || a.equals(b)) {
                return;
            }
            BlockEntity beA = level.getBlockEntity(a);
            BlockEntity beB = level.getBlockEntity(b);
            boolean changed = tryBindPower(player, beA, a, beB, b)
                    || tryBindPower(player, beB, b, beA, a)
                    || tryBindTurret(player, beA, beB, b)
                    || tryBindTurret(player, beB, beA, a);
            if (changed) {
                data.setDirty(player.getUUID(), true);
                data.setDirty();
                ModNetwork.sendToPlayer(player, ShipyardStatusPacket.of(
                        ShipyardValidator.validate(level, plot)));
            } else {
                player.displayClientMessage(Component.literal("两端不是可绑定的设备（接线盒↔用电器 或 瞄准站↔炮塔）"), true);
            }
        });
        context.setPacketHandled(true);
    }

    private static boolean tryBindPower(ServerPlayer player, BlockEntity boxBe, BlockPos boxPos,
                                        BlockEntity consumerBe, BlockPos consumerPos) {
        if (!(boxBe instanceof JunctionBoxBlockEntity) || !(consumerBe instanceof PowerConsumer consumer)) {
            return false;
        }
        if (boxPos.equals(consumer.powerBox())) {
            consumer.setPowerBox(null);
            player.displayClientMessage(Component.literal("已解绑供电连接"), true);
        } else {
            consumer.setPowerBox(boxPos.immutable());
            player.displayClientMessage(Component.literal("已绑定到接线盒 " + boxPos.toShortString()), true);
        }
        consumerBe.setChanged();
        return true;
    }

    private static boolean tryBindTurret(ServerPlayer player, BlockEntity stationBe,
                                         BlockEntity turretBe, BlockPos turretPos) {
        if (!(stationBe instanceof TurretStationBlockEntity station)
                || !(turretBe instanceof TurretBlockEntity)) {
            return false;
        }
        if (station.boundTo(turretPos)) {
            station.clearBinding();
            player.displayClientMessage(Component.literal("已解绑炮塔瞄准站"), true);
        } else {
            station.bindTo(turretPos);
            player.displayClientMessage(Component.literal("已绑定瞄准站 → 炮塔 " + turretPos.toShortString()), true);
        }
        return true;
    }
}
