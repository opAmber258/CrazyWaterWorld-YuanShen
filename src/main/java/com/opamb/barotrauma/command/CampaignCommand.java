package com.opamb.barotrauma.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.opamb.barotrauma.campaign.CampaignManager;
import com.opamb.barotrauma.campaign.CampaignMap;
import com.opamb.barotrauma.campaign.CampaignState;
import com.opamb.barotrauma.campaign.MapNode;
import com.opamb.barotrauma.campaign.NodeType;
import com.opamb.barotrauma.campaign.RoundPhase;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /barotrauma campaign ...}：v1 战役骨架的调试与验收命令。
 *
 * <p>只做服务端状态推进；地图/目的地 UI 在下一个里程碑接到任务终端。</p>
 */
public final class CampaignCommand {

    private CampaignCommand() {
    }

    /** 挂到现有 {@code /barotrauma} 根命令下。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("campaign")
                .then(Commands.literal("status").executes(CampaignCommand::status))
                .then(Commands.literal("map").executes(CampaignCommand::map))
                .then(Commands.literal("travel").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("node",
                                        IntegerArgumentType.integer(0, CampaignMap.nodes().size() - 1))
                                .executes(CampaignCommand::travel)))
                .then(Commands.literal("arrive").requires(source -> source.hasPermission(2)).executes(CampaignCommand::arrive))
                .then(Commands.literal("fail").requires(source -> source.hasPermission(2)).executes(CampaignCommand::fail))
                .then(Commands.literal("credits").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(-100000, 100000))
                                .executes(CampaignCommand::credits)));
    }

    private static ServerLevel level(CommandSourceStack source) {
        // 战役存档固定挂在深海维度，不跟玩家当前所在维度走。
        return source.getServer().getLevel(ModDimensions.THE_DEEP);
    }

    /** 输出当前节点、钱包、阶段与已访问节点。 */
    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = level(source);
        if (level == null) {
            source.sendFailure(Component.literal("深海维度未加载"));
            return 0;
        }
        CampaignState state = CampaignState.wallet(level);
        source.sendSuccess(() -> Component.literal("== 战役状态 ==").withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("当前节点：").append(CampaignManager.name(state.currentNode())), false);
        source.sendSuccess(() -> Component.literal("共享资金：" + state.credits() + " credits"), false);
        source.sendSuccess(() -> {
            var line = Component.literal("阶段：" + state.round().phase());
            if (state.round().phase() == RoundPhase.DIVING) {
                line.append(Component.literal("（→ "))
                        .append(CampaignManager.name(state.round().destination()))
                        .append(Component.literal("）"));
            }
            return line;
        }, false);
        source.sendSuccess(() -> Component.literal("已完成巡回：" + state.roundsCompleted()), false);
        source.sendSuccess(() -> Component.literal(state.round().hasInstance()
                ? "任务海域：" + state.round().instanceX() + ", " + state.round().instanceY()
                        + ", " + state.round().instanceZ()
                : "任务海域：未生成"), false);
        source.sendSuccess(() -> Component.literal("已访问节点：" + state.visited()), false);
        return 1;
    }

    /** 输出节点图与相邻关系。 */
    private static int map(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = level(source);
        if (level == null) {
            source.sendFailure(Component.literal("深海维度未加载"));
            return 0;
        }
        CampaignState state = CampaignState.wallet(level);
        source.sendSuccess(() -> Component.literal("== 战役地图（★=当前）==").withStyle(ChatFormatting.AQUA), false);
        for (MapNode node : CampaignMap.nodes()) {
            source.sendSuccess(() -> {
                var line = Component.literal(node.id() == state.currentNode() ? "★ " : "  ");
                line.append(CampaignManager.name(node.id()));
                line.append(Component.literal((node.type() == NodeType.OUTPOST ? " [前哨站]" : " [航路点]")
                        + " @ " + node.anchor().getX() + "," + node.anchor().getY() + ","
                        + node.anchor().getZ() + " → " + node.neighbors()));
                return line;
            }, false);
        }
        return 1;
    }

    private static int travel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int destination = IntegerArgumentType.getInteger(ctx, "node");
        return CampaignManager.startTravel(player, destination) ? 1 : 0;
    }

    private static int arrive(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!CampaignManager.forceArrive(source.getServer())) {
            source.sendFailure(Component.literal("当前没有进行中的巡回"));
            return 0;
        }
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = level(source);
        if (level == null) {
            source.sendFailure(Component.literal("深海维度未加载"));
            return 0;
        }
        if (!CampaignManager.failAndRollback(level)) {
            source.sendFailure(Component.literal("没有可回档的巡回"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("仅恢复战役核心数据；未恢复船、玩家位置、物品或旧任务。").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    /** 增加/扣除共享资金（管理员）。 */
    private static int credits(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = level(source);
        if (level == null) {
            source.sendFailure(Component.literal("深海维度未加载"));
            return 0;
        }
        CampaignState state = CampaignState.wallet(level);
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        if (amount >= 0) {
            state.addCredits(amount);
        } else if (!state.spendCredits(-amount)) {
            source.sendFailure(Component.literal("资金不足（当前 " + state.credits() + "）"));
            return 0;
        }
        CampaignManager.syncAll(source.getServer());
        source.sendSuccess(() -> Component.literal("共享资金：" + state.credits() + " credits")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
}
