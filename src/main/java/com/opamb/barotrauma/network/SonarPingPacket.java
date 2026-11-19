package com.opamb.barotrauma.network;

import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipSimulation;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.sonar.SonarScan;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求一次声纳扫描。
 *
 * <p>声纳必须由服务端扫：客户端拿不到未加载区块，而且在海里视野只有几格。</p>
 *
 * <p>模式按 wiki 的三种走：主动（360°）/ 定向（30° 锥）/ 被动（只听声音，射程随船的吵闹程度）。</p>
 */
public record SonarPingPacket(BlockPos sonarPos, int range, int mode, float aimX, float aimY, float aimZ) {

    /** 被动声纳的最小声程：船完全安静时也能听到周围这么远。 */
    private static final int PASSIVE_MIN_RANGE = 16;
    /** 被动声纳每多一格射程需要的"吵闹程度"跨度。 */
    private static final int PASSIVE_RANGE_SPAN = 48;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(sonarPos);
        buf.writeVarInt(range);
        buf.writeVarInt(mode);
        buf.writeFloat(aimX);
        buf.writeFloat(aimY);
        buf.writeFloat(aimZ);
    }

    public static SonarPingPacket decode(FriendlyByteBuf buf) {
        return new SonarPingPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // 服务端限频：客户端 10Hz 发请求，但每次扫描要翻已加载区块；
            // 每名玩家最多 5Hz，多余请求直接丢掉。
            long lastScan = player.getPersistentData().getLong("BarotraumaSonarScanTick");
            if (lastScan > 0L && lastScan <= player.tickCount
                    && player.tickCount - lastScan < 4L) {
                return;
            }
            player.getPersistentData().putLong("BarotraumaSonarScanTick", player.tickCount);
            ServerLevel level = player.serverLevel();
            // 声纳扫描中心必须和自动驾驶/目标标记用同一个基准：
            // 方块船 = 声纳台方块；物理船 = 船核心的世界坐标（方块在 VS2 船坞里，
            // 方块坐标不是世界坐标；玩家站的位置也不是核心，差了会让目标整体歪掉）。
            // 只有声纳/玩家确实在活动物理船上时才走物理状态；方块船不能因为
            // "世界上有一艘物理船"就套用它的电压和核心位置。
            boolean physical = com.opamb.barotrauma.ship.vs.VsState.active()
                    && com.opamb.barotrauma.ship.vs.VsPilot.activeLevel() == level;
            if (physical) {
                // 代理方块的世界坐标不一定被 managingShip 认出来，补上舱室/船心距离判定；
                // 玩家仍在方块船上（附近有方块船核心）时不能算物理船。
                // 归属要用“舱室几何边界”，不能看干湿：湿盒分支允许位置高于盒顶，
                // 只要低于水面就返回 false（非 null），会把旁边方块船的玩家误认成物理船。
                boolean confirmed = com.opamb.barotrauma.ship.vs.VsState
                        .isPhysical(level, sonarPos)
                        || Boolean.TRUE.equals(com.opamb.barotrauma.ship.vs.VsState
                        .queryInsideCompartment(level, player.position()));
                net.minecraft.world.phys.Vec3 shipPos =
                        com.opamb.barotrauma.ship.vs.VsPilot.shipPosition();
                boolean nearShip = shipPos != null
                        && shipPos.distanceToSqr(player.position()) <= SonarShipOwnership.NEAR_SHIP_RANGE_SQ;
                boolean blockShipNearby = com.opamb.barotrauma.ship.ShipCores
                        .nearestCore(level, player.blockPosition(),
                                SonarShipOwnership.BLOCK_SHIP_RANGE) != null;
                physical = SonarShipOwnership.ownedByPhysicalShip(
                        confirmed, nearShip, blockShipNearby);
            }
            BlockState sonarState = level.getBlockState(sonarPos);
            BlockPos center;
            if (physical) {
                BlockPos assembled = com.opamb.barotrauma.ship.vs.VsState.corePos();
                center = assembled != null
                        ? assembled.offset(com.opamb.barotrauma.ship.vs.VsState.offsetX(),
                                com.opamb.barotrauma.ship.vs.VsState.offsetY(),
                                com.opamb.barotrauma.ship.vs.VsState.offsetZ())
                        : player.blockPosition();
            } else {
                center = sonarState.is(ModShipBlocks.SONAR.get())
                        || sonarState.is(ModShipBlocks.NAVIGATION_TERMINAL.get())
                        ? sonarPos : player.blockPosition();
            }

            ShipCoreBlockEntity core = null;
            ShipState ship = null;
            if (physical) {
                // 物理船：方块在 VS2 船坞里，世界里没有它的核心。附近若恰好有别的
                // 方块船核心，绝不能拿来顶替——那会把方块船的电压/船体混到物理船上，
                // 让声纳显示错误的船或误判断电。只认物理船自己的那份状态。
                if (com.opamb.barotrauma.ship.vs.VsSupport.available()
                        && com.opamb.barotrauma.ship.vs.VsPilot.hasShip()) {
                    ship = com.opamb.barotrauma.ship.vs.VsPilot.state();
                }
            } else {
                core = ShipCores.resolve(level, null, center);
                if (core != null) {
                    ship = core.state() != null ? core.state() : core.rescan(level);
                }
            }
            Set<BlockPos> hull = ship == null ? null : hullForSonar(ship, physical);

            // 导航/声纳核心耐久归零：终端离线，扫描结果为空。
            boolean terminalBroken = core != null && core.condition() <= 0.0D;
            if (!terminalBroken && physical) {
                var physicalCore = com.opamb.barotrauma.ship.vs.VsPilot.sonarCoreBlockEntity(level);
                if (physicalCore != null) {
                    terminalBroken = physicalCore.condition() <= 0.0D;
                }
            }

            SonarScan.Mode scanMode = SonarScan.Mode.byOrdinal(mode);
            // 被动模式没有回波，射程由船有多吵决定：反应堆出力 + 引擎在不在推
            int effectiveRange = scanMode == SonarScan.Mode.PASSIVE
                    ? passiveRange(ship)
                    : range;

            // 定向脉冲打向"视线方向"（屏幕里准星指的地方），没给就退回船的推力方向
            float dirX = aimX;
            float dirY = aimY;
            float dirZ = aimZ;
            if (Math.abs(dirX) + Math.abs(dirY) + Math.abs(dirZ) < 1.0E-4F) {
                if (physical) {
                    // 物理船没有世界八向油门：退回船头方向。
                    net.minecraft.world.phys.Vec3 forward =
                            com.opamb.barotrauma.ship.vs.VsPilot.forwardWorld();
                    dirX = forward == null ? 1.0F : (float) forward.x;
                    dirY = 0.0F;
                    dirZ = forward == null ? 0.0F : (float) forward.z;
                } else {
                    dirX = ship == null ? 0.0F : ship.throttleX();
                    dirY = ship == null ? 0.0F : ship.throttleY();
                    dirZ = ship == null ? 1.0F : ship.throttleZ();
                }
                if (dirX == 0.0F && dirY == 0.0F && dirZ == 0.0F) {
                    dirZ = 1.0F;
                }
            }

            // 用终端自己的电压：绑定的接线盒坏了以后 consumerVoltage(终端)=0，
            // 但全船可能还有电，声纳不能继续扫描。
            double terminalVoltage = ship == null ? 1.0D : ship.simulation().voltage();
            if (ship != null) {
                BlockPos terminalCell = null;
                if (core != null) {
                    terminalCell = core.getBlockPos();
                } else if (physical) {
                    var physicalCore = com.opamb.barotrauma.ship.vs.VsPilot
                            .sonarCoreBlockEntity(level);
                    if (physicalCore != null) {
                        terminalCell = com.opamb.barotrauma.ship.vs.VsPilot
                                .toAssembly(physicalCore.getBlockPos());
                    }
                }
                if (terminalCell != null) {
                    terminalVoltage = ship.simulation().consumerVoltage(terminalCell);
                }
            }
            // 物理船没有声纳核心：终端视为离线，不能继续扫描。
            if (physical && core == null && !terminalBroken
                    && com.opamb.barotrauma.ship.vs.VsState.active()
                    && com.opamb.barotrauma.ship.vs.VsPilot.sonarCoreBlockEntity(level) == null) {
                terminalBroken = true;
            }
            String offlineReason = null;
            if (physical && core == null && com.opamb.barotrauma.ship.vs.VsState.active()
                    && com.opamb.barotrauma.ship.vs.VsPilot.sonarCoreBlockEntity(level) == null) {
                offlineReason = "终端缺失：物理船没有声纳核心";
            } else if (terminalBroken) {
                offlineReason = "终端损坏：需要扳手/螺丝刀修理";
            } else if (terminalVoltage < 0.5D) {
                offlineReason = "电力不足：声纳离线（需要 ≥50% 电压）";
            }
            SonarScan.Frame frame = !terminalBroken && terminalVoltage >= 0.5D
                    ? SonarScan.scan(level, center, effectiveRange, hull, player,
                            scanMode, dirX, dirY, dirZ)
                    : new SonarScan.Frame(effectiveRange, new int[0]);

            int power = ship == null ? 100 : ship.power();
            double speed = ship == null ? 0.0D : ship.blocksPerSecond();
            // 有惯性之后"下潜速度"得看真实垂速，不能再拿推力方向凑
            double verticalSpeed = ship == null ? 0.0D : ship.physics().verticalSpeed();
            long speedTenths = Math.round(speed * 3.6D * 10.0D);          // 格/秒 → 公里/时
            long diveTenths = Math.round(Math.abs(verticalSpeed) * 3.6D * 10.0D)
                    * (verticalSpeed < -0.05D ? -1 : verticalSpeed > 0.05D ? 1 : 0);

            // 自动驾驶目标：转成相对扫描中心的偏移（客户端点选也以这个中心为基准）
            BlockPos target = ship == null ? null : ship.autopilotTarget();
            BlockPos base = center;
            boolean hasTarget = target != null;

            // 对接面板：船核心当前世界坐标 → 最近哨站的靠泊标准位距离/状态
            BlockPos coreWorld = coreWorld(core, center, physical);
            Dock dock = dockInfo(level, coreWorld, physical);
            // 客户端舰况图用「声纳点（相对 center）− 这个偏移」换算核心局部坐标，
            // 所以基准是 center，不是玩家位置（方块船时 center 是声纳台）。
            int cdx = coreWorld.getX() - center.getX();
            int cdy = coreWorld.getY() - center.getY();
            int cdz = coreWorld.getZ() - center.getZ();
            Waypoint waypoint = waypoint(level, base);
            // 舱室格是装配坐标：物理船减装配核心，减掉位移后的 coreWorld 会整体偏掉。
            BlockPos geometryCore = physical
                    && com.opamb.barotrauma.ship.vs.VsState.corePos() != null
                    ? com.opamb.barotrauma.ship.vs.VsState.corePos() : coreWorld;

            // 物理船回传实际船头（×100，客户端画箭头/方位角）；方块船仍是八向推力。
            int headingX;
            int headingY;
            int headingZ;
            if (physical) {
                net.minecraft.world.phys.Vec3 forward =
                        com.opamb.barotrauma.ship.vs.VsPilot.forwardWorld();
                headingX = forward == null ? 0 : (int) Math.round(forward.x * 100.0D);
                headingY = 0;
                headingZ = forward == null ? 0 : (int) Math.round(forward.z * 100.0D);
            } else {
                headingX = ship == null ? 0 : ship.throttleX();
                headingY = ship == null ? 0 : ship.throttleY();
                headingZ = ship == null ? 0 : ship.throttleZ();
            }

            ModNetwork.sendToPlayer(player, new SonarDataPacket(frame.range(), frame.points(),
                    power,
                    headingX,
                    headingY,
                    headingZ,
                    scanMode.id(),
                    -center.getY(),
                    (int) speedTenths,
                    (int) diveTenths,
                    ship != null && ship.autopilotHold() ? 1 : 0,
                    hasTarget,
                    hasTarget ? target.getX() - base.getX() : 0,
                    hasTarget ? target.getY() - base.getY() : 0,
                    hasTarget ? target.getZ() - base.getZ() : 0,
                    statusText(ship, center, scanMode, frame.range(), offlineReason),
                    coreText(ship, physical),
                    dock.state(), dock.distance(), dock.label(),
                    physical,
                    hullPercent(ship), waterPercent(ship),
                    cdx, cdy, cdz,
                    roomRects(ship, geometryCore),
                    waypoint.set(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.label()));
        });
        context.setPacketHandled(true);
    }

    /**
     * 船的船核心现在的世界坐标。
     *
     * <p>物理船的方块在 VS2 船坞里，核心方块坐标已经不是世界坐标了；
     * 装配时记下的 {@code VsState.corePos()} 加上整位移才是船真正的位置。</p>
     */
    private static BlockPos coreWorld(com.opamb.barotrauma.blockentity.ShipCoreBlockEntity core,
                                      BlockPos fallback, boolean physical) {
        if (physical) {
            BlockPos assembled = com.opamb.barotrauma.ship.vs.VsState.corePos();
            if (assembled != null) {
                // 必须用完整船体变换：偏航后“核心 + 平移”会和真实位置差 2·cu，
                // 声纳显示的对接距离就会和 VsPilot.dock 的判定不一致。
                net.minecraft.world.phys.Vec3 world =
                        com.opamb.barotrauma.ship.vs.VsPilot.assemblyToWorld(assembled);
                if (world != null) {
                    return BlockPos.containing(world);
                }
            }
        }
        return core != null ? core.getBlockPos() : fallback;
    }

    /** 对接状态：0 不可用 / 1 物理船在码头范围内 / 2 方块船停在码头。 */
    private record Dock(int state, int distance, String label) {
    }

    /** 声纳上的下一个目标点（相对玩家坐标）。 */
    private record Waypoint(boolean set, int x, int y, int z, String label) {
        private static final Waypoint NONE = new Waypoint(false, 0, 0, 0, "");
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
                        com.opamb.barotrauma.world.MissionInstance.ANCHOR_Y, round.instanceZ());
            }
            var node = com.opamb.barotrauma.campaign.CampaignMap.node(round.destination());
            String label = node == null ? "目的站" : node.name();
            return new Waypoint(true,
                    target.getX() - base.getX(),
                    target.getY() - base.getY(),
                    target.getZ() - base.getZ(),
                    label);
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

    /** 船壳平均完好度（0..100）。没有耐久记录 = 满血。 */
    private static int hullPercent(ShipState ship) {
        if (ship == null) {
            return 100;
        }
        Set<BlockPos> hull = ship.geometry().hull();
        if (hull.isEmpty()) {
            return 100;
        }
        float sum = 0.0F;
        for (BlockPos pos : hull) {
            sum += ship.hullDamage().health(pos);
        }
        return Math.round(sum / hull.size() * 100.0F);
    }

    private static int waterPercent(ShipState ship) {
        return ship == null ? 0 : (int) Math.round(ship.floodRatio() * 100.0D);
    }

    /**
     * 左舷「舰况」图用的舱室侧视矩形：5 个 int 一组
     * （minX, maxX, minY, maxY, 进水百分比），坐标以船核心为原点。
     * 最多 10 个舱室，超出直接丢弃——面板上也画不下。
     */
    private static int[] roomRects(ShipState ship, BlockPos coreWorld) {
        if (ship == null || coreWorld == null) {
            return new int[0];
        }
        java.util.List<Integer> out = new java.util.ArrayList<>();
        int used = 0;
        for (com.opamb.barotrauma.ship.ShipGeometry.Compartment compartment : ship.compartments()) {
            if (used++ >= 10) {
                break;
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (BlockPos pos : compartment.cells()) {
                int lx = pos.getX() - coreWorld.getX();
                int ly = pos.getY() - coreWorld.getY();
                minX = Math.min(minX, lx);
                maxX = Math.max(maxX, lx);
                minY = Math.min(minY, ly);
                maxY = Math.max(maxY, ly);
            }
            if (minX > maxX || minY > maxY) {
                continue;
            }
            out.add(minX);
            out.add(maxX);
            out.add(minY);
            out.add(maxY);
            out.add((int) Math.round(compartment.waterRatio() * 100.0D));
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    /**
     * 声纳要显示的船体点。
     *
     * <p>物理船的船体方块在船坞坐标里，而声纳是以玩家（船）为中心扫世界——
     * 不把船体点按船的整数位移搬过去，画面上那团绿色船体就会留在装配时的老位置，
     * 看起来就是"声纳里的船不跟着船走"。</p>
     */
    private static Set<BlockPos> hullForSonar(ShipState ship, boolean physical) {
        Set<BlockPos> raw = ship.geometry().hull();
        if (!physical) {
            return raw;
        }
        int dx = com.opamb.barotrauma.ship.vs.VsState.offsetX();
        int dy = com.opamb.barotrauma.ship.vs.VsState.offsetY();
        int dz = com.opamb.barotrauma.ship.vs.VsState.offsetZ();
        if (dx == 0 && dy == 0 && dz == 0) {
            return raw;
        }
        // 只做平移：船体点是"相对核心"的局部坐标，左舷舰况图要用未旋转的
        // 侧视形状；真正的偏航旋转在客户端 drawCloud 里只对声纳屏的
        // TYPE_HULL 点做，别把左舷侧视图一起转歪。
        Set<BlockPos> out = new java.util.HashSet<>(raw.size());
        for (BlockPos pos : raw) {
            out.add(pos.offset(dx, dy, dz));
        }
        return out;
    }

    /**
     * 核心页正文。
     *
     * <p>声纳台现在兼任船核心，所以"核心"这一页讲的就是这艘船本身：
     * 船体扫描结果（舱室、方块数、密闭）+ 机器清单 + 实时状态。</p>
     */
    private static String coreText(ShipState ship, boolean physical) {
        if (ship == null) {
            return "未接入船核心\n\n声纳台就是船核心：把它装进船体内部，右键即可接管这艘船。";
        }
        // 明确告诉玩家现在哪一套移动系统在跑——两套同时跑过一次就够了
        String drive = physical
                ? "驱动：VS2 物理刚体（方块推进已停用，姿态锁定水平）"
                : "驱动：方块推进（离散落格，未物理化）";
        return ship.summary().getString() + "\n" + ship.liveReport().getString() + "\n" + drive;
    }

    /** 船越吵，被动声纳听得越远（wiki：range of passive sonar is dependent on how loud the submarine is）。 */
    private static int passiveRange(ShipState ship) {
        if (ship == null) {
            return PASSIVE_MIN_RANGE;
        }
        ShipSimulation.Report report = ship.simulation().report();
        double reactor = Math.min(1.0D, report.powerOutput() / 5000.0D);
        // 只有真正转螺旋桨才吵：纯转向/纯压载不算引擎噪音。
        boolean thrusting = ship.helmForward() != 0
                || ship.throttleX() != 0 || ship.throttleZ() != 0;
        double engine = thrusting ? 1.0D : 0.0D;
        double loudness = Math.min(1.0D, reactor * 0.75D + engine * 0.45D);
        return (int) Math.round(PASSIVE_MIN_RANGE + PASSIVE_RANGE_SPAN * loudness);
    }

    /** 面板右栏的几行字。 */
    public static String statusText(ShipState ship, BlockPos center, SonarScan.Mode mode, int range) {
        return statusText(ship, center, mode, range, null);
    }

    /** @param offlineReason 终端缺失/损坏/欠压的具体原因；null = 正常 */
    public static String statusText(ShipState ship, BlockPos center, SonarScan.Mode mode, int range,
                                    @org.jetbrains.annotations.Nullable String offlineReason) {
        StringBuilder sb = new StringBuilder();
        String modeName = switch (mode) {
            case PASSIVE -> "被动";
            case DIRECTIONAL -> "定向";
            default -> "主动";
        };
        sb.append(modeName).append(" ").append(range).append(" m");
        if (mode == SonarScan.Mode.ACTIVE) {
            sb.append("  ·  会引来生物");
        }
        if (ship == null) {
            sb.append("\n未接入船核心");
            return sb.toString();
        }
        if (offlineReason != null) {
            sb.append("\n").append(offlineReason);
            return sb.toString();
        }
        if (ship.simulation().voltage() < 0.5D) {
            sb.append("\n电力不足：声纳离线（需要 ≥50% 电压）");
            return sb.toString();
        }
        ShipSimulation.Report report = ship.simulation().report();
        sb.append(String.format(Locale.ROOT, "\n温度 %.0f°C   氧气 %.0f%%",
                report.temperature(), report.oxygenPercent() * 100.0D));
        return sb.toString();
    }
}
