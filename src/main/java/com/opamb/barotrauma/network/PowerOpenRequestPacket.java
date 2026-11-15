package com.opamb.barotrauma.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

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
            // 接线盒坐标是装配坐标；物理船要先用船体变换换到世界坐标再比距离。
            // 不能把玩家世界坐标直接传给 toAssembly（那只是船坞偏移换算）。
            net.minecraft.core.BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (net.minecraft.core.BlockPos cell : ship.simulation().junctionBoxes()) {
                net.minecraft.world.phys.Vec3 world = com.opamb.barotrauma.ship.vs.VsState.active()
                        ? com.opamb.barotrauma.ship.vs.VsPilot.assemblyToWorld(cell)
                        : net.minecraft.world.phys.Vec3.atCenterOf(cell);
                if (world == null) {
                    continue;
                }
                double distance = world.distanceToSqr(player.position());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cell;
                }
            }
            if (best == null || bestDistance > 64.0D) {
                player.displayClientMessage(Component.translatable("power.no_box"), true);
                return;
            }
            PowerPanelState.send(player, best, com.opamb.barotrauma.ship.vs.VsState.active());
        });
        context.setPacketHandled(true);
    }

    private static com.opamb.barotrauma.ship.ShipState resolveBlockShip(
            ServerLevel level, net.minecraft.core.BlockPos pos) {
        var core = com.opamb.barotrauma.ship.ShipCores.nearestCore(level, pos, 64.0D);
        return core == null ? null : core.state();
    }
}
