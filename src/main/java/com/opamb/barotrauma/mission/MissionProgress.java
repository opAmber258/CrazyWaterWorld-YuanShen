package com.opamb.barotrauma.mission;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.data.MissionSavedData;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 任务进度推进：每秒检查所有在线玩家的每条进行中任务（一趟最多 3 条）。
 *
 * <ul>
 *   <li>{@code RECON}：玩家进入目标半径 → READY；</li>
 *   <li>{@code SALVAGE}：背包里出现黑匣子 → READY；</li>
 *   <li>{@code DELIVERY} / {@code URGENT_DELIVERY}：不在这里判定——货运走原版规则，
 *       靠泊目的站时由 {@link MissionCargo} 统计船上还在的箱子后自动结算。</li>
 * </ul>
 *
 * <p>所有任务都在靠泊目的站时统一结算（{@code CampaignManager.settleArrival}）。
 * 限时任务超时进入 FAILED，并清掉船上还没交的货运；本轮抵达目的地后重置回可接取。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MissionProgress {

    private MissionProgress() {
    }

    /** 玩家进服时同步一次任务状态：海图/HUD 的客户端缓存否则要等到开终端才有数据。 */
    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel deep = player.server.getLevel(com.opamb.barotrauma.world.ModDimensions.THE_DEEP);
        if (deep == null) {
            return;
        }
        com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                com.opamb.barotrauma.network.MissionStatePacket.build(player, deep));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20L != 0L) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerLevel deep = server.getLevel(com.opamb.barotrauma.world.ModDimensions.THE_DEEP);
        if (deep == null) {
            return;
        }
        MissionSavedData data = MissionSavedData.get(deep);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean changed = false;
            for (Mission mission : data.activeMissions(player.getUUID())) {
                // 限时任务超时：ACTIVE/READY 都判 FAILED（紧急运送必须在时限内交付）。
                if ((mission.state() == Mission.State.ACTIVE
                        || mission.state() == Mission.State.READY)
                        && mission.isOverdue(deep.getGameTime())) {
                    data.failMission(mission);
                    // 超时的货运把船上/身上的任务箱清掉（原作任务结束会移除所有货物）。
                    MissionCargo.clear(deep, player, mission);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "任务 #" + mission.id
                                    + " 超时失败：未在时限内完成（零报酬，抵达目的地后可再次接取）"),
                            false);
                    changed = true;
                    continue;
                }
                if (mission.state() != Mission.State.ACTIVE) {
                    continue;
                }
                boolean ready = switch (mission.type) {
                    case RECON -> player.blockPosition().distSqr(mission.target)
                            <= (double) mission.radius * mission.radius;
                    case SALVAGE -> countWithBag(player, ModItems.BLACK_BOX.get().asItem()) > 0;
                    // 货运不在途中判定，靠泊时自动结算（CampaignManager.settleArrival）。
                    case DELIVERY, URGENT_DELIVERY -> false;
                };
                if (ready) {
                    data.markReady(mission);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "任务 #" + mission.id + " 目标达成！靠泊目的站后自动结算（"
                                    + mission.reward + " mk）"), false);
                    changed = true;
                }
            }
            if (changed) {
                com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                        com.opamb.barotrauma.network.MissionStatePacket.build(player, deep));
            }
        }
    }

    /** 热键栏/主背包 + 装备背包里的物品数量（装备背包内容不在 player inventory 里）。 */
    private static int countWithBag(ServerPlayer player, net.minecraft.world.item.Item item) {
        int count = player.getInventory().countItem(item);
        for (ItemStack stack : com.opamb.barotrauma.equipment.BagManager.contents(player)) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
