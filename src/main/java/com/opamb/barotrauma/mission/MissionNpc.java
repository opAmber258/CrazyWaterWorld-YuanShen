package com.opamb.barotrauma.mission;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.data.MissionSavedData;
import com.opamb.barotrauma.entity.NpcEntity;
import com.opamb.barotrauma.network.ModNetwork;
import com.opamb.barotrauma.registry.ModEntities;
import com.opamb.barotrauma.world.DockingBay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 站务员 NPC：站在哨站里发任务（原作里任务由站点/船员发布，海图上再挑）。
 *
 * <p>生成策略：每 2 秒扫一遍在线玩家，玩家在哪个站的范围内（{@link DockingBay#siteAt}），
 * 就确保那个站的固定位置有一个 NPC；已有就不重复生成。这样第一次进世界、
 * 靠泊、读档都能自动补齐，不需要在世界生成里塞实体。</p>
 */
public final class MissionNpc {

    /**
     * 站务员候选站位（站内局部坐标，y 是站内地板上的那一层）。
     *
     * <p>不能写死一个点：教学站在 |x|=6 有纵向隔墙、研究站在 |z|=7 还有横向隔墙，
     * 写死的点常常正好落在墙里（用户实测"哨兵一个都没有"就是这个）。这里按顺序
     * 找第一个"脚下实心、站位和头顶都是空气"的点。</p>
     */
    private static final int[][] SPOT_CANDIDATES = {
            {4, 3}, {3, 3}, {-4, 3}, {4, -3}, {3, -3}, {-3, 3}, {2, 2}, {-2, 2}};
    /** 站内地面层（{@code OutpostBuilder.INNER_MIN_Y}）。 */
    private static final int SPOT_Y = -62;

    private MissionNpc() {
    }

    /** 这个站能放 NPC 的位置；站还没生成好时返回 null。 */
    @Nullable
    public static BlockPos spotFor(ServerLevel level, DockingBay.Site site) {
        for (int[] candidate : SPOT_CANDIDATES) {
            BlockPos pos = site.at(candidate[0], SPOT_Y, candidate[1]);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockState(pos.below()).isSolid()
                    && level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()) {
                return pos;
            }
        }
        return null;
    }

    /** 每 2 秒调用：玩家所在站没有站务员就生成一个。 */
    public static void ensureStations(ServerLevel level) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.serverLevel() != level) {
                continue;
            }
            DockingBay.Site site = DockingBay.siteAt(player.getBlockX(), player.getBlockZ());
            if (site == null) {
                continue;
            }
            BlockPos spot = spotFor(level, site);
            if (spot == null) {
                continue;
            }
            if (!level.getEntitiesOfClass(NpcEntity.class, new AABB(spot).inflate(32.0D)).isEmpty()) {
                continue;
            }
            NpcEntity npc = ModEntities.NPC.get().create(level);
            if (npc == null) {
                continue;
            }
            double x = spot.getX() + 0.5D;
            double z = spot.getZ() + 0.5D;
            double faceX = site.x() + 0.5D - x;
            double faceZ = site.z() + 0.5D - z;
            float yaw = (float) (Math.atan2(-faceX, faceZ) * 180.0D / Math.PI);
            npc.moveTo(x, spot.getY(), z, yaw, 0.0F);
            npc.setSkin(skinFor(site));
            npc.finalizeSpawn(level, level.getCurrentDifficultyAt(spot),
                    MobSpawnType.STRUCTURE, null, null);
            level.addFreshEntity(npc);
            BarotraumaMod.LOGGER.info("[missions] 站务员已生成：站({}, {}) 位置 {} 皮肤 {}",
                    site.x(), site.z(), spot, npc.skin());
        }
    }

    /** 同一个站永远同一个皮肤（站坐标散列到皮肤表）。 */
    private static String skinFor(DockingBay.Site site) {
        return NpcEntity.SKINS[Math.floorMod(site.x() / 64 + site.z() * 7, NpcEntity.SKINS.length)];
    }

    /**
     * 右键站务员：先打招呼（对话界面），不直接发任务。
     *
     * <p>照原作：和站务员对话 → 选“有什么活儿”拿到具体委托 → 打开海图选目的地、
     * 勾任务 → 下潜。任务在对话里发放并绑定目的地。</p>
     */
    public static void talk(ServerPlayer player, NpcEntity npc) {
        ServerLevel level = player.serverLevel();
        int node = com.opamb.barotrauma.campaign.CampaignState.wallet(level).currentNode();
        com.opamb.barotrauma.campaign.CampaignManager.sync(player);
        ModNetwork.sendToPlayer(player,
                com.opamb.barotrauma.network.MissionStatePacket.build(player, level));
        ModNetwork.sendToPlayer(player,
                new com.opamb.barotrauma.network.NpcDialoguePacket(
                        com.opamb.barotrauma.network.NpcDialoguePacket.GREETING, node, new int[0]));
    }

    /** 对话里选“有什么活儿”：本轮第一次访问最多发 3 条报价，之后只回已有的。 */
    public static void askForWork(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // 防作弊：必须真的站在站务员旁边才能要活儿。
        if (level.getEntitiesOfClass(NpcEntity.class,
                player.getBoundingBox().inflate(8.0D)).isEmpty()) {
            return;
        }
        int node = com.opamb.barotrauma.campaign.CampaignState.wallet(level).currentNode();
        MissionSavedData data = MissionSavedData.get(level);
        List<Integer> granted = java.util.List.of();
        if (data.beginVisit(node)) {
            granted = data.grantBatch(node, destinationsFor(node),
                    MissionSavedData.GRANTS_PER_VISIT);
        }
        List<Integer> offers = data.offerIds(node);
        int phase = !granted.isEmpty()
                ? com.opamb.barotrauma.network.NpcDialoguePacket.GRANTED
                : offers.isEmpty()
                ? com.opamb.barotrauma.network.NpcDialoguePacket.NO_MISSIONS
                : com.opamb.barotrauma.network.NpcDialoguePacket.ALREADY;
        List<Integer> shown = granted.isEmpty() ? offers : granted;
        int[] ids = shown.stream().mapToInt(Integer::intValue).toArray();
        ModNetwork.sendToPlayer(player,
                com.opamb.barotrauma.network.MissionStatePacket.build(player, level));
        ModNetwork.sendToPlayer(player,
                new com.opamb.barotrauma.network.NpcDialoguePacket(phase, node, ids));
    }

    /** 本站能发往的目的地：优先哨站（货运要在站里结算），没有哨站再退回所有相邻节点。 */
    private static List<Integer> destinationsFor(int node) {
        List<Integer> outposts = new java.util.ArrayList<>();
        for (int next : com.opamb.barotrauma.campaign.CampaignMap.neighbors(node)) {
            com.opamb.barotrauma.campaign.MapNode target =
                    com.opamb.barotrauma.campaign.CampaignMap.node(next);
            if (target != null && target.type() == com.opamb.barotrauma.campaign.NodeType.OUTPOST) {
                outposts.add(next);
            }
        }
        return outposts.isEmpty()
                ? new java.util.ArrayList<>(com.opamb.barotrauma.campaign.CampaignMap.neighbors(node))
                : outposts;
    }

    /** 海图勾选的这批报价能不能接（站里挂着 / 目的地一致 / 还没被接走 / 总条数不超 3）。 */
    public static boolean canBook(ServerLevel level, ServerPlayer player,
                                  int origin, int destination, List<Integer> ids) {
        if (ids.size() > MissionSavedData.MAX_ACTIVE
                || new java.util.HashSet<>(ids).size() != ids.size()) {
            return false;
        }
        MissionSavedData data = MissionSavedData.get(level);
        if (data.activeMissions(player.getUUID()).size() + ids.size()
                > MissionSavedData.MAX_ACTIVE) {
            return false;
        }
        for (int id : ids) {
            Mission mission = data.missions().get(id);
            if (mission == null || mission.state() != Mission.State.OFFERED
                    || mission.assignedTo() != null
                    || data.offerDestination(origin, id) != destination) {
                return false;
            }
        }
        return true;
    }

    /**
     * 接取客户端勾选的任务（最多 {@link MissionSavedData#MAX_ACTIVE} 条）。
     * 货运类接取时立刻把货装到船上货架。
     */
    public static void accept(ServerPlayer player, int origin, int destination,
                              List<Integer> ids) {
        ServerLevel level = player.serverLevel();
        MissionSavedData data = MissionSavedData.get(level);
        if (ids.size() > MissionSavedData.MAX_ACTIVE) {
            player.sendSystemMessage(Component.literal("一趟最多接 "
                    + MissionSavedData.MAX_ACTIVE + " 条任务").withStyle(net.minecraft.ChatFormatting.RED));
            return;
        }
        int accepted = 0;
        for (int id : ids) {
            if (!data.accept(player.getUUID(), id, origin, destination, level.getGameTime())) {
                continue;
            }
            Mission mission = data.missions().get(id);
            if (mission == null) {
                continue;
            }
            accepted++;
            if (mission.type == MissionType.DELIVERY || mission.type == MissionType.URGENT_DELIVERY) {
                boolean hasShelf = !MissionCargo.shelves(level, player).isEmpty();
                int crates = MissionCargo.spawn(level, player, mission);
                if (crates <= 0) {
                    data.abandon(player.getUUID(), id);
                    player.sendSystemMessage(Component.literal(
                                    "任务 #" + id + " 装货失败：船上找不到能放货的箱子货架")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                    continue;
                }
                player.sendSystemMessage(Component.literal(
                                "已接任务 #" + id + "：货物已装船 " + crates + " 箱（每箱 "
                                        + mission.cargoPerCrate() + " mk），靠泊目的站自动结算"
                                        + (hasShelf ? "" : "；船上没有货架，箱子散放在脚边"))
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
            } else {
                player.sendSystemMessage(Component.literal(
                                "已接任务 #" + id + "：" + describe(mission))
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }
        if (accepted == 0) {
            player.sendSystemMessage(Component.literal(
                            "没有接到任务：一趟最多 " + MissionSavedData.MAX_ACTIVE
                                    + " 条，或这几条已经被接走")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        data.setDirty();
        ModNetwork.sendToPlayer(player,
                com.opamb.barotrauma.network.MissionStatePacket.build(player, level));
    }

    private static String describe(Mission mission) {
        return switch (mission.type) {
            case SALVAGE -> "打捞黑匣子（目标 "
                    + mission.target.getX() + ", " + mission.target.getY() + ", "
                    + mission.target.getZ() + "）";
            case RECON -> "前往目标点侦察";
            case DELIVERY, URGENT_DELIVERY -> "货运";
        };
    }
}
