package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 服务端电力面板数据：从一个接线盒出发，收集它附近 16 格内的用电设备。
 *
 * <p>设备坐标是装配坐标；物理船的方块实体就在船坞里，所以直接
 * {@code level.getBlockEntity(pos)} 能取到。面板里显示的是“附近设备”，
 * 绑定只改变设备的 {@code PowerBox}，不改任何方块。</p>
 */
public final class PowerPanelState {

    public static final int RANGE = 16;
    public static final int MAX_DEVICES = 24;

    private PowerPanelState() {
    }

    public static PowerStatePacket build(ServerPlayer player, BlockPos boxPos) {
        ServerLevel level = player.serverLevel();
        BlockEntity raw = level.getBlockEntity(boxPos);
        if (!(raw instanceof JunctionBoxBlockEntity box)) {
            return null;
        }
        ShipState ship = resolveShip(level, boxPos);
        if (ship == null) {
            return null;
        }
        BlockPos cell = VsState.active() ? com.opamb.barotrauma.ship.vs.VsPilot.toAssembly(boxPos) : boxPos;
        List<PowerStatePacket.Device> devices = new ArrayList<>();
        for (var info : ship.simulation().consumerInfos()) {
            if (info.pos().distSqr(cell) > (double) RANGE * RANGE) {
                continue;
            }
            BlockEntity deviceBe = level.getBlockEntity(info.pos());
            String key = deviceBe == null
                    ? "block.barotrauma.junction_box.unknown"
                    : deviceBe.getBlockState().getBlock().getDescriptionId();
            devices.add(new PowerStatePacket.Device(info.pos(), key, info.demandKw(),
                    info.minVoltage(), info.box()));
        }
        devices.sort(Comparator.comparingDouble(d -> d.pos().distSqr(cell)));
        if (devices.size() > MAX_DEVICES) {
            devices = new ArrayList<>(devices.subList(0, MAX_DEVICES));
        }
        double voltage = ship.simulation().voltage();
        return new PowerStatePacket(cell, ship.simulation().supplyKw(),
                ship.simulation().report().powerDemand(), voltage,
                box.condition(), ship.simulation().boxLoad(cell), devices);
    }

    public static void send(ServerPlayer player, BlockPos boxPos) {
        PowerStatePacket state = build(player, boxPos);
        if (state != null) {
            ModNetwork.sendToPlayer(player, state);
        }
    }

    /** 服务端绑定校验：设备必须在盒子 16 格内，且确实是 PowerConsumer。 */
    public static boolean bind(ServerPlayer player, BlockPos boxPos, BlockPos devicePos, boolean bind) {
        ServerLevel level = player.serverLevel();
        if (boxPos.distSqr(devicePos) > (double) RANGE * RANGE) {
            return false;
        }
        if (!(level.getBlockEntity(boxPos) instanceof JunctionBoxBlockEntity)) {
            return false;
        }
        BlockEntity raw = level.getBlockEntity(devicePos);
        if (!(raw instanceof PowerConsumer consumer)) {
            return false;
        }
        consumer.setPowerBox(bind ? boxPos : null);
        raw.setChanged();
        return true;
    }

    private static ShipState resolveShip(ServerLevel level, BlockPos pos) {
        if (VsState.active()) {
            return VsState.activeState();
        }
        var core = ShipCores.nearestCore(level, pos, 32.0D);
        return core == null ? null : core.state();
    }
}
