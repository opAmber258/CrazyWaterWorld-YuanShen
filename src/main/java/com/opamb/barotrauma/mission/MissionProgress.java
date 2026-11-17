package com.opamb.barotrauma.mission;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.data.MissionSavedData;
import com.opamb.barotrauma.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
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
                // 采矿任务已经 READY 后又把矿物交掉/丢掉：退回 ACTIVE，靠泊时不能按达成结算。
                if (mission.state() == Mission.State.READY && mission.type == MissionType.MINERAL
                        && mission.mineralCount() > 0
                        && countMineral(player, mission.mineralItem()) < mission.mineralCount()) {
                    data.markActive(mission);
                    changed = true;
                }
                if (mission.state() != Mission.State.ACTIVE) {
                    continue;
                }
                boolean ready = switch (mission.type) {
                    case RECON -> player.blockPosition().distSqr(mission.target)
                            <= (double) mission.radius * mission.radius;
                    case SALVAGE -> countWithBag(player, ModItems.BLACK_BOX.get().asItem()) > 0;
                    // 采矿：背包里（含装备背包）采够指定矿物就 READY，靠泊后结算。
                    case MINERAL -> mission.mineralCount() > 0
                            && countMineral(player, mission.mineralItem()) >= mission.mineralCount();
                    // 货运不在途中判定，靠泊时自动结算（CampaignManager.settleArrival）。
                    case DELIVERY, URGENT_DELIVERY -> false;
                };
                if (ready) {
                    data.markReady(mission);
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "任务 #" + mission.id + " 目标达成！靠泊目的站后自动结算（"
                                    + mission.reward + " 马克）"), false);
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

    /** 采矿任务：只数"从矿脉里采出来的"矿物（创造模式拿的同种矿物不算）。 */
    public static int countMineral(ServerPlayer player, String itemId) {
        Item item = itemById(itemId);
        if (item == null) {
            return 0;
        }
        int count = countMined(player.getInventory().items, item);
        count += countMined(player.getInventory().offhand, item);
        for (ItemStack stack : com.opamb.barotrauma.equipment.BagManager.contents(player)) {
            if (stack.is(item) && com.opamb.barotrauma.block.MineralVeinBlock.isMined(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countMined(java.util.List<ItemStack> stacks, Item item) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (stack.is(item) && com.opamb.barotrauma.block.MineralVeinBlock.isMined(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 结算时从背包/装备背包里扣掉采出来的矿物；返回实际扣掉的数量。 */
    public static int consumeMineral(ServerPlayer player, String itemId, int amount) {
        Item item = itemById(itemId);
        if (item == null || amount <= 0) {
            return 0;
        }
        int remaining = amount;
        remaining -= shrinkMined(player.getInventory().items, item, remaining);
        remaining -= shrinkMined(player.getInventory().offhand, item, remaining);
        if (remaining > 0) {
            for (ItemStack stack : com.opamb.barotrauma.equipment.BagManager.contents(player)) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.is(item) && com.opamb.barotrauma.block.MineralVeinBlock.isMined(stack)) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
            com.opamb.barotrauma.equipment.BagManager.commit(player);
        }
        return amount - remaining;
    }

    private static int shrinkMined(java.util.List<ItemStack> stacks, Item item, int amount) {
        int taken = 0;
        for (ItemStack stack : stacks) {
            if (taken >= amount) {
                break;
            }
            if (stack.is(item) && com.opamb.barotrauma.block.MineralVeinBlock.isMined(stack)) {
                int take = Math.min(amount - taken, stack.getCount());
                stack.shrink(take);
                taken += take;
            }
        }
        return taken;
    }

    private static Item itemById(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /** 矿物显示名（服务端聊天消息用；服务端语言固定 en_us，所以这三种直接写中文）。 */
    public static String mineralName(String itemId) {
        String id = itemId == null ? "" : itemId;
        return switch (id) {
            case "barotrauma:quartz" -> "石英";
            case "barotrauma:pyromorphite" -> "磷氯铅矿";
            case "barotrauma:uraniumore" -> "铀矿";
            default -> {
                Item item = itemById(id);
                yield item == null ? id : item.getDescription().getString();
            }
        };
    }
}
