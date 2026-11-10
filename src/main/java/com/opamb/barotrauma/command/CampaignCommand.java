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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import com.opamb.barotrauma.block.CabinetBlock;
import com.opamb.barotrauma.blockentity.CabinetBlockEntity;
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
                                .executes(CampaignCommand::credits)))
                .then(Commands.literal("missions").executes(CampaignCommand::missions))
                .then(Commands.literal("cabinets").requires(source -> source.hasPermission(2))
                        .executes(CampaignCommand::cabinets));
    }

    /** 诊断：扫描玩家周围的容器方块，打印分片/主方块/方块实体/形状状态。 */
    private static int cabinets(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        int found = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -6, -8), center.offset(8, 8, 8))) {
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof CabinetBlock cab)) {
                continue;
            }
            BlockPos immutable = p.immutable();
            int part = st.getValue(CabinetBlock.PART);
            BlockPos master = immutable.below(part);
            BlockState ms = level.getBlockState(master);
            boolean masterOk = ms.getBlock() == st.getBlock()
                    && ms.getValue(CabinetBlock.PART) == 0;
            boolean hasBe = level.getBlockEntity(master) instanceof CabinetBlockEntity;
            String shape = st.getShape(level, immutable).isEmpty() ? "empty" : "solid";
            final String line = immutable.toShortString() + " " + cab.kind().id()
                    + " part=" + part + " master=" + master.toShortString()
                    + " masterOk=" + masterOk + " be=" + hasBe + " shape=" + shape;
            source.sendSuccess(() -> Component.literal(line), false);
            found++;
        }
        final int n = found;
        source.sendSuccess(() -> Component.literal("容器方块 " + n + " 个"), false);
        return found;
    }

    /** 列出寒冷深穴任务目录：登记总数、终端实际发布、未实装类别。 */
    private static int missions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var specs = com.opamb.barotrauma.mission.MissionCatalog.specs();
        var offers = com.opamb.barotrauma.mission.MissionCatalog.offers();
        source.sendSuccess(() -> Component.literal("== 任务目录（寒冷深穴）==")
                .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("登记 " + specs.size() + " 条，终端发布 "
                + offers.size() + " 条"), false);
        var byKind = specs.stream().collect(java.util.stream.Collectors.groupingBy(
                com.opamb.barotrauma.mission.MissionSpec::kind,
                java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        byKind.forEach((kind, count) -> source.sendSuccess(
                () -> Component.literal("· " + kind + " " + count + " 条"
                        + (kind.implemented() ? "（已实装）" : "（未实装）")), false));
        for (var spec : offers) {
            source.sendSuccess(() -> Component.literal("发布 · " + spec.nameZh()
                    + "（" + spec.kind() + "，奖励 " + spec.reward() + "）"), false);
        }
        return offers.size();
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
