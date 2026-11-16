package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
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
            BlockPos bestCore = null;
            boolean hadShip = false;
            double bestDistance = Double.MAX_VALUE;

            // 物理船优先按玩家附近的有效接线盒判断；全局活动船不能挡住方块船。
            // 活动船可能在另一个维度：世界坐标相同也不能参与本维度的距离排序。
            if (VsState.active() && VsPilot.activeLevel() == level) {
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
                        // 拆掉的接线盒在 ShipSimulation 清理前还会留在 junctionBoxes() 里；
                        // 旧位置最近的话会让面板静默打开失败，先按方块实体过滤。
                        BlockPos yard = VsPilot.toYard(cell);
                        if (yard == null || !level.hasChunkAt(yard)
                                || !(level.getBlockEntity(yard) instanceof JunctionBoxBlockEntity)) {
                            continue;
                        }
                        double distance = world.distanceToSqr(player.position());
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = cell;
                            bestPhysical = true;
                            bestCore = null;
                        }
                    }
                }
            }

            // 方块船：搜索范围内每艘船都要看，不能只认最近的核心——
            // 近处的船可能没有接线盒，远处那艘的盒子反而更近。
            for (ShipCoreBlockEntity core : ShipCores.coresNear(
                    level, player.blockPosition(), CORE_SEARCH)) {
                if (VsState.isPhysical(level, core.getBlockPos())
                        || VsPilot.isYardBlock(core.getBlockPos())) {
                    // 已装配成物理船：接线盒坐标是装配坐标，由上面的分支处理。
                    // yardOffset 已换算但 probe 还没就绪时 isPhysical 可能为 false，
                    // 所以再用船坞包围盒兜一层。
                    continue;
                }
                // 方块船的 state 不随存档保存，重载后要等 rescan 才有。
                ShipState blockShip = core.state() != null ? core.state() : core.rescan(level);
                if (blockShip == null) {
                    continue;
                }
                hadShip = true;
                for (BlockPos cell : blockShip.simulation().junctionBoxes()) {
                    if (!level.hasChunkAt(cell)) {
                        continue;
                    }
                    // 已拆除但还没被模拟清理的盒子不能参与最近盒选择。
                    if (!(level.getBlockEntity(cell) instanceof JunctionBoxBlockEntity)) {
                        continue;
                    }
                    double distance = Vec3.atCenterOf(cell).distanceToSqr(player.position());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cell;
                        bestPhysical = false;
                        bestCore = core.getBlockPos();
                    }
                }
            }

            if (best == null || bestDistance > MAX_DISTANCE_SQ) {
                player.displayClientMessage(
                        Component.translatable(hadShip ? "power.no_box" : "power.no_ship"), true);
                return;
            }
            PowerPanelState.send(player, best, bestPhysical, bestCore);
        });
        context.setPacketHandled(true);
    }
}
