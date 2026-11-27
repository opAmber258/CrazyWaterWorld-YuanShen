package com.opamb.barotrauma.world.shipyard;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * `/barotrauma shipyard [leave|plot]`：编辑器 UI 接入前的命令兜底。
 */
public final class ShipyardCommand {

    private ShipyardCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("shipyard")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return ShipyardManager.enter(player) ? 1 : 0;
                })
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return ShipyardManager.leave(player) ? 1 : 0;
                }))
                .then(Commands.literal("plot").executes(ctx -> {
                    ShipyardManager.report(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("shipyard.message.status"), false);
                    return 1;
                }))
                .then(Commands.literal("test")
                        .executes(ctx -> ShipyardTestService.start(
                                ctx.getSource().getPlayerOrException()) ? 1 : 0)
                        .then(Commands.literal("stop").executes(ctx -> ShipyardTestService.stop(
                                ctx.getSource().getPlayerOrException()) ? 1 : 0)))
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> save(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("load")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> load(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("export")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> export(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("library").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    List<ShipLibrary.Entry> entries = ShipLibrary.list();
                    if (entries.isEmpty()) {
                        player.displayClientMessage(Component.translatable("shipyard.library.empty"), false);
                    } else {
                        for (ShipLibrary.Entry entry : entries) {
                            player.displayClientMessage(Component.literal(entry.id() + " · "
                                    + entry.name() + " · " + entry.sizeText()), false);
                        }
                    }
                    return 1;
                }));
    }

    private static int save(ServerPlayer player, String name) {
        ServerLevel level = player.getServer() == null ? null
                : player.getServer().getLevel(ModDimensions.SHIPYARD);
        if (level == null) {
            return 0;
        }
        ShipyardSavedData data = ShipyardSavedData.get(level);
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (plot == null) {
            return 0;
        }
        String safeName = ShipLibrary.clip(name == null || name.isBlank()
                ? "ship_" + plot.index : name);
        String id = ShipyardLibraryServer.uniqueId(
                ShipyardLibraryServer.slug(safeName), safeName, plot.shipId);
        boolean ok = ShipyardStructureIO.save(level, plot, id, safeName);
        if (ok) {
            data.setShipId(player.getUUID(), id);
            data.setDirty(player.getUUID(), false);
            data.setDirty();
            com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                    com.opamb.barotrauma.network.ShipyardStatePacket.of(plot));
        }
        player.displayClientMessage(Component.translatable(ok ? "shipyard.library.saved"
                : "shipyard.library.save_failed", id), ok);
        return ok ? 1 : 0;
    }

    private static int load(ServerPlayer player, String id) {
        ServerLevel level = player.getServer() == null ? null
                : player.getServer().getLevel(ModDimensions.SHIPYARD);
        if (level == null) {
            return 0;
        }
        ShipyardSavedData data = ShipyardSavedData.get(level);
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (plot == null) {
            return 0;
        }
        boolean ok = ShipyardStructureIO.load(level, plot, id);
        if (ok) {
            // 旧船的撤销栈不能作用到刚加载的新船坐标上。
            ShipyardServer.clearPlayer(player.getUUID());
            data.setDirty(player.getUUID(), false);
            data.setDirty();
            com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                    com.opamb.barotrauma.network.ShipyardStatePacket.of(plot));
            com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                    com.opamb.barotrauma.network.ShipyardStatusPacket.of(
                            ShipyardValidator.validate(level, plot)));
        }
        player.displayClientMessage(Component.translatable(ok ? "shipyard.library.loaded"
                : "shipyard.library.load_failed", id), ok);
        return ok ? 1 : 0;
    }

    private static int export(ServerPlayer player, String id) {
        boolean ok = ShipyardStructureIO.export(id);
        player.displayClientMessage(Component.translatable(ok ? "shipyard.library.exported"
                : "shipyard.library.export_failed", id), ok);
        return ok ? 1 : 0;
    }
}
