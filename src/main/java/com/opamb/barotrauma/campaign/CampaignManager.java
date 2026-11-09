package com.opamb.barotrauma.campaign;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.blockentity.ShipCoreBlockEntity;
import com.opamb.barotrauma.network.CampaignStatePacket;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.ship.vs.VsState;
import com.opamb.barotrauma.ship.vs.VsSupport;
import com.opamb.barotrauma.world.ModDimensions;
import com.opamb.barotrauma.world.MissionInstance;
import com.opamb.barotrauma.world.StarterSub;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** 航线选择到全员登船的服务端事务。副本生成/物理靠泊尚未实现。 */
public final class CampaignManager {
    // 只保留旧测试/诊断 API，不用于抵达判定。
    public static final double ARRIVAL_HORIZONTAL = 64, ARRIVAL_VERTICAL = 32;
    private CampaignManager() {}

    /** 从当前节点出发到相邻节点。 */
    public static boolean startTravel(ServerPlayer player, int destination) {
        ServerLevel deep = player.server.getLevel(ModDimensions.THE_DEEP);
        return deep != null && startTravel(player, CampaignState.wallet(deep).currentNode(), destination, List.of());
    }

    /** 校验相邻边、阶段与全员登船后开始下潜。 */
    public static boolean startTravel(ServerPlayer player, int origin, int destination, List<String> ids) {
        ServerLevel deep = player.server.getLevel(ModDimensions.THE_DEEP);
        if (deep == null) return false;
        CampaignState state = CampaignState.wallet(deep);
        if (player.serverLevel() != deep || !player.isAlive() || player.isSpectator()
                || !CampaignMission.valid(state.round().phase(), state.currentNode(), origin, destination, ids)) {
            BarotraumaMod.LOGGER.info("[campaign] 下潜被拒绝：level={} alive={} spectator={} phase={} current={} origin={} dest={} ids={}",
                    player.serverLevel() == deep, player.isAlive(), player.isSpectator(),
                    state.round().phase(), state.currentNode(), origin, destination, ids);
            player.sendSystemMessage(Component.translatable("campaign.barotrauma.invalid"));
            sync(player);
            return false;
        }
        var players = List.copyOf(player.server.getPlayerList().getPlayers());
        if (players.stream().anyMatch(p -> !p.isAlive())) {
            BarotraumaMod.LOGGER.info("[campaign] 下潜被拒绝：有玩家未复活");
            player.sendSystemMessage(Component.translatable("campaign.barotrauma.not_ready"));
            sync(player);
            return false;
        }
        // 1) 出发站 = 船现在停靠的站（第一次是主站，之后是本轮站立在副本顶部）。
        //    离站前把对接口舱口都关上，否则装成物理船后艇顶是个洞。
        com.opamb.barotrauma.world.DockingBay.Site originSite = currentSite(deep, state);
        com.opamb.barotrauma.world.DockingBay.close(deep);

        // 2) 船还是方块就先在出发站装配成 VS2 刚体；已经物理化就跳过。
        final boolean assembledHere = !VsState.active();
        if (assembledHere) {
            String launch = launchStarter(deep, originSite);
            if (!VsState.active()) {
                player.sendSystemMessage(Component.translatable("campaign.barotrauma.launch_failed", launch));
                sync(player);
                return false;
            }
            BarotraumaMod.LOGGER.info("[campaign] 下潜前装配物理船：{}", launch);
        }

        // 3) 目的地 = 本轮副本顶部的哨站；船传送到哨站正下方的水里（不下潜时不再靠泊），
        //    玩家在物理船里登船，直接往下开进任务洞穴。
        MissionInstance.Anchor anchor = MissionInstance.anchorForRound(state.roundsCompleted());
        com.opamb.barotrauma.world.DockingBay.Site destSite =
                new com.opamb.barotrauma.world.DockingBay.Site(anchor.x(), anchor.z());
        int preloaded = MissionInstance.preload(deep, anchor, 2);
        VsPilot.preloadShipChunks(deep);
        Vec3 shipBefore = VsPilot.shipPosition();
        String moved = VsPilot.teleportShipTo(deep, anchor.spawn());
        if (moved != null) {
            BarotraumaMod.LOGGER.info("[campaign] 副本码头传送失败：{}", moved);
            rollbackShip(deep, shipBefore, assembledHere);
            player.sendSystemMessage(Component.translatable("campaign.barotrauma.instance_failed", moved));
            sync(player);
            return false;
        }
        com.opamb.barotrauma.world.DockingBay.ensureStationHatch(deep, destSite);
        // 本轮任务的航路浮标：声纳上的金色目标点指向它，而不是头顶的副本站。
        com.opamb.barotrauma.world.MissionTarget.ensure(deep, anchor, destination);
        // 传送到副本后停车：别把出发时的油门/自动驾驶带过去，船会自己开走。
        if (VsPilot.state() != null) {
            VsPilot.state().setThrottle(0, 0, 0);
            VsPilot.state().setAutopilotHold(false);
        }
        List<Vec3> positions = VsState.boardingPositions(deep, players.size());
        if (positions.size() != players.size()) {
            BarotraumaMod.LOGGER.info("[campaign] 登船预检失败：support={} positions={}/{}",
                    VsSupport.available(), positions.size(), players.size());
            // 船已经传送过去了但没人能上船：把它送回出发站并拆回方块，别让船自己留在副本里。
            rollbackShip(deep, shipBefore, assembledHere);
            player.sendSystemMessage(Component.translatable("campaign.barotrauma.no_interior"));
            sync(player);
            return false;
        }
        BarotraumaMod.LOGGER.info("[campaign] 副本站点：slot={} 站点=({},{}) 预加载区块={}",
                anchor.slot(), destSite.x(), destSite.z(), preloaded);
        BarotraumaMod.LOGGER.info("[campaign] 登船预检通过：support={} positions={}",
                VsSupport.available(), positions.size());
        state.takeSnapshot();
        state.selectMissions(ids);
        state.beginTravel(destination, deep.getGameTime());
        state.setInstance(anchor.x(), anchor.y(), anchor.z());
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer online = players.get(i);
            Vec3 pos = positions.get(i);
            online.stopRiding();
            online.teleportTo(deep, pos.x, pos.y, pos.z, online.getYRot(), 0);
            online.setDeltaMovement(Vec3.ZERO);
            online.fallDistance = 0;
            BarotraumaMod.LOGGER.info("[campaign] 传送 {} -> {}", online.getGameProfile().getName(), pos);
            online.sendSystemMessage(Component.translatable("campaign.barotrauma.boarded"));
        }
        syncAll(player.server);
        return true;
    }

    /** 船当前停靠的站：本轮副本（如果那里真有方块船）或主站。 */
    private static com.opamb.barotrauma.world.DockingBay.Site currentSite(
            ServerLevel deep, CampaignState state) {
        if (state.round().hasInstance()) {
            com.opamb.barotrauma.world.DockingBay.Site instance =
                    new com.opamb.barotrauma.world.DockingBay.Site(
                            state.round().instanceX(), state.round().instanceZ());
            if (starterCorePos(deep, instance) != null) {
                return instance;
            }
        }
        return com.opamb.barotrauma.world.DockingBay.Site.MAIN;
    }

    /** 下潜流程失败时把已经传过去的物理船送回出发站并拆回方块。 */
    private static void rollbackShip(ServerLevel deep, Vec3 before, boolean assembledHere) {
        // 只有"这次下潜刚装配出来"的船才能回滚；玩家原本就开着的船不能因为一次失败就被拆掉/挪走。
        if (!assembledHere || before == null || !VsState.active()) {
            return;
        }
        VsPilot.teleportShipTo(deep, before);
        String docked = VsPilot.dock(deep, true);
        BarotraumaMod.LOGGER.info("[campaign] 下潜失败回滚：{}", docked);
    }

    /** 下潜前把出发站的方块船收成 VS2 物理船；失败时返回原因，由调用方决定是否中止。 */
    private static String launchStarter(ServerLevel deep, com.opamb.barotrauma.world.DockingBay.Site site) {
        BlockPos corePos = starterCorePos(deep, site);
        if (corePos == null
                || !(deep.getBlockEntity(corePos) instanceof ShipCoreBlockEntity core)) {
            return "找不到初始潜艇核心";
        }
        ShipState state = core.state() != null ? core.state() : core.rescan(deep);
        if (state == null) {
            return "初始潜艇状态不可用";
        }
        return VsPilot.launch(deep, state.collectBlocks(deep), state, core.getBlockPos());
    }

    private static List<Vec3> blockBoardingPositions(ServerLevel deep, int count,
                                                    com.opamb.barotrauma.world.DockingBay.Site site) {
        // 只认这个站方块船的真实核心和已存在的安全舱内，绝不生成替身船。
        BlockPos corePos = starterCorePos(deep, site);
        if (corePos == null
                || !(deep.getBlockEntity(corePos) instanceof com.opamb.barotrauma.blockentity.ShipCoreBlockEntity core))
            return List.of();
        // 核心在 originY+1；旧存档的船还在 -78。
        int originY = corePos.getY() - 1;
        boolean newLayout = StarterSub.newLayout(deep);
        int shipX = site.x() + (newLayout ? StarterSub.NEW_ORIGIN_X : StarterSub.ORIGIN_X);
        int shipZ = site.z() + (newLayout ? StarterSub.NEW_ORIGIN_Z : StarterSub.ORIGIN_Z);
        int sizeX = newLayout ? StarterSub.NEW_SIZE_X : StarterSub.SIZE_X;
        int sizeZ = newLayout ? StarterSub.NEW_SIZE_Z : StarterSub.SIZE_Z;
        var boat = core.state() == null ? core.rescan(deep) : core.state();
        if (boat == null || boat.compartments().isEmpty() || boat.floodRatio() > 0.001) return List.of();
        List<Vec3> result = new ArrayList<>();
        for (int x = shipX + 1; x < shipX + sizeX - 1; x++) {
            for (int z = shipZ + 1; z < shipZ + sizeZ - 1; z++) {
                BlockPos feet = new BlockPos(x, originY + 1, z);
                if (boat.compartments().stream().noneMatch(c -> c.cells().contains(feet) && c.cells().contains(feet.above()))) continue;
                if (!deep.getBlockState(feet).getCollisionShape(deep, feet).isEmpty()
                        || !deep.getBlockState(feet.above()).getCollisionShape(deep, feet.above()).isEmpty()
                        || !deep.getBlockState(feet.below()).isFaceSturdy(deep, feet.below(), net.minecraft.core.Direction.UP)) continue;
                result.add(new Vec3(x + .5, feet.getY() + .05, z + .5));
                if (result.size() == count) return List.copyOf(result);
            }
        }
        return List.of();
    }

    /** 找到这个站停靠的初始潜艇核心；新布局找站外侧的儒艮号，旧存档兼容 -75/-77。 */
    private static BlockPos starterCorePos(ServerLevel deep, com.opamb.barotrauma.world.DockingBay.Site site) {
        if (StarterSub.newLayout(deep)) {
            // 儒艮号只在主站；驾驶室改过后核心可能比标称位置低一格。
            if (com.opamb.barotrauma.world.DockingBay.Site.MAIN.equals(site)) {
                ShipCoreBlockEntity core = StarterSub.starterCore(deep);
                return core == null ? null : core.getBlockPos();
            }
            BlockPos pos = StarterSub.newCorePos(site.x(), site.z());
            deep.getChunkAt(pos);
            return deep.getBlockEntity(pos) instanceof ShipCoreBlockEntity ? pos : null;
        }
        BlockPos pos = new BlockPos(site.x() + 1, StarterSub.ORIGIN_Y + 1, site.z() - 3);
        deep.getChunkAt(pos);
        if (deep.getBlockEntity(pos) instanceof ShipCoreBlockEntity) {
            return pos;
        }
        if (com.opamb.barotrauma.world.DockingBay.Site.MAIN.equals(site)) {
            for (BlockPos legacy : List.of(StarterSub.oldCorePos(), StarterSub.legacyCorePos())) {
                deep.getChunkAt(legacy);
                if (deep.getBlockEntity(legacy) instanceof ShipCoreBlockEntity) {
                    return legacy;
                }
            }
        }
        return null;
    }

    /** 自动抵达明确禁用：玩家坐标不代表潜艇物理靠泊。 */
    public static void tick(MinecraftServer server) {}

    /** 管理员调试：直接推进到目的地并自动存档。 */
    public static boolean forceArrive(MinecraftServer server) {
        ServerLevel deep = server.getLevel(ModDimensions.THE_DEEP);
        if (deep == null) return false;
        CampaignState state = CampaignState.wallet(deep);
        if (state.round().phase() != RoundPhase.DIVING) return false;
        settleArrival(deep, state);
        syncAll(server);
        server.saveEverything(false, false, true);
        return true;
    }

    /** 物理船在实际哨站靠泊成功：正在出勤就当作抵达，推进一轮。 */
    public static void onDocked(ServerLevel deep, com.opamb.barotrauma.world.DockingBay.Site site) {
        CampaignState state = CampaignState.wallet(deep);
        var round = state.round();
        // 只有"本轮出勤的目标副本站"才算抵达；出发站/路上经过的站靠泊不能推进战役节点。
        if (round.phase() != RoundPhase.DIVING || !round.hasInstance()
                || site.x() != round.instanceX() || site.z() != round.instanceZ()) {
            return;
        }
        settleArrival(deep, state);
        syncAll(deep.getServer());
        deep.getServer().saveEverything(false, false, true);
    }

    /** 抵达结算：推节点、记一轮、把超时失败的任务放回池子。 */
    private static void settleArrival(ServerLevel deep, CampaignState state) {
        state.arrive();
        int resetFailed = com.opamb.barotrauma.data.MissionSavedData.get(deep).resetFailed();
        if (resetFailed > 0) {
            BarotraumaMod.LOGGER.info("[campaign] 抵达目的地，重置 {} 条失败任务", resetFailed);
        }
    }

    /** 仅恢复战役核心数据，绝非船/玩家/物品的完整回档。 */
    public static boolean failAndRollback(ServerLevel level) {
        CampaignState state = CampaignState.wallet(level);
        if (state.round().phase() != RoundPhase.DIVING || !state.hasSnapshot()) return false;
        state.failRound();
        boolean restored = state.rollback();
        syncAll(level.getServer());
        return restored;
    }

    /** 把战役状态推给单个玩家。 */
    public static void sync(ServerPlayer player) {
        ServerLevel deep = player.server.getLevel(ModDimensions.THE_DEEP);
        if (deep == null) {
            return;
        }
        CampaignState state = CampaignState.wallet(deep);
        // 旧存档兼容：已经在航行中的轮次没有航路浮标，登录时补建一次（幂等）。
        var round = state.round();
        if (round.phase() == RoundPhase.DIVING && round.hasInstance()) {
            com.opamb.barotrauma.world.MissionTarget.ensure(deep,
                    com.opamb.barotrauma.world.MissionTarget.anchorForInstance(
                            round.instanceX(), round.instanceZ()),
                    round.destination());
        }
        ModNetwork.sendToPlayer(player, CampaignStatePacket.build(state));
    }

    /** 把战役状态推给所有在线玩家。 */
    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) sync(online);
    }

    public static boolean near(BlockPos pos, BlockPos anchor) {
        double dx = pos.getX() - anchor.getX(), dz = pos.getZ() - anchor.getZ();
        return dx * dx + dz * dz <= ARRIVAL_HORIZONTAL * ARRIVAL_HORIZONTAL
                && Math.abs(pos.getY() - anchor.getY()) <= ARRIVAL_VERTICAL;
    }

    public static Component name(int node) {
        MapNode n = CampaignMap.node(node);
        if (n == null) {
            return Component.literal("#" + node);
        }
        return Component.translatable("campaign.barotrauma.node." + n.id())
                .append(Component.literal(" #" + n.id()));
    }
}
