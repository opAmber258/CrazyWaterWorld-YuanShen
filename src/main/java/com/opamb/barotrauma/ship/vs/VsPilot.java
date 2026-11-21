package com.opamb.barotrauma.ship.vs;

import com.opamb.barotrauma.block.HullBlock;
import com.opamb.barotrauma.blockentity.DrainPumpBlockEntity;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.data.ShipSavedData;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.network.ShipHullDamagePacket;
import com.opamb.barotrauma.network.ShipWaterStatePacket;
import com.opamb.barotrauma.registry.ModBlocks;
import com.opamb.barotrauma.registry.ModShipBlocks;
import com.opamb.barotrauma.ship.ShipGeometry;
import com.opamb.barotrauma.ship.MachineCount;
import com.opamb.barotrauma.ship.ShipSimulation;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.world.OceanEnvironment;
import com.opamb.barotrauma.world.StarterSub;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 物理船（VS2）的运行时：装配、推进、拆回方块。
 *
 * <p>这一版的核心约定是<strong>"一套移动系统"</strong>：船一旦被收成 VS2 刚体，
 * 方块那一套（{@code ShipMover} 平移、{@code ShipWater} 写水位、破口重扫）就通过
 * {@link VsState} 整段停掉，运动只由刚体算。之前这两套同时在跑，
 * 表现出来就是"船同时被方块逻辑和物理逻辑搬来搬去"。</p>
 *
 * <p>方块进了刚体之后并没有从世界消失，而是被 VS2 搬到船坞区块里、仍然在 tick。
 * 所以"这个方块属于物理船"这件事可以直接问 VS2（{@link VsBridge#managingShip}），
 * 不需要我们自己记坐标——这也让同世界的其它方块船不受影响。</p>
 *
 * <p>拆船：VS2 没有"刚体还原成方块"的 API（{@code deleteShip} 的第三个参数是
 * "把世界的方块挖掉"，不是"铺回方块"），所以装配时留一份方块快照，拆的时候按
 * 船自装配以来的整数位移铺回世界。</p>
 */
public final class VsPilot {

    private static ServerShip ship;
    private static VsThruster thruster;
    /** 装船那一刻的船状态。方块收进刚体后世界里找不到核心，面板和操舵靠它。 */
    private static ShipState state;
    /** 装配时那台声纳（船脑）的世界坐标。 */
    private static BlockPos corePos;
    /** 装配时船中心的世界坐标——拆船时用它算"船开出去多远"。 */
    private static Vec3 assembledPos = Vec3.ZERO;
    /** 装配时的方块快照（位置 → 方块状态），拆船时原样铺回。 */
    private static Map<BlockPos, BlockState> snapshot = Map.of();
    /** 装配时的船体方块数——和当前快照的差就是被砸掉的格数（破口大小）。 */
    private static int initialShipBlocks;
    /** 装配时的舱室格子（世界坐标）——船移动/拆船时把舱内积水清成空气。 */
    private static java.util.List<BlockPos> interiorCells = java.util.List.of();
    /**
     * 船壳以内、曾经确认过的舱内格（只增不减）。
     *
     * <p>切掉外壁舱口后重扫几何，洞口不再是 {@code isShell}，{@code exteriorCells}
     * 会从洞口灌进整个房间，连海空腔的 {@code interiorCells} 变成空集。靠泊吸附
     * 若只认最新几何，仍站在房间里的玩家/任务货会被排除、留在大海里。这里保留
     * 破洞前确认的舱内格，专门用于乘客归属判断（不包含包围盒四角的外海空气）。</p>
     */
    private static java.util.Set<BlockPos> knownInteriorCells = java.util.Set.of();
    /**
     * 流体拦截格 = 舱室格向外扩 1 圈（船壳层）。
     *
     * <p>船是连续坐标渲染的，拦截只能按整数格。只护舱室时，船 y 不是整数，
     * 舱底会漏出半层没被保护的空间，真实海水从破口钻进来，在舱底形成一小滩水。
     * 多护一圈船壳格，漏网的半层就落在船壳里（被船壳方块挡住），舱室本身保持真空气。</p>
     */
    private static java.util.List<BlockPos> guardCells = java.util.List.of();
    /** 水面状态同步计时。 */
    private static int waterSyncTimer;
    /** 最近一次发给客户端的水面数据；服务端判断“玩家在哪个舱室、是否已进水”时复用。 */
    private static ShipWaterStatePacket lastWaterPacket = ShipWaterStatePacket.empty();
    /** 上一次清舱内水用的整数偏移，避免每 tick 重复扫 273 格。 */
    private static int interiorOffsetX = Integer.MIN_VALUE;
    private static int interiorOffsetY = Integer.MIN_VALUE;
    private static int interiorOffsetZ = Integer.MIN_VALUE;
    /** 装配时声纳台（船脑）的世界坐标——交互锚点放在这里，跟着船整格移动。 */
    private static BlockPos sonarAnchor;
    /** 当前交互锚点实际所在的位置。 */
    private static BlockPos proxyPos;
    /** 每台泵的交互锚点（装配坐标）。泵方块同样会被 VS2 收走，右键不到。 */
    private static java.util.List<BlockPos> pumpAnchors = java.util.List.of();
    /** 泵锚点（装配坐标）→ 当前代理方块位置；每台泵各有一个代理。 */
    private static java.util.Map<BlockPos, BlockPos> pumpProxyByAnchor = java.util.Map.of();
    /**
     * 装配前抄下来的方块实体 NBT，等船坞偏移确定后装回船坞里对应的方块。
     *
     * <p>VS2 装配只搬方块状态，方块实体是新建的默认实例——反应堆燃料、泵的自动/流量、
     * 储物箱内容都会丢（用户实测"下潜后我放的燃料没了"）。装配前先存 NBT，
     * 船坞偏移一确定就 load 回去。</p>
     */
    private static java.util.Map<BlockPos, net.minecraft.nbt.CompoundTag> pendingBlockEntityTags =
            java.util.Map.of();
    /** 反应堆的交互锚点（18 格机器的主方块）。 */
    private static BlockPos reactorAnchor;
    private static BlockPos reactorProxyPos;
    /** 引擎的交互锚点（3×2×3 机器的主方块）。 */
    private static BlockPos engineAnchor;
    private static BlockPos engineProxyPos;
    /** 上一次 launch 用的维度。 */
    private static ServerLevel lastLevel;
    /**
     * 船坞平移量：船坞坐标 = 装配时的世界坐标 + 这个偏移。
     *
     * <p>VS2 装配把方块整体搬到远处的船坞区块。核心方块实体在船坞里 tick 时
     * 把真实坐标报上来（{@link VsState#observePhysicalCore}），这里减去装配时的
     * 声纳坐标就得到平移量。有了它才能读写船坞里的船体方块。</p>
     */
    private static int yardOffsetX = Integer.MIN_VALUE;
    private static int yardOffsetY = Integer.MIN_VALUE;
    private static int yardOffsetZ = Integer.MIN_VALUE;
    /** 什么时候开始允许船体破损对账（给 VS2 搬完方块留 1 秒）。 */
    private static long hullCheckStartGameTime = Long.MIN_VALUE;
    /** 船坞偏移扫描失败后的冷却，避免每 20 tick 全盒扫一遍。 */
    private static long nextYardScanGameTime = Long.MIN_VALUE;

    /** 舱门/舱口被等离子切割机累计切掉的进度（装配坐标）。 */
    private static final java.util.Map<BlockPos, Float> fixtureCut = new java.util.HashMap<>();
    /** fixtureCut 属于哪一次装配；换船/重新装配时清空进度。 */
    private static Vec3 fixtureCutAnchor;
    /** 舱门/舱口要切多少点才烂（每次 2 tick 调 1.5 点，约 6 秒）。 */
    private static final float FIXTURE_CUT_HEALTH = 90.0F;
    /** 物理船切割的位置级权限检查：同一目标 4 tick 内不重复发 BreakEvent。 */
    private static BlockPos protectCheckedPos;
    private static long protectCheckedTick = Long.MIN_VALUE;
    private static boolean protectDenied;
    /** 上次权限检查的玩家；换人必须重查，不能复用别人的授权结果。 */
    private static java.util.UUID protectPlayer;

    /**
     * 清空构件切割进度。
     *
     * <p>{@code assembledPos} 相同不代表同一次装配：原地拆了再装、或换世界时
     * 位置可能一样，必须按装配生命周期显式清掉，否则新船第一次切就可能直接切烂。</p>
     */
    private static void clearFixtureCut() {
        fixtureCut.clear();
        fixtureCutAnchor = null;
        protectCheckedPos = null;
        protectCheckedTick = Long.MIN_VALUE;
        protectDenied = false;
        protectPlayer = null;
    }

    static {
        VsState.install(VsPilot::observeCore, VsPilot::tryDamage,
                VsPilot::tryRepair, VsPilot::tryCut, VsPilot::damageExplosion);
        VsState.installBoardingResolver(VsPilot::boardingPositions);
        VsState.installGeometryRefresh(VsPilot::refreshPhysicalGeometry);
        VsState.installCompartmentQuery(new VsState.CompartmentQuery() {
            @Override
            public Boolean isPlayerInDryCompartment(Level level, Vec3 pos) {
                return VsPilot.isPlayerInDryCompartment(level, pos);
            }

            @Override
            public Boolean hasStandingRoom(Level level, Vec3 pos, double height) {
                return VsPilot.hasStandingRoom(level, pos, height);
            }

            @Override
            public Long activeShipId(Level level) {
                return ship == null ? null : ship.getId();
            }

            @Override
            public Boolean nearFloor(Level level, Vec3 pos, double maxAbove) {
                return VsPilot.nearFloor(level, pos, maxAbove);
            }

            @Override
            public Boolean isInsideCompartmentGeometry(Level level, Vec3 pos) {
                // 用实时逆变换 + interiorCells（含破洞后的历史兜底），不看水位。
                return isInsideInterior(level, pos);
            }

            @Override
            public Integer compartmentIndex(Level level, Vec3 pos) {
                if (ship == null || state == null || pos == null
                        || !(level instanceof ServerLevel)) {
                    return null;
                }
                // 船外位置必须返回 null：调用方把非 null 当作“在物理船上”。
                // 只做逆变换的话任何世界坐标都会落进某个装配格，compartmentAt
                // 返回 -1，站台/船外/别船上的玩家会被判成舱室 -1 → 可用氧 0。
                if (!isInsideInterior(level, pos)) {
                    return null;
                }
                BlockPos cell = toAssemblyCell(level, pos);
                return cell == null ? null : state.compartmentAt(cell);
            }
        });
        VsState.installBlockRecorder(VsPilot::recordPlacedBlock);
        // 只注册服务端干燥区：单人存档里客户端和集成服务端同进程，
        // 客户端渲染用的 ShipWaterRenderer 不能被这里覆盖（否则方块船舱内会漏滤镜）。
        OceanEnvironment.setServerDryZone(new OceanEnvironment.DryZone() {
            @Override
            public boolean isDry(Level level, Vec3 pos) {
                return VsPilot.isInsideDryZone(level, pos);
            }

            @Override
            public boolean isInsideCompartment(Level level, Vec3 pos) {
                return VsPilot.isInsideCompartment(level, pos);
            }
        });
    }

    /** 装配刚完成时 VS2 可能还没把这艘船标成"已加载"，推进器先挂在这里，下一 tick 再试。 */
    private static ServerShip pendingShip;
    private static VsThruster pendingThruster;
    private static ServerLevel pendingLevel;

    /**
     * 装配之后还要继续把船标成"动态"的 tick 数。
     *
     * <p>VS2 的 ShipAssembler 建船时先 {@code toShip.isStatic = true}
     * （1.20.1/main 的 ShipAssembler.kt:139），几行之后才在 :172 设回 false。
     * 刚体的类型多半是按当时那个 true 建的，之后改标志位并不重建刚体——
     * 结果就是船在物理世界里是个静态体，重力、推力全不响应。</p>
     */
    private static int unstaticTicks;

    /** 物理船活着的时候每 2 tick 推进一次船内模拟（反应堆 / 氧气 / 水泵 / 压载）。 */
    private static long simTickCounter;
    /** 连续多少 tick 没有玩家开着这艘船的声纳菜单；用于无人掌舵自动停车。 */
    private static int helmWatchdogTicks;

    private VsPilot() {
    }

    // ------------------------------------------------------------------ 查询

    /** 物理船的船状态；没有物理船时返回 null。 */
    @Nullable
    public static ShipState state() {
        return ship == null ? null : state;
    }

    public static boolean hasShip() {
        return ship != null;
    }

    public static VsThruster thruster() {
        return thruster;
    }

    /** 装配时那台声纳的位置。 */
    @Nullable
    public static BlockPos corePos() {
        return corePos;
    }

    /**
     * 装配后船核心方块实体在船坞里的坐标（核心装配位 + 船坞偏移）。
     *
     * <p>VS2 把方块搬进刚体后，世界里的原坐标只剩空气；要读船上的方块实体
     * （货架、泵等）必须按船坞坐标找。</p>
     */
    @Nullable
    public static BlockPos coreYardPos() {
        if (corePos == null || yardOffsetX == Integer.MIN_VALUE) {
            return null;
        }
        return corePos.offset(yardOffsetX, yardOffsetY, yardOffsetZ);
    }

    /**
     * 立刻对账船坞偏移，不等每秒一次的扫描。
     *
     * <p>装配 → 传送 → 接任务是在同一小段时间里发生的：货运装货时
     * {@code yardOffset} 还可能没探测到，货架会一个都找不到，货只能掉在
     * 地上（用户实测）。这里强制扫一次；扫成功后顺带把 pending 的方块
     * 实体 NBT 装回船坞，货架容器才读得到。</p>
     *
     * @return true = 偏移已经可用
     */
    public static boolean ensureYardOffset(ServerLevel level) {
        if (yardOffsetX != Integer.MIN_VALUE) {
            return true;
        }
        if (level == null || ship == null || corePos == null || snapshot.isEmpty()) {
            return false;
        }
        nextYardScanGameTime = Long.MIN_VALUE;
        return discoverYardOffset(level);
    }

    /**
     * 刚体质量（kg）。GameTest 用它断言"方块到底有没有全部进刚体"——
     * 我们踩过的坑就是只有声纳一格进去了、质量只剩 300。
     */
    public static double shipMass() {
        if (ship == null) {
            return 0.0D;
        }
        try {
            ServerShip live = lastLevel == null ? ship : view(lastLevel);
            return VsBridge.mass(live);
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    /** 刚体世界坐标；没船返回 null。 */
    @Nullable
    public static Vec3 shipPosition() {
        if (ship == null) {
            return null;
        }
        try {
            ServerShip live = lastLevel == null ? ship : view(lastLevel);
            return VsBridge.position(live);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 刚体中心到方块核心的偏移（装配时定下，随船平移不变）。
     * 传送到副本码头时用它把"核心应该落在哪"换算成 {@link #teleportShipTo} 要的船中心坐标。
     */
    @Nullable
    public static Vec3 shipCoreOffset() {
        if (ship == null || corePos == null) {
            return null;
        }
        return new Vec3(corePos.getX() - assembledPos.x,
                corePos.getY() - assembledPos.y,
                corePos.getZ() - assembledPos.z);
    }

    /** 刚体速度（格/秒）；没船返回 null。 */
    @Nullable
    public static Vec3 shipVelocity() {
        if (ship == null) {
            return null;
        }
        try {
            ServerShip live = lastLevel == null ? ship : view(lastLevel);
            return VsBridge.velocity(live);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 把物理船传送到同一维度的目标坐标；成功返回 null，失败返回原因。
     *
     * <p>下潜进任务海域实例时用。传送前先加载船坞里船实际占用的区块：VS2 下一 tick
     * 会把船的 activeChunks 更新到新位置，如果那时区块还没加载，玩家一移动就会被
     * 判定成"撞到未加载的船"并回弹（日志 {@code moved while colliding with unloaded ships}）。</p>
     */
    @Nullable
    public static String teleportShipTo(ServerLevel level, Vec3 target) {
        if (ship == null) {
            return "没有物理船";
        }
        try {
            ServerShip live = VsBridge.loaded(level, ship);
            if (live == null) {
                live = ship;
            }
            // 常驻票据：传送后 activeChunks 会变，票据由 tick() 的周期同步搬到新位置。
            VsChunkTickets.sync(level, live);
            Vec3 from = VsBridge.position(live);
            // 钉在船上的散货必须跟着传送走：VS2 传送只改刚体位置，实体留在原地。
            // 商店"送至潜艇"的大件（潜水服）和没有空箱时生成的金属箱都是这种散货，
            // 起航前生成在出发站货架顶上，不搬的话会整批留在出发站（用户实测：
            // 两只玻璃潜水服 + 一个金属箱全落在出发点）。
            Vec3 coreOffset = shipCoreOffset();
            java.util.List<net.minecraft.world.entity.item.ItemEntity> carried =
                    new java.util.ArrayList<>();
            java.util.List<Vec3> carriedOffsets = new java.util.ArrayList<>();
            if (level != null && coreOffset != null) {
                Vec3 coreBefore = from.add(coreOffset);
                net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                        coreBefore.x - 48.0D, coreBefore.y - 48.0D, coreBefore.z - 48.0D,
                        coreBefore.x + 48.0D, coreBefore.y + 48.0D, coreBefore.z + 48.0D);
                for (net.minecraft.world.entity.item.ItemEntity item
                        : level.getEntitiesOfClass(
                                net.minecraft.world.entity.item.ItemEntity.class, area)) {
                    if (!item.getPersistentData().getBoolean(
                            com.opamb.barotrauma.mission.MissionCargo.TAG_PINNED)) {
                        continue;
                    }
                    carried.add(item);
                    carriedOffsets.add(item.position().subtract(coreBefore));
                }
            }
            VsBridge.teleport(level, live, target);
            if (!carried.isEmpty()) {
                Vec3 coreAfter = target.add(coreOffset);
                int moved = 0;
                for (int i = 0; i < carried.size(); i++) {
                    net.minecraft.world.entity.item.ItemEntity item = carried.get(i);
                    if (item.isRemoved()) {
                        continue;
                    }
                    Vec3 offset = carriedOffsets.get(i);
                    item.setPos(coreAfter.x + offset.x, coreAfter.y + offset.y,
                            coreAfter.z + offset.z);
                    item.setDeltaMovement(Vec3.ZERO);
                    moved++;
                }
                LOGGER.info("[vs] 传送时随船搬走钉住的散货 {} 件", moved);
            }
            LOGGER.info("[vs] 物理船传送：{} -> {}（船坞区块已预加载）", from, target);
            return null;
        } catch (Throwable error) {
            LOGGER.warn("[vs] 物理船传送失败", error);
            return "传送失败：" + error;
        }
    }

    /** 同步加载船坞里船实际占用的区块（见 {@link VsBridge#loadShipChunks}）。 */
    public static void preloadShipChunks(ServerLevel level) {
        if (ship == null) {
            return;
        }
        try {
            ServerShip live = VsBridge.loaded(level, ship);
            VsChunkTickets.sync(level, live == null ? ship : live);
        } catch (Throwable ignored) {
            // 预加载失败不影响后续流程，VS2 自己的 chunk watch 还会补
        }
    }

    /**
     * 这艘船还在 VS2 的船列表里吗。
     *
     * <p>{@code /vs delete} 之类的操作会把船从船世界里删掉，但我们手里的
     * {@link ServerShip} 句柄不会变 null——它会变成一个"僵尸"句柄，位置、速度、
     * 质量都还读得出来（永远是最后那一帧的值），于是 status 看起来一切正常、船却根本不动。</p>
     */
    private static boolean stillExists(ServerLevel level) {
        if (ship == null) {
            return false;
        }
        if (level == null) {
            return true;
        }
        try {
            org.valkyrienskies.core.internal.world.VsiServerShipWorld world =
                    org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(level);
            return world != null && world.getAllShips().getById(ship.getId()) != null;
        } catch (Throwable ignored) {
            // 查不了就当它还在，宁可让用户看到旧状态，也别把还活着的船清掉
            return true;
        }
    }

    private static void dropStale() {
        // 船没了，交互锚点也得撤掉。
        com.opamb.barotrauma.ship.water.ShipVolumeGuard.deactivate();
        VsChunkTickets.clear(lastLevel);
        removeProxy(lastLevel);
        ship = null;
        thruster = null;
        state = null;
        corePos = null;
        snapshot = Map.of();
        initialShipBlocks = 0;
        interiorCells = java.util.List.of();
        knownInteriorCells = java.util.Set.of();
        guardCells = java.util.List.of();
        interiorOffsetX = Integer.MIN_VALUE;
        interiorOffsetY = Integer.MIN_VALUE;
        interiorOffsetZ = Integer.MIN_VALUE;
        assembledPos = Vec3.ZERO;
        bowLatched = false;
        bowLocalX = 1.0D;
        bowLocalZ = 0.0D;
        helmWatchdogTicks = 0;
        unstaticTicks = 0;
        pendingShip = null;
        pendingThruster = null;
        pendingLevel = null;
        yardOffsetX = Integer.MIN_VALUE;
        yardOffsetY = Integer.MIN_VALUE;
        yardOffsetZ = Integer.MIN_VALUE;
        nextYardScanGameTime = Long.MIN_VALUE;
        hullCheckStartGameTime = Long.MIN_VALUE;
        clearFixtureCut();
        com.opamb.barotrauma.network.PowerPanelState.clearPhysical();
        VsState.deactivate();
    }

    /**
     * 服务器生命周期切换时清掉上一世界的物理船静态状态。
     *
     * <p>单机退出世界再新建世界时 JVM 不重启，但 {@link VsPilot} / {@link VsState}
     * 是进程级静态单例；不清理的话，新世界会误以为上一世界的物理船还在，导致
     * {@code StarterSub.ensure()} 跳过初始潜艇，并继续收到旧船 id 的水面包。</p>
     *
     * <p>这里只清内存，不碰任何世界方块：旧世界已经保存并卸载，不能再用它写方块。</p>
     */
    public static void resetForServerLifecycle() {
        boolean hadShip = ship != null || pendingShip != null;
        com.opamb.barotrauma.ship.water.ShipVolumeGuard.deactivate();
        ship = null;
        thruster = null;
        state = null;
        corePos = null;
        snapshot = Map.of();
        initialShipBlocks = 0;
        interiorCells = java.util.List.of();
        knownInteriorCells = java.util.Set.of();
        guardCells = java.util.List.of();
        waterSyncTimer = 0;
        interiorOffsetX = Integer.MIN_VALUE;
        interiorOffsetY = Integer.MIN_VALUE;
        interiorOffsetZ = Integer.MIN_VALUE;
        sonarAnchor = null;
        proxyPos = null;
        pumpAnchors = java.util.List.of();
        pumpProxyByAnchor = java.util.Map.of();
        pendingBlockEntityTags = java.util.Map.of();
        reactorAnchor = null;
        reactorProxyPos = null;
        engineAnchor = null;
        engineProxyPos = null;
        VsChunkTickets.clear(lastLevel);
        lastLevel = null;
        yardOffsetX = Integer.MIN_VALUE;
        yardOffsetY = Integer.MIN_VALUE;
        yardOffsetZ = Integer.MIN_VALUE;
        nextYardScanGameTime = Long.MIN_VALUE;
        hullCheckStartGameTime = Long.MIN_VALUE;
        pendingShip = null;
        pendingThruster = null;
        pendingLevel = null;
        // 船头锁和看门狗计数是跨船状态：同一进程切换存档后如果不清，新船会
        // 沿用上一艘船的船头方向（bowLatched=true 时 currentBow 不再重新锁定）。
        bowLatched = false;
        bowLocalX = 1.0D;
        bowLocalZ = 0.0D;
        helmWatchdogTicks = 0;
        unstaticTicks = 0;
        simTickCounter = 0;
        waterStateLogCounter = 0;
        lastRecordFlushGameTime = Long.MIN_VALUE;
        pendingGeometryLevel = null;
        pendingGeometryDueTick = Long.MIN_VALUE;
        lastWaterPacket = ShipWaterStatePacket.empty();
        clearFixtureCut();
        com.opamb.barotrauma.network.PowerPanelState.clearPhysical();
        VsState.deactivate();
        if (hadShip) {
            LOGGER.info("[vs] 服务器生命周期切换：已清理上一世界的物理船静态状态");
        }
    }

    /**
     * 这个方块位置属于当前物理船吗。
     *
     * <p>方块收进刚体后会被搬到船坞区块，但仍然在 tick；{@code ShipCoreBlockEntity}
     * 用它来决定"方块级模拟要不要停手"。</p>
     */
    private static boolean isPhysicalHere(ServerLevel level, BlockPos pos) {
        if (ship == null || level == null || pos == null) {
            return false;
        }
        if (lastLevel != null && lastLevel != level) {
            return false;
        }
        ServerShip managing = VsBridge.managingShip(level, pos);
        return managing != null && managing.getId() == ship.getId();
    }


    /** 这个位置是不是在某个物理船舱的盒子里（不管进没进水）。 */
    public static boolean isInsideCompartment(Level level, Vec3 pos) {
        if (ship == null || state == null || level == null || pos == null) {
            return false;
        }
        float[] boxes = lastWaterPacket.boxes();
        if (boxes.length < 7) {
            return false;
        }
        // 用实时逆变换：偏航后只减整数平移量会把舱内位置算到盒子外。
        double[] local = toAssemblyLocal(level, pos);
        if (local == null) {
            return false;
        }
        double lx = local[0];
        double ly = local[1];
        double lz = local[2];
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (lx >= boxes[i] && lx <= boxes[i + 3] + 1.0F
                    && ly >= boxes[i + 1] && ly <= boxes[i + 4] + 1.0F
                    && lz >= boxes[i + 2] && lz <= boxes[i + 5] + 1.0F) {
                return true;
            }
        }
        return false;
    }

    /**
     * 世界坐标 → 装配坐标（小数，含实时旋转），水盒判定用。
     *
     * @return 无法换算时返回 {@code null}
     */
    @Nullable
    private static double[] toAssemblyLocal(Level level, Vec3 pos) {
        if (!(level instanceof ServerLevel server) || pos == null) {
            return null;
        }
        org.valkyrienskies.core.api.ships.ServerShip live = view(server);
        org.valkyrienskies.core.api.ships.ServerShip target = live != null ? live : ship;
        if (target == null) {
            return null;
        }
        var transform = target.getTransform();
        var origin = new org.joml.Vector3d(
                transform.getPositionInShip().x() - assembledPos.x,
                transform.getPositionInShip().y() - assembledPos.y,
                transform.getPositionInShip().z() - assembledPos.z);
        try {
            org.joml.Vector3d shipSpace = transform.getShipToWorld()
                    .invert(new org.joml.Matrix4d())
                    .transformPosition(new org.joml.Vector3d(pos.x, pos.y, pos.z),
                            new org.joml.Vector3d());
            return new double[]{
                    shipSpace.x - origin.x,
                    shipSpace.y - origin.y,
                    shipSpace.z - origin.z};
        } catch (Throwable error) {
            return null;
        }
    }

    /**
     * 物理船上新放的方块写进还原快照（装配坐标）。
     *
     * <p>靠港时 {@code restoreBlocks} 只遍历快照：不记录的话，航行中放的接线盒/
     * 舱门会留在船坞区块里，不会回到站侧船上，绑定数据也会丢。</p>
     */
    public static void recordPlacedBlock(ServerLevel level, BlockPos pos) {
        if (ship == null || state == null || lastLevel != level || pos == null
                || yardOffsetX == Integer.MIN_VALUE || !isYardBlock(pos)) {
            return;
        }
        BlockPos cell = toAssembly(pos);
        if (cell == null) {
            return;
        }
        if (!(snapshot instanceof java.util.HashMap)) {
            // 快照可能还是 Map.of() 或不可变副本：换成可变表再写。
            snapshot = new java.util.HashMap<>(snapshot);
        }
        snapshot.put(cell.immutable(), level.getBlockState(pos));
    }

    /** 这个世界坐标是否真的在这艘船的舱内（装配坐标落进舱室/连海空腔格）。
     *
     * <p>靠泊吸附会整体平移船，只有真在舱里的玩家/货物才跟着搬：站在哨站平台上、
     * 或者船外漂浮的箱子不能一起瞬移。</p>
     */
    private static boolean isInsideInterior(Level level, Vec3 pos) {
        if (ship == null || state == null || pos == null
                || !(level instanceof ServerLevel)) {
            return false;
        }
        BlockPos cell = toAssemblyCell(level, pos);
        if (cell == null) {
            return false;
        }
        // 用 interiorCells（船壳以内）而不是 pocket.cells()：圆角船体包围盒里的
        // 船外空气也属于连海空腔，但不该把站台上的玩家算进乘客。
        for (var compartment : state.compartments()) {
            if (compartment.interiorCells().contains(cell)) {
                return true;
            }
        }
        for (var pocket : state.geometry().openPockets()) {
            if (pocket.interiorCells().contains(cell)) {
                return true;
            }
        }
        // 破洞后重扫可能把整间房的 interior 清空：用过切破洞前确认过的舱内格兜底，
        // 否则靠泊吸附会把仍站在房间里的玩家/任务货留在大海里。
        return knownInteriorCells.contains(cell);
    }

    /**
     * 世界坐标 → 装配坐标格子（实时逆变换，不受传送/停靠平移影响）。
     *
     * @return 无法换算时返回 {@code null}
     */
    @Nullable
    private static BlockPos toAssemblyCell(Level level, Vec3 pos) {
        if (pos == null || !(level instanceof ServerLevel server)) {
            return null;
        }
        org.valkyrienskies.core.api.ships.ServerShip live = view(server);
        org.valkyrienskies.core.api.ships.ServerShip target = live != null ? live : ship;
        if (target == null) {
            return null;
        }
        var transform = target.getTransform();
        var origin = new org.joml.Vector3d(
                transform.getPositionInShip().x() - assembledPos.x,
                transform.getPositionInShip().y() - assembledPos.y,
                transform.getPositionInShip().z() - assembledPos.z);
        org.joml.Vector3d shipSpace;
        try {
            shipSpace = transform.getShipToWorld().invert(new org.joml.Matrix4d())
                    .transformPosition(new org.joml.Vector3d(pos.x, pos.y, pos.z),
                            new org.joml.Vector3d());
        } catch (Throwable error) {
            return null;
        }
        return BlockPos.containing(shipSpace.x - origin.x,
                shipSpace.y - origin.y, shipSpace.z - origin.z);
    }

    /** 把当前几何里确认的舱内格并入"已知舱内"集合（只增不减）。 */
    private static void rememberInterior(ShipGeometry.Result geometry) {
        if (geometry == null) {
            return;
        }
        java.util.Set<BlockPos> merged = new java.util.HashSet<>(knownInteriorCells);
        for (ShipGeometry.Compartment compartment : geometry.compartments()) {
            merged.addAll(compartment.interiorCells());
        }
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            merged.addAll(pocket.interiorCells());
        }
        knownInteriorCells = java.util.Set.copyOf(merged);
    }

    /**
     * 物理船舱内干湿询问（issue #31）：用实时 transform 把玩家世界坐标换算回
     * 装配坐标，再看所在舱室 bbox 的水面。
     *
     * <p>旧实现用 {@code VsState.offset} 平移——船被传送到任务海域实例锚点、
     * 或与哨站重叠后，这个平移不再等于“装配坐标↔世界坐标”，干舱会被判成浸没，
     * 疾跑游泳就误触发。这里直接取 {@code shipToWorld} 的逆变换，不受传送影响。</p>
     *
     * @return {@code null} = 不在任何舱室 bbox 内（调用方继续走原判定链）。
     */
    /**
     * 世界坐标 → 船的装配坐标（和水面盒同一套坐标）。
     *
     * <p>物理船用实时逆变换，船偏航后不会把舱内算到盒子外；返回 null 表示
     * 当前没有可用的物理船/水面数据。</p>
     */
    private static double[] toShipLocal(Level level, Vec3 pos) {
        if (ship == null || state == null
                || !(level instanceof ServerLevel server) || pos == null) {
            return null;
        }
        org.valkyrienskies.core.api.ships.ServerShip live = view(server);
        org.valkyrienskies.core.api.ships.ServerShip target = live != null ? live : ship;
        if (target == null) {
            return null;
        }
        var transform = target.getTransform();
        var origin = new org.joml.Vector3d(
                transform.getPositionInShip().x() - assembledPos.x,
                transform.getPositionInShip().y() - assembledPos.y,
                transform.getPositionInShip().z() - assembledPos.z);
        org.joml.Matrix4dc shipToWorld = transform.getShipToWorld();
        org.joml.Vector3d shipSpace;
        try {
            shipSpace = shipToWorld.invert(new org.joml.Matrix4d())
                    .transformPosition(new org.joml.Vector3d(pos.x, pos.y, pos.z),
                            new org.joml.Vector3d());
        } catch (Throwable error) {
            return null;
        }
        return new double[]{shipSpace.x - origin.x, shipSpace.y - origin.y, shipSpace.z - origin.z};
    }

    /**
     * 脚所在舱室的头顶净高是否放得下站立碰撞箱。
     *
     * <p>水面盒的 maxY+1 是这一列空气段的天花板；1.8 格放不下时必须保持游泳
     * 碰撞箱，否则原版会把站立碰撞箱撑进天花板、把人挤到上层。</p>
     */
    public static Boolean hasStandingRoom(Level level, Vec3 pos, double height) {
        double[] local = toShipLocal(level, pos);
        if (local == null) {
            return null;
        }
        return com.opamb.barotrauma.ship.ShipWaterBoxes.hasStandingRoom(
                lastWaterPacket.boxes(), local[0], local[1], local[2], height);
    }

    /**
     * 脚是否贴着所在舱室的底面/平台顶。
     *
     * <p>水里只有落到舱底才允许站起来；浮在水中间/贴墙/贴天花板都保持游泳
     * 碰撞箱，避免姿态每 tick 来回切（用户实测贴墙卡顿）。</p>
     */
    public static Boolean nearFloor(Level level, Vec3 pos, double maxAbove) {
        double[] local = toShipLocal(level, pos);
        if (local == null) {
            return null;
        }
        double distance = com.opamb.barotrauma.ship.ShipWaterBoxes.floorDistance(
                lastWaterPacket.boxes(), local[0], local[1], local[2]);
        return Double.isNaN(distance) ? null : distance <= maxAbove;
    }

    public static Boolean isPlayerInDryCompartment(Level level, Vec3 pos) {
        double[] local = toShipLocal(level, pos);
        if (local == null) {
            return null;
        }
        double lx = local[0];
        double ly = local[1];
        double lz = local[2];
        float[] boxes = lastWaterPacket.boxes();
        if (boxes.length < 7) {
            return null;
        }
        boolean inAny = false;
        boolean wet = false;
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            float minX = boxes[i];
            float minY = boxes[i + 1];
            float minZ = boxes[i + 2];
            float maxX = boxes[i + 3] + 1.0F;
            float maxY = boxes[i + 4] + 1.0F;
            float maxZ = boxes[i + 5] + 1.0F;
            float surface = boxes[i + 6];
            if (lx < minX || lx > maxX || lz < minZ || lz > maxZ) {
                continue;
            }
            boolean dryBox = surface <= minY + 0.01F;
            if (dryBox) {
                // 干舱：给跳跃顶点留 1 格余量
                if (ly >= minY && ly <= maxY + 1.0F) {
                    inAny = true;
                }
            } else {
                // 湿舱：水面以下都算湿（水面可能高于棱柱顶格，CodeRabbit 审查意见）；
                // 水面之上、棱柱顶 +1 余量内仍是舱内干燥
                if (ly <= surface + 0.01F) {
                    if (ly >= minY) {
                        inAny = true;
                        wet = true;
                    }
                } else if (ly >= minY && ly <= maxY + 1.0F) {
                    inAny = true;
                }
            }
        }
        if (!inAny) {
            // 几何确认在船体舱内、但水盒没覆盖到（新房间/破口后场缺格）：
            // 按干舱兜底。否则客户端和服务端都会回落到“舱外海水”，
            // 出现货舱没水却能游泳、还套水下滤镜（用户实测）。
            return isInsideInterior(level, pos) ? Boolean.TRUE : null;
        }
        return !wet;
    }

    /**
     * 玩家/实体是否在“未进水的船舱”里。
     *
     * <p>服务端用最近一次同步的水面包判断：把世界坐标换算回装配坐标，落在某个舱室
     * bbox 内且高于该舱室水面，就是干燥区。这样破口舱室进水后，水面盖过眼睛就会
     * 自动切回水下状态。</p>
     */
    public static boolean isInsideDryZone(Level level, Vec3 pos) {
        if (ship == null || state == null || level == null || pos == null) {
            return false;
        }
        // 和 isPlayerInDryCompartment 用同一套实时逆变换 + 盒内干湿判定；
        // 旧实现只减整数位移，船偏航后舱内会被误判成水外。
        return Boolean.TRUE.equals(isPlayerInDryCompartment(level, pos));
    }

    /** 把船状态推给物理推进器：油门 / 功率 / 压载 / 进水比例。 */
    private static void applyControl(ShipState control) {
        VsThruster target = thruster;
        // 只认"这艘物理船"的状态对象：同世界若还有别的方块船，别把它的油门推给这艘船
        if (target == null || control == null || control != state) {
            return;
        }
        target.setHelm(control.helmForward(), control.helmYaw(), control.throttleY());
        // 自动驾驶按航向误差给偏航速率，手动 A/D 仍是满舵。
        target.setHelmYawScale(control.helmYawScale());
        target.setPower(control.power());
        // 舵效供电：必须看导航终端自己的电压。全船电压可能还有 1.0，但终端绑定的
        // 接线盒坏了以后 consumerVoltage(终端)=0，这时不能再执行残留舵令。
        boolean helmPowered = false;
        if (lastLevel != null) {
            com.opamb.barotrauma.blockentity.ShipCoreBlockEntity terminal =
                    sonarCoreBlockEntity(lastLevel);
            if (terminal != null) {
                BlockPos terminalCell = toAssembly(terminal.getBlockPos());
                if (terminalCell != null) {
                    helmPowered = control.simulation().consumerVoltage(terminalCell)
                            >= com.opamb.barotrauma.blockentity.ShipCoreBlockEntity
                            .TERMINAL_MIN_VOLTAGE;
                }
            }
        }
        target.setHelmPowered(helmPowered);
        target.setEngineCount(control.simulation().engineCount());
        // 原作 Engine.Update 的逐引擎力：maxforce × 推力% × 电压^(2/3) × 耐久插值。
        target.setEngineForceRaw(control.simulation().engineForceRaw());
        // 技能系统还没落地：先用船长起始驾驶技能的中值 50。落地后改读玩家技能。
        target.setHelmSkill(50.0D);
        target.setEngineMultiplier(control.engineMultiplier());
        // 船头方向：装配时锁定一次（见 currentBow），之后不随引擎增减而翻转。
        double[] forward = currentBow(control);
        target.setForward(forward[0], forward[1]);
        // 原版浮力只认全船水量占比（含压载水），不再按“压载百分比”另算一份力。
        target.setWaterRatio(control.buoyancyWaterRatio());
        // 临时诊断：定位“转向转不动”时打印舵令/供电/船头/实际力。
        if (control.helmYaw() != 0 && lastLevel != null
                && lastLevel.getGameTime() % 20L == 0L) {
            LOGGER.info("[vs] helm f={} y={} v={} engines={} volt={} raw={} bow=({},{}) force=({},{})",
                    control.helmForward(), control.helmYaw(), control.throttleY(),
                    control.simulation().engineCount(), control.simulation().voltage(),
                    control.simulation().engineForceRaw(),
                    target.forwardLocalX(), target.forwardLocalZ(),
                    target.lastForceX(), target.lastForceZ());
        }
    }

    /**
     * 无人掌舵看门狗：自动驾驶关闭时，连续 2 秒没有玩家开着这艘船的声纳菜单，
     * 就把舵令清零。
     *
     * <p>{@code SonarScreen.onClose} 正常关屏会发 STOP，但掉线、死亡、被传送、
     * 客户端崩溃都不会走到那里；服务端会保留最后一条舵令，船自己一直开下去。</p>
     */
    private static void tickHelmWatchdog(ServerLevel level, ShipState control) {
        if (level == null || control == null || control.autopilotHold()
                || !control.throttled()) {
            helmWatchdogTicks = 0;
            return;
        }
        for (ServerPlayer player : level.players()) {
            // 只认“本船导航终端的声纳菜单”。只看 SonarMenu.stillValid（距离）
            // 的话，另一艘方块船的玩家在 48 格内开着声纳也能把看门狗喂住，
            // 驾驶员掉线后舵令永远不会清零。
            com.opamb.barotrauma.blockentity.ShipCoreBlockEntity terminal =
                    sonarCoreBlockEntity(level);
            if (terminal != null
                    && player.containerMenu instanceof com.opamb.barotrauma.menu.SonarMenu menu
                    && menu.sonarPos().equals(terminal.getBlockPos())
                    && menu.stillValid(player)) {
                helmWatchdogTicks = 0;
                return;
            }
        }
        if (++helmWatchdogTicks > 40) {
            helmWatchdogTicks = 0;
            control.setHelm(0, 0, 0);
            applyControl(control);
            LOGGER.info("[vs] 无人掌舵 2 秒，舵令归零");
        }
    }

    /**
     * 船头在船体局部 XZ 平面的方向。
     *
     * <p>原作每条船都是“引擎在船尾、船头朝 +X”；这个模组的船是玩家手搭的，
     * 长轴可能是 X 也可能是 Z，所以从**引擎平均位置 → 船核心**反推：核心指向
     * 引擎的反方向就是船头。没有引擎时退回局部 +X（模板默认朝向）。</p>
     *
     * @return {x, z}，已归一化
     */
    private static double[] forwardLocal(@Nullable ShipState control) {
        if (control != null) {
            double[] engines = control.simulation().engineCentroid();
            if (engines != null) {
                // 用整船包围盒中心，不用船核心：用户这艘船的导航台在左舷（z 偏一侧），
                // 拿核心当参考会让船头歪十几度。包围盒中心才是船体几何中心。
                ShipGeometry.Bounds bounds = control.geometry().bounds();
                double centerX = (bounds.minX() + bounds.maxX() + 1) / 2.0D;
                double centerZ = (bounds.minZ() + bounds.maxZ() + 1) / 2.0D;
                if (bounds.sizeX() >= bounds.sizeZ()) {
                    double sign = Math.signum(centerX - engines[0]);
                    if (sign != 0.0D) {
                        return new double[]{sign, 0.0D};
                    }
                } else {
                    double sign = Math.signum(centerZ - engines[2]);
                    if (sign != 0.0D) {
                        return new double[]{0.0D, sign};
                    }
                }
            }
            // 备选：核心 → 引擎的反方向（引擎装得很靠中间时用）。
            BlockPos core = corePos != null ? corePos : VsState.corePos();
            if (engines != null && core != null) {
                double fx = core.getX() + 0.5D - engines[0];
                double fz = core.getZ() + 0.5D - engines[2];
                double length = Math.sqrt(fx * fx + fz * fz);
                if (length > 1.0D) {
                    return new double[]{fx / length, fz / length};
                }
            }
        }
        return new double[]{1.0D, 0.0D};
    }

    /** 装配时锁定的船头方向（船体局部 XZ，单位向量）。 */
    private static double bowLocalX = 1.0D;
    private static double bowLocalZ = 0.0D;
    private static boolean bowLatched;

    /**
     * 当前使用的船头方向：第一次能读到引擎位置时锁定，之后不再重算。
     *
     * <p>如果每 tick 都用引擎质心重算，打掉一台引擎、或在船头临时放一台，
     * 质心跨过包围盒中心就会让推力瞬间反向。装配时锁一次，船的朝向就固定了；
     * 之后引擎全没了也还是原方向。</p>
     */
    private static double[] currentBow(@Nullable ShipState control) {
        if (!bowLatched && control != null
                && control.simulation().engineCentroid() != null) {
            double[] forward = forwardLocal(control);
            bowLocalX = forward[0];
            bowLocalZ = forward[1];
            bowLatched = true;
        }
        if (!bowLatched) {
            // 引擎还没上报（装配第一 tick）：先用实时推断，但不锁定。
            return forwardLocal(control);
        }
        return new double[]{bowLocalX, bowLocalZ};
    }

    /**
     * 船头在世界 XZ 平面的方向；没有物理船/查询失败返回 null。
     *
     * <p>声纳箭头、定向脉冲和自动驾驶都用它，不再拿 {@code throttleX/Z}
     * 当世界方向（那套是方块船语义）。</p>
     */
    @Nullable
    public static Vec3 forwardWorld() {
        if (ship == null) {
            return null;
        }
        try {
            ServerShip live = lastLevel == null ? ship : view(lastLevel);
            if (live == null) {
                return null;
            }
            double[] local = currentBow(state);
            org.joml.Vector3d value = live.getTransform().getShipToWorldRotation()
                    .transform(new org.joml.Vector3d(local[0], 0.0D, local[1]),
                            new org.joml.Vector3d());
            value.y = 0.0D;
            if (value.lengthSquared() < 1.0E-9D) {
                return null;
            }
            value.normalize();
            return new Vec3(value.x, 0.0D, value.z);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ tick

    /** 每个 server tick 调一次。任何异常都不许把服务器带崩。 */
    public static void tick() {
        if (unstaticTicks > 0 && ship != null && lastLevel != null) {
            unstaticTicks--;
            try {
                LoadedServerShip loaded = VsBridge.loaded(lastLevel, ship);
                if (loaded != null) {
                    // 前 10 tick 强制静态、后 10 tick 放回动态：
                    // 只反复 setStatic(false) 试过没用（字段变了但刚体类型没重建）。
                    // 这里做一个 静态→动态 的翻转，逼 VS2 重新生成刚体体类型。
                    loaded.setStatic(unstaticTicks > 10);
                }
            } catch (Throwable ignored) {
                // 拿不到就下一 tick 再试
            }
        }
        if (pendingShip != null && pendingLevel != null && pendingThruster != null) {
            try {
                VsThruster attached = VsBridge.attach(pendingLevel, pendingShip, pendingThruster);
                if (attached != null) {
                    thruster = attached;
                    ship = pendingShip;
                    activateState(pendingShip, pendingLevel);
                    pendingShip = null;
                    pendingThruster = null;
                    pendingLevel = null;
                }
            } catch (Throwable error) {
                org.slf4j.LoggerFactory.getLogger("barotrauma").warn("物理船推进器挂载失败", error);
                pendingShip = null;
                pendingThruster = null;
                pendingLevel = null;
            }
        }
        if (ship == null) {
            return;
        }
        // 先把刚体的真实位置 / 速度读出来：船内破口进水要按深度算，
        // 后面的位移/自动驾驶也要用同一份快照。
        ServerLevel level = lastLevel;
        Vec3 now = null;
        Vec3 vel = null;
        if (level != null) {
            try {
                ServerShip live = view(level);
                now = VsBridge.position(live);
                vel = VsBridge.velocity(live);
            } catch (Throwable ignored) {
                // 读不到就下一 tick 再试，不能让物理 tick 抛异常
            }
        }
        // 船世界里的方块仍然在 tick，但方块级推进已经停了：船内模拟（反应堆/氧气/水泵）
        // 得由这里接管，否则面板上的温度、氧气会一直冻在装配那一刻。
        // 破口进水同样只能在这里推进——方块船的 serverTick 在物理船上会提前 return，
        // 只在那边调 tickSeaFlood 的话，物理船砸出洞后水永远不涨。
        if (++simTickCounter % 2L == 0L && state != null) {
            try {
                // 导航台/声纳终端也吃电（原作 Steering 10 + Sonar 100 = 110 kW），
                // 物理船上方块级 tick 停了，由这里补报终端负载。
                if (level != null) {
                    com.opamb.barotrauma.blockentity.ShipCoreBlockEntity terminal =
                            sonarCoreBlockEntity(level);
                    if (terminal != null) {
                        // 世界重开 + adopt() 之后 BE 的 state 可能还是 null，
                        // reportTerminalPower 会直接返回，110 kW 终端负载一直不进模拟。
                        if (terminal.state() == null) {
                            terminal.setState(state);
                        }
                        terminal.reportTerminalPower(toAssembly(terminal.getBlockPos()));
                    }
                }
                state.simulation().tick(0.1D);
                java.util.List<net.minecraft.core.BlockPos> breachCells = new java.util.ArrayList<>();
                for (com.opamb.barotrauma.ship.ShipState.Breach breach : state.breaches()) {
                    breachCells.add(breach.pos());
                }
                if (now != null) {
                    state.simulation().tickSeaFlood(0.1D,
                            Math.max(0.0D, -now.y), breachCells);
                }
                // 还没破块、但伤害 ≥10%/70% 的船壳渗水
                state.tickLeaks(0.1D);
                // 逐格场：重力下落 + 开门/舱口压力流
                state.simulation().tickWaterFlow(0.1D);
            } catch (Throwable ignored) {
                // 模拟出错不影响物理
            }
        }
        // 油门 / 功率 / 压载每 tick 同步一次：声纳面板改了状态，物理那边立刻生效
        applyControl(state);
        // 无人掌舵看门狗：正常关屏会发 STOP，但掉线/崩溃不会。
        tickHelmWatchdog(level, state);
        // 把刚体的真实位置 / 速度抄回船状态：拆船时按位移铺方块，面板上的航速也得是真的
        if (level != null && now != null && vel != null) {
            // 迟滞：船稳定停在小数 .5 附近时，Math.round 会在 N / N+1 之间每 tick 来回跳，
            // 排水体就跟着每 tick 挪一格、旧格回填水——看起来就是"船体来回乱闪"。
            // 只有真的越过 0.62 格才换格，静止时偏移锁死。
            VsState.setOffset(
                    stableOffset(now.x - assembledPos.x, VsState.offsetX()),
                    stableOffset(now.y - assembledPos.y, VsState.offsetY()),
                    stableOffset(now.z - assembledPos.z, VsState.offsetZ()));
            // 空气海域没有真实水要拦截；保留清水调用只为兼容旧存档里可能残留的水。
            clearShipVolumeWater(level, VsState.offsetX(), VsState.offsetY(), VsState.offsetZ(), now);
            moveProxy(level, VsState.offsetX(), VsState.offsetY(), VsState.offsetZ());
            // 门开合登记过的几何重扫：合并到主 tick，最多 5 tick 扫一次。
            // 舱内自动门会随着玩家游动反复开合，每次全量重扫 + 重建流体场太重，
            // 而且日志会被"0 格被破坏"刷屏（用户日志 45 秒 21 次）。
            if (pendingGeometryDueTick != Long.MIN_VALUE
                    && level.getGameTime() >= pendingGeometryDueTick) {
                ServerLevel refreshLevel =
                        pendingGeometryLevel == null ? level : pendingGeometryLevel;
                pendingGeometryDueTick = Long.MIN_VALUE;
                pendingGeometryLevel = null;
                rebuildGeometry(refreshLevel, List.of());
            }
            // 船体破损对账：VS2 自己不支持船上破坏，我们靠船坞方块状态兜底
            // （爆炸、命令、其它 mod 把方块弄没了也能发现）。
            if (level.getGameTime() % 20L == 0L) {
                if (yardOffsetX == Integer.MIN_VALUE && level.getGameTime() >= hullCheckStartGameTime) {
                    discoverYardOffset(level);
                }
                refreshHull(level);
                // 船移动/传送后 activeChunks 会变：把常驻票据搬到新占用的区块。
                VsChunkTickets.sync(level, view(level));
            }
            // 和模拟步进（每 2 tick，10 Hz）对齐：水面目标每 100 ms 更新一次，
            // 客户端再按帧插值。原来是 10 tick（2 Hz），水面会一顿一顿。
            if (state != null && ++waterSyncTimer % 2 == 0) {
                sendWaterState(state);
            }
            // 自动驾驶：物理船没有方块形态的 tickMovement 了，这里按目标点算推力再推给刚体。
            // 当前坐标必须用**船核心**的世界坐标，和声纳扫描中心/目标偏移同一个基准；
            // 用刚体位置（质心）会让船停在离标记几格远的地方。
            if (state != null && state.autopilotHold()) {
                Vec3 ap = now;
                BlockPos assembledCore = VsState.corePos();
                if (assembledCore != null) {
                    ap = new Vec3(assembledCore.getX() + VsState.offsetX(),
                            assembledCore.getY() + VsState.offsetY(),
                            assembledCore.getZ() + VsState.offsetZ());
                }
                Vec3 forward = forwardWorld();
                state.autopilotStep(ap.x, ap.y, ap.z, vel.x, vel.y, vel.z,
                        forward == null ? 1.0D : forward.x,
                        forward == null ? 0.0D : forward.z);
                applyControl(state);
            }
            if (state != null) {
                state.physics().sync(vel.x, vel.y, vel.z);
            }
            // P3 环境威胁：超耐压深度持续压伤船壳（走 HullDamage → 漏水链路）
            if (state != null && now != null
                    && com.opamb.barotrauma.ship.ShipPressure.tick(level, state, -now.y)) {
                sendHullDamageState(state);
                syncAssembledRecord(level);
            }
            // P3 碰撞：高速时对刚体 AABB 内固体方块抽样，命中即扣耐久（温和值）
            if (state != null && vel != null && level.getGameTime() % 10L == 0L
                    && Math.abs(vel.y) + Math.hypot(vel.x, vel.z) > 4.0D) {
                collideDamage(level, now);
            }
            // 持久化：每 5 秒把快照/耐久的变化同步进认领记录（世界重开后靠它接船）。
            if (level.getGameTime() % 100L == 0L) {
                syncAssembledRecord(level);
            }
        }
    }

    /**
     * 整数偏移的迟滞：离当前格还不到 0.62 格时保持不变，避免在 .5 边界来回跳。
     *
     * <p>排水体/舱内排水都按整数偏移执行，偏移一抖就会每 tick 挪一格、旧格回填海水，
     * 视觉上就是"船体来回乱闪"。0.62 比 0.5 多出的余量能吃掉悬浮噪声。</p>
     */
    private static int stableOffset(double delta, int current) {
        int rounded = (int) Math.round(delta);
        if (rounded == current) {
            return current;
        }
        if (Math.abs(delta - current) < 0.62D) {
            return current;
        }
        return rounded;
    }

    /**
     * 船整格移动后，把新位置舱室里的水清成空气。
     *
     * <p>排水体只盖船壳，舱室是真正的空气；船挪到新位置时那片体积原本是海水，
     * 不清的话玩家一进船就是满舱水。只在整数偏移变化时扫一次（几百格）。</p>
     */
    private static void clearShipVolumeWater(ServerLevel level, int dx, int dy, int dz, Vec3 center) {
        if (dx == interiorOffsetX && dy == interiorOffsetY && dz == interiorOffsetZ) {
            return;
        }
        // 先回填船尾：旧拦截格 - 新拦截格 = 被船腾出来的那层海水
        if (interiorOffsetX != Integer.MIN_VALUE) {
            refillVacated(level, interiorOffsetX, interiorOffsetY, interiorOffsetZ, dx, dy, dz, center);
        }
        doClearShipVolumeWater(level, dx, dy, dz);
    }

    /**
     * 流体拦截格 = 当前船体方块（快照）+ 舱室格。
     *
     * <p>不能只用"舱室向外扩 1 圈"：6 邻接扩不到船壳的棱和角，海水会从那些格
     * 钻进来（无头自检实测漏 44 格）。直接用装配快照里每一个船体方块的世界格，
     * 正好覆盖整个船壳；破掉的格子会从快照里移除，海水就只出现在破口那一格。</p>
     */
    private static List<BlockPos> buildGuardCells(Collection<BlockPos> interior) {
        java.util.LinkedHashSet<BlockPos> out = new java.util.LinkedHashSet<>(snapshot.keySet());
        out.addAll(interior);
        return new ArrayList<>(out);
    }

    /**
     * 船整格移动后，把旧拦截格中"新拦截格不再覆盖"的那些格回填成海水。
     *
     * <p>不回填的话，船每走一格就在海里留下船形空气洞——用户看到的就是
     * "路过的地方全变成空气了"。只在船周围确实是水时回填：停在哨站/洞穴的
     * 干空气里时不能凭空造水。</p>
     */
    private static void refillVacated(ServerLevel level, int ox, int oy, int oz,
                                      int nx, int ny, int nz, Vec3 center) {
        if (guardCells.isEmpty() || !isUnderwater(level, center)) {
            return;
        }
        it.unimi.dsi.fastutil.longs.LongOpenHashSet covered =
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet(guardCells.size() * 2);
        for (BlockPos base : guardCells) {
            covered.add(BlockPos.asLong(base.getX() + nx, base.getY() + ny, base.getZ() + nz));
        }
        int changed = 0;
        for (BlockPos base : guardCells) {
            long oldKey = BlockPos.asLong(base.getX() + ox, base.getY() + oy, base.getZ() + oz);
            if (covered.contains(oldKey)) {
                continue;
            }
            BlockPos pos = new BlockPos(base.getX() + ox, base.getY() + oy, base.getZ() + oz);
            if (level.getBlockState(pos).isAir()) {
                // flag 2：不触发邻居更新，避免一片水互相排队 tick
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                changed++;
            }
        }
        if (changed > 0) {
            LOGGER.info("[vs] 船尾回填海水：{} 格（偏移 {},{},{} → {},{},{}）",
                    changed, ox, oy, oz, nx, ny, nz);
        }
    }

    /**
     * 空气海域不再通过世界里的水方块判断水下；船尾回填真实水也已停用。
     *
     * <p>保留这个方法名是为了让旧调用点安全地变成 no-op，避免在空气海域里凭空造水。</p>
     */
    private static boolean isUnderwater(ServerLevel level, Vec3 center) {
        return false;
    }

    private static void doClearShipVolumeWater(ServerLevel level, int dx, int dy, int dz) {
        interiorOffsetX = dx;
        interiorOffsetY = dy;
        interiorOffsetZ = dz;
        int changed = 0;
        // 舱室 + 船壳缓冲层都要清。船壳格在物理船上也是空气（方块在船坞），
        // 装配/换格瞬间灌进去的水不清掉，就会在舱底/船壳里留下一滩真实海水；
        // 舱内水位由模拟 + 客户端自绘，真实海水只允许留在船壳更外侧。
        java.util.List<BlockPos> cells = guardCells.isEmpty() ? interiorCells : guardCells;
        for (BlockPos cell : cells) {
            changed += clearWaterAt(level, cell.offset(dx, dy, dz));
        }
        if (changed > 0) {
            LOGGER.info("[vs] 舱内排水：清掉 {} 格（偏移 {},{},{}）", changed, dx, dy, dz);
        }
    }

    private static int clearWaterAt(ServerLevel level, BlockPos target) {
        if (level.getBlockState(target).getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
            level.setBlock(target, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            return 1;
        }
        return 0;
    }

    // ------------------------------------------------------------------ 船体破损

    /**
     * 核心方块实体在船坞里 tick 时报告自己的真实坐标，这里算出"原坐标 → 船坞坐标"的平移量。
     *
     * <p>VS2 装配后把整艘船平移到远处的船坞区块，船坞里的方块布局和原世界完全一样，
     * 只是整体平移。知道这个平移量，就能用装配快照里的原坐标直接读写船坞方块。</p>
     */
    /**
     * 从船坞区块中心扫出声纳方块，反推"原坐标 → 船坞坐标"的平移量。
     *
     * <p>不能只依赖核心方块实体 tick：船坞区块离玩家很远，服务端不一定给它安排
     * tick（无头自检里就没有）。但 VS2 装配一定把船放在 {@code chunkClaim} 的
     * 中心附近，所以围绕 claim 中心扫一个和船体包围盒同量级的盒子，找唯一的那台
     * 声纳（船脑），就能拿到偏移。</p>
     *
     * <p>先扫中心 ±32 格（覆盖常见潜艇），找不到再按整个建造高度扫一遍。</p>
     */
    private static boolean discoverYardOffset(ServerLevel level) {
        if (yardOffsetX != Integer.MIN_VALUE) {
            return true;
        }
        if (ship == null || corePos == null || snapshot.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        if (now < nextYardScanGameTime) {
            return false;
        }
        try {
            ServerShip live = view(level);
            if (live == null) {
                live = ship;
            }
            org.valkyrienskies.core.api.ships.properties.ChunkClaim claim = live.getChunkClaim();
            if (claim == null) {
                return false;
            }
            org.joml.Vector3i center = claim.getCenterBlockCoordinates(
                    org.valkyrienskies.mod.common.VSGameUtilsKt.getYRange(level),
                    new org.joml.Vector3i());
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : snapshot.keySet()) {
                minX = Math.min(minX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            int radiusXZ = Math.max(maxX - minX, maxZ - minZ) / 2 + 12;
            BlockPos found = findSonar(level, center, radiusXZ, 32);
            if (found == null) {
                found = findSonar(level, center, radiusXZ,
                        Math.max(1, (level.getMaxBuildHeight() - level.getMinBuildHeight()) / 2));
            }
            if (found == null) {
                nextYardScanGameTime = now + 200L;
                LOGGER.warn("[vs] 船坞偏移探测：claim 中心 {} 附近没找到声纳方块", center);
                return false;
            }
            yardOffsetX = found.getX() - corePos.getX();
            yardOffsetY = found.getY() - corePos.getY();
            yardOffsetZ = found.getZ() - corePos.getZ();
            LOGGER.info("[vs] 船坞偏移（扫 claim 中心）：({},{},{})｜声纳 {} → {}｜claim 中心 {}",
                    yardOffsetX, yardOffsetY, yardOffsetZ, corePos, found, center);
            applyPendingBlockEntityTags(level);
            return true;
        } catch (Throwable error) {
            LOGGER.warn("[vs] 船坞偏移探测失败：{}", error.toString());
            return false;
        }
    }

    @Nullable
    private static BlockPos findSonar(ServerLevel level, org.joml.Vector3i center,
                                      int radiusXZ, int radiusY) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.x - radiusXZ, center.y - radiusY, center.z - radiusXZ,
                center.x + radiusXZ, center.y + radiusY, center.z + radiusXZ)) {
            if (isConsoleMaster(level.getBlockState(pos))) {
                return pos.immutable();
            }
        }
        return null;
    }

    /** 声纳台/导航终端的主方块（导航终端只有下排中列才是核心+交互点）。 */
    private static boolean isConsoleMaster(BlockState state) {
        if (state.is(ModShipBlocks.SONAR.get())) {
            return true;
        }
        return state.is(ModShipBlocks.NAVIGATION_TERMINAL.get())
                && state.getValue(com.opamb.barotrauma.block.NavigationTerminalBlock.PART)
                == com.opamb.barotrauma.block.NavigationTerminalLayout.Part.LOWER_MIDDLE;
    }

    /** 声纳台/导航终端的任意一段：不参与船壳耐久、爆炸破壳和焊接。 */
    private static boolean isConsolePart(BlockState state) {
        return state.is(ModShipBlocks.SONAR.get())
                || state.is(ModShipBlocks.NAVIGATION_TERMINAL.get());
    }

    private static void observeCore(ServerLevel level, BlockPos shipyardPos) {
        BlockPos original = corePos;
        if (original == null) {
            return;
        }
        int dx = shipyardPos.getX() - original.getX();
        int dy = shipyardPos.getY() - original.getY();
        int dz = shipyardPos.getZ() - original.getZ();
        if (dx == yardOffsetX && dy == yardOffsetY && dz == yardOffsetZ) {
            return;
        }
        yardOffsetX = dx;
        yardOffsetY = dy;
        yardOffsetZ = dz;
        lastLevel = level;
        LOGGER.info("[vs] 船坞坐标对账：偏移 ({},{},{})｜原核心 {} → 船坞 {}",
                dx, dy, dz, original, shipyardPos);
        applyPendingBlockEntityTags(level);
    }

    /**
     * 每秒对账一次船坞方块和装配快照，发现船体被破坏就重新分舱。
     *
     * <p>物理船上砸方块由 {@link #tryBreak} 直接处理；这里兜的是爆炸、命令、
     * 其它 mod 等旁路修改。破口会让舱室从"密闭"变成"连海"，模拟里开始进水。</p>
     */
    /** 门开合后强制重扫船坞几何；方块类型没变，{@link #refreshHull} 不会触发。 */
    private static void refreshPhysicalGeometry(ServerLevel level) {
        if (level == null) {
            return;
        }
        // 只登记，主 tick 里合并成一次：舱内自动门会跟玩家反复开合，
        // 每次全量扫 6k+ 格并重建流体场太重，还会让日志刷屏。
        pendingGeometryLevel = level;
        if (pendingGeometryDueTick == Long.MIN_VALUE) {
            pendingGeometryDueTick = level.getGameTime() + 5L;
        }
    }

    /** 待处理的几何重扫（门开合触发）。 */
    private static ServerLevel pendingGeometryLevel;
    private static long pendingGeometryDueTick = Long.MIN_VALUE;

    /**
     * 船坞坐标 → 装配坐标。物理船的方块实体（排水泵等）报上来的是船坞里的格子，
     * 而船状态里的舱室、破口全是装配坐标。
     */
    public static BlockPos toAssembly(BlockPos yardPos) {
        if (yardPos == null || yardOffsetX == Integer.MIN_VALUE) {
            return yardPos;
        }
        return yardPos.offset(-yardOffsetX, -yardOffsetY, -yardOffsetZ);
    }

    /**
     * 装配坐标 → 船坞坐标。物理船上的方块实体在船坞里，读写它们之前要用这个换算。
     * 船坞偏移还没对账时返回 {@code null}（调用方应放弃本次读写）。
     */
    @Nullable
    public static BlockPos toYard(BlockPos assemblyPos) {
        if (assemblyPos == null || yardOffsetX == Integer.MIN_VALUE) {
            return null;
        }
        return assemblyPos.offset(yardOffsetX, yardOffsetY, yardOffsetZ);
    }

    /** 这个方块坐标是不是当前物理船在船坞里的方块（落在船坞包围盒内）。 */
    public static boolean isYardBlock(BlockPos pos) {
        if (pos == null || state == null || yardOffsetX == Integer.MIN_VALUE) {
            return false;
        }
        var bounds = state.geometry().bounds();
        return pos.getX() >= bounds.minX() + yardOffsetX
                && pos.getX() <= bounds.maxX() + yardOffsetX
                && pos.getY() >= bounds.minY() + yardOffsetY
                && pos.getY() <= bounds.maxY() + yardOffsetY
                && pos.getZ() >= bounds.minZ() + yardOffsetZ
                && pos.getZ() <= bounds.maxZ() + yardOffsetZ;
    }

    /**
     * 当前物理船的装配快照（装配坐标 → 方块状态）。
     *
     * <p>只读用途：任务装货时按快照里的方块位置找货架，再用 {@link #toYard}
     * 换到船坞坐标读方块实体。不要改这个 Map——需要登记新方块走
     * {@link #recordPlacedBlock}。</p>
     */
    public static Map<BlockPos, BlockState> assembledBlocks() {
        return snapshot;
    }

    /**
     * 装配快照里所有泵的锚点：压载泵只取六格机器的主方块，普通泵/小型泵各占一格。
     *
     * <p>物理船收进刚体后方块右键不到，每台泵都要有自己的交互代理；
     * 只用一个锚点的话，右键小泵打开的会是压载泵菜单（玩家实测"小水泵没法用"）。</p>
     */
    private static java.util.List<BlockPos> findPumpAnchors(Map<BlockPos, BlockState> blocks) {
        java.util.List<BlockPos> anchors = new java.util.ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (entry.getValue().is(ModBlocks.BALLAST_PUMP.get())
                    && entry.getValue().getValue(com.opamb.barotrauma.block.BallastPumpBlock.PART)
                    == com.opamb.barotrauma.block.BallastPumpBlock.Part.LOWER_MIDDLE) {
                anchors.add(entry.getKey().immutable());
            }
        }
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (entry.getValue().is(ModBlocks.DRAIN_PUMP.get())
                    || entry.getValue().is(ModBlocks.SMALL_PUMP.get())) {
                anchors.add(entry.getKey().immutable());
            }
        }
        // 固定顺序，代理位置不会在每 tick 之间跳来跳去
        anchors.sort(java.util.Comparator
                .<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return java.util.List.copyOf(anchors);
    }

    /** 装配快照里的反应堆主方块（前层下排中列）。 */
    @Nullable
    private static BlockPos findReactorAnchor(Map<BlockPos, BlockState> blocks) {
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (entry.getValue().is(ModBlocks.REACTOR.get())
                    && entry.getValue().getValue(com.opamb.barotrauma.block.ReactorBlock.PART)
                    == com.opamb.barotrauma.block.ReactorBlock.Part.FRONT_LOWER_MIDDLE) {
                return entry.getKey().immutable();
            }
        }
        return null;
    }

    /** 装配快照里的引擎主方块（前层下排中列）。 */
    @Nullable
    private static BlockPos findEngineAnchor(Map<BlockPos, BlockState> blocks) {
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (entry.getValue().is(ModBlocks.ENGINE.get())
                    && entry.getValue().getValue(com.opamb.barotrauma.block.EngineBlock.PART)
                    == com.opamb.barotrauma.block.EngineBlock.Part.FRONT_LOWER_MIDDLE) {
                return entry.getKey().immutable();
            }
        }
        return null;
    }

    /** 物理船排水泵在船坞里的坐标（第一台，兼容旧调用）；偏移未知或没有泵时返回 null。 */
    @Nullable
    public static BlockPos pumpYardPos() {
        if (pumpAnchors.isEmpty() || yardOffsetX == Integer.MIN_VALUE) {
            return null;
        }
        return pumpAnchors.get(0).offset(yardOffsetX, yardOffsetY, yardOffsetZ);
    }

    /** 物理船反应堆在船坞里的坐标；偏移未知或没有堆时返回 null。 */
    @Nullable
    public static BlockPos reactorYardPos() {
        if (reactorAnchor == null || yardOffsetX == Integer.MIN_VALUE) {
            return null;
        }
        return reactorAnchor.offset(yardOffsetX, yardOffsetY, yardOffsetZ);
    }

    /**
     * 物理船某台泵的方块实体。{@code proxyPos} 是玩家右键的那个不可见代理方块：
     * 先按"代理位置 → 装配锚点"找回是哪台泵，再加船坞偏移定位船坞里的真实方块实体。
     *
     * <p>之前所有泵共用一个锚点（压载泵优先），右键小泵/排水泵的代理时打开的
     * 都是压载泵菜单——玩家实测"小水泵没法用"。现在每台泵都有自己的代理。</p>
     */
    @Nullable
    public static DrainPumpBlockEntity pumpBlockEntity(ServerLevel level, BlockPos proxyPos) {
        if (level == null) {
            return null;
        }
        BlockPos anchor = null;
        if (proxyPos != null) {
            for (Map.Entry<BlockPos, BlockPos> entry : pumpProxyByAnchor.entrySet()) {
                if (proxyPos.equals(entry.getValue())) {
                    anchor = entry.getKey();
                    break;
                }
            }
        }
        if (anchor == null && pumpAnchors.size() == 1) {
            // 只有一个代理时允许兜底（装配后的头几 tick 映射可能还没建好）
            anchor = pumpAnchors.get(0);
        }
        if (anchor == null || yardOffsetX == Integer.MIN_VALUE) {
            return null;
        }
        BlockPos yard = anchor.offset(yardOffsetX, yardOffsetY, yardOffsetZ);
        return level.getBlockEntity(yard) instanceof DrainPumpBlockEntity pump ? pump : null;
    }

    /** 兼容旧调用：没有代理位置时退回第一台泵。 */
    @Nullable
    public static DrainPumpBlockEntity pumpBlockEntity(ServerLevel level) {
        return pumpBlockEntity(level, null);
    }

    /** 物理船反应堆方块实体（面板/操作走它）。 */
    @Nullable
    public static com.opamb.barotrauma.blockentity.ReactorBlockEntity reactorBlockEntity(ServerLevel level) {
        BlockPos pos = reactorYardPos();
        if (level == null || pos == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof com.opamb.barotrauma.blockentity.ReactorBlockEntity reactor
                ? reactor : null;
    }

    /** 物理船导航/声纳核心（修理面板走它）。 */
    @Nullable
    public static com.opamb.barotrauma.blockentity.ShipCoreBlockEntity sonarCoreBlockEntity(ServerLevel level) {
        if (sonarAnchor == null || yardOffsetX == Integer.MIN_VALUE) {
            return null;
        }
        BlockPos yard = sonarAnchor.offset(yardOffsetX, yardOffsetY, yardOffsetZ);
        return level.getBlockEntity(yard) instanceof com.opamb.barotrauma.blockentity.ShipCoreBlockEntity core
                ? core : null;
    }

    /** 物理船引擎在船坞里的坐标。 */
    @Nullable
    public static BlockPos engineYardPos() {
        if (engineAnchor == null || yardOffsetX == Integer.MIN_VALUE) {
            return null;
        }
        return engineAnchor.offset(yardOffsetX, yardOffsetY, yardOffsetZ);
    }

    /** 物理船引擎方块实体（面板走它）。 */
    @Nullable
    public static com.opamb.barotrauma.blockentity.EngineBlockEntity engineBlockEntity(ServerLevel level) {
        BlockPos pos = engineYardPos();
        if (level == null || pos == null) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof com.opamb.barotrauma.blockentity.EngineBlockEntity engine
                ? engine : null;
    }

    private static void refreshHull(ServerLevel level) {
        ShipState current = state;
        if (current == null || snapshot.isEmpty() || yardOffsetX == Integer.MIN_VALUE) {
            return;
        }
        // 刚装配完先等 1 秒：VS2 搬方块/加载船坞区块有延迟，别把"还没读出来"当成破坏
        if (level.getGameTime() < hullCheckStartGameTime) {
            return;
        }
        List<BlockPos> broken = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            BlockPos yard = entry.getKey().offset(yardOffsetX, yardOffsetY, yardOffsetZ);
            BlockState actual = level.getBlockState(yard);
            if (actual.isAir() || !actual.is(entry.getValue().getBlock())) {
                broken.add(entry.getKey());
            }
        }
        if (broken.isEmpty()) {
            return;
        }
        for (BlockPos pos : broken) {
            snapshot.remove(pos);
        }
        rebuildGeometry(level, broken);
    }

    /**
     * 用船坞里的真实方块重新扫描船体几何，并替换当前船状态的舱室/连海空腔。
     *
     * <p>扫描坐标仍用"装配原坐标"，只是读取时加上船坞偏移。这样舱室、破口、
     * 声纳位置都和客户端收到的那套坐标一致，不需要做任何坐标变换。</p>
     */
    private static void rebuildGeometry(ServerLevel level, List<BlockPos> broken) {
        ShipState current = state;
        BlockPos core = corePos;
        if (current == null || core == null || yardOffsetX == Integer.MIN_VALUE) {
            return;
        }
        if (lastLevel != null && lastLevel != level) {
            // 活动物理船在另一个维度：那边开关门/放门时也会走到这个刷新入口，
            // 但船坞坐标只在活动船自己的维度里有效。拿别的维度去读会把活动船的
            // 几何清空，replaceGeometry 还会顺手清掉船壳耐久记录。
            return;
        }
        final int ox = yardOffsetX;
        final int oy = yardOffsetY;
        final int oz = yardOffsetZ;
        ShipGeometry.Probe probe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                if (pos.equals(core)) {
                    return false;
                }
                return ModShipBlocks.isSolidForShip(level.getBlockState(pos.offset(ox, oy, oz)));
            }

            @Override
            public boolean isWater(BlockPos pos) {
                // 船坞区块里是船体方块本身，没有海水；进水是模拟量
                return false;
            }

            @Override
            public boolean isDoorway(BlockPos pos) {
                return ModShipBlocks.isDoorway(level.getBlockState(pos.offset(ox, oy, oz)));
            }

            @Override
            public boolean isOpenDoorway(BlockPos pos) {
                return ModShipBlocks.isOpenDoorway(level.getBlockState(pos.offset(ox, oy, oz)));
            }
        };
        ShipGeometry.Result geometry = ShipGeometry.scanShip(core, ShipCoreBlockEntity.RADIUS,
                pos -> ModShipBlocks.isShipBlock(level.getBlockState(pos.offset(ox, oy, oz)))
                        && !com.opamb.barotrauma.world.DockingBay.isStationSideDoor(pos)
                        && !com.opamb.barotrauma.world.DockingBay.isStationDockHatch(pos), probe, 0,
                // 船壳当墙：船坞里的船也是圆角外壳，包围盒四角的海水不算船内进水。
                pos -> {
                    if (pos.equals(core)) {
                        return false;
                    }
                    BlockState shell = level.getBlockState(pos.offset(ox, oy, oz));
                    return ModShipBlocks.sealsHull(shell);
                },
                pos -> ModShipBlocks.isEquipment(level.getBlockState(pos.offset(ox, oy, oz))));
        current.replaceGeometry(geometry);
        rememberInterior(geometry);
        // breached 方块仍是 ship block，重新装配/重扫后耐久表可能是空的：按 blockstate 补 0%。
        List<BlockPos> breached = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            if (HullBlock.isBreached(entry.getValue())) {
                current.hullDamage().setHealth(entry.getKey(), 0.0F);
                breached.add(entry.getKey());
            }
        }
        // 记录连海破口位置：客户端据此在洞口画水束/气泡。内部隔墙的洞会被过滤掉。
        current.addBreaches(broken);
        current.addBreaches(breached);
        // 洞越大进水越快：已删除格 + 0% breached 格都算开口。
        int destroyed = Math.max(0, initialShipBlocks - snapshot.size());
        current.simulation().setBreachSize(Math.max(1, destroyed + breached.size()));
        // 用"旧的舱内格 + 新的密闭舱室"合并，而不是直接取新 openPockets：
        // 破口舱室会从 compartments 挪到 openPockets，但它的格子在旧集合里，
        // 必须继续挡真实海水；而 openPockets 可能包含船壳外的空气，不能一起保护。
        java.util.LinkedHashSet<BlockPos> merged = new java.util.LinkedHashSet<>(interiorCells);
        for (ShipGeometry.Compartment compartment : geometry.compartments()) {
            merged.addAll(compartment.cells());
        }
        interiorCells = new ArrayList<>(merged);
        guardCells = buildGuardCells(interiorCells);
        // 舱室格变了：让下一 tick 重新清一次新舱室里的水
        interiorOffsetX = Integer.MIN_VALUE;
        interiorOffsetY = Integer.MIN_VALUE;
        interiorOffsetZ = Integer.MIN_VALUE;
        LOGGER.debug("[vs] 空气海域（破损后）：舱室 {} 格 + 剩余船体 {} 格 = {} 格（真实水拦截保持停用）",
                interiorCells.size(), snapshot.size(), guardCells.size());
        if (broken.isEmpty()) {
            LOGGER.debug("[vs] 门格几何重扫：{} 个舱室 / {} 处连海 / 舱容 {}",
                    geometry.compartments().size(), geometry.openPockets().size(),
                    geometry.totalVolume());
        } else {
            LOGGER.info("[vs] 船体破损：{} 格被破坏 → 重扫 {} 个舱室 / {} 处连海 / 舱容 {}",
                    broken.size(), geometry.compartments().size(), geometry.openPockets().size(),
                    geometry.totalVolume());
        }
        if (!broken.isEmpty()) {
            LOGGER.info("[vs] 破口位置（原坐标，前 5 个）：{}",
                    broken.subList(0, Math.min(5, broken.size())));
        }
        for (ShipGeometry.Compartment pocket : geometry.openPockets()) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : pocket.cells()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            // 水场格数/容量：破口裁空 interior 时，靠历史舱内格兜底应该和房间格数
            // 对得上；如果这里远小于"格"，水会只在一小块里涨（2026-11-20 实测）。
            int fieldCells = 0;
            double fieldCapacity = 0.0D;
            int pocketIndex = geometry.openPockets().indexOf(pocket);
            if (current != null && current.simulation() != null
                    && current.simulation().fluid() != null && pocketIndex >= 0) {
                int body = current.compartments().size() + pocketIndex;
                fieldCells = current.simulation().fluid().cellsOfBody(body).size();
                fieldCapacity = current.simulation().fluid().bodyCapacity(body);
            }
            LOGGER.debug("[vs] 连海空腔：{} 格（水场 {} 格 / {} 单位），bbox x{}..{} y{}..{} z{}..{}",
                    pocket.volume(), fieldCells, Math.round(fieldCapacity),
                    minX, maxX, minY, maxY, minZ, maxZ);
        }
        sendWaterState(current);
    }

    /**
     * 从玩家眼睛沿视线对装配快照做 AABB 射线检测，返回命中的装配原坐标方块。
     *
     * <p>船在世界里是连续渲染的刚体，方块本体在船坞；必须用完整的
     * {@code worldToShip}（含旋转）把视线变换回装配原坐标，不能只减平移量，
     * 否则船一转身/倾斜，砸的位置就会偏。</p>
     */
    @Nullable
    private static BlockPos pickHullBlock(ServerLevel level, ServerPlayer player) {
        return pickHullBlock(level, player, false);
    }

    /**
     * 船坞里这个格子当前是不是开着的舱门/舱口。
     *
     * <p>要读船坞当前方块状态而不是快照里的 {@code OPEN}：快照可能已经过期。</p>
     */
    private static boolean isOpenDoorway(ServerLevel level, BlockPos assemblyPos) {
        if (yardOffsetX == Integer.MIN_VALUE) {
            return false;
        }
        BlockState at = level.getBlockState(
                assemblyPos.offset(yardOffsetX, yardOffsetY, yardOffsetZ));
        if (at.getBlock() instanceof com.opamb.barotrauma.block.BulkheadDoorBlock
                && at.hasProperty(com.opamb.barotrauma.block.BulkheadDoorBlock.OPEN)) {
            return at.getValue(com.opamb.barotrauma.block.BulkheadDoorBlock.OPEN);
        }
        if (at.getBlock() instanceof com.opamb.barotrauma.block.HatchBlock
                && at.hasProperty(com.opamb.barotrauma.block.HatchBlock.OPEN)) {
            return at.getValue(com.opamb.barotrauma.block.HatchBlock.OPEN);
        }
        return false;
    }

    /**
     * @param skipBreached 切割用：0% 的破口船壳无碰撞但仍留在快照里，
     *                     不能挡住后方舱门/舱口的切割射线；焊接仍要能命中破口。
     */
    @Nullable
    private static BlockPos pickHullBlock(ServerLevel level, ServerPlayer player, boolean skipBreached) {
        if (ship == null || state == null || snapshot.isEmpty()) {
            return null;
        }
        ServerShip live = view(level);
        if (live == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        // +1 格余量：客户端创造模式的交互距离和属性值可能差一点，
        // 客户端既然发出了破坏包，说明它已经判定命中了。
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.getBlockReach() + 1.0D));
        org.joml.Matrix4d worldToShip;
        org.joml.Vector3d origin;
        try {
            worldToShip = new org.joml.Matrix4d(live.getTransform().getShipToWorld()).invert();
            org.joml.Vector3dc positionInShip = live.getTransform().getPositionInShip();
            origin = new org.joml.Vector3d(
                    positionInShip.x() - assembledPos.x,
                    positionInShip.y() - assembledPos.y,
                    positionInShip.z() - assembledPos.z);
        } catch (Throwable error) {
            return null;
        }
        org.joml.Vector3d eyeShip = worldToShip.transformPosition(
                new org.joml.Vector3d(eye.x, eye.y, eye.z), new org.joml.Vector3d()).sub(origin);
        org.joml.Vector3d endShip = worldToShip.transformPosition(
                new org.joml.Vector3d(end.x, end.y, end.z), new org.joml.Vector3d()).sub(origin);
        Vec3 from = new Vec3(eyeShip.x, eyeShip.y, eyeShip.z);
        Vec3 to = new Vec3(endShip.x, endShip.y, endShip.z);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : snapshot.keySet()) {
            if (skipBreached) {
                BlockState at = snapshot.get(pos);
                if (at != null && at.hasProperty(HullBlock.BREACHED)
                        && at.getValue(HullBlock.BREACHED)) {
                    continue;
                }
                // 开着的舱门/舱口没有实体阻挡：切割射线要穿过去打后方目标。
                if (isOpenDoorway(level, pos)) {
                    continue;
                }
            }
            java.util.Optional<Vec3> hit = new AABB(pos).clip(from, to);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = hit.get().distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        if (best == null) {
            return null;
        }
        // 船外还有真实世界方块（地形/哨站）。如果视线先撞到它们，不能隔墙拆/焊船。
        ClipContext clip = new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player);
        BlockHitResult worldHit = RaycastUtilsKt.vanillaClip(level, clip);
        if (worldHit.getType() == HitResult.Type.BLOCK
                && worldHit.getLocation().distanceToSqr(eye) < bestDistance) {
            return null;
        }
        return best;
    }

    /**
     * 物理船左键：先扣船壳耐久；0 时方块切成 breached（无碰撞、保留数据），不删除。
     * {@code ShipBreakMixin} 在服务端收到"开始破坏"时调用。
     */
    private static boolean tryDamage(ServerLevel level, ServerPlayer player, BlockPos packetPos, float amount) {
        if (ship == null || state == null || lastLevel != level || snapshot.isEmpty()
                || player.isSpectator() || amount <= 0.0F) {
            return false;
        }
        BlockPos best = pickHullBlock(level, player);
        if (best == null) {
            return false;
        }
        BlockState block = snapshot.get(best);
        if (block == null) {
            return false;
        }
        if (isConsolePart(block)) {
            player.displayClientMessage(Component.translatable("message.barotrauma.hull_sonar")
                    .withStyle(ChatFormatting.GOLD), true);
            return true;
        }
        float max = state.hullMaxHealth(block);
        if (max <= 0.0F) {
            return false;
        }
        // 创造模式蹲下 = 建造模式：0% 的 breached 方块直接清掉，普通方块一击打到 0%。
        if (player.isCreative() && player.isShiftKeyDown() && HullBlock.isBreached(block)) {
            if (yardOffsetX == Integer.MIN_VALUE) {
                discoverYardOffset(level);
            }
            if (yardOffsetX == Integer.MIN_VALUE) {
                return false;
            }
            state.hullDamage().clear(best);
            state.removeBreach(best);
            level.setBlock(best.offset(yardOffsetX, yardOffsetY, yardOffsetZ),
                    Blocks.AIR.defaultBlockState(), 3);
            snapshot.remove(best);
            rebuildGeometry(level, List.of(best));
            state.removeBreach(best);
            sendHullDamageState(state);
            return true;
        }
        if (player.isCreative() && player.isShiftKeyDown()) {
            amount = max * 2.0F;
        }
        float health = state.hullDamage().applyDamage(best, amount, max);
        if (health <= 0.0F) {
            breachSnapshotBlock(level, player, best, block);
        } else {
            Vec3 hitWorld = toWorld(best);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    hitWorld.x, hitWorld.y, hitWorld.z,
                    6, 0.25D, 0.25D, 0.25D, 0.02D);
            level.playSound(null, BlockPos.containing(hitWorld),
                    net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_HIT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.2F);
            sendHullDamageState(state);
        }
        return true;
    }

    /** 物理船右键焊接：沿视线找一格受损船壳并回耐久。 */
    private static boolean tryRepair(ServerLevel level, ServerPlayer player, float amount) {
        if (ship == null || state == null || lastLevel != level || snapshot.isEmpty()
                || player.isSpectator()) {
            return false;
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            discoverYardOffset(level);
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            return false;
        }
        BlockPos best = pickHullBlock(level, player);
        if (best == null) {
            return false;
        }
        BlockState block = snapshot.get(best);
        if (block == null || isConsolePart(block)) {
            return false;
        }
        float max = state.hullMaxHealth(block);
        if (max <= 0.0F || !state.hullDamage().isDamaged(best)) {
            return false;
        }
        boolean wasBreached = HullBlock.isBreached(block);
        float health = state.hullDamage().repair(best, amount, max);
        if (level.getGameTime() % 10L == 0L) {
            level.playSound(null, BlockPos.containing(toWorld(best)),
                    net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.35F, 1.7F);
        }
        if (wasBreached && health > 0.0F) {
            // 焊过 0%：先撤掉破口记录，再恢复船坞方块碰撞并重扫封口。
            state.removeBreach(best);
            BlockState restored = block.setValue(HullBlock.BREACHED, false);
            level.setBlock(best.offset(yardOffsetX, yardOffsetY, yardOffsetZ), restored, 3);
            snapshot.put(best, restored);
            rebuildGeometry(level, List.of());
        }
        sendHullDamageState(state);
        return true;
    }

    /**
     * 等离子切割机：沿视线切船壳（扣耐久，0% 走破口），或把舱门/舱口切烂。
     *
     * <p>舱门没有船壳耐久表，单独按格累计切割量；切穿后从船坞方块、快照和
     * 舱室几何里一起摘掉，并在切口位置掉回门/舱口物品。</p>
     */
    private static boolean tryCut(ServerLevel level, ServerPlayer player, float amount) {
        if (ship == null || state == null || lastLevel != level || snapshot.isEmpty()
                || player.isSpectator()) {
            return false;
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            discoverYardOffset(level);
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            return false;
        }
        // 切割射线跳过 0% 的破口船壳：它无碰撞，不能挡住后方舱门/舱口。
        BlockPos best = pickHullBlock(level, player, true);
        if (best == null) {
            return false;
        }
        BlockState block = snapshot.get(best);
        if (block == null || isConsolePart(block)) {
            return false;
        }
        // 位置级破坏权限：物理船的方块在世界里是空气，保护监听器要的是映射后的
        // 世界坐标；同一目标 4 tick 内只发一次事件，避免按住时刷屏。
        Vec3 hit = toWorld(best);
        if (hit == null) {
            return false;
        }
        BlockPos worldPos = BlockPos.containing(hit);
        long now = level.getGameTime();
        if (!worldPos.equals(protectCheckedPos)
                || !player.getUUID().equals(protectPlayer)
                || now - protectCheckedTick > 4L) {
            net.minecraft.world.level.GameType mode = player.gameMode.getGameModeForPlayer();
            protectDenied = net.minecraftforge.common.ForgeHooks
                    .onBlockBreakEvent(level, mode, player, worldPos) == -1
                    || player.blockActionRestricted(level, worldPos, mode);
            protectCheckedPos = worldPos.immutable();
            protectCheckedTick = now;
            protectPlayer = player.getUUID();
        }
        if (protectDenied) {
            return false;
        }
        float max = state.hullMaxHealth(block);
        if (max > 0.0F) {
            float health = state.hullDamage().applyDamage(best, amount, max);
            if (health <= 0.0F) {
                breachSnapshotBlock(level, player, best, block);
            } else {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        hit.x, hit.y, hit.z, 4, 0.2D, 0.2D, 0.2D, 0.01D);
                if (level.getGameTime() % 10L == 0L) {
                    level.playSound(null, BlockPos.containing(hit),
                            net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.35F, 1.9F);
                }
                sendHullDamageState(state);
            }
            return true;
        }
        if (!isCuttableFixture(block)) {
            return false;
        }
        if (fixtureCutAnchor == null || !fixtureCutAnchor.equals(assembledPos)) {
            fixtureCut.clear();
            fixtureCutAnchor = assembledPos;
        }
        float progress = fixtureCut.merge(best.immutable(), amount, Float::sum);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                hit.x, hit.y, hit.z, 4, 0.25D, 0.25D, 0.25D, 0.02D);
        if (level.getGameTime() % 10L == 0L) {
            level.playSound(null, BlockPos.containing(hit),
                    net.minecraft.sounds.SoundEvents.FIRE_AMBIENT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.3F, 1.6F);
        }
        if (progress >= FIXTURE_CUT_HEALTH && removeFixture(level, player, best, block)) {
            fixtureCut.remove(best);
        }
        return true;
    }

    /** 等离子切割机能把哪些船内构件切烂（舱门/舱口；船壳走耐久）。 */
    private static boolean isCuttableFixture(BlockState block) {
        net.minecraft.world.level.block.Block b = block.getBlock();
        return b == com.opamb.barotrauma.registry.ModShipBlocks.BULKHEAD_DOOR.get()
                || b == com.opamb.barotrauma.registry.ModShipBlocks.HATCH.get()
                || b == com.opamb.barotrauma.registry.ModShipBlocks.HATCH_LADDER.get();
    }

    /** 切穿舱门/舱口：清船坞方块、移出快照、重扫几何，并在切口掉回物品。 */
    private static boolean removeFixture(ServerLevel level, ServerPlayer player,
                                         BlockPos original, BlockState block) {
        // 舱口是三格构件（LEFT/MIDDLE/RIGHT）；只拆命中格会留下两半，
        // 两边再各切一次又能刷出完整物品。这里按整套删除、只掉一次。
        List<BlockPos> cells = new ArrayList<>();
        if (block.getBlock() instanceof com.opamb.barotrauma.block.HatchBlock
                && block.hasProperty(com.opamb.barotrauma.block.HatchBlock.PART)
                && block.hasProperty(com.opamb.barotrauma.block.HatchBlock.FACING)) {
            net.minecraft.core.Direction facing =
                    block.getValue(com.opamb.barotrauma.block.HatchBlock.FACING);
            net.minecraft.core.Direction longAxis = facing.getClockWise();
            int offset = block.getValue(com.opamb.barotrauma.block.HatchBlock.PART).ordinal() - 1;
            BlockPos middle = original.relative(longAxis, -offset);
            BlockState middleState = snapshot.get(middle);
            if (middleState != null
                    && middleState.getBlock() instanceof com.opamb.barotrauma.block.HatchBlock
                    && middleState.hasProperty(com.opamb.barotrauma.block.HatchBlock.PART)
                    && middleState.getValue(com.opamb.barotrauma.block.HatchBlock.PART)
                    == com.opamb.barotrauma.block.HatchBlock.Part.MIDDLE
                    && middleState.getValue(com.opamb.barotrauma.block.HatchBlock.FACING) == facing) {
                cells.add(middle);
                cells.add(middle.relative(longAxis));
                cells.add(middle.relative(longAxis.getOpposite()));
            } else {
                cells.add(original);
            }
        } else if (block.getBlock() instanceof com.opamb.barotrauma.block.BulkheadDoorBlock
                && block.hasProperty(com.opamb.barotrauma.block.BulkheadDoorBlock.PART)) {
            // 舱壁门占上下两格：只拆命中格会留下半扇，再切一次又掉一个完整门。
            // 和方块船路径（PlasmaCutterItem.breakFixture）保持一致，整套只掉一次。
            BlockPos lower = block.getValue(com.opamb.barotrauma.block.BulkheadDoorBlock.PART)
                    == com.opamb.barotrauma.block.BulkheadDoorBlock.Part.UPPER
                    ? original.below() : original;
            for (BlockPos cell : new BlockPos[]{lower, lower.above()}) {
                BlockState part = snapshot.get(cell);
                if (part != null && part.getBlock()
                        instanceof com.opamb.barotrauma.block.BulkheadDoorBlock) {
                    cells.add(cell);
                }
            }
            if (cells.isEmpty()) {
                cells.add(original);
            }
        } else {
            cells.add(original);
        }
        Vec3 world = toWorld(original);
        if (world == null) {
            return false;
        }
        // 三格舱口会一次删三格：每格都要过位置级破坏权限。Forge 的
        // setBlock 不触发 BreakEvent，不能只查命中格，否则跨保护区边界时
        // 能借放行的一格删掉受保护的两格。
        for (BlockPos cell : cells) {
            if (!snapshot.containsKey(cell)) {
                continue;
            }
            Vec3 cellHit = toWorld(cell);
            if (cellHit == null) {
                return false;
            }
            BlockPos cellWorld = BlockPos.containing(cellHit);
            net.minecraft.world.level.GameType mode = player.gameMode.getGameModeForPlayer();
            if (net.minecraftforge.common.ForgeHooks
                    .onBlockBreakEvent(level, mode, player, cellWorld) == -1
                    || player.blockActionRestricted(level, cellWorld, mode)) {
                return false;
            }
        }
        for (BlockPos cell : cells) {
            if (!snapshot.containsKey(cell)) {
                continue;
            }
            level.setBlock(cell.offset(yardOffsetX, yardOffsetY, yardOffsetZ),
                    Blocks.AIR.defaultBlockState(), 3);
            snapshot.remove(cell);
            fixtureCut.remove(cell);
        }
        rebuildGeometry(level, cells);
        net.minecraft.world.item.ItemStack drop =
                new net.minecraft.world.item.ItemStack(block.getBlock().asItem());
        if (!drop.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level,
                    BlockPos.containing(world), drop);
        }
        level.playSound(null, BlockPos.containing(world),
                net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.8F);
        return true;
    }

    /** 服务端预检全部登船位置：真实船坞碰撞 + 舱室 + 当前完整刚体变换（含旋转）。 */
    private static List<Vec3> boardingPositions(ServerLevel level, int count) {
        if (ship == null || state == null || lastLevel != level || count < 1) return List.of();
        // 刚装配/刚传送到副本码头时，船坞偏移和水面包都还是旧位置的，不能拿来做判定。
        // 直接把装配快照里的舱室格按刚体当前变换换到世界坐标，再查世界碰撞。
        List<Vec3> result = new ArrayList<>();
        for (ShipGeometry.Compartment compartment : state.geometry().compartments()) {
            for (BlockPos original : compartment.cells().stream().sorted(java.util.Comparator
                    .comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getX())
                    .thenComparingInt(p -> p.getZ())).toList()) {
                if (!compartment.cells().contains(original.above())) continue;
                Vec3 center = toWorld(original);
                if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
                    continue;
                }
                double feetY = center.y - 0.45D;
                if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(center))) continue;
                if (!level.noCollision(new net.minecraft.world.phys.AABB(
                        center.x - .31D, feetY, center.z - .31D,
                        center.x + .31D, feetY + 1.8D, center.z + .31D))) {
                    continue;
                }
                result.add(new Vec3(center.x, feetY, center.z));
                if (result.size() == count) return List.copyOf(result);
            }
        }
        return List.of();
    }

    /** 水面包为装配快照坐标；在同一坐标空间检查，不用只支持平移的旧干区近似。 */
    private static boolean boardingCellDry(BlockPos original) {
        float[] boxes = lastWaterPacket.boxes();
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (original.getX() + .5 >= boxes[i] && original.getX() + .5 <= boxes[i + 3] + 1
                    && original.getZ() + .5 >= boxes[i + 2] && original.getZ() + .5 <= boxes[i + 5] + 1
                    && original.getY() + .05 >= boxes[i + 1] && original.getY() + 1.7 <= boxes[i + 4] + 1
                    && original.getY() + .05 > boxes[i + 6] + .01) return true;
        }
        return false;
    }

    /** 把装配快照里的原坐标映射到船当前的世界坐标。 */
    private static Vec3 toWorld(BlockPos original) {
        if (ship == null) {
            return Vec3.atCenterOf(original);
        }
        // 和 view 一样读“已加载”句柄：装配时拿到的 ServerShip 快照 transform
        // 不随物理更新，船开走后会一直返回装配那一刻的旧坐标。
        ServerShip live = lastLevel == null ? ship : view(lastLevel);
        org.joml.Vector3dc positionInShip = live.getTransform().getPositionInShip();
        org.joml.Vector3d shipSpace = new org.joml.Vector3d(
                original.getX() + 0.5D + positionInShip.x() - assembledPos.x,
                original.getY() + 0.5D + positionInShip.y() - assembledPos.y,
                original.getZ() + 0.5D + positionInShip.z() - assembledPos.z);
        org.joml.Vector3d world = live.getTransform().getShipToWorld()
                .transformPosition(shipSpace, new org.joml.Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    /** 给反应堆熔毁/起火用：装配坐标 → 当前世界坐标；没有物理船返回 null。 */
    @Nullable
    public static Vec3 assemblyToWorld(BlockPos assemblyPos) {
        if (ship == null || assemblyPos == null) {
            return null;
        }
        try {
            return toWorld(assemblyPos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 耐久到 0：把船坞方块切成 {@code breached}（无碰撞、不可见、保留数据），不删除、不掉落。
     * 修回 >0 时 {@link #tryRepair} 会恢复实体并重扫封口。
     */
    private static void breachSnapshotBlock(ServerLevel level, @Nullable ServerPlayer player,
                                            BlockPos original, BlockState block) {
        if (yardOffsetX == Integer.MIN_VALUE) {
            discoverYardOffset(level);
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            return;
        }
        if (!(block.getBlock() instanceof HullBlock)) {
            // 兜底：不是船壳类方块就退回原来的删除逻辑（正常不会走到）。
            state.hullDamage().clear(original);
            level.setBlock(original.offset(yardOffsetX, yardOffsetY, yardOffsetZ),
                    Blocks.AIR.defaultBlockState(), 3);
            snapshot.remove(original);
            rebuildGeometry(level, List.of(original));
            return;
        }
        state.hullDamage().setHealth(original, 0.0F);
        BlockState breached = block.setValue(HullBlock.BREACHED, true);
        level.setBlock(original.offset(yardOffsetX, yardOffsetY, yardOffsetZ), breached, 3);
        snapshot.put(original, breached);
        level.playSound(null, BlockPos.containing(toWorld(original)),
                net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.9F);
        rebuildGeometry(level, List.of(original));
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.barotrauma.hull_broken")
                    .withStyle(ChatFormatting.RED), true);
        }
        sendHullDamageState(state);
    }

    /**
     * 物理船爆炸：方块在世界里是空气，原版爆炸列表根本碰不到它们。
     * 这里按装配快照逐个映射到世界坐标，按距离衰减扣耐久，最后统一重扫。
     */
    private static int damageExplosion(ServerLevel level, Vec3 center, double radius, float baseAmount) {
        if (ship == null || state == null || lastLevel != level || snapshot.isEmpty()
                || radius <= 0.0D) {
            return 0;
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            discoverYardOffset(level);
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            return 0;
        }
        ServerShip live = view(level);
        if (live == null) {
            return 0;
        }
        org.joml.Vector3dc positionInShip = live.getTransform().getPositionInShip();
        org.joml.Vector3d origin = new org.joml.Vector3d(
                positionInShip.x() - assembledPos.x,
                positionInShip.y() - assembledPos.y,
                positionInShip.z() - assembledPos.z);
        org.joml.Matrix4dc shipToWorld = live.getTransform().getShipToWorld();
        org.joml.Vector3d blast = new org.joml.Vector3d(center.x, center.y, center.z);
        List<BlockPos> broken = new ArrayList<>();
        int hit = 0;
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState block = entry.getValue();
            if (isConsolePart(block)) {
                continue;
            }
            float max = state.hullMaxHealth(block);
            // 设备（泵/反应堆/引擎/门/柜子等）没有船壳耐久，但爆炸同样要伤到
            // 耐久组件；物理船方块在世界里是空气，RepairDamageEvents 拿不到它们。
            BlockPos deviceYard = pos.offset(yardOffsetX, yardOffsetY, yardOffsetZ);
            net.minecraft.world.level.block.entity.BlockEntity deviceBe =
                    level.getBlockEntity(deviceYard);
            com.opamb.barotrauma.ship.RepairTarget device =
                    deviceBe instanceof com.opamb.barotrauma.ship.RepairTarget target
                            ? target : null;
            if (max <= 0.0F && device == null) {
                continue;
            }
            org.joml.Vector3d shipSpace = new org.joml.Vector3d(
                    pos.getX() + 0.5D + origin.x,
                    pos.getY() + 0.5D + origin.y,
                    pos.getZ() + 0.5D + origin.z);
            org.joml.Vector3d world = shipToWorld.transformPosition(shipSpace, new org.joml.Vector3d());
            double distance = world.distance(blast);
            if (distance > radius) {
                continue;
            }
            float amount = (float) (baseAmount * (1.0D - distance / radius));
            if (amount <= 0.0F) {
                continue;
            }
            hit++;
            if (device != null) {
                device.damage(com.opamb.barotrauma.event.RepairDamageEvents.EXPLOSION_DAMAGE);
                deviceBe.setChanged();
            }
            if (max > 0.0F && state.hullDamage().applyDamage(pos, amount, max) <= 0.0F) {
                broken.add(pos);
            }
        }
        List<BlockPos> breached = new ArrayList<>();
        for (BlockPos pos : broken) {
            BlockState block = snapshot.get(pos);
            if (block != null && block.getBlock() instanceof HullBlock) {
                state.hullDamage().setHealth(pos, 0.0F);
                BlockState next = block.setValue(HullBlock.BREACHED, true);
                level.setBlock(pos.offset(yardOffsetX, yardOffsetY, yardOffsetZ), next, 3);
                snapshot.put(pos, next);
                breached.add(pos);
            } else if (block != null) {
                state.hullDamage().clear(pos);
                level.setBlock(pos.offset(yardOffsetX, yardOffsetY, yardOffsetZ),
                        Blocks.AIR.defaultBlockState(), 3);
                snapshot.remove(pos);
            }
        }
        if (!breached.isEmpty()) {
            rebuildGeometry(level, breached);
        }
        if (hit > 0) {
            sendHullDamageState(state);
        }
        return hit;
    }

    /** 自检用：按原坐标在船坞里拆一格并重扫，验证破口进水链路。 */
    public static boolean damageForTest(ServerLevel level, BlockPos originalPos) {
        if (ship == null || state == null) {
            return false;
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            discoverYardOffset(level);
        }
        if (yardOffsetX == Integer.MIN_VALUE) {
            return false;
        }
        if (!snapshot.containsKey(originalPos)) {
            return false;
        }
        level.setBlock(originalPos.offset(yardOffsetX, yardOffsetY, yardOffsetZ),
                Blocks.AIR.defaultBlockState(), 3);
        snapshot.remove(originalPos);
        rebuildGeometry(level, List.of(originalPos));
        return true;
    }

    /**
     * 交互锚点跟着船整格移动。
     *
     * <p>船上右键不到方块（VS2 2.4.11 限制），所以声纳的交互点用一块真实方块代理。
     * 船挪一格，代理挪一格；旧位置的代理撤掉、新位置补上。</p>
     */
    private static void moveProxy(ServerLevel level, int dx, int dy, int dz) {
        BlockPos sonarTarget = sonarAnchor == null ? null : sonarAnchor.offset(dx, dy, dz);
        BlockPos reactorTarget = reactorAnchor == null ? null : reactorAnchor.offset(dx, dy, dz);
        BlockPos engineTarget = engineAnchor == null ? null : engineAnchor.offset(dx, dy, dz);
        boolean sonarSame = sonarTarget != null && sonarTarget.equals(proxyPos)
                && proxyPos != null && level.getBlockState(proxyPos).is(ModShipBlocks.SONAR_PROXY.get());
        // 每台泵各一个代理：全部都已经在目标位置才算没变。
        java.util.Map<BlockPos, BlockPos> nextPumpProxies = new java.util.HashMap<>();
        for (BlockPos anchor : pumpAnchors) {
            BlockPos target = anchor.offset(dx, dy, dz);
            BlockPos current = pumpProxyByAnchor.get(anchor);
            if (target.equals(current) && current != null
                    && level.getBlockState(current).is(ModShipBlocks.PUMP_PROXY.get())) {
                nextPumpProxies.put(anchor, current);
            }
        }
        boolean pumpSame = nextPumpProxies.size() == pumpAnchors.size();
        boolean reactorSame = reactorTarget != null && reactorTarget.equals(reactorProxyPos)
                && reactorProxyPos != null
                && level.getBlockState(reactorProxyPos).is(ModShipBlocks.REACTOR_PROXY.get());
        boolean engineSame = engineTarget != null && engineTarget.equals(engineProxyPos)
                && engineProxyPos != null
                && level.getBlockState(engineProxyPos).is(ModShipBlocks.ENGINE_PROXY.get());
        if (sonarSame && pumpSame && reactorSame && engineSame) {
            return;
        }
        // 两阶段：先撤所有要移动的旧代理，再放新代理。声纳和泵可能相邻，
        // 若先放后撤，会把对方刚放好的代理当成障碍物而跳过。
        if (!sonarSame) {
            clearProxy(level, proxyPos, ModShipBlocks.SONAR_PROXY.get());
        }
        if (!pumpSame) {
            for (BlockPos old : pumpProxyByAnchor.values()) {
                clearProxy(level, old, ModShipBlocks.PUMP_PROXY.get());
            }
        }
        if (!reactorSame) {
            clearProxy(level, reactorProxyPos, ModShipBlocks.REACTOR_PROXY.get());
        }
        if (!engineSame) {
            clearProxy(level, engineProxyPos, ModShipBlocks.ENGINE_PROXY.get());
        }
        proxyPos = sonarSame ? proxyPos : placeProxy(level, sonarTarget, ModShipBlocks.SONAR_PROXY.get());
        if (!pumpSame) {
            nextPumpProxies = new java.util.HashMap<>();
            for (BlockPos anchor : pumpAnchors) {
                // 泵型从装配快照读：读档/adopt() 后 pumpProxyByAnchor 可能还没建立，
                // 不能依赖旧代理上抄下来的标记。
                BlockState pumpState = snapshot.get(anchor);
                boolean smallPump = pumpState != null
                        && pumpState.is(ModBlocks.SMALL_PUMP.get());
                BlockPos placed = placeProxy(level, anchor.offset(dx, dy, dz),
                        ModShipBlocks.PUMP_PROXY.get().defaultBlockState()
                                .setValue(com.opamb.barotrauma.block.PumpProxyBlock.SMALL,
                                        smallPump));
                if (placed != null) {
                    nextPumpProxies.put(anchor, placed);
                }
            }
        }
        pumpProxyByAnchor = nextPumpProxies;
        reactorProxyPos = reactorSame ? reactorProxyPos
                : placeProxy(level, reactorTarget, ModShipBlocks.REACTOR_PROXY.get());
        engineProxyPos = engineSame ? engineProxyPos
                : placeProxy(level, engineTarget, ModShipBlocks.ENGINE_PROXY.get());
    }

    private static void clearProxy(ServerLevel level, BlockPos current, Block proxy) {
        if (current != null && level.getBlockState(current).is(proxy)) {
            level.setBlock(current, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** 放置代理；目标被别的方块占着时返回 null，不把该格记成当前位置。 */
    private static BlockPos placeProxy(ServerLevel level, BlockPos target, Block proxy) {
        return placeProxy(level, target, proxy.defaultBlockState());
    }

    /** 带状态放置代理（泵代理要区分小泵/大泵的拾取形状）。 */
    private static BlockPos placeProxy(ServerLevel level, BlockPos target, BlockState state) {
        if (target == null) {
            return null;
        }
        BlockState existing = level.getBlockState(target);
        if (existing.is(state.getBlock())) {
            return target;
        }
        if (existing.isAir() || existing.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
            level.setBlock(target, state, 2);
            return target;
        }
        return null;
    }

    /** 撤掉交互锚点（拆船 / 船丢了的时候）。 */
    private static void removeProxy(ServerLevel level) {
        if (level != null) {
            if (proxyPos != null && level.getBlockState(proxyPos).is(ModShipBlocks.SONAR_PROXY.get())) {
                level.setBlock(proxyPos, Blocks.AIR.defaultBlockState(), 2);
            }
            if (pumpProxyByAnchor != null) {
                for (BlockPos pumpProxy : pumpProxyByAnchor.values()) {
                    if (pumpProxy != null
                            && level.getBlockState(pumpProxy).is(ModShipBlocks.PUMP_PROXY.get())) {
                        level.setBlock(pumpProxy, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            if (reactorProxyPos != null
                    && level.getBlockState(reactorProxyPos).is(ModShipBlocks.REACTOR_PROXY.get())) {
                level.setBlock(reactorProxyPos, Blocks.AIR.defaultBlockState(), 2);
            }
            if (engineProxyPos != null
                    && level.getBlockState(engineProxyPos).is(ModShipBlocks.ENGINE_PROXY.get())) {
                level.setBlock(engineProxyPos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        proxyPos = null;
        sonarAnchor = null;
        pumpAnchors = java.util.List.of();
        pumpProxyByAnchor = java.util.Map.of();
        pendingBlockEntityTags = java.util.Map.of();
        reactorAnchor = null;
        reactorProxyPos = null;
        engineAnchor = null;
        engineProxyPos = null;
    }

    /** 水面同步日志节流。 */
    private static long waterStateLogCounter;

    /** 把每个舱室的包围盒 + 水面高度发给客户端（自绘水面用）。 */
    private static void sendWaterState(ShipState st) {
        long shipId = ship != null ? ship.getId() : -1L;
        // 只有模型比整格小的家具/机器需要补水面；船壳/地板/墙是完整方块，
        // 补进去会把水渲染到墙里（客户端还会把墙格当成舱内）。
        java.util.function.Predicate<BlockPos> fullCube = pos -> {
            if (lastLevel == null || yardOffsetX == Integer.MIN_VALUE) {
                return true;
            }
            BlockPos yard = toYard(pos);
            if (yard == null) {
                return true;
            }
            net.minecraft.world.level.block.state.BlockState state = lastLevel.getBlockState(yard);
            return state.isCollisionShapeFullBlock(lastLevel, yard);
        };
        ShipWaterStatePacket packet = com.opamb.barotrauma.ship.ShipWaterStateBuilder.build(st, shipId, true,
                (float) assembledPos.x, (float) assembledPos.y, (float) assembledPos.z, fullCube);
        lastWaterPacket = packet;
        ModNetwork.sendToAll(packet);
        // 船壳耐久和水平同步：同一套 anchor/坐标空间，客户端用同一个 render 变换映射。
        ModNetwork.sendToAll(ShipHullDamagePacket.build(st, shipId, true,
                (float) assembledPos.x, (float) assembledPos.y, (float) assembledPos.z));
        if (++waterStateLogCounter % 60L == 0L) {
            LOGGER.info("[vs] 水面同步：船 {}｜密闭舱 {}｜连海 {}｜连海水 {} 单位｜进水 {}%",
                    shipId, st.compartments().size(), st.geometry().openPockets().size(),
                    Math.round(st.simulation().pocketWaterTotal()),
                    Math.round(st.floodRatio() * 100.0D));
        }
    }

    /** 受击/焊接后立即同步一次船壳耐久（不等下一个水面包）。 */
    private static void sendHullDamageState(ShipState st) {
        if (st == null) {
            return;
        }
        long shipId = ship != null ? ship.getId() : -1L;
        ModNetwork.sendToAll(ShipHullDamagePacket.build(st, shipId, true,
                (float) assembledPos.x, (float) assembledPos.y, (float) assembledPos.z));
    }

    /** 装船成功后登记到 {@link VsState}，让方块那一套知道要停手。 */
    private static void activateState(ServerShip target, ServerLevel level) {
        if (target == null) {
            return;
        }
        lastLevel = level;
        VsState.activate(corePos, state, VsPilot::isPhysicalHere, VsPilot::applyControl);
    }

    // ------------------------------------------------------------------ 装配

    /** 装配最多试几次。实测同一位置会间歇性只收进一小部分方块，重试一次基本就好。 */
    private static final int ASSEMBLY_ATTEMPTS = 3;

    /**
     * 实际质量达到期望值的这个比例才算装配成功。
     *
     * <p>不放 100%：VS2 算质量走的是它自己的体素/连通性数据，对"被别的方块挡住的边角"
     * 有一点点容差，我们接受 15%。低于这个数就一定是"方块没全进去"。</p>
     */
    private static final double ASSEMBLY_MIN_RATIO = 0.85D;

    /** 把方块收成一艘物理船。返回给玩家看的一行摘要。 */
    public static String launch(ServerLevel level, Collection<BlockPos> blocks) {
        return launch(level, blocks, null, null);
    }

    public static String launch(ServerLevel level, Collection<BlockPos> blocks, ShipState shipState) {
        return launch(level, blocks, shipState, null);
    }

    public static String launch(ServerLevel level, Collection<BlockPos> blocks,
                                @Nullable ShipState shipState, @Nullable BlockPos core) {
        // 存在性检查必须用船真正所在的维度（lastLevel），不能用请求所在维度；
        // 否则在另一维度 /launch 被拒绝时，lastLevel 已经被改错，活动船的面板/
        // 几何刷新都会指向错误维度。
        // 离站：先把对接口舱口都关上，免得装成物理船后艇顶还开着洞。
        com.opamb.barotrauma.world.DockingBay.close(level);
        if (ship != null && !stillExists(lastLevel)) {
            dropStale();
        }
        if (ship != null) {
            return "已经有一艘物理船了，先 /barotrauma ship vs dock 拆掉";
        }
        lastLevel = level;
        // 新一次装配：丢掉上一艘船残留的构件切割进度和电力面板会话。
        // 必须放在"已有物理船"的拒绝之后：这次装配没有发生，别把别人开着的面板会话清掉。
        clearFixtureCut();
        com.opamb.barotrauma.network.PowerPanelState.clearPhysical();
        if (blocks.isEmpty()) {
            return "没有船体方块可收";
        }
        // 1) 方块快照必须在装配之前取：装配会把方块从世界里搬走
        Map<BlockPos, BlockState> captured = new LinkedHashMap<>();
        for (BlockPos pos : blocks) {
            BlockState at = level.getBlockState(pos);
            if (!at.isAir()) {
                captured.put(pos.immutable(), at);
            }
        }
        if (captured.isEmpty()) {
            return "没有船体方块可收";
        }
        // 方块实体的 NBT（燃料、泵设置、储物）在装配失败回滚时要一起放回去。
        Map<BlockPos, net.minecraft.nbt.CompoundTag> capturedTags =
                captureBlockEntityTags(level, captured.keySet());
        // 装配成功后船坞偏移一确定就装回这些 NBT（VS2 只搬方块状态）。
        pendingBlockEntityTags = capturedTags;
        // 1b) 这艘船"应该"多重：VS2 自己给每个方块定的质量加起来。
        //     装配后必须拿它对账，否则会出现"船壳被删掉、刚体里只有声纳一格"却报成功。
        double expected = 0.0D;
        for (BlockState state : captured.values()) {
            expected += VsBridge.blockMass(state);
        }
        // 2) 把舱内的水方块清掉：水不在装配集合里，留在原地就是一个"水泡"，
        //    船一走原地陷下去一块，还会被当成"船舱进水"
        if (shipState != null) {
            shipState.clearWater(level);
        }
        // 3) 装配 + 对账。装配是"先删方块再铺到船坞区块"，一旦铺失败，
        //    世界里那艘船就直接消失了（用户看到的"船没了、原地变海水、船和声纳不在一体"）。
        //    所以每次装配完都拿质量对账，不对就删掉半成品、把方块按快照铺回原位再试。
        StringBuilder trail = new StringBuilder();
        ServerShip assembled = null;
        double lastMass = 0.0D;
        for (int attempt = 1; attempt <= ASSEMBLY_ATTEMPTS; attempt++) {
            ServerShip candidate;
            try {
                candidate = VsBridge.assemble(level, blocks);
            } catch (Throwable error) {
                if (trail.length() > 0) {
                    trail.append("；");
                }
                trail.append("第").append(attempt).append("次异常 ").append(error);
                continue;
            }
            if (candidate == null) {
                if (trail.length() > 0) {
                    trail.append("；");
                }
                trail.append("第").append(attempt).append("次 VS2 没返回船");
                continue;
            }
            lastMass = VsBridge.mass(candidate);
            double ratio = expected <= 0.0D ? 1.0D : lastMass / expected;
            LOGGER.info("[vs] 装配第 {} 次：送进去 {} 格，期望质量 {}，实际 {}（{}%），{}",
                    attempt, captured.size(), Math.round(expected), Math.round(lastMass),
                    Math.round(ratio * 100.0D), VsBridge.claim(candidate));
            if (ratio >= ASSEMBLY_MIN_RATIO) {
                assembled = candidate;
                break;
            }
            // 只收进一部分：这个刚体不能留（留着就是"幽灵船"），删掉它再把方块铺回原位
            if (trail.length() > 0) {
                trail.append("；");
            }
            trail.append("第").append(attempt).append("次只收进 ").append(Math.round(lastMass))
                    .append("/").append(Math.round(expected)).append(" kg");
            try {
                VsBridge.discard(level, candidate);
            } catch (Throwable error) {
                trail.append("（删不掉：").append(error).append("）");
            }
            restoreBlocks(level, captured, 0, 0, 0, capturedTags);
            if (shipState != null) {
                shipState.clearWater(level);
            }
        }
        if (assembled == null) {
            // 全部失败：方块已经在每次尝试后铺回原位了，这里再兜一次，确保一格不丢
            int back = restoreBlocks(level, captured, 0, 0, 0, capturedTags);
            if (shipState != null) {
                shipState.clearWater(level);
            }
            String msg = "VS2 装配失败（" + trail + "）。已按快照把 " + back
                    + " 格船体放回原位，船没丢、没进水。重试一次 /barotrauma ship vs launch 通常就好了";
            pendingBlockEntityTags = java.util.Map.of();
            LOGGER.warn("[vs] {}", msg);
            return msg;
        }
        corePos = core;
        snapshot = captured;
        initialShipBlocks = captured.size();
        assembledPos = VsBridge.position(assembled);
        // 4) 排水体：VS2 已经把船体方块从世界挖走了，海里那块空腔会被海水立刻灌满。
        //    用不可见、不碰撞、挡水的方块把**船壳方块**占的位置填死——只盖船壳，
        //    舱室格子保持真正的空气（用户要求：舱内必须能放方块、能交互）。
        //    船整格移动时，新位置的水由 tick 里的 clearShipVolumeWater 清掉。
        interiorCells = new java.util.ArrayList<>();
        if (shipState != null) {
            for (com.opamb.barotrauma.ship.ShipGeometry.Compartment compartment : shipState.compartments()) {
                interiorCells.addAll(compartment.cells());
            }
            // 连海空腔（破口舱室）也算"舱内"：真实海水不许直接灌进来，
            // 否则客户端会同时看到原版水 + 自绘水面，破口就成了瞬满。
            for (com.opamb.barotrauma.ship.ShipGeometry.Compartment pocket : shipState.geometry().openPockets()) {
                interiorCells.addAll(pocket.cells());
            }
        }
        knownInteriorCells = java.util.Set.of();
        if (shipState != null) {
            rememberInterior(shipState.geometry());
        }
        interiorOffsetX = Integer.MIN_VALUE;
        interiorOffsetY = Integer.MIN_VALUE;
        interiorOffsetZ = Integer.MIN_VALUE;
        // 4) 空气海域：不再需要拦截/回填真实水。guardCells 仍保留，用于清掉旧存档里
        //    可能残留的水方块和做诊断；LevelFluidGuardMixin 因为 guard 未激活而失效。
        guardCells = buildGuardCells(interiorCells);
        com.opamb.barotrauma.ship.water.ShipVolumeGuard.deactivate();
        LOGGER.info("[vs] 空气海域：真实水拦截已停用（舱室 {} 格 + 船体 {} 格，保留清水/诊断）",
                interiorCells.size(), snapshot.size());
        // 船坞偏移要等核心方块实体在船坞里 tick 之后才知道；破损对账先等 1 秒
        yardOffsetX = Integer.MIN_VALUE;
        yardOffsetY = Integer.MIN_VALUE;
        yardOffsetZ = Integer.MIN_VALUE;
        nextYardScanGameTime = level.getGameTime();
        hullCheckStartGameTime = level.getGameTime() + 20L;

        // 4b) 交互锚点：VS2 船上右键不到方块，把一块不可见的声纳代理放到声纳原位，
        //     玩家右键它就能打开声纳（跟船整格移动，见 tick / moveProxy）。
        //     导航终端是 3×3×1 多方块，只有主方块（下排中列）放代理。
        sonarAnchor = null;
        for (Map.Entry<BlockPos, BlockState> entry : captured.entrySet()) {
            BlockState state = entry.getValue();
            if (state.is(ModShipBlocks.NAVIGATION_TERMINAL.get())
                    && state.getValue(com.opamb.barotrauma.block.NavigationTerminalBlock.PART)
                    == com.opamb.barotrauma.block.NavigationTerminalLayout.Part.LOWER_MIDDLE) {
                sonarAnchor = entry.getKey().immutable();
                break;
            }
        }
        if (sonarAnchor == null) {
            for (Map.Entry<BlockPos, BlockState> entry : captured.entrySet()) {
                if (entry.getValue().is(ModShipBlocks.SONAR.get())) {
                    sonarAnchor = entry.getKey().immutable();
                    break;
                }
            }
        }
        proxyPos = null;
        if (sonarAnchor != null) {
            proxyPos = sonarAnchor;
            level.setBlock(sonarAnchor, ModShipBlocks.SONAR_PROXY.get().defaultBlockState(), 3);
        }
        // 4c) 每台泵各放一个不可见代理：泵方块被收进刚体后右键不到，
        //     共用一个代理会让小泵/排水泵打开压载泵菜单。
        pumpAnchors = findPumpAnchors(captured);
        java.util.Map<BlockPos, BlockPos> pumpProxies = new java.util.HashMap<>();
        for (BlockPos pumpAnchor : pumpAnchors) {
            // 代理的拾取形状要跟泵型号一致：小泵模型只有 6/16 高，
            // 排水泵/压载泵更高，必须用整格范围才点得到。
            BlockState pumpState = captured.get(pumpAnchor);
            boolean smallPump = pumpState != null
                    && pumpState.is(ModBlocks.SMALL_PUMP.get());
            level.setBlock(pumpAnchor, ModShipBlocks.PUMP_PROXY.get().defaultBlockState()
                    .setValue(com.opamb.barotrauma.block.PumpProxyBlock.SMALL, smallPump), 3);
            pumpProxies.put(pumpAnchor, pumpAnchor);
        }
        pumpProxyByAnchor = pumpProxies;
        // 反应堆同理：18 格机器的主方块位置上放一个不可见锚点。
        reactorAnchor = findReactorAnchor(captured);
        reactorProxyPos = null;
        if (reactorAnchor != null) {
            reactorProxyPos = reactorAnchor;
            level.setBlock(reactorAnchor, ModShipBlocks.REACTOR_PROXY.get().defaultBlockState(), 3);
        }
        // 引擎：3×2×3 机器的主方块位置放一个不可见锚点。
        engineAnchor = findEngineAnchor(captured);
        engineProxyPos = null;
        if (engineAnchor != null) {
            engineProxyPos = engineAnchor;
            level.setBlock(engineAnchor, ModShipBlocks.ENGINE_PROXY.get().defaultBlockState(), 3);
        }
        // 见 unstaticTicks 的注释：装配出来的船默认是静态体，要连推几 tick 才动得起来
        unstaticTicks = 20;
        VsThruster control = new VsThruster();
        VsThruster attached = null;
        try {
            attached = VsBridge.attach(level, assembled, control);
        } catch (Throwable ignored) {
            // 走下面的 pending 分支，下一 tick 再试
        }
        if (attached == null) {
            pendingShip = assembled;
            pendingThruster = control;
            pendingLevel = level;
            thruster = control;
            ship = assembled;
            state = shipState;
            // 原作中性浮力：压载舱注到全船 7% 水量的相对水位（下一 tick 生效）
            shipState.simulation().requestBallastPrime();
            VsState.activate(core, shipState, VsPilot::isPhysicalHere, VsPilot::applyControl);
            applyControl(shipState);
            sendWaterState(shipState);
            recordAssembled(level, captured, shipState, core, assembled);
            Vec3 pendingPos = VsBridge.position(assembled);
            return String.format(Locale.ROOT,
                    "已收成物理船：%d 格，质量 %.0f/%.0f，中心 (%.1f, %.1f, %.1f)；VS2 尚未加载，推进器下一 tick 补挂",
                    captured.size(), lastMass, expected,
                    pendingPos.x, pendingPos.y, pendingPos.z);
        }
        thruster = attached;
        ship = assembled;
        state = shipState;
        // 原作中性浮力：压载舱注到全船 7% 水量的相对水位（下一 tick 生效）
        shipState.simulation().requestBallastPrime();
        VsState.activate(core, shipState, VsPilot::isPhysicalHere, VsPilot::applyControl);
        applyControl(shipState);
        sendWaterState(shipState);
        recordAssembled(level, captured, shipState, core, assembled);
        Vec3 pos = VsBridge.position(assembled);
        return String.format(Locale.ROOT,
                "已收成物理船：%d 格，质量 %.0f/%.0f，中心 (%.1f, %.1f, %.1f)，%s｜方块级移动/水位已停用，只用刚体",
                captured.size(), lastMass, expected, pos.x, pos.y, pos.z, flags(level, assembled));
    }

    /**
     * 按快照把方块铺回世界（带整数位移），返回真的铺下去的格数。
     *
     * <p>两个地方用：装配失败的回滚（位移 0），和 {@link #dock}（位移 = 船开出去多远）。
     * 空位才铺，被别的方块占着就跳过——绝不覆盖玩家后来放的东西。</p>
     */
    static int restoreBlocks(ServerLevel level, Map<BlockPos, BlockState> snapshot,
                             int dx, int dy, int dz) {
        return restoreBlocks(level, snapshot, dx, dy, dz, Map.of());
    }

    /**
     * 同 {@link #restoreBlocks(ServerLevel, Map, int, int, int)}，并把装配快照里的
     * 方块实体 NBT 一起装回去。
     *
     * <p>只铺 BlockState 会新建一个**空的**方块实体：反应堆燃料、泵的自动/流量、
     * 炉子里的东西、床的主人全没了。用户实测"下潜/靠泊后我放的燃料没了"。
     * 装配时先把 NBT 存下来，靠泊（或在船坞里读当前 BE）再 load 回去。</p>
     */
    static int restoreBlocks(ServerLevel level, Map<BlockPos, BlockState> snapshot,
                             int dx, int dy, int dz,
                             Map<BlockPos, net.minecraft.nbt.CompoundTag> blockEntityTags) {
        int placed = 0;
        for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
            BlockPos target = entry.getKey().offset(dx, dy, dz);
            BlockState existing = level.getBlockState(target);
            if (!existing.isAir() && !existing.canBeReplaced()
                    && !existing.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                continue;
            }
            level.setBlock(target, entry.getValue(), 3);
            if (blockEntityTags != null && !blockEntityTags.isEmpty()) {
                net.minecraft.nbt.CompoundTag tag = blockEntityTags.get(entry.getKey());
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(target);
                if (tag != null && be != null) {
                    try {
                        // 方块整体平移了 (dx,dy,dz)：接线盒绑定坐标也要跟着迁移，
                        // 否则靠泊后设备还去旧位置找接线盒，consumerVoltage() 一直是 0。
                        net.minecraft.nbt.CompoundTag copy = tag.copy();
                        migratePowerBox(copy, dx, dy, dz);
                        be.load(copy);
                        be.setChanged();
                    } catch (Throwable ignored) {
                        // 单个 BE 读不回来不能让整艘船的还原失败
                    }
                }
            }
            placed++;
        }
        return placed;
    }

    /**
     * 靠泊平移后迁移设备 NBT 里的接线盒绑定坐标。
     *
     * <p>泵/引擎存的是接线盒的绝对坐标；方块铺回时整体位移 (dx,dy,dz)，
     * 绑定也要加同一个位移。零位移（装配失败回滚）保持原值。</p>
     */
    private static void migratePowerBox(net.minecraft.nbt.CompoundTag tag,
                                        int dx, int dy, int dz) {
        if (tag == null || !tag.contains("PowerBox")
                || (dx == 0 && dy == 0 && dz == 0)) {
            return;
        }
        tag.putLong("PowerBox",
                BlockPos.of(tag.getLong("PowerBox")).offset(dx, dy, dz).asLong());
    }

    /** 捕获一组方块位置上的方块实体 NBT（装配前/靠泊前调用）。 */
    static Map<BlockPos, net.minecraft.nbt.CompoundTag> captureBlockEntityTags(
            ServerLevel level, java.util.Collection<BlockPos> positions) {
        Map<BlockPos, net.minecraft.nbt.CompoundTag> tags = new java.util.HashMap<>();
        for (BlockPos pos : positions) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                continue;
            }
            try {
                tags.put(pos.immutable(), be.saveWithoutMetadata());
            } catch (Throwable ignored) {
                // 读不出来就跳过这一格
            }
        }
        return tags;
    }

    /**
     * 船坞偏移确定后，把装配前抄的方块实体 NBT 装回船坞里的方块。
     *
     * <p>只在第一次得到偏移时执行；装完就清空，避免之后重新装配串数据。</p>
     */
    private static void applyPendingBlockEntityTags(ServerLevel level) {
        if (level == null || pendingBlockEntityTags.isEmpty()
                || yardOffsetX == Integer.MIN_VALUE) {
            return;
        }
        int applied = 0;
        for (Map.Entry<BlockPos, net.minecraft.nbt.CompoundTag> entry : pendingBlockEntityTags.entrySet()) {
            BlockPos yard = entry.getKey().offset(yardOffsetX, yardOffsetY, yardOffsetZ);
            level.getChunkAt(yard);
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(yard);
            if (be == null) {
                continue;
            }
            try {
                be.load(entry.getValue().copy());
                be.setChanged();
                applied++;
            } catch (Throwable ignored) {
                // 单个 BE 读不回来不能让整船装配失败
            }
        }
        LOGGER.info("[vs] 方块实体 NBT 已装回船坞：{} 格（反应堆燃料/泵设置/储物）", applied);
        pendingBlockEntityTags = java.util.Map.of();
    }

    public static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("barotrauma");

    /** 诊断：船在 VS2 里是不是"已加载/动态"。不动态的船物理不会算。 */
    private static String flags(ServerLevel level, ServerShip target) {
        LoadedServerShip loaded = VsBridge.loaded(level, target);
        if (loaded == null) {
            return "未加载";
        }
        return loaded.isStatic() ? "已加载·静态" : "已加载·动态";
    }

    // ------------------------------------------------------------------ 拆船

    /**
     * 拆回方块。
     *
     * <p>用装配时的方块快照铺回世界，位置 = 原坐标 + 船自装配以来的整数位移。
     * 俯仰/横滚是锁死的（永远平行于海平面，见 {@link VsThruster}），但<b>偏航角
     * 不锁</b>：船可以转向，铺回的方块却还是装配时未旋转的快照。所以船上的
     * 乘客/货物要按船体逆变换映射回快照坐标，不能只加平移。</p>
     */
    public static String dock(ServerLevel level) {
        return dock(level, false);
    }

    /**
     * 拆船后乘客/任务散箱的落点：世界坐标 → 快照船体坐标 → 铺回后的方块坐标。
     *
     * <p>{@code toWorld()} 的逆运算：世界点先乘 {@code shipToWorld⁻¹}，减去
     * {@code positionInShip}、加回装配锚点就得到快照坐标（方块格原点 + 0.5），
     * 再叠加铺回位移 {@code (dx,dy,dz)}。取不到变换时退回纯平移，与旧行为一致。</p>
     */
    private static org.joml.Vector3d dockRestorePoint(
            @Nullable org.joml.Matrix4dc worldToShip, @Nullable org.joml.Vector3dc shipOrigin,
            double x, double y, double z, int dx, int dy, int dz,
            double shiftX, double shiftY, double shiftZ) {
        if (worldToShip == null || shipOrigin == null) {
            return new org.joml.Vector3d(x + shiftX, y + shiftY, z + shiftZ);
        }
        org.joml.Vector3d local = worldToShip.transformPosition(
                new org.joml.Vector3d(x, y, z), new org.joml.Vector3d());
        return local.sub(shipOrigin)
                .add(assembledPos.x, assembledPos.y, assembledPos.z)
                .add(dx, dy, dz);
    }

    /**
     * @param force 调试命令用：不做距离判定、不吸附码头，按当前位置拆回。
     */
    public static String dock(ServerLevel level, boolean force) {
        if (ship == null) {
            return "现在没有物理船";
        }
        // 方块要铺回它原来那个维度，跨维度拆船会把船铺到错误的世界上
        if (lastLevel != null && lastLevel != level) {
            return "物理船在另一个维度（" + lastLevel.dimension().location() + "），去那边再拆";
        }
        ServerShip live = view(level);
        if (live == null) {
            live = ship;
        }
        Vec3 now = VsBridge.position(live);
        int dx = (int) Math.round(now.x - assembledPos.x);
        int dy = (int) Math.round(now.y - assembledPos.y);
        int dz = (int) Math.round(now.z - assembledPos.z);
        // 拆船前记下船体逆变换：航行中偏航角可能非零，而铺回的方块是未旋转的快照。
        // 乘客/货物的相对船体位置必须按船体坐标映射回去，只加平移会把人放错舷侧。
        org.joml.Matrix4dc worldToShip = null;
        org.joml.Vector3dc shipOrigin = null;
        try {
            var transform = live.getTransform();
            worldToShip = new org.joml.Matrix4d(transform.getShipToWorld()).invert();
            shipOrigin = new org.joml.Vector3d(transform.getPositionInShip());
        } catch (Throwable ignored) {
            // 取不到变换就退回纯平移（与旧行为一致）
        }
        Map<BlockPos, BlockState> restore = snapshot;
        BlockPos oldCore = corePos;
        // 靠泊吸附会整体平移船；先把"在船上"的玩家和任务散箱记下来，拆完一起搬。
        // 核心世界位置用当前船体变换算：只加平移的话，偏航后核心相对旋转中心的偏移
        // 会算错（180° 时差 2·cu），任务散箱搜索框可能整体偏出舱室。
        Vec3 oldCoreWorld = oldCore == null ? null : toWorld(oldCore);
        if (oldCoreWorld == null && oldCore != null) {
            oldCoreWorld = new Vec3(oldCore.getX() + dx + 0.5D, oldCore.getY() + dy + 0.5D,
                    oldCore.getZ() + dz + 0.5D);
        }
        List<ServerPlayer> riders = new ArrayList<>();
        List<net.minecraft.world.entity.item.ItemEntity> pinnedCargo = new ArrayList<>();
        if (oldCoreWorld != null) {
            // 不再用固定 32 格球预先排除：核心扫描是每轴 24 格的立方体，
            // 斜角舱室里的玩家距离可能超过 32。直接按“是否真在舱内”判断。
            for (ServerPlayer candidate : level.players()) {
                if (candidate.level() == level && isInsideInterior(level, candidate.position())) {
                    riders.add(candidate);
                }
            }
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(
                    oldCoreWorld.x - 48.0D, oldCoreWorld.y - 48.0D, oldCoreWorld.z - 48.0D,
                    oldCoreWorld.x + 48.0D, oldCoreWorld.y + 48.0D, oldCoreWorld.z + 48.0D);
            for (net.minecraft.world.entity.item.ItemEntity item
                    : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) {
                if (item.getPersistentData().getBoolean(
                        com.opamb.barotrauma.mission.MissionCargo.TAG_PINNED)
                        && isInsideInterior(level, item.position())) {
                    pinnedCargo.add(item);
                }
            }
        }
        int snapX = 0;
        int snapY = 0;
        int snapZ = 0;

        // 0) 靠泊判定：必须把船开到某个哨站对接口附近；靠泊时吸附到码头标准位。
        com.opamb.barotrauma.world.DockingBay.Site dockSite = null;
        if (!force) {
            BlockPos currentCore = oldCore == null ? null : oldCore.offset(dx, dy, dz);
            if (currentCore == null || oldCoreWorld == null) {
                return "不知道船核心在哪，无法判断靠泊位置";
            }
            // 站点查找/距离检查必须用旋转后的真实核心位置：偏航 180° 时
            // “未旋转核心 + 平移”与真实位置能差 2·cu，实际已经贴到对接口也会被判太远。
            BlockPos checkCore = BlockPos.containing(oldCoreWorld);
            dockSite = com.opamb.barotrauma.world.DockingBay.siteAt(checkCore.getX(), checkCore.getZ());
            if (dockSite == null) {
                return "离哨站太远：把船开到哨站对接口附近再靠泊";
            }
            BlockPos dockCore = com.opamb.barotrauma.world.DockingBay.dockCore(
                    dockSite, StarterSub.layout(level));
            double horizontal = Math.hypot(checkCore.getX() - dockCore.getX(),
                    checkCore.getZ() - dockCore.getZ());
            if (horizontal > 16.0D || Math.abs(checkCore.getY() - dockCore.getY()) > 6) {
                return String.format(Locale.ROOT,
                        "离对接口太远（水平 %.1f 格 / 高度差 %d 格），开到对接口 16 格内再靠泊",
                        horizontal, Math.abs(checkCore.getY() - dockCore.getY()));
            }
            // 吸附位移仍按“未旋转的旧核心 + 位移 = 码头核心”算：铺回的方块是
            // 装配快照、偏航不参与，这样拆完方块才正好落在 dockCore。
            snapX = dockCore.getX() - currentCore.getX();
            snapY = dockCore.getY() - currentCore.getY();
            snapZ = dockCore.getZ() - currentCore.getZ();
            dx += snapX;
            dy += snapY;
            dz += snapZ;
            LOGGER.info("[vs] 靠泊吸附：站点=({},{}) 位移=({},{},{})", dockSite.x(), dockSite.z(), dx, dy, dz);
        }

        // 1) 先把刚体从船世界里删掉，但不碰世界方块（VS2 的 deleteShip 参数不是"铺回方块"）
        // 1b) 删船之前把船坞里方块实体的 NBT 抄下来：只铺 BlockState 会新建空实体，
        //     反应堆燃料、泵的自动/流量、储物全都会丢（用户实测"我放的燃料没了"）。
        Map<BlockPos, net.minecraft.nbt.CompoundTag> dockedTags = new java.util.HashMap<>();
        if (yardOffsetX != Integer.MIN_VALUE) {
            for (BlockPos original : restore.keySet()) {
                BlockPos yard = original.offset(yardOffsetX, yardOffsetY, yardOffsetZ);
                level.getChunkAt(yard);
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(yard);
                if (be == null) {
                    continue;
                }
                try {
                    dockedTags.put(original.immutable(), be.saveWithoutMetadata());
                } catch (Throwable ignored) {
                    // 单格失败不影响其它格
                }
            }
        }
        try {
            VsBridge.discard(level, live);
        } catch (Throwable error) {
            return "拆船失败（VS2 删船异常：" + error + "），船还在，方块没动";
        }
        // 2) 按快照铺回世界。
        com.opamb.barotrauma.ship.water.ShipVolumeGuard.deactivate();
                removeProxy(level);
        int placed = restoreBlocks(level, restore, dx, dy, dz, dockedTags);
        pendingBlockEntityTags = java.util.Map.of();
        // 3) 拆回来时那片体积早就被海水灌满了，方块铺回会把水封在舱里——
        //    不排掉的话拆回来的就是"满水的沉船"（隔离自检实测舱内 26 格全是水）
        if (state != null) {
            state.clearWater(level, dx, dy, dz);
        }
        // 靠泊吸附把船整体搬走时，船上的玩家和任务散箱要一起搬：否则船瞬移到码头、
        // 人还留在海里，散箱也会被落下导致货运结算 0 箱（用户实测）。
        // 偏航非零时，光加平移会把"船头的人"放到恢复后船体的侧舷：先按船体逆变换
        // 把位置换回快照坐标（original + 0.5），再映射到铺回后的方块布局。
        double shiftX = dx - (now.x - assembledPos.x);
        double shiftY = dy - (now.y - assembledPos.y);
        double shiftZ = dz - (now.z - assembledPos.z);
        boolean rotated = worldToShip != null && shipOrigin != null;
        if (rotated || Math.abs(shiftX) > 1.0E-6D || Math.abs(shiftY) > 1.0E-6D
                || Math.abs(shiftZ) > 1.0E-6D) {
            for (ServerPlayer rider : riders) {
                org.joml.Vector3d dest = dockRestorePoint(worldToShip, shipOrigin,
                        rider.getX(), rider.getY(), rider.getZ(), dx, dy, dz,
                        shiftX, shiftY, shiftZ);
                rider.teleportTo(level, dest.x, dest.y, dest.z, rider.getYRot(), rider.getXRot());
                rider.setDeltaMovement(Vec3.ZERO);
                rider.fallDistance = 0.0F;
            }
            for (net.minecraft.world.entity.item.ItemEntity item : pinnedCargo) {
                org.joml.Vector3d dest = dockRestorePoint(worldToShip, shipOrigin,
                        item.getX(), item.getY(), item.getZ(), dx, dy, dz,
                        shiftX, shiftY, shiftZ);
                item.setPos(dest.x, dest.y, dest.z);
                item.setDeltaMovement(Vec3.ZERO);
            }
        }
        int skipped = restore.size() - placed;
        // 4) 新的声纳台重新扫描
        BlockPos newCore = oldCore == null ? null : oldCore.offset(dx, dy, dz);
        if (newCore != null && level.getBlockEntity(newCore) instanceof ShipCoreBlockEntity core) {
            ShipState next = core.rescan(level);
            // 飞行期间积累的耐久记录在装配状态里（快照坐标 + 位移 = 世界坐标）。
            // 覆盖重扫结果：重扫只能从 blockstate 恢复 0%，恢复不了 10%~90% 的
            // 部分损伤和加固倍率，直接铺回会"拆一次船、修好的船壳又漏了"。
            if (state != null) {
                next.hullDamage().retain(pos -> false);
                for (Map.Entry<BlockPos, Float> entry : state.hullDamage().sortedEntries()) {
                    next.hullDamage().setHealth(entry.getKey().offset(dx, dy, dz), entry.getValue());
                }
                next.setHullHealthMultiplier(state.hullHealthMultiplier());
                next.setEngineMultiplier(state.engineMultiplier());
                // 飞行期间的水按格搬回方块船：装配坐标 + 靠泊位移 = 世界坐标。
                Map<BlockPos, Double> carried = new java.util.HashMap<>();
                for (Map.Entry<BlockPos, Double> entry : state.waterByCell().entrySet()) {
                    carried.put(entry.getKey().offset(dx, dy, dz), entry.getValue());
                }
                if (!carried.isEmpty()) {
                    next.simulation().seedWaterByCell(carried, next.geometry());
                }
                if (state.simulation().ballastPrimed()) {
                    next.simulation().restoreBallastState(true,
                            state.simulation().ballastNeutral(),
                            state.simulation().ballastMaxPercent());
                }
                core.setChanged();
            }
        }
        // 认领记录作废：船已回到方块形态，耐久随核心 NBT 走。
        try {
            ShipSavedData.get(level).clearAssembled();
        } catch (Throwable ignored) {
            // 存档取不到也不能挡拆船
        }
        VsChunkTickets.clear(level);
        ship = null;
        thruster = null;
        state = null;
        corePos = null;
        snapshot = Map.of();
        initialShipBlocks = 0;
        interiorCells = java.util.List.of();
        knownInteriorCells = java.util.Set.of();
        guardCells = java.util.List.of();
        interiorOffsetX = Integer.MIN_VALUE;
        interiorOffsetY = Integer.MIN_VALUE;
        interiorOffsetZ = Integer.MIN_VALUE;
        assembledPos = Vec3.ZERO;
        bowLatched = false;
        bowLocalX = 1.0D;
        bowLocalZ = 0.0D;
        helmWatchdogTicks = 0;
        unstaticTicks = 0;
        pendingShip = null;
        pendingThruster = null;
        pendingLevel = null;
        yardOffsetX = Integer.MIN_VALUE;
        yardOffsetY = Integer.MIN_VALUE;
        yardOffsetZ = Integer.MIN_VALUE;
        nextYardScanGameTime = Long.MIN_VALUE;
        hullCheckStartGameTime = Long.MIN_VALUE;
        clearFixtureCut();
        com.opamb.barotrauma.network.PowerPanelState.clearPhysical();
        VsState.deactivate();
        // 5) 靠泊成功：船已经回到方块形态，再打开该站舱口并结算本轮出勤。
        if (dockSite != null) {
            com.opamb.barotrauma.world.DockingBay.setHatchOpen(level, dockSite, true);
            com.opamb.barotrauma.campaign.CampaignManager.onDocked(level, dockSite);
        }
        String where = String.format(Locale.ROOT, "%+d,%+d,%+d", dx, dy, dz);
        return skipped == 0
                ? "已拆回世界：铺回 " + placed + " 格（船位移 " + where + "）"
                : "已拆回世界：铺回 " + placed + " 格，" + skipped + " 格被占用没放下（船位移 " + where + "）";
    }

    // ------------------------------------------------------------------ 认领记录（P1 持久化）

    /**
     * 认领失败后的连贯回滚：清掉本 tick 可能已部分发布的运行时状态，<strong>保留存档记录</strong>。
     *
     * <p>{@link #adoptFromSave} 入口保证 {@code ship == null}；此处若非空，说明
     * {@link #adopt} 已经发布到一半（几何重建成功、{@code sendWaterState} 等尾部
     * 步骤抛了异常）。不回滚的话就是"运行时已接管"的半套状态。
     * {@link #resetForServerLifecycle()} 是纯内存清理，不碰任何世界方块。</p>
     *
     * <p>记录必须保留：VS2 船还在（回滚不删刚体），清了记录它就是永久孤儿。
     * 保留的代价是确定性损坏时每次重启都会重试失败一次——可观测、可人工修复
     * （修数据或 {@code /vs delete}），且绝不产生双份船。</p>
     */
    private static void rollbackPartialAdoption() {
        if (ship != null) {
            resetForServerLifecycle();
        }
        pendingAdoption = null;
    }

    /** 装配成功：把认领记录写进世界存档；世界加载后 P1.2 按它接回物理船。 */
    private static void recordAssembled(ServerLevel level, Map<BlockPos, BlockState> captured,
                                        ShipState shipState, BlockPos core, ServerShip assembled) {
        try {
            Vec3 pos = VsBridge.position(assembled);
            ShipSavedData.AssembledShip record = ShipSavedData.AssembledShip.of(
                    assembled.getId(), core, proxyPos, pos, captured, shipState);
            // 历史舱内格必须随认领记录落盘：切掉外壁舱口后几何的 interior 会被裁空，
            // 存档/重启后靠泊吸附还要靠它认出房里的人/任务货。
            record.knownInterior.addAll(knownInteriorCells);
            if (shipState != null) {
                for (ShipGeometry.Compartment compartment : shipState.compartments()) {
                    record.knownInterior.addAll(compartment.interiorCells());
                }
                for (ShipGeometry.Compartment pocket : shipState.geometry().openPockets()) {
                    record.knownInterior.addAll(pocket.interiorCells());
                }
            }
            ShipSavedData.get(level).setAssembled(record);
            // 立刻落盘：只在正常存世界时写的话，退出世界时若被 VS2 的区块卸载 bug
            // 打断（实测 NoSuchElementException），认领记录会丢，物理船变永久孤儿。
            level.getDataStorage().save();
            lastRecordFlushGameTime = level.getGameTime();
        } catch (Throwable error) {
            LOGGER.warn("[vs] 写装配认领记录失败：{}", error.toString());
        }
    }

    /** 认领记录落盘节流（装配时立即写，之后每 30 秒补写一次位置/耐久）。 */
    private static long lastRecordFlushGameTime = Long.MIN_VALUE;

    /** 立刻把当前物理船的认领记录落盘（退出世界前调用；只写数据，不碰方块）。 */
    public static void flushShipRecord(@Nullable ServerLevel level) {
        if (level == null || ship == null || state == null || corePos == null) {
            return;
        }
        try {
            syncAssembledRecord(level);
            level.getDataStorage().save();
            lastRecordFlushGameTime = level.getGameTime();
        } catch (Throwable error) {
            LOGGER.warn("[vs] 认领记录落盘失败：{}", error.toString());
        }
    }

    /** 每 5 秒把快照/耐久的变化（砸掉的格、修回来的格、扣的耐久）同步进认领记录。 */
    public static void syncAssembledRecord(ServerLevel level) {
        if (ship == null || state == null || corePos == null) {
            return;
        }
        try {
            ShipSavedData data = ShipSavedData.get(level);
            ShipSavedData.AssembledShip record = data.assembled();
            if (record == null) {
                recordAssembled(level, snapshot, state, corePos, ship);
                return;
            }
            record.snapshot.clear();
            for (Map.Entry<BlockPos, BlockState> entry : snapshot.entrySet()) {
                record.snapshot.put(entry.getKey().asLong(), entry.getValue());
            }
            record.hullDamage.copyFrom(state.hullDamage());
            record.hullMultiplier = state.hullHealthMultiplier();
            record.engineMultiplier = state.engineMultiplier();
            // 逐格水位/压载标定也随 5 秒同步写进认领记录，不能只靠装配那一刻的快照。
            record.water.clear();
            record.water.putAll(state.waterByCell());
            record.ballastPrimed = state.simulation().ballastPrimed();
            record.ballastNeutral = (float) state.simulation().ballastNeutral();
            record.ballastMax = (float) state.simulation().ballastMaxPercent();
            // 只增不减的舱内格历史：切破洞后新几何裁空的部分也要落盘。
            record.knownInterior.clear();
            record.knownInterior.addAll(knownInteriorCells);
            if (proxyPos != null) {
                record.proxyPos = proxyPos;
            }
            // 位置回退匹配用的"最后已知位置"：只认"已加载"的刚体句柄。
            // view() 的回退句柄（未加载数据船）读到的是陈旧 transform，会把 lastX/Y/Z
            // 写回旧位置；也不能写 record.x/y/z——那是快照坐标空间的装配锚点，动它会
            // 破坏破口的世界→快照映射。
            try {
                LoadedServerShip live = VsBridge.loaded(level, ship);
                if (live != null) {
                    Vec3 now = VsBridge.position(live);
                    record.lastX = now.x;
                    record.lastY = now.y;
                    record.lastZ = now.z;
                }
            } catch (Throwable ignored) {
                // 读不到就保留上一次的已知位置
            }
            data.setDirty();
            // 每 30 秒补写一次盘：位置/耐久只靠退出时的世界保存，遇到客户端崩溃
            // 或 VS2 卸载区块异常就会退回装配那一刻的状态。
            long now = level.getGameTime();
            if (now - lastRecordFlushGameTime >= 600L) {
                lastRecordFlushGameTime = now;
                level.getDataStorage().save();
            }
        } catch (Throwable error) {
            LOGGER.warn("[vs] 同步装配记录失败：{}", error.toString());
        }
    }

    // ------------------------------------------------------------------ 世界加载认领（P1.2）

    /** 待认领记录：世界加载后刚体一时找不到就挂起重试，超时把快照铺回原位。 */
    @Nullable
    private static ShipSavedData.AssembledShip pendingAdoption;
    private static long adoptDeadlineGameTime = Long.MIN_VALUE;
    /** 重试窗口：VS2 船数据是随世界懒加载的，给 30 秒。 */
    private static final long ADOPT_RETRY_TICKS = 600L;
    /** 按 id 找不到时按位置匹配的半径（米）。服务器停机期间船不会动。 */
    private static final double ADOPT_POSITION_TOLERANCE = 16.0D;

    /**
     * 世界加载后按存档记录认领物理船。
     *
     * <p>服务端启动先 {@link #resetForServerLifecycle()} 清空静态，再调用这里：</p>
     * <ol>
     *   <li>按 ship id 找 VS2 刚体；找不到退到"位置最近"匹配；</li>
     *   <li>找到 → {@link #adopt}：从记录重建快照/船状态/推进器/代理方块；</li>
     *   <li>找不到 → 挂起 {@link #pendingAdoption} 重试；超时按丢失处理，
     *       把快照铺回装配位置并清记录，避免留下不可控的野船。</li>
     * </ol>
     */
    public static void adoptFromSave(ServerLevel level) {
        if (!VsSupport.available() || ship != null) {
            return;
        }
        ShipSavedData.AssembledShip record;
        try {
            record = ShipSavedData.get(level).assembled();
        } catch (Throwable error) {
            LOGGER.warn("[vs] 读装配认领记录失败：{}", error.toString());
            return;
        }
        if (record == null) {
            return;
        }
        ServerShip found = VsBridge.ensureShipDataLoaded(level, record.vsShipId);
        if (found == null) {
            // 用"最后已知位置"而不是装配位置：船开出去再停服的话，装配原点早就不是船所在了
            found = VsBridge.nearestShip(level, record.lastX, record.lastY, record.lastZ,
                    ADOPT_POSITION_TOLERANCE);
        }
        if (found != null) {
            // 认领路径抛任何异常都不许带崩启动（这里跑在 ServerStarted 的下一 tick 主线程）：
            // 回滚 + 清记录，降级为无物理船，宁可少一艘船，不要开不起服
            try {
                adopt(level, record, found);
            } catch (Throwable error) {
                LOGGER.warn("[vs] 认领物理船 {} 异常（{}），回滚运行时状态（记录保留，下次重启重试）",
                        record.vsShipId, error.toString());
                rollbackPartialAdoption();
            }
            pendingAdoption = null;
            return;
        }
        if (pendingAdoption == null) {
            pendingAdoption = record;
            adoptDeadlineGameTime = level.getGameTime() + ADOPT_RETRY_TICKS;
            LOGGER.warn("[vs] 有装配记录（shipId={}，快照 {} 格）但 VS2 刚体还没加载，{} 秒内重试",
                    record.vsShipId, record.snapshot.size(), ADOPT_RETRY_TICKS / 20L);
        }
    }

    /** 每秒重试认领；超过期限按"刚体丢失"处理：快照铺回装配位置 + 清记录。 */
    public static void tryAdoptTick(ServerLevel level) {
        if (pendingAdoption == null) {
            return;
        }
        if (ship != null) {
            pendingAdoption = null;
            return;
        }
        ServerShip found = VsBridge.ensureShipDataLoaded(level, pendingAdoption.vsShipId);
        if (found == null) {
            found = VsBridge.nearestShip(level, pendingAdoption.lastX, pendingAdoption.lastY,
                    pendingAdoption.lastZ, ADOPT_POSITION_TOLERANCE);
        }
        if (found != null) {
            // 与 adoptFromSave 相同的兜底：认领异常回滚 + 清记录降级，不带崩 tick 循环
            try {
                adopt(level, pendingAdoption, found);
            } catch (Throwable error) {
                LOGGER.warn("[vs] 认领物理船 {} 异常（{}），回滚运行时状态（记录保留，下次重启重试）",
                        pendingAdoption.vsShipId, error.toString());
                rollbackPartialAdoption();
            }
            pendingAdoption = null;
            return;
        }
        if (level.getGameTime() > adoptDeadlineGameTime) {
            // 铺方块前的终检（三态）：按 id 和按最后已知位置（全量船，含不同 id 的
            // 恢复结果）都确认不存在，才允许铺——查询失败或疑似仍在都继续等。
            // 铺错（双份船）不可逆，拒绝铺（继续等）可逆。
            Boolean gone = VsBridge.shipDefinitelyGone(level, pendingAdoption.vsShipId,
                    pendingAdoption.lastX, pendingAdoption.lastY, pendingAdoption.lastZ,
                    ADOPT_POSITION_TOLERANCE);
            if (gone == null || !gone) {
                adoptDeadlineGameTime = level.getGameTime() + ADOPT_RETRY_TICKS;
                LOGGER.warn("[vs] 超时但 VS2 船数据{}，延长 {} 秒继续等待认领",
                        gone == null ? "查询失败（暂无法判定）" : "仍有疑似存在的船",
                        ADOPT_RETRY_TICKS / 20L);
                return;
            }
            LOGGER.warn("[vs] {} 秒内没等到 VS2 刚体且确认已不存在，按丢失处理",
                    ADOPT_RETRY_TICKS / 20L);
            restoreRecordBlocks(level, pendingAdoption);
            try {
                ShipSavedData.get(level).clearAssembled();
            } catch (Throwable ignored) {
                // 清不掉记录也不影响铺方块
            }
            pendingAdoption = null;
        }
    }

    /**
     * P3 碰撞伤害（退路实现，VS2 2.4.11 没有可订阅的船-地形碰撞回调）：
     * 高速时在刚体 AABB 附近抽样 8 个整数格，命中实心方块就对最近快照格扣 5% 耐久。
     */
    private static void collideDamage(ServerLevel level, Vec3 shipPos) {
        if (ship == null || state == null || snapshot.isEmpty()) {
            return;
        }
        try {
            BlockPos probe = BlockPos.containing(shipPos);
            for (BlockPos offset : SAMPLE_OFFSETS) {
                BlockPos at = probe.offset(offset);
                BlockState solid = level.getBlockState(at);
                if (!solid.isAir() && solid.isSolidRender(level, at)) {
                    BlockPos best = null;
                    double bestDist = Double.MAX_VALUE;
                    for (BlockPos pos : snapshot.keySet()) {
                        double d = pos.distSqr(probe);
                        if (d < bestDist) {
                            bestDist = d;
                            best = pos;
                        }
                    }
                    if (best == null) {
                        return;
                    }
                    BlockState block = snapshot.get(best);
                    float max = state.hullMaxHealth(block);
                    if (max <= 0.0F) {
                        return;
                    }
                    state.hullDamage().applyDamage(best, max * 0.05F, max);
                    level.playSound(null, at, net.minecraft.sounds.SoundEvents.NETHERITE_BLOCK_HIT,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.8F);
                    sendHullDamageState(state);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // 碰撞检测出错不影响物理
        }
    }

    private static final BlockPos[] SAMPLE_OFFSETS = {
            new BlockPos(3, 0, 0), new BlockPos(-3, 0, 0), new BlockPos(0, 2, 0),
            new BlockPos(0, -2, 0), new BlockPos(0, 0, 3), new BlockPos(0, 0, -3),
            new BlockPos(2, 1, 2), new BlockPos(-2, -1, -2)
    };

    /** 兜底：刚体丢了（存档损坏/外部删船），把快照按装配位置铺回世界，不让船凭空消失。 */
    private static void restoreRecordBlocks(ServerLevel level, ShipSavedData.AssembledShip record) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (Map.Entry<Long, BlockState> entry : record.snapshot.entrySet()) {
            blocks.put(BlockPos.of(entry.getKey()), entry.getValue());
        }
        int placed = restoreBlocks(level, blocks, 0, 0, 0);
        LOGGER.warn("[vs] 丢失的物理船已按快照铺回装配位置：{}/{} 格", placed, blocks.size());
    }

    /** 认领成功：从记录重建全部运行时状态（快照、几何、耐久、推进器、代理、干燥区）。 */
    private static void adopt(ServerLevel level, ShipSavedData.AssembledShip record,
                              ServerShip found) {
        Map<BlockPos, BlockState> snap = new LinkedHashMap<>();
        for (Map.Entry<Long, BlockState> entry : record.snapshot.entrySet()) {
            snap.put(BlockPos.of(entry.getKey()), entry.getValue());
        }
        int sonar = 0;
        for (BlockState blockState : snap.values()) {
            // 导航终端 9 段只算一台声纳，否则认领后功耗需求 ×9。
            if (isConsoleMaster(blockState)) {
                sonar++;
            }
        }
        BlockPos core = record.corePos;
        // 用快照当探针重建几何：船坞区块此时可能还没加载，绝不能读世界方块。
        ShipGeometry.Probe probe = new ShipGeometry.Probe() {
            @Override
            public boolean isSolid(BlockPos pos) {
                if (pos.equals(core)) {
                    return false;
                }
                BlockState blockState = snap.get(pos);
                return blockState != null && ModShipBlocks.isSolidForShip(blockState);
            }

            @Override
            public boolean isWater(BlockPos pos) {
                return false;
            }

            @Override
            public boolean isDoorway(BlockPos pos) {
                BlockState blockState = snap.get(pos);
                return blockState != null && ModShipBlocks.isDoorway(blockState);
            }

            @Override
            public boolean isOpenDoorway(BlockPos pos) {
                BlockState blockState = snap.get(pos);
                return blockState != null && ModShipBlocks.isOpenDoorway(blockState);
            }
        };
        ShipGeometry.Result geometry = ShipGeometry.scanShip(core, ShipCoreBlockEntity.RADIUS,
                snap::containsKey, probe, 0,
                // 和 rebuildGeometry 用同一套船壳判定，认领时才能正确排除船外空气。
                pos -> {
                    BlockState st = snap.get(pos);
                    return st != null && (ModShipBlocks.isRepairableHull(st)
                            || ModShipBlocks.isDoorway(st));
                },
                pos -> {
                    BlockState st = snap.get(pos);
                    return st != null && ModShipBlocks.isEquipment(st);
                });
        ShipState rebuilt = new ShipState(geometry,
                new MachineCount(0, 0, 0, 0, 0, 0, sonar));
        // 历史舱内格要在第一次 simulation()/seedWater 前恢复：破口空腔的
        // interiorCells 可能被外部洪泛清空，否则水场会没有格子。
        rebuilt.restoreKnownInterior(record.knownInterior);
        rebuilt.setHullHealthMultiplier(record.hullMultiplier);
        rebuilt.setEngineMultiplier(record.engineMultiplier);
        rebuilt.hullDamage().copyFrom(record.hullDamage);
        // 注意键类型：snap 是 Map<BlockPos,...>，containsKey(long) 会装箱成 Long 恒为 false，
        // 曾把重建出的耐久/几何全部清空。
        rebuilt.hullDamage().retain(snap::containsKey);
        List<BlockPos> breached = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : snap.entrySet()) {
            if (HullBlock.isBreached(entry.getValue())) {
                rebuilt.hullDamage().setHealth(entry.getKey(), 0.0F);
                breached.add(entry.getKey());
            }
        }
        rebuilt.addBreaches(breached);
        // 破口大小与 live 路径（refreshHull :790）同口径：被整格打掉的格 + 快照里 BREACHED 状态的格。
        // 只数 breached 会漏掉打穿的洞——重启后进水比重启前慢，且此后每次重扫的 destroyed 都从 0 起算。
        // initialBlocks<=0 是旧存档（无该字段），按当前快照大小兜底（与原行为一致）。
        int initial = record.initialBlocks > 0 ? record.initialBlocks : snap.size();
        int destroyed = Math.max(0, initial - snap.size());
        rebuilt.simulation().setBreachSize(Math.max(1, destroyed + breached.size()));
        // 读档/重新认领：先注回逐格水位，再决定要不要重新 prime 压载。
        if (!record.water.isEmpty()) {
            rebuilt.simulation().seedWaterByCell(record.water, geometry);
        }
        if (record.ballastPrimed) {
            rebuilt.simulation().restoreBallastState(true,
                    record.ballastNeutral, record.ballastMax);
        } else {
            // 旧存档没有水位：压载重置到原版中性相对水位
            rebuilt.simulation().requestBallastPrime();
        }

        ship = found;
        state = rebuilt;
        snapshot = snap;
        initialShipBlocks = initial;
        corePos = core;
        sonarAnchor = core;
        proxyPos = record.proxyPos;
        // 泵锚点从装配快照里恢复；实际代理方块由 moveProxy 按当前偏移补放。
        pumpAnchors = findPumpAnchors(snap);
        pumpProxyByAnchor = java.util.Map.of();
        reactorAnchor = findReactorAnchor(snap);
        reactorProxyPos = null;
        engineAnchor = findEngineAnchor(snap);
        engineProxyPos = null;
        assembledPos = new Vec3(record.x, record.y, record.z);
        lastLevel = level;
        yardOffsetX = Integer.MIN_VALUE;
        yardOffsetY = Integer.MIN_VALUE;
        yardOffsetZ = Integer.MIN_VALUE;
        nextYardScanGameTime = level.getGameTime();
        hullCheckStartGameTime = level.getGameTime() + 100L;
        interiorCells = new ArrayList<>();
        for (ShipGeometry.Compartment compartment : rebuilt.compartments()) {
            interiorCells.addAll(compartment.cells());
        }
        for (ShipGeometry.Compartment pocket : rebuilt.geometry().openPockets()) {
            interiorCells.addAll(pocket.cells());
        }
        guardCells = buildGuardCells(interiorCells);
        // 历史舱内格从认领记录恢复（旧存档没有这份集合 → 空集，退回按几何重建）。
        // 不能只按 rebuilt 几何重建：切掉外壁舱口后那间房的 interior 已经被裁空。
        knownInteriorCells = java.util.Set.copyOf(record.knownInterior);
        rememberInterior(rebuilt.geometry());
        interiorOffsetX = Integer.MIN_VALUE;
        interiorOffsetY = Integer.MIN_VALUE;
        interiorOffsetZ = Integer.MIN_VALUE;
        waterSyncTimer = 0;
        // VS2 可能把船恢复成静态体：和装配一样连推 20 tick 逼它回动态。
        unstaticTicks = 20;

        VsThruster control = new VsThruster();
        VsThruster attached = null;
        try {
            attached = VsBridge.attach(level, found, control);
        } catch (Throwable ignored) {
            // 下一 tick 走 pending 分支补挂
        }
        if (attached == null) {
            pendingShip = found;
            pendingThruster = control;
            pendingLevel = level;
            thruster = control;
        } else {
            thruster = attached;
        }
        VsState.activate(core, rebuilt, VsPilot::isPhysicalHere, VsPilot::applyControl);
        applyControl(rebuilt);
        // 世界重开/读档直接进船时，船坞区块可能还没加载：VS2 会把玩家移动包
        // 判成 "colliding with unloaded ships" 并取消、把位置打回旧点（实测
        // 260 条/2 秒）。认领时同步把船实际占用的区块拉起来，玩家一进来就能走。
        preloadShipChunks(level);
        sendWaterState(rebuilt);
        LOGGER.info("[vs] 已认领世界里的物理船：shipId={}，快照 {} 格，破口 {} 处，推进器 {}",
                record.vsShipId, snap.size(), breached.size(), attached != null ? "已挂" : "待挂");
        LOGGER.info("[vs] 认领观测：记录里的耐久条目={}，重建后耐久条目={}",
                record.hullDamage.entries().size(), rebuilt.hullDamage().entries().size());
    }

    /**
     * 读数据一律走"已加载"的句柄。
     *
     * <p>{@code assembleToShip} 返回的只是 {@code ShipData}（未加载快照），它的 transform
     * 不随物理更新，读它永远得到装配那一刻的坐标。</p>
     */
    private static ServerShip view(ServerLevel level) {
        if (ship == null) {
            return null;
        }
        ServerShip loaded = VsBridge.loaded(level, ship);
        return loaded != null ? loaded : ship;
    }

    /** 当前活动物理船所在的世界；没有物理船返回 null。 */
    @Nullable
    public static ServerLevel activeLevel() {
        return ship == null ? null : lastLevel;
    }

    /** 数一下船中心周围有多少非空气方块——拆船后应该是满的。 */
    private static int countBlocks(ServerLevel level, Vec3 center) {
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(cx - 10, cy - 6, cz - 10, cx + 10, cy + 6, cz + 10)) {
            if (!level.getBlockState(pos).isAir()) {
                count++;
            }
        }
        return count;
    }

    public static String status() {        ServerLevel level = pendingLevel != null ? pendingLevel : lastLevel;
        if (ship != null && !stillExists(level)) {
            dropStale();
        }
        if (ship == null) {
            return "现在没有物理船｜" + VsBridge.worldStats(level);
        }
        ServerShip live = level == null ? ship : view(level);
        if (live == null) {
            live = ship;
        }
        Vec3 pos = VsBridge.position(live);
        Vec3 vel = VsBridge.velocity(live);
        String flags;
        try {
            flags = live.isStatic() ? "静态" : "动态";
        } catch (Throwable error) {
            flags = "未知";
        }
        int dx = (int) Math.round(pos.x - assembledPos.x);
        int dy = (int) Math.round(pos.y - assembledPos.y);
        int dz = (int) Math.round(pos.z - assembledPos.z);
        return String.format(Locale.ROOT,
                "物理船｜质量 %.0f｜中心 (%.1f, %.1f, %.1f)｜速度 (%.2f, %.2f, %.2f) = %.2f 格/秒"
                        + "｜推力实测 (%.0f, %.0f, %.0f) N｜%s｜%s｜%s"
                        + "｜位移 (%+d, %+d, %+d)｜快照 %d 格｜姿态 %s"
                        + "｜physTick=%d 唤醒=%d 直压=%d",
                VsBridge.mass(live), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, vel.length(),
                thruster == null ? 0 : thruster.lastForceX(),
                thruster == null ? 0 : thruster.lastForceY(),
                thruster == null ? 0 : thruster.lastForceZ(),
                flags, VsBridge.physicsEntity(level, live), VsThruster.probe(),
                dx, dy, dz, snapshot.size(), VsThruster.attitude(),
                VsThruster.calls(), VsThruster.wakes(), VsThruster.directCalls());
    }

    // ------------------------------------------------------------------ 进水诊断

    /**
     * 诊断"装配之后原地是不是变成水坑了"——也就是用户说的"进水"。
     *
     * <p>VS2 装配会把船体方块从世界里挖走，世界里那一块就成了空腔；潜艇在海里，
     * 四周的海水立刻灌进来。透过透明船壳看过去，就是"船里全是水"。
     * 这个方法把船的舱室格子 / 船体格子各数一遍，看它们现在是水、空气还是别的方块。
     * 装配刚完成时应该全空气；过 2 秒再数一次，"水"的格数就是灌进来的海水量。</p>
     */
    public static String floodProbe(ServerLevel level, @Nullable ShipState probe) {
        if (probe == null) {
            return "进水诊断：没有船状态";
        }
        java.util.List<BlockPos> rooms = new java.util.ArrayList<>();
        for (com.opamb.barotrauma.ship.ShipGeometry.Compartment compartment : probe.compartments()) {
            for (BlockPos pos : compartment.cells()) {
                rooms.add(pos);
            }
        }
        int[] room = countStates(level, rooms);
        int[] hull = countStates(level, probe.geometry().hull());
        return String.format(Locale.ROOT,
                "进水诊断：舱室格 %d（水=%d 空气=%d 别的=%d）｜船体格 %d（水=%d 空气=%d 别的=%d）",
                room[0], room[1], room[2], room[3], hull[0], hull[1], hull[2], hull[3]);
    }

    /** 数一批格子里水 / 空气 / 其它各多少，返回 {总数, 水, 空气, 其它}。 */
    private static int[] countStates(ServerLevel level, java.util.Collection<BlockPos> cells) {
        int water = 0;
        int air = 0;
        int other = 0;
        for (BlockPos pos : cells) {
            BlockState state = level.getBlockState(pos);
            if (state.is(net.minecraft.world.level.block.Blocks.WATER)) {
                water++;
            } else if (state.isAir()) {
                air++;
            } else {
                other++;
            }
        }
        return new int[]{cells.size(), water, air, other};
    }

    // ------------------------------------------------------------------ 自检
    /** 自检：装船 + 给推力，等真实 tick 跑完再看速度。 */
    public static String beginSmoke(ServerLevel level, Collection<BlockPos> blocks) {
        return beginSmoke(level, blocks, null, null);
    }

    public static String beginSmoke(ServerLevel level, Collection<BlockPos> blocks,
                                    @Nullable ShipState shipState, @Nullable BlockPos core) {
        // 取样必须在装配之前：装配完方块就不在世界里了
        String sample = sampleBlock(level, blocks);
        String water = waterAt(level, blocks);
        String result = launch(level, blocks, shipState, core);
        if (thruster != null) {
            thruster.setPower(100);
            thruster.setHelm(1, 0, 0);
        }
        return result + " | 推力 +Z 全速 | " + sample + " | " + water
                + " | 装配后立即｜" + floodProbe(level, shipState);
    }

    /** 船体周围是水还是空气——决定它到底该不该有浮力。 */
    private static String waterAt(ServerLevel level, Collection<BlockPos> blocks) {
        int water = 0;
        int air = 0;
        for (BlockPos pos : blocks) {
            BlockPos outside = pos.above();
            BlockState state = level.getBlockState(outside);
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                water++;
            } else if (state.isAir()) {
                air++;
            }
        }
        return "周边 水=" + water + " 空气=" + air;
    }

    /**
     * 抽样看 VS2 认不认我们的方块。
     *
     * <p>自定义方块如果没被任何 {@code BlockStateInfoProvider} 认领，VS2 会把它当成空气：
     * 船就没有碰撞体、也不排水——表现就是"穿模往下掉"。</p>
     */
    private static String sampleBlock(ServerLevel level, Collection<BlockPos> blocks) {
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            try {
                kotlin.Pair<Double, org.valkyrienskies.core.internal.world.chunks.VsiBlockType> info =
                        org.valkyrienskies.mod.common.BlockStateInfo.INSTANCE.get(state);
                if (info == null) {
                    return state.getBlock() + " 在 VS2 里没有物理属性";
                }
                return state.getBlock() + " mass=" + info.getFirst()
                        + " type=" + (info.getSecond() == null ? "null" : info.getSecond().toInt());
            } catch (Throwable error) {
                return "取样失败：" + error;
            }
        }
        return "取样：船体方块已不在世界";
    }

    public static String finishSmoke(ServerLevel level) {
        if (ship == null) {
            return "物理船自检：没有船（装配失败）";
        }
        ServerShip live = view(level);
        if (live == null) {
            live = ship;
        }
        Vec3 pos = VsBridge.position(live);
        Vec3 vel = VsBridge.velocity(live);
        double mass = VsBridge.mass(live);
        String line = String.format(Locale.ROOT,
                "物理船 10s 后｜质量 %.0f｜中心 (%.1f, %.1f, %.1f)｜速度 %.2f 格/秒（%.2f, %.2f, %.2f）",
                mass, pos.x, pos.y, pos.z, vel.length(), vel.x, vel.y, vel.z);
        line += "｜" + VsBridge.diagnose(level, ship) + "｜推进器=" + (thruster != null)
                + "｜物理回调次数=" + VsThruster.calls() + "｜" + VsThruster.probe();
        // 装配之后过了 10 秒，原地灌了多少海水——这就是用户看到的"船里全是水"
        line += "｜10s 后｜" + floodProbe(level, state) + "｜" + VsBridge.claim(live);
        String docked = dock(level, true);
        int placed = countBlocks(level, pos);
        return line + " | " + docked + "，拆回后方块数=" + placed;
    }
}
