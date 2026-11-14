package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Comparator;
import java.util.function.Supplier;

/** 客户端 → 服务端：打开本船最近接线盒的电力面板。 */
public record PowerOpenRequestPacket() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static PowerOpenRequestPacket decode(FriendlyByteBuf buf) {
        return new PowerOpenRequestPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ServerLevel level = player.serverLevel();
            var ship = com.opamb.barotrauma.ship.vs.VsState.active()
                    ? com.opamb.barotrauma.ship.vs.VsState.activeState()
                    : resolveBlockShip(level, player.blockPosition());
            if (ship == null) {
                player.displayClientMessage(Component.translatable("power.no_ship"), true);
                return;
            }
            net.minecraft.core.BlockPos playerCell = com.opamb.barotrauma.ship.vs.VsState.active()
                    ? com.opamb.barotrauma.ship.vs.VsPilot.toAssembly(player.blockPosition())
                    : player.blockPosition();
            var box = ship.simulation().junctionBoxes().stream()
                    .min(Comparator.comparingDouble(p -> p.distSqr(playerCell)))
                    .orElse(null);
            if (box == null) {
                player.displayClientMessage(Component.translatable("power.no_box"), true);
                return;
            }
            PowerPanelState.send(player, box);
        });
        context.setPacketHandled(true);
    }

    private static com.opamb.barotrauma.ship.ShipState resolveBlockShip(
            ServerLevel level, net.minecraft.core.BlockPos pos) {
        var core = com.opamb.barotrauma.ship.ShipCores.nearestCore(level, pos, 64.0D);
        return core == null ? null : core.state();
    }
}
