package com.opamb.barotrauma.world.shipyard;

import com.opamb.barotrauma.BarotraumaMod;
import com.opamb.barotrauma.world.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 造船厂入口/出口与地块分配。
 *
 * <p>首版用命令做入口兜底；编辑器 UI 接入后走同一套方法。</p>
 */
@Mod.EventBusSubscriber(modid = BarotraumaMod.MOD_ID)
public final class ShipyardManager {

    private ShipyardManager() {
    }

    /** 玩家重进存档时若仍在船厂，补发地块状态，客户端才能默认打开编辑器。 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.SHIPYARD)) {
            return;
        }
        ShipyardSavedData.Plot plot = ShipyardSavedData.get(level).plot(player.getUUID());
        if (plot != null) {
            com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                    com.opamb.barotrauma.network.ShipyardStatePacket.of(plot));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // 撤销历史是会话级的；登录进另一个存档后旧坐标不能继续生效。
        ShipyardServer.clearPlayer(event.getEntity().getUUID());
        MinecraftServer server = event.getEntity().getServer();
        ServerLevel shipyard = server == null ? null : server.getLevel(ModDimensions.SHIPYARD);
        if (shipyard != null) {
            ShipyardSavedData data = ShipyardSavedData.get(shipyard);
            if (event.getEntity().getUUID().equals(data.testOwner())
                    && !com.opamb.barotrauma.ship.vs.VsPilot.hasShip()) {
                // 没有活动物理船时不该继续锁着试航位，同时清理遗留测试区。
                ShipyardTestService.clearTestArea(server, data);
                data.endTest();
            }
        }
    }

    /** 第一人称放置/破坏也要进地块 bounds，否则保存/试航/清单看不到这些方块。 */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!guard(player, event.getPos(), event.getPlacedBlock())) {
            event.setCanceled(true);
            return;
        }
        track(player, event.getPos());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!guard(player, event.getPos(), event.getState())) {
            event.setCanceled(true);
            return;
        }
        track(player, event.getPos());
    }

    /** 船厂内第一人称也只能编辑自己地块内的船体/设备方块。 */
    private static boolean guard(ServerPlayer player, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.SHIPYARD)) {
            return true;
        }
        ShipyardSavedData.Plot plot = ShipyardSavedData.get(level).plot(player.getUUID());
        if (plot == null) {
            return false;
        }
        if (!ShipyardServer.insidePlot(plot, pos)) {
            player.displayClientMessage(Component.translatable("shipyard.message.outside_plot")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!com.opamb.barotrauma.registry.ModShipBlocks.isShipBlock(state)
                && !com.opamb.barotrauma.registry.ModShipBlocks.isEquipment(state)) {
            player.displayClientMessage(Component.translatable("shipyard.message.not_ship_block")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    private static void track(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.SHIPYARD)) {
            return;
        }
        ShipyardSavedData data = ShipyardSavedData.get(level);
        ShipyardSavedData.Plot plot = data.plot(player.getUUID());
        if (plot != null && ShipyardServer.insidePlot(plot, pos)) {
            plot.expand(pos.offset(-4, -4, -4));
            plot.expand(pos.offset(4, 4, 4));
            data.setDirty(player.getUUID(), true);
            data.setDirty();
            ShipyardServer.refreshCore(level, plot,
                    pos.offset(-4, -4, -4), pos.offset(4, 4, 4));
            ShipyardServer.schedule(player, level, plot);
        }
    }

    public static boolean enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        ServerLevel shipyard = server.getLevel(ModDimensions.SHIPYARD);
        if (shipyard == null) {
            player.displayClientMessage(Component.translatable("shipyard.message.no_level")
                    .withStyle(ChatFormatting.RED), false);
            return false;
        }
        if (player.level().dimension().equals(ModDimensions.SHIPYARD)) {
            player.displayClientMessage(Component.translatable("shipyard.message.already"), false);
            return true;
        }
        ShipyardSavedData data = ShipyardSavedData.get(shipyard);
        ShipyardSavedData.Plot existing = data.plot(player.getUUID());
        ShipyardSavedData.Plot plot = data.allocate(player.getUUID());
        if (existing == null) {
            ShipyardPlot.prepare(shipyard, plot, ShipyardPlot.Preset.PLATFORM);
        }
        // 记录返回点：只从非船厂维度覆盖，避免从试航区回来时把返回点改成试航区。
        // SHIPYARD_TEST 也是船厂系列维度，从它进入时同样不能覆盖原世界的返回点。
        ResourceLocation current = player.level().dimension().location();
        if (!current.equals(ModDimensions.SHIPYARD.location())
                && !current.equals(ModDimensions.SHIPYARD_TEST.location())) {
            data.recordReturn(player.getUUID(), current,
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
        BlockPos center = ShipyardPlot.center(plot);
        player.teleportTo(shipyard, center.getX() + 0.5D, center.getY() + 2.0D,
                center.getZ() + 0.5D, 0.0F, 15.0F);
        data.markVisited(player.getUUID(), shipyard.getGameTime());
        data.setDirty();
        com.opamb.barotrauma.network.ModNetwork.sendToPlayer(player,
                com.opamb.barotrauma.network.ShipyardStatePacket.of(plot));
        player.displayClientMessage(Component.translatable("shipyard.message.entered",
                plot.index, center.getX(), center.getZ()), false);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        if (player.level().dimension().equals(ModDimensions.SHIPYARD_TEST)) {
            // 试航中直接 leave 会把船和锁留在测试维度；先结算试航再走正常返回。
            if (!ShipyardTestService.stop(player)) {
                return false;
            }
            return leave(player);
        }
        ServerLevel shipyard = server.getLevel(ModDimensions.SHIPYARD);
        if (shipyard != null) {
            ShipyardSavedData.Plot plot = ShipyardSavedData.get(shipyard).plot(player.getUUID());
            if (plot != null && plot.returnDimension != null) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, plot.returnDimension);
                ServerLevel target = server.getLevel(key);
                if (target != null) {
                    player.teleportTo(target, plot.returnX, plot.returnY, plot.returnZ,
                            plot.returnYaw, plot.returnPitch);
                    player.displayClientMessage(Component.translatable("shipyard.message.left"), false);
                    return true;
                }
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY() + 1.0D,
                spawn.getZ() + 0.5D, 0.0F, 0.0F);
        player.displayClientMessage(Component.translatable("shipyard.message.left_fallback"), false);
        return true;
    }

    public static ShipyardSavedData.Plot plot(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        ServerLevel shipyard = server.getLevel(ModDimensions.SHIPYARD);
        return shipyard == null ? null : ShipyardSavedData.get(shipyard).plot(player.getUUID());
    }

    /** 命令用：打印当前地块信息。 */
    public static void report(ServerPlayer player) {
        ShipyardSavedData.Plot plot = plot(player);
        if (plot == null) {
            player.displayClientMessage(Component.translatable("shipyard.message.no_plot"), false);
            return;
        }
        player.displayClientMessage(Component.translatable("shipyard.message.plot",
                plot.index, plot.originX(), plot.originY(), plot.originZ(),
                plot.shipId.isEmpty() ? "-" : plot.shipId), false);
    }

    static ResourceKey<Level> key(ResourceLocation location) {
        return ResourceKey.create(Registries.DIMENSION, location);
    }
}
