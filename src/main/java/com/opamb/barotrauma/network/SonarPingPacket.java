package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.menu.SonarMenu;
import com.opamb.barotrauma.ship.ShipSimulation;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.sonar.SonarScan;
import com.opamb.barotrauma.sonar.SonarPulse;
import com.opamb.barotrauma.sonar.SonarCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import org.joml.Quaternionf;

import java.util.Set;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：订阅当前脉冲和仪表读数，或调整下一发脉冲。
 *
 * <p>声纳必须由服务端扫：客户端拿不到未加载区块，而且在海里视野只有几格。</p>
 *
 * <p>模式按 wiki 的三种走：主动（360°）/ 定向（30° 锥）/ 被动（只听声音，射程随船的吵闹程度）。</p>
 */
public record SonarPingPacket(BlockPos sonarPos, int menuId, int range, int mode,
                              float aimX, float aimY, float aimZ, long lastPulse, int settingsRevision) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(sonarPos);
        buf.writeVarInt(menuId);
        buf.writeVarInt(range);
        buf.writeVarInt(mode);
        buf.writeFloat(aimX);
        buf.writeFloat(aimY);
        buf.writeFloat(aimZ);
        buf.writeVarLong(lastPulse);
        buf.writeVarInt(settingsRevision);
    }

    public static SonarPingPacket decode(FriendlyByteBuf buf) {
        return new SonarPingPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarLong(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof SonarMenu menu)
                    || menu.containerId != menuId || !menu.sonarPos().equals(sonarPos) || !menu.stillValid(player)) {
                return;
            }
            // 限制单个观察者的状态查询频率；终端另有自己的扫频。
            long lastScan = player.getPersistentData().getLong("BarotraumaSonarScanTick");
            if (lastScan > 0L && lastScan <= player.tickCount
                    && player.tickCount - lastScan < 4L) {
                return;
            }
            player.getPersistentData().putLong("BarotraumaSonarScanTick", player.tickCount);
            ServerLevel level = player.serverLevel();
            boolean physical = menu.physical();
            ShipCoreBlockEntity core = menu.terminal();
            ShipState ship = menu.ship(level);
            BlockPos assemblyCore = physical ? VsState.corePos() : null;
            Vec3 worldCore = physical ? VsPilot.assemblyToWorld(assemblyCore) : null;
            BlockPos center;
            if (physical) {
                center = worldCore == null ? player.blockPosition() : BlockPos.containing(worldCore);
            } else {
                center = sonarPos;
            }
            Set<BlockPos> hull = ship == null ? null : ship.geometry().hull();

            // 导航/声纳核心耐久归零：终端离线，扫描结果为空。
            boolean terminalBroken = core != null && core.condition() <= 0.0D;
            // 用终端自己的电压：绑定的接线盒坏了以后 consumerVoltage(终端)=0，
            // 但全船可能还有电，声纳不能继续扫描。
            double terminalVoltage = ship == null ? 1.0D : ship.simulation().voltage();
            if (ship != null) {
                BlockPos terminalCell = null;
                if (core != null) {
                    terminalCell = physical ? VsPilot.toAssembly(core.getBlockPos())
                            : core.getBlockPos();
                }
                if (terminalCell != null) {
                    terminalVoltage = ship.simulation().consumerVoltage(terminalCell);
                }
            }
            // 物理船没有声纳核心：终端视为离线，不能继续扫描。
            String offlineReason = null;
            if (core == null || ship == null) {
                offlineReason = "终端缺失：未接入船核心";
            } else if (physical && worldCore == null) {
                offlineReason = "暂时无法定位潜艇";
            } else if (terminalBroken) {
                offlineReason = "终端损坏：需要扳手/螺丝刀修理";
            } else if (terminalVoltage < ShipCoreBlockEntity.TERMINAL_MIN_VOLTAGE) {
                offlineReason = "电力不足：声纳离线（需要 ≥50% 电压）";
            }
            boolean online = offlineReason == null;
            long now = level.getGameTime();
            SonarCycle cycle = core == null ? new SonarCycle() : core.sonarCycle();
            if (settingsRevision > 0) {
                cycle.configure(SonarScan.Mode.byOrdinal(mode), range, new Vec3(aimX, aimY, aimZ));
            }
            ShipState scanShip = ship;
            Vec3 direction = cycle.direction();
            SonarPulse current = cycle.update(online, now, center, () -> cycle.mode() == SonarScan.Mode.PASSIVE
                    ? SonarScan.listen(level, center, cycle.range(), passiveSoundRange(scanShip))
                    : SonarScan.scan(level, center, cycle.range(), null, null, cycle.mode(),
                            (float) direction.x, (float) direction.y, (float) direction.z));
            SonarPulse pulse = current != null && current.tick() != lastPulse ? current : null;

            int power = ship == null ? 100 : ship.power();
            double speed = ship == null ? 0.0D : ship.blocksPerSecond();
            // 有惯性之后"下潜速度"得看真实垂速，不能再拿推力方向凑
            double verticalSpeed = ship == null ? 0.0D : ship.physics().verticalSpeed();
            long speedTenths = Math.round(speed * 3.6D * 10.0D);          // 格/秒 → 公里/时
            long diveTenths = Math.round(Math.abs(verticalSpeed) * 3.6D * 10.0D)
                    * (verticalSpeed < -0.05D ? -1 : verticalSpeed > 0.05D ? 1 : 0);

            // 自动驾驶目标：转成相对扫描中心的偏移（客户端点选也以这个中心为基准）
            BlockPos target = ship == null || !ship.autopilotHold() ? null : ship.autopilotTarget();
            BlockPos base = center;
            boolean hasTarget = target != null;

            // 对接面板：船核心当前世界坐标 → 最近哨站的靠泊标准位距离/状态
            boolean located = !physical || worldCore != null;
            Dock dock = located ? dockInfo(level, center, physical) : new Dock(0, -1, "");
            Waypoint waypoint = located ? waypoint(level, base) : Waypoint.NONE;
            Long shipId = physical ? VsState.queryActiveShipId(level) : null;
            Vec3 shipAnchor = physical ? VsPilot.shipCoreOffset() : null;
            float distortion = core == null ? 0.0F : (float) (1.0D - core.condition() / core.maxCondition());
            BlockPos hullOrigin = worldCore == null ? center : assemblyCore;
            Vec3 hullOffset = worldCore == null ? Vec3.ZERO : worldCore.subtract(Vec3.atCenterOf(center));
            Quaternionf hullRotation = worldCore == null ? new Quaternionf() : VsPilot.assemblyRotation();
            if (physical && worldCore == null) {
                hull = null;
            }

            int headingX;
            int headingZ;
            if (physical) {
                Vec3 forward = VsPilot.forwardWorld();
                headingX = forward == null ? 0 : (int) Math.round(forward.x * 100.0D);
                headingZ = forward == null ? 0 : (int) Math.round(forward.z * 100.0D);
            } else {
                headingX = ship == null ? 0 : ship.throttleX();
                headingZ = ship == null ? 0 : ship.throttleZ();
            }

            ModNetwork.sendToPlayer(player, new SonarDataPacket(cycle.range(), SonarScan.hullPoints(hull, hullOrigin),
                    power,
                    headingX,
                    headingZ,
                    cycle.mode().id(),
                    -center.getY(),
                    (int) speedTenths,
                    (int) diveTenths,
                    ship == null ? 0 : ship.navigationMode().ordinal(),
                    hasTarget,
                    hasTarget ? target.getX() - base.getX() : 0,
                    hasTarget ? target.getY() - base.getY() : 0,
                    hasTarget ? target.getZ() - base.getZ() : 0,
                    offlineReason == null ? "" : offlineReason,
                    dock.state(), dock.distance(), dock.label(),
                    physical,
                    hullOffset, hullRotation,
                    waypoint.set(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.label(),
                    menuId, center, now, online, direction, settingsRevision, pulse,
                    shipId == null || shipAnchor == null ? -1L : shipId,
                    shipAnchor == null ? Vec3.ZERO : shipAnchor.add(0.5D, 0.5D, 0.5D),
                    waypoint.distance(), distortion));
        });
        context.setPacketHandled(true);
    }

    private record Dock(int state, int distance, String label) {
    }

    /** 声纳上的下一个目标点（相对玩家坐标）。 */
    private record Waypoint(boolean set, int x, int y, int z, String label, int distance) {
        private static final Waypoint NONE = new Waypoint(false, 0, 0, 0, "", 0);
    }

    /**
     * 本轮任务的目标点：有目的站时指向它的靠泊核心；无人航路点没有站，
     * 指向目的坐标（靠近 32 格即抵达）。
     */
    private static Waypoint waypoint(ServerLevel level, BlockPos base) {
        try {
            com.opamb.barotrauma.campaign.CampaignState state =
                    com.opamb.barotrauma.campaign.CampaignState.get(level);
            var round = state.round();
            if (round.phase() != com.opamb.barotrauma.campaign.RoundPhase.DIVING
                    || !round.hasInstance()) {
                return Waypoint.NONE;
            }
            BlockPos target;
            if (round.endHasOutpost()) {
                com.opamb.barotrauma.world.DockingBay.Site site =
                        new com.opamb.barotrauma.world.DockingBay.Site(
                                round.instanceX(), round.instanceZ());
                target = com.opamb.barotrauma.world.DockingBay.dockCore(
                        site, com.opamb.barotrauma.world.StarterSub.layout(level));
            } else {
                target = new BlockPos(round.instanceX(),
                        com.opamb.barotrauma.world.MissionInstance.destinationY(round.terrain()), round.instanceZ());
            }
            var node = com.opamb.barotrauma.campaign.CampaignMap.node(round.destination());
            String label = node == null ? "目的站" : node.name();
            var path = com.opamb.barotrauma.world.MissionInstance.activePath();
            boolean canyon = round.terrain() == com.opamb.barotrauma.world.level.TerrainTemplate.CANYON;
            int distance = canyon && path != null ? (int) path.distanceRemaining(Vec3.atCenterOf(base),
                    Vec3.atCenterOf(target)) : 0;
            return new Waypoint(true,
                    target.getX() - base.getX(),
                    target.getY() - base.getY(),
                    target.getZ() - base.getZ(),
                    label, distance);
        } catch (Throwable ignored) {
            // 世界数据还没初始化（测试/非常早的加载）：不标目标点
            return Waypoint.NONE;
        }
    }

    /**
     * 最近哨站的靠泊判定，和 {@code VsPilot.dock} 完全同一套坐标：
     * 站点中心 + 该布局的核心偏移（新布局「儒艮号」是 +6,0，旧小艇是 +1,−3）。
     */
    private static Dock dockInfo(ServerLevel level, BlockPos coreWorld, boolean physical) {
        com.opamb.barotrauma.world.DockingBay.Site site =
                com.opamb.barotrauma.world.DockingBay.siteAt(coreWorld.getX(), coreWorld.getZ());
        if (site == null) {
            return new Dock(0, -1, "");
        }
        BlockPos dockCore = com.opamb.barotrauma.world.DockingBay.dockCore(
                site, com.opamb.barotrauma.world.StarterSub.layout(level));
        int distance = (int) Math.round(Math.hypot(
                coreWorld.getX() - dockCore.getX(), coreWorld.getZ() - dockCore.getZ()));
        int dy = Math.abs(coreWorld.getY() - dockCore.getY());
        String label = site.equals(com.opamb.barotrauma.world.DockingBay.Site.MAIN)
                ? "起始哨站" : "任务前哨站";
        int state;
        if (physical) {
            // 和 VsPilot.dock 的判定一致：水平 16 格 / 高差 6 格
            state = distance <= 16 && dy <= 6 ? 1 : 0;
        } else {
            // 方块船就停在码头：核心基本重合才算「已靠泊」
            state = distance <= 6 && dy <= 4 ? 2 : 0;
        }
        return new Dock(state, Math.max(0, distance), label);
    }

    private static int passiveSoundRange(ShipState ship) {
        ShipSimulation.Report report = ship.simulation().report();
        double reactor = Math.min(1.0D, report.powerOutput() / 5000.0D);
        // 只有真正转螺旋桨才吵：纯转向/纯压载不算引擎噪音。
        boolean thrusting = ship.helmForward() != 0
                || ship.throttleX() != 0 || ship.throttleZ() != 0;
        double engine = thrusting ? ship.simulation().engineSupplyFactor() * ship.power() / 100.0D : 0.0D;
        return (int) Math.round(Math.max(reactor * 40.0D, engine * 60.0D));
    }

}
