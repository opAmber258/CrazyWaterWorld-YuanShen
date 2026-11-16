package com.opamb.barotrauma.medical;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.medical.affliction.AfflictionContainer;
import com.opamb.barotrauma.medical.affliction.AfflictionType;
import com.opamb.barotrauma.medical.affliction.Limb;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * {@code /barotrauma medical}：医疗系统的诊断与测试命令。
 *
 * <p>M1 没有 UI 和治疗物品，测试全靠这里：{@code medical} 看自己的病症，
 * {@code medical give &lt;id&gt; [强度]}（OP）注入测试病症，
 * {@code medical clear} 清空自己（摔伤測到一半想停时用）。</p>
 */
public final class MedicalCommand {

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "blunttrauma", "钝器伤(内伤)",
            "bleeding", "出血(流血)",
            "burn", "烧伤",
            "bloodloss", "失血",
            "oxygenlow", "缺氧",
            "stun", "眩晕",
            "radiationsickness", "辐射病");

    private MedicalCommand() {
    }

    /** 挂到现有 {@code /barotrauma} 根命令下（见 DeepSeaEvents#onRegisterCommands）。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("medical")
                .executes(MedicalCommand::show)
                .then(Commands.literal("clear").requires(source -> source.hasPermission(2)).executes(MedicalCommand::clear))
                .then(Commands.literal("give").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("affliction", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        AfflictionType.ALL.stream().map(AfflictionType::id), builder))
                                .executes(ctx -> give(ctx, 50.0))
                                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0.0, 200.0))
                                        .executes(ctx -> give(ctx, DoubleArgumentType.getDouble(ctx, "strength"))))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AfflictionContainer container = MedicalCapabilities.container(player);
        // 诊断头：先说明"现在注入会不会被清掉"，否则玩家会以为注入无效。
        // 创造模式下没有调试器时，MedicalEvents 每秒 clear() 一次——这是设计如此。
        boolean wiped = player.isSpectator() || player.isCreative();
        if (wiped) {
            boolean hasDebugger = carriesDebugger(player);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    hasDebugger
                            ? "【诊断】创造模式 + 背包里有医疗调试器 → 病症不会被清空"
                            : "【诊断】创造模式且背包里没有医疗调试器 → 病症每秒被清空！"
                              + "注入前请先 /gamemode survival，或把医疗调试器放进背包")
                    .withStyle(hasDebugger ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        }
        if (container == null || container.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("当前没有病症").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        ctx.getSource().sendSuccess(
                () -> Component.literal("—— 病症清单 ——").withStyle(ChatFormatting.GOLD), false);
        for (AfflictionType type : AfflictionType.ALL) {
            double total = container.totalStrength(type);
            if (total <= 0) {
                continue;
            }
            String name = DISPLAY_NAMES.getOrDefault(type.id(), type.id());
            String detail = type.limbSpecific() ? perLimbText(container, type) : "";
            ctx.getSource().sendSuccess(
                    () -> Component.literal(name + " 总计 " + format(total) + detail), false);
        }
        double oxygen = container.strength(AfflictionType.OXYGEN_LOW, null);
        double bloodloss = container.strength(AfflictionType.BLOODLOSS, null);
        ctx.getSource().sendSuccess(() -> Component.literal(
                String.format("失血=%.0f 缺氧=%.0f（失血>5、缺氧>50 各自开始扣活力；其他病症按各自阈值）",
                        bloodloss, oxygen))
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AfflictionContainer container = MedicalCapabilities.container(player);
        if (container == null) {
            return 0;
        }
        container.clear();
        ctx.getSource().sendSuccess(() -> Component.literal("病症已清空").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, double strength) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "affliction");
        AfflictionType type = AfflictionType.byId(id);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("未知病症 id：" + id));
            return 0;
        }
        AfflictionContainer container = MedicalCapabilities.container(player);
        if (container == null) {
            ctx.getSource().sendFailure(Component.literal("容器为 null——医疗能力未挂载"));
            return 0;
        }
        Limb limb = type.limbSpecific() ? Limb.TORSO : null;
        double after = container.add(type, limb, strength);

        // 立即回读：证明写入真的发生了（这是排查"注入了却看不到"的关键证据）
        double readBack = container.strength(type, limb);
        int rowCount = container.rows().size();
        BarotraumaMod.LOGGER.info(
                "[medical-diag] give {} on {} | player={} creative={} spectator={} "
                        + " wrote=[{} limb={}] readBack={} rows={}",
                type.id(), player.getName().getString(), player.getUUID(),
                player.isCreative(), player.isSpectator(),
                after, limb, readBack, rowCount);
        BarotraumaMod.LOGGER.info("[medical-diag] rows={}", container.rows());

        final double shown = readBack;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "已注入 " + type.id() + (limb == null ? "(全身)" : " " + limb.name())
                        + " +" + strength + " → 当前 " + format(shown)
                        + (player.isCreative() ? "（注意：创造模式需要背包里有医疗调试器，否则每秒被清空）" : "")),
                false);
        return 1;
    }

    /** 背包里是否有医疗调试器（`MedicalEvents` 创造模式清空的豁免条件，必须与那里一致）。 */
    private static boolean carriesDebugger(ServerPlayer player) {
        var expected = com.opamb.barotrauma.registry.ModItems.MEDICAL_DEBUGGER.get();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(expected)) {
                return true;
            }
        }
        return false;
    }

    private static String perLimbText(AfflictionContainer container, AfflictionType type) {
        StringBuilder text = new StringBuilder(" [");
        boolean first = true;
        for (Limb limb : Limb.values()) {
            double strength = container.strength(type, limb);
            if (strength > 0) {
                if (!first) {
                    text.append(", ");
                }
                text.append(limb.name()).append('=').append(format(strength));
                first = false;
            }
        }
        return text.append(']').toString();
    }

    private static String format(double value) {
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }
}
