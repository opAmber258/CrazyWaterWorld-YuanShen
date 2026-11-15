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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 服务端电力面板数据：从一个接线盒出发，收集它附近 16 格内的用电设备。
 *
 * <p>坐标系由调用方显式传入（物理船用装配坐标，普通世界用世界坐标），不再按
 * 坐标数值推断。客户端回传的绑定坐标由服务端按“方块实体实际在哪”判定坐标系，
 * 不直接采信客户端标志。</p>
 */
public final class PowerPanelState {

    public static final int RANGE = 16;
    public static final int MAX_DEVICES = 24;
    /** 玩家必须站在接线盒 8 格内才能打开/改绑定。 */
    private static final double MAX_PLAYER_DISTANCE_SQ = 8.0D * 8.0D;

    private PowerPanelState() {
    }

    /**
     * @param boxCell  接线盒坐标：物理船是装配坐标，普通世界是世界坐标
     * @param physical 是否物理船坐标系（由服务端调用方显式提供）
     */
    public static PowerStatePacket build(ServerPlayer player, BlockPos boxCell, boolean physical) {
        ServerLevel level = player.serverLevel();
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

    /**
     * @param boxCell  接线盒坐标：物理船是装配坐标，普通世界是世界坐标
     * @param physical 是否物理船坐标系
     */
    public static void send(ServerPlayer player, BlockPos boxCell, boolean physical) {
        PowerStatePacket state = build(player, boxCell, physical);
        if (state != null) {
            ModNetwork.sendToPlayer(player, state);
        }
    }

    /** 服务端绑定校验：发送者站在盒子 8 格内，设备在盒子 16 格内且确实是 PowerConsumer。 */
    public static boolean bind(ServerPlayer player, BlockPos boxCell, BlockPos deviceCell, boolean bind) {
        ServerLevel level = player.serverLevel();
        // 坐标来自客户端：按方块实体实际位置判定坐标系，不信任客户端标志。
        Boolean physical = physicalOf(level, boxCell);
        if (physical == null) {
            return false;
        }
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

    /** 绑定成功后刷新面板：按方块实体实际位置重新判定坐标系。 */
    public static void refresh(ServerPlayer player, BlockPos boxCell) {
        Boolean physical = physicalOf(player.serverLevel(), boxCell);
        if (physical != null) {
            send(player, boxCell, physical);
        }
    }

    /**
     * 判定这个接线盒坐标属于哪套坐标系：世界坐标直接能取到方块实体就是世界；
     * 否则在船坞坐标能取到就是物理船；都取不到返回 null。
     */
    @Nullable
    private static Boolean physicalOf(ServerLevel level, BlockPos boxCell) {
        if (boxCell == null) {
            return null;
        }
        if (level.getBlockEntity(boxCell) instanceof JunctionBoxBlockEntity) {
            return false;
        }
        if (!VsState.active()) {
            return null;
        }
        BlockPos yard = VsPilot.toYard(boxCell);
        if (yard != null && level.getBlockEntity(yard) instanceof JunctionBoxBlockEntity) {
            return true;
        }
        return null;
    }

    private static ShipState resolveShip(ServerLevel level, BlockPos pos, boolean physical) {
        if (physical) {
            return VsState.activeState();
        }
        var core = ShipCores.nearestCore(level, pos, 32.0D);
        return core == null ? null : core.state();
    }
}
