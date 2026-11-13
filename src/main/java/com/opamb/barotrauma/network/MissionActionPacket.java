package com.opamb.barotrauma.network;

import com.opamb.barotrauma.campaign.CampaignState;
import com.opamb.barotrauma.registry.ModItems;
import com.opamb.barotrauma.ship.ShipCores;
import com.opamb.barotrauma.ship.ShipState;
import com.opamb.barotrauma.ship.vs.VsPilot;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：商店终端上的操作。
 *
 * <ul>
 *   <li>{@code BUY_REPAIR}——修船（按缺失耐久扣钱）；</li>
 *   <li>{@code BUY_OXYGEN} / {@code BUY_WELDER}——补给；</li>
 *   <li>{@code BUY_UPGRADE} / {@code BUY_ENGINE_UPGRADE}——船体/引擎升级；</li>
 *   <li>{@code BUY_DEEP_PERMIT}——深潜许可。</li>
 * </ul>
 *
 * <p>任务接取已经挪到码头的站务员 NPC（{@link NpcMissionAcceptPacket}），
 * 结算靠泊目的站时自动进行，所以这里只剩商店。</p>
 */
public record MissionActionPacket(int action) {

    public static final int BUY_REPAIR = 1;
    public static final int BUY_OXYGEN = 2;
    public static final int BUY_WELDER = 3;
    public static final int BUY_UPGRADE = 4;
    public static final int BUY_ENGINE_UPGRADE = 5;
    public static final int BUY_DEEP_PERMIT = 6;

    /** 深潜许可售价。 */
    private static final int COST_DEEP_PERMIT = 500;

    private static final int COST_OXYGEN = 40;
    private static final int COST_WELDER = 30;
    private static final int COST_REPAIR_PER_HP = 1;
    private static final int COST_UPGRADE_BASE = 150;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
    }

    public static MissionActionPacket decode(FriendlyByteBuf buf) {
        return new MissionActionPacket(buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            if (!level.dimension().equals(ModDimensions.THE_DEEP)) {
                return;
            }
            CampaignState wallet = CampaignState.wallet(level);
            switch (action) {
                case BUY_REPAIR -> handleRepair(player, level, wallet);
                case BUY_OXYGEN -> handleBuyItem(player, level, wallet, COST_OXYGEN,
                        new ItemStack(ModItems.OXYGEN_TANK.get()));
                case BUY_WELDER -> handleBuyItem(player, level, wallet, COST_WELDER,
                        weldingToolWithFuel());
                case BUY_UPGRADE -> handleUpgrade(player, level, wallet);
                case BUY_ENGINE_UPGRADE -> handleEngineUpgrade(player, level, wallet);
                case BUY_DEEP_PERMIT -> handleBuyItem(player, level, wallet, COST_DEEP_PERMIT,
                        new ItemStack(ModItems.DEEP_PERMIT.get()));
                default -> { /* 未知操作忽略 */ }
            }
        });
        context.setPacketHandled(true);
    }

    /** 修船：按缺失耐久扣共享钱包。 */
    private static void handleRepair(ServerPlayer player, ServerLevel level, CampaignState wallet) {
        var core = ShipCores.nearestCore(level, player.blockPosition(), 24.0D);
        if (core == null || core.state() == null) {
            player.sendSystemMessage(Component.literal("附近 24 格内没有船（把船停在泊位再修）")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        ShipState state = core.state();
        if (!isCurrentShip(state)) {
            player.sendSystemMessage(Component.literal("这个核心不属于你当前的船")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        int missing = 0;
        for (var entry : state.hullDamage().sortedEntries()) {
            missing += Math.round((1.0F - entry.getValue()) * 200.0F);
        }
        if (missing <= 0) {
            player.sendSystemMessage(Component.literal("船体完好，不用修").withStyle(ChatFormatting.GRAY));
            sendState(player, level);
            return;
        }
        int cost = missing * COST_REPAIR_PER_HP;
        if (!wallet.spendCredits(cost)) {
            player.sendSystemMessage(Component.literal(
                    "修船需要 " + cost + " credits，余额不足（当前 " + wallet.credits() + "）")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        for (BlockPos pos : state.hullDamage().entries().keySet().toArray(new BlockPos[0])) {
            state.hullDamage().setHealth(pos, 1.0F);
            state.removeBreach(pos);
        }
        core.setChanged();
        ModNetwork.sendToAll(com.opamb.barotrauma.network.ShipHullDamagePacket.build(
                state, -1L, false, 0.0F, 0.0F, 0.0F));
        player.sendSystemMessage(Component.literal(
                "船体已修复（消耗 " + cost + " credits）").withStyle(ChatFormatting.GREEN));
        sendState(player, level);
    }

    /** 商店购买：先扣钱再入包；背包放不下时退款并提示。 */
    private static void handleBuyItem(ServerPlayer player, ServerLevel level,
                                      CampaignState wallet, int cost, ItemStack stack) {
        if (!wallet.spendCredits(cost)) {
            player.sendSystemMessage(Component.literal(
                    "需要 " + cost + " credits，余额不足（当前 " + wallet.credits() + "）")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        if (!player.getInventory().add(stack)) {
            wallet.addCredits(cost);
            player.sendSystemMessage(Component.literal("购买失败：背包已满，未扣除 credits")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        player.sendSystemMessage(Component.literal(
                "已购买 " + stack.getHoverName().getString() + "（-" + cost + " credits）")
                .withStyle(ChatFormatting.GREEN));
        sendState(player, level);
    }

    /** 商店出售的焊接工具自带一罐满燃料，买来即可使用（油箱仍可单独制作）。 */
    private static ItemStack weldingToolWithFuel() {
        ItemStack welder = new ItemStack(ModItems.WELDING_TOOL.get());
        com.opamb.barotrauma.equipment.ItemSubSlot.set(welder,
                new ItemStack(ModItems.WELDING_FUEL_TANK.get()));
        return welder;
    }

    /** 船体加固：每级 +20%，上限 3 级。 */
    private static void handleUpgrade(ServerPlayer player, ServerLevel level, CampaignState wallet) {
        var core = ShipCores.nearestCore(level, player.blockPosition(), 24.0D);
        if (core == null || core.state() == null) {
            player.sendSystemMessage(Component.literal("附近 24 格内没有船（升级装在船上）")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        ShipState state = core.state();
        if (!isCurrentShip(state)) {
            player.sendSystemMessage(Component.literal("这个核心不属于你当前的船")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        int level1 = Math.round((state.hullHealthMultiplier() - 1.0F) / 0.2F);
        if (level1 >= 3) {
            player.sendSystemMessage(Component.literal("船体加固已满级（+60%）")
                    .withStyle(ChatFormatting.GRAY));
            sendState(player, level);
            return;
        }
        int cost = COST_UPGRADE_BASE * (level1 + 1);
        if (!wallet.spendCredits(cost)) {
            player.sendSystemMessage(Component.literal(
                    "升级需要 " + cost + " credits，余额不足（当前 " + wallet.credits() + "）")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        state.setHullHealthMultiplier(state.hullHealthMultiplier() + 0.2F);
        core.setChanged();
        player.sendSystemMessage(Component.literal(String.format(
                "船体加固 +20%%（当前 ×%.1f，消耗 %d credits）",
                state.hullHealthMultiplier(), cost)).withStyle(ChatFormatting.GREEN));
        sendState(player, level);
    }

    /** 引擎升级：每级 +20% 推力（上限 ×1.6），花费与船体加固同档。 */
    private static void handleEngineUpgrade(ServerPlayer player, ServerLevel level,
                                            CampaignState wallet) {
        var core = ShipCores.nearestCore(level, player.blockPosition(), 24.0D);
        if (core == null || core.state() == null) {
            player.sendSystemMessage(Component.literal("附近 24 格内没有船（升级装在船上）")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        ShipState state = core.state();
        if (!isCurrentShip(state)) {
            player.sendSystemMessage(Component.literal("这个核心不属于你当前的船")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        int current = ShipState.engineLevel(state.engineMultiplier());
        if (current >= ShipState.ENGINE_MAX_LEVEL) {
            player.sendSystemMessage(Component.literal("引擎已满级（×1.6）")
                    .withStyle(ChatFormatting.GRAY));
            sendState(player, level);
            return;
        }
        int cost = COST_UPGRADE_BASE * (current + 1);
        if (!wallet.spendCredits(cost)) {
            player.sendSystemMessage(Component.literal(
                    "升级需要 " + cost + " credits，余额不足（当前 " + wallet.credits() + "）")
                    .withStyle(ChatFormatting.RED));
            sendState(player, level);
            return;
        }
        state.setEngineMultiplier(ShipState.engineMultiplierForLevel(current + 1));
        core.setChanged();
        player.sendSystemMessage(Component.literal(String.format(
                "引擎 +20%%（当前 ×%.1f，消耗 %d credits）",
                state.engineMultiplier(), cost)).withStyle(ChatFormatting.GREEN));
        sendState(player, level);
    }

    /**
     * 24 格内最近的核心是否就是玩家当前的物理船。
     *
     * <p>没有物理船时按最近的方块船处理；有物理船时，附近另一艘方块船的核心
     * 也可能是“最近”的，不能仅凭距离就扣钱/改状态。</p>
     */
    private static boolean isCurrentShip(ShipState state) {
        ShipState physical = VsPilot.state();
        return physical == null || state == physical;
    }

    private static void sendState(ServerPlayer player, ServerLevel level) {
        ModNetwork.sendToPlayer(player, MissionStatePacket.build(player, level));
    }
}
