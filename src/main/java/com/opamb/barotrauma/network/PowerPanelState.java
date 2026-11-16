package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.JunctionBoxBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.ship.PowerConsumer;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端电力面板数据：从一个接线盒出发，收集它附近 16 格内的用电设备。
 *
 * <p>坐标系由调用方显式传入（物理船用装配坐标，普通世界用世界坐标），并且打开
 * 面板时把目标身份（维度 + 坐标 + 坐标系）记在服务端会话里。之后的绑定/刷新只
 * 认这份记录，不再按“这个坐标当前有没有方块实体”重新猜坐标系——否则别人在原
 * 装配位置另放一个接线盒，物理船面板就会被错当成普通世界面板。</p>
 */
public final class PowerPanelState {

    public static final int RANGE = 16;
    public static final int MAX_DEVICES = 24;
    /** 玩家必须站在接线盒 8 格内才能打开/改绑定。 */
    private static final double MAX_PLAYER_DISTANCE_SQ = 8.0D * 8.0D;

    /** 一次面板会话的目标身份；方块船记核心坐标，刷新时按它取当前船状态。 */
    private record Panel(ResourceKey<Level> dimension, BlockPos boxCell, boolean physical,
                         @Nullable BlockPos corePos) {
    }

    private static final Map<UUID, Panel> PANELS = new ConcurrentHashMap<>();

    private PowerPanelState() {
    }

    /**
     * @param boxCell  接线盒坐标：物理船是装配坐标，普通世界是世界坐标
     * @param physical 是否物理船坐标系（由服务端调用方显式提供）
     * @param ship     这面盒子所属的船（打开请求已经选好，不要在这里重新搜）
     */
    public static PowerStatePacket build(ServerPlayer player, BlockPos boxCell, boolean physical,
                                         ShipState ship, boolean open) {
        ServerLevel level = player.serverLevel();
        BlockPos boxYard = physical ? VsPilot.toYard(boxCell) : boxCell;
        if (boxYard == null || !level.hasChunkAt(boxYard)) {
            // 客户端给的坐标绝不能触发同步区块加载。
            return null;
        }
        BlockEntity raw = level.getBlockEntity(boxYard);
        if (!(raw instanceof JunctionBoxBlockEntity box)) {
            return null;
        }
        if (ship == null) {
            return null;
        }
        List<PowerStatePacket.Device> devices = new ArrayList<>();
        for (var info : ship.simulation().consumerInfos()) {
            if (info.pos().distSqr(boxCell) > (double) RANGE * RANGE) {
                continue;
            }
            BlockPos deviceYard = physical ? VsPilot.toYard(info.pos()) : info.pos();
            BlockEntity deviceBe = deviceYard == null || !level.hasChunkAt(deviceYard)
                    ? null : level.getBlockEntity(deviceYard);
            String key = deviceBe == null
                    ? "block.barotrauma.junction_box.unknown"
                    : deviceBe.getBlockState().getBlock().getDescriptionId();
            // 绑定只改设备的 BE，ShipSimulation 的快照要等下一 tick 才更新；
            // 直接读 BE，绑定后立刻刷新也能显示新状态。
            BlockPos bound = deviceBe instanceof PowerConsumer consumer
                    ? consumer.powerBox() : info.box();
            devices.add(new PowerStatePacket.Device(info.pos(), key, info.demandKw(),
                    info.minVoltage(), bound));
        }
        devices.sort(Comparator.comparingDouble(d -> d.pos().distSqr(boxCell)));
        if (devices.size() > MAX_DEVICES) {
            devices = new ArrayList<>(devices.subList(0, MAX_DEVICES));
        }
        double voltage = ship.simulation().voltage();
        return new PowerStatePacket(boxCell, ship.simulation().supplyKw(),
                ship.simulation().report().powerDemand(), voltage,
                box.condition(), ship.simulation().boxLoad(boxCell), open, devices);
    }

    /**
     * 打开面板并记下目标身份。
     *
     * @param boxCell  接线盒坐标：物理船是装配坐标，普通世界是世界坐标
     * @param physical 是否物理船坐标系
     */
    public static void send(ServerPlayer player, BlockPos boxCell, boolean physical) {
        send(player, boxCell, physical,
                corePosFor(player.serverLevel(), boxCell, physical), true);
    }

    /**
     * 打开面板并记下目标身份；船由调用方选定（快捷打开已经按最近接线盒挑好）。
     *
     * @param boxCell  接线盒坐标：物理船是装配坐标，普通世界是世界坐标
     * @param physical 是否物理船坐标系
     * @param corePos  这面盒子所属方块船的船核心；物理船传 null
     */
    public static void send(ServerPlayer player, BlockPos boxCell, boolean physical,
                            @Nullable BlockPos corePos) {
        send(player, boxCell, physical, corePos, true);
    }

    /**
     * @param open true = 响应打开请求（客户端可以显示面板）；false = 绑定后的刷新，
     *             客户端只能更新已经打开的面板，不能把按 Esc 关掉的界面重新弹出来。
     */
    public static void send(ServerPlayer player, BlockPos boxCell, boolean physical,
                            @Nullable BlockPos corePos, boolean open) {
        ShipState ship = stateOf(player.serverLevel(), physical, corePos);
        PowerStatePacket state = build(player, boxCell, physical, ship, open);
        if (state == null) {
            PANELS.remove(player.getUUID());
            return;
        }
        PANELS.put(player.getUUID(), new Panel(player.serverLevel().dimension(),
                boxCell.immutable(), physical, corePos == null ? null : corePos.immutable()));
        ModNetwork.sendToPlayer(player, state);
    }

    /**
     * 服务端绑定校验：只接受该玩家当前打开面板的目标坐标。
     *
     * <p>坐标系、维度和盒子坐标都取自打开面板时的记录，客户端回传的坐标只有
     * 与记录一致才处理，避免绑定落到原装配位置或普通世界里的其它设备上。</p>
     */
    public static boolean bind(ServerPlayer player, BlockPos boxCell, BlockPos deviceCell, boolean bind) {
        ServerLevel level = player.serverLevel();
        Panel panel = PANELS.get(player.getUUID());
        if (panel == null
                || !panel.dimension().equals(level.dimension())
                || !panel.boxCell().equals(boxCell)
                || deviceCell == null) {
            return false;
        }
        boolean physical = panel.physical();
        BlockPos boxYard = physical ? VsPilot.toYard(boxCell) : boxCell;
        BlockPos deviceYard = physical ? VsPilot.toYard(deviceCell) : deviceCell;
        if (boxYard == null || deviceYard == null) {
            return false;
        }
        // 先查区块是否已加载，绝不因客户端坐标同步加载区块。
        if (!level.hasChunkAt(boxYard) || !level.hasChunkAt(deviceYard)) {
            return false;
        }
        if (!(level.getBlockEntity(boxYard) instanceof JunctionBoxBlockEntity)) {
            // 面板目标已经不存在，这次会话作废。
            PANELS.remove(player.getUUID());
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
        BlockEntity raw = level.getBlockEntity(deviceYard);
        if (!(raw instanceof PowerConsumer consumer)) {
            return false;
        }
        // 设备必须属于面板选中的这艘船，否则会写进一个永远不会被这台盒子供电的绑定。
        ShipState ship = stateOf(level, physical, panel.corePos());
        if (ship == null) {
            return false;
        }
        boolean known = false;
        for (var info : ship.simulation().consumerInfos()) {
            if (info.pos().equals(deviceCell)) {
                known = true;
                break;
            }
        }
        if (!known) {
            return false;
        }
        // PowerBox 与 ShipSimulation 的 boxes/consumers 使用同一套坐标。
        consumer.setPowerBox(bind ? boxCell : null);
        raw.setChanged();
        return true;
    }

    /** 绑定成功后刷新面板：沿用会话里的船身份重新取当前状态，不重新搜索。 */
    public static void refresh(ServerPlayer player, BlockPos boxCell) {
        Panel panel = PANELS.get(player.getUUID());
        if (panel != null && panel.boxCell().equals(boxCell)) {
            // 绑定后的刷新：只更新已经打开的面板，不再触发 setScreen。
            send(player, boxCell, panel.physical(), panel.corePos(), false);
        }
    }

    /** 玩家退出时丢掉面板会话。 */
    public static void clear(ServerPlayer player) {
        PANELS.remove(player.getUUID());
    }

    /**
     * 船停用/靠泊/重新装配/换世界时清掉所有面板会话。
     *
     * <p>物理面板只记装配坐标；同一坐标上换了另一艘船时，旧会话不能再去
     * 绑定或刷新新船。</p>
     */
    public static void clearAll() {
        PANELS.clear();
    }

    /** 没有快捷打开请求时自己找船：方块船的核心可能离盒子很远，按 64 格找。 */
    @Nullable
    private static BlockPos corePosFor(ServerLevel level, BlockPos pos, boolean physical) {
        if (physical) {
            return null; // 物理船用 VsState.activeState()，不需要核心坐标
        }
        var core = ShipCores.nearestCore(level, pos, 64.0D);
        return core == null ? null : core.getBlockPos();
    }

    /**
     * 按会话里的船身份取“当前”状态：开关门等操作会让核心 rescan 出新对象，
     * 不能一直用打开面板时那个旧引用。
     */
    @Nullable
    private static ShipState stateOf(ServerLevel level, boolean physical, @Nullable BlockPos corePos) {
        if (physical) {
            return VsState.activeState();
        }
        if (corePos == null || !level.hasChunkAt(corePos)) {
            return null;
        }
        if (!(level.getBlockEntity(corePos) instanceof ShipCoreBlockEntity core)) {
            return null;
        }
        // 方块船 state 不存档，重载后要等 rescan 才有。
        return core.state() != null ? core.state() : core.rescan(level);
    }
}
