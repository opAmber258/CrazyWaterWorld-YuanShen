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
        // 先为每名玩家预检，不够安全位置时不改变巡回/任务，也不传送任何人。
        List<Vec3> positions;
        if (VsState.active()) {
            positions = VsState.boardingPositions(deep, players.size());
        } else {
            // 方块船先取真实舱内位置，再把船收成物理船；VS2 装配后船的世界位置不变，
            // 这些落点仍然有效，而且能避开“船坞偏移还没扫出来”的一 tick 空窗。
            positions = blockBoardingPositions(deep, players.size());
            if (positions.size() == players.size() && VsSupport.available()) {
                String launch = launchStarter(deep);
                if (!VsState.active()) {
                    player.sendSystemMessage(Component.translatable("campaign.barotrauma.launch_failed", launch));
                    sync(player);
                    return false;
                }
                BarotraumaMod.LOGGER.info("[campaign] 下潜前装配物理船：{}", launch);
            }
        }
        if (positions.size() != players.size()) {
            BarotraumaMod.LOGGER.info("[campaign] 登船预检失败：active={} support={} positions={}/{}",
                    VsState.active(), VsSupport.available(), positions.size(), players.size());
            player.sendSystemMessage(Component.translatable("campaign.barotrauma.no_interior"));
            sync(player);
            return false;
        }
        BarotraumaMod.LOGGER.info("[campaign] 登船预检通过：active={} support={} positions={}",
                VsState.active(), VsSupport.available(), positions.size());
        if (VsState.active()) {
            BarotraumaMod.LOGGER.info("[campaign] 登船时物理船位置：{}", VsPilot.shipPosition());
        }
        state.takeSnapshot();
        state.selectMissions(ids);
        state.beginTravel(destination, deep.getGameTime());
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

    /** 下潜前把初始方块船收成 VS2 物理船；失败时返回原因，由调用方决定是否中止。 */
    private static String launchStarter(ServerLevel deep) {
        deep.getChunkAt(StarterSub.corePos());
        if (!(deep.getBlockEntity(StarterSub.corePos()) instanceof ShipCoreBlockEntity core)) {
            return "找不到初始潜艇核心";
        }
        ShipState state = core.state() != null ? core.state() : core.rescan(deep);
        if (state == null) {
            return "初始潜艇状态不可用";
        }
        return VsPilot.launch(deep, state.collectBlocks(deep), state, core.getBlockPos());
    }

    private static List<Vec3> blockBoardingPositions(ServerLevel deep, int count) {
        if (VsState.active()) return VsState.boardingPositions(deep, count);
        // 无活动船时只认原始初始艇的真实核心和已存在的安全舱内，绝不生成替身船。
        deep.getChunkAt(StarterSub.corePos());
        if (!(deep.getBlockEntity(StarterSub.corePos()) instanceof com.opamb.barotrauma.blockentity.ShipCoreBlockEntity core))
            return List.of();
        var boat = core.state() == null ? core.rescan(deep) : core.state();
        if (boat == null || boat.compartments().isEmpty() || boat.floodRatio() > 0.001) return List.of();
        List<Vec3> result = new ArrayList<>();
        for (int x = StarterSub.ORIGIN_X + 1; x < StarterSub.ORIGIN_X + StarterSub.SIZE_X - 1; x++) {
            for (int z = StarterSub.ORIGIN_Z + 1; z < StarterSub.ORIGIN_Z + StarterSub.SIZE_Z - 1; z++) {
                BlockPos feet = new BlockPos(x, StarterSub.ORIGIN_Y + 1, z);
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

    /** 自动抵达明确禁用：玩家坐标不代表潜艇物理靠泊。 */
    public static void tick(MinecraftServer server) {}

    /** 管理员调试：直接推进到目的地并自动存档。 */
    public static boolean forceArrive(MinecraftServer server) {
        ServerLevel deep = server.getLevel(ModDimensions.THE_DEEP);
        if (deep == null) return false;
        CampaignState state = CampaignState.wallet(deep);
        if (state.round().phase() != RoundPhase.DIVING) return false;
        // 调试推进，不结算未生成/未完成的任务，也不移动玩家到逻辑地图坐标。
        state.arrive();
        syncAll(server);
        server.saveEverything(false, false, true);
        return true;
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
        if (deep != null) ModNetwork.sendToPlayer(player, CampaignStatePacket.build(CampaignState.wallet(deep)));
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
