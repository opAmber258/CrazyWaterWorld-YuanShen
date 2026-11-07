package com.opamb.barotrauma.medical;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
            "stun", "眩晕");

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
                String.format("失血=%.0f 缺氧=%.0f（缺氧>50、失血>5 才开始掉血）", bloodloss, oxygen))
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
            return 0;
        }
        Limb limb = type.limbSpecific() ? Limb.TORSO : null;
        container.add(type, limb, strength);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "已注入 " + type.id() + " " + (limb == null ? "(全身)" : limb.name()) + " +" + strength), true);
        return 1;
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
