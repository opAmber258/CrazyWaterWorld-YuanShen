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
 * 任务进度推进：每秒检查所有在线玩家的进行中任务是否达成（OFFERED/ACTIVE → READY）。
 *
 * <ul>
 *   <li>{@code RECON}：玩家进入目标半径；</li>
 *   <li>{@code SALVAGE}：背包里出现黑匣子；</li>
 *   <li>{@code DELIVERY} / {@code URGENT_DELIVERY}：交给 B 端终端时判定（在终端交付逻辑里），这里不推进。</li>
 * </ul>
 *
 * <p>限时任务（{@code deadlineTicks > 0}）超时进入 FAILED，本轮抵达目的地后重置。</p>
 *
 * <p>状态变化时给任务主人补发一次状态包（其它同步靠终端打开/操作时推送）。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MissionProgress {

    private MissionProgress() {
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
            Mission mission = data.activeMission(player.getUUID());
            if (mission == null) {
                continue;
            }
            // 限时任务超时：ACTIVE/READY 都判 FAILED（紧急运送必须在时限内交付）。
            if ((mission.state() == Mission.State.ACTIVE
                    || mission.state() == Mission.State.READY)
                    && mission.isOverdue(deep.getGameTime())) {
                data.failMission(mission);
                com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                        com.opamb.barotrauma.network.MissionStatePacket.build(player, deep));
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "任务超时失败：未在时限内完成（零报酬，抵达目的地后可再次接取）"),
                        false);
                continue;
            }
            // 运送类已达成但补给箱丢了：退回 ACTIVE（保留认领人与时限），
            // 玩家可回终端放弃后重新接取——accept 会补发补给箱（CodeRabbit 审查意见）。
            if (mission.state() == Mission.State.READY
                    && (mission.type == MissionType.DELIVERY
                        || mission.type == MissionType.URGENT_DELIVERY)
                    && player.getInventory().countItem(
                            com.opamb.barotrauma.registry.ModItems.SUPPLY_CRATE.get().asItem()) == 0) {
                data.demoteToActive(mission);
                com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                        com.opamb.barotrauma.network.MissionStatePacket.build(player, deep));
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "补给箱丢失：任务退回进行中；可在终端放弃后重新接取（补发补给箱）"), false);
                continue;
            }
            if (mission.state() != Mission.State.ACTIVE) {
                continue;
            }
            boolean ready = false;
            switch (mission.type) {
                case RECON -> ready = player.blockPosition().distSqr(mission.target)
                        <= (double) mission.radius * mission.radius;
                case SALVAGE -> ready = player.getInventory()
                        .countItem(ModItems.BLACK_BOX.get().asItem()) > 0;
                // 运送类"达成"= 携带补给箱即可回终端交付；全额/半额由交付终端位置决定。
                case DELIVERY, URGENT_DELIVERY -> ready = player.getInventory()
                        .countItem(com.opamb.barotrauma.registry.ModItems.SUPPLY_CRATE.get().asItem()) > 0;
            }
            if (ready) {
                data.markReady(mission);
                com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                        com.opamb.barotrauma.network.MissionStatePacket.build(player, deep));
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "任务目标达成！回哨站终端交付（奖励 " + mission.reward + " credits）"), false);
            }
        }
    }
}
