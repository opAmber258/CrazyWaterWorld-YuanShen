package com.opamb.barotrauma.network;

import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：打开玩家附近最近接线盒的电力面板。 */
public record PowerOpenRequestPacket() {

    /** 面板只认 8 格内的接线盒（和右键打开的检查一致）。 */
    private static final double MAX_DISTANCE = 8.0D;
    private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;
    /** 方块船的核心可能离接线盒很远，按 64 格找船。 */
    private static final double CORE_SEARCH = 64.0D;

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
            BlockPos best = null;
            boolean bestPhysical = false;
            boolean hadShip = false;
            double bestDistance = Double.MAX_VALUE;

            // 物理船优先按玩家附近的有效接线盒判断；全局活动船不能挡住方块船。
            if (VsState.active()) {
                ShipState physical = VsState.activeState();
                if (physical != null) {
                    hadShip = true;
                    for (BlockPos cell : physical.simulation().junctionBoxes()) {
                        // 接线盒坐标是装配坐标；物理船要换到世界坐标再比距离。
                        // 不能把玩家世界坐标直接传给 toAssembly（那只是船坞偏移换算）。
                        Vec3 world = VsPilot.assemblyToWorld(cell);
                        if (world == null) {
                            continue;
                        }
                        double distance = world.distanceToSqr(player.position());
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = cell;
                            bestPhysical = true;
                        }
                    }
                }
            }

            var core = ShipCores.nearestCore(level, player.blockPosition(), CORE_SEARCH);
            if (core != null) {
                hadShip = true;
                for (BlockPos cell : core.state().simulation().junctionBoxes()) {
                    if (!level.hasChunkAt(cell)) {
                        continue;
                    }
                    double distance = Vec3.atCenterOf(cell).distanceToSqr(player.position());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cell;
                        bestPhysical = false;
                    }
                }
            }

            if (best == null || bestDistance > MAX_DISTANCE_SQ) {
                player.displayClientMessage(
                        Component.translatable(hadShip ? "power.no_box" : "power.no_ship"), true);
                return;
            }
            PowerPanelState.send(player, best, bestPhysical);
        });
        context.setPacketHandled(true);
    }
}
