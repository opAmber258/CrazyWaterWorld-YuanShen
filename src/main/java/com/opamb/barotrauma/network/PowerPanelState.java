package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 服务端电力面板数据：从一个接线盒出发，收集它附近 16 格内的用电设备。
 *
 * <p>属于物理船的盒子/设备用<b>装配坐标</b>；普通世界里的盒子用世界坐标。
 * 物理船的方块实体在船坞里，读写前用 {@link VsPilot#toYard(BlockPos)} 换算。
 * 绑定只改变设备的 {@code PowerBox}（存同一套坐标），不改任何方块。</p>
 */
public final class PowerPanelState {

    public static final int RANGE = 16;
    public static final int MAX_DEVICES = 24;
    /** 玩家必须站在接线盒 8 格内才能打开/改绑定。 */
    private static final double MAX_PLAYER_DISTANCE_SQ = 8.0D * 8.0D;

    private PowerPanelState() {
    }

    /** @param boxCell 接线盒坐标：物理船是装配坐标，普通世界是世界坐标 */
    public static PowerStatePacket build(ServerPlayer player, BlockPos boxCell) {
        ServerLevel level = player.serverLevel();
        boolean physical = isPhysicalCell(boxCell);
        BlockPos boxYard = physical ? VsPilot.toYard(boxCell) : boxCell;
        if (boxYard == null) {
            return null;
        }
        BlockEntity raw = level.getBlockEntity(boxYard);
        if (!(raw instanceof JunctionBoxBlockEntity box)) {
            return null;
        }
        ShipState ship = resolveShip(level, boxCell, physical);
        if (ship == null) {
            return null;
        }
        List<PowerStatePacket.Device> devices = new ArrayList<>();
        for (var info : ship.simulation().consumerInfos()) {
            if (info.pos().distSqr(boxCell) > (double) RANGE * RANGE) {
                continue;
            }
            BlockPos deviceYard = physical ? VsPilot.toYard(info.pos()) : info.pos();
            BlockEntity deviceBe = deviceYard == null ? null : level.getBlockEntity(deviceYard);
            String key = deviceBe == null
                    ? "block.barotrauma.junction_box.unknown"
                    : deviceBe.getBlockState().getBlock().getDescriptionId();
            devices.add(new PowerStatePacket.Device(info.pos(), key, info.demandKw(),
                    info.minVoltage(), info.box()));
        }
        devices.sort(Comparator.comparingDouble(d -> d.pos().distSqr(boxCell)));
        if (devices.size() > MAX_DEVICES) {
            devices = new ArrayList<>(devices.subList(0, MAX_DEVICES));
        }
        double voltage = ship.simulation().voltage();
        return new PowerStatePacket(boxCell, ship.simulation().supplyKw(),
                ship.simulation().report().powerDemand(), voltage,
                box.condition(), ship.simulation().boxLoad(boxCell), devices);
    }

    /** @param boxCell 接线盒坐标：物理船是装配坐标，普通世界是世界坐标 */
    public static void send(ServerPlayer player, BlockPos boxCell) {
        PowerStatePacket state = build(player, boxCell);
        if (state != null) {
            ModNetwork.sendToPlayer(player, state);
        }
    }

    /** 服务端绑定校验：发送者站在盒子 8 格内，设备在盒子 16 格内且确实是 PowerConsumer。 */
    public static boolean bind(ServerPlayer player, BlockPos boxCell, BlockPos deviceCell, boolean bind) {
        ServerLevel level = player.serverLevel();
        boolean boxPhysical = isPhysicalCell(boxCell);
        boolean devicePhysical = isPhysicalCell(deviceCell);
        // 物理船盒子只能绑物理船设备，世界盒子只能绑世界设备，避免坐标系混用。
        if (boxPhysical != devicePhysical) {
            return false;
        }
        boolean physical = boxPhysical;
        if (boxCell.distSqr(deviceCell) > (double) RANGE * RANGE) {
            return false;
        }
        Vec3 boxWorld = physical
                ? VsPilot.assemblyToWorld(boxCell)
                : Vec3.atCenterOf(boxCell);
        if (boxWorld == null || player.distanceToSqr(boxWorld) > MAX_PLAYER_DISTANCE_SQ) {
            return false;
        }
        BlockPos boxYard = physical ? VsPilot.toYard(boxCell) : boxCell;
        BlockPos deviceYard = physical ? VsPilot.toYard(deviceCell) : deviceCell;
        if (boxYard == null || deviceYard == null) {
            return false;
        }
        if (!(level.getBlockEntity(boxYard) instanceof JunctionBoxBlockEntity)) {
            return false;
        }
        BlockEntity raw = level.getBlockEntity(deviceYard);
        if (!(raw instanceof PowerConsumer consumer)) {
            return false;
        }
        // PowerBox 与 ShipSimulation 的 boxes/consumers 使用同一套坐标。
        consumer.setPowerBox(bind ? boxCell : null);
        raw.setChanged();
        return true;
    }

    /** 这个坐标是否属于当前物理船（装配坐标在船体包围盒内）。 */
    private static boolean isPhysicalCell(BlockPos cell) {
        return VsState.active() && VsPilot.isAssemblyCell(cell);
    }

    private static ShipState resolveShip(ServerLevel level, BlockPos pos, boolean physical) {
        if (physical) {
            return VsState.activeState();
        }
        var core = ShipCores.nearestCore(level, pos, 32.0D);
        return core == null ? null : core.state();
    }
}
